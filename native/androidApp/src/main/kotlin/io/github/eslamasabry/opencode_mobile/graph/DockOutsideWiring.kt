package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.android.notifications.R as NotifyR
import io.github.eslamasabry.oc.android.notifications.StatusAction
import io.github.eslamasabry.oc.android.notifications.StatusContent
import io.github.eslamasabry.oc.android.notifications.StatusKind
import io.github.eslamasabry.oc.engine.attention.dock.DockOutsideWords
import io.github.eslamasabry.oc.engine.attention.dock.OutsideKind
import io.github.eslamasabry.oc.engine.attention.dock.OutsideRow

/** [DockOutsideWords] from the notifications module's resources, in the app language (same path as every other row). */
internal class AndroidDockWords(private val words: NotifyWords) : DockOutsideWords {
    override fun untitled() = words.text(NotifyR.string.notify_dock_untitled)
    override fun helperGeneric() = words.text(NotifyR.string.notify_dock_helper_generic)
    override fun needsYouNamed(name: String) = words.text(NotifyR.string.notify_dock_name_needs_you, name)
    override fun needsYouCount(count: Int) = words.plural(NotifyR.plurals.notify_status_needs_you, count, count)
    override fun waitingForAnswer() = words.text(NotifyR.string.notify_dock_waiting_answer)
    override fun othersWorking(count: Int) = words.plural(NotifyR.plurals.notify_dock_others_working, count, count)
    override fun helperAsking(child: String) = words.text(NotifyR.string.notify_dock_helper_asking, child)
    override fun workingOn(name: String) = words.text(NotifyR.string.notify_dock_working_on, name)
    override fun conversationsWorking(count: Int) = words.text(NotifyR.string.notify_status_chats_working, count)
    override fun tasks(done: Int, total: Int) = words.text(NotifyR.string.notify_status_tasks_progress, done, total)
    override fun stateWorking() = words.text(NotifyR.string.notify_dock_state_working)
    override fun stateThinking() = words.text(NotifyR.string.notify_dock_state_thinking)
    override fun stateNeedsYou() = words.text(NotifyR.string.notify_dock_state_needs_you)
    override fun stateDone() = words.text(NotifyR.string.notify_dock_state_done)
    override fun stateFailed() = words.text(NotifyR.string.notify_dock_state_failed)
    override fun helpers(count: Int) = words.plural(NotifyR.plurals.notify_dock_helpers, count, count)
    override fun more(count: Int) = words.text(NotifyR.string.notify_status_more, count)
    override fun lockWorking(count: Int) = words.text(NotifyR.string.notify_status_lock_working, count)
    override fun onServer(server: String) = words.text(NotifyR.string.notify_status_on_server, server)
    override fun openNamed(name: String) = words.text(NotifyR.string.notify_status_action_open_named, name)
    override fun stopNamed(name: String) = words.text(NotifyR.string.notify_status_action_stop_named, name)
}

/**
 * The row as the one status notification's content. The turn id carries the row's pulse count, so the delete intent
 * (a swipe away) tells the runtime exactly which count was dismissed. Buttons: Open {name}, Stop {name} only with one
 * working conversation; the third slot (Pause background) is added by the runtime.
 */
internal fun OutsideRow.toStatusContent(): StatusContent =
    StatusContent.of(
        kind =
            when (kind) {
                OutsideKind.NeedsYou -> StatusKind.NeedsYou
                OutsideKind.Working -> StatusKind.Working
                OutsideKind.SeveralWorking -> StatusKind.SeveralWorking
            },
        title = title,
        text = text,
        lines = lines,
        subText = subText,
        progress = progress,
        actions = listOfNotNull(StatusAction.Open, StatusAction.StopReply.takeIf { stop != null }),
        conversation = open.ref,
        turnId = "$TURN_PREFIX$pulses",
        publicTitle = publicTitle,
        count = count,
        openLabel = open.label,
        stopConversation = stop?.ref,
        stopLabel = stop?.label.orEmpty(),
    ) ?: StatusContent(StatusKind.Working, "OpenCode", publicTitle = publicTitle)

internal const val TURN_PREFIX = "dock:"

/** The pulse count a row's turn id carries (see [toStatusContent]); null for any other turn id. */
internal fun dockPulsesOf(turnId: String?): Int? =
    turnId?.takeIf { it.startsWith(TURN_PREFIX) }?.removePrefix(TURN_PREFIX)?.toIntOrNull()
