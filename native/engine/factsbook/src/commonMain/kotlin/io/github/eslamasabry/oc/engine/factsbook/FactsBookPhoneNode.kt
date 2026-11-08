package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Why a step of running the node on this phone failed (F: factsbook_node_host.dart `NodeHostFailure`). */
public enum class NodeHostFailure { NoBinary, BadBinary, Prepare, Start, Pair, Remove }

/** A host step: done, or failed with technical [detail] for Details only (never the passphrase or a code). */
public sealed interface NodeStep<out T> {
    public data class Done<out T>(val value: T) : NodeStep<T>

    public data class Failed(val failure: NodeHostFailure, val detail: String? = null) : NodeStep<Nothing>
}

/** What the built-in Linux says about the node right now. */
public data class NodeHostFacts(
    val linuxReady: Boolean = false,
    val binaryInstalled: Boolean = false,
    /** A vault was created (the node has been set up here). */
    val dataExists: Boolean = false,
    val serviceRunning: Boolean = false,
    /** A build for this phone is installed or in the documented place, ready to install. */
    val binaryAvailable: Boolean = false,
)

/** What `factsbook-node pair` printed that the app needs. Never logged, never shown. */
public class NodePairing(public val code: String, public val ownerDevice: String?) {
    override fun toString(): String = "NodePairing(<code>)"

    public companion object {
        /** The node's one JSON line (`{"code":..,"owner_device":..}`) out of everything the command printed. */
        public fun parse(output: String): NodePairing? =
            output.lines().asReversed().firstNotNullOfOrNull { line ->
                val text = line.trim()
                if (!text.startsWith("{")) return@firstNotNullOfOrNull null
                val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
                json.str("code")?.let { NodePairing(it, json.str("owner_device")) }
            }
    }
}

/** The steps of running the node inside the built-in Linux (F: `FactsBookNodeHost`). The app binds the real one. */
public interface FactsBookNodeHost {
    /** The built-in Linux exists on this build (Android with the runtime). */
    public val supported: Boolean

    /** The address the node listens on: loopback only, inside this phone. */
    public val address: String

    public suspend fun inspect(): NodeStep<NodeHostFacts>

    /** Copies the node build from the documented place into the Linux and checks it. [NodeHostFailure.NoBinary] without one. */
    public suspend fun installBinary(): NodeStep<Unit>

    /** Writes the config, makes a passphrase (kept in the secret store), creates the vault. A vault that exists stays. */
    public suspend fun createVault(): NodeStep<Unit>

    public suspend fun startNode(): NodeStep<Unit>

    public suspend fun stopNode(): NodeStep<Unit>

    /** A one-time pairing code that also enrols [ownerKeyHex] as this phone's approval key. */
    public suspend fun pairingCode(ownerKeyHex: String): NodeStep<NodePairing>

    public suspend fun log(): String

    /** Stops the node and deletes its data folder and passphrase. The program stays. */
    public suspend fun removeData(): NodeStep<Unit>
}

public enum class PhoneNodeState { Checking, Unsupported, LinuxNotSetUp, NoBuild, NotInstalled, Installing, Running, Stopping, Stopped, Removing, Failed }

public enum class PhoneNodeStep { Copying, Preparing, Starting, Pairing }

public enum class PhoneNodeProblem { Install, Start, Pair, Stop, Remove }

public data class PhoneNodeStatus(
    val state: PhoneNodeState = PhoneNodeState.Checking,
    val step: PhoneNodeStep = PhoneNodeStep.Copying,
    val problem: PhoneNodeProblem? = null,
    /** Technical text for Details only. */
    val failure: String? = null,
    val facts: NodeHostFacts = NodeHostFacts(),
    /** A step or the start on app open is under way: coming up, not failed. */
    val busy: Boolean = false,
    /** This app is paired with the node on this phone. */
    val pairedHere: Boolean = false,
    /** The person pressed Stop and has not started it since. */
    val personStopped: Boolean = false,
) {
    override fun toString(): String = "PhoneNodeStatus($state, $problem)"
}

/**
 * FactsBook running on this phone, inside the built-in Linux (F: factsbook_phone_node.dart): install the node, start it
 * on the loopback address, pair this app with it by itself, stop it, and remove it with its data. One run does it all;
 * each step can be asked again, and a vault that exists is never made twice. One lane.
 */
