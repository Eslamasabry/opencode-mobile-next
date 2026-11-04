package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok

/** What `create_agent_request` carries for a handoff's first prompt. */
public data class HandoffAgentCreate(
    val initialPrompt: String,
    val provider: String,
    val model: String,
    val cwd: String,
    val mode: String?,
    val variant: String?,
)

/** The agent daemon calls a handoff's first prompt needs. */
public interface HandoffAgentDaemon {
    /** `get_providers_snapshot`: the providers that are ready now. */
    public suspend fun providers(): Outcome<List<HandoffProvider>>

    /** Creates the agent with its first prompt; the daemon's own (durable) agent id. */
    public suspend fun createAgent(request: HandoffAgentCreate): Outcome<String>
}

public sealed interface HandoffAgentStart {
    public data class Started(val daemonId: String) : HandoffAgentStart

    public data class Refused(val reason: HandoffAgentRefusal) : HandoffAgentStart
}

public enum class HandoffAgentRefusal {
    /** [HandoffAgentStarter.draft] was not called for this id, or its agent already exists. */
    NotADraft,
    /** The provider or model is not ready on the daemon (preflight). */
    ProviderUnavailable,
    /** The dispatch fence refused after preflight; nothing was created. */
    Fenced,
}

/**
 * The live/durable identity rules of a phone-agent handoff target (Paseo gateway `daemonSessionId`,
 * `handoffSessionId`, `handoffNavigationSessionId`, the draft branch of `_promptAsync`). A draft keeps its app id until
 * its first prompt creates the agent; afterwards the daemon's id is both the durable identity and the navigation id,
 * because the conversation opens on the agent backend's own gateway, which knows the agent only by the daemon's id.
 * The dispatch fence runs after the provider preflight and before creation.
 */
public class HandoffAgentStarter(private val daemon: HandoffAgentDaemon, private val directory: String) {
    private val drafts = mutableSetOf<String>()
    private val real = mutableMapOf<String, String>()
    private val app = mutableMapOf<String, String>()

    /** Registers a draft conversation (created locally, no agent yet). */
    public fun draft(appId: String) {
        drafts += appId
    }

    /** The daemon's id for [appId] once its agent exists; otherwise [appId] itself. */
    public fun durableSessionId(appId: String): String = real[appId] ?: appId

    /** The id another gateway on the same daemon opens the conversation by. */
    public fun navigationSessionId(sessionId: String): String = durableSessionId(sessionId)

    /** This gateway's id for the daemon's [daemonId]: the draft's id when this gateway created it. */
    public fun appSessionId(daemonId: String): String = app[daemonId] ?: daemonId

    public suspend fun promptHandoff(
        sessionId: String,
        text: String,
        model: HandoffModelRef,
        agent: String?,
        variant: String?,
        beforeSend: () -> Boolean,
    ): Outcome<HandoffAgentStart> {
        val appId = appSessionId(sessionId)
        if (appId !in drafts) return HandoffAgentStart.Refused(HandoffAgentRefusal.NotADraft).ok()
        val normalized = model.normalized
        val inventory = when (val p = daemon.providers()) {
            is Outcome.Failed -> return p
            is Outcome.Ok -> p.value
        }
        val provider = inventory.singleOrNull { it.id == normalized.providerId }
        if (provider == null || provider.modelIds.none { bareModelId(provider.id, it) == normalized.modelId }) {
            return HandoffAgentStart.Refused(HandoffAgentRefusal.ProviderUnavailable).ok()
        }
        if (!beforeSend()) return HandoffAgentStart.Refused(HandoffAgentRefusal.Fenced).ok()
        val created = daemon.createAgent(
            HandoffAgentCreate(text, normalized.providerId, normalized.modelId, directory, agent, variant?.ifEmpty { null }),
        )
        val daemonId = when (created) {
            is Outcome.Failed -> return created
            is Outcome.Ok -> created.value
        }
        drafts -= appId
        real[appId] = daemonId
        app[daemonId] = appId
        return HandoffAgentStart.Started(daemonId).ok()
    }
}
