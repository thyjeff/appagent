package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NotificationHelper(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val httpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "FocusPilot Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing status of the Hermes API server bridge"
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "FocusPilot Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled tasks, alarms, and focus reminders"
                enableVibration(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Hermes Agent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate push alerts from your Hermes AI agent"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(remindersChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    fun sendLocalAlert(title: String, message: String, priority: String = "normal") {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val importance = if (priority.equals("high", ignoreCase = true)) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hermes Alert: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(importance)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)

        // Relay to NTFY if configured
        val ntfyUrl = securityManager.ntfyUrl.value
        if (ntfyUrl.isNotBlank()) {
            relayToNtfy(ntfyUrl, title, message, priority)
        }
    }

    fun sendReminderNotification(id: Int, title: String, message: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Reminder: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun relayToNtfy(ntfyUrl: String, title: String, message: String, priority: String) {
        scope.launch {
            try {
                val mediaType = "text/plain; charset=utf-8".toMediaType()
                val body = message.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(ntfyUrl)
                    .header("Title", title)
                    .header("Priority", if (priority.equals("high", ignoreCase = true)) "high" else "default")
                    .header("Tags", "robot,focuspilot")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (ignored: Exception) {
                // Network error sending to ntfy - silently continue
            }
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "focuspilot_service_channel"
        const val CHANNEL_REMINDERS = "focuspilot_reminders_channel"
        const val CHANNEL_ALERTS = "focuspilot_alerts_channel"
    }
}
