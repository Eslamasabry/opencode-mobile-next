package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor

// Flutter lib/state/sv3_skill_conversation.dart.

public enum class SkillConversationStatus {
    Ready, Unsupported, InvalidInput, Changed, AlreadyStarted, CreationUncertain, ActivationUncertain, PromptUncertain,
}

/** Acknowledgement of preparation/dispatch, never proof an agent wrote a file. [sessionId] survives second-step failures. */
public data class SkillConversationResult(val status: SkillConversationStatus, val sessionId: String? = null)

/** The picker selection captured when the person confirmed; null parts use server defaults. */
public data class SkillSelection(val model: String? = null, val agent: String? = null, val variant: String = "")

/** The connection-side ports. Errors may be thrown or returned; neither ever reaches the person. */
public interface SkillConversationPorts {
    public val isClosed: Boolean
    public val canUseSkill: Boolean
    public val canAskForSkill: Boolean

    public suspend fun createSession(selection: SkillSelection): Outcome<String>

    public suspend fun prompt(sessionId: String, text: String, selection: SkillSelection): Outcome<Unit>

    public suspend fun activateSkill(sessionId: String, skillId: String, resume: Boolean): Outcome<Unit>
}

/**
 * One explicitly requested new-conversation action, bound to a connection. Repeated calls cannot create a second
 * session, even after an ambiguous failure. Keeps no durable state and never logs failures or skill content.
 */
public class SkillConversation(
    private val ports: SkillConversationPorts,
    private val redactor: Redactor,
    private val isCurrent: () -> Boolean,
    private val selection: SkillSelection = SkillSelection(),
) {
    private var started = false

    /** Creates a session and stages the exact catalog [skillId] without running the agent. */
    public suspend fun useInNewConversation(skillId: String?): SkillConversationResult {
        if (!ports.canUseSkill) return SkillConversationResult(SkillConversationStatus.Unsupported)
        if (skillId == null || !safeIdentifier(skillId)) return SkillConversationResult(SkillConversationStatus.InvalidInput)
        return start(SkillConversationStatus.ActivationUncertain) { ports.activateSkill(it, skillId, resume = false) }
    }

    /**
     * Asks the agent for a SKILL.md draft through the prompt endpoint. The person's requirements are redacted before
     * dispatch (the server keeps prompt text); installing is never requested or inferred from the acknowledgement.
     */
    public suspend fun askAgentToWriteSkill(requirements: String): SkillConversationResult {
        if (!ports.canAskForSkill) return SkillConversationResult(SkillConversationStatus.Unsupported)
        val brief = redactor.redact(requirements.trim())
        if (brief.isEmpty()) return SkillConversationResult(SkillConversationStatus.InvalidInput)
        return start(SkillConversationStatus.PromptUncertain) {
            ports.prompt(it, SKILL_DRAFT_PREFIX + brief, selection)
        }
    }

    private suspend fun start(failure: SkillConversationStatus, action: suspend (String) -> Outcome<Unit>): SkillConversationResult {
        if (started) return SkillConversationResult(SkillConversationStatus.AlreadyStarted)
        if (!current()) return SkillConversationResult(SkillConversationStatus.Changed)
        started = true
        val created = attempt({ Failure.Device() }) { ports.createSession(selection) }
        val sessionId =
            (created as? Outcome.Ok)?.value?.let { it as? Outcome.Ok }?.value
                ?: return SkillConversationResult(SkillConversationStatus.CreationUncertain)
        if (!current()) return SkillConversationResult(SkillConversationStatus.Changed, sessionId)
        val sent = attempt({ Failure.Device() }) { action(sessionId) }
        if (sent !is Outcome.Ok || sent.value !is Outcome.Ok) return SkillConversationResult(failure, sessionId)
        return SkillConversationResult(
            if (current()) SkillConversationStatus.Ready else SkillConversationStatus.Changed,
            sessionId,
        )
    }

    private fun current(): Boolean = !ports.isClosed && isCurrent()

    private fun safeIdentifier(id: String): Boolean =
        id.isNotEmpty() && id == id.trim() && !CONTROL_CHARACTERS.containsMatchIn(id) && !redactor.containsSecret(id)

    public companion object {
        public const val SKILL_DRAFT_PREFIX: String =
            "Draft a reusable SKILL.md for the following requirements. " +
                "Return the draft in this conversation for review. " +
                "Do not install it or modify files.\n\n"
    }
}
