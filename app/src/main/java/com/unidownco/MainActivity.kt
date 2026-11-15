package com.unidownco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.unidownco.core.design.components.UniBottomNavBar
import com.unidownco.core.design.components.UniNavTab
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.feature.details.VideoDetailsScreen
import com.unidownco.feature.home.HomeScreen
import com.unidownco.feature.home.HomeViewModel
import com.unidownco.feature.library.LibraryScreen
import com.unidownco.feature.library.LibraryViewModel
import com.unidownco.feature.queue.QueueScreen
import com.unidownco.feature.queue.QueueViewModel
import com.unidownco.feature.settings.SettingsScreen
import com.unidownco.feature.settings.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val queueViewModel: QueueViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()
        handleShareIntent(intent)

        setContent {
            UniDownTheme {
                MainAppContainer(
                    homeViewModel = homeViewModel,
                    queueViewModel = queueViewModel,
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                homeViewModel.onUrlChanged(sharedText)
                if (settingsViewModel.uiState.value.autoDownloadOnShare) {
                    homeViewModel.onStartAnalysis()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainAppContainer(
    homeViewModel: HomeViewModel,
    queueViewModel: QueueViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentTab by remember { mutableStateOf(UniNavTab.HOME) }
    var selectedDetailId by remember { mutableStateOf<Long?>(null) }
    val activeQueue by queueViewModel.activeQueue.collectAsState()
    val colors = UniDownTheme.colors

    Scaffold(
        bottomBar = {
            if (selectedDetailId == null) {
                UniBottomNavBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it },
                    queueBadgeCount = activeQueue.size
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
        ) {
            if (selectedDetailId != null) {
                VideoDetailsScreen(
                    downloadId = selectedDetailId!!,
                    viewModel = libraryViewModel,
                    onNavigateBack = { selectedDetailId = null }
                )
            } else {
                when (currentTab) {
                    UniNavTab.HOME -> HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToQueue = { currentTab = UniNavTab.QUEUE }
                    )
                    UniNavTab.QUEUE -> QueueScreen(
                        viewModel = queueViewModel
                    )
                    UniNavTab.LIBRARY -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onNavigateToDetails = { id -> selectedDetailId = id }
                    )
                    UniNavTab.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
