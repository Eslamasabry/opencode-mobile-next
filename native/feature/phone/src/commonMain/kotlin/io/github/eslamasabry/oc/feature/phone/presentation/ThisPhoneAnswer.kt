package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.engine.runtime.budget.ServiceExitReason
import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RestoreReason
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem
import kotlin.time.Duration
import kotlin.time.Instant

/** Who or what stopped the phone server (plan 11 §6.1; B-LINUX-250). */
public enum class StoppedBy {
    Person,
    Idle,
    Itself,
    Android,
}

/** Why the app will not bring the server back by itself (plan 11 §6.1 `GaveUp(reason)`). */
public enum class GaveUpWhy {
    BudgetExhausted,
    Ownership,
    PolicyDisabled,
    StorageUnavailable,
}

/**
 * The phone server's visible state (plan 11 §6.1). Derived from the status contract plus an
 * authenticated loopback probe; a pid alone is never evidence (B-LINUX-240, B-STATE-025). Plan 11
 * homes the derivation in `:engine:runtime` (`PhoneServerState`); until S3-RTENG writes it, this
 * page owns the same table here (report D2).
 */
public sealed interface PhoneServerState {
    /** Nothing read yet, or the server process is up and no probe answered yet. */
    public data object Checking : PhoneServerState

    public data object NotInstalled : PhoneServerState

    public data object SettingUp : PhoneServerState

    public data object InstallFailed : PhoneServerState

    public data class Stopped(val by: StoppedBy) : PhoneServerState

    public data object Starting : PhoneServerState

    public data object Running : PhoneServerState

    /** The process is alive but the probe fails ("isn't answering", map BUILTIN-LINUX-26). */
    public data object NotAnswering : PhoneServerState

    /** Recovery is waiting for [attempt] of 3; [nextAt] null when the time is unknown. */
    public data class Restarting(val attempt: Int, val nextAt: Instant?) : PhoneServerState

    /** An automatic attempt is launching now. */
    public data object RestartingNow : PhoneServerState

    /** `restorePhase = Restoring`, shown natively for the first time (map Q5). */
    public data object Restoring : PhoneServerState

    public data class GaveUp(val why: GaveUpWhy) : PhoneServerState

    public companion object {
        public fun of(facts: PhoneServerFacts): PhoneServerState {
            val status = facts.status ?: return Checking
            if (!status.installed) {
                return when (status.phase) {
                    RuntimePhase.Installing -> SettingUp
                    RuntimePhase.Failed -> InstallFailed
                    RuntimePhase.Idle,
                    RuntimePhase.Ready -> NotInstalled
                }
            }
            if (status.restorePhase == RestorePhase.Restoring) return Restoring
            if (facts.starting) return Starting
            if (status.serverRunning) {
                return when (facts.probe) {
                    ProbeAnswer.Answered -> Running
                    ProbeAnswer.NeedsPassword,
                    ProbeAnswer.NoAnswer -> NotAnswering
                    null -> Checking
                }
            }
            if (facts.stoppedByPerson || status.restoreReason == RestoreReason.Stopped)
                return Stopped(StoppedBy.Person)
            gaveUp(status.restoreReason)?.let {
                return GaveUp(it)
            }
            when (val recovery = facts.recovery) {
                RecoveryFacts.Exhausted -> return GaveUp(GaveUpWhy.BudgetExhausted)
                is RecoveryFacts.Waiting -> return Restarting(recovery.attempt, recovery.nextAt)
                RecoveryFacts.Restarting -> return RestartingNow
                RecoveryFacts.None -> Unit
            }
            if (status.idleBlocks || status.restoreReason == RestoreReason.IdleStopped)
                return Stopped(StoppedBy.Idle)
            return Stopped(stoppedBy(facts.lastExit))
        }

        private fun gaveUp(reason: RestoreReason?): GaveUpWhy? =
            when (reason) {
                RestoreReason.BudgetExhausted -> GaveUpWhy.BudgetExhausted
                RestoreReason.OwnershipUnknown,
                RestoreReason.ComponentRecoveryRequired -> GaveUpWhy.Ownership
                RestoreReason.PolicyDisabled -> GaveUpWhy.PolicyDisabled
                RestoreReason.StorageUnavailable -> GaveUpWhy.StorageUnavailable
                RestoreReason.Stopped,
                RestoreReason.SystemTimeout,
                RestoreReason.IdleStopped,
                null -> null
            }

        /**
         * Android's kill (memory or its process limit; the code cannot tell which), else it stopped
         * by itself.
         */
        private fun stoppedBy(exit: ServiceExitReason?): StoppedBy =
            if (exit == ServiceExitReason.MemoryOrPhantomKill) StoppedBy.Android
            else StoppedBy.Itself
    }
}

