package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.ui.theme.EmeraldOnline

@Composable
fun PermissionsScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val hasUsage = viewModel.hasUsagePermission()
    val canExactAlarm = viewModel.canScheduleExactAlarms()

    val allowUsage by viewModel.securityManager.allowUsageStats.collectAsState()
    val allowFiles by viewModel.securityManager.allowFileAccess.collectAsState()
    val allowLaunch by viewModel.securityManager.allowAppLaunch.collectAsState()
    val allowTasks by viewModel.securityManager.allowTaskManagement.collectAsState()
    val allowAlerts by viewModel.securityManager.allowAlerts.collectAsState()
    val serverPort by viewModel.securityManager.serverPort.collectAsState()
    val ntfyUrl by viewModel.securityManager.ntfyUrl.collectAsState()

    var ntfyInput by remember(ntfyUrl) { mutableStateOf(ntfyUrl) }
    var portInput by remember(serverPort) { mutableStateOf(serverPort.toString()) }

    // Android 13+ Notification Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. System Permissions ---
        Text(
            text = "Android System Permissions",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // Usage Access Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = if (hasUsage) EmeraldOnline else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Usage Stats Access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (hasUsage) "Granted - Screen-time tracking active"
                                else "Special permission required by Android",
                                fontSize = 12.sp,
                                color = if (hasUsage) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!hasUsage) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to open usage settings directly", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("open_usage_settings_button")
                        ) {
                            Text("Grant in Settings", fontSize = 12.sp)
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = EmeraldOnline,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allows FocusPilot to report foreground app durations to Hermes so the agent can evaluate distraction triggers. Android does NOT share screen contents or keystrokes.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Notification Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Post Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Push alerts and reminder dialogs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        FilledTonalButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        ) {
                            Text("Request", fontSize = 12.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // Exact Alarm Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (canExactAlarm) EmeraldOnline else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Exact Alarms", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (canExactAlarm) "Granted - Precise reminders" else "Restricted",
                                fontSize = 12.sp,
                                color = if (canExactAlarm) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Settings", fontSize = 12.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = EmeraldOnline, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // --- 2. Granular Agent Capability Toggles ---
        Text(
            text = "Agent Remote Capabilities",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CapabilityRow(
                    title = "Allow Screen Time Reporting",
                    description = "Hermes can call /v1/usage/summary and /v1/usage/apps",
                    enabled = allowUsage,
                    onToggle = { viewModel.setCapability("usage", it) }
                )

                Divider()

                CapabilityRow(
                    title = "Allow Approved Files Access",
                    description = "Hermes can list and read user-approved documents (/v1/files)",
                    enabled = allowFiles,
                    onToggle = { viewModel.setCapability("files", it) }
                )

                Divider()

                CapabilityRow(
                    title = "Allow App Launching",
                    description = "Hermes can trigger launcher intents (/v1/apps/launch)",
                    enabled = allowLaunch,
                    onToggle = { viewModel.setCapability("launch", it) }
                )

                Divider()

                CapabilityRow(
                    title = "Allow Task & Reminder Management",
                    description = "Hermes can query tasks and schedule alarms (/v1/tasks, /v1/reminders)",
                    enabled = allowTasks,
                    onToggle = { viewModel.setCapability("tasks", it) }
                )

                Divider()

                CapabilityRow(
                    title = "Allow Push Alerts",
                    description = "Hermes can deliver immediate priority alerts (/v1/alerts)",
                    enabled = allowAlerts,
                    onToggle = { viewModel.setCapability("alerts", it) }
                )
            }
        }

        // --- 3. Server & Integration Settings ---
        Text(
            text = "Network & Relay Settings",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Phone API Port") },
                    placeholder = { Text("8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = ntfyInput,
                    onValueChange = { ntfyInput = it },
                    label = { Text("NTFY Push Relay URL (Optional)") },
                    placeholder = { Text("https://ntfy.sh/your-secret-channel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val p = portInput.toIntOrNull() ?: 8080
                        viewModel.setServerPort(p)
                        viewModel.setNtfyUrl(ntfyInput)
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Settings")
                }
            }
        }

        // --- 4. Privacy Guarantee Statement ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Security Architecture", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• All data stays on your local device and private Tailscale network.\n" +
                            "• No screen recording, camera, microphone, or keylogging is ever executed.\n" +
                            "• File access is strictly bound to files you explicitly approve via Android Storage Access Framework.\n" +
                            "• Emergency Kill Switch instantly revokes remote execution without restarting the phone.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
