package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItem
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItemState
import io.github.eslamasabry.oc.ui.kit.state.KitTaskLines
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCancelled
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCompleted
import io.github.eslamasabry.oc.ui.words.res.mobileTaskInProgress
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPending
import io.github.eslamasabry.oc.ui.words.res.mobileTasksProgress
import io.github.eslamasabry.oc.ui.words.res.workTitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One task of the list [KitTaskSheet] shows: its words and its state (Dart `KitTaskItem`). */
@Immutable
public data class KitTaskSheetItem(val text: String, val state: KitTaskItemState)

/** The rules of Dart `showKitTaskSheet` that do not draw (`lib/ui/kit/kit_task_sheet.dart`). */
public object KitTaskSheetRules {
    /** The sheet's key when the caller gives none (Dart `const Key('task-sheet')`). */
    public const val SHEET_TAG: String = "task-sheet"

    /** The words a screen reader hears for a task's mark (Dart `_TaskLines` label switch). */
    public fun label(state: KitTaskItemState): StringResource =
        when (state) {
            KitTaskItemState.Todo -> Res.string.mobileTaskPending
            KitTaskItemState.InProgress -> Res.string.mobileTaskInProgress
            KitTaskItemState.Done -> Res.string.mobileTaskCompleted
            KitTaskItemState.Cancelled -> Res.string.mobileTaskCancelled
        }

    /**
     * The mark a task shows: the one in progress keeps its words and its bold but shows a still
     * ring when the agent is not [running] (a spinner that never stops would say work goes on).
     */
    public fun markState(state: KitTaskItemState, running: Boolean): KitTaskItemState =
        if (state == KitTaskItemState.InProgress && !running) KitTaskItemState.Todo else state
}

/**
 * The agent's whole task list in a sheet titled "Tasks", with "5 of 6 done" under it (Dart
 * `showKitTaskSheet`): a done task is a check in muted words, the task in progress a spinner ring in
 * bold, a task still to do an empty ring. Opened from the task chip. [done] and [total] are the
 * tracked counts (cancelled tasks are in neither). When the agent is not [running], the task in
 * progress keeps its words and bold but shows a still ring.
 *
 * States: open, closed; per task todo, in progress (running or still), done, cancelled
 * Text: frame (header), reading (task words)
 * Cap: Sheet (640)
 * Motion: [KitSheet]'s; the marks cross-fade QUICK; Off = instant
 * Replaces: showKitTaskSheet, KitTaskItem, KitTaskItemState (lib/ui/kit/kit_task_sheet.dart)
 */
@Composable
public fun KitTaskSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    items: List<KitTaskSheetItem>,
    done: Int,
    total: Int,
    running: Boolean = true,
    sheetTag: String = KitTaskSheetRules.SHEET_TAG,
) {
    val lines =
        items.map { KitTaskItem(it.text, it.state, stringResource(KitTaskSheetRules.label(it.state))) }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.workTitle),
        subtitle = stringResource(Res.string.mobileTasksProgress, done, total),
    ) {
        KitTaskLines(lines, Modifier.testTag(sheetTag), running = running)
    }
}
