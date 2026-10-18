package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.FraudShieldViewModel
import com.example.ui.screens.CaseDetailScreen
import com.example.ui.screens.CaseHistoryScreen
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.InvestigationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FraudShieldTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

enum class AppTab(val label: String) {
    INVESTIGATE("Investigate"),
    HISTORY("History"),
    EMERGENCY("Emergency"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: FraudShieldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            FirebaseApp.initializeApp(this)
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
        } catch (e: Exception) {
            // Safe initialization if services config is partial
        }

        setContent {
            FraudShieldTheme {
                FraudShieldApp(viewModel)
            }
        }
    }
}

@Composable
fun FraudShieldApp(viewModel: FraudShieldViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.INVESTIGATE) }
    var viewingEmergencyDirectly by remember { mutableStateOf(false) }
    val activeResult by viewModel.activeResult.collectAsState()

    if (viewingEmergencyDirectly) {
        EmergencyScreen(onBack = { viewingEmergencyDirectly = false })
        return
    }

    if (activeResult != null) {
        CaseDetailScreen(
            viewModel = viewModel,
            onBack = { viewModel.clearActiveResult() },
            onNavigateEmergency = {
                viewModel.clearActiveResult()
                viewingEmergencyDirectly = true
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = (selectedTab == AppTab.INVESTIGATE),
                    onClick = { selectedTab = AppTab.INVESTIGATE },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Investigate") },
                    label = { Text("Investigate") },
                    modifier = Modifier.testTag("nav_tab_investigate")
                )
                NavigationBarItem(
                    selected = (selectedTab == AppTab.HISTORY),
                    onClick = { selectedTab = AppTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    modifier = Modifier.testTag("nav_tab_history")
                )
                NavigationBarItem(
                    selected = (selectedTab == AppTab.EMERGENCY),
                    onClick = { selectedTab = AppTab.EMERGENCY },
                    icon = { Icon(Icons.Default.Emergency, contentDescription = "Emergency") },
                    label = { Text("Emergency") },
                    modifier = Modifier.testTag("nav_tab_emergency")
                )
                NavigationBarItem(
                    selected = (selectedTab == AppTab.SETTINGS),
                    onClick = { selectedTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.INVESTIGATE -> InvestigationScreen(
                    viewModel = viewModel,
                    onNavigateEmergency = { viewingEmergencyDirectly = true },
                    onResultAvailable = { /* handled by activeResult != null */ }
                )
                AppTab.HISTORY -> CaseHistoryScreen(
                    viewModel = viewModel,
                    onCaseSelected = { case -> viewModel.selectCaseForDetail(case) }
                )
                AppTab.EMERGENCY -> EmergencyScreen(onBack = { selectedTab = AppTab.INVESTIGATE })
                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
