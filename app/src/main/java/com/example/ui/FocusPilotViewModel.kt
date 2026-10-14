package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusPilotApp
import com.example.data.local.entity.ApprovedFileEntity
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TimetableBlockEntity
import com.example.receiver.ReminderScheduler
import com.example.service.PhoneApiService
import com.example.util.FileReadResult
import com.example.util.NetworkUtils
import com.example.util.NotificationHelper
import com.example.util.StorageAccessHelper
import com.example.util.UsageStatsHelper
import com.example.util.UsageSummary
import com.example.alarm.AlarmController
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusPilotViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusPilotApp
    private val db = app.database
    val securityManager = app.securityManager
    val cloudRelayManager = com.example.relay.CloudRelayManager(app, securityManager, db)

    private val usageStatsHelper = UsageStatsHelper(app)
    private val storageAccessHelper = StorageAccessHelper(app, securityManager)
    private val notificationHelper = NotificationHelper(app, securityManager)
    private val reminderScheduler = ReminderScheduler(app)
    val appCustomizationManager = app.appCustomizationManager

    val themeColorHex = appCustomizationManager.themeColorHex
    val themeMode = appCustomizationManager.themeMode
    val appBannerText = appCustomizationManager.appBannerText
    val studentName = appCustomizationManager.studentName
    val activeProfile = appCustomizationManager.activeProfile
    val customQuote = appCustomizationManager.customQuote
    val customRules = appCustomizationManager.customRules

    enum class ScreenTimePeriod { DAY, WEEK, MONTH }

    private val _selectedScreenTimePeriod = MutableStateFlow(ScreenTimePeriod.DAY)
    val selectedScreenTimePeriod: StateFlow<ScreenTimePeriod> = _selectedScreenTimePeriod.asStateFlow()

    val isServerRunning: StateFlow<Boolean> = PhoneApiService.isServerRunning

    private val _usageSummary = MutableStateFlow(usageStatsHelper.getTodayUsageSummary())
    val usageSummary: StateFlow<UsageSummary> = _usageSummary.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _networkInterfaces = MutableStateFlow(NetworkUtils.getNetworkInterfacesSummary())
    val networkInterfaces: StateFlow<List<Pair<String, String>>> = _networkInterfaces.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = db.taskDao().getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = db.reminderDao().getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val approvedFiles: StateFlow<List<ApprovedFileEntity>> = db.approvedFileDao().getAllApprovedFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = db.auditLogDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timetableBlocks: StateFlow<List<com.example.data.local.entity.TimetableBlockEntity>> = db.timetableDao().getAllBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Auto-refresh usage stats and state periodically so screen time updates in real-time
        viewModelScope.launch(Dispatchers.IO) {
            // Check if timetable needs initial 7-day seed
            val existing = db.timetableDao().getAllBlocksSync()
            if (existing.isEmpty()) {
                seedStudent24hTimetable()
            }
            while (isActive) {
                refreshState()
                delay(15000) // 15-second screen time polling
            }
        }
    }

    fun selectScreenTimePeriod(period: ScreenTimePeriod) {
        _selectedScreenTimePeriod.value = period
        refreshState()
    }

    fun refreshUsageStats() {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch(Dispatchers.IO) {
            val tf = when (_selectedScreenTimePeriod.value) {
                ScreenTimePeriod.DAY -> com.example.util.UsageTimeframe.DAY
                ScreenTimePeriod.WEEK -> com.example.util.UsageTimeframe.WEEK
                ScreenTimePeriod.MONTH -> com.example.util.UsageTimeframe.MONTH
            }
            _usageSummary.value = usageStatsHelper.getTimeframeUsageSummary(tf)
            _localIp.value = NetworkUtils.getLocalIpAddress()
            _networkInterfaces.value = NetworkUtils.getNetworkInterfacesSummary()
        }
    }

    fun startServer() {
        PhoneApiService.startService(app)
        refreshState()
    }

    fun stopServer() {
        PhoneApiService.stopService(app)
    }

    fun toggleKillSwitch(enabled: Boolean) {
        securityManager.setRemoteAccessEnabled(enabled)
    }

    fun regenerateToken(): String {
        return securityManager.regenerateApiToken()
    }

    fun setApiToken(token: String) {
        securityManager.setApiToken(token)
    }

    fun setCapability(capability: String, enabled: Boolean) {
        when (capability) {
            "usage" -> securityManager.setAllowUsageStats(enabled)
            "files" -> securityManager.setAllowFileAccess(enabled)
            "launch" -> securityManager.setAllowAppLaunch(enabled)
            "tasks" -> securityManager.setAllowTaskManagement(enabled)
            "alerts" -> securityManager.setAllowAlerts(enabled)
        }
    }

    fun setServerPort(port: Int) {
        securityManager.setServerPort(port)
        if (isServerRunning.value) {
            // Restart service with new port
            PhoneApiService.stopService(app)
            PhoneApiService.startService(app)
        }
    }

    fun setNtfyUrl(url: String) {
        securityManager.setNtfyUrl(url)
    }

    val customMcpUrl: StateFlow<String> = securityManager.customMcpUrl
    val pairingPin: StateFlow<String> = securityManager.pairingPin

    fun setCustomMcpUrl(url: String) {
        securityManager.setCustomMcpUrl(url)
    }

    val apiToken: StateFlow<String> = securityManager.apiToken

    fun regenerateApiToken(): String {
        return securityManager.regenerateApiToken()
    }

    fun regeneratePairingPin(): String {
        return securityManager.regeneratePairingPin()
    }

    fun testTriggerAlarm(title: String = "Test Loud Alarm", message: String = "MCP and Cloud Relay wake-up verification") {
        AlarmController.triggerAlarm(app, title, message)
    }

    fun testCloudRelayAlarm(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val relayUrl = securityManager.ntfyUrl.value
                val token = securityManager.apiToken.value
                if (relayUrl.isBlank()) {
                    onResult(false, "No Cloud Relay URL configured")
                    return@launch
                }
                val payload = org.json.JSONObject().apply {
                    put("action", "trigger_alarm")
                    put("title", "Hermes Cellular Test Alarm")
                    put("message", "Continuous ring & vibration verified over mobile data!")
                    put("token", token)
                }
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(relayUrl)
                    .header("Title", "FocusPilot Test Alarm")
                    .header("Priority", "urgent")
                    .header("Tags", "alarm,robot")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val code = response.code
                if (response.isSuccessful) {
                    onResult(true, "Alarm dispatched to relay: $relayUrl")
                } else {
                    onResult(false, "Relay returned HTTP $code")
                }
                response.close()
            } catch (e: Exception) {
                onResult(false, "Failed to send: ${e.message}")
            }
        }
    }

    fun syncPhoneWithMcpServer(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mcpBase = securityManager.customMcpUrl.value
                    .replace("/sse", "")
                    .replace("/messages", "")
                    .trimEnd('/')
                if (mcpBase.isBlank()) {
                    onResult(false, "No MCP Server URL set")
                    return@launch
                }
                val token = securityManager.apiToken.value
                val syncUrl = "$mcpBase/api/phone/sync"

                val telemetry = com.example.hardware.HardwareController.getDeviceTelemetry(app)
                val batteryPct = telemetry.optInt("battery_percent", 88)
                val isCharging = telemetry.optBoolean("is_charging", false)

                val daySummary = usageStatsHelper.getTimeframeUsageSummary(com.example.util.UsageTimeframe.DAY)
                val weekSummary = usageStatsHelper.getTimeframeUsageSummary(com.example.util.UsageTimeframe.WEEK)
                val monthSummary = usageStatsHelper.getTimeframeUsageSummary(com.example.util.UsageTimeframe.MONTH)

                val timetableBlocksList = db.timetableDao().getAllBlocksSync()
                val tasksList = db.taskDao().getAllTasksSync()

                val payload = org.json.JSONObject().apply {
                    put("token", token)
                    put("relay_url", securityManager.ntfyUrl.value)
                    put("account", "jossrichard18@gmail.com")
                    put("device", "Android Phone (FocusPilot)")
                    put("battery_percent", batteryPct)
                    put("is_charging", isCharging)
                    put("status", "online")

                    // Live real screen time data
                    put("screen_time_day_minutes", daySummary.totalScreenTimeMs / 60000)
                    put("screen_time_week_minutes", weekSummary.totalScreenTimeMs / 60000)
                    put("screen_time_month_minutes", monthSummary.totalScreenTimeMs / 60000)
                    put("screen_time_day_formatted", daySummary.formattedTotalTime)
                    put("screen_time_week_formatted", weekSummary.formattedTotalTime)
                    put("screen_time_month_formatted", monthSummary.formattedTotalTime)
                    put("screen_time_daily_average_week", weekSummary.formattedDailyAverage)
                    put("screen_time_daily_average_month", monthSummary.formattedDailyAverage)
                    put("screen_time_productive_minutes", daySummary.productiveMinutes)
                    put("screen_time_distraction_minutes", daySummary.distractionMinutes)

                    val appsArr = org.json.JSONArray()
                    for (appItem in daySummary.topApps) {
                        appsArr.put(org.json.JSONObject().apply {
                            put("package_name", appItem.packageName)
                            put("app_name", appItem.appName)
                            put("minutes", appItem.totalTimeInForegroundMs / 60000)
                            put("formatted_duration", appItem.formattedDuration)
                            put("category", appItem.category)
                        })
                    }
                    put("top_apps", appsArr)

                    // Timetable and Tasks
                    val ttArr = org.json.JSONArray()
                    for (b in timetableBlocksList) {
                        ttArr.put(org.json.JSONObject().apply {
                            put("id", b.id)
                            put("day_of_week", b.dayOfWeek)
                            put("title", b.title)
                            put("subject", b.subjectOrProject)
                            put("category", b.category)
                            put("start_minute", b.startMinuteOfDay)
                            put("end_minute", b.endMinuteOfDay)
                            put("notes", b.notes)
                        })
                    }
                    put("timetable", ttArr)

                    val tskArr = org.json.JSONArray()
                    for (t in tasksList) {
                        tskArr.put(org.json.JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("priority", t.priority)
                            put("completed", t.completed)
                        })
                    }
                    put("tasks", tskArr)

                    // Live Customization
                    put("customization", app.appCustomizationManager.getAsJsonObject())
                }
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url(syncUrl)
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val code = response.code
                response.close()
                onResult(isSuccess, if (isSuccess) "Phone synced successfully with MCP Server!" else "Sync returned HTTP $code")
            } catch (e: Exception) {
                onResult(false, "Sync failed: ${e.message}")
            }
        }
    }

    // --- SAF File Actions ---
    fun approveFileUri(uri: Uri, isDirectory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            storageAccessHelper.persistUriPermission(uri)
            val info = storageAccessHelper.resolveFileInfo(uri, isDirectory)
            val entity = ApprovedFileEntity(
                uriString = info.uriString,
                displayName = info.displayName,
                mimeType = info.mimeType,
                isDirectory = info.isDirectory,
                sizeBytes = info.sizeBytes,
                permissionLevel = "READ_ONLY"
            )
            db.approvedFileDao().insert(entity)
        }
    }

    fun revokeApprovedFile(file: ApprovedFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            storageAccessHelper.releaseUriPermission(Uri.parse(file.uriString))
            db.approvedFileDao().delete(file)
        }
    }

    fun readFileContent(file: ApprovedFileEntity, onResult: (FileReadResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = storageAccessHelper.readFileSafely(file)
            viewModelScope.launch(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    // --- Task Actions ---
    fun addTask(title: String, description: String, priority: String, category: String, dueDate: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = TaskEntity(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                category = category.trim().ifEmpty { "General" },
                dueDateEpochMs = dueDate
            )
            db.taskDao().insertTask(task)
        }
    }

    fun toggleTask(id: Int, completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.taskDao().setTaskCompleted(id, completed)
            // Bi-directional sync webhook: notify Cloud Relay / Hermes agent
            try {
                val relayUrl = securityManager.ntfyUrl.value
                val token = securityManager.apiToken.value
                if (relayUrl.isNotBlank()) {
                    val payload = org.json.JSONObject().apply {
                        put("action", "task_status_changed")
                        put("task_id", id)
                        put("completed", completed)
                        put("token", token)
                        put("timestamp_epoch_ms", System.currentTimeMillis())
                    }
                    val client = okhttp3.OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payload.toString().toRequestBody(mediaType)
                    val request = okhttp3.Request.Builder()
                        .url(relayUrl)
                        .header("Title", "FocusPilot Task Sync")
                        .header("Tags", "white_check_mark,clipboard")
                        .post(body)
                        .build()
                    client.newCall(request).execute().close()
                }
            } catch (ignored: Exception) {}
        }
    }

    fun seedStudent24hTimetable() {
        viewModelScope.launch(Dispatchers.IO) {
            db.timetableDao().clearAllBlocks()
            val sample = mutableListOf<com.example.data.local.entity.TimetableBlockEntity>()

            // Monday (1)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 1, title = "Morning Wake Up & Cardio", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Cardio & hydration"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Data Structures & Algorithms", subjectOrProject = "CS201", category = "Subject", startMinuteOfDay = 8 * 60 + 30, endMinuteOfDay = 10 * 60, notes = "Trees & Dynamic Programming"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Operating Systems Lab", subjectOrProject = "CS302 Lab", category = "Subject", startMinuteOfDay = 10 * 60 + 30, endMinuteOfDay = 12 * 60 + 30, notes = "Process Scheduling & Semaphores"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Lunch & Rest", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 13 * 60 + 30, notes = "Hostel mess"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Hermes AI Agent Sprint", subjectOrProject = "Major Project", category = "Project", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60 + 30, notes = "Pi bridge integration"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Evening Gym & Walk", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 18 * 60, endMinuteOfDay = 19 * 60 + 15, notes = "Upper body workout"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Assignment Review & Daily Wrap", subjectOrProject = "Academic", category = "Subject", startMinuteOfDay = 20 * 60 + 30, endMinuteOfDay = 22 * 60, notes = "Review day notes"),
                TimetableBlockEntity(dayOfWeek = 1, title = "Sleep & Recovery", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "8h sleep")
            ))

            // Tuesday (2) - Specific user classes: 25EC211 Probability & Math Defense & NeuroPath
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 2, title = "Morning Awakening & Stretch", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Hydration"),
                TimetableBlockEntity(dayOfWeek = 2, title = "25EC211 Probability & Random Processes", subjectOrProject = "Math Dept", category = "Subject", startMinuteOfDay = 8 * 60 + 40, endMinuteOfDay = 10 * 60 + 15, notes = "Bayes theorem & Distributions"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Analog Electronics Lecture", subjectOrProject = "EC204", category = "Subject", startMinuteOfDay = 10 * 60 + 30, endMinuteOfDay = 12 * 60 + 30, notes = "Op-Amp filters"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Lunch Break", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 13 * 60 + 30, notes = "Campus cafeteria"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Microcontroller Hardware Lab", subjectOrProject = "EC212", category = "Subject", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60 + 30, notes = "ARM Cortex peripherals"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Math Defense Deep Work", subjectOrProject = "Defense Prep", category = "Project", startMinuteOfDay = 17 * 60 + 45, endMinuteOfDay = 19 * 60, notes = "Strict focus on calculus problems"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Dinner & Campus Fellowship", subjectOrProject = "Social", category = "Routine", startMinuteOfDay = 19 * 60 + 30, endMinuteOfDay = 20 * 60 + 30, notes = "Hostel"),
                TimetableBlockEntity(dayOfWeek = 2, title = "NeuroPath Study Session", subjectOrProject = "NeuroPath", category = "Subject", startMinuteOfDay = 20 * 60 + 40, endMinuteOfDay = 22 * 60 + 30, notes = "Neural networks & cognitive models"),
                TimetableBlockEntity(dayOfWeek = 2, title = "Sleep & Recovery", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "Rest")
            ))

            // Wednesday (3)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 3, title = "Morning Cardio Run", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Track run"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Computer Architecture & Organization", subjectOrProject = "CS301", category = "Subject", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60 + 30, notes = "Pipelining & Caches"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Signals & Systems Lab", subjectOrProject = "EC205", category = "Subject", startMinuteOfDay = 11 * 60, endMinuteOfDay = 13 * 60, notes = "FFT & MATLAB"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Lunch & Recharge", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 13 * 60, endMinuteOfDay = 14 * 60, notes = "Hostel"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Autonomous Agent Coding Sprint", subjectOrProject = "Capstone", category = "Project", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60 + 30, notes = "Hermes Tool Calling"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Campus Sports & Football", subjectOrProject = "Athletics", category = "Health", startMinuteOfDay = 17 * 60 + 30, endMinuteOfDay = 19 * 60, notes = "Karunya main ground"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Weekly Assignment Submission", subjectOrProject = "Academic", category = "Subject", startMinuteOfDay = 20 * 60 + 30, endMinuteOfDay = 22 * 60, notes = "Moodle portal uploads"),
                TimetableBlockEntity(dayOfWeek = 3, title = "Sleep & Rest", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "Sleep")
            ))

            // Thursday (4)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 4, title = "Morning Awakening & Gym", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Leg day"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Digital Signal Processing", subjectOrProject = "EC303", category = "Subject", startMinuteOfDay = 8 * 60 + 30, endMinuteOfDay = 10 * 60, notes = "IIR/FIR Filters"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Linear Algebra & Optimization", subjectOrProject = "MA202", category = "Subject", startMinuteOfDay = 10 * 60 + 30, endMinuteOfDay = 12 * 60 + 30, notes = "Eigenvalues & SVD"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Lunch Break", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 13 * 60 + 30, notes = "Cafeteria"),
                TimetableBlockEntity(dayOfWeek = 4, title = "VLSI Circuit Design Lab", subjectOrProject = "EC311", category = "Subject", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60 + 30, notes = "Cadence tool simulations"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Evening Walk & Music", subjectOrProject = "Rest", category = "Break", startMinuteOfDay = 18 * 60, endMinuteOfDay = 19 * 60, notes = "No screen"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Research Paper Reading Block", subjectOrProject = "Research", category = "Project", startMinuteOfDay = 20 * 60 + 30, endMinuteOfDay = 22 * 60 + 15, notes = "Arxiv AI papers"),
                TimetableBlockEntity(dayOfWeek = 4, title = "Sleep & Recovery", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "Sleep")
            ))

            // Friday (5)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 5, title = "Morning Yoga & Meditation", subjectOrProject = "Wellness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Breathing exercises"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Database Management Systems", subjectOrProject = "CS204", category = "Subject", startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60 + 30, notes = "Transactions & Indexing"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Software Engineering Principles", subjectOrProject = "CS206", category = "Subject", startMinuteOfDay = 11 * 60, endMinuteOfDay = 12 * 60 + 30, notes = "Design Patterns"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Lunch Break", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 13 * 60 + 30, notes = "Mess"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Capstone Team Review & Demo", subjectOrProject = "Project Demo", category = "Project", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60 + 30, notes = "Team sync with mentor"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Campus Worship / Fellowship", subjectOrProject = "Chapel", category = "Routine", startMinuteOfDay = 17 * 60 + 30, endMinuteOfDay = 19 * 60 + 30, notes = "Karunya Chapel"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Weekend Study Planning", subjectOrProject = "Planning", category = "Routine", startMinuteOfDay = 20 * 60 + 45, endMinuteOfDay = 22 * 60, notes = "Prepare Saturday targets"),
                TimetableBlockEntity(dayOfWeek = 5, title = "Sleep", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "Rest")
            ))

            // Saturday (6)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 6, title = "Morning Long Run & Hills", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60 + 30, endMinuteOfDay = 8 * 60 + 45, notes = "Campus hill trail"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Deep Work Hackathon Sprint", subjectOrProject = "Autonomous System", category = "Project", startMinuteOfDay = 10 * 60, endMinuteOfDay = 13 * 60, notes = "Deep focus coding"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Lunch & Chill", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 13 * 60, endMinuteOfDay = 14 * 60, notes = "Hostel"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Library Self-Study & Revision", subjectOrProject = "Exam Prep", category = "Subject", startMinuteOfDay = 14 * 60 + 30, endMinuteOfDay = 17 * 60, notes = "Central Library silence zone"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Evening Social & Recreation", subjectOrProject = "Rest", category = "Break", startMinuteOfDay = 18 * 60, endMinuteOfDay = 20 * 60, notes = "Free time"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Project Documentation & Git Commit", subjectOrProject = "Dev", category = "Project", startMinuteOfDay = 20 * 60 + 30, endMinuteOfDay = 22 * 60, notes = "Clean repos"),
                TimetableBlockEntity(dayOfWeek = 6, title = "Sleep", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "Sleep")
            ))

            // Sunday (7)
            sample.addAll(listOf(
                TimetableBlockEntity(dayOfWeek = 7, title = "Morning Awakening & Quiet Time", subjectOrProject = "Mindfulness", category = "Health", startMinuteOfDay = 7 * 60 + 30, endMinuteOfDay = 8 * 60 + 30, notes = "Reflection"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Karunya Campus Worship Service", subjectOrProject = "Worship", category = "Routine", startMinuteOfDay = 9 * 60, endMinuteOfDay = 11 * 60 + 30, notes = "Sunday Service"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Sunday Feast Lunch", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 14 * 60, notes = "Mess"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Weekly Overview & Hermes Synchronization", subjectOrProject = "AI Assistant", category = "Project", startMinuteOfDay = 15 * 60, endMinuteOfDay = 17 * 60, notes = "Sync 7-day schedule"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Campus Sunset Walk", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 17 * 60 + 30, endMinuteOfDay = 18 * 60 + 45, notes = "Scenic walk"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Next Week Goal Setting & Pack Bag", subjectOrProject = "Organization", category = "Routine", startMinuteOfDay = 20 * 60, endMinuteOfDay = 21 * 60 + 30, notes = "Monday readiness"),
                TimetableBlockEntity(dayOfWeek = 7, title = "Early Sleep & Recovery", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 22 * 60 + 30, endMinuteOfDay = 24 * 60, notes = "Recharge for Monday")
            ))

            db.timetableDao().insertAll(sample)
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.auditLogDao().clearLogs()
        }
    }

    fun sendTestAlert(title: String = "FocusPilot Alert", message: String = "Test notification from agent") {
        notificationHelper.sendLocalAlert(title, message)
    }

    fun triggerLoudAlarmNow(
        title: String = "Wake Up / Follow Timetable",
        message: String = "Hermes alarm check: verify full ringing screen."
    ) {
        com.example.hardware.HardwareController.playAlarmRingtone(app, 100)
        com.example.alarm.AlarmController.triggerAlarm(app, title, message)
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.taskDao().deleteTask(task)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.taskDao().deleteTaskById(id)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderScheduler.cancelReminder(reminder.id)
            db.reminderDao().deleteReminder(reminder)
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderScheduler.cancelReminder(id)
            db.reminderDao().deleteReminderById(id)
        }
    }

    fun scheduleReminder(title: String, message: String, delaySeconds: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
            val reminder = ReminderEntity(
                title = title.trim(),
                message = message.trim(),
                triggerTimeEpochMs = triggerTime
            )
            val id = db.reminderDao().insertReminder(reminder).toInt()
            reminderScheduler.scheduleReminder(id, title, message, triggerTime)
        }
    }

    fun deleteTimetableBlock(block: TimetableBlockEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.timetableDao().deleteBlockById(block.id)
        }
    }

    fun deleteTimetableBlock(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.timetableDao().deleteBlockById(id)
        }
    }

    fun addOrUpdateTimetableBlock(block: TimetableBlockEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (block.id == 0) {
                db.timetableDao().insertBlock(block)
            } else {
                db.timetableDao().updateBlock(block)
            }
        }
    }

    fun hasUsagePermission(): Boolean = usageStatsHelper.hasUsagePermission()
    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExactAlarms()
}
