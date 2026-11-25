package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.engine.analytics.health.AppJourneys
import io.github.eslamasabry.oc.engine.analytics.health.ApprovalMeter
import io.github.eslamasabry.oc.engine.analytics.health.FirstReplyOnce
import io.github.eslamasabry.oc.engine.analytics.health.JourneyMemory
import io.github.eslamasabry.oc.engine.analytics.health.SetupJobState
import io.github.eslamasabry.oc.engine.analytics.health.SetupJourneyCodec
import io.github.eslamasabry.oc.engine.analytics.health.SetupJourneyMemory
import io.github.eslamasabry.oc.engine.analytics.health.SetupJourneySaved
import io.github.eslamasabry.oc.engine.analytics.health.SetupStepState
import io.github.eslamasabry.oc.engine.analytics.health.SetupStepView
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.analytics.health.HealthFlow
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.StartStepKind
import io.github.eslamasabry.oc.core.model.PermissionReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The journey analytics' app-side half (docs/native/analytics/journeys-2026-11-09.md): the setup engine's progress as
 * funnel words, the approval list as asked/answered facts, and the two bits of memory that outlive the process.
 * Words and numbers only: no error text, no ids leave this file.
 */

/** The funnel's `fail_kind` for a setup error (a word of the closed list), never the error's text. */
internal fun setupFailKindOf(error: SetupError?): String =
    when (error) {
        null -> "unknown"
        SetupError.NoInternet, is SetupError.Offline -> "network"
        is SetupError.NoSpace, SetupError.PersistenceFailure, SetupError.StatusUnreadable -> "storage"
        SetupError.HostRefused, SetupError.OwnershipUnknown, SetupError.HostMismatch -> "permission"
        is SetupError.Checksum -> "checksum"
        SetupError.Interrupted -> "killed_by_system"
        is SetupError.StepTimedOut -> "timeout"
        is SetupError.Install, SetupError.PackageRepair -> "apt"
        is SetupError.PortBusy -> "port_busy"
        SetupError.NodeCannotRun, SetupError.OpenCodeNoProgram, SetupError.OpenCodeWontRun,
        SetupError.OpenCodeNoStart, SetupError.CannotStart,
        SetupError.ToolsUncheckable, SetupError.Unexpected -> "unknown"
        is SetupError.StartStepFailed -> startFailKindOf(error.kind)
    }

/** A start that names its kind: fixed words of the closed list only, never text from the phone or server. */
private fun startFailKindOf(kind: StartStepKind): String =
    when (kind) {
        StartStepKind.TimedOut -> "timeout"
        StartStepKind.Ownership -> "permission"
        StartStepKind.Exited -> "exited"
        StartStepKind.Refused -> "refused"
        StartStepKind.Password -> "password"
        StartStepKind.Withdrawn -> "withdrawn"
        else -> "unknown"
    }

internal fun journeyJobState(state: SetupState): SetupJobState =
    when (state) {
        SetupState.Idle -> SetupJobState.Idle
        SetupState.Running -> SetupJobState.Running
        SetupState.Done -> SetupJobState.Done
        SetupState.Failed -> SetupJobState.Failed
        SetupState.Interrupted -> SetupJobState.Interrupted
        SetupState.Cancelled -> SetupJobState.Cancelled
    }

internal fun journeySteps(progress: SetupProgress): List<SetupStepView> =
    progress.components.map { c: ComponentProgress ->
        SetupStepView(
            c.id,
            when (c.state) {
                ComponentState.Pending, ComponentState.Checking -> SetupStepState.Pending
                ComponentState.Running -> SetupStepState.Running
                ComponentState.Done -> SetupStepState.Done
                ComponentState.Failed -> SetupStepState.Failed
                ComponentState.Skipped -> SetupStepState.Skipped
            },
            failKind = if (c.state == ComponentState.Failed) setupFailKindOf(c.error ?: progress.error) else null,
        )
    }

/** [SetupJourneyMemory] over the app's preferences: reads in memory, writes on the app scope. */
internal class PrefsSetupMemory(
    private val answers: TelemetryAnswers,
    private val scope: CoroutineScope,
) : SetupJourneyMemory {
    override fun load(): SetupJourneySaved? = SetupJourneyCodec.decode(answers.setupJourney())

    override fun save(saved: SetupJourneySaved?) {
        scope.launch { answers.saveSetupJourney(saved?.let(SetupJourneyCodec::encode)) }
    }
}

