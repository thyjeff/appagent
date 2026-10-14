package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.FocusPilotViewModel
import com.example.ui.navigation.FocusScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ApprovedFilesScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.HermesIntegrationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.FocusPilotTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val viewModel: FocusPilotViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val themeColorHex by viewModel.themeColorHex.collectAsState()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val customColor = remember(themeColorHex) {
                try {
                    val hex = themeColorHex.trim().removePrefix("#")
                    val colorLong = hex.toLong(16)
                    if (hex.length == 6) Color(0xFF000000 or colorLong)
                    else Color(colorLong)
                } catch (e: Exception) {
                    null
                }
            }

            FocusPilotTheme(
                darkTheme = isDark,
                customPrimaryColor = customColor
            ) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: FocusPilotViewModel) {
    var currentScreen by remember { mutableStateOf(FocusScreen.HOME) }
    var showAuditLog by remember { mutableStateOf(false) }
    var showApprovedFiles by remember { mutableStateOf(false) }
    var showAgentIntegration by remember { mutableStateOf(false) }

    val isServerRunning by viewModel.isServerRunning.collectAsState()

    // Sub-screen overlays
    if (showAgentIntegration) {
        HermesIntegrationScreen(
            viewModel = viewModel,
            onBack = { showAgentIntegration = false }
        )
        return
    }

    if (showAuditLog) {
        BackHandler { showAuditLog = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Audit Trail", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showAuditLog = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AuditLogScreen(viewModel = viewModel)
            }
        }
        return
    }

    if (showApprovedFiles) {
        BackHandler { showApprovedFiles = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Approved Documents", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showApprovedFiles = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                ApprovedFilesScreen(viewModel = viewModel)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal Header Bar
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FocusPilot",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isServerRunning) EmeraldOnline else Color(0xFF94A3B8))
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showAgentIntegration = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "Connect Agent", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { viewModel.refreshState() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Main Screen Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentScreen) {
                    FocusScreen.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSchedule = { currentScreen = FocusScreen.SCHEDULE },
                        onNavigateToConnectAgent = { showAgentIntegration = true }
                    )
                    FocusScreen.SCHEDULE -> ScheduleScreen(viewModel = viewModel)
                    FocusScreen.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                    FocusScreen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToAuditLog = { showAuditLog = true },
                        onNavigateToApprovedFiles = { showApprovedFiles = true },
                        onNavigateToHermesGuide = { showAgentIntegration = true }
                    )
                }
            }
        }

        // Floating Pill Navigation Bar (Directly Inspired by Reference Images 1 & 2)
        FloatingPillNavBar(
            currentScreen = currentScreen,
            onSelectScreen = { currentScreen = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun FloatingPillNavBar(
    currentScreen: FocusScreen,
    onSelectScreen: (FocusScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("floating_pill_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FocusScreen.entries.forEach { screen ->
                val isSelected = currentScreen == screen

                Surface(
                    onClick = { onSelectScreen(screen) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else Color.Transparent,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag(screen.testTag)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = screen.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
