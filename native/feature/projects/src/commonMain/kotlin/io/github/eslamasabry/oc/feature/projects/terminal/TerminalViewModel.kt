package io.github.eslamasabry.oc.feature.projects.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalTranscript
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A screen-owned subscription to server-owned terminals. Leaving disconnects; it never kills a
 * shell.
 */
@AssistedInject
public class TerminalViewModel(
    @Assisted private val route: TerminalRoute.Home,
    private val servers: ProjectServers,
    private val clipboard: TerminalClipboard,
    grids: TerminalGrids,
    private val privacy: TerminalPrivacy,
    teaching: TerminalTeaching,
    private val nav: AppNavigator,
) : ViewModel(), TerminalActions {
    private val mutable = MutableStateFlow(TerminalUi(route.directory))
    public val state: StateFlow<TerminalUi> = mutable.asStateFlow()
    private var adopted = false
    private val tip = teaching.visit()
    private var server: ProjectServer? = null
    private var transport: TerminalChannel? = null
    private var output: Job? = null
    private var locationWatch: Job? = null
    private var eventWatch: Job? = null
    private var generation = 0
    private var loadGeneration = 0
    private var visible = false
    private var startedOne = false
    private var cursor: Long? = null
    private var transcript = TerminalTranscript()
    private val grid = grids.create(::send, ::resize)
    private val input = Channel<Pair<Int, String>>(Channel.UNLIMITED)

    init {
        mutable.update { it.copy(grid = grid?.view) }
        viewModelScope.launch {
            tip.visible.collect { on -> mutable.update { it.copy(teaching = on) } }
        }
        viewModelScope.launch {
            for ((version, first) in input) {
                val channel = transport
                if (
                    version != generation ||
                        !visible ||
                        channel == null ||
                        mutable.value.phase != TerminalPhase.Connected
                )
                    continue
                val result = attempt({ Failure.Network() }) { channel.write(first) }
                if (version == generation && result is Outcome.Failed) failed(result.failure)
            }
        }
        viewModelScope.launch {
            servers.servers.collect { live ->
                val next = live.firstOrNull { it.profile == route.profile }
                if (!adopted || next?.gateway !== server?.gateway) {
                    adopted = true
                    adopt(next)
                }
            }
        }
    }

    private fun adopt(next: ProjectServer?) {
        detach()
        locationWatch?.cancel()
        eventWatch?.cancel()
        server = next
        cursor = null
        transcript.reset()
        grid?.reset()
        mutable.update {
            it.copy(
                selected = null,
                processes = persistentListOf(),
                transcript = "",
                details = null,
                working = false,
            )
        }
        loadGeneration++
        if (next == null) {
            mutable.update { it.copy(phase = TerminalPhase.Unavailable) }
            return
        }
        mutable.update { it.copy(serverName = next.name, failure = null) }
        // The explicit project-terminal navigation changes the active project once. Subsequent
        // changes invalidate it.
        val at = next.gateway.location.value
        if (at.directory != route.directory) {
            val target = ServerLocation(directory = route.directory)
            next.gateway.setLocation(target)
            next.operations?.setLocation(target)
        }
        locationWatch = viewModelScope.launch {
            var admitted = at.directory == route.directory
            next.gateway.location.collect { location ->
                if (location.directory == route.directory) {
                    val first = !admitted
                    admitted = true
                    if (first && visible) refresh()
                } else if (admitted) {
                    detach()
                    mutable.update { it.copy(phase = TerminalPhase.Stale) }
                }
            }
        }
        if (at.directory != route.directory)
            viewModelScope.launch {
                val arrived =
                    withTimeoutOrNull(10_000) {
                        next.gateway.location.first { it.directory == route.directory }
                    }
                if (arrived == null && server?.gateway === next.gateway) failed(Failure.TimedOut())
            }
        eventWatch = viewModelScope.launch {
            next.gateway.events.project.collect { envelope ->
                if (
                    (envelope.event is GatewayEvent.TerminalChanged ||
                        envelope.event == GatewayEvent.Connected) && visible && current(next)
                )
                    refresh()
            }
        }
        if (visible) refresh()
    }

    private fun current(value: ProjectServer): Boolean =
        server?.gateway === value.gateway &&
            value.gateway.location.value.directory == route.directory

    override fun back(): Unit = nav.back()

    override fun shown(on: Boolean) {
        if (visible == on) return
        visible = on
        if (on) refresh()
        else {
            detach()
            mutable.update { it.copy(phase = TerminalPhase.Paused) }
        }
    }

    override fun refresh() {
        val live = server ?: return
        if (!current(live)) return
        if (!live.gateway.capabilities.value.terminal || live.operations == null) {
            mutable.update { it.copy(phase = TerminalPhase.Unavailable) }
            return
        }
        val read = ++loadGeneration
        viewModelScope.launch {
            val result = live.operations.listTerminals()
            if (!current(live) || read != loadGeneration || !visible) return@launch
            when (result) {
                is Outcome.Failed -> failed(result.failure)
                is Outcome.Ok -> {
                    val rows = result.value.sortedByDescending { it.running }.toImmutableList()
                    val selected =
                        rows.firstOrNull { it.id == mutable.value.selected }
                            ?: rows.firstOrNull { it.running }
                            ?: rows.firstOrNull()
                    if (selected?.id != mutable.value.selected) {
                        detach()
                        cursor = null
                        transcript.reset()
                        grid?.reset()
                        mutable.update {
                            it.copy(transcript = "", details = null, selecting = false)
                        }
                    }
                    mutable.update {
                        it.copy(
                            processes = rows,
                            failure = null,
                            selected = selected?.id,
                            phase = if (transport != null) it.phase else TerminalPhase.Ready,
                        )
                    }
                    if (!startedOne && rows.none { it.running }) {
                        startedOne = true
                        create()
                    } else if (selected != null && transport == null) {
                        if (selected.running) startedOne = true
                        connect(selected.id)
                    }
                }
            }
        }
    }

    override fun create() {
        val live = server ?: return
        if (!current(live) || mutable.value.working || !live.gateway.capabilities.value.terminal)
            return
        val operations = live.operations ?: return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = operations.createTerminal()
            if (!current(live)) return@launch
            mutable.update { it.copy(working = false) }
            when (result) {
                is Outcome.Failed -> failed(result.failure)
                is Outcome.Ok -> {
                    startedOne = true
                    val row = result.value
                    mutable.update {
                        it.copy(
                            processes =
                                (it.processes.filterNot { p -> p.id == row.id } + row)
                                    .toImmutableList()
                        )
                    }
                    select(row.id)
                }
            }
        }
    }

    override fun select(id: String) {
        if (mutable.value.processes.none { it.id == id }) return
        if (mutable.value.selected != id) {
            detach()
            cursor = null
            transcript = TerminalTranscript()
            grid?.reset()
            mutable.update { it.copy(selected = id, transcript = "", failure = null) }
        }
        connect(id)
    }

    private fun connect(id: String) {
        val live = server ?: return
        val operations = live.operations ?: return
        if (!visible || !current(live) || !live.gateway.capabilities.value.terminal) return
        detach()
        val version = generation
        mutable.update { it.copy(selected = id, phase = TerminalPhase.Connecting, failure = null) }
        output = viewModelScope.launch {
            when (val result = operations.connectTerminal(id, cursor)) {
                is Outcome.Failed -> if (version == generation) failed(result.failure)
                is Outcome.Ok -> {
                    val channel = result.value
                    if (version != generation || !visible || !current(live)) {
                        attempt({ Failure.Network() }) { channel.close() }
                        return@launch
                    }
                    transport = channel
                    grid?.inputEnabled(true)
                    grid?.publishSize()
                    mutable.update { it.copy(phase = TerminalPhase.Connected) }
                    tip.offer {
                        !visible ||
                            mutable.value.rename != null ||
                            mutable.value.removing.isNotEmpty() ||
                            mutable.value.details != null
                    }
                    try {
                        val read =
                            attempt({ Failure.Network() }) {
                                channel.output.collect { chunk ->
                                    if (version == generation && current(live)) {
                                        transcript.add(chunk)
                                        grid?.append(chunk)
                                        cursor = channel.cursor ?: cursor
                                        mutable.update { it.copy(transcript = transcript.text) }
                                    }
                                }
                            }
                        if (version == generation) {
                            cursor = channel.cursor ?: cursor
                            transport = null
                            grid?.inputEnabled(false)
                            mutable.update {
                                it.copy(
                                    phase = TerminalPhase.Closed,
                                    failure = (read as? Outcome.Failed)?.failure,
                                )
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            withTimeoutOrNull(2_000) {
                                attempt({ Failure.Network() }) { channel.close() }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun detach() {
        generation++
        val channel = transport
        cursor = channel?.cursor ?: cursor
        transport = null
        grid?.inputEnabled(false)
        output?.cancel()
        output = null
        if (channel != null)
            viewModelScope.launch { attempt({ Failure.Network() }) { channel.close() } }
    }

    override fun reconnect() {
        mutable.value.selected?.let(::connect)
    }

    override fun send(value: String) {
        if (value.isEmpty() || mutable.value.phase != TerminalPhase.Connected || !visible) return
        val current = mutable.value
        val text = modified(value, current.control, current.alt)
        mutable.update { it.copy(control = false, alt = false) }
        input.trySend(generation to text)
    }

    override fun key(key: String) {
        if (mutable.value.phase != TerminalPhase.Connected) return
        if (key == "ctrl") {
            mutable.update { it.copy(control = !it.control) }
            return
        }
        if (key == "alt") {
            mutable.update { it.copy(alt = !it.alt) }
            return
        }
        val state = mutable.value
        mutable.update { it.copy(control = false, alt = false) }
        if (grid != null) grid.key(key, state.control, state.alt)
        else {
            val bytes =
                when (key) {
                    "escape" -> "\u001b"
                    "tab" -> "\t"
                    "up" -> "\u001b[A"
                    "down" -> "\u001b[B"
                    "left" -> "\u001b[D"
                    "right" -> "\u001b[C"
                    "home" -> "\u001b[H"
                    "end" -> "\u001b[F"
                    "pageUp" -> "\u001b[5~"
                    "pageDown" -> "\u001b[6~"
                    "interrupt" -> "\u0003"
                    "eof" -> "\u0004"
                    else -> key
                }
            send(modified(bytes, state.control, state.alt))
        }
    }

    private fun modified(text: String, ctrl: Boolean, alt: Boolean): String {
        if (text.isEmpty() || (!ctrl && !alt)) return text
        val first = text[0].code
        val control =
            when {
                !ctrl -> first
                first in 0x61..0x7a -> first - 0x60
                first in 0x40..0x5f -> first - 0x40
                first == 0x20 -> 0
                first == 0x3f -> 0x7f
                else -> first
            }
        return (if (alt) "\u001b" else "") + control.toChar() + text.substring(1)
    }

    override fun dismissTip(): Unit = tip.dismiss()

    override fun askRename() {
        tip.dismiss()
        mutable.value.selected?.let { id -> mutable.update { it.copy(rename = id) } }
    }

    override fun rename(title: String?) {
        val id = mutable.value.rename ?: return
        val live = server ?: return
        mutable.update { it.copy(rename = null) }
        if (title.isNullOrBlank() || !current(live)) return
        viewModelScope.launch {
            when (val result = live.operations?.renameTerminal(id, title.trim())) {
                is Outcome.Failed -> if (current(live)) failed(result.failure)
                is Outcome.Ok -> if (current(live)) refresh()
                null -> Unit
            }
        }
    }

    override fun askRemove() {
        tip.dismiss()
        val row = mutable.value.processes.firstOrNull { it.id == mutable.value.selected } ?: return
        mutable.update { it.copy(removing = persistentListOf(row)) }
    }

    override fun askRemoveEnded() {
        mutable.update {
            it.copy(removing = it.processes.filterNot { p -> p.running }.toImmutableList())
        }
    }

    override fun remove() {
        val rows = mutable.value.removing
        val live = server ?: return
        val operations = live.operations ?: return
        if (rows.isEmpty() || !current(live) || mutable.value.working) return
        mutable.update { it.copy(working = true) }
        viewModelScope.launch {
            // Re-read before the destructive batch: ended rows that restarted are kept.
            val listed = operations.listTerminals()
            if (!current(live)) return@launch
            if (listed is Outcome.Failed) {
                failed(listed.failure)
                mutable.update { it.copy(working = false) }
                return@launch
            }
            val fresh = (listed as Outcome.Ok).value
            for (row in rows) {
                if (!current(live)) break
                val now = fresh.firstOrNull { it.id == row.id } ?: continue
                if (!row.running && now.running) continue
                when (val result = operations.removeTerminal(row.id)) {
                    is Outcome.Failed -> {
                        failed(result.failure)
                        mutable.update { it.copy(working = false) }
                        return@launch
                    }
                    is Outcome.Ok ->
                        if (row.id == mutable.value.selected) {
                            detach()
                            mutable.update { it.copy(selected = null) }
                        }
                }
            }
            mutable.update { it.copy(working = false, removing = persistentListOf()) }
            refresh()
        }
    }

    override fun dismiss(): Unit = mutable.update {
        it.copy(
            rename = null,
            removing = persistentListOf(),
            details = null,
            clipboardFailed = false,
        )
    }

    override fun details() {
        val process =
            mutable.value.processes.firstOrNull { it.id == mutable.value.selected } ?: return
        tip.dismiss()
        mutable.update { it.copy(details = privacy.describe(process)) }
    }

    override fun copy() {
        mutable.update {
            it.copy(
                clipboardFailed =
                    !clipboard.copy(
                        grid?.selectedText()?.takeIf { text -> text.isNotEmpty() } ?: it.transcript
                    )
            )
        }
    }

    override fun paste() {
        clipboard
            .read()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (grid != null) grid.paste(it) else send(it) }
    }

    override fun accessibleMode(on: Boolean): Unit = mutable.update { it.copy(accessibleMode = on) }

    override fun selectText() {
        tip.used()
        val on = !mutable.value.selecting
        grid?.selectText(on)
        mutable.update { it.copy(selecting = on) }
    }

    private fun resize(rows: Int, cols: Int) {
        val live = server ?: return
        val id = mutable.value.selected ?: return
        val version = generation
        viewModelScope.launch {
            if (version == generation && current(live))
                live.operations?.resizeTerminal(id, rows, cols)
        }
    }

    private fun failed(failure: Failure) {
        mutable.update {
            it.copy(
                failure = failure,
                phase = if (transport == null) TerminalPhase.Failed else it.phase,
            )
        }
    }

    override fun onCleared() {
        tip.dismiss()
        input.close()
        // ViewModel scope is already cancelled: channel.close must not depend on that scope.
        output?.cancel()
        locationWatch?.cancel()
        eventWatch?.cancel()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TerminalRoute.Home): TerminalViewModel
    }
}
