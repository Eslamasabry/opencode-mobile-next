package io.github.eslamasabry.oc.feature.agenttools.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.LocatedProjectGateway
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderSession
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderer
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
public interface RemoteTerminalBindings {
    @Multibinds(allowEmpty = true) public val remoteTerminalRenderers: Set<RemoteTerminalRenderer>
}

public data class AgentTerminalUi(
    val supported: Boolean = false,
    val listed: Boolean = false,
    val busy: Boolean = false,
    val failure: Failure? = null,
    val terminals: ImmutableList<TerminalProcess> = persistentListOf(),
    val askingCreate: Boolean = false,
    val rename: TerminalProcess? = null,
    val close: TerminalProcess? = null,
    val liveId: String? = null,
    val title: String = "",
    val view: PlatformViewHandle? = null,
    val transcript: String = "",
    val inputEnabled: Boolean = false,
    val accessibleMode: Boolean? = null,
    val uncertain: Boolean = false,
)

public interface AgentTerminalActions {
    public fun listTerminals()

    public fun askNewTerminal()

    public fun finishTerminalName(name: String?)

    public fun askRenameTerminal(id: String)

    public fun finishTerminalRename(name: String?)

    public fun askCloseTerminal(id: String)

    public fun cancelCloseTerminal()

    public fun confirmCloseTerminal()

    public fun openTerminal(id: String)

    public fun leaveTerminal()

    public fun sendTerminal(value: String)

    public fun terminalAccessible(on: Boolean)
}

