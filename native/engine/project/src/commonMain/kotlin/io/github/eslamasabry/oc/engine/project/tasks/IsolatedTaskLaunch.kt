package io.github.eslamasabry.oc.engine.project.tasks

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where a fresh-worktree task launch stands. */
public enum class IsolatedTaskPhase {
    /** Nothing requested yet. */
    Idle,

    /** The create request is in flight; a cancel here cannot promise that nothing exists. */
    Creating,

    /** The server returned the worktree directory; its setup has not reported. */
    Preparing,

    /** `worktree.ready` arrived for this directory. */
    Ready,

    /** No readiness event within the wait; the person may keep waiting or open it anyway. */
    Unconfirmed,

    /** `worktree.failed` arrived, or the create call itself failed. */
    Failed,

    /** Switching scope and creating the blank session. */
    Opening,

    /** The session exists in the worktree's scope. */
    Opened,

    /** The person stopped waiting. Anything the server created stays listed. */
    Cancelled,
}

/** A `worktree.ready` / `worktree.failed` event as the launch needs it. */
public data class WorktreeEvent(
    val ready: Boolean,
    val directory: String?,
    val project: String?,
    val name: String? = null,
    val branch: String? = null,
    /** Server text for `worktree.failed`; shown as the setup's own words, never an exception. */
    val message: String? = null,
)

public data class IsolatedTaskState(
    val phase: IsolatedTaskPhase = IsolatedTaskPhase.Idle,
    /** The created worktree once the server answered, whatever happened afterwards. */
    val worktree: WorktreeInfo? = null,
    val session: Session? = null,
    /** Server-provided `worktree.failed` text, when any. */
    val message: String? = null,
    /** The create call's failure, when it failed before answering. */
    val failure: Failure? = null,
    /** The last open attempt's failure; the launch stays openable. */
    val openError: Failure? = null,
) {
    val canCancel: Boolean get() = phase == IsolatedTaskPhase.Creating || phase == IsolatedTaskPhase.Preparing || phase == IsolatedTaskPhase.Unconfirmed
    val canOpen: Boolean get() = phase == IsolatedTaskPhase.Ready || phase == IsolatedTaskPhase.Unconfirmed

    /** The copy was made but its setup failed: start in it only by an explicit choice. False when create failed. */
    val canStartAfterFailedSetup: Boolean get() = phase == IsolatedTaskPhase.Failed && worktree != null && failure == null
    val isTerminal: Boolean get() = phase == IsolatedTaskPhase.Opened || phase == IsolatedTaskPhase.Failed || phase == IsolatedTaskPhase.Cancelled
}

/**
 * One explicit "start a task in a fresh worktree" run (port of lib/state/isolated_task_launch.dart). Subscribes to
 * [events] before the create call, so a ready event that lands before the create answer is kept and matched once the
 * directory is known; correlation is by project and normalized directory. Cancelling and timing out never delete.
 */
