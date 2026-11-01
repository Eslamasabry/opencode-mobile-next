package io.github.eslamasabry.oc.engine.agents.memory

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.memory.DartJsonText.text
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs

/**
 * Why an agent's newest turn failed, as the chat saw it. [reason] is the host's own text, already redacted: it is
 * shown only under Details, never as copy (09 `AgentErrorCause` classifies it; this engine only stores it).
 */
public data class RememberedTurnFailure(val reason: String, val provider: String? = null, val model: String? = null) {
    override fun toString(): String = "RememberedTurnFailure(${reason.length} chars, provider=$provider)"
}

/**
 * Per conversation and saved server (plan 17 §1.8, port of `AgentTurnFailureMemory` in
 * `lib/state/chat_notice_memory.dart`). Paseo 0.11.2 forgets a failure's words on restart, so the app keeps them:
 * a Flutter StringList under `oc.agentTurnFailures.<profileId>`, one JSON entry `{"s","r","p"?,"m"?}` per
 * conversation, newest last, redacted, at most [MAX_ENTRIES]. Forgotten when a later turn answers.
 *
 * [redact] is the app's `Redactor.redact` (masks known secrets and credential shapes before anything is stored).
 */
public class AgentTurnFailureMemory(private val prefs: AgentPrefs, private val redact: (String) -> String) {
    private fun entries(profile: ProfileId) = MemoryLines.read(prefs.stringList(AgentKeys.agentTurnFailures(profile)))

    /** The newest kept failure of [sessionId], or of another record of the same conversation ([alsoAs]). */
    public fun read(profile: ProfileId, sessionId: String, alsoAs: Collection<String> = emptyList()): RememberedTurnFailure? {
        val ids = alsoAs.toSet() + sessionId
        val entry = entries(profile).lastOrNull { it.second.text("s") in ids }?.second ?: return null
        val reason = entry.text("r")?.takeIf { it.isNotBlank() } ?: return null
        return RememberedTurnFailure(reason, entry.text("p"), entry.text("m"))
    }

    public suspend fun remember(profile: ProfileId, sessionId: String, failure: RememberedTurnFailure): Outcome<Boolean> {
        val entry = linkedMapOf<String, Any?>("s" to sessionId, "r" to redact(failure.reason))
        failure.provider?.let { entry["p"] = it }
        failure.model?.let { entry["m"] = it }
        val kept = entries(profile).filter { it.second.text("s") != sessionId }.map { it.first } + DartJsonText.encode(entry)
        return MemoryLines.write(prefs, AgentKeys.agentTurnFailures(profile), kept.takeLast(MAX_ENTRIES))
    }

    /** Forgets [sessionId] (and [alsoAs]); no write when nothing matched. */
    public suspend fun forget(profile: ProfileId, sessionId: String, alsoAs: Collection<String> = emptyList()): Outcome<Boolean> {
        val ids = alsoAs.toSet() + sessionId
        val all = entries(profile)
        val kept = all.filter { it.second.text("s") !in ids }
        if (kept.size == all.size) return Outcome.Ok(true)
        return MemoryLines.write(prefs, AgentKeys.agentTurnFailures(profile), kept.map { it.first })
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 100
    }
}
