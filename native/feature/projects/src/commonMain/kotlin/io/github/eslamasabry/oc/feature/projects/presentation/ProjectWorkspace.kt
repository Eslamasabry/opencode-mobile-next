package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.LocatedProjectGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.WorktreeInspectionGateway
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.core.model.WorktreeScope
import io.github.eslamasabry.oc.engine.git.WorktreeInspection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One project's checkout, explicitly scoped to its folder rather than the connection's shared
 * location.
 */
public data class ProjectWorkspaceUi(
    val available: Boolean = false,
    val visible: Boolean = false,
    val busy: Boolean = false,
    val failure: Failure? = null,
    val mode: VcsDiffMode = VcsDiffMode.WorkingTree,
    val canReadChanges: Boolean = false,
    val canReadWorktrees: Boolean = false,
    val canCreate: Boolean = false,
    val health: VersionControlHealth? = null,
    val changes: ImmutableList<FileDiff> = persistentListOf(),
    val worktrees: ImmutableList<WorktreeInfo> = persistentListOf(),
    val askingName: Boolean = false,
    val removal: WorktreeInfo? = null,
    /** Null means inspection is incomplete; it must never enable Remove. */
    val removalChanges: ImmutableList<VersionControlFile>? = null,
    val selectedDiff: String? = null,
    val diffPage: Int = 0,
    val canInspect: Boolean = false,
    val inspectionScopes: ImmutableList<WorktreeScope> = persistentListOf(),
    val inspectionScope: WorktreeScope? = null,
    val inspectionChanges: ImmutableList<VersionControlFile>? = null,
    val inspectionSessions: ImmutableList<Session> = persistentListOf(),
    val inspectionCursor: String? = null,
    val inspectionBusy: Boolean = false,
    val inspectionFailure: Failure? = null,
)

public interface ProjectWorkspaceActions {
    public fun openWorkspace()

    public fun closeWorkspace()

    public fun retryWorkspace()

    public fun chooseChanges(mode: VcsDiffMode)

    public fun newNamedWorktree()

    public fun createGeneratedWorktree()

    public fun finishWorktreeName(name: String?)

    public fun inspectWorktreeRemoval(directory: String)

    public fun confirmWorktreeRemoval()

    public fun cancelWorktreeRemoval()

    public fun openWorkspaceDiff(path: String)

    public fun closeWorkspaceDiff()

    public fun workspaceDiffPage(delta: Int)

    public fun inspectCopy(directory: String)

    public fun loadMoreCopyConversations()

    public fun openCopyConversation(sessionId: String)
}

/**
 * Serial commands on the caller's main lane; generations discard every retired gateway/sheet
 * response.
 */
