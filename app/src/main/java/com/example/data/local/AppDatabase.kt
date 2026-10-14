package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AllowedAppDao
import com.example.data.local.dao.ApprovedFileDao
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.TimetableDao
import com.example.data.local.entity.AllowedAppEntity
import com.example.data.local.entity.ApprovedFileEntity
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TimetableBlockEntity

@Database(
    entities = [
        TaskEntity::class,
        ReminderEntity::class,
        ApprovedFileEntity::class,
        AuditLogEntity::class,
        AllowedAppEntity::class,
        TimetableBlockEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun approvedFileDao(): ApprovedFileDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focuspilot_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
