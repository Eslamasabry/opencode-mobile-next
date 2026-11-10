package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
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
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
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
        SetupError.NodeCannotRun, SetupError.OpenCodeNoProgram, SetupError.OpenCodeWontRun,
        SetupError.OpenCodeNoStart, SetupError.CannotStart, is SetupError.PortBusy,
        SetupError.ToolsUncheckable, SetupError.Unexpected -> "unknown"
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

    override fun seen(): Boolean = marked || answers.firstReplySeen()

    override fun mark() {
        marked = true
        scope.launch { answers.markFirstReplySeen() }
    }
}

/** One pending request as the approval meter needs it. [answer] is null while nobody has answered. */
internal data class PendingFacts(val id: String, val sessionId: String, val kind: String, val answer: String?)

internal fun pendingFacts(list: List<PendingRequest>): List<PendingFacts> =
    list.map { p ->
        val answer: RequestAnswer? =
            when (val phase = p.phase) {
                is AnswerPhase.Held -> phase.answer
                is AnswerPhase.Sending -> phase.answer
                else -> null
            }
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
        PendingFacts(p.id.raw, p.sessionId.raw, kind, result)
    }

/** What the feed tells: the three calls of an [ApprovalMeter]. */
internal interface ApprovalSink {
    fun asked(id: String, sessionId: String, kind: String)

    fun answered(id: String, result: String)

    fun gone(id: String)
}

internal fun ApprovalMeter.asSink(): ApprovalSink =
    object : ApprovalSink {
        override fun asked(id: String, sessionId: String, kind: String) = this@asSink.asked(id, sessionId, kind)

        override fun answered(id: String, result: String) = this@asSink.answered(id, result)

        override fun gone(id: String) = this@asSink.gone(id)
    }

/** Turns the pending list of one connection into asked / answered / gone calls. */
internal class ApprovalFeed(private val meter: ApprovalSink) {
    private val known = HashSet<String>()

    fun update(list: List<PendingFacts>) {
        val ids = list.map { it.id }.toSet()
        for (fact in list) {
            if (known.add(fact.id)) meter.asked(fact.id, fact.sessionId, fact.kind)
            fact.answer?.let { meter.answered(fact.id, it) }
        }
        for (gone in known.toList()) {
            if (gone !in ids) {
                known.remove(gone)
                meter.gone(gone)
            }
        }
    }
}
