package io.github.eslamasabry.oc.feature.chat.tasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.chat.tasks.presentation.ChatTaskList
import io.github.eslamasabry.oc.feature.chat.tasks.presentation.ChatTaskStatus
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItem
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItemState
import io.github.eslamasabry.oc.ui.kit.state.KitTaskLines
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiTodos
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCancelled
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCompleted
import io.github.eslamasabry.oc.ui.words.res.mobileTaskInProgress
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPending
import io.github.eslamasabry.oc.ui.words.res.mobileTasksProgress
import org.jetbrains.compose.resources.stringResource

/** A single plan home, read from the latest tool's bounded payload in the conversation. */
@Composable
public fun ChatTasksSheet(
    tasks: ChatTaskList?,
    visible: Boolean,
    onDismiss: () -> Unit,
    running: Boolean = true,
) {
    KitSheet(
        visible = visible && tasks != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.chatUiTodos),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
        subtitle = tasks?.let { stringResource(Res.string.mobileTasksProgress, it.done, it.total) },
    ) {
        KitTaskLines(
            items =
                tasks?.tasks.orEmpty().map { task ->
                    KitTaskItem(
                        text = task.text,
                        state =
                            when (task.status) {
                                ChatTaskStatus.Pending -> KitTaskItemState.Todo
                                ChatTaskStatus.InProgress -> KitTaskItemState.InProgress
                                ChatTaskStatus.Completed -> KitTaskItemState.Done
                                ChatTaskStatus.Cancelled -> KitTaskItemState.Cancelled
                            },
                        label =
                            stringResource(
                                when (task.status) {
                                    ChatTaskStatus.Pending -> Res.string.mobileTaskPending
                                    ChatTaskStatus.InProgress -> Res.string.mobileTaskInProgress
                                    ChatTaskStatus.Completed -> Res.string.mobileTaskCompleted
                                    ChatTaskStatus.Cancelled -> Res.string.mobileTaskCancelled
                                }
                            ),
                    )
                },
            running = running,
            modifier = Modifier.testTag("chat-tasks"),
        )
    }
}
