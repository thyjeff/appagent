package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.receiver.ReminderScheduler
import com.example.ui.theme.CrimsonKillSwitch
import com.example.ui.theme.FocusPilotTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure activity wakes up the device and shows over keyguard / lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Wake Up & Follow Timetable"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Hermes reminder: check your scheduled college task."

        // Start ringing immediately
        AlarmController.startRinging(this)

        setContent {
            FocusPilotTheme {
                AlarmScreenContent(
                    title = title,
                    message = message,
                    onSnooze = { snoozeMinutes ->
                        stopAllAlarmSounds()
                        val scheduler = ReminderScheduler(this)
                        scheduler.scheduleReminder(
                            id = (System.currentTimeMillis() % 100000).toInt(),
                            title = "Snoozed: $title",
                            message = message,
                            triggerEpochMs = System.currentTimeMillis() + (snoozeMinutes * 60_000L)
                        )
                        Toast.makeText(this, "Alarm snoozed for $snoozeMinutes minutes", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onDismiss = {
                        stopAllAlarmSounds()
                        Toast.makeText(this, "Alarm stopped & dismissed", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun stopAllAlarmSounds() {
        try {
            com.example.hardware.HardwareController.stopAlarmRingtone()
            com.example.alarm.AlarmController.stopAlarm(this)
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        stopAllAlarmSounds()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_MESSAGE = "extra_alarm_message"
    }
}

@Composable
fun AlarmScreenContent(
    title: String,
    message: String,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf("") }
    var selectedSnoozeMinutes by remember { mutableIntStateOf(10) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = timeFormat.format(Date())
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF31102B)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Live Clock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = dateFormat.format(Date()),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTimeString.ifEmpty { "--:--:--" },
                    fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // Pulsing Alarm Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(CrimsonKillSwitch.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CrimsonKillSwitch, Color(0xFF991B1B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarm Ringing",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            // Task / Schedule Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HERMES SCHEDULED ALARM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Adjustable Snooze Duration Selector (5m, 7m, 10m, 15m, 20m, 30m)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Snooze Interval: ${selectedSnoozeMinutes}m",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val snoozeLimits = listOf(5, 7, 10, 15, 20, 30)
                    snoozeLimits.forEach { limit ->
                        val isSelected = limit == selectedSnoozeMinutes
                        Surface(
                            onClick = { selectedSnoozeMinutes = limit },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0x33FFFFFF),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${limit}m",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF0F172A) else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Big Action Buttons: Snooze & Stop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("dismiss_alarm_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonKillSwitch)
                ) {
                    Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("I'M AWAKE / STOP ALARM", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onSnooze(selectedSnoozeMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("snooze_alarm_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Snooze (+$selectedSnoozeMinutes Minutes)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
