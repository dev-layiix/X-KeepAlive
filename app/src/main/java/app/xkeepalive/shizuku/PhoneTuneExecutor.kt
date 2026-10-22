package app.xkeepalive.shizuku

import app.xkeepalive.core.DrainedApp
import app.xkeepalive.core.OpResult
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.PolicyCommands
import app.xkeepalive.core.PolicyOutput
import app.xkeepalive.core.ShellResult
import app.xkeepalive.core.TuneAction
import app.xkeepalive.core.TunePlan
import app.xkeepalive.core.TuneStep
import app.xkeepalive.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TuneReport(
    val target: String,
    val mode: String,
    val operations: List<OpResult>,
    val ok: Boolean,
)

/**
 * Phone tuning that never touches the monitored games: fast animations,
 * drainage limits for battery-hungry apps, bloatware disabling and cache
 * trimming. Same rules as the game policies: only documented ADB commands
 * over Shizuku, originals read first, every change verified and restored.
 */
class PhoneTuneExecutor(
    private val shell: ShizukuBridge,
    private val settings: SettingsRepository,
    private val ownPackage: String,
) {
    private val gate = Mutex()

    suspend fun setFastAnims(enabled: Boolean): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady("anims")
        if (!enabled) return restoreAnimsLocked()
        val observed = readScales()
        val saved = TunePlan.decodeScales(settings.snapshot().savedAnimScales)
        val base = if (saved.isEmpty()) observed.filterValues { it != null }.mapValues { it.value!! } else saved
        if (base.isNotEmpty() && saved.isEmpty()) settings.saveAnimScales(TunePlan.encodeScales(base))
        val target = PolicyCommands.animScaleKeys.associateWith { PolicyCommands.FAST_ANIM_VALUE }
        val operations = mutableListOf<OpResult>()
        for (step in TunePlan.animApplySteps(observed, target)) {
            operations += executeStep("", step)
            if (!operations.last().verified) {
                return finishPartial("anims", operations, "A scale could not be confirmed.")
            }
        }
        if (operations.isEmpty()) {
            operations += OpResult("anims", "", true, "Scales are already fast.")
        }
        settings.setFastAnims(true)
        TuneReport("anims", "TUNE", operations, true)
    }

    suspend fun addDrain(packageName: String): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady(packageName)
        if (!isTuneable(packageName)) {
            return TuneReport(packageName, "SKIPPED", listOf(failedOp("This app cannot be tuned.")), false)
        }
        val standby = readStandby(packageName)
        val runBg = readAppOp(packageName, "RUN_IN_BACKGROUND")
        val runAny = readAppOp(packageName, "RUN_ANY_IN_BACKGROUND")
        val saved = TunePlan.captureDrain(packageName, standby, runBg, runAny)
        settings.saveDrained(saved)
        val operations = mutableListOf<OpResult>()
        for (step in TunePlan.drainApplySteps(standby, runBg, runAny)) {
            operations += executeStep(packageName, step)
            if (!operations.last().verified) {
                restoreDrainedLocked(saved)
                return finishPartial(packageName, operations, "A drainage change could not be confirmed and was restored.")
            }
        }
        if (operations.isEmpty()) {
            operations += OpResult("drain", "", true, "This app is already restricted.")
        }
        TuneReport(packageName, "TUNE", operations, true)
    }

    suspend fun removeDrain(packageName: String): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady(packageName)
        val saved = TunePlan.decodeDrain(settings.snapshot().drainedApps).firstOrNull { it.packageName == packageName }
            ?: return TuneReport(packageName, "SKIPPED", listOf(failedOp("No saved drainage for this app.")), false)
        restoreDrainedLocked(saved)
        settings.removeDrained(packageName)
        TuneReport(packageName, "REVERT", listOf(OpResult("drain", "", true, "Original values restored.")), true)
    }

    suspend fun disableApp(packageName: String): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady(packageName)
        if (!isTuneable(packageName)) {
            return TuneReport(packageName, "SKIPPED", listOf(failedOp("This app cannot be disabled.")), false)
        }
        val set = shell.exec(PolicyCommands.disablePackage(packageName))
        // Checks only this package: the full disabled list is slow and gets
        // truncated at 48KB, which made back-to-back disables misreport.
        val disabled = isDisabled(packageName)
        val ok = disabled == true
        val op = OpResult(
            "disable",
            set.command,
            ok && set.error == null,
            detail(
                when (disabled) {
                    true -> "Package disabled."
                    false -> "Disable not confirmed."
                    null -> "Disable state could not be read, so it was not confirmed."
                },
                set,
            ),
        )
        if (op.verified) settings.addDisabled(packageName)
        TuneReport(packageName, if (op.verified) "TUNE" else "FAILED", listOf(op), op.verified)
    }

    suspend fun enableApp(packageName: String): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady(packageName)
        if (!PackageNames.isValid(packageName)) {
            return TuneReport(packageName, "SKIPPED", listOf(failedOp("Invalid package.")), false)
        }
        val set = shell.exec(PolicyCommands.enablePackage(packageName))
        val disabled = isDisabled(packageName)
        val ok = disabled == false
        val op = OpResult(
            "enable",
            set.command,
            ok && set.error == null,
            detail(
                when (disabled) {
                    false -> "Package enabled again."
                    true -> "Enable not confirmed."
                    null -> "Enable state could not be read, so it was not confirmed."
                },
                set,
            ),
        )
        if (op.verified) settings.removeDisabled(packageName)
        TuneReport(packageName, if (op.verified) "REVERT" else "FAILED", listOf(op), op.verified)
    }

    suspend fun trimCaches(): TuneReport = gate.withLock {
        if (!shell.snapshot().ready) return notReady("cache")
        val run = shell.exec(PolicyCommands.trimCaches())
        val ok = run.error == null && !PolicyOutput.commandFailed(run.exitCode, run.output)
        TuneReport(
            "cache",
            if (ok) "TUNE" else "FAILED",
            listOf(OpResult("trim caches", run.command, ok, detail("Cache trim requested.", run))),
            ok,
        )
    }

    private suspend fun restoreAnimsLocked(): TuneReport {
        val saved = TunePlan.decodeScales(settings.snapshot().savedAnimScales)
        val operations = mutableListOf<OpResult>()
        for (step in TunePlan.animRestoreSteps(saved)) {
            operations += executeStep("", step)
        }
        settings.setFastAnims(false)
        if (operations.isEmpty()) {
            operations += OpResult("anims", "", true, "No saved scales to restore.")
        }
        val ok = operations.all { it.verified }
        if (ok) settings.clearAnimScales()
        return TuneReport("anims", if (ok) "REVERT" else "RESTORE_INCOMPLETE", operations, ok)
    }

    private suspend fun restoreDrainedLocked(saved: DrainedApp) {
        for (step in TunePlan.drainRestoreSteps(saved)) {
            executeStep(saved.packageName, step)
        }
    }

    private suspend fun finishPartial(target: String, done: List<OpResult>, reason: String): TuneReport {
        val note = OpResult("tune", "", false, reason)
        return TuneReport(target, "FAILED", done + note, false)
    }

    private suspend fun isTuneable(packageName: String): Boolean {
        val watched = settings.snapshot().watched.map { it.packageName }.toSet()
        return TunePlan.isTuneable(packageName, ownPackage, watched)
    }

    private suspend fun executeStep(packageName: String, step: TuneStep): OpResult = when (step.action) {
        TuneAction.SET_ANIM_SCALE -> {
            val set = shell.exec(PolicyCommands.setAnimScale(step.extra, step.value))
            val now = readScale(step.extra)
            OpResult(
                step.name,
                set.command,
                now == step.value,
                detail("Scale read: ${now ?: "unknown"}.", set),
            )
        }
        TuneAction.SET_STANDBY -> {
            val set = shell.exec(PolicyCommands.setStandby(packageName, step.value))
            val now = readStandby(packageName)
            OpResult(
                step.name,
                set.command,
                now == step.value,
                detail("Bucket read: ${now ?: "unknown"}.", set),
            )
        }
        TuneAction.SET_APP_OP -> {
            val set = shell.exec(PolicyCommands.setAppOp(packageName, step.extra, step.value))
            val mode = readAppOp(packageName, step.extra)
            OpResult(
                step.name,
                set.command,
                mode == step.value,
                detail("Mode read: ${mode ?: "unknown"}. Expected ${step.value}.", set),
            )
        }
        TuneAction.DISABLE_PACKAGE, TuneAction.ENABLE_PACKAGE ->
            OpResult(step.name, "", false, "Not executed through this path.")
    }

    private suspend fun readScales(): Map<String, String?> =
        PolicyCommands.animScaleKeys.associateWith { key -> readScale(key) }

    private suspend fun readScale(key: String): String? {
        val result = shell.exec(PolicyCommands.getAnimScale(key))
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.parseAnimScale(result.output)
    }

    private suspend fun readStandby(packageName: String): String? {
        val result = shell.exec(PolicyCommands.getStandby(packageName))
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.parseStandbyBucket(result.output)
    }

    private suspend fun readAppOp(packageName: String, op: String): String? {
        val result = shell.exec(PolicyCommands.getAppOp(packageName, op))
        val ok = result.error == null && !PolicyOutput.commandFailed(result.exitCode, result.output)
        return PolicyOutput.observedAppOpMode(result.output, op, ok)
    }

    private suspend fun isDisabled(packageName: String): Boolean? {
        val result = shell.exec(PolicyCommands.isDisabledPackage(packageName))
        if (result.error != null || PolicyOutput.commandFailed(result.exitCode, result.output)) return null
        return PolicyOutput.parseDisabledPackages(result.output).contains(packageName)
    }

    private fun notReady(target: String) = TuneReport(
        target,
        "SKIPPED",
        listOf(OpResult("shell", "", false, shell.snapshot().message)),
        false,
    )

    private fun failedOp(detail: String) = OpResult("tune", "", false, detail)

    private fun detail(summary: String, vararg results: ShellResult): String {
        val raw = results.joinToString(" | ") { result ->
            val error = result.error?.let { " error=$it" }.orEmpty()
            "exit=${result.exitCode}$error out=${result.output.trim().replace("\n", " ").take(160)}"
        }
        return "$summary $raw".take(500)
    }
}
