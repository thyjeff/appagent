package com.example.focus

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FocusPilotTheme
import kotlinx.coroutines.delay

class FocusOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while overlay is shown
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FocusPilotTheme {
                FocusOverlayContent(
                    onDismiss = {
                        FocusController.disableStrictFocus(this)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun FocusOverlayContent(onDismiss: () -> Unit) {
    val isActive by FocusController.isStrictFocusActive.collectAsState()
    val subject by FocusController.focusSubject.collectAsState()
    val endTime by FocusController.focusEndTimeEpochMs.collectAsState()
    val notes by FocusController.focusNotes.collectAsState()

    var remainingSeconds by remember {
        mutableLongStateOf(maxOf(0L, (endTime - System.currentTimeMillis()) / 1000))
    }

    LaunchedEffect(endTime, isActive) {
        if (!isActive) {
            onDismiss()
            return@LaunchedEffect
        }
        while (isActive) {
            val rem = maxOf(0L, (endTime - System.currentTimeMillis()) / 1000)
            remainingSeconds = rem
            if (rem <= 0) {
                break
            }
            delay(1000)
        }
    }

    val minutesLeft = remainingSeconds / 60
    val secondsLeft = remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutesLeft, secondsLeft)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617),
                        Color(0xFF0F172A)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Shield Lock Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Strict Focus Lock",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(44.dp)
                )
            }

            // Headline
            Text(
                text = "HERMES STRICT FOCUS MODE",
                color = Color(0xFF93C5FD),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            // Subject Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strict_focus_subject_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Deep Work Block Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = subject,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (notes.isNotBlank()) {
                        Text(
                            text = notes,
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Countdown Timer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            // Warning Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Distracting feeds and social apps are restricted. Stay engaged with your academic goal.",
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Emergency Exit / Finish Button
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("exit_strict_focus_button")
            ) {
                Text("Emergency Exit Focus Session", fontSize = 12.sp)
            }
        }
    }
}