public class FactsBookPhoneNode(
    private val engine: FactsBookEngine,
    private val host: FactsBookNodeHost,
    private val store: FactsBookStore,
    /** The unauthorized client for the node's address, for the probe and the pairing. */
    private val pairingGateway: (String) -> HttpFactsBookGateway,
    /** This phone's IANA time zone, adopted by a new node right after pairing. */
    private val phoneZone: () -> String?,
    private val probeAttempts: Int = 30,
    private val probeDelay: Duration = 1.seconds,
    private val linuxWaitAttempts: Int = 30,
    private val linuxWaitDelay: Duration = 3.seconds,
) {
    private val lane = Mutex()
    private val mutable = MutableStateFlow(PhoneNodeStatus())
    public val status: StateFlow<PhoneNodeStatus> = mutable.asStateFlow()

    public val supported: Boolean get() = host.supported

    private val baseUrl: String get() = "http://${host.address}"

    private fun pairedHere(): Boolean = engine.state.value.paired && engine.state.value.link?.baseUrl == baseUrl

    private fun set(state: PhoneNodeState, step: PhoneNodeStep? = null) {
        mutable.update {
            it.copy(
                state = state,
                step = step ?: it.step,
                problem = if (state == PhoneNodeState.Failed) it.problem else null,
                failure = if (state == PhoneNodeState.Failed) it.failure else null,
                pairedHere = pairedHere(),
                personStopped = store.nodeStopped(),
            )
        }
    }

    private fun fail(problem: PhoneNodeProblem, detail: String?) {
        mutable.update { it.copy(state = PhoneNodeState.Failed, problem = problem, failure = detail, pairedHere = pairedHere()) }
    }

    private suspend fun <T> busy(body: suspend () -> T): T? {
        if (!lane.tryLock()) return null
        mutable.update { it.copy(busy = true) }
        try {
            return body()
        } finally {
            mutable.update { it.copy(busy = false, pairedHere = pairedHere()) }
            lane.unlock()
        }
    }

    /** Reads how things are now. */
    public suspend fun refresh() {
        busy { refreshLocked(running = true) }
    }

    private suspend fun refreshLocked(running: Boolean) {
        if (!host.supported) return set(PhoneNodeState.Unsupported)
        val facts =
            when (val f = host.inspect()) {
                is NodeStep.Failed -> return fail(PhoneNodeProblem.Start, f.detail ?: f.failure.name)
                is NodeStep.Done -> f.value
            }
        mutable.update { it.copy(facts = facts) }
        set(
            when {
                !facts.linuxReady -> PhoneNodeState.LinuxNotSetUp
                running && facts.serviceRunning -> PhoneNodeState.Running
                facts.dataExists -> PhoneNodeState.Stopped
                facts.binaryAvailable -> PhoneNodeState.NotInstalled
                else -> PhoneNodeState.NoBuild
            }
        )
    }

    /** "Set up memory on this phone": install, make the vault, start and pair. Finished steps are skipped. */
    public suspend fun runHere() {
        busy {
            store.setNodeStopped(false)
            set(PhoneNodeState.Installing, PhoneNodeStep.Copying)
            val facts = (host.inspect() as? NodeStep.Done)?.value ?: NodeHostFacts()
            if (!facts.linuxReady) return@busy set(PhoneNodeState.LinuxNotSetUp)
            if (!facts.binaryInstalled) {
                when (val r = host.installBinary()) {
                    is NodeStep.Failed ->
                        return@busy if (r.failure == NodeHostFailure.NoBinary) set(PhoneNodeState.NoBuild)
                        else fail(PhoneNodeProblem.Install, r.detail ?: r.failure.name)
                    is NodeStep.Done -> Unit
                }
            }
            set(PhoneNodeState.Installing, PhoneNodeStep.Preparing)
            (host.createVault() as? NodeStep.Failed)?.let { return@busy fail(PhoneNodeProblem.Install, it.detail ?: it.failure.name) }
            if (startLocked()) pairLocked()
        }
    }

    /** Starts a stopped node and pairs again if the pairing was lost. */
    public suspend fun start() {
        busy {
            store.setNodeStopped(false)
            if (startLocked()) pairLocked()
        }
    }

    public suspend fun stop() {
        busy {
            set(PhoneNodeState.Stopping)
            store.setNodeStopped(true)
            when (val r = host.stopNode()) {
                is NodeStep.Failed -> fail(PhoneNodeProblem.Stop, r.detail ?: r.failure.name)
                is NodeStep.Done -> set(PhoneNodeState.Stopped)
            }
        }
    }

    public suspend fun restart() {
        busy {
            store.setNodeStopped(false)
            set(PhoneNodeState.Stopping)
            (host.stopNode() as? NodeStep.Failed)?.let { return@busy fail(PhoneNodeProblem.Stop, it.detail ?: it.failure.name) }
            if (startLocked()) pairLocked()
        }
    }

    /** "Remove memory from this phone": stops the node, deletes its data and forgets the pairing. The caller has asked. */
    public suspend fun remove() {
        busy {
            set(PhoneNodeState.Removing)
            when (val r = host.removeData()) {
                is NodeStep.Failed -> fail(PhoneNodeProblem.Remove, r.detail ?: r.failure.name)
                is NodeStep.Done -> {
                    if (pairedHere()) engine.disconnect()
                    refreshLocked(running = false)
                }
            }
        }
    }

    public suspend fun log(): String = host.log()

    /** Starts the node and waits until it answers. False after a failure. */
    private suspend fun startLocked(): Boolean {
        set(PhoneNodeState.Installing, PhoneNodeStep.Starting)
        val running = (host.inspect() as? NodeStep.Done)?.value?.serviceRunning == true
        if (!running) (host.startNode() as? NodeStep.Failed)?.let { fail(PhoneNodeProblem.Start, it.detail ?: it.failure.name); return false }
        repeat(probeAttempts) {
            if (pairingGateway(baseUrl).info() is FbResult.Ok) return true
            delay(probeDelay)
        }
        val log = host.log().trim()
        fail(PhoneNodeProblem.Start, log.ifEmpty { "The node did not answer in time." })
        return false
    }

    private suspend fun pairLocked() {
        if (pairedHere()) return set(PhoneNodeState.Running)
        set(PhoneNodeState.Installing, PhoneNodeStep.Pairing)
        val key = engine.ownerPublicKeyHex() ?: return fail(PhoneNodeProblem.Pair, "No approval key.")
        val pairing =
            when (val r = host.pairingCode(key)) {
                is NodeStep.Failed -> return fail(PhoneNodeProblem.Pair, r.detail ?: r.failure.name)
                is NodeStep.Done -> r.value
            }
        when (val paired = engine.pair(host.address, pairing.code, pairing.ownerDevice, pairingGateway)) {
            is FbResult.Err -> return fail(PhoneNodeProblem.Pair, "The node refused the pairing (${paired.error.code}).")
            is FbResult.Ok -> set(PhoneNodeState.Running)
        }
        adoptPhoneZone()
    }

    /** A new node keeps reminders in UTC; right after pairing its home zone becomes this phone's. Quiet if it fails. */
    private suspend fun adoptPhoneZone() {
        val node = engine.node ?: return
        val zone = phoneZone() ?: return
        if (!engine.state.value.canApprove) return
        val settings = (node.settings() as? FbResult.Ok)?.value ?: return
        if (settings.vaultZone == zone || settings.revision != 1L) return
        node.setHomeZone(settings, zone)
    }

    /**
     * When the app opens: a node set up on this phone, paired with this app and stopped is started, unless the person
     * stopped it. The built-in Linux is often still starting then, so a paired node waits for it.
     */
    public suspend fun ensureRunning() {
        if (!host.supported) return
        busy {
            refreshLocked(running = true)
            var waits = 0
            while (waits < linuxWaitAttempts && pairedHere() && !store.nodeStopped() &&
                (status.value.state == PhoneNodeState.LinuxNotSetUp || status.value.state == PhoneNodeState.Failed)
            ) {
                delay(linuxWaitDelay)
                refreshLocked(running = true)
                waits++
            }
            if (status.value.state != PhoneNodeState.Stopped || !pairedHere() || store.nodeStopped()) return@busy
            var tries = 0
            while (!(startLocked().also { ok -> if (ok) pairLocked() }) && tries < START_RETRIES && !store.nodeStopped()) {
                delay(linuxWaitDelay)
                tries++
            }
        }
    }

    public companion object {
        /** Further whole start attempts [ensureRunning] makes after a failure. */
        public const val START_RETRIES: Int = 3
    }
}
