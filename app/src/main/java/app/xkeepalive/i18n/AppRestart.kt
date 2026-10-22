package app.xkeepalive.i18n

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.xkeepalive.MainActivity
import kotlin.system.exitProcess

/**
 * Restarts the whole app process so a new language applies everywhere with
 * zero leftovers. The language is already persisted before this runs, so the
 * fresh process starts directly in the new locale.
 *
 * Uses AlarmManager instead of a bare startActivity + exit: killing the
 * process immediately can swallow the relaunch intent and leave the app
 * closed. The alarm is registered with the system and survives our death.
 */
object AppRestart {
    fun restart(context: Context) {
        val appContext = context.applicationContext
        val launch = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pending = PendingIntent.getActivity(
            appContext,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            val alarm = appContext.getSystemService(AlarmManager::class.java)
            // set() needs no exact-alarm permission and is enough for ~1s.
            alarm?.set(AlarmManager.RTC, System.currentTimeMillis() + 800, pending)
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            exitProcess(0)
        }, 300)
    }
}
