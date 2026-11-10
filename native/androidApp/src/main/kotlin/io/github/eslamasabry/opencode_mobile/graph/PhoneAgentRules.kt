package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.readiness.AgentHiddenReason
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import kotlinx.serialization.json.JsonObject
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentStatus as ReadinessStatus

/**
 * The Agents page's rules for what is installed and what its row says, taken from the Flutter app
 * (`phone_agents_host.dart` runtime facts, `buildAgentRow`). Pure: the phone is read elsewhere.
 */
internal object PhoneAgentRules {
    /**
     * Flutter: `installed: version['installed'] == true || version['foundVersion'] is String`. Any copy that runs counts,
     * so a version other than the pin is an update on offer, not an absence.
     */
    fun runs(version: Map<String, Any?>?): Boolean =
        version?.get("installed") == true || version?.get("foundVersion") is String

    /**
     * Whether the agent is on this phone. [payload] is the shared inventory's answer (null when storage could not be
     * read safely); a copy that runs is installed whatever the inventory says, and the inventory only speaks for agents
     * it has a folder for.
     */
    fun present(payload: Boolean?, version: Map<String, Any?>?): Boolean? =
        if (runs(version)) true else payload

    /** Claude Code is the agent the inventory has no folder for, so only its version answers. */
    fun needsVersionProbe(agent: AgentId, payload: Boolean?): Boolean =
        agent == AgentId.CLAUDE || payload == true

    /**
     * The processor the phone runs, from the FIRST (primary) ABI as Flutter did. An x86_64 emulator also lists
     * arm64-v8a (translation); taking that one would never match the proof Flutter stored for x64.
     */
    fun architectureOf(abis: List<String>): AgentArchitecture? =
        when (abis.firstOrNull()) {
            "arm64-v8a" -> AgentArchitecture.Arm64
            "x86_64" -> AgentArchitecture.X64
            else -> null
        }

    /** The copy that runs, as a version string. */
    fun foundVersion(version: Map<String, Any?>?): String? = version?.get("foundVersion") as? String

    /**
     * A sign-in probe starts a process in the phone's Linux, and Android stops an app's child processes past a limit,
     * so none starts while an install runs (the install is the thing those processes could kill).
     */
    fun mayProbeSignIn(installRunning: Boolean): Boolean = !installRunning

    /**
     * This phone's own install of the pinned version is verified by its install step, so it needs no earlier proof; a
     * version proved by the Flutter app counts while its helper is a tested one.
     */
    fun qualified(
        agent: AgentDescriptor,
        architecture: AgentArchitecture,
        gate: JsonObject?,
        found: String?,
        pins: AgentPins = AgentPins.builtIn,
    ): Boolean =
        found != null &&
            (found == agent.recipe?.version || PhoneGate.qualifies(gate, agent, architecture, pins, found))

    /** What a row says, from Flutter's `buildAgentRow` over the facts read. */
    fun status(
        agent: AgentDescriptor,
        architecture: AgentArchitecture,
        installed: Boolean,
        payloadPresent: Boolean,
        qualified: Boolean,
        signIn: AgentSignInPhase?,
    ): PhoneAgentStatus {
        val row =
            AgentReadiness.row(
                agent,
                architecture,
                ServerCapabilities.Unknown,
                PhoneAgentRuntime(
                    agentId = agent.id,
                    installed = installed,
                    payloadPresent = payloadPresent,
                    // The page reads status without the helper; the helper has its own line on the page.
                    hostAvailable = true,
                    architectureQualified = qualified,
                    signInPhase = signIn,
                ),
            )
        return when (row.status) {
            ReadinessStatus.Ready -> PhoneAgentStatus.Ready
            ReadinessStatus.NeedsInstall -> PhoneAgentStatus.NotInstalled
            ReadinessStatus.NeedsQualification -> PhoneAgentStatus.PhoneCheck
            ReadinessStatus.SignedOut -> PhoneAgentStatus.SignInNeeded
            ReadinessStatus.StoppedInBackground -> PhoneAgentStatus.Stopped
            ReadinessStatus.LimitReached -> PhoneAgentStatus.Limit
            ReadinessStatus.Unavailable ->
                when (row.hiddenReason) {
                    AgentHiddenReason.RuntimeUnknown -> PhoneAgentStatus.Checking
                    AgentHiddenReason.UnsupportedArchitecture,
                    AgentHiddenReason.UnverifiedDownload -> PhoneAgentStatus.NotInstalled
                    else -> PhoneAgentStatus.Unavailable
                }
        }
    }

    /**
     * The install steps whose own check already passes. Flutter ran each component's check before its install; the
     * Node download and Paseo `npm ci` are the long ones, so a step that is already in place is not run again.
     * [check] runs one step's check script and answers its exit code (null when it could not run).
     */
    fun satisfiedSteps(steps: Map<String, String>, check: (String) -> Int?): Set<String> =
        steps.filterValues { script -> check(script) == 0 }.keys
}
