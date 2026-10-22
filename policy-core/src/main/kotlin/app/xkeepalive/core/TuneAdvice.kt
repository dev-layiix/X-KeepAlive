package app.xkeepalive.core

/**
 * Honest in-app recommendations based on real usage (foreground minutes from
 * UsageStats + preinstalled flag). Returns null when the app should be left
 * alone, so the UI only shows genuine RESTRICT/DISABLE candidates. Monitored
 * games, the app itself and critical packages never get advice.
 */
enum class TuneAdviceKind {
    RESTRICT,
    DISABLE,
}

enum class TuneReason {
    NEVER_USED_PREINSTALLED,
    NEVER_USED,
    RARELY_USED,
}

data class TuneAdvice(
    val packageName: String,
    val kind: TuneAdviceKind,
    val reason: TuneReason,
    val foregroundMinutes7d: Long,
)

object TuneAdvisor {
    private const val RARE_MINUTES_7D = 15L
    private const val RARE_LAST_USED_DAYS = 7L
    private const val ABANDONED_DAYS = 30L

    /**
     * @param lastUsedAgoDays days since last foreground use, or null when the
     * app never appears in usage stats.
     * @param alreadyHandled true when the app is already drained or disabled
     * by us: nothing more to suggest.
     */
    fun advise(
        packageName: String,
        isSystem: Boolean,
        foregroundMinutes7d: Long,
        lastUsedAgoDays: Long?,
        ownPackage: String,
        watchedPackages: Set<String>,
        alreadyHandled: Boolean,
    ): TuneAdvice? {
        if (alreadyHandled) return null
        if (!TunePlan.isTuneable(packageName, ownPackage, watchedPackages)) return null
        val fg = foregroundMinutes7d.coerceAtLeast(0L)
        if (fg == 0L && lastUsedAgoDays == null) {
            return TuneAdvice(
                packageName,
                TuneAdviceKind.DISABLE,
                if (isSystem) TuneReason.NEVER_USED_PREINSTALLED else TuneReason.NEVER_USED,
                fg,
            )
        }
        if (fg == 0L && (lastUsedAgoDays ?: Long.MAX_VALUE) >= ABANDONED_DAYS) {
            return TuneAdvice(
                packageName,
                TuneAdviceKind.DISABLE,
                if (isSystem) TuneReason.NEVER_USED_PREINSTALLED else TuneReason.NEVER_USED,
                fg,
            )
        }
        if (fg < RARE_MINUTES_7D || (lastUsedAgoDays ?: Long.MAX_VALUE) > RARE_LAST_USED_DAYS) {
            return TuneAdvice(packageName, TuneAdviceKind.RESTRICT, TuneReason.RARELY_USED, fg)
        }
        return null
    }
}
