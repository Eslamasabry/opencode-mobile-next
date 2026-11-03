package io.github.eslamasabry.oc.engine.providerlogos

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Port of lib/state/sv3_skill_conversation.dart + lib/domain/sv3_skill_capabilities.dart (B-CHAT-108).
 */

/** Plain-word outcomes of a skill conversation action. Never server text. */
public enum class SkillConversationStatus {
    Ready,
    Unsupported,
    InvalidInput,
    Changed,
    AlreadyStarted,
    CreationUncertain,
    ActivationUncertain,
    PromptUncertain,
}

/**
 * Acknowledgement of preparation/dispatch, never proof an agent wrote a file. [session] is retained after
 * second-step failures so the caller can open/reconcile it: never delete it automatically or repeat the request.
 */
public data class SkillConversationResult(val status: SkillConversationStatus, val session: Session? = null)

/**
 * PORT (server): the slice of one connected gateway this action uses. The Android adapter wraps the bound
 * `ServerGateway` (core:domain) plus the runtime-probed session-skill facet (Dart `SessionSkillGateway`, not yet in
 * core:domain).
 */
public interface SkillConversationServer {
    /** `ServerCapabilities.serverCatalog`. */
    public val serverCatalog: Boolean

    /** Dart `SessionSkillGateway.sessionSkillsSupported`; false when the gateway has no session-skill facet. */
    public val sessionSkillsSupported: Boolean

    /** Dart `gateway.isClosed`: the gateway was closed (disconnect, profile switch). */
    public val isClosed: Boolean

    /**
     * Creates one session. Gateways implementing `SessionSelectionGateway` call `createSelectedSession(selection)`;
     * others call `createSession()` and the selection rides on prompts. Any failure (the request may have reached
     * the server) is `Outcome.Failed`; it is never retried.
     */
    public suspend fun createSession(selection: SessionSelection): Outcome<Session>

    /** Stages [skillId] on [session] without running the agent (`resume` false). Failed = uncertain. */
    public suspend fun activateSessionSkill(session: SessionId, skillId: String, resume: Boolean): Outcome<Unit>

    /** Dart `promptAsync`: dispatches [text]. Ok only when admission was confirmed; lost/failed is Failed. */
    public suspend fun promptAsync(
        session: SessionId,
        text: String,
        model: ModelRef?,
        agent: String?,
        variant: String?,
    ): Outcome<Unit>
}

/** PORT (redaction): the app's credential masker (core:security `Redactor`, with registered provider keys). */
public interface SecretScreen {
    public fun redact(text: String): String

    public fun containsSecret(text: String): Boolean
}

/** Activation additionally needs the optional, runtime-probed session-skill facet. */
public fun canStartSkillConversation(server: SkillConversationServer): Boolean =
    server.serverCatalog && server.sessionSkillsSupported

/** Requests a draft through ordinary prompting; does not install a skill. */
public fun skillAuthoringRequests(server: SkillConversationServer): Boolean = server.serverCatalog

/** No provider discovery contract supplies a key provisioning page URL. */
public const val ProviderKeyPageLinks: Boolean = false

/**
 * One explicitly requested new-conversation action, bound to a connection. Construct once per user-confirmed action:
 * repeated calls cannot create a second session, even after an ambiguous network failure. Keeps no durable state and
 * never logs transport failures or skill content.
 *
 * [isCurrent] checks the captured profile/location revision and the gateway identity; the owner invalidates it on
 * deletion or disconnection. [selection] is the captured picker selection: applied on creation where supported and on
 * prompts. The owner refreshes its session inventory after this action.
 */
public class SkillConversationController(
    private val server: SkillConversationServer,
    private val secrets: SecretScreen,
    private val isCurrent: () -> Boolean,
    private val selection: SessionSelection = SessionSelection(null, "", null, false, false),
) {
    private val startLock = Mutex()
    private var started = false

    public val canUseSkill: Boolean get() = canStartSkillConversation(server)

    public val canAskForSkill: Boolean get() = skillAuthoringRequests(server)

    /** Creates a session, stages the exact catalog ID and leaves the agent idle. A name never replaces a missing ID. */
    public suspend fun useInNewConversation(skill: SkillInfo): SkillConversationResult {
        if (!canUseSkill) return SkillConversationResult(SkillConversationStatus.Unsupported)
        val id = skill.id
        if (id == null || !safeIdentifier(id)) return SkillConversationResult(SkillConversationStatus.InvalidInput)
        return start(SkillConversationStatus.ActivationUncertain) { session ->
            server.activateSessionSkill(session.id, id, resume = false)
        }
    }

    /**
     * Asks the agent for a SKILL.md draft via the ordinary prompt. The requirements are redacted before dispatch;
     * installation is never requested or inferred from the acknowledgement.
     */
    public suspend fun askAgentToWriteSkill(requirements: String): SkillConversationResult {
        if (!canAskForSkill) return SkillConversationResult(SkillConversationStatus.Unsupported)
        val brief = secrets.redact(requirements.trim())
        if (brief.isEmpty()) return SkillConversationResult(SkillConversationStatus.InvalidInput)
        return start(SkillConversationStatus.PromptUncertain) { session ->
            server.promptAsync(
                session.id,
                text = DRAFT_REQUEST + brief,
                model = selection.model,
                agent = selection.agent,
                variant = selection.variant.ifEmpty { null },
            )
        }
    }

    private suspend fun start(
        failure: SkillConversationStatus,
        action: suspend (Session) -> Outcome<Unit>,
    ): SkillConversationResult {
        val claimed = startLock.withLock {
            when {
                started -> SkillConversationStatus.AlreadyStarted
                !current() -> SkillConversationStatus.Changed
                else -> {
                    started = true
                    null
                }
            }
        }
        if (claimed != null) return SkillConversationResult(claimed)
        // Creation may have reached the server. There is no idempotency promise.
        val session = when (val created = server.createSession(selection)) {
            is Outcome.Ok -> created.value
            is Outcome.Failed -> return SkillConversationResult(SkillConversationStatus.CreationUncertain)
        }
        if (!current()) return SkillConversationResult(SkillConversationStatus.Changed, session)
        // Server failure text is never surfaced: it may contain credentials.
        if (action(session) is Outcome.Failed) return SkillConversationResult(failure, session)
        val status = if (current()) SkillConversationStatus.Ready else SkillConversationStatus.Changed
        return SkillConversationResult(status, session)
    }

    private fun current(): Boolean = !server.isClosed && isCurrent()

    private fun safeIdentifier(id: String): Boolean =
        id.isNotEmpty() &&
            id == id.trim() &&
            id.none { it.code in 0x00..0x1f || it.code == 0x7f } &&
            !secrets.containsSecret(id)

    private companion object {
        const val DRAFT_REQUEST = "Draft a reusable SKILL.md for the following requirements. " +
            "Return the draft in this conversation for review. " +
            "Do not install it or modify files.\n\n"
    }
}