/** [JourneyMemory] over the app's preferences: reads in memory, writes on the app scope. */
internal class PrefsJourneyMemory(
    private val answers: TelemetryAnswers,
    private val scope: CoroutineScope,
) : JourneyMemory {
    override fun load(): String? = answers.journey()

    override fun save(encoded: String) {
        scope.launch { answers.saveJourney(encoded) }
    }
}

/** `first_reply` once per install, remembered under [io.github.eslamasabry.oc.engine.analytics.product.TelemetryKeys.FIRST_REPLY_SEEN]. */
internal class PrefsFirstReply(private val answers: TelemetryAnswers, private val scope: CoroutineScope) : FirstReplyOnce {
    @Volatile private var marked = false

    @Volatile private var sentNow = false

    override fun seen(): Boolean = marked || answers.firstReplySeen()

    override fun mark() {
        marked = true
        scope.launch { answers.markFirstReplySeen() }
    }

    /** `first_reply` was sent for this install (an install that saw its first reply before agreeing has not sent it yet). */
    override fun sent(): Boolean = sentNow || answers.firstReplySent()

    override fun markSent() {
        sentNow = true
        scope.launch { answers.markFirstReplySent() }
    }
}

/** A widget's own answer went to the server (a widget has no request list of its own): counted as the widget's. */
internal fun noteWidgetAnswer(requestId: String, allowed: Boolean) {
    AppJourneys.approvalsOrNull?.answeredOn(requestId, if (allowed) "allowed" else "denied", "widget")
}

/** Where one request's answer stands, for the approval meter: only a [Confirmed] answer is ever counted. */
internal enum class AnswerStand {
    /** Nobody answered, or an answer is held (still undoable), on its way, or was not accepted. */
    Open,

    /** The server confirmed the answer (`AnswerPhase.Sent`). */
    Confirmed,

    /** The run that asked was stopped (`AnswerPhase.NoLongerNeeded`). */
    RunStopped,
}

/** One pending request as the approval meter needs it. [answer] is the confirmed answer's word, null until then. */
internal data class PendingFacts(
    val id: String,
    val sessionId: String,
    val kind: String,
    val answer: String?,
    val stand: AnswerStand = if (answer != null) AnswerStand.Confirmed else AnswerStand.Open,
)

internal fun pendingFacts(list: List<PendingRequest>): List<PendingFacts> =
    list.map { p ->
        val phase = p.phase
        val answer: RequestAnswer? = (phase as? AnswerPhase.Sent)?.answer
        val result =
            when (answer) {
                null -> null
                is RequestAnswer.Permission ->
                    when (answer.reply) {
                        PermissionReply.Once -> "allowed"
                        PermissionReply.Always -> "always"
                        PermissionReply.Reject -> "denied"
                    }
                is RequestAnswer.Question -> "answered"
                RequestAnswer.Decline -> "declined"
            }
        val kind =
            when (p) {
                is PendingRequest.Permission -> ApprovalMeter.kindOf(p.request.permission)
                is PendingRequest.Question -> "question"
            }
        val stand =
            when {
                phase is AnswerPhase.Sent -> AnswerStand.Confirmed
                phase == AnswerPhase.NoLongerNeeded -> AnswerStand.RunStopped
                else -> AnswerStand.Open
            }
        PendingFacts(p.id.raw, p.sessionId.raw, kind, result, stand)
    }

/** What the feed tells an [ApprovalMeter]. */
internal interface ApprovalSink {
    fun asked(id: String, sessionId: String, kind: String)

    fun confirmed(id: String)

    fun answered(id: String, result: String)

    fun stopped(id: String)

    fun gone(id: String)
}

internal fun ApprovalMeter.asSink(): ApprovalSink =
    object : ApprovalSink {
        override fun asked(id: String, sessionId: String, kind: String) = this@asSink.asked(id, sessionId, kind)

        override fun confirmed(id: String) = this@asSink.confirmed(id)

        override fun answered(id: String, result: String) = this@asSink.answered(id, result)

        override fun stopped(id: String) = this@asSink.stopped(id)

        override fun gone(id: String) = this@asSink.gone(id)
    }

/**
 * Turns the pending list of one connection into asked / confirmed / answered / stopped / gone calls. A confirmed answer
 * is reported as `auto` when [automatic] says a saved Auto or "Approve everything" gave it (that record is written just
 * after the server confirms, so the word waits for it, or for the request to leave); otherwise as the answer's word.
 */