/** How the answer reads at a glance (the kit's status tones). */
public enum class AnswerTone {
    Ok,
    Working,
    Off,
    Paused,
    NeedsYou,
}

/** The answer's big words; `:ui:words` owns each sentence (ThisPhoneScreen maps them). */
public enum class AnswerHeadline {
    Checking,
    NotSetUp,
    SettingUp,
    InstallFailed,
    Starting,
    Stopping,
    Running,
    NotAnswering,
    Off,
    AndroidClosed,
    StoppedItself,
    Restarting,
    StartFailed,
    Recovery,
}

/** The line under the answer. */
public sealed interface AnswerLine {
    public data object StaysOff : AnswerLine

    public data object RestartsSoon : AnswerLine

    public data class RestartsIn(val wait: AboutWait) : AnswerLine

    public data object GaveUp : AnswerLine

    public data object StartFailedLine : AnswerLine

    public data object RecoveryBody : AnswerLine

    public data class Storage(val bytes: Long) : AnswerLine

    public data class Memory(val bytes: Long) : AnswerLine

    public data class History(val info: PhoneHistoryInfo) : AnswerLine

    /** Why it is restarting by itself (the Android or self stop that preceded it). */
    public data class Because(val headline: AnswerHeadline) : AnswerLine
}

/** The answer's one act (the filled button at the thumb). Each names its object in the words. */
public enum class PhoneAct {
    SetUp,
    SetUpAgain,
    Start,
    StartAgain,
    RestartNow,
    OpenAppSettings,
    ShowProgress,
    ExportProjects,
    SharedFolders,
}

public data class ThisPhoneAnswer(
    val tone: AnswerTone,
    val headline: AnswerHeadline,
    val line: AnswerLine? = null,
    val primary: PhoneAct? = null,
    /**
     * When the line counts down to a moment, the page re-reads the answer each second until then.
     */
    val until: Instant? = null,
)

/**
 * "about 40 s", "about 2 min": rounded up, so the promise is never early (port of `thisPhoneWait`).
 */
public sealed interface AboutWait {
    public data class Seconds(val value: Int) : AboutWait

    public data class Minutes(val value: Int) : AboutWait

    public companion object {
        public fun of(left: Duration): AboutWait {
            val seconds = left.inWholeSeconds.toInt()
            if (seconds < 60) return Seconds(if (seconds <= 5) 5 else (seconds + 4) / 5 * 5)
            return Minutes((seconds + 59) / 60)
        }
    }
}

/**
 * The page's one answer for the state it reads now (port of this_phone_answer.dart `_answerNow`,
 * in-app host only).
 */
