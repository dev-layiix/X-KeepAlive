package app.xkeepalive.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.xkeepalive.MainActivity
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.permissions.PermissionGateway
import app.xkeepalive.service.MonitorService
import app.xkeepalive.shizuku.ShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the widget switch. Turning off is always safe. Turning on reuses
 * the same preconditions as the in-app switch; when anything is missing the
 * app is opened instead so the user can complete the step there.
 */
class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WidgetUpdater.ACTION_TOGGLE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                toggle(context.applicationContext)
            } catch (_: Throwable) {
                // Never crash the broadcast delivery.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun toggle(appContext: Context) {
        val container = (appContext as? XBackgroundApp)?.container
        val settings = container?.settings ?: SettingsRepository(appContext)
        val snapshot = settings.snapshot()
        val on = snapshot.automationEnabled && snapshot.watched.any { it.enabled }
        if (on) {
            settings.setAutomation(false)
            MonitorService.stop(appContext)
        } else {
            val permissions = container?.permissions ?: PermissionGateway(appContext)
            val shizuku = container?.shizuku ?: ShizukuBridge(appContext)
            val ready = snapshot.watched.any { it.enabled } &&
                snapshot.consentAccepted &&
                permissions.hasUsageAccess() &&
                permissions.hasNotifications() &&
                shizuku.snapshot().ready
            if (!ready) {
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                settings.setAutomation(true)
                MonitorService.start(appContext)
            }
        }
        WidgetUpdater.refreshNow(appContext)
    }
}
