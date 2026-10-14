package com.example.relay

import android.content.Context
import android.util.Log
import com.example.alarm.AlarmController
import com.example.data.local.AppDatabase
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TimetableBlockEntity
import com.example.security.SecurityManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class CloudRelayManager(
    private val context: Context,
    private val securityManager: SecurityManager,
    private val database: AppDatabase
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val notificationHelper = NotificationHelper(context, securityManager)
    private var listenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRelayConnected = MutableStateFlow(false)
    val isRelayConnected: StateFlow<Boolean> = _isRelayConnected.asStateFlow()

    private val _lastMessageReceived = MutableStateFlow("")
    val lastMessageReceived: StateFlow<String> = _lastMessageReceived.asStateFlow()

    fun startListening() {
        stopListening()
        listenerJob = scope.launch {
            while (isActive) {
                val ntfyUrl = securityManager.ntfyUrl.value
                if (ntfyUrl.isBlank()) {
                    _isRelayConnected.value = false
                    delay(5000)
                    continue
                }

                try {
                    // Normalize to json stream endpoint (e.g. https://ntfy.sh/<topic>/json)
                    val streamUrl = if (ntfyUrl.endsWith("/json")) ntfyUrl else "${ntfyUrl.trimEnd('/')}/json"
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Accept", "text/event-stream")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        _isRelayConnected.value = true
                        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                        var line: String? = null
                        while (isActive && reader.readLine().also { line = it } != null) {
                            val currentLine = line
                            if (!currentLine.isNullOrBlank()) {
                                handleRelayMessage(currentLine)
                            }
                        }
                    } else {
                        _isRelayConnected.value = false
                        response.close()
                    }
                } catch (e: Exception) {
                    _isRelayConnected.value = false
                }
                delay(4000) // Reconnect delay
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
        _isRelayConnected.value = false
    }

    private suspend fun handleRelayMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)
            val event = root.optString("event", "")
            if (event != "message") return

            val messageText = root.optString("message", "")
            val title = root.optString("title", "Hermes Agent Alert")

            _lastMessageReceived.value = "$title: $messageText"

            // Check if payload is structured JSON command from Hermes
            if (messageText.trim().startsWith("{") && messageText.trim().endsWith("}")) {
                val command = JSONObject(messageText)
                val action = command.optString("action", "")

                when (action) {
                    "trigger_alarm", "play_alarm_ringtone" -> {
                        val alarmTitle = command.optString("title", title)
                        val alarmMsg = command.optString("message", "Wake up or follow scheduled task.")
                        val vol = command.optInt("volume_percent", 100)
                        com.example.hardware.HardwareController.playAlarmRingtone(context, vol)
                        AlarmController.triggerAlarm(context, alarmTitle, alarmMsg)
                    }
                    "stop_alarm" -> {
                        com.example.hardware.HardwareController.stopAlarmRingtone()
                        AlarmController.stopAlarm(context)
                    }
                    "enable_strict_focus_mode" -> {
                        val subject = command.optString("subject", "Deep Work Session")
                        val duration = command.optInt("duration_minutes", 45)
                        val notes = command.optString("notes", "")
                        com.example.focus.FocusController.enableStrictFocus(context, subject, duration, notes)
                        notificationHelper.sendLocalAlert("Strict Focus Activated", "Hermes locked distractions for $subject ($duration min)")
                    }
                    "disable_strict_focus_mode" -> {
                        com.example.focus.FocusController.disableStrictFocus(context)
                        notificationHelper.sendLocalAlert("Focus Mode Disengaged", "Normal device permissions restored")
                    }
                    "sync_weekly_timetable" -> {
                        // Batch 7-day schedule sync
                        val scheduleArray = command.optJSONArray("schedule") ?: command.optJSONArray("blocks")
                        if (scheduleArray != null) {
                            val newBlocks = mutableListOf<TimetableBlockEntity>()
                            for (i in 0 until scheduleArray.length()) {
                                val item = scheduleArray.getJSONObject(i)
                                if (item.has("blocks")) {
                                    // Grouped by day: { "day_of_week": 1, "blocks": [...] }
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
                                    // Flat list with day_of_week
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
                                notificationHelper.sendLocalAlert(
                                    title = "Weekly Timetable Synced",
                                    message = "Hermes updated 7-day schedule (${newBlocks.size} blocks loaded across Mon-Sun)."
                                )
                            }
                        }
                    }
                    "update_timetable" -> {
                        val blocksArray = command.optJSONArray("blocks")
                        if (blocksArray != null) {
                            val newBlocks = mutableListOf<TimetableBlockEntity>()
                            for (i in 0 until blocksArray.length()) {
                                val b = blocksArray.getJSONObject(i)
                                newBlocks.add(
                                    TimetableBlockEntity(
                                        dayOfWeek = b.optInt("day_of_week", 1),
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
                            if (newBlocks.isNotEmpty()) {
                                database.timetableDao().clearAllBlocks()
                                database.timetableDao().insertAll(newBlocks)
                                notificationHelper.sendLocalAlert(
                                    title = "Timetable Updated",
                                    message = "Hermes optimized your schedule with ${newBlocks.size} blocks."
                                )
                            }
                        }
                    }
                    "add_timetable_block" -> {
                        val blockTitle = command.optString("title", "New Session")
                        val block = TimetableBlockEntity(
                            dayOfWeek = command.optInt("day_of_week", 1),
                            title = blockTitle,
                            subjectOrProject = command.optString("subject", ""),
                            category = command.optString("category", "Subject"),
                            startMinuteOfDay = command.optInt("start_minute", 480),
                            endMinuteOfDay = command.optInt("end_minute", 540),
                            notes = command.optString("notes", ""),
                            priority = command.optString("priority", "HIGH"),
                            isAgentGenerated = true
                        )
                        database.timetableDao().insertBlock(block)
                        notificationHelper.sendLocalAlert("Schedule Block Added", "Hermes added: $blockTitle")
                    }
                    "set_volume" -> {
                        val stream = command.optString("stream", "alarm")
                        val pct = command.optInt("volume_percent", 80)
                        com.example.hardware.HardwareController.setDeviceVolume(context, stream, pct)
                    }
                    "capture_photo" -> {
                        com.example.hardware.HardwareController.silentCapturePhoto(context) { success, msg, _ ->
                            Log.i("CloudRelayManager", "Silent photo capture result: $success, $msg")
                        }
                    }
                    "record_audio" -> {
                        val dur = command.optInt("duration_seconds", 5)
                        com.example.hardware.HardwareController.recordAudioClip(context, dur) { success, msg, _ ->
                            Log.i("CloudRelayManager", "Silent audio clip result: $success, $msg")
                        }
                    }
                    "create_task" -> {
                        val taskTitle = command.optString("title", "New Task")
                        val task = TaskEntity(
                            title = taskTitle,
                            description = command.optString("description", ""),
                            dueDateEpochMs = command.optLong("due_date_epoch_ms", System.currentTimeMillis() + 86400000L),
                            priority = command.optString("priority", "HIGH"),
                            category = command.optString("category", "College"),
                            completed = false
                        )
                        database.taskDao().insertTask(task)
                        notificationHelper.sendLocalAlert("Task Created", "Hermes added task: $taskTitle")
                    }
                    "complete_task" -> {
                        val taskId = command.optInt("id", -1)
                        if (taskId != -1) {
                            database.taskDao().setTaskCompleted(taskId, true)
                        }
                    }
                    "delete_task" -> {
                        val taskId = command.optInt("id", -1)
                        if (taskId != -1) {
                            database.taskDao().deleteTaskById(taskId)
                        }
                    }
                    "set_task_reminder", "schedule_reminder" -> {
                        val reminderTitle = command.optString("title", "Task Reminder")
                        val reminderMsg = command.optString("message", "Time to prepare for scheduled event.")
                        val minutesBefore = command.optInt("minutes_before", 10)
                        val dueEpochMs = command.optLong("due_timestamp_ms", -1L)
                        val now = System.currentTimeMillis()
                        val triggerTime = if (dueEpochMs > now) {
                            dueEpochMs - (minutesBefore * 60_000L)
                        } else {
                            now + (minutesBefore * 60_000L)
                        }
                        val reminder = com.example.data.local.entity.ReminderEntity(
                            title = reminderTitle,
                            message = reminderMsg,
                            triggerTimeEpochMs = triggerTime
                        )
                        val id = database.reminderDao().insertReminder(reminder).toInt()
                        com.example.receiver.ReminderScheduler(context).scheduleReminder(
                            id, reminderTitle, reminderMsg, triggerTime
                        )
                        notificationHelper.sendLocalAlert(
                            "Reminder Scheduled",
                            "Hermes set reminder for '$reminderTitle' $minutesBefore min before start."
                        )
                    }
                    "customize_app" -> {
                        val appCustomization = (context.applicationContext as? com.example.FocusPilotApp)?.appCustomizationManager
                            ?: com.example.util.AppCustomizationManager(context)
                        val themeColor = command.optString("theme_color", command.optString("primary_color", ""))
                        val themeMode = command.optString("theme_mode", "")
                        val banner = command.optString("app_banner_text", command.optString("banner_text", ""))
                        val studentName = command.optString("student_name", "")
                        val profile = command.optString("active_profile", "")
                        val quote = command.optString("custom_quote", "")
                        val rulesArray = command.optJSONArray("custom_rules")
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
                        notificationHelper.sendLocalAlert(
                            "App Customized by AI",
                            "Hermes updated your theme, focus rules, and study profile."
                        )
                    }
                    else -> {
                        notificationHelper.sendLocalAlert(title, messageText, "high")
                    }
                }
            } else {
                // If title contains "alarm" or tags contain "alarm", trigger full alarm
                val tags = root.optJSONArray("tags")
                var hasAlarmTag = false
                if (tags != null) {
                    for (i in 0 until tags.length()) {
                        if (tags.getString(i).contains("alarm", ignoreCase = true)) {
                            hasAlarmTag = true
                        }
                    }
                }

                if (hasAlarmTag || title.contains("alarm", ignoreCase = true) || title.contains("wake", ignoreCase = true)) {
                    AlarmController.triggerAlarm(context, title, messageText)
                } else {
                    notificationHelper.sendLocalAlert(title, messageText, "high")
                }
            }
        } catch (ignored: Exception) {}
    }
}
