package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AllowedAppEntity
import com.example.data.local.entity.ApprovedFileEntity
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, dueDateEpochMs ASC, createdAtEpochMs DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE completed = 0")
    suspend fun getPendingTasksSync(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Int, completed: Boolean)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerTimeEpochMs ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isFired = 0 AND triggerTimeEpochMs > :currentTimeEpochMs ORDER BY triggerTimeEpochMs ASC")
    fun getUpcomingReminders(currentTimeEpochMs: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isFired = 0 AND triggerTimeEpochMs > :currentTimeEpochMs")
    suspend fun getPendingRemindersSync(currentTimeEpochMs: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isFired = 1 WHERE id = :id")
    suspend fun markFired(id: Int)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Int)
}

@Dao
interface ApprovedFileDao {
    @Query("SELECT * FROM approved_files ORDER BY dateAddedEpochMs DESC")
    fun getAllApprovedFiles(): Flow<List<ApprovedFileEntity>>

    @Query("SELECT * FROM approved_files")
    suspend fun getAllApprovedFilesSync(): List<ApprovedFileEntity>

    @Query("SELECT * FROM approved_files WHERE id = :id")
    suspend fun getById(id: Int): ApprovedFileEntity?

    @Query("SELECT * FROM approved_files WHERE uriString = :uriString")
    suspend fun getByUriString(uriString: String): ApprovedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ApprovedFileEntity): Long

    @Delete
    suspend fun delete(file: ApprovedFileEntity)

    @Query("DELETE FROM approved_files WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM approved_files WHERE uriString = :uriString")
    suspend fun deleteByUriString(uriString: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestampEpochMs DESC LIMIT 150")
    fun getRecentLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_logs")
    fun getLogCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps ORDER BY appName ASC")
    fun getAllAllowedApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT * FROM allowed_apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): AllowedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AllowedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AllowedAppEntity>)

    @Query("UPDATE allowed_apps SET isLaunchAllowed = :allowed WHERE packageName = :packageName")
    suspend fun setLaunchAllowed(packageName: String, allowed: Boolean)

    @Delete
    suspend fun delete(app: AllowedAppEntity)
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_blocks ORDER BY dayOfWeek ASC, startMinuteOfDay ASC")
    fun getAllBlocks(): Flow<List<com.example.data.local.entity.TimetableBlockEntity>>

    @Query("SELECT * FROM timetable_blocks ORDER BY dayOfWeek ASC, startMinuteOfDay ASC")
    suspend fun getAllBlocksSync(): List<com.example.data.local.entity.TimetableBlockEntity>

    @Query("SELECT * FROM timetable_blocks WHERE dayOfWeek = :dayOfWeek ORDER BY startMinuteOfDay ASC")
    fun getBlocksForDay(dayOfWeek: Int): Flow<List<com.example.data.local.entity.TimetableBlockEntity>>

    @Query("SELECT * FROM timetable_blocks WHERE dayOfWeek = :dayOfWeek ORDER BY startMinuteOfDay ASC")
    suspend fun getBlocksForDaySync(dayOfWeek: Int): List<com.example.data.local.entity.TimetableBlockEntity>

    @Query("SELECT * FROM timetable_blocks WHERE id = :id")
    suspend fun getBlockById(id: Int): com.example.data.local.entity.TimetableBlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: com.example.data.local.entity.TimetableBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<com.example.data.local.entity.TimetableBlockEntity>)

    @Update
    suspend fun updateBlock(block: com.example.data.local.entity.TimetableBlockEntity)

    @Query("DELETE FROM timetable_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: Int)

    @Query("DELETE FROM timetable_blocks WHERE dayOfWeek = :dayOfWeek")
    suspend fun clearBlocksForDay(dayOfWeek: Int)

    @Query("DELETE FROM timetable_blocks")
    suspend fun clearAllBlocks()
}

