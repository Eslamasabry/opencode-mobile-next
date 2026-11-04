package io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalTranscript
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ContributesTo(ConnectionScope::class)
public interface RemoteTerminalAccessors {
    public val remoteTerminalOperations: ServerOperationsGateway
}

/**
 * The view owns only its socket. Leaving disconnects it without ending the remote process. Input is
 * never queued or replayed; reconnecting is an explicit action and obtains a new one-use ticket
 * through the gateway.
 */
@Inject
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
public class RemoteTerminalViewModel(
    private val registry: ConnectionRegistry,
    private val navigator: AppNavigator,
    private val textPolicies: Set<RemoteTerminalTextPolicy>,
) : ViewModel(), RemoteTerminalActions {
    private val mutable = MutableStateFlow(RemoteTerminalUi())
    public val state: StateFlow<RemoteTerminalUi> = mutable.asStateFlow()
    private val commands = Mutex()
    private var profile: ProfileId? = null
    private var graph: ConnectionGraph? = null
    private var location: ServerLocation? = null
    private var generation = 0L
    private var watcher: Job? = null
    private var output: Job? = null
    private var channel: TerminalChannel? = null
    private val cursors = LinkedHashMap<String, Long>()
    private val transcript = TerminalTranscript()

    public fun start(target: ProfileId) {
        if (profile == target && watcher?.isActive == true) return
        detach()
        if (profile != target) {
            cursors.clear()
            transcript.reset()
        }
        profile = target
        watcher =
            viewModelScope.launch(registry.mainContext) {
                registry.live
                    .map { it[target] }
                    .distinctUntilChanged()
                    .collectLatest { expected ->
                        release()
                        cursors.clear()
                        transcript.reset()
                        graph = expected
                        mutable.value = RemoteTerminalUi()
                        if (expected == null) return@collectLatest
                        combine(expected.session.server.location, expected.session.capabilities) {
                                place,
                                caps ->
                                place to caps.terminal
                            }
                            .collectLatest { (place, available) ->
                                generation++
                                release()
                                if (location != place) {
                                    cursors.clear()
                                    transcript.reset()
                                }
                                location = place
                                mutable.value = RemoteTerminalUi(available = available)
                                if (available) load(expected, place, generation)
                            }
                    }
            }
    }

    private fun current(expected: ConnectionGraph, place: ServerLocation, stamp: Long): Boolean =
        generation == stamp &&
            graph === expected &&
            registry.live.value[expected.profile.id] === expected &&
            location == place &&
            expected.session.server.location.value == place &&
            expected.session.capabilities.value.terminal

    private fun operations(expected: ConnectionGraph): ServerOperationsGateway? =
        (expected as? RemoteTerminalAccessors)?.remoteTerminalOperations

    private suspend fun load(expected: ConnectionGraph, place: ServerLocation, stamp: Long) {
        val gateway = operations(expected) ?: return
        if (!current(expected, place, stamp)) return
        mutable.value = mutable.value.copy(busy = true, issue = null)
        val result = gateway.listTerminals()
        if (!current(expected, place, stamp)) return
        mutable.value =
            when (result) {
                is Outcome.Failed ->
                    mutable.value.copy(busy = false, issue = RemoteTerminalIssue.Failed)
                is Outcome.Ok ->
                    mutable.value.copy(busy = false, terminals = result.value.take(100).map(::item))
            }
    }

    private fun item(value: TerminalProcess): RemoteTerminalItem =
        RemoteTerminalItem(value.id, value.title.take(200), value.running)

    override fun refresh() = command { expected, place, stamp, _ -> load(expected, place, stamp) }

    override fun create() = command { expected, place, stamp, gateway ->
        val result = gateway.createTerminal()
        if (!current(expected, place, stamp)) return@command
        if (result is Outcome.Ok) {
            val next = item(result.value)
            mutable.value =
                mutable.value.copy(
                    terminals =
                        (mutable.value.terminals.filterNot { it.id == next.id } + next).takeLast(
                            100
                        )
                )
            open(expected, place, stamp, gateway, next)
        } else mutable.value = mutable.value.copy(issue = RemoteTerminalIssue.Failed)
    }

    override fun connect(id: String) = command { expected, place, stamp, gateway ->
        val selected = mutable.value.terminals.firstOrNull { it.id == id } ?: return@command
        open(expected, place, stamp, gateway, selected)
    }

    private suspend fun open(
        expected: ConnectionGraph,
        place: ServerLocation,
        stamp: Long,
        gateway: ServerOperationsGateway,
        selected: RemoteTerminalItem,
    ) {
        release()
        if (mutable.value.selected?.id != selected.id) transcript.reset()
        mutable.value =
            mutable.value.copy(
                selected = selected,
                connected = false,
                transcript = shownTranscript(),
                issue = null,
            )
        val opened = gateway.connectTerminal(selected.id, cursors[selected.id])
        if (!current(expected, place, stamp)) {
            if (opened is Outcome.Ok) opened.value.close()
            return
        }
        if (opened is Outcome.Failed) {
            mutable.value = mutable.value.copy(issue = RemoteTerminalIssue.Failed)
            return
        }
        val next = (opened as Outcome.Ok).value
        channel = next
        mutable.value = mutable.value.copy(connected = true)
        output =
            viewModelScope.launch(registry.mainContext) {
                val read =
                    attempt({ Failure.Network() }) {
                        next.output.collect { chunk ->
                            if (!current(expected, place, stamp) || channel !== next) return@collect
                            transcript.add(chunk)
                            mutable.value = mutable.value.copy(transcript = shownTranscript())
                            rememberCursor(selected.id, next.cursor)
                        }
                    }
                if (current(expected, place, stamp) && channel === next) {
                    rememberCursor(selected.id, next.cursor)
                    channel = null
                    mutable.value =
                        mutable.value.copy(
                            connected = false,
                            issue =
                                if (read is Outcome.Failed) RemoteTerminalIssue.Failed else null,
                        )
                }
                attempt({ Failure.Network() }) { next.close() }
            }
    }

    override fun send(value: String) {
        if (
            value.isEmpty() ||
                value.length > 65_536 ||
                !mutable.value.connected ||
                mutable.value.busy
        )
            return
        command { expected, place, stamp, _ ->
            val currentChannel = channel ?: return@command
            val sent = attempt({ Failure.Network() }) { currentChannel.write(value) }
            if (!current(expected, place, stamp) || channel !== currentChannel) return@command
            if (sent is Outcome.Failed) {
                release()
                mutable.value =
                    mutable.value.copy(
                        connected = false,
                        issue = RemoteTerminalIssue.InputUncertain,
                    )
            }
        }
    }

    override fun interrupt(): Unit = send("\u0003")

    override fun endOfInput(): Unit = send("\u0004")

    override fun escape(): Unit = send("\u001b")

    override fun tab(): Unit = send("\t")

    override fun disconnect() {
        release()
        mutable.value = mutable.value.copy(connected = false, busy = false, ending = false)
    }

    override fun showList() {
        disconnect()
        mutable.value = mutable.value.copy(selected = null, transcript = "", issue = null)
        refresh()
    }

    override fun askEnd() {
        if (mutable.value.selected != null) mutable.value = mutable.value.copy(ending = true)
    }

    override fun cancelEnd() {
        mutable.value = mutable.value.copy(ending = false)
    }

    override fun confirmEnd() = command { expected, place, stamp, gateway ->
        val selected = mutable.value.selected ?: return@command
        mutable.value = mutable.value.copy(ending = false)
        release()
        val result = gateway.removeTerminal(selected.id)
        if (!current(expected, place, stamp)) return@command
        if (result is Outcome.Ok) {
            cursors.remove(selected.id)
            transcript.reset()
            mutable.value =
                mutable.value.copy(
                    selected = null,
                    connected = false,
                    transcript = "",
                    issue = null,
                )
            load(expected, place, stamp)
        } else
            mutable.value =
                mutable.value.copy(connected = false, issue = RemoteTerminalIssue.Failed)
    }

    private fun command(
        block: suspend (ConnectionGraph, ServerLocation, Long, ServerOperationsGateway) -> Unit
    ) {
        if (mutable.value.busy) return
        val expected = graph ?: return
        val place = location ?: return
        val stamp = generation
        val gateway = operations(expected) ?: return
        if (!current(expected, place, stamp)) return
        mutable.value = mutable.value.copy(busy = true, issue = null)
        viewModelScope.launch(registry.mainContext) {
            commands.withLock {
                if (current(expected, place, stamp)) block(expected, place, stamp, gateway)
                if (current(expected, place, stamp))
                    mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    private fun shownTranscript(): String =
        textPolicies.fold(transcript.text) { text, policy -> policy.redact(text) }

    private fun rememberCursor(id: String, value: Long?) {
        if (value == null || value < 0) return
        cursors.remove(id)
        cursors[id] = value
        while (cursors.size > 32) cursors.remove(cursors.keys.first())
    }

    private fun release() {
        val old = channel
        channel = null
        mutable.value.selected?.id?.let { rememberCursor(it, old?.cursor) }
        output?.cancel()
        output = null
        if (old != null)
            registry.appScope.launch(registry.mainContext) {
                attempt({ Failure.Network() }) { old.close() }
            }
    }

    public fun detach() {
        generation++
        watcher?.cancel()
        watcher = null
        release()
        graph = null
        location = null
        mutable.value = RemoteTerminalUi()
    }

    override fun back() {
        detach()
        navigator.back()
    }

    override fun onCleared() {
        detach()
    }
}
