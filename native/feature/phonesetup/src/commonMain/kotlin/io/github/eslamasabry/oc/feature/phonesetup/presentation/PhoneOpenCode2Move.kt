package io.github.eslamasabry.oc.feature.phonesetup.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.history.HistoryShellPort
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchAdmission
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchCheck
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Why one try of the OpenCode 1 → OpenCode 2 move on this phone stopped (Flutter `ManagedMoveReason`,
 * lib/state/managed_move.dart). Each has fixed words (`openCode2MoveFailed*`); technical text travels only as the
 * [PhoneMoveResult.Stopped.failure] for Details.
 */
public enum class PhoneMoveStop {
    /** The phone's Linux or server was not answering yet (starting or busy). */
    PhoneNotReady,

    /** OpenCode 2 already holds a separate history; moving would overwrite one. Nothing was changed. */
    SeparateHistory,

    /** The move itself stopped part-way (space, a busy server, a failed step). */
    MoveStopped,

    /** The move ran but OpenCode 2 did not confirm the conversations arrived. */
    NotConfirmed,
    ;

    /** A later try can succeed without the person changing anything. */
    public val retryable: Boolean
        get() = this != SeparateHistory
}

public sealed interface PhoneMoveResult {
    public data object Done : PhoneMoveResult

    public data class Stopped(val reason: PhoneMoveStop, val failure: Failure? = null) : PhoneMoveResult
}

/** The phone's own saved server as the move reads it (the two Flutter migration markers on ServerProfile). */
public data class PhoneMoveProfile(
    val id: String,
    /** Old OpenCode 1 metadata kept until the import finished: never permission to start OpenCode 1. */
    val requiresOpenCode2Migration: Boolean,
    /** The history was copied; OpenCode 2 has not confirmed the import yet. Never allows a second copy. */
    val openCode2HistoryPending: Boolean,
) {
    val pending: Boolean
        get() = requiresOpenCode2Migration || openCode2HistoryPending
}

/** OpenCode 2's answer to "did the OpenCode 1 conversations arrive" (`GET /experimental/migration/v1`). */
public enum class PhoneImportStatus {
    /** Required or running: ask again shortly. */
    Waiting,

    /** Completed. */
    Done,

    /** OpenCode 2 reported an error, or the server has no import status to give. */
    Failed,

    /** The app has no live connection to that saved server right now. */
    NotConnected,
}

/** The host side of the move: saved profiles, the setup job and the connection. Bound in androidApp. */
public interface PhoneMovePorts {
    /**
     * The phone's own saved server that still has to move, or null when none does. [activeOnly] limits it to the
     * active server (the launch check); otherwise the active one comes first, then any other phone profile.
     */
    public suspend fun pending(activeOnly: Boolean): Outcome<PhoneMoveProfile?>

    /** That profile read again by id; null when it was removed or is no longer this phone's server. */
    public suspend fun reread(id: String): Outcome<PhoneMoveProfile?>

    /**
     * The setup job's OpenCode step at the pinned OpenCode 2 version, then the setup finish: it copies the
     * OpenCode 1 history (never moves it), starts OpenCode 2, marks the history pending and connects.
     */
    public suspend fun installOpenCode2(): Outcome<Unit>

    /** One read of OpenCode 2's import status for [id], over the connection that is current now. */
    public suspend fun importStatus(id: String): Outcome<PhoneImportStatus>

    /** Clears the pending mark of [id], only while the saved row is the one this move confirmed. */
    public suspend fun confirmed(id: String): Outcome<Unit>
}

/**
 * The move from OpenCode 1 to OpenCode 2 on this phone's built-in Linux, in Flutter's order (lib/main_parts/root.dart
 * `_moveAttempt`, lib/builtin/builtin_server.dart, lib/builtin/setup/setup_finish.dart):
 *
 * 1. The read-only history check ([HistorySwitchCheck]) runs first, before anything is installed or stopped. When
 *    OpenCode 2 already has its own conversations the move stops there ([PhoneMoveStop.SeparateHistory]): both
 *    histories and the installed programs stay exactly as they were.
 * 2. The setup job installs OpenCode 2 at the pinned version; its finish copies the old history and starts OpenCode 2.
 * 3. The move waits for OpenCode 2 to confirm the import. Only a confirmation clears the pending mark; without one the
 *    OpenCode 1 data is left untouched and the next try confirms again instead of copying again.
 */
