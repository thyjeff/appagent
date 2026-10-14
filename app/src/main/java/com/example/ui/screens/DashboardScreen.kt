package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.ui.theme.CrimsonKillSwitch
import com.example.ui.theme.EmeraldOnline

@Composable
fun DashboardScreen(
    viewModel: FocusPilotViewModel,
    onNavigateToHermesGuide: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by viewModel.isServerRunning.collectAsState()
    val isRemoteEnabled by viewModel.securityManager.isRemoteAccessEnabled.collectAsState()
    val token by viewModel.securityManager.apiToken.collectAsState()
    val port by viewModel.securityManager.serverPort.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val usageSummary by viewModel.usageSummary.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val files by viewModel.approvedFiles.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    val pendingTasksCount = remember(tasks) { tasks.count { !it.completed } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Emergency Kill Switch Banner ---
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kill_switch_card"),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (!isRemoteEnabled) CrimsonKillSwitch.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.outlineVariant
                ),
                width = if (!isRemoteEnabled) 2.dp else 1.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (!isRemoteEnabled) Icons.Default.Block else Icons.Default.Security,
                        contentDescription = "Kill Switch Status",
                        tint = if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (!isRemoteEnabled) "EMERGENCY SHUTDOWN ACTIVE" else "Remote Agent Interface",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!isRemoteEnabled) "All incoming requests from Hermes will be blocked (403)"
                            else "Hermes requests permitted according to capability policies",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isRemoteEnabled,
                    onCheckedChange = { viewModel.toggleKillSwitch(it) },
                    modifier = Modifier.testTag("kill_switch_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = CrimsonKillSwitch,
                        uncheckedTrackColor = CrimsonKillSwitch.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // --- 2. Main Server Bridge Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server_status_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top status pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) EmeraldOnline else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "BRIDGE ACTIVE" else "BRIDGE STOPPED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isRunning) EmeraldOnline else Color.Gray
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (isRunning) viewModel.stopServer() else viewModel.startServer()
                        },
                        modifier = Modifier.testTag("toggle_server_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Stop Bridge" else "Start Bridge")
                    }
                }

                // IP and Port
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SERVER ADDRESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "http://$localIp:$port",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("URL", "http://$localIp:$port"))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Server URL", modifier = Modifier.size(18.dp))
                    }
                }

                // API Token Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AUTHENTICATION TOKEN (BEARER)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row {
                            IconButton(
                                onClick = { showToken = !showToken },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Token Visibility",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("FocusPilot Token", token))
                                    Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Token", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Text(
                        text = if (showToken) token else "••••••••••••••••••••••••••••••••",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showRegenerateDialog = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate Token", fontSize = 12.sp)
                    }
                }

                // Quick cURL Command Copy
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test with cURL from Pi/Host:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    val cmd = "curl -H \"Authorization: Bearer $token\" http://$localIp:$port/v1/status"
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("cURL Command", cmd))
                                    Toast.makeText(context, "cURL command copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy cURL", fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = "curl -H \"Authorization: Bearer ...\" http://$localIp:$port/v1/status",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // --- 3. Telemetry & Stats Grid ---
        Text(
            text = "Companion Overview",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Screen Time Today", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (usageSummary.hasPermission) usageSummary.formattedTotalTime else "Needs Access",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Approved Locations", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${files.size} locations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Pending Tasks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$pendingTasksCount tasks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Agent Requests", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${auditLogs.size} audited",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- 4. Vertical Slice Action / Integration ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Verification & Tests",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.sendTestAlert()
                            Toast.makeText(context, "Test alert sent to device", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_notification_button")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Alert", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.refreshState()
                            Toast.makeText(context, "Network and telemetry refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onNavigateToHermesGuide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hermes_setup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hermes Pi / MCP Setup Guide & Script")
                }
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Regenerate API Token?") },
            text = { Text("Regenerating the token will revoke the current key immediately. You will need to update your Hermes host or MCP client configuration.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.regenerateToken()
                        showRegenerateDialog = false
                        Toast.makeText(context, "New API token generated", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
