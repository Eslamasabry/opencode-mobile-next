package io.github.eslamasabry.oc.engine.agents.worktree

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class IsolatedTaskPhase {
    Form,
    Creating,
    Preparing,
    Unconfirmed,
    SetupFailed,
    CreateFailed,
    CreateUncertain,
    Opening,
    OpenFailed,
    OpenUncertain,
    Sending,
    Unsent,
    Opened,
    Cancelled,
    Removed,
    ScopeChanged,
}

public data class IsolatedTaskState(
    val phase: IsolatedTaskPhase = IsolatedTaskPhase.Form,
    val worktree: WorktreeInfo? = null,
    val ref: ConversationRef? = null,
    val failure: Failure? = null,
    val draftKept: Boolean = false,
    val sendUncertain: Boolean = false,
) {
    public val busy: Boolean
        get() = phase == IsolatedTaskPhase.Opening || phase == IsolatedTaskPhase.Sending
}

public sealed interface IsolatedTaskSendResult {
    public data object Accepted : IsolatedTaskSendResult

    public data class NotSent(val failure: Failure) : IsolatedTaskSendResult

    public data class Unconfirmed(val failure: Failure) : IsolatedTaskSendResult
}

/** Platform composition supplies persistence and the exact graph's durable delivery owner. */
public interface IsolatedTaskTarget {
    public val gateway: ServerGateway
    public val operations: ServerOperationsGateway

    public fun isCurrent(): Boolean

    public suspend fun send(ref: ConversationRef, task: String): IsolatedTaskSendResult

    public suspend fun keepDraft(ref: ConversationRef, task: String): Outcome<Unit>

    public suspend fun clearTaskDraft(task: String): Outcome<Unit>
}

/**
 * One worktree task. The event subscription precedes creation; readiness is correlated to the exact
 * project and normalized directory. Stop waiting never undoes work. Unknown mutation outcomes never
 * retry.
 */