public object ThisPhoneAnswers {
    public fun of(
        state: PhoneServerState,
        facts: PhoneServerFacts,
        now: Instant? = null,
        stopping: Boolean = false,
    ): ThisPhoneAnswer {
        if (stopping) return ThisPhoneAnswer(AnswerTone.Working, AnswerHeadline.Stopping)
        val failedStart = facts.startProblem
        if (failedStart == StartProblem.ComponentRecovery) return recovery()
        return when (state) {
            PhoneServerState.Checking ->
                ThisPhoneAnswer(AnswerTone.Working, AnswerHeadline.Checking)
            PhoneServerState.NotInstalled ->
                ThisPhoneAnswer(AnswerTone.Off, AnswerHeadline.NotSetUp, primary = PhoneAct.SetUp)
            PhoneServerState.SettingUp ->
                ThisPhoneAnswer(
                    AnswerTone.Working,
                    AnswerHeadline.SettingUp,
                    primary = PhoneAct.ShowProgress,
                )
            PhoneServerState.InstallFailed ->
                ThisPhoneAnswer(
                    AnswerTone.NeedsYou,
                    AnswerHeadline.InstallFailed,
                    primary = PhoneAct.SetUpAgain,
                )
            PhoneServerState.Starting ->
                ThisPhoneAnswer(AnswerTone.Working, AnswerHeadline.Starting)
            PhoneServerState.Restoring,
            PhoneServerState.RestartingNow ->
                ThisPhoneAnswer(
                    AnswerTone.Working,
                    AnswerHeadline.Restarting,
                    line = AnswerLine.Because(why(facts)),
                )
            PhoneServerState.Running ->
                ThisPhoneAnswer(
                    AnswerTone.Ok,
                    AnswerHeadline.Running,
                    line =
                        facts.status?.bytesUsed?.takeIf { it > 0 }?.let { AnswerLine.Storage(it) },
                )
            PhoneServerState.NotAnswering ->
                ThisPhoneAnswer(
                    AnswerTone.NeedsYou,
                    AnswerHeadline.NotAnswering,
                    primary = PhoneAct.RestartNow,
                )
            is PhoneServerState.Stopped ->
                when {
                    failedStart != null && failedStart.isFailure() -> startFailed()
                    state.by == StoppedBy.Person ->
                        ThisPhoneAnswer(
                            AnswerTone.Off,
                            AnswerHeadline.Off,
                            AnswerLine.StaysOff,
                            PhoneAct.Start,
                        )
                    state.by == StoppedBy.Idle ->
                        ThisPhoneAnswer(
                            AnswerTone.Off,
                            AnswerHeadline.Off,
                            primary = PhoneAct.Start,
                        )
                    else ->
                        ThisPhoneAnswer(AnswerTone.Off, why(facts), primary = PhoneAct.RestartNow)
                }
            is PhoneServerState.Restarting -> {
                val at = state.nextAt
                val left = if (at != null && now != null) at - now else null
                val line =
                    if (left == null || left <= Duration.ZERO) AnswerLine.RestartsSoon
                    else AnswerLine.RestartsIn(AboutWait.of(left))
                ThisPhoneAnswer(
                    AnswerTone.Paused,
                    why(facts),
                    line,
                    PhoneAct.RestartNow,
                    until = at,
                )
            }
            is PhoneServerState.GaveUp ->
                if (state.why == GaveUpWhy.Ownership) {
                    recovery()
                } else {
                    ThisPhoneAnswer(
                        AnswerTone.NeedsYou,
                        why(facts),
                        AnswerLine.GaveUp,
                        PhoneAct.RestartNow,
                    )
                }
        }
    }

    private fun StartProblem.isFailure(): Boolean =
        this != StartProblem.Interrupted && this != StartProblem.Busy

    private fun startFailed() =
        ThisPhoneAnswer(
            AnswerTone.NeedsYou,
            AnswerHeadline.StartFailed,
            AnswerLine.StartFailedLine,
            PhoneAct.StartAgain,
        )

    private fun recovery() =
        ThisPhoneAnswer(
            AnswerTone.NeedsYou,
            AnswerHeadline.Recovery,
            AnswerLine.RecoveryBody,
            PhoneAct.OpenAppSettings,
        )

    private fun why(facts: PhoneServerFacts): AnswerHeadline =
        if (facts.lastExit == ServiceExitReason.MemoryOrPhantomKill) AnswerHeadline.AndroidClosed
        else AnswerHeadline.StoppedItself
}
