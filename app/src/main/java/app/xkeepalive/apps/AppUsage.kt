package app.xkeepalive.apps

import android.app.usage.UsageStatsManager
import android.content.Context

data class UsageInfo(
    val foregroundMinutes7d: Long,
    val lastUsedMs: Long,
)

/**
 * Real foreground usage over the last 7 days, from the usage-access permission
 * the app already holds. No battery percentages are invented: only minutes
 * the user actually spent in each app plus the last time it was opened.
 */
object AppUsage {
    private const val WINDOW_MS = 7L * 24 * 3_600_000L

    fun last7Days(context: Context): Map<String, UsageInfo> {
        val manager = context.applicationContext.getSystemService(UsageStatsManager::class.java)
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val raw = try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - WINDOW_MS, end)
        } catch (_: SecurityException) {
            return emptyMap()
        } ?: return emptyMap()
        val out = linkedMapOf<String, MutableUsage>()
        raw.forEach { stats ->
            val packageName = stats.packageName ?: return@forEach
            if (packageName.isBlank()) return@forEach
            val slot = out.getOrPut(packageName) { MutableUsage() }
            slot.foregroundMs += stats.totalTimeInForeground
            if (stats.lastTimeUsed > slot.lastUsedMs) slot.lastUsedMs = stats.lastTimeUsed
        }
        return out.mapValues { (_, slot) ->
            UsageInfo(slot.foregroundMs / 60_000L, slot.lastUsedMs)
        }
    }

    private class MutableUsage {
        var foregroundMs: Long = 0L
        var lastUsedMs: Long = 0L
    }
}
