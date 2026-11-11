package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact
import io.github.eslamasabry.oc.feature.chat.world.ToolFileOutputs
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiAttachToPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiFileSaved
import io.github.eslamasabry.oc.ui.words.res.chatUiGeneratedFile
import io.github.eslamasabry.oc.ui.words.res.chatUiSaveFile
import io.github.eslamasabry.oc.ui.words.res.filesSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * C7 (Flutter `_attachToolOutputFile`, `_downloadToolOutputFile`): a file the agent produced as inline content can be
 * attached to the next prompt or saved to the phone. Existing kit parts only.
 */
@Composable
internal fun ToolFileSheet(
    file: ToolArtifact?,
    saver: ToolFileOutputs?,
    onAttach: ((ToolArtifact) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val generic = stringResource(Res.string.chatUiGeneratedFile)
    val name = file?.displayName ?: generic
    var result by remember(file) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(Res.string.chatUiFileSaved, name)
    val failed = stringResource(Res.string.filesSaveFailed)
    KitSheet(
        visible = file != null,
        onDismissRequest = onDismiss,
        title = name,
        closeLabel = stringResource(Res.string.kitSheetDismiss),
    ) {
        if (file == null) return@KitSheet
        when (result) {
            true -> KitNotice(message = saved)
            false -> KitNotice(message = failed)
            null -> Unit
        }
        if (onAttach != null)
            KitRow(
                title = stringResource(Res.string.chatUiAttachToPrompt),
                onClick = {
                    onAttach(file)
                    onDismiss()
                },
                modifier = Modifier.testTag("chat-file-attach"),
            )
        val url = file.url
        if (saver != null && url != null)
            KitRow(
                title = stringResource(Res.string.chatUiSaveFile, name),
                onClick = {
                    scope.launch {
                        when (val saveResult = saver.save(name, file.mime, url)) {
                            is Outcome.Ok -> if (saveResult.value) result = true
                            is Outcome.Failed -> result = false
                        }
                    }
                },
                modifier = Modifier.testTag("chat-file-save"),
            )
    }
}
