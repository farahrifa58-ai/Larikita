package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.RunEntity
import com.example.domain.localization.AppStrings
import com.example.ui.screens.*
import com.example.ui.theme.LariKitaTheme
import com.example.ui.theme.LimeGreenPrimary
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

data class NavItem(
    val screen: AppScreen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
            val allRuns by viewModel.allCompletedRuns.collectAsStateWithLifecycle()
            val latestRun by viewModel.latestRun.collectAsStateWithLifecycle()
            val summaryRun by viewModel.summaryRun.collectAsStateWithLifecycle()
            val unfinishedRun by viewModel.unfinishedRun.collectAsStateWithLifecycle()
            val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
            val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(syncMessage) {
                syncMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearSyncMessage()
                }
            }

            // BackHandler for custom screen state stack
            BackHandler(enabled = currentScreen != AppScreen.BERANDA && currentScreen != AppScreen.LARI) {
                viewModel.navigateTo(AppScreen.BERANDA)
            }

            val navItems = listOf(
                NavItem(AppScreen.BERANDA, AppStrings.get("nav_home", userProfile.languageCode), Icons.Default.Home, Icons.Outlined.Home),
                NavItem(AppScreen.RIWAYAT, AppStrings.get("nav_history", userProfile.languageCode), Icons.Default.History, Icons.Outlined.History),
                NavItem(AppScreen.LARI, AppStrings.get("nav_run", userProfile.languageCode), Icons.Default.DirectionsRun, Icons.Outlined.DirectionsRun),
                NavItem(AppScreen.STATISTIK, AppStrings.get("nav_stats", userProfile.languageCode), Icons.Default.BarChart, Icons.Outlined.BarChart),
                NavItem(AppScreen.PROFIL, AppStrings.get("nav_profile", userProfile.languageCode), Icons.Default.Person, Icons.Outlined.Person)
            )

            var showSplash by remember { mutableStateOf(true) }

            LariKitaTheme(darkTheme = userProfile.isDarkMode) {
                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTablet = maxWidth >= 600.dp
                        val showBottomNav = currentScreen != AppScreen.LARI && currentScreen != AppScreen.RINGKASAN

                        if (isTablet) {
                        // Tablet Responsive Layout: Navigation Rail
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (showBottomNav) {
                                NavigationRail(
                                    modifier = Modifier.fillMaxHeight(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    header = {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsRun,
                                            contentDescription = null,
                                            tint = LimeGreenPrimary,
                                            modifier = Modifier
                                                .padding(vertical = 16.dp)
                                                .size(32.dp)
                                        )
                                    }
                                ) {
                                    navItems.forEach { item ->
                                        val isSelected = currentScreen == item.screen
                                        NavigationRailItem(
                                            selected = isSelected,
                                            onClick = {
                                                if (item.screen == AppScreen.LARI) {
                                                    viewModel.startRun(false)
                                                } else {
                                                    viewModel.navigateTo(item.screen)
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.title
                                                )
                                            },
                                            label = { Text(item.title, fontSize = 11.sp) },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = Color(0xFF09140D),
                                                indicatorColor = LimeGreenPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                MainContent(
                                    currentScreen = currentScreen,
                                    viewModel = viewModel,
                                    trackingState = trackingState,
                                    userProfile = userProfile,
                                    allRuns = allRuns,
                                    latestRun = latestRun,
                                    summaryRun = summaryRun,
                                    unfinishedRun = unfinishedRun,
                                    historyFilter = historyFilter
                                )
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        // Phone Layout: Bottom Navigation Bar
                        Scaffold(
                            bottomBar = {
                                if (showBottomNav) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 8.dp
                                    ) {
                                        navItems.forEach { item ->
                                            val isSelected = currentScreen == item.screen
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = {
                                                    if (item.screen == AppScreen.LARI) {
                                                        viewModel.startRun(false)
                                                    } else {
                                                        viewModel.navigateTo(item.screen)
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                        contentDescription = item.title,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = item.title,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = Color(0xFF09140D),
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    indicatorColor = LimeGreenPrimary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.testTag("nav_tab_${item.title.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            },
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                MainContent(
                                    currentScreen = currentScreen,
                                    viewModel = viewModel,
                                    trackingState = trackingState,
                                    userProfile = userProfile,
                                    allRuns = allRuns,
                                    latestRun = latestRun,
                                    summaryRun = summaryRun,
                                    unfinishedRun = unfinishedRun,
                                    historyFilter = historyFilter
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun MainContent(
    currentScreen: AppScreen,
    viewModel: MainViewModel,
    trackingState: com.example.domain.tracker.TrackingUiState,
    userProfile: com.example.data.model.UserProfile,
    allRuns: List<RunEntity>,
    latestRun: RunEntity?,
    summaryRun: RunEntity?,
    unfinishedRun: RunEntity?,
    historyFilter: com.example.ui.viewmodel.HistoryFilter
) {
    when (currentScreen) {
        AppScreen.BERANDA -> {
            HomeScreen(
                viewModel = viewModel,
                userProfile = userProfile,
                allRuns = allRuns,
                latestRun = latestRun,
                unfinishedRun = unfinishedRun,
                onStartRun = { isSim -> viewModel.startRun(isSim) },
                onResumeUnfinishedRun = { run -> viewModel.resumeUnfinishedRun(run) },
                onSaveUnfinishedRun = { run -> viewModel.saveUnfinishedRun(run) },
                onDiscardUnfinishedRun = { viewModel.discardUnfinishedRun() },
                onViewHistory = { viewModel.navigateTo(AppScreen.RIWAYAT) },
                onSelectRunDetail = { run ->
                    viewModel.selectDetailRun(run)
                    viewModel.navigateTo(AppScreen.RIWAYAT)
                }
            )
        }
        AppScreen.LARI -> {
            LiveTrackingScreen(
                trackingState = trackingState,
                onStartTracking = { isSim -> viewModel.startRun(isSim) },
                onPauseTracking = { viewModel.pauseRun() },
                onResumeTracking = { viewModel.resumeRun() },
                onFinishTracking = { viewModel.finishRun() },
                onCancelTracking = { viewModel.cancelRun() },
                onBackToHome = { viewModel.navigateTo(AppScreen.BERANDA) }
            )
        }
        AppScreen.RINGKASAN -> {
            RunSummaryScreen(
                run = summaryRun,
                onSaveRun = { title, notes ->
                    viewModel.updateSummaryRun(title, notes)
                },
                onViewHistory = { viewModel.navigateTo(AppScreen.RIWAYAT) },
                onBackHome = { viewModel.navigateTo(AppScreen.BERANDA) }
            )
        }
        AppScreen.RIWAYAT -> {
            HistoryScreen(
                runs = allRuns,
                currentFilter = historyFilter,
                onFilterChange = { viewModel.setHistoryFilter(it) },
                onDeleteRun = { viewModel.deleteRun(it) }
            )
        }
        AppScreen.STATISTIK -> {
            StatisticsScreen(
                viewModel = viewModel,
                runs = allRuns
            )
        }
        AppScreen.PROFIL -> {
            ProfileScreen(
                viewModel = viewModel,
                userProfile = userProfile,
                onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                onPopulateSample = { viewModel.populateSampleRuns() },
                onClearData = { viewModel.clearAllData() },
                onManualSync = { viewModel.performManualSync() }
            )
        }
    }
}
