package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val dueDateEpochMs: Long? = null,
    val completed: Boolean = false,
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val category: String = "General",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val triggerTimeEpochMs: Long,
    val isFired: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "approved_files")
data class ApprovedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val isDirectory: Boolean = false,
    val sizeBytes: Long = 0,
    val dateAddedEpochMs: Long = System.currentTimeMillis(),
    val permissionLevel: String = "READ_ONLY" // READ_ONLY, READ_WRITE
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val clientIp: String,
    val endpoint: String,
    val method: String,
    val statusCode: Int,
    val requestSummary: String,
    val responseSummary: String,
    val isBlocked: Boolean = false
)

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLaunchAllowed: Boolean = true,
    val dailyLimitMinutes: Int = 0, // 0 = unlimited
    val category: String = "Utility"
)

@Entity(tableName = "timetable_blocks")
data class TimetableBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: Int = 1, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val title: String,
    val subjectOrProject: String = "",
    val category: String = "Subject", // Subject, Project, Health, Routine, Sleep, Break
    val startMinuteOfDay: Int, // 0 to 1439 (e.g. 8 * 60 + 30 = 510 for 08:30)
    val endMinuteOfDay: Int,   // 0 to 1439 (e.g. 10 * 60 = 600 for 10:00)
    val notes: String = "",
    val isAgentGenerated: Boolean = true,
    val priority: String = "HIGH",
    val isCompleted: Boolean = false
)

