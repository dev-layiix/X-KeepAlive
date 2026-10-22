package app.xkeepalive.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.xkeepalive.R
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.i18n.AppRestart
import app.xkeepalive.ui.guide.GuideScreen
import app.xkeepalive.ui.home.HomeScreen
import app.xkeepalive.ui.log.LogScreen
import app.xkeepalive.ui.settings.SettingsScreen
import app.xkeepalive.ui.setup.LanguageScreen
import app.xkeepalive.ui.setup.SetupScreen
import app.xkeepalive.ui.test.ProtocolScreen
import app.xkeepalive.ui.tune.TuneScreen
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Ink
import app.xkeepalive.ui.theme.Line
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Panel
import app.xkeepalive.viewmodel.MainViewModel

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
private fun tabs(): List<Tab> = listOf(
    Tab("home", R.string.nav_home, Icons.Filled.Home),
    Tab("tune", R.string.nav_tune, Icons.Filled.Star),
    Tab("guide", R.string.nav_guide, Icons.Filled.Info),
    Tab("log", R.string.nav_log, Icons.Filled.Menu),
    Tab("settings", R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun XBackgroundRoot(viewModel: MainViewModel, initialTab: String = "home") {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val consent by viewModel.consentVisible.collectAsStateWithLifecycle()
    val askNotification by viewModel.askNotification.collectAsStateWithLifecycle()
    val restartToken by viewModel.restartToken.collectAsStateWithLifecycle()
    val batteryPrompt by viewModel.batteryPrompt.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationResult(granted) }

    LaunchedEffect(askNotification) {
        if (askNotification && Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }
    // Applies a manual language change with a full app restart, so every
    // screen, dialog and notification comes back in the new locale.
    LaunchedEffect(restartToken) {
        if (restartToken > 0) {
            AppRestart.restart(context)
        }
    }
    // First launch shows the language picker (no system auto-detect), then the
    // step-by-step setup. Once completed, the app starts on Home, or on the
    // tab requested by a deep link (widget gear icon).
    val validTabs = setOf("home", "tune", "guide", "log", "settings")
    val homeTab = if (initialTab in validTabs) initialTab else "home"
    val startDestination = when {
        !AppLanguages.isSupported(settings.languageCode) -> "language"
        !settings.setupCompleted -> "setup"
        else -> homeTab
    }
    LaunchedEffect(settings.setupCompleted) {
        if (settings.setupCompleted && route == "setup") {
            nav.navigate("home") {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = Ink,
        contentColor = Color(0xFFE8EEF4),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route != "protocol" && route != "setup" && route != "language") {
                Column {
                    HorizontalDivider(color = Line, thickness = 1.dp)
                    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                    tabs().forEach { tab ->
                        val label = stringResource(tab.labelRes)
                        NavigationBarItem(
                            selected = route == tab.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF041018),
                                selectedTextColor = Accent,
                                indicatorColor = Accent,
                                unselectedIconColor = Mist,
                                unselectedTextColor = Mist,
                            ),
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = label,
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable("language") {
                LanguageScreen(onSelect = viewModel::selectLanguage)
            }
            composable("setup") {
                SetupScreen(
                    settings = settings,
                    session = session,
                    onAboutPhone = viewModel::openAboutPhone,
                    onDeveloperOptions = viewModel::openDeveloperOptions,
                    onOpenShizuku = viewModel::openShizukuApp,
                    onInstallShizuku = viewModel::openShizukuStore,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onUsageAccess = viewModel::openUsageAccess,
                    onNotifications = viewModel::requestNotifications,
                    onOwnBattery = viewModel::openOwnBattery,
                    onGameBattery = viewModel::openGameBatteryForGuide,
                    onOpenHome = {
                        viewModel.completeSetup()
                        nav.navigate("home") {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onFinish = {
                        viewModel.completeSetup()
                        nav.navigate("home") {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable("home") {
                HomeScreen(
                    settings = settings,
                    session = session,
                    events = events,
                    apps = apps,
                    appsLoading = appsLoading,
                    onGameEnabled = viewModel::setGameEnabled,
                    onAdd = viewModel::addGame,
                    onRemove = viewModel::removeGame,
                    onGroupEnabled = viewModel::setGroupEnabled,
                    onRefreshApps = viewModel::refreshApps,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onOpenShizuku = viewModel::openShizukuApp,
                    onInstallShizuku = viewModel::openShizukuStore,
                )
            }
            composable("tune") {
                val advice by viewModel.advice.collectAsStateWithLifecycle()
                val tuneBusy by viewModel.tuneBusy.collectAsStateWithLifecycle()
                TuneScreen(
                    settings = settings,
                    apps = apps,
                    appsLoading = appsLoading,
                    advice = advice,
                    usageAccess = session.permissions.usageAccess,
                    busy = tuneBusy,
                    onApplyAll = viewModel::applyAllAdvice,
                    onFastAnims = viewModel::setFastAnims,
                    onAddDrain = viewModel::addDrainApp,
                    onRemoveDrain = viewModel::removeDrainApp,
                    onDisable = viewModel::disableTuneApp,
                    onEnable = viewModel::enableTuneApp,
                    onTrim = viewModel::trimCaches,
                    onRefreshApps = viewModel::refreshApps,
                )
            }
            composable("guide") {
                GuideScreen(
                    settings = settings,
                    session = session,
                    onAboutPhone = viewModel::openAboutPhone,
                    onDeveloperOptions = viewModel::openDeveloperOptions,
                    onOpenShizuku = viewModel::openShizukuApp,
                    onInstallShizuku = viewModel::openShizukuStore,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onUsageAccess = viewModel::openUsageAccess,
                    onNotifications = viewModel::requestNotifications,
                    onOwnBattery = viewModel::openOwnBattery,
                    onGameBattery = viewModel::openGameBatteryForGuide,
                    onOpenHome = {
                        nav.navigate("home") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("log") {
                LogScreen(events = events, onClear = viewModel::clearLog)
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    session = session,
                    onRestore = viewModel::setRestoreOnBoot,
                    onPoll = viewModel::setPollInterval,
                    onUsage = viewModel::openUsageAccess,
                    onOwnBattery = viewModel::openOwnBattery,
                    onGamePower = viewModel::openGamePower,
                    onProtocol = { nav.navigate("protocol") },
                    onLanguage = viewModel::setLanguage,
                )
            }
            composable("protocol") {
                ProtocolScreen(
                    tests = events.filter { it.kind == "TEST" },
                    onSave = viewModel::recordTest,
                )
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsent,
            containerColor = Panel,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.consent_title)) },
            text = {
                Text(
                    stringResource(R.string.consent_body),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmConsent) { Text(stringResource(R.string.consent_start)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConsent) { Text(stringResource(R.string.consent_cancel)) } },
        )
    }

    batteryPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBatteryPrompt,
            containerColor = Panel,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.batt_popup_title)) },
            text = { Text(stringResource(R.string.batt_popup_body, prompt.label)) },
            confirmButton = { TextButton(onClick = viewModel::confirmBatteryPrompt) { Text(stringResource(R.string.batt_activate)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissBatteryPrompt) { Text(stringResource(R.string.batt_close)) } },
        )
    }
}
