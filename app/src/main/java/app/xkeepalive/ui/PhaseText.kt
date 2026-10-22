package app.xkeepalive.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.xkeepalive.R
import app.xkeepalive.core.Blocker
import app.xkeepalive.core.Phase
import app.xkeepalive.core.ProcessNotes
import app.xkeepalive.core.ProcessOutcome

/** Resource mapping for engine states, so the interface is fully localized. */

fun phaseLabelRes(phase: Phase): Int = when (phase) {
    Phase.INACTIVE -> R.string.phase_inactive
    Phase.WAITING_FOR_GAME -> R.string.phase_waiting
    Phase.GAME_FOREGROUND -> R.string.phase_foreground
    Phase.GAME_BACKGROUND -> R.string.phase_background
    Phase.BACKGROUND_MANAGEMENT_ACTIVE -> R.string.phase_active
    Phase.GAME_RETURNED -> R.string.phase_returned
}

@Composable
fun phaseLabel(phase: Phase): String = stringResource(phaseLabelRes(phase))

@Composable
fun blockerLabel(blocker: Blocker, phase: Phase): String = when (blocker) {
    Blocker.PERMISSIONS -> stringResource(R.string.blocker_permissions)
    Blocker.NO_GAME_SELECTED -> stringResource(R.string.blocker_no_game)
    Blocker.NONE -> phaseLabel(phase)
}

fun outcomeLabelRes(outcome: ProcessOutcome): Int = when (outcome) {
    ProcessOutcome.NOT_CHECKED -> R.string.outcome_not_checked
    ProcessOutcome.UNKNOWN_NO_PRIVILEGE -> R.string.outcome_no_privilege
    ProcessOutcome.STILL_IN_MEMORY -> R.string.outcome_in_memory
    ProcessOutcome.TERMINATED -> R.string.outcome_terminated
    ProcessOutcome.RESTARTED_NEW_PID -> R.string.outcome_recreated
    ProcessOutcome.NOT_RUNNING -> R.string.outcome_not_running
}

@Composable
fun outcomeLabel(outcome: ProcessOutcome): String = stringResource(outcomeLabelRes(outcome))

fun eventTitleRes(kind: String): Int? = when (kind) {
    "SERVICE_STARTED" -> R.string.event_service_started
    "SERVICE_STOPPED" -> R.string.event_service_stopped
    "PHASE" -> R.string.event_phase
    "PROCESS" -> R.string.event_process
    "LIMIT" -> R.string.event_limit
    "POLICY" -> R.string.event_policy
    "SHIZUKU" -> R.string.event_shizuku
    "BOOT" -> R.string.event_boot
    "TEST" -> R.string.event_test
    "PID" -> R.string.event_pid
    "ERROR" -> R.string.event_error
    else -> null
}

@Composable
fun eventTitle(kind: String): String = eventTitleRes(kind)?.let { stringResource(it) } ?: kind

/**
 * Maps the known engine diagnostics to translated texts by exact match.
 * Unknown or dynamic messages (PIDs, package names) are shown unchanged.
 */
fun processNoteRes(note: String): Int? = when (note) {
    ProcessNotes.STILL_IN_MEMORY -> R.string.note_still_in_memory
    ProcessNotes.TERMINATED -> R.string.note_terminated
    ProcessNotes.RESTARTED -> R.string.note_restarted
    ProcessNotes.NOT_RUNNING -> R.string.note_not_running
    ProcessNotes.NOT_RUNNING_ON_LEAVE -> R.string.note_not_running_on_leave
    ProcessNotes.ALIVE_ON_LEAVE -> R.string.note_alive_on_leave
    ProcessNotes.UNKNOWN -> R.string.note_unknown
    ProcessNotes.PID_FOLLOWED -> R.string.note_pid_followed
    else -> null
}

@Composable
fun processNoteText(note: String): String = processNoteRes(note)?.let { stringResource(it) } ?: note
