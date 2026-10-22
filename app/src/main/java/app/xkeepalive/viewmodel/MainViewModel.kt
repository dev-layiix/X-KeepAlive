package app.xkeepalive.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.R
import app.xkeepalive.apps.AppUsage
import app.xkeepalive.apps.InstalledApp
import app.xkeepalive.apps.InstalledApps
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.TuneAdvice
import app.xkeepalive.core.TuneAdviceKind
import app.xkeepalive.core.TuneAdvisor
import app.xkeepalive.core.TunePlan
import app.xkeepalive.core.WatchedGames
import app.xkeepalive.data.UserSettings
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.i18n.AppLocale
import app.xkeepalive.i18n.LanguageStore
import app.xkeepalive.service.MonitorService
import app.xkeepalive.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as XBackgroundApp).container
    private var pendingEnable = false
    private var pendingPackage: String? = null
    private var languageJob: Job? = null
    private val toggles = Mutex()

    val settings = container.settings.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )
    val session = container.session.state
    val events = container.events.events

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading = _appsLoading.asStateFlow()

    private val _advice = MutableStateFlow<List<TuneAdvice>>(emptyList())
    val advice = _advice.asStateFlow()

    private val _tuneBusy = MutableStateFlow<Set<String>>(emptySet())
    val tuneBusy = _tuneBusy.asStateFlow()

    /** Serializes taps: while key is busy the UI disables its button. */
    private suspend fun runTuneOp(key: String, block: suspend () -> Unit) {
        if (key in _tuneBusy.value) return
        _tuneBusy.value += key
        try {
            block()
        } finally {
            _tuneBusy.value -= key
        }
    }

    private val _consentVisible = MutableStateFlow(false)
    val consentVisible = _consentVisible.asStateFlow()

    private val _askNotification = MutableStateFlow(false)
    val askNotification = _askNotification.asStateFlow()

    private val _batteryPrompt = MutableStateFlow<BatteryPrompt?>(null)
    val batteryPrompt = _batteryPrompt.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _restartToken = MutableStateFlow(0)
    val restartToken = _restartToken.asStateFlow()
    private fun text(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    init {
        // Keeps the synchronous boot copy aligned with DataStore. A blank code
        // means first launch: the language picker is shown, nothing is
        // auto-detected from the system.
        viewModelScope.launch {
            val current = container.settings.snapshot()
            if (current.languageCode.isNotBlank()) {
                LanguageStore.write(getApplication(), current.languageCode)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val permissions = container.permissions.snapshot()
                val shizuku = container.shizuku.snapshot()
                container.session.update {
                    it.copy(
                        permissions = permissions,
                        shizuku = shizuku,
                        serviceRunning = MonitorService.running.get(),
                    )
                }
                delay(1_500)
            }
        }
        refreshApps()
        viewModelScope.launch {
            val current = container.settings.snapshot()
            if (current.automationEnabled) return@launch
            current.policies.map { it.packageName }.filter { PackageNames.isValid(it) }.forEach { packageName ->
                val report = container.policy.revert(packageName)
                val restored = report.mode == "REVERT" && report.anyVerified
                container.events.append(
                    "POLICY",
                    if (restored) {
                        text(R.string.msg_leftover_restored, report.packageName)
                    } else {
                        text(R.string.msg_leftover_not_restored, report.packageName, report.mode)
                    },
                )
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _appsLoading.value = true
            val loaded = withContext(Dispatchers.Default) {
                InstalledApps.load(getApplication())
            }
            _apps.value = loaded
            _appsLoading.value = false
            refreshAdvice()
        }
    }

    /**
     * Real-usage recommendations: which apps to restrict or disable, based on
     * foreground minutes and preinstalled flag. Monitored games, this app and
     * apps already handled are never suggested.
     */
    fun refreshAdvice() {
        viewModelScope.launch {
            if (!container.permissions.hasUsageAccess()) {
                _advice.value = emptyList()
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                val usage = AppUsage.last7Days(getApplication())
                val snapshot = container.settings.snapshot()
                val watched = snapshot.watched.map { it.packageName }.toSet()
                val drained = TunePlan.decodeDrain(snapshot.drainedApps).map { it.packageName }.toSet()
                val disabled = TunePlan.decodePackages(snapshot.disabledByUs)
                val now = System.currentTimeMillis()
                _apps.value.mapNotNull { app ->
                    val info = usage[app.packageName]
                    val agoDays = info
                        ?.takeIf { it.lastUsedMs > 0L }
                        ?.let { (now - it.lastUsedMs) / 86_400_000L }
                    TuneAdvisor.advise(
                        packageName = app.packageName,
                        isSystem = app.isSystem,
                        foregroundMinutes7d = info?.foregroundMinutes7d ?: 0L,
                        lastUsedAgoDays = agoDays,
                        ownPackage = getApplication<Application>().packageName,
                        watchedPackages = watched,
                        alreadyHandled = app.packageName in drained || app.packageName in disabled,
                    )
                }.sortedWith(
                    compareBy<TuneAdvice> { if (it.kind == TuneAdviceKind.DISABLE) 0 else 1 }
                        .thenBy { it.foregroundMinutes7d },
                ).take(10)
            }
            _advice.value = result
        }
    }

    fun addGame(app: InstalledApp) {
        viewModelScope.launch {
            if (!PackageNames.isValid(app.packageName)) {
                _messages.emit(text(R.string.msg_package_invalid))
                return@launch
            }
            val current = container.settings.snapshot()
            if (current.watched.any { it.packageName == app.packageName }) {
                _messages.emit(text(R.string.msg_already_in_list, app.label))
                return@launch
            }
            if (current.watched.size >= WatchedGames.MAX_LIST) {
                _messages.emit(text(R.string.msg_list_full))
                return@launch
            }
            val added = container.settings.addWatched(app.packageName, app.label)
            if (!added) {
                _messages.emit(text(R.string.msg_already_in_list, app.label))
                return@launch
            }
            _messages.emit(text(R.string.msg_added, app.label))
            // Only asks when background use is really off for this game. When
            // it is already allowed, there is nothing to open.
            if (!container.permissions.isBackgroundUnrestricted(app.packageName)) {
                _batteryPrompt.value = BatteryPrompt(app.packageName, app.label)
            }
        }
    }

    fun removeGame(packageName: String) {
        viewModelScope.launch {
            toggles.withLock {
                container.settings.setWatchedEnabled(packageName, false)
                container.settings.removeWatched(packageName)
                stopIfIdle()
            }
        }
    }

    fun setGroupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            toggles.withLock {
                val watched = container.settings.snapshot().watched
                if (!enabled) {
                    if (watched.none { it.enabled }) return@withLock
                    stopService()
                    return@withLock
                }
                if (watched.none { it.enabled }) {
                    _messages.emit(text(R.string.msg_group_hint))
                    return@withLock
                }
                setAutomationLocked()
            }
        }
    }

    fun setGameEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            toggles.withLock { setGameEnabledLocked(packageName, enabled) }
        }
    }

    private suspend fun setGameEnabledLocked(packageName: String, enabled: Boolean) {
            val current = container.settings.snapshot()
            val game = current.watched.firstOrNull { it.packageName == packageName } ?: return
            if (enabled && !WatchedGames.canEnable(current.watched, packageName)) {
                _messages.emit(text(R.string.msg_max_active))
                return
            }
            if (!enabled) {
                container.settings.setWatchedEnabled(packageName, false)
                stopIfIdle()
                return
            }
            if (!current.consentAccepted) {
                pendingPackage = packageName
                _consentVisible.value = true
                return
            }
            if (!container.permissions.hasUsageAccess()) {
                _messages.emit(text(R.string.msg_grant_usage))
                container.permissions.openUsageAccess()
                return
            }
            if (!container.permissions.hasNotifications()) {
                pendingPackage = packageName
                pendingEnable = true
                _askNotification.value = true
                return
            }
            container.settings.setWatchedEnabled(packageName, true)
            // The snapshot above is stale by design: enabling a game must
            // always (re)start monitoring, even when the master switch was
            // off. Otherwise the game shows ON while the service stays stopped.
            container.settings.setAutomation(true)
            startMonitoring()
    }

    fun setAutomation(enabled: Boolean) {
        viewModelScope.launch {
            toggles.withLock {
                if (!enabled) {
                    stopIfIdle()
                    return@withLock
                }
                setAutomationLocked()
            }
        }
    }

    private suspend fun setAutomationLocked() {
        val current = container.settings.snapshot()
        if (current.watched.none { it.enabled } && !PackageNames.isValid(current.selectedPackage)) {
            _messages.emit(text(R.string.msg_add_game_first))
            return
        }
        if (!current.consentAccepted) {
            _consentVisible.value = true
            return
        }
        if (!container.permissions.hasUsageAccess()) {
            _messages.emit(text(R.string.msg_grant_usage))
            container.permissions.openUsageAccess()
            return
        }
        if (!container.permissions.hasNotifications()) {
            pendingEnable = true
            _askNotification.value = true
            return
        }
        startMonitoring()
    }

    fun confirmConsent() {
        viewModelScope.launch {
            _consentVisible.value = false
            container.settings.setConsentAccepted()
            val packageName = pendingPackage
            pendingPackage = null
            toggles.withLock {
                if (packageName != null) setGameEnabledLocked(packageName, true) else setAutomationLocked()
            }
        }
    }

    fun dismissConsent() {
        _consentVisible.value = false
    }

    fun onNotificationResult(granted: Boolean) {
        _askNotification.value = false
        if (!pendingEnable) return
        pendingEnable = false
        val packageName = pendingPackage
        pendingPackage = null
        if (!granted) {
            viewModelScope.launch {
                _messages.emit(text(R.string.msg_no_notification))
            }
            return
        }
        viewModelScope.launch {
            toggles.withLock {
                if (packageName != null) setGameEnabledLocked(packageName, true) else setAutomationLocked()
            }
        }
    }

    fun requestShizukuPermission() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (!Shizuku.pingBinder()) {
                    _messages.emit(text(R.string.msg_shizuku_not_running))
                    return@launch
                }
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _messages.emit(text(R.string.msg_shizuku_already))
                    return@launch
                }
                Shizuku.requestPermission(SHIZUKU_REQUEST)
            } catch (throwable: Throwable) {
                _messages.emit(throwable.message ?: text(R.string.msg_shizuku_request_failed))
            }
        }
    }

    fun setLanguage(code: String) {
        applyLanguage(code, 600)
    }

    /** First-launch picker: saves faster, the restart brings up the setup. */
    fun selectLanguage(code: String) {
        applyLanguage(code, 300)
    }

    private fun applyLanguage(code: String, debounceMs: Long) {
        if (!AppLanguages.isSupported(code)) return
        // Rapid taps are debounced into a single restart so the app never
        // freezes. The new language is saved first, then the whole app
        // restarts and comes back directly in the new locale.
        AppLocale.pendingOverride = code
        languageJob?.cancel()
        languageJob = viewModelScope.launch {
            delay(debounceMs)
            container.settings.setLanguageCode(code)
            LanguageStore.write(getApplication(), code)
            _restartToken.value += 1
        }
    }

    fun setRestoreOnBoot(enabled: Boolean) {
        viewModelScope.launch { container.settings.setRestoreOnBoot(enabled) }
    }

    fun completeSetup() {
        viewModelScope.launch { container.settings.setSetupCompleted(true) }
    }

    fun setPollInterval(intervalMs: Long) {
        viewModelScope.launch { container.settings.setPollInterval(intervalMs) }
    }

    fun setFastAnims(enabled: Boolean) {
        viewModelScope.launch {
            runTuneOp("anims") {
                val report = container.tune.setFastAnims(enabled)
                _messages.emit(
                    if (report.ok) {
                        text(if (enabled) R.string.msg_tune_anims_on else R.string.msg_tune_anims_off)
                    } else {
                        report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed)
                    },
                )
            }
        }
    }

    fun addDrainApp(app: InstalledApp) {
        viewModelScope.launch {
            runTuneOp("drain:${app.packageName}") {
                val report = container.tune.addDrain(app.packageName)
                _messages.emit(
                    if (report.ok) text(R.string.msg_tune_drain_on, app.label)
                    else report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed),
                )
                refreshAdvice()
            }
        }
    }

    fun removeDrainApp(packageName: String) {
        viewModelScope.launch {
            runTuneOp("undrain:$packageName") {
                val report = container.tune.removeDrain(packageName)
                if (!report.ok) {
                    _messages.emit(report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed))
                }
            }
        }
    }

    fun disableTuneApp(app: InstalledApp) {
        viewModelScope.launch {
            runTuneOp("bloat:${app.packageName}") {
                val report = container.tune.disableApp(app.packageName)
                _messages.emit(
                    if (report.ok) text(R.string.msg_tune_disabled, app.label)
                    else report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed),
                )
                refreshAdvice()
            }
        }
    }

    fun enableTuneApp(packageName: String) {
        viewModelScope.launch {
            runTuneOp("unbloat:$packageName") {
                val report = container.tune.enableApp(packageName)
                _messages.emit(
                    if (report.ok) text(R.string.msg_tune_enabled, packageName)
                    else report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed),
                )
            }
        }
    }

    /** One tap applies every recommendation: disables + restricts in a row. */
    fun applyAllAdvice() {
        viewModelScope.launch {
            runTuneOp("batch") {
                var disabled = 0
                var restricted = 0
                var failed = 0
                _advice.value.toList().forEach { item ->
                    val app = _apps.value.firstOrNull { it.packageName == item.packageName }
                    val ok = if (app == null) {
                        false
                    } else if (item.kind == TuneAdviceKind.DISABLE) {
                        container.tune.disableApp(app.packageName).ok
                    } else {
                        container.tune.addDrain(app.packageName).ok
                    }
                    if (ok) {
                        if (item.kind == TuneAdviceKind.DISABLE) disabled++ else restricted++
                    } else {
                        failed++
                    }
                }
                refreshAdvice()
                _messages.emit(text(R.string.msg_tune_batch, disabled, restricted, failed))
            }
        }
    }

    fun trimCaches() {
        viewModelScope.launch {
            runTuneOp("cache") {
                val report = container.tune.trimCaches()
                _messages.emit(
                    if (report.ok) text(R.string.msg_tune_cache)
                    else report.operations.firstOrNull()?.detail ?: text(R.string.msg_tune_failed),
                )
            }
        }
    }

    fun clearLog() {
        viewModelScope.launch { container.events.clear() }
    }

    fun openUsageAccess() = container.permissions.openUsageAccess()

    fun openOwnBattery() {
        // Checks first: if Unrestricted is already active, there is nothing to open.
        if (container.permissions.isIgnoringBatteryOptimizations()) {
            viewModelScope.launch {
                _messages.emit(text(R.string.msg_already_unrestricted))
            }
            return
        }
        container.permissions.openOwnBatterySettings()
    }

    fun openGameBatteryForGuide() {
        viewModelScope.launch {
            val snapshot = container.settings.snapshot()
            val packageName = snapshot.watched.firstOrNull { it.enabled }?.packageName
                ?: snapshot.watched.firstOrNull()?.packageName
                ?: snapshot.selectedPackage
            if (!PackageNames.isValid(packageName)) {
                _messages.emit(text(R.string.msg_add_game_first))
                return@launch
            }
            container.settings.setGameBatteryHintDone()
            container.permissions.openGamePowerSettings(packageName)
        }
    }

    fun confirmBatteryPrompt() {
        val prompt = _batteryPrompt.value ?: return
        _batteryPrompt.value = null
        viewModelScope.launch {
            container.settings.setGameBatteryHintDone()
            container.permissions.openGamePowerSettings(prompt.packageName)
        }
    }

    fun dismissBatteryPrompt() {
        _batteryPrompt.value = null
    }

    fun openGamePower() {
        viewModelScope.launch {
            val packageName = container.settings.snapshot().selectedPackage
            if (!PackageNames.isValid(packageName)) {
                _messages.emit(text(R.string.msg_choose_game_first))
                return@launch
            }
            container.permissions.openGamePowerSettings(packageName)
        }
    }

    fun openDeveloperOptions() {
        if (!container.permissions.openDeveloperOptions()) {
            viewModelScope.launch {
                _messages.emit(text(R.string.msg_dev_options_failed))
            }
        }
    }

    fun openAboutPhone() {
        if (!container.permissions.openAboutPhone()) {
            viewModelScope.launch { _messages.emit(text(R.string.msg_about_failed)) }
        }
    }

    fun requestNotifications() {
        if (container.permissions.hasNotifications()) return
        _askNotification.value = true
    }

    fun openShizukuApp() {
        if (!container.permissions.openShizuku()) {
            viewModelScope.launch { _messages.emit(text(R.string.msg_shizuku_missing)) }
        }
    }

    fun openShizukuStore() {
        if (!container.permissions.openShizukuStore()) {
            viewModelScope.launch { _messages.emit(text(R.string.msg_store_failed)) }
        }
    }

    fun recordTest(result: TestRecord) {
        viewModelScope.launch {
            container.events.append(
                "TEST",
                listOf(
                    text(R.string.test_wait, result.waitLabel),
                    text(R.string.test_foreground, result.foregroundApp),
                    text(R.string.test_process, result.processResult),
                    text(R.string.test_service, result.serviceResult),
                    text(R.string.test_shizuku, result.shizukuResult),
                    result.notes.take(300),
                ).filter { it.isNotBlank() }.joinToString(" | "),
            )
            _messages.emit(text(R.string.msg_test_saved))
        }
    }

    private suspend fun stopService() {
        container.settings.setAutomation(false)
        try {
            container.stopMonitor()
        } catch (throwable: Throwable) {
            _messages.emit(throwable.message ?: text(R.string.msg_stop_failed))
        }
        WidgetUpdater.refresh(getApplication())
    }

    private suspend fun stopIfIdle() {
        val enabledNow = container.settings.snapshot().watched.any { game ->
            game.enabled && PackageNames.isValid(game.packageName)
        }
        if (enabledNow) return
        container.settings.setAutomation(false)
        val enabledAfter = container.settings.snapshot().watched.any { game ->
            game.enabled && PackageNames.isValid(game.packageName)
        }
        if (enabledAfter) {
            container.settings.setAutomation(true)
            container.startMonitor()
            return
        }
        try {
            container.stopMonitor()
        } catch (throwable: Throwable) {
            _messages.emit(throwable.message ?: text(R.string.msg_stop_failed))
        }
        WidgetUpdater.refresh(getApplication())
    }

    private suspend fun startMonitoring() {
        if (!shizukuReadyForService()) {
            container.settings.setAutomation(false)
            return
        }
        try {
            container.settings.setAutomation(true)
            container.startMonitor()
        } catch (throwable: Throwable) {
            container.settings.setAutomation(false)
            _messages.emit(throwable.message ?: text(R.string.msg_start_failed))
        }
        WidgetUpdater.refresh(getApplication())
    }

    private suspend fun shizukuReadyForService(): Boolean {
        val shizuku = container.shizuku.snapshot()
        if (shizuku.ready) return true
        _messages.emit(
            when {
                !shizuku.managerInstalled -> text(R.string.msg_svc_no_install)
                !shizuku.binderAlive -> text(R.string.msg_svc_not_running)
                else -> text(R.string.msg_svc_no_auth)
            },
        )
        return false
    }

    companion object {
        const val SHIZUKU_REQUEST = 42
    }
}

data class BatteryPrompt(
    val packageName: String,
    val label: String,
)

data class TestRecord(    val waitLabel: String,
    val foregroundApp: String,
    val processResult: String,
    val serviceResult: String,
    val shizukuResult: String,
    val notes: String,
)