/** A real subscribed remote terminal rendered by the platform's retained terminal emulator. */
internal class AgentTerminal(
    private val profile: ProfileId,
    private val host: AgentToolsHost,
    private val renderers: Set<RemoteTerminalRenderer>,
    private val scope: CoroutineScope,
    private val publish: (AgentTerminalUi) -> Unit,
) : AgentTerminalActions {
    private var state = AgentTerminalUi()
    private var owner: AgentToolsSession? = null
    private var generation = 0
    private var streamGeneration = 0
    private var channel: TerminalChannel? = null
    private var rendering: RemoteTerminalRenderSession? = null
    private var output: Job? = null
    private var transcript: Job? = null

    private fun set(next: AgentTerminalUi) {
        state = next
        publish(next)
    }

    fun adopt(source: AgentToolsSession?) {
        if (
            source?.gateway === owner?.gateway &&
                owner?.let { host.isCurrent(profile, it.identity) } == true
        )
            return
        closeView()
        generation++
        owner = source
        set(
            AgentTerminalUi(
                supported =
                    source?.gateway?.capabilities?.value?.terminal == true &&
                        source?.gateway is LocatedProjectGateway &&
                        renderers.size == 1
            )
        )
    }

    private fun live(): AgentToolsSession? {
        val source = owner ?: return null
        if (!host.isCurrent(profile, source.identity)) {
            closeView()
            set(
                state.copy(
                    supported = false,
                    busy = false,
                    failure = Failure.Refused(RefusalReason.StaleScope),
                )
            )
            return null
        }
        return source
    }

    private fun ops(source: AgentToolsSession): ServerOperationsGateway? =
        (source.gateway as? LocatedProjectGateway)?.operationsAt(source.gateway.location.value)

    private fun valid(source: AgentToolsSession, run: Int): Boolean =
        run == generation &&
            owner?.gateway === source.gateway &&
            host.isCurrent(profile, source.identity)

    private fun failed(failure: Failure) =
        set(
            state.copy(
                busy = false,
                failure = failure,
                uncertain =
                    state.uncertain ||
                        failure is Failure.Refused &&
                            failure.reason == RefusalReason.DispatchUncertain,
            )
        )

    private fun safe(process: TerminalProcess): TerminalProcess =
        process.copy(
            title = host.display(process.title),
            directory = host.display(process.directory),
            command = host.display(process.command),
        )

    override fun listTerminals() {
        val source = live() ?: return
        val operations = ops(source) ?: return
        if (!state.supported || state.busy) return
        val run = ++generation
        set(state.copy(busy = true, failure = null))
        scope.launch {
            when (val listed = operations.listTerminals()) {
                is Outcome.Failed -> if (valid(source, run)) failed(listed.failure)
                is Outcome.Ok ->
                    if (valid(source, run))
                        set(
                            state.copy(
                                busy = false,
                                listed = true,
                                terminals = listed.value.map(::safe).toImmutableList(),
                            )
                        )
            }
        }
    }

    override fun askNewTerminal() {
        if (live() != null && !state.busy && !state.uncertain) set(state.copy(askingCreate = true))
    }

    override fun finishTerminalName(name: String?) {
        set(state.copy(askingCreate = false))
        if (name == null || !validTerminalName(name)) return
        val source = live() ?: return
        val operations = ops(source) ?: return
        if (state.busy || state.uncertain) return
        val run = ++generation
        set(state.copy(busy = true, failure = null))
        scope.launch {
            if (!valid(source, run)) return@launch
            when (val made = operations.createTerminal(name.trim())) {
                is Outcome.Failed -> if (valid(source, run)) failed(made.failure)
                is Outcome.Ok ->
                    if (valid(source, run)) {
                        set(
                            state.copy(
                                busy = false,
                                listed = true,
                                terminals =
                                    (state.terminals.filterNot { it.id == made.value.id } +
                                            safe(made.value))
                                        .toImmutableList(),
                            )
                        )
                        openTerminal(made.value.id)
                    }
            }
        }
    }

    override fun askRenameTerminal(id: String) {
        if (live() != null && !state.busy && !state.uncertain)
            set(state.copy(rename = state.terminals.firstOrNull { it.id == id }))
    }

    override fun finishTerminalRename(name: String?) {
        val target = state.rename ?: return
        set(state.copy(rename = null))
        if (name == null || !validTerminalName(name)) return
        val source = live() ?: return
        val operations = ops(source) ?: return
        if (state.busy || state.uncertain) return
        val run = ++generation
        set(state.copy(busy = true, failure = null))
        scope.launch {
            if (valid(source, run))
                when (val renamed = operations.renameTerminal(target.id, name.trim())) {
                    is Outcome.Failed -> if (valid(source, run)) failed(renamed.failure)
                    is Outcome.Ok ->
                        if (valid(source, run)) {
                            set(state.copy(busy = false))
                            listTerminals()
                        }
                }
        }
    }

    override fun askCloseTerminal(id: String) {
        if (live() != null && !state.busy && !state.uncertain)
            set(state.copy(close = state.terminals.firstOrNull { it.id == id }))
    }

    override fun cancelCloseTerminal() = set(state.copy(close = null))

    override fun confirmCloseTerminal() {
        val target = state.close ?: return
        val source = live() ?: return
        val operations = ops(source) ?: return
        if (state.busy || state.uncertain) return
        val run = ++generation
        set(state.copy(busy = true, close = null, failure = null))
        scope.launch {
            if (valid(source, run))
                when (val removed = operations.removeTerminal(target.id)) {
                    is Outcome.Failed -> if (valid(source, run)) failed(removed.failure)
                    is Outcome.Ok ->
                        if (valid(source, run)) {
                            if (state.liveId == target.id) closeView()
                            set(
                                state.copy(
                                    busy = false,
                                    terminals =
                                        state.terminals
                                            .filterNot { it.id == target.id }
                                            .toImmutableList(),
                                )
                            )
                        }
                }
        }
    }

    override fun openTerminal(id: String) {
        val source = live() ?: return
        val operations = ops(source) ?: return
        val terminal = state.terminals.firstOrNull { it.id == id } ?: return
        val renderer = renderers.singleOrNull() ?: return
        if (state.busy || !state.supported) return
        closeView()
        val run = ++generation
        val stream = ++streamGeneration
        set(state.copy(busy = true, liveId = id, title = terminal.title, failure = null))
        scope.launch {
            val connected = operations.connectTerminal(id)
            if (!valid(source, run) || stream != streamGeneration) {
                (connected as? Outcome.Ok)?.value?.close()
                return@launch
            }
            if (connected is Outcome.Failed) {
                failed(connected.failure)
                return@launch
            }
            val owned = (connected as Outcome.Ok).value
            channel = owned
            val screen =
                renderer.open(
                    onInput = { text -> if (stream == streamGeneration) sendTerminal(text) },
                    onResize = { rows, cols ->
                        if (stream == streamGeneration)
                            scope.launch {
                                if (host.isCurrent(profile, source.identity))
                                    when (val resized = operations.resizeTerminal(id, rows, cols)) {
                                        is Outcome.Failed ->
                                            if (stream == streamGeneration) failed(resized.failure)
                                        is Outcome.Ok -> Unit
                                    }
                            }
                    },
                )
            if (!valid(source, run) || stream != streamGeneration) {
                owned.close()
                (screen as? Outcome.Ok)?.value?.close()
                return@launch
            }
            if (screen is Outcome.Failed) {
                owned.close()
                channel = null
                failed(screen.failure)
                return@launch
            }
            val rendered = (screen as Outcome.Ok).value
            rendering = rendered
            set(state.copy(busy = false, view = rendered.view, inputEnabled = true))
            transcript = scope.launch {
                rendered.transcript.collect { text ->
                    if (stream == streamGeneration) set(state.copy(transcript = text))
                }
            }
            output = scope.launch {
                try {
                    owned.output.collect { text ->
                        if (
                            stream != streamGeneration || !host.isCurrent(profile, source.identity)
                        ) {
                            owned.close()
                            return@collect
                        }
                        when (val appended = rendered.append(text)) {
                            is Outcome.Failed -> {
                                failed(appended.failure)
                                owned.close()
                            }
                            is Outcome.Ok -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (stream == streamGeneration)
                        failed(Failure.Unavailable(FeatureId("terminal")))
                } finally {
                    if (stream == streamGeneration) set(state.copy(inputEnabled = false))
                }
            }
        }
    }

    override fun sendTerminal(value: String) {
        val owned = channel ?: return
        val source = live() ?: return
        if (!state.inputEnabled || value.isEmpty()) return
        val stream = streamGeneration
        scope.launch {
            if (stream == streamGeneration && host.isCurrent(profile, source.identity))
                try {
                    owned.write(value)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (stream == streamGeneration)
                        failed(Failure.Unavailable(FeatureId("terminal")))
                }
        }
    }

    override fun terminalAccessible(on: Boolean) = set(state.copy(accessibleMode = on))

    override fun leaveTerminal() = closeView()

    private fun closeView() {
        streamGeneration++
        output?.cancel()
        output = null
        transcript?.cancel()
        transcript = null
        val oldChannel = channel
        channel = null
        val oldRendering = rendering
        rendering = null
        if (oldChannel != null || oldRendering != null)
            scope.launch(NonCancellable) {
                try {
                    oldChannel?.close()
                } finally {
                    oldRendering?.close()
                }
            }
        set(
            state.copy(
                liveId = null,
                view = null,
                transcript = "",
                inputEnabled = false,
                busy = false,
            )
        )
    }

    fun dispose() {
        generation++
        closeView()
        owner = null
    }
}

public fun validTerminalName(value: String): Boolean =
    value.trim().isNotEmpty() &&
        value.length <= 256 &&
        value.none { it.code < 32 || it.code == 127 }
