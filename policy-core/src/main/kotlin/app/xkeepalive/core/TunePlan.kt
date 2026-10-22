package app.xkeepalive.core

/**
 * Phone tuning that never touches monitored games: fast animations, drainage
 * limits for battery-hungry apps, bloatware disabling and cache trimming.
 * Pure logic, fully unit-tested. Original values are captured first so every
 * change can be restored exactly.
 */
data class DrainedApp(
    val packageName: String,
    val savedStandby: String,
    val savedRunInBackground: String,
    val savedRunAnyInBackground: String,
)

enum class TuneAction {
    SET_ANIM_SCALE,
    SET_STANDBY,
    SET_APP_OP,
    DISABLE_PACKAGE,
    ENABLE_PACKAGE,
}

data class TuneStep(
    val name: String,
    val action: TuneAction,
    val value: String = "",
    val extra: String = "",
)

object TunePlan {
    /** Packages that tuning must never disable, whatever the user picks. */
    val neverTouch = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "moe.shizuku.privileged.api",
    )

    /**
     * Whether a package may be tuned at all. The app itself, the monitored
     * games and the packages above are always excluded.
     */
    fun isTuneable(packageName: String, ownPackage: String, watchedPackages: Set<String>): Boolean {
        if (!PackageNames.isValid(packageName)) return false
        if (packageName == ownPackage) return false
        if (packageName in watchedPackages) return false
        if (packageName in neverTouch) return false
        return true
    }

    fun animApplySteps(
        observed: Map<String, String?>,
        target: Map<String, String>,
    ): List<TuneStep> = PolicyCommands.animScaleKeys.mapNotNull { key ->
        val want = target[key] ?: return@mapNotNull null
        if (want !in PolicyCommands.animScaleValues) return@mapNotNull null
        val now = observed[key]
        if (now == want) return@mapNotNull null
        TuneStep("Animation $key", TuneAction.SET_ANIM_SCALE, value = want, extra = key)
    }

    fun animRestoreSteps(saved: Map<String, String>): List<TuneStep> =
        saved.mapNotNull { (key, value) ->
            if (key !in PolicyCommands.animScaleKeys) return@mapNotNull null
            if (value !in PolicyCommands.animScaleValues) return@mapNotNull null
            TuneStep("Restore $key", TuneAction.SET_ANIM_SCALE, value = value, extra = key)
        }

    fun captureDrain(
        packageName: String,
        standby: String?,
        runInBackground: String?,
        runAnyInBackground: String?,
    ): DrainedApp = DrainedApp(
        packageName = packageName,
        savedStandby = standby?.takeIf { it in PolicyCommands.standbyBuckets }.orEmpty(),
        savedRunInBackground = runInBackground?.takeIf { it in PolicyCommands.appOpModes }.orEmpty(),
        savedRunAnyInBackground = runAnyInBackground?.takeIf { it in PolicyCommands.appOpModes }.orEmpty(),
    )

    fun drainApplySteps(
        standby: String?,
        runInBackground: String?,
        runAnyInBackground: String?,
    ): List<TuneStep> {
        val steps = mutableListOf<TuneStep>()
        if (standby != null && standby in PolicyCommands.standbyBuckets && standby != PolicyCommands.DRAIN_BUCKET) {
            steps += TuneStep("Restrict standby", TuneAction.SET_STANDBY, value = PolicyCommands.DRAIN_BUCKET)
        }
        if (runInBackground != null && runInBackground in PolicyCommands.appOpModes && runInBackground != PolicyCommands.DRAIN_APP_OP_MODE) {
            steps += TuneStep(
                "RUN_IN_BACKGROUND",
                TuneAction.SET_APP_OP,
                value = PolicyCommands.DRAIN_APP_OP_MODE,
                extra = "RUN_IN_BACKGROUND",
            )
        }
        if (runAnyInBackground != null && runAnyInBackground in PolicyCommands.appOpModes && runAnyInBackground != PolicyCommands.DRAIN_APP_OP_MODE) {
            steps += TuneStep(
                "RUN_ANY_IN_BACKGROUND",
                TuneAction.SET_APP_OP,
                value = PolicyCommands.DRAIN_APP_OP_MODE,
                extra = "RUN_ANY_IN_BACKGROUND",
            )
        }
        return steps
    }

    fun drainRestoreSteps(saved: DrainedApp): List<TuneStep> {
        val steps = mutableListOf<TuneStep>()
        if (saved.savedStandby in PolicyCommands.standbyBuckets) {
            steps += TuneStep("Bucket restore", TuneAction.SET_STANDBY, value = saved.savedStandby)
        }
        if (saved.savedRunInBackground in PolicyCommands.appOpModes) {
            steps += TuneStep(
                "Restore RUN_IN_BACKGROUND",
                TuneAction.SET_APP_OP,
                value = saved.savedRunInBackground,
                extra = "RUN_IN_BACKGROUND",
            )
        }
        if (saved.savedRunAnyInBackground in PolicyCommands.appOpModes) {
            steps += TuneStep(
                "Restore RUN_ANY_IN_BACKGROUND",
                TuneAction.SET_APP_OP,
                value = saved.savedRunAnyInBackground,
                extra = "RUN_ANY_IN_BACKGROUND",
            )
        }
        return steps
    }

    fun encodeScales(scales: Map<String, String>): String =
        scales.entries
            .filter { (key, value) -> key in PolicyCommands.animScaleKeys && PolicyOutput.parseAnimScale(value) != null }
            .sortedBy { it.key }
            .joinToString(";") { (key, value) -> "$key=${PolicyOutput.parseAnimScale(value)}" }

    fun decodeScales(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) return@forEach
            val normalized = PolicyOutput.parseAnimScale(pieces[1]) ?: return@forEach
            if (pieces[0] in PolicyCommands.animScaleKeys) out[pieces[0]] = normalized
        }
        return out
    }

    fun encodeDrain(apps: List<DrainedApp>): String =
        apps.filter { PackageNames.isValid(it.packageName) }
            .sortedBy { it.packageName }
            .joinToString(";") { app ->
                "${app.packageName}|${app.savedStandby}|${app.savedRunInBackground}|${app.savedRunAnyInBackground}"
            }

    fun decodeDrain(raw: String): List<DrainedApp> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val pieces = part.split('|')
            if (pieces.size != 4 || !PackageNames.isValid(pieces[0])) return@mapNotNull null
            DrainedApp(pieces[0], pieces[1], pieces[2], pieces[3])
        }
    }

    fun encodePackages(packages: Set<String>): String =
        packages.filter { PackageNames.isValid(it) }.sorted().joinToString(",")

    fun decodePackages(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filter { PackageNames.isValid(it) }.toSet()
}
