package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
fun SettingsScreen(
    viewModel: FocusPilotViewModel,
    onNavigateToAuditLog: () -> Unit,
    onNavigateToApprovedFiles: () -> Unit,
    onNavigateToHermesGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRunning by viewModel.isServerRunning.collectAsState()
    val isRemoteEnabled by viewModel.securityManager.isRemoteAccessEnabled.collectAsState()
    val token by viewModel.securityManager.apiToken.collectAsState()
    val port by viewModel.securityManager.serverPort.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val customMcpUrl by viewModel.customMcpUrl.collectAsState()
    val approvedFiles by viewModel.approvedFiles.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    val allowUsage by viewModel.securityManager.allowUsageStats.collectAsState()
    val allowFiles by viewModel.securityManager.allowFileAccess.collectAsState()
    val allowLaunch by viewModel.securityManager.allowAppLaunch.collectAsState()
    val allowTasks by viewModel.securityManager.allowTaskManagement.collectAsState()
    val allowAlerts by viewModel.securityManager.allowAlerts.collectAsState()

    var showToken by remember { mutableStateOf(false) }

    var permRefreshTrigger by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permRefreshTrigger++
    }

    val hasCameraPerm = permRefreshTrigger.let {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val hasAudioPerm = permRefreshTrigger.let {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasLocationPerm = permRefreshTrigger.let {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val hasNotificationPerm = permRefreshTrigger.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    val hasOverlayPerm = permRefreshTrigger.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- 1. Hermes Agent Connection & Bridge ---
        Text(
            text = "Hermes Agent Connection",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bridge Status & Action Row
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
                                .background(if (isRunning) EmeraldOnline else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "BRIDGE ACTIVE" else "BRIDGE STOPPED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isRunning) EmeraldOnline else Color(0xFF64748B)
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (isRunning) {
                                viewModel.stopServer()
                                Toast.makeText(context, "Bridge stopped", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.startServer()
                                Toast.makeText(context, "Bridge started", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("toggle_bridge_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Stop Bridge" else "Start Bridge", fontSize = 13.sp)
                    }
                }

                // Common MCP Server Link
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("COMMON MCP SERVER URL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = customMcpUrl,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("MCP URL", customMcpUrl))
                                Toast.makeText(context, "MCP URL copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy MCP URL", modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // Phone Bearer Token
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PHONE BEARER TOKEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

                            Row {
                                IconButton(
                                    onClick = { showToken = !showToken },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Bearer Token", token))
                                        Toast.makeText(context, "Bearer token copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Token", modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        Text(
                            text = if (showToken) token else "••••••••••••••••••••••••••••••••",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Local IP Endpoint
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LOCAL LAN ENDPOINT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "http://$localIp:$port",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Local URL", "http://$localIp:$port"))
                                Toast.makeText(context, "Local URL copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Full Hub Button
                Button(
                    onClick = onNavigateToHermesGuide,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Agent Hub (Claude, Cursor, QR & Pi)")
                }
            }
        }

        // --- 2. Emergency Cut-Off (Kill Switch) ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (!isRemoteEnabled) CrimsonKillSwitch.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.outlineVariant
                )
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
                        contentDescription = null,
                        tint = if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (!isRemoteEnabled) "Emergency Cut-Off Active" else "Remote Agent Interface",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (!isRemoteEnabled) CrimsonKillSwitch else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!isRemoteEnabled) "Agent commands are blocked (403)" else "Incoming commands allowed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isRemoteEnabled,
                    onCheckedChange = { viewModel.toggleKillSwitch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = CrimsonKillSwitch
                    )
                )
            }
        }

        // --- 3. Permissions & Privacy ---
        Text(
            text = "Hardware & Agent Access Permissions",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // Card A: Real Hardware & Sensor Permissions (Camera, Audio, Location, Overlay, Usage)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Sensor & Hardware Access", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Required for silent camera, mic, campus geofence & overlay", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permLauncher.launch(perms.toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("grant_all_hardware_permissions_btn")
                    ) {
                        Text("Grant All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider()

                // 1. Camera Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Camera Access", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = if (hasCameraPerm) "Granted • Silent camera2 capture enabled" else "Needed for instant silent photo snap",
                                fontSize = 11.sp,
                                color = if (hasCameraPerm) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!hasCameraPerm) {
                        OutlinedButton(
                            onClick = { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                    }
                }

                // 2. Microphone Audio Recording
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Microphone Audio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = if (hasAudioPerm) "Granted • Silent audio recording active" else "Needed for audio notes & voice check",
                                fontSize = 11.sp,
                                color = if (hasAudioPerm) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!hasAudioPerm) {
                        OutlinedButton(
                            onClick = { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                    }
                }

                // 3. Location / Campus Geofence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Campus Geofence Location", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = if (hasLocationPerm) "Granted • Hostel vs Academic detection active" else "Needed to detect if student in hostel during class",
                                fontSize = 11.sp,
                                color = if (hasLocationPerm) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!hasLocationPerm) {
                        OutlinedButton(
                            onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                    }
                }

                // 4. Full-Screen Focus Lock Overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Full-Screen Focus Overlay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = if (hasOverlayPerm) "Granted • Strict focus lock displays over apps" else "Needed to display overlay during study blocks",
                                fontSize = 11.sp,
                                color = if (hasOverlayPerm) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!hasOverlayPerm) {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                    }
                }

                // 5. Battery & Telemetry Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Battery % & Telemetry", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Active • Live battery %, Wi-Fi SSID & stream volume reporting", fontSize = 11.sp, color = EmeraldOnline)
                        }
                    }
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                }

                // 6. Screen-Time Usage Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Screen-Time Usage Access", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = if (viewModel.hasUsagePermission()) "Active • Tracking study adherence" else "Required for distraction telemetry",
                                fontSize = 11.sp,
                                color = if (viewModel.hasUsagePermission()) EmeraldOnline else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!viewModel.hasUsagePermission()) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = EmeraldOnline, modifier = Modifier.size(20.dp))
                    }
                }

                // 7. File & Storage Access (SAF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Storage & File Access (SAF)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "${approvedFiles.size} folders/files approved for agent",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onNavigateToApprovedFiles,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Manage", fontSize = 11.sp)
                    }
                }
            }
        }

        // Card B: Agent Capability Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Remote Agent Capabilities Control", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Toggle specific features Hermes is permitted to execute remotely", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Divider()

                CapabilityToggleRow(
                    title = "Allow Screen Time Reporting",
                    enabled = allowUsage,
                    onToggle = { viewModel.setCapability("usage", it) }
                )
                CapabilityToggleRow(
                    title = "Allow Loud Alarms & Wake-Up Alerts",
                    enabled = allowAlerts,
                    onToggle = { viewModel.setCapability("alerts", it) }
                )
                CapabilityToggleRow(
                    title = "Allow Task & Timetable Sync",
                    enabled = allowTasks,
                    onToggle = { viewModel.setCapability("tasks", it) }
                )
                CapabilityToggleRow(
                    title = "Allow Approved Files Access",
                    enabled = allowFiles,
                    onToggle = { viewModel.setCapability("files", it) }
                )
                CapabilityToggleRow(
                    title = "Allow App Launching",
                    enabled = allowLaunch,
                    onToggle = { viewModel.setCapability("launch", it) }
                )
            }
        }

        // --- 4. Navigation to Files & Audit Trail ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onNavigateToApprovedFiles,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Approved Files", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${approvedFiles.size} folders", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Surface(
                onClick = onNavigateToAuditLog,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Audit Trail", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${auditLogs.size} logs", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun CapabilityToggleRow(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
