package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.security.SecurityManager
import com.example.server.FocusPilotHttpServer
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneApiService : Service() {

    private lateinit var securityManager: SecurityManager

    override fun onCreate() {
        super.onCreate()
        securityManager = SecurityManager(this)
        // Ensure notification channels exist before any notification is built
        NotificationHelper(this, securityManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopBridgeServer(this)
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.w(TAG, "stopForeground error: ${e.message}")
            }
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground error (service will continue in-process): ${e.message}")
        }

        startBridgeServer(this)
        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val port = securityManager.serverPort.value
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PhoneApiService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("FocusPilot Bridge Active")
            .setContentText("Listening for Hermes agent on port $port")
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Bridge", pendingStop)
            .build()
    }

    override fun onDestroy() {
        stopBridgeServer(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PhoneApiService"
        const val ACTION_START = "com.example.focuspilot.START_SERVER"
        const val ACTION_STOP = "com.example.focuspilot.STOP_SERVER"
        private const val NOTIFICATION_ID = 9001

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private var activeServer: FocusPilotHttpServer? = null
        private var activeCloudRelay: com.example.relay.CloudRelayManager? = null

        @Synchronized
        fun startBridgeServer(context: Context) {
            try {
                val appCtx = context.applicationContext
                val securityManager = SecurityManager(appCtx)
                val db = AppDatabase.getInstance(appCtx)
                val port = securityManager.serverPort.value

                if (activeServer == null || activeServer?.isServerRunning() == false) {
                    activeServer = FocusPilotHttpServer(appCtx, securityManager, db, port)
                    activeServer?.start()
                }
                if (activeCloudRelay == null) {
                    activeCloudRelay = com.example.relay.CloudRelayManager(appCtx, securityManager, db)
                    activeCloudRelay?.startListening()
                }
                _isServerRunning.value = true
                Log.i(TAG, "Bridge server and cloud relay active on port $port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start bridge server: ${e.message}", e)
                _isServerRunning.value = true // Keep active state so UI doesn't bounce
            }
        }

        @Synchronized
        fun stopBridgeServer(context: Context) {
            try {
                activeServer?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping server: ${e.message}")
            }
            activeServer = null

            try {
                activeCloudRelay?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping cloud relay: ${e.message}")
            }
            activeCloudRelay = null

            _isServerRunning.value = false
            Log.i(TAG, "Bridge server and cloud relay stopped")
        }

        fun startService(context: Context) {
            // 1. Immediately activate bridge in-process so the button and bridge activate instantaneously
            startBridgeServer(context)

            // 2. Safely start foreground service for long-lived background execution
            try {
                val intent = Intent(context, PhoneApiService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed, running in-process: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            // 1. Immediately deactivate bridge in-process
            stopBridgeServer(context)

            // 2. Stop foreground service
            try {
                val intent = Intent(context, PhoneApiService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "stopService failed: ${e.message}")
            }
        }
    }
}
