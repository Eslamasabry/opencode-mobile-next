package io.github.eslamasabry.oc.engine.aisetup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the setup owner is bound to: profile, location (directory and workspace) and the live transport. Two scopes
 * are the same only when all four match and the transport is the very same object.
 */
public class SetupScope(
    public val profileId: String,
    public val directory: String?,
    public val workspace: String?,
    public val transport: Any,
) {
    public fun sameAs(other: SetupScope?): Boolean =
        other != null &&
            profileId == other.profileId &&
            directory == other.directory &&
            workspace == other.workspace &&
            transport === other.transport
}

/**
 * The connection as the setup page sees it. [scope] is null while there is no live transport; [setupConfigRead] is
 * the selected server's `ServerCapabilities.setupConfigRead` (so a server that can share its setup once connected
 * shows offline, one that never can shows unavailable).
 */
public data class SetupConnection(val scope: SetupScope?, val online: Boolean, val setupConfigRead: Boolean)

/**
 * Composition owner of the review-only AI setup page (Dart `SetupSession`). It builds the [SetupController] outside
 * the UI for the connection's current profile, location and transport, and recreates it (after the old one is
 * disposed) whenever any of them changes, including an away-and-back switch. Offline keeps the last safe read;
 * reconnecting reads again. Review only: it exposes [refresh] and nothing that proposes, applies or undoes.
 *
 * [gatewayFor] answers null when the server has no verified setup adapter (it gates on the capability, never on the
 * backend kind). [controllerFor] wires the profile's audit log and the redactor. Everything runs in [scope].
 */
public class SetupSession(
    private val connection: StateFlow<SetupConnection>,
    private val gatewayFor: (SetupScope) -> SetupConfigGateway?,
    private val controllerFor: (SetupScope, SetupConfigGateway, isCurrent: () -> Boolean) -> SetupController,
    private val scope: CoroutineScope,
) : AiSetupReviewActions {
    private var controller: SetupController? = null
    private var observer: Job? = null
    private var snapshot = SetupSnapshot(phase = SetupPhase.Loading)
    private var key: Any? = Unset
    private var supported = false
    private var online = false
    private var disposed = false
    private var generation = 0
    private var lifecycle: Job = Job().apply { complete() }
    private val _state = MutableStateFlow(aiSetupReviewState(snapshot, online))

    /** What the review page renders. */
    public val state: StateFlow<AiSetupReviewState> = _state.asStateFlow()

    /** The latest redacted snapshot; `Loading` until the first read answers. */
    public val currentSnapshot: SetupSnapshot get() = snapshot

    /** False when the server has no verified setup adapter: the page shows unavailable and reads nothing. */
    public val isSupported: Boolean get() = supported

    public val isOnline: Boolean get() = online

    private val collector: Job = scope.launch { connection.collect { sync(it) } }

    override fun refresh() {
        scope.launch { read() }
    }

    /** Reads the server's setup again. No-op offline, without an owner, or while a read runs. */
    public suspend fun read() {
        val current = controller ?: return
        if (!online) return
        current.refresh()
        // Publish the settled snapshot now so a finished read never shows as still loading.
        if (!disposed && current === controller) {
            snapshot = current.snapshot
            publish()
        }
    }

    /** A deletion or reset is about to touch this profile's data: release the owner now, then rebuild. */
    public fun profileDataChanged() {
        if (disposed) return
        key = Released
        sync(connection.value)
    }

    /** Completes once every earlier owner is disposed and the current one finished its first read. */
    public suspend fun settled() {
        while (true) {
            val job = lifecycle
            job.join()
            if (job === lifecycle) return
        }
    }

    private fun sync(value: SetupConnection) {
        if (disposed) return
        if (!sameKey(value.scope, key)) {
            key = value.scope
            rebuild(value)
            return
        }
        if (value.online == online) return
        online = value.online
        val current = controller ?: return
        current.setOnline(online)
        snapshot = current.snapshot
        publish()
        if (online) refresh()
    }

    private fun rebuild(value: SetupConnection) {
        val gen = ++generation
        val previous = controller
        observer?.cancel()
        observer = null
        controller = null
        online = value.online
        val bound = value.scope
        val gateway = bound?.let(gatewayFor)
        // No live transport yet: offline when the server can share its setup once connected, else unavailable.
        val waiting = bound == null && value.setupConfigRead
        supported = gateway != null || waiting
        snapshot = SetupSnapshot(
            phase = when {
                waiting -> SetupPhase.Offline
                gateway == null -> SetupPhase.Unsupported
                else -> SetupPhase.Loading
            },
        )
        publish()
        enqueue {
            previous?.dispose()
            if (disposed || gen != generation || gateway == null || bound == null) return@enqueue
            val created = controllerFor(bound, gateway) {
                !disposed && gen == generation && connection.value.scope?.sameAs(bound) == true
            }
            controller = created
            observer = scope.launch {
                created.state.collect {
                    if (!disposed && gen == generation && it.phase != SetupPhase.Idle) {
                        snapshot = it
                        publish()
                    }
                }
            }
            if (!online) {
                created.setOnline(false)
                snapshot = created.snapshot
                publish()
                return@enqueue
            }
            read()
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        val previous = lifecycle
        lifecycle = scope.launch {
            previous.join()
            block()
        }
    }

    private fun publish() {
        _state.value = aiSetupReviewState(snapshot, online)
    }

    public fun dispose() {
        disposed = true
        generation++
        collector.cancel()
        observer?.cancel()
        observer = null
        val previous = controller
        controller = null
        enqueue { previous?.dispose() }
    }

    private fun sameKey(a: SetupScope?, b: Any?): Boolean =
        when {
            b is SetupScope -> a?.sameAs(b) == true
            else -> a == null && b == null
        }

    private object Unset

    private object Released
}
