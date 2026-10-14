package com.example.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.receiver.ReminderScheduler
import com.example.security.SecurityManager
import com.example.util.NotificationHelper
import com.example.util.StorageAccessHelper
import com.example.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.local.entity.TimetableBlockEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class FocusPilotHttpServer(
    private val context: Context,
    private val securityManager: SecurityManager,
    private val database: AppDatabase,
    private val port: Int
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val usageStatsHelper = UsageStatsHelper(context)
    private val storageAccessHelper = StorageAccessHelper(context, securityManager)
    private val notificationHelper = NotificationHelper(context, securityManager)
    private val reminderScheduler = ReminderScheduler(context)

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        scope.launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                isRunning.set(false)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    fun isServerRunning(): Boolean = isRunning.get()

    private suspend fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
        var endpoint = "/"
        var method = "GET"
        var statusCode = 200

        try {
            socket.soTimeout = 10_000 // 10 second socket timeout
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val outputStream = socket.getOutputStream()

            // 1. Read request line: e.g. "GET /v1/status HTTP/1.1"
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                method = parts[0].uppercase()
                endpoint = parts[1]
            }

            // 2. Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(':')
                if (colonIdx != -1) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // 3. Handle CORS pre-flight OPTIONS request
            if (method == "OPTIONS") {
                sendCorsResponse(outputStream)
                return
            }

            // 4. Read body if POST
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBuilder = StringBuilder()
            if (contentLength > 0 && contentLength < 1_000_000) { // max 1MB payload
                val buffer = CharArray(1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val read = reader.read(buffer, 0, minOf(remaining, buffer.size))
                    if (read == -1) break
                    bodyBuilder.append(buffer, 0, read)
                    remaining -= read
                }
            }
            val requestBody = bodyBuilder.toString()

            // 5. Emergency Kill-Switch Check
            if (!securityManager.isRemoteAccessEnabled.value) {
                statusCode = 403
                val resp = JSONObject().put("error", "Remote access has been disabled by user on device (Emergency Kill Switch is ACTIVE)").toString()
                sendResponse(outputStream, 403, "Forbidden", resp)
                recordAuditLog(clientIp, endpoint, method, 403, "Kill-switch blocked request", resp, isBlocked = true)
                return
            }

            // 6. Authentication Check: Bearer token
            val authHeader = headers["authorization"] ?: ""
            val expectedToken = securityManager.getApiToken()
            val hasValidToken = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                val token = authHeader.substring(7).trim()
                token == expectedToken
            } else {
                // Also support query param ?token=...
                val queryToken = extractQueryParam(endpoint, "token")
                queryToken == expectedToken
            }

            if (!hasValidToken) {
                statusCode = 401
                val resp = JSONObject().put("error", "Unauthorized. Provide valid Bearer token in 'Authorization: Bearer <token>' header.").toString()
                sendResponse(outputStream, 401, "Unauthorized", resp)
                recordAuditLog(clientIp, endpoint, method, 401, "Invalid token attempt", resp, isBlocked = true)
                return
            }

            // Clean endpoint without query string
            val cleanPath = endpoint.substringBefore('?')

            // 7. Route request
            val (status, responseJson, summary) = routeRequest(cleanPath, method, endpoint, requestBody)
            statusCode = status
            sendResponse(outputStream, statusCode, getStatusText(statusCode), responseJson)
            recordAuditLog(clientIp, cleanPath, method, statusCode, summary.take(200), responseJson.take(200), isBlocked = statusCode >= 400)

        } catch (e: SocketTimeoutException) {
            // Connection timed out
        } catch (e: Exception) {
            try {
                val resp = JSONObject().put("error", "Internal server error: ${e.message}").toString()
                sendResponse(socket.getOutputStream(), 500, "Internal Server Error", resp)
                recordAuditLog(clientIp, endpoint, method, 500, "Exception: ${e.message}", resp, isBlocked = true)
            } catch (ignored: Exception) {}
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private suspend fun routeRequest(
        path: String,
        method: String,
        rawUri: String,
        body: String
    ): Triple<Int, String, String> {
        return when {
            (path == "/v1/overview" || path == "/v1/agent/overview") && method == "GET" -> handleAgentOverview()
            path == "/v1/status" && method == "GET" -> handleStatus()
            path == "/v1/permissions" && method == "GET" -> handlePermissions()
            path == "/v1/usage/summary" && method == "GET" -> handleUsageSummary()
            path == "/v1/usage/apps" && method == "GET" -> handleUsageApps(rawUri)
            path == "/v1/files" && method == "GET" -> handleFilesList()
            path == "/v1/files/read" && method == "POST" -> handleFileRead(body)
            path == "/v1/tasks" && method == "GET" -> handleTasksList()
            path == "/v1/tasks" && method == "POST" -> handleCreateTask(body)
            path == "/v1/tasks" && method == "PUT" -> handleUpdateTask(body)
            path == "/v1/tasks" && method == "DELETE" -> handleDeleteTask(rawUri, body)
            path == "/v1/reminders" && method == "POST" -> handleCreateReminder(body)
            path == "/v1/reminders/task" && method == "POST" -> handleTaskReminder(body)
            path == "/v1/alerts" && method == "POST" -> handleCreateAlert(body)
            path == "/v1/apps/launch" && method == "POST" -> handleAppLaunch(body)
            path == "/v1/timetable" && method == "GET" -> handleGetTimetable()
            path == "/v1/timetable" && method == "POST" -> handleSetTimetable(body)
            path == "/v1/timetable/sync_weekly" && method == "POST" -> handleSyncWeeklyTimetable(body)
            path == "/v1/timetable/block" && method == "POST" -> handleAddTimetableBlock(body)
            path == "/v1/timetable/block" && method == "PUT" -> handleUpdateTimetableBlock(body)
            path == "/v1/timetable/block" && method == "DELETE" -> handleDeleteTimetableBlock(rawUri)
            path == "/v1/timetable/clear" && method == "POST" -> handleClearTimetable()
            path == "/v1/alarms/trigger" && method == "POST" -> handleTriggerLoudAlarm(body)
            path == "/v1/focus/strict/enable" && method == "POST" -> handleEnableStrictFocus(body)
            path == "/v1/focus/strict/disable" && method == "POST" -> handleDisableStrictFocus()
            path == "/v1/telemetry" && method == "GET" -> handleTelemetry()
            path == "/v1/alarm/ringtone" && method == "POST" -> handleAlarmRingtone(body)
            path == "/v1/alarm/stop" && method == "POST" -> handleAlarmStop()
            path == "/v1/camera/snap" && method == "POST" -> handleCameraSnap()
            path == "/v1/audio/record" && method == "POST" -> handleAudioRecord(body)
            path == "/v1/campus/context" && method == "GET" -> handleCampusContext()
            path == "/v1/device/volume" && method == "POST" -> handleSetVolume(body)
            path == "/v1/app/customize" && method == "POST" -> handleAppCustomize(body)
            path == "/v1/app/customization" && method == "GET" -> handleGetAppCustomization()
            path.startsWith("/v1/usage/timeframe") && method == "GET" -> handleUsageTimeframe(rawUri)
            else -> {
                val err = JSONObject()
                    .put("error", "Endpoint not found: $method $path")
                    .put("supported_endpoints", JSONArray(listOf(
                        "GET /v1/overview",
                        "GET /v1/status",
                        "GET /v1/permissions",
                        "GET /v1/usage/summary",
                        "GET /v1/usage/apps",
                        "GET /v1/files",
                        "POST /v1/files/read",
                        "GET /v1/tasks",
                        "POST /v1/tasks",
                        "PUT /v1/tasks",
                        "DELETE /v1/tasks?id=X",
                        "POST /v1/reminders",
                        "POST /v1/alerts",
                        "POST /v1/apps/launch",
                        "GET /v1/timetable",
                        "POST /v1/timetable",
                        "POST /v1/timetable/block",
                        "PUT /v1/timetable/block",
                        "DELETE /v1/timetable/block?id=X",
                        "POST /v1/timetable/clear",
                        "POST /v1/alarms/trigger"
                    )))
                    .toString()
                Triple(404, err, "Unknown route: $path")
            }
        }
    }

    // --- Endpoints ---

    private fun handleStatus(): Triple<Int, String, String> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val statusInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusInt == BatteryManager.BATTERY_STATUS_FULL

        val json = JSONObject()
            .put("status", "online")
            .put("service", "FocusPilot Phone API")
            .put("version", "1.0")
            .put("port", port)
            .put("battery_percent", batteryPct)
            .put("is_charging", isCharging)
            .put("remote_access_enabled", securityManager.isRemoteAccessEnabled.value)
            .put("timestamp_epoch_ms", System.currentTimeMillis())

        return Triple(200, json.toString(), "Status check OK (Battery: $batteryPct%)")
    }

    private suspend fun handlePermissions(): Triple<Int, String, String> {
        val approvedFiles = database.approvedFileDao().getAllApprovedFilesSync()
        val json = JSONObject()
            .put("usage_stats_permission_granted", usageStatsHelper.hasUsagePermission())
            .put("exact_alarms_permission_granted", reminderScheduler.canScheduleExactAlarms())
            .put("remote_access_enabled", securityManager.isRemoteAccessEnabled.value)
            .put("capabilities", JSONObject()
                .put("allow_usage_stats", securityManager.allowUsageStats.value)
                .put("allow_file_access", securityManager.allowFileAccess.value)
                .put("allow_app_launch", securityManager.allowAppLaunch.value)
                .put("allow_task_management", securityManager.allowTaskManagement.value)
                .put("allow_alerts", securityManager.allowAlerts.value)
            )
            .put("approved_files_count", approvedFiles.size)

        return Triple(200, json.toString(), "Permissions retrieved")
    }

    private fun handleUsageSummary(): Triple<Int, String, String> {
        if (!securityManager.allowUsageStats.value) {
            val err = JSONObject().put("error", "Capability 'allow_usage_stats' is disabled in user privacy settings").toString()
            return Triple(403, err, "Usage stats capability disabled")
        }

        val summary = usageStatsHelper.getTodayUsageSummary()
        val appsArray = JSONArray()
        for (app in summary.topApps) {
            appsArray.put(JSONObject()
                .put("package_name", app.packageName)
                .put("app_name", app.appName)
                .put("foreground_time_ms", app.totalTimeInForegroundMs)
                .put("formatted_duration", app.formattedDuration)
                .put("category", app.category)
                .put("last_used_epoch_ms", app.lastTimeUsedEpochMs)
            )
        }

        val json = JSONObject()
            .put("has_permission", summary.hasPermission)
            .put("start_time_epoch_ms", summary.startTimeEpochMs)
            .put("end_time_epoch_ms", summary.endTimeEpochMs)
            .put("total_screen_time_ms", summary.totalScreenTimeMs)
            .put("formatted_total_screen_time", summary.formattedTotalTime)
            .put("app_count", summary.appCount)
            .put("top_apps", appsArray)
            .put("data_limitations", summary.dataLimitations)

        return Triple(200, json.toString(), "Usage summary: ${summary.formattedTotalTime} total")
    }

    private fun handleUsageApps(rawUri: String): Triple<Int, String, String> {
        if (!securityManager.allowUsageStats.value) {
            val err = JSONObject().put("error", "Capability 'allow_usage_stats' is disabled in user privacy settings").toString()
            return Triple(403, err, "Usage apps capability disabled")
        }

        val now = System.currentTimeMillis()
        val daysParam = extractQueryParam(rawUri, "days")?.toIntOrNull() ?: 1
        val startTime = now - (daysParam.coerceIn(1, 30) * 24L * 60 * 60 * 1000)

        val apps = usageStatsHelper.getAllAppsUsage(startTime, now)
        val array = JSONArray()
        for (app in apps) {
            array.put(JSONObject()
                .put("package_name", app.packageName)
                .put("app_name", app.appName)
                .put("foreground_time_ms", app.totalTimeInForegroundMs)
                .put("formatted_duration", app.formattedDuration)
                .put("category", app.category)
                .put("last_used_epoch_ms", app.lastTimeUsedEpochMs)
            )
        }

        val json = JSONObject()
            .put("days_queried", daysParam)
            .put("apps_count", apps.size)
            .put("apps", array)

        return Triple(200, json.toString(), "Retrieved ${apps.size} app usage records")
    }

    private suspend fun handleFilesList(): Triple<Int, String, String> {
        if (!securityManager.allowFileAccess.value) {
            val err = JSONObject().put("error", "Capability 'allow_file_access' is disabled in user privacy settings").toString()
            return Triple(403, err, "File access capability disabled")
        }

        val files = database.approvedFileDao().getAllApprovedFilesSync()
        val array = JSONArray()
        for (f in files) {
            array.put(JSONObject()
                .put("file_id", f.id)
                .put("display_name", f.displayName)
                .put("mime_type", f.mimeType)
                .put("is_directory", f.isDirectory)
                .put("size_bytes", f.sizeBytes)
                .put("permission_level", f.permissionLevel)
                .put("date_added_epoch_ms", f.dateAddedEpochMs)
            )
        }

        val json = JSONObject()
            .put("count", files.size)
            .put("approved_files", array)

        return Triple(200, json.toString(), "Listed ${files.size} approved files")
    }

    private suspend fun handleFileRead(body: String): Triple<Int, String, String> {
        if (!securityManager.allowFileAccess.value) {
            val err = JSONObject().put("error", "Capability 'allow_file_access' is disabled in user privacy settings").toString()
            return Triple(403, err, "File read capability disabled")
        }

        val requestJson = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }

        val fileId = if (requestJson.has("file_id")) requestJson.getInt("file_id") else null
        val fileName = if (requestJson.has("name")) requestJson.getString("name") else null
        val uriStr = if (requestJson.has("uri")) requestJson.getString("uri") else null

        val approvedFile = when {
            fileId != null -> database.approvedFileDao().getById(fileId)
            uriStr != null -> database.approvedFileDao().getByUriString(uriStr)
            fileName != null -> {
                val all = database.approvedFileDao().getAllApprovedFilesSync()
                all.firstOrNull { it.displayName.equals(fileName, ignoreCase = true) }
            }
            else -> null
        }

        if (approvedFile == null) {
            val err = JSONObject()
                .put("error", "File not found or not in user's approved locations list. You can only read files explicitly granted by the user in FocusPilot.")
                .toString()
            return Triple(404, err, "File not approved: id=$fileId, name=$fileName")
        }

        val readResult = storageAccessHelper.readFileSafely(approvedFile)
        if (!readResult.success) {
            val err = JSONObject()
                .put("error", readResult.error ?: "Failed to read approved file")
                .put("file_name", readResult.fileName)
                .toString()
            return Triple(403, err, "File read blocked: ${readResult.error}")
        }

        val resp = JSONObject()
            .put("success", true)
            .put("file_id", approvedFile.id)
            .put("display_name", readResult.fileName)
            .put("mime_type", readResult.mimeType)
            .put("size_bytes", readResult.sizeBytes)
            .put("is_text", readResult.isText)

        if (readResult.isText) {
            resp.put("content", readResult.textContent)
        } else {
            resp.put("content_base64", readResult.base64Content)
        }

        return Triple(200, resp.toString(), "Read file ${readResult.fileName} (${readResult.sizeBytes} bytes)")
    }

    private suspend fun handleTasksList(): Triple<Int, String, String> {
        if (!securityManager.allowTaskManagement.value) {
            val err = JSONObject().put("error", "Capability 'allow_task_management' is disabled in user privacy settings").toString()
            return Triple(403, err, "Task management capability disabled")
        }

        val tasks = database.taskDao().getAllTasksSync()
        val array = JSONArray()
        for (t in tasks) {
            array.put(JSONObject()
                .put("id", t.id)
                .put("title", t.title)
                .put("description", t.description)
                .put("completed", t.completed)
                .put("priority", t.priority)
                .put("category", t.category)
                .put("due_date_epoch_ms", t.dueDateEpochMs)
                .put("created_at_epoch_ms", t.createdAtEpochMs)
            )
        }

        val json = JSONObject()
            .put("count", tasks.size)
            .put("tasks", array)

        return Triple(200, json.toString(), "Retrieved ${tasks.size} tasks")
    }

    private suspend fun handleCreateReminder(body: String): Triple<Int, String, String> {
        if (!securityManager.allowAlerts.value) {
            val err = JSONObject().put("error", "Capability 'allow_alerts' is disabled in user privacy settings").toString()
            return Triple(403, err, "Alerts/reminders capability disabled")
        }

        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }

        val title = req.optString("title", "Focus Reminder")
        val message = req.optString("message", "Hermes reminder: check your focus goal.")

        val now = System.currentTimeMillis()
        val triggerTime = when {
            req.has("trigger_time_epoch_ms") -> req.getLong("trigger_time_epoch_ms")
            req.has("delay_seconds") -> now + (req.getLong("delay_seconds") * 1000)
            else -> now + (5 * 60 * 1000) // default 5 minutes
        }

        val reminder = ReminderEntity(
            title = title,
            message = message,
            triggerTimeEpochMs = triggerTime
        )
        val id = database.reminderDao().insertReminder(reminder).toInt()
        reminderScheduler.scheduleReminder(id, title, message, triggerTime)

        val resp = JSONObject()
            .put("success", true)
            .put("reminder_id", id)
            .put("title", title)
            .put("message", message)
            .put("trigger_time_epoch_ms", triggerTime)

        return Triple(201, resp.toString(), "Scheduled reminder '$title' for $triggerTime")
    }

    private suspend fun handleTaskReminder(body: String): Triple<Int, String, String> {
        if (!securityManager.allowAlerts.value) {
            val err = JSONObject().put("error", "Capability 'allow_alerts' is disabled in user privacy settings").toString()
            return Triple(403, err, "Alerts/reminders capability disabled")
        }

        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }

        val title = req.optString("title", "Task Reminder")
        val message = req.optString("message", "Time to prepare for your upcoming task.")
        val offsetMinutesBefore = req.optInt("minutes_before", 10)
        val taskId = req.optInt("task_id", -1)
        val dueEpochMs = if (taskId != -1) {
            database.taskDao().getTaskById(taskId)?.dueDateEpochMs ?: req.optLong("due_timestamp_ms", -1L)
        } else {
            req.optLong("due_timestamp_ms", -1L)
        }

        val now = System.currentTimeMillis()
        val triggerTime = if (dueEpochMs > now) {
            dueEpochMs - (offsetMinutesBefore * 60_000L)
        } else {
            now + (offsetMinutesBefore * 60_000L)
        }

        val reminder = ReminderEntity(
            title = title,
            message = message,
            triggerTimeEpochMs = triggerTime
        )
        val id = database.reminderDao().insertReminder(reminder).toInt()
        reminderScheduler.scheduleReminder(id, title, message, triggerTime)

        val resp = JSONObject()
            .put("success", true)
            .put("reminder_id", id)
            .put("title", title)
            .put("message", message)
            .put("minutes_before", offsetMinutesBefore)
            .put("trigger_time_epoch_ms", triggerTime)

        return Triple(201, resp.toString(), "Scheduled task reminder for $offsetMinutesBefore min before task")
    }

    private fun handleCreateAlert(body: String): Triple<Int, String, String> {
        if (!securityManager.allowAlerts.value) {
            val err = JSONObject().put("error", "Capability 'allow_alerts' is disabled in user privacy settings").toString()
            return Triple(403, err, "Alerts capability disabled")
        }

        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }

        val title = req.optString("title", "Hermes Notification")
        val message = req.optString("message", "")
        val priority = req.optString("priority", "normal")

        if (message.isBlank()) {
            return Triple(400, JSONObject().put("error", "'message' field is required").toString(), "Empty alert message")
        }

        notificationHelper.sendLocalAlert(title, message, priority)

        val resp = JSONObject()
            .put("success", true)
            .put("title", title)
            .put("message", message)
            .put("priority", priority)

        return Triple(200, resp.toString(), "Sent push alert '$title'")
    }

    private suspend fun handleAppLaunch(body: String): Triple<Int, String, String> {
        if (!securityManager.allowAppLaunch.value) {
            val err = JSONObject().put("error", "Capability 'allow_app_launch' is disabled in user privacy settings").toString()
            return Triple(403, err, "App launch capability disabled")
        }

        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }

        val packageName = req.optString("package_name", "").trim()
        if (packageName.isBlank()) {
            return Triple(400, JSONObject().put("error", "'package_name' is required").toString(), "Missing package_name")
        }

        // Verify if app is allowed by user
        val allowedApp = database.allowedAppDao().getByPackage(packageName)
        if (allowedApp != null && !allowedApp.isLaunchAllowed) {
            val err = JSONObject().put("error", "App '$packageName' is blocked by user allowlist in FocusPilot").toString()
            return Triple(403, err, "App launch blocked by allowlist: $packageName")
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            val err = JSONObject().put("error", "No launcher intent found for package '$packageName'. App may not be installed.").toString()
            return Triple(404, err, "Package not found: $packageName")
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        val resp = JSONObject()
            .put("success", true)
            .put("launched_package", packageName)

        return Triple(200, resp.toString(), "Launched package $packageName")
    }

    private suspend fun handleGetTimetable(): Triple<Int, String, String> {
        val blocks = database.timetableDao().getAllBlocksSync()
        val array = JSONArray()
        for (b in blocks) {
            array.put(JSONObject()
                .put("id", b.id)
                .put("title", b.title)
                .put("subject_or_project", b.subjectOrProject)
                .put("category", b.category)
                .put("start_minute", b.startMinuteOfDay)
                .put("end_minute", b.endMinuteOfDay)
                .put("start_time_formatted", formatMinuteOfDay(b.startMinuteOfDay))
                .put("end_time_formatted", formatMinuteOfDay(b.endMinuteOfDay))
                .put("notes", b.notes)
                .put("priority", b.priority)
                .put("is_agent_generated", b.isAgentGenerated)
                .put("is_completed", b.isCompleted)
            )
        }
        val json = JSONObject()
            .put("count", blocks.size)
            .put("blocks", array)
        return Triple(200, json.toString(), "Retrieved ${blocks.size} timetable blocks")
    }

    private suspend fun handleSetTimetable(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }
        val blocksArray = req.optJSONArray("blocks")
            ?: return Triple(400, JSONObject().put("error", "'blocks' array required").toString(), "Missing blocks array")

        val newBlocks = mutableListOf<com.example.data.local.entity.TimetableBlockEntity>()
        for (i in 0 until blocksArray.length()) {
            val b = blocksArray.getJSONObject(i)
            newBlocks.add(
                com.example.data.local.entity.TimetableBlockEntity(
                    title = b.getString("title"),
                    subjectOrProject = b.optString("subject_or_project", b.optString("subject", "")),
                    category = b.optString("category", "Subject"),
                    startMinuteOfDay = b.getInt("start_minute"),
                    endMinuteOfDay = b.getInt("end_minute"),
                    notes = b.optString("notes", ""),
                    priority = b.optString("priority", "HIGH"),
                    isAgentGenerated = true
                )
            )
        }
        val replaceAll = req.optBoolean("replace_all", true)
        if (replaceAll) {
            database.timetableDao().clearAllBlocks()
        }
        database.timetableDao().insertAll(newBlocks)
        return Triple(200, JSONObject().put("success", true).put("inserted_count", newBlocks.size).toString(), "Updated ${newBlocks.size} timetable blocks")
    }

    private suspend fun handleUpdateTimetableBlock(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }
        val id = req.optInt("id", -1)
        if (id == -1) return Triple(400, JSONObject().put("error", "Block 'id' required").toString(), "Missing block id")
        val existing = database.timetableDao().getBlockById(id)
            ?: return Triple(404, JSONObject().put("error", "Block $id not found").toString(), "Block not found")

        val updated = existing.copy(
            title = req.optString("title", existing.title),
            subjectOrProject = req.optString("subject_or_project", existing.subjectOrProject),
            category = req.optString("category", existing.category),
            startMinuteOfDay = req.optInt("start_minute", existing.startMinuteOfDay),
            endMinuteOfDay = req.optInt("end_minute", existing.endMinuteOfDay),
            notes = req.optString("notes", existing.notes),
            priority = req.optString("priority", existing.priority),
            isCompleted = req.optBoolean("is_completed", existing.isCompleted),
            isAgentGenerated = true
        )
        database.timetableDao().updateBlock(updated)
        return Triple(200, JSONObject().put("success", true).put("block_id", id).toString(), "Updated block $id")
    }

    private suspend fun handleDeleteTimetableBlock(rawUri: String): Triple<Int, String, String> {
        val id = extractQueryParam(rawUri, "id")?.toIntOrNull()
            ?: return Triple(400, JSONObject().put("error", "'id' query parameter required").toString(), "Missing id")
        database.timetableDao().deleteBlockById(id)
        return Triple(200, JSONObject().put("success", true).put("deleted_id", id).toString(), "Deleted block $id")
    }

    private fun handleTriggerLoudAlarm(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val title = req.optString("title", "Wake Up / Follow Timetable")
        val message = req.optString("message", "Hermes agent scheduled alarm.")
        com.example.alarm.AlarmController.triggerAlarm(context, title, message)
        return Triple(200, JSONObject().put("success", true).put("alarm_triggered", true).put("title", title).toString(), "Triggered loud ringing alarm '$title'")
    }

    private suspend fun handleAgentOverview(): Triple<Int, String, String> {
        val cal = Calendar.getInstance()
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val blocks = database.timetableDao().getAllBlocksSync()
        val activeBlock = blocks.firstOrNull { currentMinute in it.startMinuteOfDay until it.endMinuteOfDay }
        val nextBlock = blocks.firstOrNull { it.startMinuteOfDay > currentMinute }

        val tasks = database.taskDao().getAllTasksSync()
        val pendingTasks = tasks.filter { !it.completed }
        val completedTasks = tasks.filter { it.completed }

        val usageSummary = usageStatsHelper.getTodayUsageSummary()
        val topApps = usageSummary.topApps.take(8)

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val statusInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL

        val isStudyBlockActive = activeBlock != null && (activeBlock.category.equals("Subject", true) || activeBlock.category.equals("Lab", true) || activeBlock.category.equals("Project", true))
        val totalScreenTimeMinutes = usageSummary.totalScreenTimeMs / 60000

        val screenTimeJson = JSONObject()
        screenTimeJson.put("total_minutes_today", totalScreenTimeMinutes)
        screenTimeJson.put("is_study_block_active", isStudyBlockActive)

        val topAppsArray = JSONArray()
        val packageBreakdown = JSONObject()
        val categoryBreakdown = mutableMapOf<String, Long>()

        for (app in topApps) {
            val appObj = JSONObject()
            val minutes = app.totalTimeInForegroundMs / 60000
            appObj.put("app_name", app.appName)
            appObj.put("package_name", app.packageName)
            appObj.put("minutes_used", minutes)
            appObj.put("formatted_duration", app.formattedDuration)
            appObj.put("category", app.category)
            topAppsArray.put(appObj)

            packageBreakdown.put(app.packageName, app.formattedDuration)
            categoryBreakdown[app.category] = (categoryBreakdown[app.category] ?: 0L) + minutes
        }
        screenTimeJson.put("top_apps", topAppsArray)
        screenTimeJson.put("breakdown_by_package", packageBreakdown)

        val catObj = JSONObject()
        for ((cat, mins) in categoryBreakdown) {
            catObj.put(cat, "${mins}m")
        }
        screenTimeJson.put("breakdown_by_category", catObj)

        val tasksSummaryJson = JSONObject()
        tasksSummaryJson.put("total_tasks", tasks.size)
        tasksSummaryJson.put("pending_count", pendingTasks.size)
        tasksSummaryJson.put("completed_count", completedTasks.size)

        val campusContext = com.example.hardware.HardwareController.getCampusContext(context)
        val strictFocusActive = com.example.focus.FocusController.isStrictFocusActive.value

        val json = JSONObject()
            .put("status", "success")
            .put("current_time_formatted", String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
            .put("current_minute_of_day", currentMinute)
            .put("battery_percent", batteryPct)
            .put("is_charging", isCharging)
            .put("is_strict_focus_active", strictFocusActive)
            .put("campus_context", campusContext)
            .put("active_block", activeBlock?.let { b ->
                JSONObject()
                    .put("id", b.id)
                    .put("day_of_week", b.dayOfWeek)
                    .put("title", b.title)
                    .put("subject_or_project", b.subjectOrProject)
                    .put("category", b.category)
                    .put("start_minute", b.startMinuteOfDay)
                    .put("end_minute", b.endMinuteOfDay)
                    .put("start_formatted", formatMinuteOfDay(b.startMinuteOfDay))
                    .put("end_formatted", formatMinuteOfDay(b.endMinuteOfDay))
                    .put("is_completed", b.isCompleted)
                    .put("notes", b.notes)
            } ?: JSONObject.NULL)
            .put("next_block", nextBlock?.let { b ->
                JSONObject()
                    .put("id", b.id)
                    .put("day_of_week", b.dayOfWeek)
                    .put("title", b.title)
                    .put("start_minute", b.startMinuteOfDay)
                    .put("end_minute", b.endMinuteOfDay)
                    .put("start_formatted", formatMinuteOfDay(b.startMinuteOfDay))
                    .put("end_formatted", formatMinuteOfDay(b.endMinuteOfDay))
            } ?: JSONObject.NULL)
            .put("screen_time", screenTimeJson)
            .put("tasks_summary", tasksSummaryJson)
            .put("timetable_block_count", blocks.size)

        return Triple(200, json.toString(), "Agent overview: active='${activeBlock?.title ?: "None"}', screenTime=${totalScreenTimeMinutes}m")
    }

    private suspend fun handleCreateTask(body: String): Triple<Int, String, String> {
        if (!securityManager.allowTaskManagement.value) {
            val err = JSONObject().put("error", "Capability 'allow_task_management' is disabled in user privacy settings").toString()
            return Triple(403, err, "Task management capability disabled")
        }
        val req = try { JSONObject(body) } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }
        val title = req.optString("title", "").trim()
        if (title.isBlank()) {
            return Triple(400, JSONObject().put("error", "'title' is required").toString(), "Empty task title")
        }
        val task = TaskEntity(
            title = title,
            description = req.optString("description", ""),
            dueDateEpochMs = req.optLong("due_date_epoch_ms", System.currentTimeMillis() + 86400000L),
            priority = req.optString("priority", "HIGH"),
            category = req.optString("category", "College"),
            completed = req.optBoolean("completed", false)
        )
        val id = database.taskDao().insertTask(task).toInt()
        val resp = JSONObject().put("success", true).put("task_id", id).put("title", title)
        return Triple(201, resp.toString(), "Created task '$title' (id=$id)")
    }

    private suspend fun handleUpdateTask(body: String): Triple<Int, String, String> {
        if (!securityManager.allowTaskManagement.value) {
            val err = JSONObject().put("error", "Capability 'allow_task_management' is disabled in user privacy settings").toString()
            return Triple(403, err, "Task management capability disabled")
        }
        val req = try { JSONObject(body) } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }
        val id = req.optInt("id", -1)
        if (id == -1) return Triple(400, JSONObject().put("error", "'id' is required").toString(), "Missing task id")

        val existing = database.taskDao().getTaskById(id)
            ?: return Triple(404, JSONObject().put("error", "Task $id not found").toString(), "Task not found")

        val updated = existing.copy(
            title = req.optString("title", existing.title),
            description = req.optString("description", existing.description),
            completed = req.optBoolean("completed", existing.completed),
            priority = req.optString("priority", existing.priority),
            category = req.optString("category", existing.category),
            dueDateEpochMs = req.optLong("due_date_epoch_ms", existing.dueDateEpochMs ?: (System.currentTimeMillis() + 86400000L))
        )
        database.taskDao().updateTask(updated)
        return Triple(200, JSONObject().put("success", true).put("task_id", id).put("completed", updated.completed).toString(), "Updated task $id")
    }

    private suspend fun handleDeleteTask(rawUri: String, body: String): Triple<Int, String, String> {
        if (!securityManager.allowTaskManagement.value) {
            val err = JSONObject().put("error", "Capability 'allow_task_management' is disabled in user privacy settings").toString()
            return Triple(403, err, "Task management capability disabled")
        }
        val id = extractQueryParam(rawUri, "id")?.toIntOrNull() ?: try {
            JSONObject(body).optInt("id", -1).takeIf { it != -1 }
        } catch (e: Exception) { null }

        if (id == null) {
            return Triple(400, JSONObject().put("error", "'id' query param or body field required").toString(), "Missing task id")
        }
        database.taskDao().deleteTaskById(id)
        return Triple(200, JSONObject().put("success", true).put("deleted_id", id).toString(), "Deleted task $id")
    }

    private suspend fun handleAddTimetableBlock(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) {
            return Triple(400, JSONObject().put("error", "Invalid JSON body").toString(), "Malformed JSON")
        }
        val title = req.optString("title", "").trim()
        if (title.isBlank()) return Triple(400, JSONObject().put("error", "'title' required").toString(), "Missing title")
        val block = TimetableBlockEntity(
            title = title,
            subjectOrProject = req.optString("subject_or_project", req.optString("subject", "")),
            category = req.optString("category", "Subject"),
            startMinuteOfDay = req.optInt("start_minute", 480),
            endMinuteOfDay = req.optInt("end_minute", 540),
            notes = req.optString("notes", ""),
            priority = req.optString("priority", "HIGH"),
            isAgentGenerated = true
        )
        val id = database.timetableDao().insertBlock(block).toInt()
        return Triple(201, JSONObject().put("success", true).put("block_id", id).put("title", title).toString(), "Added block '$title'")
    }

    private suspend fun handleClearTimetable(): Triple<Int, String, String> {
        database.timetableDao().clearAllBlocks()
        return Triple(200, JSONObject().put("success", true).toString(), "Cleared all timetable blocks")
    }

    private fun formatMinuteOfDay(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }

    private fun handleEnableStrictFocus(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val subject = req.optString("subject", "Deep Work Session")
        val duration = req.optInt("duration_minutes", 45)
        val notes = req.optString("notes", "")
        com.example.focus.FocusController.enableStrictFocus(context, subject, duration, notes)
        val resp = JSONObject().put("success", true).put("subject", subject).put("duration_minutes", duration)
        return Triple(200, resp.toString(), "Enabled strict focus for $subject")
    }

    private fun handleDisableStrictFocus(): Triple<Int, String, String> {
        com.example.focus.FocusController.disableStrictFocus(context)
        val resp = JSONObject().put("success", true)
        return Triple(200, resp.toString(), "Disabled strict focus")
    }

    private suspend fun handleSyncWeeklyTimetable(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val scheduleArray = req.optJSONArray("schedule") ?: req.optJSONArray("blocks")
            ?: return Triple(400, JSONObject().put("error", "'schedule' array required").toString(), "Missing schedule")

        val newBlocks = mutableListOf<TimetableBlockEntity>()
        for (i in 0 until scheduleArray.length()) {
            val item = scheduleArray.getJSONObject(i)
            if (item.has("blocks")) {
                val day = item.optInt("day_of_week", 1)
                val subBlocks = item.getJSONArray("blocks")
                for (j in 0 until subBlocks.length()) {
                    val b = subBlocks.getJSONObject(j)
                    newBlocks.add(
                        TimetableBlockEntity(
                            dayOfWeek = b.optInt("day_of_week", day),
                            title = b.getString("title"),
                            subjectOrProject = b.optString("subject", ""),
                            category = b.optString("category", "Subject"),
                            startMinuteOfDay = b.getInt("start_minute"),
                            endMinuteOfDay = b.getInt("end_minute"),
                            notes = b.optString("notes", ""),
                            priority = b.optString("priority", "HIGH"),
                            isAgentGenerated = true
                        )
                    )
                }
            } else {
                newBlocks.add(
                    TimetableBlockEntity(
                        dayOfWeek = item.optInt("day_of_week", 1),
                        title = item.getString("title"),
                        subjectOrProject = item.optString("subject", ""),
                        category = item.optString("category", "Subject"),
                        startMinuteOfDay = item.getInt("start_minute"),
                        endMinuteOfDay = item.getInt("end_minute"),
                        notes = item.optString("notes", ""),
                        priority = item.optString("priority", "HIGH"),
                        isAgentGenerated = true
                    )
                )
            }
        }
        if (newBlocks.isNotEmpty()) {
            database.timetableDao().clearAllBlocks()
            database.timetableDao().insertAll(newBlocks)
        }
        val resp = JSONObject().put("success", true).put("blocks_synced", newBlocks.size)
        return Triple(200, resp.toString(), "Batch weekly timetable synced (${newBlocks.size} blocks)")
    }

    private fun handleTelemetry(): Triple<Int, String, String> {
        val telemetry = com.example.hardware.HardwareController.getDeviceTelemetry(context)
        return Triple(200, telemetry.toString(), "Telemetry retrieved")
    }

    private fun handleAlarmRingtone(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val vol = req.optInt("volume_percent", 100)
        val title = req.optString("title", "Hermes Wake-Up Alarm")
        val msg = req.optString("message", "Time for scheduled block.")
        com.example.hardware.HardwareController.playAlarmRingtone(context, vol)
        com.example.alarm.AlarmController.triggerAlarm(context, title, msg)
        val resp = JSONObject().put("success", true).put("volume_percent", vol)
        return Triple(200, resp.toString(), "Triggered alarm ringtone with STREAM_ALARM")
    }

    private fun handleAlarmStop(): Triple<Int, String, String> {
        com.example.hardware.HardwareController.stopAlarmRingtone()
        com.example.alarm.AlarmController.stopAlarm(context)
        val resp = JSONObject().put("success", true)
        return Triple(200, resp.toString(), "Stopped alarm ringtone")
    }

    private fun handleCameraSnap(): Triple<Int, String, String> {
        var resultStr = ""
        var isSuccess = false
        var base64Data: String? = null
        val lock = Object()
        com.example.hardware.HardwareController.silentCapturePhoto(context) { succ, msg, b64 ->
            synchronized(lock) {
                isSuccess = succ
                resultStr = msg
                base64Data = b64
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            lock.wait(5000)
        }
        val resp = JSONObject().put("success", isSuccess).put("message", resultStr)
        if (base64Data != null) {
            resp.put("base64_jpeg", base64Data)
        }
        return Triple(if (isSuccess) 200 else 500, resp.toString(), resultStr)
    }

    private fun handleAudioRecord(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val dur = req.optInt("duration_seconds", 5)
        var resultStr = ""
        var isSuccess = false
        var base64Data: String? = null
        val lock = Object()
        com.example.hardware.HardwareController.recordAudioClip(context, dur) { succ, msg, b64 ->
            synchronized(lock) {
                isSuccess = succ
                resultStr = msg
                base64Data = b64
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            lock.wait((dur + 2) * 1000L)
        }
        val resp = JSONObject().put("success", isSuccess).put("message", resultStr)
        if (base64Data != null) {
            resp.put("base64_audio", base64Data)
        }
        return Triple(if (isSuccess) 200 else 500, resp.toString(), resultStr)
    }

    private fun handleCampusContext(): Triple<Int, String, String> {
        val ctx = com.example.hardware.HardwareController.getCampusContext(context)
        return Triple(200, ctx.toString(), "Campus context retrieved")
    }

    private fun handleSetVolume(body: String): Triple<Int, String, String> {
        val req = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val stream = req.optString("stream", "alarm")
        val pct = req.optInt("volume_percent", 80)
        val success = com.example.hardware.HardwareController.setDeviceVolume(context, stream, pct)
        val resp = JSONObject().put("success", success).put("stream", stream).put("volume_percent", pct)
        return Triple(200, resp.toString(), "Set volume for $stream to $pct%")
    }

    // --- Helpers ---

    private fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return null
        val pairs = query.split('&')
        for (pair in pairs) {
            val kv = pair.split('=', limit = 2)
            if (kv.isNotEmpty() && kv[0] == key) {
                return if (kv.size > 1) kv[1] else ""
            }
        }
        return null
    }

    private fun sendCorsResponse(output: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendResponse(output: OutputStream, code: Int, statusText: String, bodyJson: String) {
        val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun getStatusText(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    private fun handleAppCustomize(body: String): Triple<Int, String, String> {
        val appCustomization = (context.applicationContext as? com.example.FocusPilotApp)?.appCustomizationManager
            ?: com.example.util.AppCustomizationManager(context)
        return try {
            val json = JSONObject(body)
            val themeColor = json.optString("theme_color", json.optString("primary_color", ""))
            val themeMode = json.optString("theme_mode", "")
            val banner = json.optString("app_banner_text", json.optString("banner_text", ""))
            val studentName = json.optString("student_name", "")
            val profile = json.optString("active_profile", "")
            val quote = json.optString("custom_quote", "")
            val rulesArray = json.optJSONArray("custom_rules")
            val rulesList = if (rulesArray != null) {
                val list = mutableListOf<String>()
                for (i in 0 until rulesArray.length()) list.add(rulesArray.getString(i))
                list
            } else null

            appCustomization.applyCustomization(
                themeColorHex = if (themeColor.isNotBlank()) themeColor else null,
                themeMode = if (themeMode.isNotBlank()) themeMode else null,
                bannerText = if (banner.isNotBlank()) banner else null,
                studentName = if (studentName.isNotBlank()) studentName else null,
                activeProfile = if (profile.isNotBlank()) profile else null,
                customQuote = if (quote.isNotBlank()) quote else null,
                customRulesList = rulesList
            )

            val res = JSONObject()
                .put("status", "success")
                .put("message", "App customization applied successfully")
                .put("current_config", appCustomization.getAsJsonObject())
            Triple(200, res.toString(), "Customized app UI & rules")
        } catch (e: Exception) {
            Triple(400, JSONObject().put("error", e.message).toString(), "Failed to customize app")
        }
    }

    private fun handleGetAppCustomization(): Triple<Int, String, String> {
        val appCustomization = (context.applicationContext as? com.example.FocusPilotApp)?.appCustomizationManager
            ?: com.example.util.AppCustomizationManager(context)
        val json = JSONObject()
            .put("status", "success")
            .put("customization", appCustomization.getAsJsonObject())
        return Triple(200, json.toString(), "Retrieved app customization")
    }

    private fun handleUsageTimeframe(rawUri: String): Triple<Int, String, String> {
        val timeframeParam = rawUri.substringAfter("timeframe=", "day").substringBefore("&").lowercase()
        val timeframe = when (timeframeParam) {
            "week" -> com.example.util.UsageTimeframe.WEEK
            "month" -> com.example.util.UsageTimeframe.MONTH
            else -> com.example.util.UsageTimeframe.DAY
        }
        val summary = usageStatsHelper.getTimeframeUsageSummary(timeframe)
        val json = JSONObject()
            .put("status", "success")
            .put("timeframe", timeframe.name)
            .put("has_permission", summary.hasPermission)
            .put("total_screen_time_ms", summary.totalScreenTimeMs)
            .put("formatted_total_time", summary.formattedTotalTime)
            .put("daily_average_ms", summary.dailyAverageMs)
            .put("formatted_daily_average", summary.formattedDailyAverage)
            .put("productive_minutes", summary.productiveMinutes)
            .put("distraction_minutes", summary.distractionMinutes)
            .put("app_count", summary.appCount)

        val appsArray = JSONArray()
        for (app in summary.topApps) {
            appsArray.put(
                JSONObject()
                    .put("package_name", app.packageName)
                    .put("app_name", app.appName)
                    .put("category", app.category)
                    .put("duration_ms", app.totalTimeInForegroundMs)
                    .put("formatted_duration", app.formattedDuration)
                    .put("minutes", app.totalTimeInForegroundMs / 60000)
            )
        }
        json.put("top_apps", appsArray)

        val catObj = JSONObject()
        for ((cat, mins) in summary.categoryDistribution) {
            catObj.put(cat, mins)
        }
        json.put("category_distribution_minutes", catObj)

        return Triple(200, json.toString(), "Retrieved ${timeframe.name} usage summary")
    }

    private fun recordAuditLog(
        clientIp: String,
        endpoint: String,
        method: String,
        statusCode: Int,
        requestSummary: String,
        responseSummary: String,
        isBlocked: Boolean
    ) {
        scope.launch {
            try {
                database.auditLogDao().insertLog(
                    AuditLogEntity(
                        clientIp = clientIp,
                        endpoint = endpoint,
                        method = method,
                        statusCode = statusCode,
                        requestSummary = requestSummary,
                        responseSummary = responseSummary,
                        isBlocked = isBlocked
                    )
                )
            } catch (ignored: Exception) {}
        }
    }
}
