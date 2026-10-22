package app.xkeepalive.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import app.xkeepalive.MainActivity
import app.xkeepalive.R
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.service.MonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Premium X-KeepAlive home widget. Mirrors the real monitoring switch:
 * tapping the toggle turns the service on/off, the gear opens the app
 * settings, and the icons launch the monitored games. State and count are
 * always read from the real data, never simulated.
 *
 * Note: system widgets cannot run continuous animations, so the "glow" is
 * rendered as a static state (blue halo when active, dim when stopped) and
 * redrawn on every state change with no polling and no extra battery cost.
 */
object WidgetUpdater {
    const val ACTION_TOGGLE = "app.xkeepalive.WIDGET_TOGGLE"
    const val EXTRA_TAB = "open_tab"

    private val appSlots = intArrayOf(
        R.id.widget_app_0,
        R.id.widget_app_1,
        R.id.widget_app_2,
        R.id.widget_app_3,
    )

    fun refresh(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshNow(context.applicationContext)
            } catch (_: Throwable) {
                // Widgets must never crash the app.
            }
        }
    }

    suspend fun refreshNow(appContext: Context) {
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(ComponentName(appContext, KeepAliveWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val container = (appContext as? XBackgroundApp)?.container
        val settings = container?.settings?.snapshot()
            ?: SettingsRepository(appContext).snapshot()
        val enabled = settings.watched.filter { it.enabled }
        val on = settings.automationEnabled && enabled.isNotEmpty()
        val running = MonitorService.running.get()
        val active = on && running
        ids.forEach { widgetId ->
            val views = RemoteViews(appContext.packageName, R.layout.widget_keepalive)
            views.setImageViewBitmap(R.id.widget_logo, makeLogo(appContext, active))
            views.setImageViewResource(
                R.id.widget_dot,
                if (active) R.drawable.widget_dot_on else R.drawable.widget_dot_off,
            )
            views.setTextViewText(
                R.id.widget_status,
                appContext.getString(
                    if (active) R.string.widget_status_on else R.string.widget_status_off,
                ),
            )
            val countText = appContext.resources.getQuantityString(
                R.plurals.widget_watched,
                enabled.size,
                enabled.size,
            )
            views.setTextViewText(R.id.widget_count_bottom, countText)
            // Legacy id kept hidden for old cached instances after app updates.
            views.setTextViewText(R.id.widget_count, countText)
            views.setImageViewResource(
                R.id.widget_toggle,
                if (on) R.drawable.widget_toggle_on else R.drawable.widget_toggle_off,
            )
            views.setContentDescription(
                R.id.widget_toggle,
                appContext.getString(if (on) R.string.widget_on else R.string.widget_off),
            )
            views.setTextViewText(
                R.id.widget_toggle_label,
                appContext.getString(if (on) R.string.widget_on else R.string.widget_off),
            )
            bindQuickAccess(appContext, views, enabled.map { it.packageName })
            // Only the gear opens the app (Settings tab) and the switch toggles.
            // Taps anywhere else do nothing; game icons launch their game.
            views.setOnClickPendingIntent(R.id.widget_settings, openApp(appContext, "settings"))
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle(appContext))
            views.setOnClickPendingIntent(R.id.widget_toggle_label, toggle(appContext))
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun bindQuickAccess(appContext: Context, views: RemoteViews, packages: List<String>) {
        val pm = appContext.packageManager
        appSlots.forEachIndexed { index, viewId ->
            val packageName = packages.getOrNull(index)
            val launch = packageName?.let {
                runCatching { pm.getLaunchIntentForPackage(it) }.getOrNull()
            }
            if (packageName == null || launch == null) {
                views.setViewVisibility(viewId, View.GONE)
                return@forEachIndexed
            }
            val icon = runCatching {
                pm.getApplicationIcon(packageName).toBitmap(96, 96)
            }.getOrNull()
            if (icon == null) {
                views.setViewVisibility(viewId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setImageViewBitmap(viewId, icon)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getActivity(
                    appContext,
                    200 + index,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    /**
     * Circular logo with a luminous ring: electric blue halo when the service
     * is really running, dim graphite when it is stopped.
     */
    private fun makeLogo(appContext: Context, active: Boolean): Bitmap {
        val size = 156
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val center = size / 2f
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (active) 0x402E7DE1 else 0x221E2733
        }
        canvas.drawCircle(center, center, center, halo)
        val ringWidth = size * 0.045f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            color = if (active) 0xFF4DD8FF.toInt() else 0xFF3A4656.toInt()
        }
        val ringRadius = center - ringWidth
        canvas.drawCircle(center, center, ringRadius, ring)
        val logo = runCatching {
            trimTransparent(BitmapFactory.decodeResource(appContext.resources, R.drawable.ic_keepalive_logo))
        }.getOrNull()
        if (logo != null) {
            // Fits the WHOLE artwork inside the ring (contain, never crop), so
            // texts at the edges are never cut off.
            val inner = (ringRadius - ringWidth) * 2f
            val scale = minOf(inner / logo.width, inner / logo.height)
            val dw = logo.width * scale
            val dh = logo.height * scale
            val dst = android.graphics.RectF(
                center - dw / 2f,
                center - dh / 2f,
                center + dw / 2f,
                center + dh / 2f,
            )
            canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        }
        return out
    }

    /**
     * Cuts uneven transparent borders off the source asset so the artwork is
     * truly centered inside the ring on every launcher.
     */
    private fun trimTransparent(src: Bitmap?): Bitmap? {
        if (src == null) return null
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return src
        val contentW = right - left + 1
        val contentH = bottom - top + 1
        val side = maxOf(contentW, contentH)
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val cropLeft = (centerX - side / 2).coerceIn(0, w - side)
        val cropTop = (centerY - side / 2).coerceIn(0, h - side)
        return Bitmap.createBitmap(src, cropLeft, cropTop, side, side)
    }

    private fun openApp(context: Context, tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TAB, tab)
        }
        return PendingIntent.getActivity(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toggle(context: Context): PendingIntent {
        val intent = Intent(context, WidgetToggleReceiver::class.java).setAction(ACTION_TOGGLE)
        return PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
