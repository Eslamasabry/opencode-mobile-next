package io.github.eslamasabry.oc.engine.agents.check

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.gate.CheckRequiredReason
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.host.Abi
import io.github.eslamasabry.oc.engine.agents.host.AgentHelper
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.AuthAction
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** The four steps the person sees: Install → Version → Connection → Ready. */
public enum class PhoneCheckStep { Install, Version, Daemon, Hello }

/** Closed failure reasons (`AgentHostFailure` of the Dart check); each has one sentence in words. */
public enum class PhoneCheckFailure {
    Busy, WrongArchitecture, Install, Version, Daemon, Timeout, HelperVersion, NeedsSignIn, Unavailable, Storage, Stale,
}

public data class PhoneCheckResult(
    val agentId: AgentId,
    val architecture: AgentArchitecture?,
    val passed: Boolean,
    val completed: ImmutableList<PhoneCheckStep>,
    val failure: PhoneCheckFailure? = null,
    val observedHelperVersion: String? = null,
    val expectedHelperVersion: String,
) {
    /** Only an ARM64 pass qualifies ARM64: an x86 emulator pass never unlocks a phone. */
    val arm64Qualified: Boolean get() = passed && architecture == AgentArchitecture.Arm64
}

/** What the check needs besides [AgentHost]; each is the Paseo/runtime owner's (S3-PASEO, S3-RTWIRE). */
public interface PhoneCheckPorts {
    /** Installs (or, with [helperOnly], only refreshes the shared helper components for) [agent] and waits. */
    public suspend fun installAndWait(owner: OwnerId, agent: AgentId, helperOnly: Boolean): Outcome<Unit>

    /** The running helper's local work: true busy, false idle, null unknown (unknown refuses as busy). */
    public suspend fun localWorkBusy(owner: OwnerId): Boolean?

    /** The running helper is connected and already runs the pinned Paseo: keep it (no restart drops live chats). */
    public suspend fun liveHelperAtPin(owner: OwnerId): Boolean

    /** Authenticated Paseo `hello` only, within 60 s (never a prompt or a model call). The server version it reports. */
    public suspend fun helloVersion(owner: OwnerId): Outcome<String?>
}

/**
 * The phone check (AGENTS-BACKENDS-09, plan 17 §1.5), ported from `PhoneAgentsHost._phoneCheck` without the Claude
 * rollback path (kept Kotlin `NativeComponentUpdateRecovery`, see the report). On pass it writes the gate; for Claude it
 * ends with a sign-in read so a dead end becomes "needs sign-in". It never restarts a helper that already runs the
 * pinned Paseo, and [checkInFlight] lets the chat refuse a first message while it runs.
 */
public class PhoneCheck(
    private val prefs: AgentPrefs,
    private val host: AgentHost,
    private val helper: AgentHelper,
    private val ports: PhoneCheckPorts,
    private val pins: AgentPins = AgentPins.builtIn,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
) {
    private val running = AtomicBoolean(false)

    public val checkInFlight: Boolean get() = running.load()

    public suspend fun run(owner: OwnerId, agentId: AgentId, automatic: Boolean = false): PhoneCheckResult {
        val agent = catalog.byId(agentId)
        if (agent?.recipe == null || !running.compareAndSet(expectedValue = false, newValue = true)) {
            return fail(agentId, null, persistentListOf(), PhoneCheckFailure.Busy)
        }
        try {
            return check(owner, agent, automatic)
        } finally {
            running.store(false)
        }
    }

    private suspend fun check(owner: OwnerId, agent: AgentDescriptor, automatic: Boolean): PhoneCheckResult {
        val completed = mutableListOf<PhoneCheckStep>()
        val status = host.status.value
        val arch = status.abi.toArchitecture()
            ?: return fail(agent.id, null, completed, PhoneCheckFailure.WrongArchitecture)
        if (status.running && ports.localWorkBusy(owner) != false) return fail(agent.id, arch, completed, PhoneCheckFailure.Busy)
        if (host.prepareUpdate(owner) is Outcome.Failed) return fail(agent.id, arch, completed, PhoneCheckFailure.Unavailable)
        // Clear old proof only once the check has exclusive admission. An automatic update keeps it until the new
        // version passes, so the older version stays usable meanwhile.
        if (!automatic) {
            val gates = PhoneGate.readRaw(prefs, owner).apply { remove(agent.id.raw) }
            if (PhoneGate.write(prefs, owner, gates) is Outcome.Failed) return fail(agent.id, arch, completed, PhoneCheckFailure.Storage)
        }
        val activeVersion = agent.recipe!!.version
        if (ports.installAndWait(owner, agent.id, helperOnly = false) is Outcome.Failed) {
            return fail(agent.id, arch, completed, PhoneCheckFailure.Install)
        }
        completed += PhoneCheckStep.Install

        val installed = host.installedVersion(agent.id)
        if (installed !is Outcome.Ok || installed.value?.version != activeVersion) {
            return fail(agent.id, arch, completed, PhoneCheckFailure.Version)
        }
        val workspace = host.workspaceCheck(owner)
        if (workspace !is Outcome.Ok || !workspace.value.usable) return fail(agent.id, arch, completed, PhoneCheckFailure.Version)
        completed += PhoneCheckStep.Version

        if (status.running && ports.localWorkBusy(owner) != false) return fail(agent.id, arch, completed, PhoneCheckFailure.Busy)
        val keepLive = status.running && ports.liveHelperAtPin(owner)
        if (!keepLive && helper.ensureRunning(owner, restartIfVersionChanged = true) is Outcome.Failed) {
            return fail(agent.id, arch, completed, PhoneCheckFailure.Daemon)
        }
        if (!host.status.value.running && !keepLive) return fail(agent.id, arch, completed, PhoneCheckFailure.Daemon)
        completed += PhoneCheckStep.Daemon

        val observed = when (val hello = ports.helloVersion(owner)) {
            is Outcome.Failed -> return fail(agent.id, arch, completed, PhoneCheckFailure.Timeout)
            is Outcome.Ok -> hello.value?.takeIf { it.length <= 64 && HELPER_VERSION.matches(it) }
        }
        if (observed != pins.paseo.version) return fail(agent.id, arch, completed, PhoneCheckFailure.HelperVersion, observed)

        // Installation proof survives sign-out: signing in later needs no download or repeated qualification.
        val gates = PhoneGate.readRaw(prefs, owner)
        gates[agent.id.raw] = PhoneGate.entry(PhoneGate.fingerprint(agent, arch, pins), arch, activeVersion)
        if (PhoneGate.write(prefs, owner, gates) is Outcome.Failed) return fail(agent.id, arch, completed, PhoneCheckFailure.Storage, observed)

        if (agent.id == AgentId.CLAUDE) {
            val auth = host.auth(owner, agent.id, AuthAction.Probe, CLAUDE_PROBE_TIMEOUT)
            val projection = (auth as? Outcome.Ok)?.value
            if (projection?.state == AuthState.SignedOut || projection?.error == AuthProbeError.SignInExpired) {
                return fail(agent.id, arch, completed, PhoneCheckFailure.NeedsSignIn, observed)
            }
            if (projection?.state != AuthState.SignedIn) {
                val timedOut = projection?.error == AuthProbeError.TimedOut
                return fail(agent.id, arch, completed, if (timedOut) PhoneCheckFailure.Timeout else PhoneCheckFailure.Unavailable, observed)
            }
        }
        completed += PhoneCheckStep.Hello
        return PhoneCheckResult(agent.id, arch, true, completed.toImmutableList(), null, observed, pins.paseo.version)
    }

    private fun fail(
        agent: AgentId,
        arch: AgentArchitecture?,
        completed: List<PhoneCheckStep>,
        failure: PhoneCheckFailure,
        observed: String? = null,
    ) = PhoneCheckResult(agent, arch, false, completed.toImmutableList(), failure, observed, pins.paseo.version)

    public companion object {
        public val HELPER_VERSION: Regex = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?$")
        public val CLAUDE_PROBE_TIMEOUT: kotlin.time.Duration = 65.seconds
    }
}

