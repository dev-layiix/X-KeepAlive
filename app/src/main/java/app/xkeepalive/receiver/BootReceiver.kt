package app.xkeepalive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.R
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.widget.WidgetUpdater
import app.xkeepalive.data.EventLogRepository
import app.xkeepalive.permissions.PermissionGateway
import app.xkeepalive.service.MonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (appContext as? XBackgroundApp)?.container
                val settings = container?.settings ?: SettingsRepository(appContext)
                val events = container?.events ?: EventLogRepository(appContext)
                val permissions = container?.permissions ?: PermissionGateway(appContext)
                if (container == null) events.load()
                val snapshot = settings.snapshot()
                val games = snapshot.watched.filter { it.enabled }
                if (!snapshot.automationEnabled || !snapshot.restoreOnBoot || games.isEmpty()) {
                    events.append(
                        "BOOT",
                        appContext.getString(R.string.log_boot_idle),
                    )
                    return@launch
                }
                if (!permissions.hasUsageAccess() || !permissions.hasNotifications()) {
                    events.append(
                        "BOOT",
                        appContext.getString(R.string.log_boot_no_permissions),
                    )
                    return@launch
                }
                val shizuku = container?.shizuku?.snapshot()
                if (shizuku?.ready != true) {
                    // Shizuku usually starts after this receiver at boot. The
                    // automation flag is left untouched so the user can start
                    // manually once Shizuku is up; disabling it here forced a
                    // full re-setup after every reboot.
                    events.append(
                        "BOOT",
                        appContext.getString(R.string.log_boot_no_shizuku),
                    )
                    return@launch
                }
                MonitorService.start(appContext)
                events.append(
                    "BOOT",
                    appContext.getString(R.string.log_boot_restarted, games.joinToString { it.label }),
                )
                // The widget comes back with the launcher; realign it with reality.
                WidgetUpdater.refresh(appContext)
            } catch (throwable: Throwable) {
                // Boot must not crash the system process that delivers the broadcast.
                throwable.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
