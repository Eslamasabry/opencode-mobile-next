package io.github.eslamasabry.oc.feature.git.servertools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.GitRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@ContributesTo(ConnectionScope::class)
public interface GitServerToolsAccessors {
    public val gitServerOperations: ServerOperationsGateway
}

public enum class ServerToolsResult {
    Initialized,
    WorktreeCreated,
    ShellSaved,
}

public data class GitServerToolsUi(
    val directory: String? = null,
    val gitInit: Boolean = false,
    val worktreeCreate: Boolean = false,
    val shellSettings: Boolean = false,
    val open: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val invalidName: Boolean = false,
    val result: ServerToolsResult? = null,
    val createdDirectory: String? = null,
    val shells: TerminalShellSettings? = null,
) {
    public val available: Boolean
        get() = gitInit || worktreeCreate || shellSettings
}

public interface GitServerToolsActions {
    public fun show()

    public fun close()

    public fun initialize()

    public fun createWorktree(name: String)

    public fun selectShell(path: String)

    public fun readShells()
}

/**
 * Commands always name the displayed live graph and location. A reconnect cannot retarget a pending
 * action.
 */
@AssistedInject
public class GitServerToolsViewModel(
    @Assisted private val route: GitRoute,
    private val registry: ConnectionRegistry,
) : ViewModel(), GitServerToolsActions {
    private val mutable = MutableStateFlow(GitServerToolsUi())
    public val state: StateFlow<GitServerToolsUi> = mutable.asStateFlow()
    private var graph: ConnectionGraph? = null
    private var location: ServerLocation? = null

    init {
        viewModelScope.launch {
            registry.live.collectLatest { live ->
                val next = live[route.ref.profileId]
                graph = next
                if (next == null) {
                    location = null
                    mutable.value = GitServerToolsUi()
                } else {
                    next.session.server.location.collectLatest { target ->
                        location = target
                        next.session.capabilities.collectLatest { caps ->
                            val before = mutable.value
                            mutable.value =
                                GitServerToolsUi(
                                    directory = target.directory,
                                    gitInit = caps.gitInit && !target.directory.isNullOrBlank(),
                                    worktreeCreate =
                                        caps.worktreeCreate && !target.directory.isNullOrBlank(),
                                    shellSettings = caps.shellSettings,
                                    open = before.open,
                                )
                        }
                    }
                }
            }
        }
    }

    override fun show() {
        if (!state.value.available) return
        mutable.value = state.value.copy(open = true)
        if (state.value.shellSettings) readShells()
    }

    override fun close() {
        mutable.value = state.value.copy(open = false)
    }

    private fun current(expected: ConnectionGraph, target: ServerLocation): Boolean =
        registry.live.value[route.ref.profileId] === expected &&
            expected.session.server.location.value == target

    private data class ToolReply(
        val result: ServerToolsResult,
        val directory: String? = null,
        val shells: TerminalShellSettings? = null,
        val refreshFailed: Boolean = false,
    )

    private fun command(block: suspend (ServerOperationsGateway) -> Outcome<ToolReply>) {
        val expected = graph ?: return
        val target = location ?: return
        if (state.value.busy || !current(expected, target)) return
        val operations = (expected as? GitServerToolsAccessors)?.gitServerOperations ?: return
        mutable.value =
            state.value.copy(
                busy = true,
                failed = false,
                invalidName = false,
                result = null,
                createdDirectory = null,
            )
        viewModelScope.launch {
            val answer = block(operations)
            if (current(expected, target)) {
                val reply = (answer as? Outcome.Ok)?.value
                mutable.value =
                    state.value.copy(
                        busy = false,
                        failed = answer is Outcome.Failed || reply?.refreshFailed == true,
                        result = reply?.result,
                        createdDirectory = reply?.directory,
                        shells = reply?.shells ?: state.value.shells,
                    )
            }
        }
    }

    override fun initialize() {
        if (!state.value.gitInit) return
        command { ops ->
            when (val result = ops.initializeGitRepository()) {
                is Outcome.Failed -> result
                is Outcome.Ok -> Outcome.Ok(ToolReply(ServerToolsResult.Initialized))
            }
        }
    }

    override fun createWorktree(name: String) {
        val directory = state.value.directory ?: return
        val value = name.trim()
        if (!state.value.worktreeCreate) return
        if (value.isEmpty() || value.length > 120 || value.any { it.code < 32 || it.code == 127 }) {
            mutable.value = state.value.copy(invalidName = true)
            return
        }
        command { ops ->
            when (val result = ops.createWorktree(directory, value)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    Outcome.Ok(
                        ToolReply(
                            ServerToolsResult.WorktreeCreated,
                            directory = result.value.directory,
                        )
                    )
                }
            }
        }
    }

    override fun selectShell(path: String) {
        val expected = graph ?: return
        val target = location ?: return
        if (!state.value.shellSettings) return
        if (
            path.isNotEmpty() &&
                state.value.shells?.options?.none { it.path == path && it.acceptable } != false
        )
            return
        command { ops ->
            when (val result = ops.selectTerminalShell(path)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    if (!current(expected, target))
                        return@command Outcome.Ok(ToolReply(ServerToolsResult.ShellSaved))
                    val latest = ops.terminalShellSettings()
                    Outcome.Ok(
                        ToolReply(
                            ServerToolsResult.ShellSaved,
                            shells = (latest as? Outcome.Ok)?.value,
                            refreshFailed = latest is Outcome.Failed,
                        )
                    )
                }
            }
        }
    }

    override fun readShells() {
        val expected = graph ?: return
        val target = location ?: return
        if (state.value.busy || !state.value.shellSettings || !current(expected, target)) return
        val operations = (expected as? GitServerToolsAccessors)?.gitServerOperations ?: return
        mutable.value = state.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            val answer = operations.terminalShellSettings()
            if (current(expected, target))
                mutable.value =
                    state.value.copy(
                        busy = false,
                        failed = answer is Outcome.Failed,
                        shells = (answer as? Outcome.Ok)?.value,
                    )
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: GitRoute): GitServerToolsViewModel
    }
}