internal fun Abi?.toArchitecture(): AgentArchitecture? =
    when (this) {
        Abi.Arm64V8a -> AgentArchitecture.Arm64
        Abi.X86_64 -> AgentArchitecture.X64
        null -> null
    }

/** What `inspect()` concludes from the gate and the strict inventory, before sign-in (fed into readiness). */
public data class GateInspection(
    val agentId: AgentId,
    val installed: Boolean,
    val payloadPresent: Boolean,
    val hostAvailable: Boolean,
    val architectureQualified: Boolean,
    val behindPin: Boolean,
    val checkRequiredReason: CheckRequiredReason?,
    val stoppedInBackground: Boolean,
)

/** Port of `PhoneAgentsHost.inspect` (B-LINUX-129, 132, 133, 134). */
public class GateInspector(
    private val prefs: AgentPrefs,
    private val host: AgentHost,
    private val pins: AgentPins = AgentPins.builtIn,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
) {
    public suspend fun inspect(owner: OwnerId, agentId: AgentId): Outcome<GateInspection> {
        val agent = catalog.byId(agentId)?.takeIf { it.recipe != null }
            ?: return GateInspection(agentId, false, false, false, false, false, CheckRequiredReason.FirstCheck, false).ok()
        val status = host.status.value
        val arch = status.abi.toArchitecture()
        val pin = PhoneGate.read(prefs, owner)[agentId.raw]
        val fingerprintMatches = arch != null &&
            (pin?.get("fingerprint") as? JsonPrimitive)?.content == PhoneGate.fingerprint(agent, arch, pins)
        val installed = when (val v = host.installedVersion(agentId)) {
            is Outcome.Failed -> return v
            is Outcome.Ok -> v.value
        }
        val qualified = fingerprintMatches && installed?.version == agent.recipe!!.version
        val older = if (qualified || arch == null) null else PhoneGate.olderQualifiedVersion(pin, agent, arch, pins)
            ?.takeIf { it == installed?.version }
        val proven = qualified || older != null
        return GateInspection(
            agentId = agentId,
            // Any version that runs is installed: a pin bump must not read as "Not installed".
            installed = installed?.installed == true,
            payloadPresent = installed?.payloadPresent == true,
            hostAvailable = status.running,
            architectureQualified = proven,
            behindPin = older != null,
            checkRequiredReason = if (proven) null else PhoneGate.changeReason(pin, agent, pins),
            stoppedInBackground = proven && !status.running,
        ).ok()
    }
}

/**
 * The readiness input for one agent: this inspection plus the sign-in phase ([signInPhase] null = not inspected) and
 * the runtime capability proof the verified host load supplied (never inferred from the agent's name).
 */
public fun GateInspection.toRuntime(
    signInPhase: io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase?,
    capabilities: io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities =
        io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities.None,
    resetAtEpochMillis: Long? = null,
): io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime =
    io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime(
        agentId = agentId,
        installed = installed,
        payloadPresent = payloadPresent,
        hostAvailable = hostAvailable,
        architectureQualified = architectureQualified,
        capabilities = capabilities,
        signInPhase = signInPhase,
        stoppedInBackground = stoppedInBackground,
        resetAtEpochMillis = resetAtEpochMillis,
        checkRequiredReason = checkRequiredReason,
        behindPin = behindPin,
    )