public class IsolatedTaskLaunch(
    private val scope: CoroutineScope,
    /** The project id events must name (or leave empty). */
    public val projectId: String,
    private val events: Flow<WorktreeEvent>,
    private val create: suspend () -> Outcome<WorktreeInfo>,
    private val open: suspend (directory: String) -> Outcome<Session>,
    public val readinessTimeout: Duration = 45.seconds,
) {
    private val _state = MutableStateFlow(IsolatedTaskState())
    public val state: StateFlow<IsolatedTaskState> = _state.asStateFlow()

    private var subscription: Job? = null
    private var timer: Job? = null
    private val early = HashMap<String, WorktreeEvent>()

    private val current: IsolatedTaskState get() = _state.value

    private fun update(block: (IsolatedTaskState) -> IsolatedTaskState) {
        _state.value = block(_state.value)
    }

    /** Creates the worktree and waits for its readiness event. Only the first call does anything. */
    public fun start() {
        if (current.phase != IsolatedTaskPhase.Idle) return
        update { it.copy(phase = IsolatedTaskPhase.Creating) }
        subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) { events.collect(::onEvent) }
        scope.launch {
            when (val created = create()) {
                is Outcome.Failed -> {
                    if (current.phase == IsolatedTaskPhase.Cancelled) return@launch
                    update { it.copy(failure = created.failure) }
                    finish(IsolatedTaskPhase.Failed)
                }
                is Outcome.Ok -> {
                    update { it.copy(worktree = created.value) }
                    // The person stopped waiting before the answer came back: record what the server made, never remove it.
                    if (current.phase == IsolatedTaskPhase.Cancelled) return@launch
                    val kept = early.remove(key(created.value.directory))
                    if (kept != null) {
                        apply(kept)
                    } else {
                        update { it.copy(phase = IsolatedTaskPhase.Preparing) }
                        armTimer()
                    }
                }
            }
        }
    }

    /** Stops waiting. Does not undo a create that may already have run. */
    public fun cancel() {
        if (!current.canCancel) return
        timer?.cancel()
        timer = null
        finish(IsolatedTaskPhase.Cancelled)
    }

    /** From [IsolatedTaskPhase.Unconfirmed], waits another [readinessTimeout]. */
    public fun keepWaiting() {
        if (current.phase != IsolatedTaskPhase.Unconfirmed) return
        update { it.copy(phase = IsolatedTaskPhase.Preparing) }
        armTimer()
    }

    /**
     * Opens a blank session inside the worktree: when ready; when unconfirmed only with [acceptUnconfirmed]; after a
     * failed setup only with [acceptFailedSetup]. A failed open keeps the launch openable.
     */
    public suspend fun open(acceptUnconfirmed: Boolean = false, acceptFailedSetup: Boolean = false) {
        val now = current
        val directory = now.worktree?.directory ?: return
        val afterFailedSetup = acceptFailedSetup && now.canStartAfterFailedSetup
        if (!now.canOpen && !afterFailedSetup) return
        if (now.phase == IsolatedTaskPhase.Unconfirmed && !acceptUnconfirmed) return
        val previous = now.phase
        timer?.cancel()
        timer = null
        update { it.copy(phase = IsolatedTaskPhase.Opening, openError = null) }
        when (val opened = open(directory)) {
            is Outcome.Ok -> {
                update { it.copy(session = opened.value) }
                finish(IsolatedTaskPhase.Opened)
            }
            is Outcome.Failed -> update { it.copy(phase = previous, openError = opened.failure) }
        }
    }

    /** Stops listening and waiting (the screen closed). The server's worktree is untouched. */
    public fun close() {
        subscription?.cancel()
        timer?.cancel()
    }

    private fun onEvent(event: WorktreeEvent) {
        val directory = event.directory?.trim().orEmpty()
        if (directory.isEmpty()) return
        val eventProject = event.project?.trim().orEmpty()
        if (eventProject.isNotEmpty() && eventProject != projectId) return
        val known = current.worktree?.directory
        if (known == null) {
            early[key(directory)] = event
            return
        }
        if (key(directory) != key(known)) return
        if (current.phase != IsolatedTaskPhase.Preparing && current.phase != IsolatedTaskPhase.Unconfirmed) return
        apply(event)
    }

    private fun apply(event: WorktreeEvent) {
        timer?.cancel()
        timer = null
        if (event.ready) {
            update { s ->
                val w = s.worktree!!
                s.copy(
                    phase = IsolatedTaskPhase.Ready,
                    worktree = w.copy(
                        name = event.name?.trim()?.ifEmpty { null } ?: w.name,
                        branch = event.branch?.trim()?.ifEmpty { null } ?: w.branch,
                        ready = true,
                    ),
                )
            }
            return
        }
        update { it.copy(message = event.message?.trim()?.ifEmpty { null }) }
        finish(IsolatedTaskPhase.Failed)
    }

    private fun armTimer() {
        timer?.cancel()
        timer =
            scope.launch {
                delay(readinessTimeout)
                if (current.phase == IsolatedTaskPhase.Preparing) update { it.copy(phase = IsolatedTaskPhase.Unconfirmed) }
            }
    }

    private fun finish(phase: IsolatedTaskPhase) {
        subscription?.cancel()
        subscription = null
        update { it.copy(phase = phase) }
    }

    private companion object {
        fun key(directory: String): String {
            var value = directory.trim().replace('\\', '/')
            while (value.length > 1 && value.endsWith("/")) value = value.dropLast(1)
            return value
        }
    }
}