public class IsolatedTaskLaunch(
    private val scope: CoroutineScope,
    private val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
    private val project: WorkspaceProject,
    private val target: IsolatedTaskTarget,
    private val timeoutMs: Long = 45_000,
) {
    private val mutable = MutableStateFlow(IsolatedTaskState())
    public val state: StateFlow<IsolatedTaskState> = mutable.asStateFlow()
    private var events: Job? = null
    private var timer: Job? = null
    private val early = LinkedHashMap<String, Boolean>()
    private var task = ""
    private var closed = false

    private fun key(path: String): String =
        path.trim().replace('\\', '/').trimEnd('/').ifEmpty { "/" }

    private fun stale(): Failure = Failure.Refused(RefusalReason.StaleScope)

    private fun current(): Boolean = !closed && target.isCurrent()

    private fun retire(phase: IsolatedTaskPhase, failure: Failure? = null) {
        timer?.cancel()
        timer = null
        events?.cancel()
        events = null
        mutable.update { it.copy(phase = phase, failure = failure) }
    }

    public fun start(text: String, name: String) {
        if (
            state.value.phase != IsolatedTaskPhase.Form &&
                state.value.phase != IsolatedTaskPhase.CreateFailed
        )
            return
        task = text.trim()
        if (!current()) {
            retire(IsolatedTaskPhase.ScopeChanged, stale())
            return
        }
        mutable.value = IsolatedTaskState(IsolatedTaskPhase.Creating)
        events =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                target.gateway.events.all.collect(::event)
            }
        scope.launch {
            val projects = target.operations.listProjects()
            if (!current()) {
                retire(IsolatedTaskPhase.ScopeChanged, stale())
                return@launch
            }
            when (projects) {
                is Outcome.Failed -> {
                    retire(IsolatedTaskPhase.CreateFailed, projects.failure)
                    return@launch
                }
                is Outcome.Ok ->
                    if (
                        projects.value.none {
                            it.id == project.id && key(it.directory) == key(project.directory)
                        }
                    ) {
                        retire(IsolatedTaskPhase.ScopeChanged, stale())
                        return@launch
                    }
            }
            when (val ready = target.gateway.awaitActionTransport()) {
                is Outcome.Failed -> {
                    retire(IsolatedTaskPhase.CreateFailed, ready.failure)
                    return@launch
                }
                is Outcome.Ok -> Unit
            }
            if (!current()) {
                retire(IsolatedTaskPhase.ScopeChanged, stale())
                return@launch
            }
            // From this point an absent receipt is uncertain; never repeat the create.
            when (
                val copy =
                    target.operations.createWorktree(
                        project.directory,
                        name.trim().takeIf { it.isNotEmpty() },
                    )
            ) {
                is Outcome.Failed ->
                    if (!closed) retire(IsolatedTaskPhase.CreateUncertain, copy.failure)
                is Outcome.Ok -> {
                    mutable.update { it.copy(worktree = copy.value) }
                    if (!current()) {
                        retire(IsolatedTaskPhase.ScopeChanged, stale())
                        return@launch
                    }
                    if (state.value.phase == IsolatedTaskPhase.Cancelled) return@launch
                    val seen = early.remove(key(copy.value.directory))
                    early.clear()
                    if (seen != null) {
                        mutable.update { it.copy(phase = IsolatedTaskPhase.Preparing) }
                        ready(seen)
                    } else {
                        mutable.update { it.copy(phase = IsolatedTaskPhase.Preparing) }
                        armTimer()
                    }
                }
            }
        }
    }

    private fun event(envelope: GatewayEnvelope) {
        val event = envelope.event as? GatewayEvent.WorktreeOutcome ?: return
        val location = envelope.location ?: return
        val directory = location.directory?.takeIf { it.isNotBlank() } ?: return
        if (location.project != null && location.project != project.id) return
        if (!current()) {
            retire(IsolatedTaskPhase.ScopeChanged, stale())
            return
        }
        val copy = state.value.worktree
        if (copy == null) {
            if (early.size >= 32) early.remove(early.keys.first())
            early[key(directory)] = event.ready
        } else if (
            key(copy.directory) == key(directory) &&
                state.value.phase in
                    setOf(IsolatedTaskPhase.Preparing, IsolatedTaskPhase.Unconfirmed)
        ) {
            ready(event.ready)
        }
    }

    private fun ready(success: Boolean) {
        timer?.cancel()
        timer = null
        if (success) open(acceptUnconfirmed = true) else retire(IsolatedTaskPhase.SetupFailed)
    }

    private fun armTimer() {
        timer?.cancel()
        timer = scope.launch {
            delay(timeoutMs)
            if (state.value.phase == IsolatedTaskPhase.Preparing)
                mutable.update { it.copy(phase = IsolatedTaskPhase.Unconfirmed) }
        }
    }

    public fun keepWaiting() {
        if (state.value.phase != IsolatedTaskPhase.Unconfirmed || !current()) return
        mutable.update { it.copy(phase = IsolatedTaskPhase.Preparing) }
        armTimer()
    }

    public fun stopWaiting() {
        if (state.value.busy) return
        retire(IsolatedTaskPhase.Cancelled)
    }

    public fun open(acceptUnconfirmed: Boolean = false, acceptFailedSetup: Boolean = false) {
        val before = state.value
        val copy = before.worktree ?: return
        val permitted =
            before.phase == IsolatedTaskPhase.Preparing && acceptUnconfirmed ||
                before.phase == IsolatedTaskPhase.Unconfirmed && acceptUnconfirmed ||
                before.phase == IsolatedTaskPhase.SetupFailed && acceptFailedSetup ||
                before.phase == IsolatedTaskPhase.OpenFailed
        if (!permitted) return
        if (!current()) {
            retire(IsolatedTaskPhase.ScopeChanged, stale())
            return
        }
        retire(IsolatedTaskPhase.Opening)
        scope.launch {
            when (val ready = target.gateway.awaitActionTransport()) {
                is Outcome.Failed -> {
                    retire(IsolatedTaskPhase.OpenFailed, ready.failure)
                    return@launch
                }
                is Outcome.Ok -> Unit
            }
            if (!current()) {
                retire(IsolatedTaskPhase.ScopeChanged, stale())
                return@launch
            }
            val created =
                target.gateway.createSession(
                    NewSession(location = ServerLocation(directory = copy.directory))
                )
            when (created) {
                is Outcome.Failed -> retire(IsolatedTaskPhase.OpenUncertain, created.failure)
                is Outcome.Ok -> {
                    val ref = ConversationRef(profile, created.value.id)
                    mutable.update { it.copy(ref = ref) }
                    val confirmed =
                        when (val read = target.gateway.session(ref.sessionId)) {
                            is Outcome.Ok -> read.value
                            is Outcome.Failed -> {
                                retire(IsolatedTaskPhase.OpenUncertain, read.failure)
                                return@launch
                            }
                        }
                    if (
                        !current() ||
                            confirmed.directory == null ||
                            key(confirmed.directory!!) != key(copy.directory) ||
                            confirmed.workspaceId != null ||
                            confirmed.projectId?.raw?.let { it != project.id } == true
                    ) {
                        retire(IsolatedTaskPhase.OpenUncertain, stale())
                        return@launch
                    }
                    if (task.isEmpty()) {
                        retire(IsolatedTaskPhase.Opened)
                        return@launch
                    }
                    mutable.update { it.copy(phase = IsolatedTaskPhase.Sending) }
                    val sent = target.send(ref, task)
                    if (!current()) {
                        retire(IsolatedTaskPhase.ScopeChanged, stale())
                        return@launch
                    }
                    when (sent) {
                        IsolatedTaskSendResult.Accepted -> {
                            target.clearTaskDraft(task)
                            retire(IsolatedTaskPhase.Opened)
                        }
                        is IsolatedTaskSendResult.NotSent -> {
                            val kept = target.keepDraft(ref, task) is Outcome.Ok
                            mutable.update { it.copy(draftKept = kept) }
                            retire(IsolatedTaskPhase.Unsent, sent.failure)
                        }
                        is IsolatedTaskSendResult.Unconfirmed -> {
                            mutable.update { it.copy(draftKept = false, sendUncertain = true) }
                            retire(IsolatedTaskPhase.Unsent, sent.failure)
                        }
                    }
                }
            }
        }
    }

    public fun removeFailedCopy() {
        if (state.value.phase != IsolatedTaskPhase.SetupFailed || !current()) return
        val copy = state.value.worktree ?: return
        mutable.update { it.copy(phase = IsolatedTaskPhase.Opening) }
        scope.launch {
            if (!current()) {
                retire(IsolatedTaskPhase.ScopeChanged, stale())
                return@launch
            }
            when (
                val removed = target.operations.removeWorktree(project.directory, copy.directory)
            ) {
                is Outcome.Ok -> retire(IsolatedTaskPhase.Removed)
                is Outcome.Failed -> retire(IsolatedTaskPhase.SetupFailed, removed.failure)
            }
        }
    }

    public fun close() {
        closed = true
        timer?.cancel()
        events?.cancel()
    }
}
