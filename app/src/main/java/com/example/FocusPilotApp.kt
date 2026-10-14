package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllowedAppEntity
import com.example.data.local.entity.TaskEntity
import com.example.security.SecurityManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusPilotApp : Application() {

    lateinit var securityManager: SecurityManager
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var appCustomizationManager: com.example.util.AppCustomizationManager
        private set

    override fun onCreate() {
        super.onCreate()
        securityManager = SecurityManager(this)
        database = AppDatabase.getInstance(this)
        appCustomizationManager = com.example.util.AppCustomizationManager(this)

        // Initialize notification channels
        NotificationHelper(this, securityManager)

        // Prepopulate starter starter tasks if first launch
        CoroutineScope(Dispatchers.IO).launch {
            val existingTasks = database.taskDao().getAllTasksSync()
            if (existingTasks.isEmpty()) {
                database.taskDao().insertTask(
                    TaskEntity(
                        title = "Pair FocusPilot with Hermes Host",
                        description = "Copy API token and configure MCP client on Raspberry Pi 5 / host.",
                        priority = "HIGH",
                        category = "Setup",
                        dueDateEpochMs = System.currentTimeMillis() + 86400000
                    )
                )
                database.taskDao().insertTask(
                    TaskEntity(
                        title = "Approve Focus Journal Directory",
                        description = "Grant SAF folder permission for Hermes daily reflection access.",
                        priority = "MEDIUM",
                        category = "Storage"
                    )
                )
                database.taskDao().insertTask(
                    TaskEntity(
                        title = "Enable Android Usage Access",
                        description = "Grant Usage Stats permission in system settings for screen-time telemetry.",
                        priority = "HIGH",
                        category = "Permissions"
                    )
                )
            }

            // Seed initial 24h student schedule if empty
            val existingBlocks = database.timetableDao().getAllBlocksSync()
            if (existingBlocks.isEmpty()) {
                val sample = listOf(
                    com.example.data.local.entity.TimetableBlockEntity(title = "Morning Wake Up & Workout", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 7 * 60, endMinuteOfDay = 8 * 60, notes = "Cardio & high energy breakfast"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Data Structures & Algorithms", subjectOrProject = "College Core", category = "Subject", startMinuteOfDay = 8 * 60 + 30, endMinuteOfDay = 10 * 60, notes = "Trees & Dynamic Programming review"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Campus Walk & Coffee Break", subjectOrProject = "Rest", category = "Break", startMinuteOfDay = 10 * 60, endMinuteOfDay = 10 * 60 + 30, notes = "No screen break"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Operating Systems Lab", subjectOrProject = "College Lab", category = "Subject", startMinuteOfDay = 10 * 60 + 30, endMinuteOfDay = 12 * 60 + 30, notes = "Process Scheduling & Semaphores"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Lunch & Social Time", subjectOrProject = "Meal", category = "Routine", startMinuteOfDay = 12 * 60 + 30, endMinuteOfDay = 13 * 60 + 30, notes = "Campus cafeteria"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Hermes & AI Engine Project", subjectOrProject = "Major Capstone", category = "Project", startMinuteOfDay = 13 * 60 + 30, endMinuteOfDay = 16 * 60, notes = "Raspberry Pi & Android Bridge tuning"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Computer Networks Tutorial", subjectOrProject = "College Subject", category = "Subject", startMinuteOfDay = 16 * 60 + 15, endMinuteOfDay = 17 * 60 + 45, notes = "TCP/IP & Socket Programming"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Gym & Strength Training", subjectOrProject = "Fitness", category = "Health", startMinuteOfDay = 18 * 60, endMinuteOfDay = 19 * 60 + 15, notes = "Push workout"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Dinner & Wind Down", subjectOrProject = "Routine", category = "Routine", startMinuteOfDay = 19 * 60 + 30, endMinuteOfDay = 20 * 60 + 30, notes = "Unwind"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Assignment Prep & Daily Review", subjectOrProject = "Hermes Sync", category = "Subject", startMinuteOfDay = 20 * 60 + 30, endMinuteOfDay = 22 * 60, notes = "Submit weekly college assignments"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Night Reading", subjectOrProject = "Rest", category = "Break", startMinuteOfDay = 22 * 60, endMinuteOfDay = 23 * 60, notes = "Read fiction book, zero phone screen"),
                    com.example.data.local.entity.TimetableBlockEntity(title = "Sleep & Recovery", subjectOrProject = "Sleep", category = "Sleep", startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60, notes = "8 hours sleep target")
                )
                database.timetableDao().insertAll(sample)
            }
        }
    }
}
