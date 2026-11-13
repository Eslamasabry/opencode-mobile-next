package io.github.eslamasabry.oc.ui.shell

import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import io.github.eslamasabry.oc.ui.routes.RequestFocus
import io.github.eslamasabry.oc.ui.routes.RequestFocusSignal
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the back stack NavShell attaches (plan 03 §15.3). Calls made while detached (activity recreation) are kept,
 * latest wins, and applied on attach. Main thread only. [clock] ages the "show this request" signal (tests leave it
 * stopped, so a request never ages there).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class ShellNavigator(clock: Clock = StoppedClock) : AppNavigator {
    private var stack: MutableList<NavKey>? = null
    private var pending: ((MutableList<NavKey>) -> Unit)? = null
    private val mutableTop = MutableStateFlow<AppRoute?>(null)

    /**
     * K of the edge light plan §3: the run the last [switchTo] left, bottom first. It counts only while it lies
     * directly under the current run; any other change forgets it. Memory only: after a process death it is gone and
     * Back walks the restored stack.
     */
    private var kept: List<NavKey>? = null
    private val focus = RequestFocusSignal(clock)

    /** NavShell's row-transform gate listens here (P2): a chat push is TapOpen, an in-app Back is ButtonBack. */
    internal var gateListener: ((GateEvent) -> Unit)? = null
    override val top: StateFlow<AppRoute?> = mutableTop.asStateFlow()

    internal fun attach(backStack: MutableList<NavKey>) {
        stack = backStack
        pending?.invoke(backStack)
        pending = null
        publish()
    }

    internal fun detach(backStack: MutableList<NavKey>) {
        if (stack === backStack) stack = null
    }

    internal fun publish() {
        mutableTop.value = stack?.lastOrNull() as? AppRoute
    }

    private fun mutate(change: (MutableList<NavKey>) -> Unit) {
        focus.expireIfOld()
        val current = stack
        if (current == null) {
            pending = { s ->
                change(s)
                kept = s.keptRun()
            }
        } else {
            change(current)
            kept = current.keptRun()
        }
        publish()
    }

    override fun open(route: AppRoute): Unit = mutate { s ->
        when {
            route.motion == RouteMotion.Root -> s.replaceWith(route)
            s.lastOrNull() != route -> s.add(route)
        }
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean): Unit = mutate { s ->
        if ((s.lastOrNull() as? ChatRoute)?.ref != ref) {
            gateListener?.invoke(GateEvent.TapOpen)
            s.add(ChatRoute(ref, focusComposer))
        }
    }

    override fun backToChat(ref: ConversationRef): Unit = mutate { s ->
        if (s.size > 1 && (s[s.lastIndex - 1] as? ChatRoute)?.ref == ref) {
            gateListener?.invoke(GateEvent.ButtonBack)
            s.removeAt(s.lastIndex)
        } else if ((s.lastOrNull() as? ChatRoute)?.ref != ref) {
            gateListener?.invoke(GateEvent.TapOpen)
            s.add(ChatRoute(ref))
        }
    }

    override val requestFocus: StateFlow<RequestFocus?> = focus.state

    override fun consumeRequestFocus(seq: Long): Unit = focus.consume(seq)

    /**
     * Edge light plan v3.1 §3, with R = the topmost conversation and every page above it (its Files, Changes or Git),
     * K = [kept], B(x) = the stack below x. Entries are moved, never recreated, so a moved page keeps its saved state
     * and view model (Nav3 keys both by the entry's content key and clears them only when that key leaves the stack).
     * A run that leaves the stack (the old K) has both cleared by Nav3's pop.
     */
    override fun switchTo(target: ConversationRef, request: String?) {
        if (request == null) focus.clear() else focus.emit(target, request)
        mutate { s ->
            val runStart = s.indexOfLast { it is ChatRoute }
            val run = if (runStart < 0) emptyList() else s.subList(runStart, s.size).toList()
            val keptRun = s.keptRun()
            if (run.isNotEmpty() && (run.first() as ChatRoute).ref == target) {
                // 1: already there; pages above it close.
                if (run.size > 1) {
                    gateListener?.invoke(GateEvent.ButtonBack)
                    s.rewrite(s.subList(0, runStart + 1).toList())
                }
                return@mutate
            }
            val next =
                if (keptRun != null && (keptRun.first() as ChatRoute).ref == target) {
                    // 2: switching back: the two runs swap.
                    s.subList(0, runStart - keptRun.size).toList() + run + keptRun
                } else {
                    // 3: the old kept run, if any, is dropped; the target's run is moved up, or a new entry pushed.
                    val baseEnd =
                        when {
                            keptRun != null -> runStart - keptRun.size
                            runStart < 0 -> s.size
                            else -> runStart
                        }
                    val base = s.subList(0, baseEnd).toMutableList()
                    val found = base.runOf(target)
                    val moved = found?.let { range -> base.subList(range.first, range.last + 1).toList() }
                    base.removeRunsOf(target)
                    base + run + (moved ?: listOf(ChatRoute(target)))
                }
            gateListener?.invoke(GateEvent.TapOpen)
            s.rewrite(next)
            kept = run.takeIf { it.isNotEmpty() }
        }
    }

    override fun land(): Unit = mutate { it.replaceWith(ChatsRoute.Home) }

    override fun replaceRoot(root: AppRoute): Unit = mutate { it.replaceWith(root) }

    override fun back(): Unit = mutate { s ->
        if (s.size > 1) {
            gateListener?.invoke(GateEvent.ButtonBack)
            s.removeAt(s.lastIndex)
        }
    }

    /**
     * The whole back stack, bottom first, as the activity keeps it across a process death (the shell's own saveable
     * stack is composed behind the update gate and can come back as home). Empty while no stack is attached.
     */
    public fun snapshot(): List<AppRoute> = stack.orEmpty().filterIsInstance<AppRoute>()

    /**
     * Puts a [snapshot] back as the whole stack, without the row-transform gate (nothing is tapped). An empty list
     * changes nothing. Called once the shell is up after a process death.
     */
    public fun restoreStack(routes: List<AppRoute>) {
        if (routes.isEmpty()) return
        mutate { s ->
            s.clear()
            s.addAll(routes)
            kept = null // memory only: a put-back stack has no kept run
        }
    }

    /** NavDisplay's onBack: the shell has already told the gate whether a gesture or a key/button committed it. */
    internal fun systemBack(): Unit = mutate { s -> if (s.size > 1) s.removeAt(s.lastIndex) }

    private fun MutableList<NavKey>.replaceWith(route: AppRoute) {
        add(route)
        while (size > 1) removeAt(0)
    }

    /** [kept] while it still lies directly under the current run (K.end == R.start), else null. */
    private fun List<NavKey>.keptRun(): List<NavKey>? {
        val k = kept ?: return null
        val runStart = indexOfLast { it is ChatRoute }
        if (runStart < k.size) return null
        return k.takeIf { subList(runStart - k.size, runStart) == it }
    }

    /** Changes only the part of the stack that differs from [next], so entries below it are not touched. */
    private fun MutableList<NavKey>.rewrite(next: List<NavKey>) {
        var same = 0
        while (same < size && same < next.size && this[same] == next[same]) same++
        while (size > same) removeAt(lastIndex)
        addAll(next.subList(same, next.size))
    }
}