internal class ApprovalFeed(
    private val meter: ApprovalSink,
    private val automatic: (PendingFacts) -> Boolean = { false },
) {
    private val known = HashSet<String>()
    private val waitingWord = LinkedHashMap<String, PendingFacts>()
    private val reported = HashSet<String>()

    fun update(list: List<PendingFacts>) {
        val ids = list.map { it.id }.toSet()
        for (fact in list) {
            if (known.add(fact.id)) meter.asked(fact.id, fact.sessionId, fact.kind)
            when (fact.stand) {
                AnswerStand.Confirmed ->
                    if (fact.id !in reported && fact.id !in waitingWord) {
                        meter.confirmed(fact.id)
                        waitingWord[fact.id] = fact
                    }
                AnswerStand.RunStopped -> if (reported.add(fact.id)) meter.stopped(fact.id)
                AnswerStand.Open -> Unit
            }
        }
        recheck()
        for (gone in known.toList()) {
            if (gone !in ids) {
                known.remove(gone)
                waitingWord.remove(gone)?.let { report(it) }
                reported.remove(gone)
                meter.gone(gone)
            }
        }
    }

    /** A saved mode may have recorded one of the confirmed answers since the last look. */
    fun recheck() {
        for (fact in waitingWord.values.toList()) {
            if (automatic(fact)) {
                waitingWord.remove(fact.id)
                report(fact)
            }
        }
    }

    private fun report(fact: PendingFacts) {
        if (!reported.add(fact.id)) return
        meter.answered(fact.id, if (automatic(fact)) "auto" else fact.answer ?: return)
    }
}

/**
 * Stage 5, the app's own update (`update_offered`, `update_result` `failed` and `declined`), read from the update state
 * the notice and the prompt draw from. Each release (its tag, kept in memory only, never sent) is offered and declined
 * at most once per run; a failure counts when the update stops on it. `update_result` `patch` has no source in this
 * app (there are no patch updates), and `installed` stays with the start-up version check.
 */
internal class UpdateMeter(private val flow: () -> HealthFlow = { HealthFlow() }) {
    private val offered = HashSet<String>()
    private val declined = HashSet<String>()
    private var lastPhase: UpdatePhase? = null

    fun observe(state: AppUpdateState) {
        val tag = state.release?.tag
        val shown = tag != null && state.phase == UpdatePhase.Available && (state.noticeVisible || state.promptOpen)
        if (shown && offered.add(tag!!)) flow().updateOffered()
        if (tag != null && state.notNow && tag in offered && declined.add(tag)) flow().updateResult("in_app", "declined")
        if (state.phase == UpdatePhase.Failed && lastPhase != UpdatePhase.Failed) {
            flow().updateResult("in_app", "failed", updateFailKind(state.failure))
        }
        lastPhase = state.phase
    }

    companion object {
        /** The contract's `update_result.fail_kind` for why an update stopped. */
        fun updateFailKind(failure: UpdateFailure?): String =
            when (failure) {
                UpdateFailure.Network, UpdateFailure.NoFile -> "download"
                UpdateFailure.Checksum -> "checksum"
                UpdateFailure.Signer -> "signer"
                UpdateFailure.Version -> "version"
                UpdateFailure.Installer, UpdateFailure.Package -> "installer"
                UpdateFailure.Storage, null -> "other"
            }
    }
}

/**
 * Stage 5, `bg_service_end`: the background service ended, once per run of it. `os_limit` when Android's background
 * budget ran out (its dataSync `onTimeout`); `other` when it was destroyed for any other reason while live mode or a reply
 * watch held it (a widget action alone is no background run and is not counted). Words only.
 */
internal class BackgroundServiceEnd(private val flow: () -> HealthFlow = { HealthFlow() }) {
    private var reported = false

    fun timedOut(batteryExempt: Boolean) = end("os_limit", batteryExempt)

    fun destroyed(held: Boolean, batteryExempt: Boolean) {
        if (held) end("other", batteryExempt)
        reported = false
    }

    private fun end(reason: String, batteryExempt: Boolean) {
        if (reported) return
        reported = true
        flow().bgServiceEnd(reason, batteryExempt)
    }

    companion object {
        /** The one the service reports to (the service is one per process). */
        val app: BackgroundServiceEnd = BackgroundServiceEnd()

        /** Whether the person lifted battery optimisation for the app (a yes/no, nothing else). */
        fun batteryExempt(context: android.content.Context): Boolean =
            runCatching {
                    context.getSystemService(android.os.PowerManager::class.java)
                        ?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
                .getOrDefault(false)
    }
}

/** Stage 5, `connect_computer_chosen`: the person opened the form that connects a new computer (no profile yet). */
internal fun choseToConnectComputer(route: AppRoute): Boolean = route is ServersRoute.Edit && route.profile == null
