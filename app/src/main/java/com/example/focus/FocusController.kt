package com.example.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FocusController {
    private const val TAG = "FocusController"

    private val _isStrictFocusActive = MutableStateFlow(false)
    val isStrictFocusActive: StateFlow<Boolean> = _isStrictFocusActive.asStateFlow()

    private val _focusSubject = MutableStateFlow("Deep Work Session")
    val focusSubject: StateFlow<String> = _focusSubject.asStateFlow()

    private val _focusEndTimeEpochMs = MutableStateFlow(0L)
    val focusEndTimeEpochMs: StateFlow<Long> = _focusEndTimeEpochMs.asStateFlow()

    private val _focusNotes = MutableStateFlow("")
    val focusNotes: StateFlow<String> = _focusNotes.asStateFlow()

    fun enableStrictFocus(
        context: Context,
        subject: String,
        durationMinutes: Int = 45,
        notes: String = ""
    ) {
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        _focusSubject.value = subject.ifBlank { "Deep Work Session" }
        _focusEndTimeEpochMs.value = endTime
        _focusNotes.value = notes
        _isStrictFocusActive.value = true

        Log.i(TAG, "Enabling strict focus mode for $subject until $endTime")

        // Try setting DND priority interruption filter if permission granted
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set DND interruption filter: ${e.message}")
        }

        // Launch full-screen focus overlay
        try {
            val intent = Intent(context, FocusOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("subject", subject)
                putExtra("endTime", endTime)
                putExtra("notes", notes)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch FocusOverlayActivity: ${e.message}", e)
        }
    }

    fun disableStrictFocus(context: Context) {
        _isStrictFocusActive.value = false
        _focusEndTimeEpochMs.value = 0L

        Log.i(TAG, "Disabling strict focus mode")

        // Restore normal notification filter
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore notification filter: ${e.message}")
        }
    }
}
