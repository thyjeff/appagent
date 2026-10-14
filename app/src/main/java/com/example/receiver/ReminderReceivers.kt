package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.security.SecurityManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Focus Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to check your scheduled tasks."

        val securityManager = SecurityManager(context)
        val notificationHelper = NotificationHelper(context, securityManager)
        notificationHelper.sendReminderNotification(reminderId, title, message)

        if (reminderId != -1) {
            val db = AppDatabase.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                db.reminderDao().markFired(reminderId)
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val db = AppDatabase.getInstance(context)
            val scheduler = ReminderScheduler(context)
            CoroutineScope(Dispatchers.IO).launch {
                val now = System.currentTimeMillis()
                val pending = db.reminderDao().getPendingRemindersSync(now)
                for (reminder in pending) {
                    scheduler.scheduleReminder(
                        reminder.id,
                        reminder.title,
                        reminder.message,
                        reminder.triggerTimeEpochMs
                    )
                }
            }
        }
    }
}

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(id: Int, title: String, message: String, triggerEpochMs: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // In case exact alarm permission is revoked, fallback to normal inexact alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
        }
    }

    fun cancelReminder(id: Int) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