internal class ProjectWorkspace(
    private val directory: String,
    private val scope: CoroutineScope,
    private val publish: (ProjectWorkspaceUi) -> Unit,
    private val openConversation: (SessionId) -> Unit,
) : ProjectWorkspaceActions {
    private var server: ProjectServer? = null
    private var generation = 0
    private var state = ProjectWorkspaceUi()
    private var operationLocation = ServerLocation.Default

    private fun set(next: ProjectWorkspaceUi) {
        state = next
        publish(next)
    }

    fun adopt(next: ProjectServer?) {
        if (server?.gateway === next?.gateway && server?.operations === next?.operations) return
        server = next
        generation++
        val caps = next?.gateway?.capabilities?.value
        val read = next?.gateway is LocatedProjectGateway && caps?.sessionDiff == true
        val trees = next?.operations != null && caps?.worktreeBrowsing == true
        val inspect = next?.gateway is WorktreeInspectionGateway && caps?.worktreeInspection == true
        set(
            ProjectWorkspaceUi(
                available = read || trees || inspect,
                canInspect = inspect,
                canReadChanges = read,
                canReadWorktrees = trees,
                canCreate = trees && caps?.worktreeCreate == true,
            )
        )
    }

    private fun current(run: Int, live: ProjectServer): Boolean {
        val active = run == generation && server?.gateway === live.gateway && state.visible
        if (active && live.gateway.location.value != operationLocation) {
            val stale = Failure.Refused(RefusalReason.StaleScope)
            set(
                state.copy(
                    busy = false,
                    inspectionBusy = false,
                    failure = stale,
                    inspectionFailure = stale,
                )
            )
            return false
        }
        return active
    }

    private fun scoped(live: ProjectServer): ServerOperationsGateway? =
        (live.gateway as? LocatedProjectGateway)?.operationsAt(
            ServerLocation(directory = directory)
        )

    override fun openWorkspace() {
        if (!state.available) return
        set(state.copy(visible = true))
        retryWorkspace()
    }

    override fun closeWorkspace() {
        generation++
        set(
            state.copy(
                visible = false,
                busy = false,
                inspectionBusy = false,
                askingName = false,
                removal = null,
                removalChanges = null,
            )
        )
    }

    override fun retryWorkspace() {
        val live = server ?: return
        if (!state.visible || state.busy) return
        val run = ++generation
        val mode = state.mode
        operationLocation = live.gateway.location.value
        set(state.copy(busy = true, failure = null))
        scope.launch {
            var next = state
            if (next.canReadChanges) {
                val ops = scoped(live) ?: return@launch
                when (val health = ops.versionControlHealth()) {
                    is Outcome.Ok -> next = next.copy(health = health.value)
                    is Outcome.Failed -> next = next.copy(failure = health.failure)
                }
                if (!current(run, live)) return@launch
                when (val changes = ops.listVcsDiffs(mode)) {
                    is Outcome.Ok ->
                        next = next.copy(changes = changes.value, selectedDiff = null, diffPage = 0)
                    is Outcome.Failed -> next = next.copy(failure = changes.failure)
                }
                if (!current(run, live)) return@launch
            }
            if (next.canReadWorktrees) {
                when (val trees = live.operations!!.listWorktrees(directory)) {
                    is Outcome.Ok -> next = next.copy(worktrees = trees.value)
                    is Outcome.Failed -> next = next.copy(failure = trees.failure)
                }
                if (!current(run, live)) return@launch
            }
            set(next.copy(busy = false))
            if (next.canInspect) loadInspectionCopies(live, run)
        }
    }

    private fun inspection(live: ProjectServer): WorktreeInspection? =
        (live.gateway as? WorktreeInspectionGateway)?.let { gateway ->
            WorktreeInspection(gateway) { live.gateway.capabilities.value }
        }

    private suspend fun loadInspectionCopies(live: ProjectServer, run: Int) {
        val reader = inspection(live) ?: return
        set(state.copy(inspectionBusy = true, inspectionFailure = null))
        val copies = reader.copies(ServerLocation(directory, operationLocation.workspace))
        if (!current(run, live)) return
        when (copies) {
            is Outcome.Failed ->
                set(state.copy(inspectionBusy = false, inspectionFailure = copies.failure))
            is Outcome.Ok -> {
                val selected =
                    state.inspectionScope?.takeIf { it in copies.value }
                        ?: copies.value.firstOrNull()
                set(
                    state.copy(
                        inspectionScopes = copies.value,
                        inspectionScope = selected,
                        inspectionBusy = false,
                        inspectionChanges = null,
                        inspectionSessions = persistentListOf(),
                        inspectionCursor = null,
                    )
                )
                if (selected != null) readInspection(live, run, selected, null, false)
            }
        }
    }

    override fun inspectCopy(directory: String) {
        val live = server ?: return
        val target = state.inspectionScopes.firstOrNull { it.directory == directory } ?: return
        if (!state.visible || !state.canInspect || state.busy || state.inspectionBusy) return
        if (target.workspaceId != live.gateway.location.value.workspace) {
            set(state.copy(inspectionFailure = Failure.Refused(RefusalReason.StaleScope)))
            return
        }
        val run = ++generation
        operationLocation = live.gateway.location.value
        set(
            state.copy(
                inspectionScope = target,
                inspectionChanges = null,
                inspectionSessions = persistentListOf(),
                inspectionCursor = null,
            )
        )
        scope.launch { readInspection(live, run, target, null, false) }
    }

    override fun loadMoreCopyConversations() {
        val live = server ?: return
        val target = state.inspectionScope ?: return
        val cursor = state.inspectionCursor ?: return
        if (!state.visible || state.inspectionBusy || state.busy || !current(generation, live))
            return
        val run = generation
        set(state.copy(inspectionBusy = true))
        scope.launch { readInspection(live, run, target, cursor, true) }
    }

    private suspend fun readInspection(
        live: ProjectServer,
        run: Int,
        target: WorktreeScope,
        cursor: String?,
        append: Boolean,
    ) {
        val reader = inspection(live) ?: return
        if (!current(run, live)) return
        set(state.copy(inspectionBusy = true, inspectionFailure = null))
        if (!append) {
            val changes = reader.loadChanges(target)
            if (!current(run, live)) return
            when (changes) {
                is Outcome.Ok -> set(state.copy(inspectionChanges = changes.value))
                is Outcome.Failed ->
                    set(state.copy(inspectionChanges = null, inspectionFailure = changes.failure))
            }
        }
        val page = reader.conversationPage(target, cursor)
        if (!current(run, live)) return
        when (page) {
            is Outcome.Failed ->
                set(state.copy(inspectionBusy = false, inspectionFailure = page.failure))
            is Outcome.Ok -> {
                val before = if (append) state.inspectionSessions else persistentListOf()
                val seen = before.mapTo(HashSet()) { it.id }
                set(
                    state.copy(
                        inspectionBusy = false,
                        inspectionSessions =
                            (before + page.value.items.filter { seen.add(it.id) })
                                .toImmutableList(),
                        inspectionCursor = page.value.nextCursor,
                    )
                )
            }
        }
    }

    override fun openCopyConversation(sessionId: String) {
        val live = server ?: return
        if (!current(generation, live)) return
        val session = state.inspectionSessions.firstOrNull { it.id.raw == sessionId } ?: return
        openConversation(session.id)
    }

    override fun chooseChanges(mode: VcsDiffMode) {
        if (state.busy || !state.canReadChanges || state.mode == mode) return
        set(
            state.copy(mode = mode, changes = persistentListOf(), selectedDiff = null, diffPage = 0)
        )
        retryWorkspace()
    }

    override fun newNamedWorktree() {
        if (state.canCreate && !state.busy) set(state.copy(askingName = true))
    }

    override fun finishWorktreeName(name: String?) {
        set(state.copy(askingName = false))
        if (name == null || !validWorktreeName(name)) return
        create(name.trim())
    }

    override fun createGeneratedWorktree() = create(null)

    private fun create(name: String?) {
        val live = server ?: return
        val ops = live.operations ?: return
        if (
            !state.visible ||
                state.busy ||
                !state.canCreate ||
                !live.gateway.capabilities.value.worktreeCreate
        )
            return
        val run = ++generation
        set(state.copy(busy = true, failure = null, removal = null))
        scope.launch {
            if (!current(run, live)) return@launch
            when (val result = ops.createWorktree(directory, name)) {
                is Outcome.Failed ->
                    if (current(run, live)) set(state.copy(busy = false, failure = result.failure))
                is Outcome.Ok ->
                    if (current(run, live)) {
                        set(
                            state.copy(
                                busy = false,
                                worktrees =
                                    (state.worktrees.filterNot {
                                            it.directory == result.value.directory
                                        } + result.value)
                                        .toImmutableList(),
                            )
                        )
                    }
            }
        }
    }

    override fun inspectWorktreeRemoval(directory: String) {
        val live = server ?: return
        val ops = live.operations ?: return
        val target = state.worktrees.firstOrNull { it.directory == directory } ?: return
        if (state.busy || !state.visible || !live.gateway.capabilities.value.worktreeBrowsing)
            return
        val run = ++generation
        set(state.copy(busy = true, failure = null, removal = target, removalChanges = null))
        scope.launch {
            val inspected = ops.listWorktreeFileStatuses(target.directory)
            if (!current(run, live)) return@launch
            when (inspected) {
                is Outcome.Ok -> set(state.copy(busy = false, removalChanges = inspected.value))
                is Outcome.Failed ->
                    set(state.copy(busy = false, failure = inspected.failure, removal = null))
            }
        }
    }

    override fun cancelWorktreeRemoval() {
        if (!state.busy) set(state.copy(removal = null, removalChanges = null))
    }

    override fun confirmWorktreeRemoval() {
        val live = server ?: return
        val ops = live.operations ?: return
        val target = state.removal ?: return
        val inspected = state.removalChanges ?: return
        if (state.busy || !state.visible || !live.gateway.capabilities.value.worktreeBrowsing)
            return
        val run = ++generation
        set(state.copy(busy = true, failure = null))
        scope.launch {
            // The explicit confirmation belongs to this exact inspection, never a changed checkout.
            val fresh = ops.listWorktreeFileStatuses(target.directory)
            if (!current(run, live)) return@launch
            when (fresh) {
                is Outcome.Failed ->
                    set(
                        state.copy(
                            busy = false,
                            failure = fresh.failure,
                            removal = null,
                            removalChanges = null,
                        )
                    )
                is Outcome.Ok -> {
                    if (fresh.value != inspected) {
                        set(
                            state.copy(
                                busy = false,
                                failure = Failure.Refused(RefusalReason.StaleScope),
                                removal = null,
                                removalChanges = null,
                            )
                        )
                        return@launch
                    }
                    when (val removed = ops.removeWorktree(directory, target.directory)) {
                        is Outcome.Failed ->
                            if (current(run, live))
                                set(
                                    state.copy(
                                        busy = false,
                                        failure = removed.failure,
                                        removal = null,
                                        removalChanges = null,
                                    )
                                )
                        is Outcome.Ok ->
                            if (current(run, live))
                                set(
                                    state.copy(
                                        busy = false,
                                        worktrees =
                                            state.worktrees
                                                .filterNot { it.directory == target.directory }
                                                .toImmutableList(),
                                        removal = null,
                                        removalChanges = null,
                                    )
                                )
                    }
                }
            }
        }
    }

    override fun openWorkspaceDiff(path: String) {
        if (state.changes.any { it.file == path })
            set(state.copy(selectedDiff = path, diffPage = 0))
    }

    override fun closeWorkspaceDiff() = set(state.copy(selectedDiff = null, diffPage = 0))

    override fun workspaceDiffPage(delta: Int) {
        val selected = state.changes.firstOrNull { it.file == state.selectedDiff } ?: return
        // A patch ends with a newline: count real lines so exactly 200 lines is one page.
        val lines = selected.patch.orEmpty().trimEnd('\n').lineSequence().count()
        val pages = ((lines - 1) / 200).coerceAtLeast(0)
        set(state.copy(diffPage = (state.diffPage + delta).coerceIn(0, pages)))
    }
}

/** Names are a single slug. The host, not the app, chooses branches and filesystem locations. */
public fun validWorktreeName(value: String): Boolean =
    Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}").matches(value.trim()) && !value.contains("..")
