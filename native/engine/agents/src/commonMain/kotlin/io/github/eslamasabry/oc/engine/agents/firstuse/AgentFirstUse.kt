package io.github.eslamasabry.oc.engine.agents.firstuse

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs

/**
 * "Set up at first use" (coordinator ruling 2026-11-13, owner rule "don't hide a problem, just fix it"). Some phone
 * agents have no read-only way to tell signed in from signed out (Qwen Code 0.26.4 removed `qwen auth status`; Goose
 * 1.54.0's only check prompts the model; omp's ACP never refuses a session), so the sign-in check script refuses them
 * (`probeUnsupported`). The app does not pretend to know: such an agent, once installed, is usable; the person sets it
 * up in its own terminal, and the truth comes from the agent's own answer when a conversation starts — the one moment
 * creating a session is what the person asked for. Never "Ready", never "Signed in", never an account name.
 *
 * Which agents: derived from the script's own supported list ([AgentAuthScripts.checksSignIn]), never a second list.
 */
public object AgentFirstUse {
    public fun applies(agent: AgentDescriptor): Boolean =
        agent.runsOnPhoneHost &&
            agent.recipe != null &&
            agent.signInMethod != AgentSignInMethod.None &&
            !AgentAuthScripts.checksSignIn(agent.id)

    /** When the agent's failure came: opening the conversation (ACP `session/new`) or answering its first message. */
    public enum class Phase { StartConversation, FirstPrompt }

    /** What a failed start or first answer of a set-up-at-first-use agent means for the person. */
    public enum class Verdict {
        /** "<agent> needs setting up before it can answer", with "Set up <agent>"; the typed message is kept. */
        NeedsSetUp,

        /** A plain failure with Try again (the words of the existing failure path). */
        Plain,
    }

    /**
     * Classifies one failure of [agent] once, from the agent's own answer. The JSON-RPC code never reaches the app (the
     * helper passes only its sentence, capped, in `Failure.details`), so [reason] is that text; it is matched, never
     * shown. [setUpDone]: the person finished this agent's set-up terminal ([AgentSetUpStore]).
     *
     * - Qwen Code (and an installed Gemini CLI): `session/new` answers -32000 "authentication required" ("Use Qwen Code
     *   CLI to authenticate first.") until a provider is configured; the helper may also refuse the provider as
     *   "AuthRequired" before sending.
     * - Goose: `session/new` on a home that was never configured answers -32603 (an internal error), not -32000, so a
     *   failed start of a Goose that was never set up through the app means it needs setting up; after set-up the
     *   same error is a plain failure.
     * - omp: sessions open without credentials; the first answer fails with "No model selected." or "No API key found
     *   for <provider>." (omp 18.5.1 `AgentSession`), wrapped as -32603.
     * - Agents the check script can read have their own sign-in path: always [Verdict.Plain] here.
     */
    public fun classify(agent: AgentDescriptor, phase: Phase, reason: String?, setUpDone: Boolean): Verdict {
        if (!applies(agent)) return Verdict.Plain
        val text = reason.orEmpty()
        if (AUTH_REQUIRED.containsMatchIn(text)) return Verdict.NeedsSetUp
        return when (agent.id.raw) {
            GOOSE ->
                if (phase == Phase.StartConversation && !setUpDone) Verdict.NeedsSetUp else Verdict.Plain
            OMP -> if (OMP_NO_MODEL.containsMatchIn(text)) Verdict.NeedsSetUp else Verdict.Plain
            else -> Verdict.Plain
        }
    }

    private val AUTH_REQUIRED = Regex("auth(?:entication)?[ _-]?required|authenticate first", RegexOption.IGNORE_CASE)
    private val OMP_NO_MODEL = Regex("No model selected\\.|No API key found for ")
    private const val GOOSE = "goose"
    private const val OMP = "omp-acp"
}

/**
 * Which agents the person set up through the app's set-up terminal, per owner (`oc.agentSetUp.<owner>`, a list of
 * agent ids). Marked when the terminal session ends, whatever it claims: nothing here can verify a sign-in. Swept
 * with the owner's profile by the generic `oc.<what>.<profileId>` rule.
 */
public class AgentSetUpStore(private val prefs: AgentPrefs) {
    public fun isSetUp(owner: OwnerId, agent: AgentId): Boolean =
        agent.raw in prefs.stringList(AgentKeys.agentSetUp(owner)).orEmpty()

    public suspend fun markSetUp(owner: OwnerId, agent: AgentId): Outcome<Unit> {
        val key = AgentKeys.agentSetUp(owner)
        val now = prefs.stringList(key).orEmpty()
        if (agent.raw in now) return Outcome.Ok(Unit)
        return prefs.commit { putStringList(key, (now + agent.raw).sorted()) }
    }
}
