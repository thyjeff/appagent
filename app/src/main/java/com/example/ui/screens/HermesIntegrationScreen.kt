package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.util.QrCodeUtil
import org.json.JSONObject

private val EmeraldOnline = Color(0xFF10B981)
private val GoldAccent = Color(0xFFFBBF24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesIntegrationScreen(
    viewModel: FocusPilotViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val localIp by viewModel.localIp.collectAsState()
    val port by viewModel.securityManager.serverPort.collectAsState()
    val token by viewModel.securityManager.apiToken.collectAsState()
    val ntfyUrl by viewModel.securityManager.ntfyUrl.collectAsState()
    val customMcpUrl by viewModel.customMcpUrl.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    val isRelayConnected by viewModel.cloudRelayManager.isRelayConnected.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Common Link & Bearer, 1: Scan QR, 2: Chat Prompt, 3: Pi 5 Script
    var mcpUrlInput by remember(customMcpUrl) { mutableStateOf(customMcpUrl) }
    var isTestingRelay by remember { mutableStateOf(false) }
    var isSyncingMcp by remember { mutableStateOf(false) }

    // Pairing payload encoded in the QR code
    val pairingPayload = remember(localIp, port, token, ntfyUrl, customMcpUrl) {
        JSONObject().apply {
            put("app", "FocusPilot")
            put("device", "Android")
            put("account", "jossrichard18@gmail.com")
            put("cloud_relay_url", ntfyUrl)
            put("mcp_server_url", customMcpUrl)
            put("local_api_url", "http://$localIp:$port")
            put("token", token)
            put("version", "2.0")
        }.toString()
    }

    val qrBitmap = remember(pairingPayload) {
        QrCodeUtil.generateQrBitmap(pairingPayload, 600)
    }

    // Direct prompt for Hermes or ChatGPT
    val promptForHermes = """
Hermes, here are the credentials to connect to my FocusPilot Android companion app:
• Remote Common MCP Server: $customMcpUrl
• Auth Bearer Token: $token
• Cloud Relay Topic: $ntfyUrl
• Direct LAN Endpoint: http://$localIp:$port
• Student Account: jossrichard18@gmail.com
• Web Portal Login: Google Sign-In or 6-digit PIN $pairingPin

How you should manage my college day:
1. Call get_student_overview to inspect my current timetable block, pending tasks, and screen time today.
2. If I'm using distracting phone apps during a scheduled study block, send me a warning alert.
3. Call trigger_loud_alarm to ring a full loud wake-up alarm with continuous sound and vibration for morning wake-up or urgent tasks.
4. Update or rewrite my 24h schedule anytime via update_timetable to balance my day.
5. Create and update my college assignments via list_tasks and create_task.
    """.trimIndent()

    val pythonScript = """
# focuspilot_hermes.py - Run on Raspberry Pi 5
import requests
import json
import time

MCP_URL = "$customMcpUrl"
RELAY_URL = "$ntfyUrl"
TOKEN = "$token"

headers = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json"
}

def trigger_loud_alarm(title="Wake Up!", message="Hermes Alarm"):
    payload = {"action": "trigger_alarm", "title": title, "message": message, "token": TOKEN}
    requests.post(RELAY_URL, data=json.dumps(payload), headers={"Title": title})

def update_timetable(blocks):
    payload = {"action": "update_timetable", "blocks": blocks, "token": TOKEN}
    requests.post(RELAY_URL, data=json.dumps(payload))

print("FocusPilot Hermes Client Connected via Bearer Token!")
    """.trimIndent()

    val claudeDesktopConfigJson = """
{
  "mcpServers": {
    "focuspilot": {
      "url": "$customMcpUrl",
      "headers": {
        "Authorization": "Bearer $token"
      }
    }
  }
}
    """.trimIndent()

    val singleUrlWithToken = if (customMcpUrl.contains("?")) {
        "$customMcpUrl&token=$token"
    } else {
        "$customMcpUrl?token=$token"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Hermes Agent", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cloud Status & Account Header Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Cellular Cloud Relay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (isRelayConnected) "Connected (Ready for Alarms & Sync)" else "Connecting to Private Relay...",
                                fontSize = 12.sp,
                                color = if (isRelayConnected) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "jossrichard18@gmail.com",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Tab Selector
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("1. Common Link & Bearer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("2. Scan QR", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("3. Tell Hermes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("4. Pi 5 Script", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: Common Link + Phone Bearer Token (The Simplest Method)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section Banner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldAccent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Simplest Agent Connection",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GoldAccent.copy(alpha = 0.15f))
                                        .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("RECOMMENDED", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "Your MCP project is deployed! You don't need any complex setup: simply use your Common Server Link and paste your Phone's Bearer Token into your agent (Claude Desktop, Cursor, Hermes, OpenDevin).",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            // --- STEP 1: Common Server Link ---
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("STEP 1: Common MCP Server Link", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = mcpUrlInput,
                                            onValueChange = { mcpUrlInput = it },
                                            placeholder = { Text("https://your-domain.vercel.app/sse") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_mcp_url_input"),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.setCustomMcpUrl(mcpUrlInput)
                                                Toast.makeText(context, "MCP URL saved!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("Save")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Common MCP Link", customMcpUrl))
                                                Toast.makeText(context, "Copied Common MCP Link!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Server Link", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(customMcpUrl.replace("/sse", "")))
                                                context.startActivity(browserIntent)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Portal", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // --- STEP 2: Phone Bearer Token ---
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("STEP 2: Phone Bearer Token", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text("Unique To This Phone", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = token,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = GoldAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Bearer Token", token))
                                                Toast.makeText(context, "Bearer token copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Token", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Authorization Header", "Bearer $token"))
                                                Toast.makeText(context, "Copied 'Bearer $token'!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Copy Header", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.regenerateApiToken()
                                                Toast.makeText(context, "New token generated!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }

                            // --- 1-CLICK AGENT CONFIGURATIONS ---
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Claude Desktop / Cursor Config", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("claude_desktop_config.json", claudeDesktopConfigJson))
                                            Toast.makeText(context, "Claude Desktop JSON copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy JSON", fontSize = 12.sp)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(12.dp)
                                    ) {
                                    Text(
                                        text = claudeDesktopConfigJson,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFDE68A)
                                    )
                                }

                                // Single Zero-Headers URL (For tools that take a single URL)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Zero-Headers Single URL (?token=...)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Single MCP URL", singleUrlWithToken))
                                            Toast.makeText(context, "Single URL with token copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy URL", fontSize = 11.sp)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = singleUrlWithToken,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // --- LIVE VERIFICATION & TEST STATION ---
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("🧪 Test Connection & Cloud Alarm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = "Test that your phone rings instantly when Hermes or Claude sends a command via cellular data:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                isTestingRelay = true
                                                viewModel.testCloudRelayAlarm { ok, msg ->
                                                    isTestingRelay = false
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isTestingRelay) "Sending..." else "Ring Loud Alarm", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                isSyncingMcp = true
                                                viewModel.syncPhoneWithMcpServer { ok, msg ->
                                                    isSyncingMcp = false
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isSyncingMcp) "Syncing..." else "Sync Schedule", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Quick 6-Digit Pair PIN Display (For Web Portal)
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Web Portal 6-Digit PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            text = "${pairingPin.take(3)} ${pairingPin.takeLast(3)}",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Pairing PIN", pairingPin))
                                            Toast.makeText(context, "Pairing PIN copied: $pairingPin", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy PIN", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: Scan QR
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Scan QR with Hermes or Laptop Camera",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Point your Raspberry Pi camera or laptop webcam at this QR code. It encodes your Common MCP Server link, Phone Bearer Token, and Cloud Relay topic.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "FocusPilot Pairing QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Pairing JSON", pairingPayload))
                                    Toast.makeText(context, "Pairing JSON copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy QR Payload JSON")
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: Chat Prompt (Paste to Hermes/Claude)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paste Directly into AI Chat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Button(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Hermes Prompt", promptForHermes))
                                        Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Prompt")
                                }
                            }

                            Text(
                                text = "Paste this directly into ChatGPT, Claude, or your Hermes terminal. It teaches the agent how to inspect your schedule, trigger loud alarms, and prevent study distractions.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = promptForHermes,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFDE68A),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: Pi 5 Script
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Raspberry Pi 5 Python Client", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Button(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Python Script", pythonScript))
                                        Toast.makeText(context, "Python script copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Python")
                                }
                            }

                            Text(
                                text = "Run this lightweight script on your Raspberry Pi 5 to control your phone 24/7 over cellular mobile data.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = pythonScript,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA7F3D0),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