public class PhoneOpenCode2Move(
    private val ports: PhoneMovePorts,
    shell: HistoryShellPort,
    private val pollEvery: Duration = 1.seconds,
    private val confirmWithin: Duration = 180.seconds,
    private val readTimeout: Duration = 15.seconds,
) {
    private val check = HistorySwitchCheck(shell)

    /** Whether the launch page has to run the move for the active server. */
    public suspend fun needed(): Boolean = (ports.pending(activeOnly = true) as? Outcome.Ok)?.value?.pending == true

    public suspend fun move(): PhoneMoveResult {
        val original =
            when (val read = ports.pending(activeOnly = false)) {
                is Outcome.Failed -> return stopped(PhoneMoveStop.MoveStopped, read.failure)
                is Outcome.Ok -> read.value ?: return PhoneMoveResult.Done
            }
        if (!original.pending) return PhoneMoveResult.Done
        if (original.requiresOpenCode2Migration) {
            when (check.admission()) {
                HistorySwitchAdmission.Allowed -> Unit
                HistorySwitchAdmission.SeparateHistory ->
                    return stopped(PhoneMoveStop.SeparateHistory, Failure.Refused(RefusalReason.MigrationPending))
                HistorySwitchAdmission.Unavailable ->
                    return stopped(PhoneMoveStop.PhoneNotReady, Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            }
            when (val installed = ports.installOpenCode2()) {
                is Outcome.Failed -> return stopped(reasonOf(installed.failure), installed.failure)
                is Outcome.Ok -> Unit
            }
        }
        val current =
            when (val read = ports.reread(original.id)) {
                is Outcome.Failed -> return stopped(PhoneMoveStop.NotConfirmed, read.failure)
                is Outcome.Ok ->
                    read.value
                        ?: return stopped(PhoneMoveStop.MoveStopped, Failure.Refused(RefusalReason.ProfileDeleted))
            }
        // The finish clears the old marker only after the copy and the start; a marker left means it did not finish.
        if (current.requiresOpenCode2Migration) return stopped(PhoneMoveStop.NotConfirmed)
        if (!current.openCode2HistoryPending) return PhoneMoveResult.Done
        when (val answer = awaitImport(current.id)) {
            PhoneImportStatus.Done -> Unit
            PhoneImportStatus.NotConnected -> return stopped(PhoneMoveStop.PhoneNotReady)
            PhoneImportStatus.Waiting,
            PhoneImportStatus.Failed -> return stopped(PhoneMoveStop.NotConfirmed)
        }
        return when (val saved = ports.confirmed(current.id)) {
            is Outcome.Failed -> stopped(PhoneMoveStop.NotConfirmed, saved.failure)
            is Outcome.Ok -> PhoneMoveResult.Done
        }
    }

    /** Polls until OpenCode 2 answers Done, Failed or NotConnected, or [confirmWithin] passed (Waiting). */
    private suspend fun awaitImport(id: String): PhoneImportStatus {
        var waited = Duration.ZERO
        while (true) {
            val answer =
                when (val read = withTimeoutOrNull(readTimeout) { ports.importStatus(id) }) {
                    null -> PhoneImportStatus.Failed
                    is Outcome.Failed -> PhoneImportStatus.Failed
                    is Outcome.Ok -> read.value
                }
            if (answer != PhoneImportStatus.Waiting) return answer
            if (waited >= confirmWithin) return PhoneImportStatus.Waiting
            delay(pollEvery)
            waited += pollEvery
        }
    }

    private fun stopped(reason: PhoneMoveStop, failure: Failure? = null): PhoneMoveResult.Stopped =
        PhoneMoveResult.Stopped(reason, failure)

    internal companion object {
        /** How a failed setup job reads (the same table the launch page used before this class existed). */
        fun reasonOf(failure: Failure): PhoneMoveStop =
            when (failure) {
                is Failure.Refused ->
                    if (failure.reason == RefusalReason.MigrationPending) PhoneMoveStop.SeparateHistory
                    else PhoneMoveStop.MoveStopped
                is Failure.Network,
                is Failure.TimedOut -> PhoneMoveStop.PhoneNotReady
                is Failure.Runtime ->
                    if (failure.problem == RuntimeProblem.SlotBusy || failure.problem == RuntimeProblem.RecoveryUnavailable)
                        PhoneMoveStop.PhoneNotReady
                    else PhoneMoveStop.MoveStopped
                else -> PhoneMoveStop.MoveStopped
            }
    }
}
