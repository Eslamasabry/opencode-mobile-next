package io.github.eslamasabry.oc.feature.projects.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public enum class IsolatedPhase {
    Creating,
    Preparing,
    Ready,
    Unconfirmed,
    Failed,
    Opening,
    Opened,
    Cancelled,
}

public data class IsolatedTaskUi(
    val phase: IsolatedPhase = IsolatedPhase.Creating,
    val copy: WorktreeInfo? = null,
    val failure: Failure? = null,
    val acceptedUnconfirmed: Boolean = false,
    val acceptedFailedSetup: Boolean = false,
) {
    public val canCancel: Boolean
        get() =
            phase in
                setOf(IsolatedPhase.Creating, IsolatedPhase.Preparing, IsolatedPhase.Unconfirmed)
}

/**
 * One user-requested copy. Subscribes before create, keeps early results, and never deletes a copy.
 */
internal class IsolatedTaskLaunch(
    private val projectId: String?,
    private val events: Flow<GatewayEnvelope>,
    private val scope: CoroutineScope,
    private val current: () -> Boolean,
    private val create: suspend () -> Outcome<WorktreeInfo>,
    private val open: suspend (String) -> Outcome<Session>,
    private val opened: (Session) -> Unit,
) {
    private val mutable = MutableStateFlow(IsolatedTaskUi())
    val state: StateFlow<IsolatedTaskUi> = mutable.asStateFlow()
    private val early = LinkedHashMap<Pair<String, String?>, GatewayEvent.WorktreeOutcome>()
    private var subscription: Job? = null
    private var timer: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        subscription =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                events.collect { envelope ->
                    val result = envelope.event as? GatewayEvent.WorktreeOutcome ?: return@collect
                    if (result.workspace) return@collect
                    val eventProject = result.projectId ?: envelope.location?.project
                    val expectedProject = state.value.copy?.projectId ?: projectId
                    if (
                        eventProject != null &&
                            expectedProject != null &&
                            eventProject != expectedProject
                    )
                        return@collect
                    val directory =
                        result.directory ?: envelope.location?.directory ?: return@collect
                    val key =
                        ProjectRules.normalize(directory).takeIf { it.isNotBlank() }
                            ?: return@collect
                    val copy = state.value.copy
                    if (copy == null && state.value.phase == IsolatedPhase.Creating) {
                        // B-AGENT-115: bound unrelated early observations until the create reply
                        // supplies the exact path.
                        val identity = key to eventProject
                        if (early.size >= 64 && identity !in early) early.remove(early.keys.first())
                        early[identity] =
                            result.copy(directory = directory, projectId = eventProject)
                    } else if (
                        copy != null &&
                            ProjectRules.normalize(copy.directory) == key &&
                            state.value.phase in
                                setOf(IsolatedPhase.Preparing, IsolatedPhase.Unconfirmed)
                    )
                        settle(result)
                }
            }
        scope.launch {
            if (!current()) {
                fail(Failure.Refused(RefusalReason.StaleScope))
                return@launch
            }
            when (val result = create()) {
                is Outcome.Failed ->
                    if (state.value.phase != IsolatedPhase.Cancelled) fail(result.failure)
                is Outcome.Ok -> {
                    mutable.value = state.value.copy(copy = result.value)
                    if (state.value.phase == IsolatedPhase.Cancelled) return@launch
                    if (!current()) {
                        cancel()
                        return@launch
                    }
                    mutable.value = state.value.copy(phase = IsolatedPhase.Preparing)
                    val key = ProjectRules.normalize(result.value.directory)
                    val expectedProject = result.value.projectId ?: projectId
                    val observed =
                        if (expectedProject != null) {
                            early.remove(key to expectedProject) ?: early.remove(key to null)
                        } else early.entries.firstOrNull { it.key.first == key }?.value
                    early.clear()
                    if (observed != null) settle(observed)
                    else if (result.value.ready) settle(GatewayEvent.WorktreeOutcome(true))
                    else waitAgain()
                }
            }
        }
    }

    private fun settle(event: GatewayEvent.WorktreeOutcome) {
        timer?.cancel()
        val copy = state.value.copy ?: return
        mutable.value =
            state.value.copy(
                copy =
                    copy.copy(
                        name = event.name ?: copy.name,
                        branch = event.branch ?: copy.branch,
                        ready = event.ready,
                    ),
                phase = if (event.ready) IsolatedPhase.Ready else IsolatedPhase.Failed,
                failure =
                    if (event.ready) null
                    else Failure.Runtime(RuntimeProblem.Generic, event.details),
            )
        if (!event.ready) subscription?.cancel()
    }

    fun cancel() {
        if (!state.value.canCancel) return
        timer?.cancel()
        subscription?.cancel()
        early.clear()
        mutable.value = state.value.copy(phase = IsolatedPhase.Cancelled)
    }

    fun keepWaiting() {
        if (state.value.phase != IsolatedPhase.Unconfirmed) return
        mutable.value = state.value.copy(phase = IsolatedPhase.Preparing)
        waitAgain()
    }

    private fun waitAgain() {
        timer?.cancel()
        timer = scope.launch {
            delay(45.seconds)
            if (state.value.phase == IsolatedPhase.Preparing)
                mutable.value = state.value.copy(phase = IsolatedPhase.Unconfirmed)
        }
    }

    fun open(acceptUnconfirmed: Boolean = false, acceptFailedSetup: Boolean = false) {
        val before = state.value
        val directory = before.copy?.directory ?: return
        if (
            before.phase != IsolatedPhase.Ready &&
                !(before.phase == IsolatedPhase.Unconfirmed && acceptUnconfirmed) &&
                !(before.phase == IsolatedPhase.Failed && acceptFailedSetup)
        )
            return
        if (!current()) {
            fail(Failure.Refused(RefusalReason.StaleScope))
            return
        }
        timer?.cancel()
        mutable.value =
            before.copy(
                phase = IsolatedPhase.Opening,
                failure = null,
                acceptedUnconfirmed = before.acceptedUnconfirmed || acceptUnconfirmed,
                acceptedFailedSetup = before.acceptedFailedSetup || acceptFailedSetup,
            )
        scope.launch {
            when (val result = open(directory)) {
                is Outcome.Failed ->
                    mutable.value = state.value.copy(phase = before.phase, failure = result.failure)
                is Outcome.Ok -> {
                    if (!current()) {
                        fail(Failure.Refused(RefusalReason.StaleScope))
                        return@launch
                    }
                    subscription?.cancel()
                    mutable.value = state.value.copy(phase = IsolatedPhase.Opened)
                    opened(result.value)
                }
            }
        }
    }

    private fun fail(failure: Failure) {
        timer?.cancel()
        subscription?.cancel()
        mutable.value = state.value.copy(phase = IsolatedPhase.Failed, failure = failure)
    }
}