/** The run of [ref]'s topmost conversation in this list: its chat and the pages of that conversation right above it. */
private fun List<NavKey>.runOf(ref: ConversationRef): IntRange? {
    val start = indexOfLast { it is ChatRoute && it.ref == ref }
    if (start < 0) return null
    var end = start
    while (end + 1 < size && this[end + 1].isPageOf(ref)) end++
    return start..end
}

/** Every run of [ref] (older duplicates of the target). */
private fun MutableList<NavKey>.removeRunsOf(ref: ConversationRef) {
    while (true) {
        val range = runOf(ref) ?: return
        subList(range.first, range.last + 1).clear()
    }
}

/** The conversation's own pages that travel with it: Files, Changes (and their files) and Git. */
private fun NavKey.isPageOf(ref: ConversationRef): Boolean =
    when (this) {
        is FilesRoute.Folder -> from == ref
        is FilesRoute.File -> from == ref
        is ReviewRoute.Changes -> this.ref == ref
        is ReviewRoute.File -> this.ref == ref
        is ReviewRoute.Staged -> this.ref == ref
        is ReviewRoute.UndoFromHere -> this.ref == ref
        is GitRoute -> this.ref == ref
        else -> false
    }

/** A clock that never moves: without the app's clock a waiting request does not age. */
private object StoppedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(0)
}
