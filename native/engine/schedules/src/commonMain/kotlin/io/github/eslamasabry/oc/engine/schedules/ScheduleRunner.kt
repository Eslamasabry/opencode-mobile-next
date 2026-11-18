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
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Port of lib/scheduling/runner.dart: one claimed occurrence, with no reconnect and no resend loop.
 * Creating the conversation, changing its model and the prompt are mutations that are never
 * repeated after an ambiguous answer ("uncertain_delivery"). A turn a person started is never
 * steered or interrupted: a busy conversation skips ("busy").
 */
public class ScheduleRunner(
    private val profiles: ScheduleProfiles,
    private val nowMs: () -> Long,
    private val pollMs: Long = 2_000,
    /** Whether the profile still names the server the schedule was made for (its stamp or an accepted alias). */
    private val accepts: suspend (ScheduledPrompt) -> Boolean = { profiles.fingerprint(it.profileId) == it.profileFingerprint },
) {
    /**
     * [skip]: the run stopped before any mutation for a reason the person need not act on (Flutter
     * ScheduledRunFailure).
     */
    private class Stop(val code: String, val skip: Boolean) : Exception(code)

    /**
     * [isCurrent] is re-checked around every read: an edit, pause or delete made while the run
     * waits stops it before the next mutation ("stale_target"). [onSessionCreated] persists routing
     * before the prompt boundary.
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

        /** B-440: web reads this run answered, and which asks (by id) it already answered, so none counts twice. */
        var webReads = 0
        val answered = mutableSetOf<String>()

        /** Failed reply attempts per ask: a reply can lose a race with the server's own bookkeeping, so it is tried again. */
        val replyTries = mutableMapOf<String, Int>()

        fun result(
            status: RunStatus,
            error: String? = null,
            title: String? = null,
            summary: String? = null,
        ) =
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
                webReadsAllowed = webReads,
            )

        fun remaining(): Long =
            (deadlineMs - nowMs()).also { if (it <= 0) throw Stop("timeout", skip = false) }

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
            val current = withTimeoutOrNull(remaining()) { accepts(schedule) }
            if (current != true) throw Stop("stale_target", skip = true)
        }

        suspend fun busy(gateway: ServerGateway, id: String): Boolean {
            val status = read { gateway.sessionStatuses() }[SessionId(id)]
            return status != null && status != SessionStatus.Idle
        }

        suspend fun run(): ScheduledRun {
            try {
                return attempt()
            } catch (e: Stop) {
                val status =
                    if (dispatched || accepted || !e.skip) RunStatus.Failed else RunStatus.Skipped
                return result(status, if (dispatched && !accepted) "uncertain_delivery" else e.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return result(
                    RunStatus.Failed,
                    if (dispatched && !accepted) "uncertain_delivery" else "unavailable",
                )
            } finally {
                withContext(NonCancellable) {
                    connection?.let { open -> withTimeoutOrNull(CLOSE_WAIT_MS) { open.close() } }
                }
            }
        }

        private suspend fun attempt(): ScheduledRun {
            if (!schedule.valid || (!schedule.enabled && !manual))
                throw Stop("stale_target", skip = true)
            checkProfile()
            val opened = bounded {
                profiles
                    .openUntil(
                        schedule.profileId,
                        schedule.projectPath,
                        schedule.workspace,
                        deadlineMs,
                    )
                    .also {
                        // Keep ownership even if the timeout wins as the open returns to its
                        // caller.
                        if (it is Outcome.Ok) connection = it.value
                    }
            }
                .value()
            val gateway = opened.gateway
            val readyUntil = minOf(deadlineMs, nowMs() + PHONE_READY_MS)
            while (true) {
                guard()
                val health = bounded { gateway.health() }
                guard()
                if (health is Outcome.Ok && health.value.healthy) break
                if (
                    !opened.startedPhoneServer ||
                        nowMs() >= readyUntil ||
                        (health is Outcome.Failed && health.failure is Failure.SignIn)
                ) {
                    if (health is Outcome.Failed) health.value()
                    throw Stop("unavailable", skip = true)
                }
                bounded {
                    delay(pollMs)
                    Unit
                }
            }
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
                before =
                    read { gateway.messagePage(SessionId(existing)) }
                        .items
                        .map { it.info.id }
                        .toSet()
                if (busy(gateway, existing)) return result(RunStatus.Skipped, "busy")
            } else {
                checkProfile()
                // Creating the conversation is itself a mutation, never repeated after an ambiguous
                // answer.
                dispatched = true
                val created = bounded {
                    // The selected-session call creates in the gateway's pinned project only; a
                    // borrowed gateway
                    // pinned elsewhere creates by location and the model rides with the prompt
                    // instead.
                    val pinnedHere = gateway.location.value.directory == schedule.projectPath
                    if (
                        selection != null && pinnedHere && (model != null || schedule.agent != null)
                    ) {
                        selection.createSelectedSession(
                            SessionSelection(
                                model,
                                "",
                                schedule.agent,
                                modelKnown = model != null,
                                agentKnown = schedule.agent != null,
                            )
                        )
                    } else {
                        gateway.createSession(
                            NewSession(
                                location = ServerLocation(schedule.projectPath, schedule.workspace)
                            )
                        )
                    }
                }
                    .value()
                sessionId = created.id.raw
                dispatched = false
                guard()
                bounded { onSessionCreated(created.id.raw) }
                guard()
            }
            val target = SessionId(sessionId!!)
            checkProfile()
            val session = read { gateway.session(target) }
            // B-AGENT-218: a conversation the server made in another project never receives the
            // prompt.
            if (session.directory != null && session.directory != schedule.projectPath)
                throw Stop("wrong_project", skip = false)
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
                if (agent != null && agent != currentAgent)
                    select { selection.setSessionAgent(target, agent) }
                val currentModel = read { gateway.session(target) }.selection?.model
                if (model != null && currentModel != model)
                    select { selection.setSessionModel(target, model, "") }
                // Applying a selection is not an idle reservation: a person may have started a turn
                // meanwhile.
                if (busy(gateway, target.raw)) return result(RunStatus.Skipped, "busy")
            }
            guard()
            dispatched = true
            val admission = bounded {
                gateway.prompt(
                    target,
                    OutgoingPrompt(
                        schedule.prompt,
                        model = model?.let { ModelSelection(it) },
                        agent = schedule.agent,
                    ),
                )
            }
                .value()
            if (admission is PromptAdmission.Uncertain)
                throw Stop("uncertain_delivery", skip = false)
            accepted = true
            // The server owns its run after acknowledgement. It is followed within this attempt's
            // own deadline so the
            // person can be told when it ends; if the deadline comes first the prompt was still
            // accepted ("started").
            return try {
                follow(gateway, target, before)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A phone runtime created for this occurrence cannot be released as a successful
                // run
                // until fresh output finishes. Remote servers continue independently after
                // admission.
                if (opened.startedPhoneServer) throw e
                // Accepted, but what came of it is not known: "sent", never "replied" (settled later by [settle]).
                result(RunStatus.Ran, UNCONFIRMED)
            }
        }

        /**
         * True when a person must answer. B-440: with the schedule's `webReads` on and a server that types web reads
         * ([ServerCapabilities.webReadPermissions]), an ask whose TYPED kind is exactly a web read
         * ([ScheduleWebReads]) is answered "once" here and counted; any other permission (and any ask this cannot
         * answer) still needs the person. Questions and forms are never answered (checked by the caller).
         */
        suspend fun needsYou(gateway: ServerGateway, target: SessionId): Boolean {
            val asks = read { gateway.pendingPermissions() }.filter { it.sessionId == target }
            if (asks.isEmpty()) return false
            val mayAnswer = schedule.webReads && gateway.capabilities.value.webReadPermissions
            for (ask in asks) {
                if (ask.id.raw in answered) continue
                if (!mayAnswer || !ScheduleWebReads.isWebRead(ask.permission)) return true
                val reply = bounded { gateway.replyPermission(target, ask.id, PermissionReply.Once) }
                if (reply is Outcome.Failed) {
                    // Tried again on the next look (about two seconds later); only a third refusal needs the person.
                    val tries = (replyTries[ask.id.raw] ?: 0) + 1
                    replyTries[ask.id.raw] = tries
                    if (tries >= MAX_REPLY_TRIES) return true
                    continue
                }
                answered += ask.id.raw
                webReads++
            }
            return false
        }

        private suspend fun follow(
            gateway: ServerGateway,
            target: SessionId,
            before: Set<String>,
        ): ScheduledRun {
            while (true) {
                val session = read { gateway.session(target) }
                val replies = read {
                    gateway.messagePage(target)
                }
                    .items
                    .filter { it.info.role == Role.Assistant && it.info.id !in before }
                if (session.lastRunFailed || replies.any { it.info.errorText != null }) {
                    return result(RunStatus.Failed, "agent_failed")
                }
                if (needsYou(gateway, target)) return result(RunStatus.Failed, "needs_you")
                if (
                    gateway.capabilities.value.forms &&
                        read { gateway.sessionForms(target) }.isNotEmpty()
                ) {
                    return result(RunStatus.Failed, "needs_you")
                }
                val status = read { gateway.sessionStatuses() }[target]
                if (
                    (status == null || status == SessionStatus.Idle) &&
                        replies.any { it.info.time?.completedMs != null }
                ) {
                    return result(
                        RunStatus.Ran,
                        null,
                        session.title?.trim()?.takeIf { it.isNotEmpty() },
                        replyLine(replies),
                    )
                }
                delay(pollMs)
            }
        }
    }

    /**
     * Settles one run the server accepted but whose reply could not be followed, from the conversation itself. Reads
     * only; sends nothing. Null while it is still unknown (still working, or too soon to say).
     */
    public suspend fun settle(gateway: ServerGateway, run: ScheduledRun, nowMs: Long): ScheduledRun? {
        val target = SessionId(run.sessionId ?: return null)
        val session = (gateway.session(target) as? Outcome.Ok)?.value ?: return null
        val page = (gateway.messagePage(target) as? Outcome.Ok)?.value ?: return null
        // Replies that began after this run was admitted (the conversation may hold older ones).
        val replies =
            page.items.filter { it.info.role == Role.Assistant && (it.info.time?.createdMs ?: 0L) >= run.startedAtMs }
        if (session.lastRunFailed || replies.any { it.info.errorText != null }) return run.copy(status = RunStatus.Failed, errorCode = "agent_failed")
        val statuses = (gateway.sessionStatuses() as? Outcome.Ok)?.value ?: return null
        val status = statuses[target]
        if (status != null && status != SessionStatus.Idle) return null // still working
        if ((gateway.pendingPermissions() as? Outcome.Ok)?.value.orEmpty().any { it.sessionId == target }) {
            return run.copy(status = RunStatus.Failed, errorCode = "needs_you")
        }
        if (replies.any { it.info.time?.completedMs != null }) return run.copy(errorCode = null)
        // Idle for a good while and nothing came back: it did not reply. Sooner than that, keep waiting.
        if (nowMs - run.startedAtMs >= NO_REPLY_AFTER_MS) return run.copy(status = RunStatus.Failed, errorCode = "uncertain_delivery")
        return null
    }

    public companion object {
        /** The stored code of a run the server accepted whose reply was not followed to its end. */
        public const val UNCONFIRMED: String = "unconfirmed"

        private const val MAX_REPLY_TRIES = 3
        private const val NO_REPLY_AFTER_MS = 600_000L
        private const val CLOSE_WAIT_MS = 3_000L
        private const val PHONE_READY_MS = 90_000L

        /** Fixed codes only: never transport text, server payloads, prompts or credentials. */
        public fun code(failure: Failure): String =
            when (failure) {
                is Failure.SignIn -> "auth_required"
                is Failure.Certificate -> "certificate_invalid"
                is Failure.Refused ->
                    when (failure.reason) {
                        RefusalReason.CleartextNotConfirmed -> "cleartext_unconfirmed"
                        RefusalReason.ProfileDeleted,
                        RefusalReason.StaleScope,
                        RefusalReason.ProfileClosing -> "stale_target"
                        RefusalReason.DispatchUncertain -> "uncertain_delivery"
                        else -> "unavailable"
                    }
                is Failure.TimedOut -> "timeout"
                is Failure.Unavailable -> "unsupported"
                else -> "unavailable"
            }

        private val leading = Regex("^[\\s#>*\\-]+")

        /**
         * The first line of the newest reply, about 100 characters, for the "finished" notice. Text
         * parts only: never errors, tool output or reasoning.
         */
        public fun replyLine(replies: List<MessageWithParts>): String? {
            for (message in replies.reversed()) {
                val text =
                    message.parts
                        .filter { it.type == PartType.Text && !it.synthetic }
                        .joinToString("\n") { it.text }
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
