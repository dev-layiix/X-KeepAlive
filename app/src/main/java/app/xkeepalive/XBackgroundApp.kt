package app.xkeepalive

import android.app.Application
import android.content.Context
import app.xkeepalive.data.EventLogRepository
import app.xkeepalive.data.SessionRepository
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.detect.ForegroundAppDetector
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.i18n.AppLocale
import app.xkeepalive.i18n.LanguageStore
import app.xkeepalive.permissions.PermissionGateway
import app.xkeepalive.service.MonitorService
import app.xkeepalive.shizuku.BackgroundPolicyExecutor
import app.xkeepalive.shizuku.PhoneTuneExecutor
import app.xkeepalive.shizuku.ShizukuBridge
import app.xkeepalive.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class XBackgroundApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        // The service, notifications and widgets inherit this base locale
        // (saved language, English fallback, no system auto-detect).
        // Uses the synchronous boot copy for the same reason as MainActivity.
        val stored = AppLocale.pendingOverride?.takeIf { AppLanguages.isSupported(it) }
            ?: LanguageStore.read(base).ifBlank {
                runBlocking { runCatching { SettingsRepository.readLanguageCode(base) }.getOrDefault("") }
            }
        super.attachBaseContext(AppLocale.wrap(base, AppLanguages.resolveStoredOrSystem(stored)))
    }

    override fun onCreate() {
        super.onCreate()
        // Shizuku also loads the Application in the shell process (uid 2000).
        // That process only runs the allowed commands.
        val uid = android.os.Process.myUid()
        if (uid == 2000 || uid == 0) return
        container = AppContainer(this)
        // Realigns the widget with the current app language on every start,
        // including right after a language change restart.
        WidgetUpdater.refresh(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsRepository(appContext)
    val events = EventLogRepository(appContext)
    val session = SessionRepository()
    val shizuku = ShizukuBridge(appContext)
    val policy = BackgroundPolicyExecutor(shizuku, settings)
    val tune = PhoneTuneExecutor(shizuku, settings, appContext.packageName)
    val detector = ForegroundAppDetector(appContext)
    val permissions = PermissionGateway(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { events.load() }
    }

    fun startMonitor() = MonitorService.start(appContext)

    fun stopMonitor() = MonitorService.stop(appContext)
}
