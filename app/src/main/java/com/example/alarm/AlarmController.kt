package com.example.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlarmController {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun triggerAlarm(context: Context, title: String, message: String) {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_MESSAGE, message)
        }
        context.startActivity(intent)
    }

    fun startRinging(context: Context) {
        stopRinging() // Ensure any prior instance is stopped

        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback or silently log
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from start
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (ignored: Exception) {}
    }

    @Volatile private var isStopping = false

    fun stopRinging() {
        if (isStopping) return
        isStopping = true
        try {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                } catch (ignored: Exception) {}
                try {
                    it.release()
                } catch (ignored: Exception) {}
            }
            mediaPlayer = null

            try {
                vibrator?.cancel()
            } catch (ignored: Exception) {}
            vibrator = null

            try {
                com.example.hardware.HardwareController.stopAlarmRingtone()
            } catch (ignored: Exception) {}
        } finally {
            isStopping = false
        }
    }

    fun stopAlarm(context: Context? = null) {
        stopRinging()
    }
}
