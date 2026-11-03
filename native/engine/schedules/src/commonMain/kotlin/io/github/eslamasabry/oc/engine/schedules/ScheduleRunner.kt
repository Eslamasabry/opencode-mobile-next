package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Port of lib/scheduling/runner.dart: one claimed occurrence, with no reconnect and no resend loop. Creating the
 * conversation, changing its model and the prompt are mutations that are never repeated after an ambiguous answer
 * ("uncertain_delivery"). A turn a person started is never steered or interrupted: a busy conversation skips ("busy").
 */
public class ScheduleRunner(
    private val profiles: ScheduleProfiles,
    private val nowMs: () -> Long,
    private val pollMs: Long = 2_000,
) {
    /** [skip]: the run stopped before any mutation for a reason the person need not act on (Flutter ScheduledRunFailure). */
    private class Stop(val code: String, val skip: Boolean) : Exception(code)

    /**
     * [isCurrent] is re-checked around every read: an edit, pause or delete made while the run waits stops it before
     * the next mutation ("stale_target"). [onSessionCreated] persists routing before the prompt boundary.
     */
    public suspend fun execute(
        schedule: ScheduledPrompt,
        deadlineMs: Long,
        isCurrent: suspend () -> Boolean,
        onSessionCreated: suspend (String) -> Unit = {},
        manual: Boolean = false,
    ): ScheduledRun = Attempt(schedule, deadlineMs, isCurrent, onSessionCreated, manual).run()

    private inner class Attempt(
        val schedule: ScheduledPrompt,
        val deadlineMs: Long,
        val isCurrent: suspend () -> Boolean,
        val onSessionCreated: suspend (String) -> Unit,
        val manual: Boolean,
    ) {
        val startedAt = nowMs()
        // A manual "Run now" has no timed moment; it is identified by its start.
        val scheduledAt = if (manual) startedAt else schedule.nextRunAtMs ?: startedAt
        val late = !manual && startedAt - scheduledAt > Recurrences.LATE_TOLERANCE_MS
        var connection: ScheduleConnection? = null
        var sessionId: String? = schedule.sessionId
        var dispatched = false
        var accepted = false

        fun result(status: RunStatus, error: String? = null, title: String? = null, summary: String? = null) =
            ScheduledRun(
                id = ScheduleIds.occurrence(schedule, scheduledAt),
                scheduleId = schedule.id,
                scheduledAtMs = scheduledAt,
                startedAtMs = startedAt,
                finishedAtMs = nowMs(),
                status = status,
                sessionId = sessionId,
                late = late,
                errorCode = error,
                title = title,
                summary = summary,
            )

        fun remaining(): Long = (deadlineMs - nowMs()).also { if (it <= 0) throw Stop("timeout", skip = false) }

        suspend fun <T : Any> bounded(block: suspend () -> T): T =
            withTimeoutOrNull(remaining()) { block() } ?: throw Stop("timeout", skip = false)

        suspend fun guard() {
            if (!bounded { isCurrent() }) throw Stop("stale_target", skip = true)
        }

        fun <T> Outcome<T>.value(): T =
            when (this) {
                is Outcome.Ok -> value
                is Outcome.Failed -> throw Stop(code(failure), skip = false)
            }

        suspend fun <T : Any> read(call: suspend () -> Outcome<T>): T {
            guard()
            val value = bounded { call() }.value()
            guard()
            return value
        }

        suspend fun checkProfile() {
            guard()
            val current = withTimeoutOrNull(remaining()) { profiles.fingerprint(schedule.profileId) }
            if (current != schedule.profileFingerprint) throw Stop("stale_target", skip = true)
        }

        suspend fun busy(gateway: ServerGateway, id: String): Boolean {
            val status = read { gateway.sessionStatuses() }[SessionId(id)]
            return status != null && status != SessionStatus.Idle
        }

        suspend fun run(): ScheduledRun {
            try {
                return attempt()
            } catch (e: Stop) {
                val status = if (dispatched || accepted || !e.skip) RunStatus.Failed else RunStatus.Skipped
                return result(status, if (dispatched && !accepted) "uncertain_delivery" else e.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return result(RunStatus.Failed, if (dispatched && !accepted) "uncertain_delivery" else "unavailable")
            } finally {
                connection?.let { open -> withTimeoutOrNull(CLOSE_WAIT_MS) { open.close() } }
            }
        }

        private suspend fun attempt(): ScheduledRun {
            if (!schedule.valid || (!schedule.enabled && !manual)) throw Stop("stale_target", skip = true)
            checkProfile()
            val opened = bounded { profiles.open(schedule.profileId, schedule.projectPath, schedule.workspace) }.value()
            connection = opened
            val gateway = opened.gateway
            if (!read { gateway.health() }.healthy) throw Stop("unavailable", skip = true)
            if (!gateway.capabilities.value.scheduledPrompts) throw Stop("unsupported", skip = true)
            val model = schedule.modelProviderId?.let { ModelRef(it, schedule.modelId!!) }
            val selection = gateway.facet<SessionSelectionGateway>()
            var before = emptySet<String>()
            val existing = schedule.sessionId
            if (existing != null) {
                val session = read { gateway.session(SessionId(existing)) }
                if (session.directory != null && session.directory != schedule.projectPath) {
                    throw Stop("wrong_project", skip = true)
                }
                guard()
                bounded { onSessionCreated(existing) }
                before = read { gateway.messagePage(SessionId(existing)) }.items.map { it.info.id }.toSet()
                if (busy(gateway, existing)) return result(RunStatus.Skipped, "busy")
            } else {
                checkProfile()
                // Creating the conversation is itself a mutation, never repeated after an ambiguous answer.
                dispatched = true
                val created =
                    bounded {
                        // The selected-session call creates in the gateway's pinned project only; a borrowed gateway
                        // pinned elsewhere creates by location and the model rides with the prompt instead.
                        val pinnedHere = gateway.location.value.directory == schedule.projectPath
                        if (selection != null && pinnedHere && (model != null || schedule.agent != null)) {
                            selection.createSelectedSession(
                                SessionSelection(
                                    model, "", schedule.agent,
                                    modelKnown = model != null, agentKnown = schedule.agent != null,
                                ),
                            )
                        } else {
                            gateway.createSession(NewSession(location = ServerLocation(schedule.projectPath, schedule.workspace)))
                        }
                    }.value()
                sessionId = created.id.raw
                dispatched = false
                guard()
                bounded { onSessionCreated(created.id.raw) }
                guard()
            }
            val target = SessionId(sessionId!!)
            checkProfile()
            val session = read { gateway.session(target) }
            // B-AGENT-218: a conversation the server made in another project never receives the prompt.
            if (session.directory != null && session.directory != schedule.projectPath) throw Stop("wrong_project", skip = false)
            if (busy(gateway, target.raw)) return result(RunStatus.Skipped, "busy")
            if (existing != null && selection != null) {
                // These calls patch only the named field: never write back a whole stale session.
                suspend fun select(action: suspend () -> Outcome<Unit>) {
                    checkProfile()
                    if (busy(gateway, target.raw)) throw Stop("busy", skip = true)
                    guard()
                    dispatched = true
                    bounded { action() }.value()
                    dispatched = false
                }
                val currentAgent = session.selection?.agent ?: session.agent
                val agent = schedule.agent
                if (agent != null && agent != currentAgent) select { selection.setSessionAgent(target, agent) }
                val currentModel = read { gateway.session(target) }.selection?.model
                if (model != null && currentModel != model) select { selection.setSessionModel(target, model, "") }
                // Applying a selection is not an idle reservation: a person may have started a turn meanwhile.
                if (busy(gateway, target.raw)) return result(RunStatus.Skipped, "busy")
            }
            guard()
            dispatched = true
            val admission =
                bounded {
                    gateway.prompt(target, OutgoingPrompt(schedule.prompt, model = model?.let { ModelSelection(it) }, agent = schedule.agent))
                }.value()
            if (admission is PromptAdmission.Uncertain) throw Stop("uncertain_delivery", skip = false)
            accepted = true
            // The server owns its run after acknowledgement. It is followed within this attempt's own deadline so the
            // person can be told when it ends; if the deadline comes first the prompt was still accepted ("started").
            return try {
                follow(gateway, target, before)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result(RunStatus.Ran)
            }
        }

        private suspend fun follow(gateway: ServerGateway, target: SessionId, before: Set<String>): ScheduledRun {
            while (true) {
                val session = read { gateway.session(target) }
                val replies =
                    read { gateway.messagePage(target) }.items.filter { it.info.role == Role.Assistant && it.info.id !in before }
                if (session.lastRunFailed || replies.any { it.info.errorText != null }) {
                    return result(RunStatus.Failed, "agent_failed")
                }
                if (read { gateway.pendingPermissions() }.any { it.sessionId == target }) {
                    return result(RunStatus.Failed, "needs_you")
                }
                if (gateway.capabilities.value.forms && read { gateway.sessionForms(target) }.isNotEmpty()) {
                    return result(RunStatus.Failed, "needs_you")
                }
                val status = read { gateway.sessionStatuses() }[target]
                if ((status == null || status == SessionStatus.Idle) && replies.any { it.info.time?.completedMs != null }) {
                    return result(RunStatus.Ran, null, session.title?.trim()?.takeIf { it.isNotEmpty() }, replyLine(replies))
                }
                delay(pollMs)
            }
        }
    }

    public companion object {
        private const val CLOSE_WAIT_MS = 3_000L

        /** Fixed codes only: never transport text, server payloads, prompts or credentials. */
        public fun code(failure: Failure): String =
            when (failure) {
                is Failure.SignIn -> "auth_required"
                is Failure.Certificate -> "certificate_invalid"
                is Failure.Refused ->
                    when (failure.reason) {
                        RefusalReason.CleartextNotConfirmed -> "cleartext_unconfirmed"
                        RefusalReason.ProfileDeleted, RefusalReason.StaleScope, RefusalReason.ProfileClosing -> "stale_target"
                        RefusalReason.DispatchUncertain -> "uncertain_delivery"
                        else -> "unavailable"
                    }
                is Failure.TimedOut -> "timeout"
                is Failure.Unavailable -> "unsupported"
                else -> "unavailable"
            }

        private val leading = Regex("^[\\s#>*\\-]+")

        /**
         * The first line of the newest reply, about 100 characters, for the "finished" notice. Text parts only: never
         * errors, tool output or reasoning.
         */
        public fun replyLine(replies: List<MessageWithParts>): String? {
            for (message in replies.reversed()) {
                val text = message.parts.filter { it.type == PartType.Text && !it.synthetic }.joinToString("\n") { it.text }
                for (raw in text.split('\n')) {
                    val line = raw.replaceFirst(leading, "").trim()
                    if (line.isEmpty()) continue
                    return if (line.length <= 100) line else line.take(99).trimEnd() + "…"
                }
            }
            return null
        }
    }
}
