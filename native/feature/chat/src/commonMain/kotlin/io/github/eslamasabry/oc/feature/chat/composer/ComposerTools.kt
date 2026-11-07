package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiAddAnImageOrFileToThe
import io.github.eslamasabry.oc.ui.words.res.chatUiAttachFile
import io.github.eslamasabry.oc.ui.words.res.chatUiAttachedCount
import io.github.eslamasabry.oc.ui.words.res.chatUiPromptTools
import io.github.eslamasabry.oc.ui.words.res.chatUiSlashCommandsAndAgents
import io.github.eslamasabry.oc.ui.words.res.composerClearTextSubtitle
import io.github.eslamasabry.oc.ui.words.res.composerClearTextTitle
import io.github.eslamasabry.oc.ui.words.res.composerReuseSubtitle
import io.github.eslamasabry.oc.ui.words.res.composerReuseTitle
import io.github.eslamasabry.oc.ui.words.res.composerToolCommandsTitle
import io.github.eslamasabry.oc.ui.words.res.composerToolSavedSubtitle
import io.github.eslamasabry.oc.ui.words.res.composerToolsAgentPicturesOnly
import io.github.eslamasabry.oc.ui.words.res.composerToolsMore
import io.github.eslamasabry.oc.ui.words.res.composerToolsTextOnly
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.photoCameraAction
import io.github.eslamasabry.oc.ui.words.res.photoLibraryAction
import io.github.eslamasabry.oc.ui.words.res.photoLibraryDescription
import io.github.eslamasabry.oc.ui.words.res.promptStashTitle
import io.github.eslamasabry.oc.ui.words.res.tipSendLaterTitle
import io.github.eslamasabry.oc.ui.words.res.usagePrompts
import org.jetbrains.compose.resources.stringResource

/** The four places of the composer's "+" tray (Flutter `composer_tray.dart`), in Flutter's order. */
public enum class TrayItem { Photos, Camera, File, More }

/**
 * Which round buttons "+" opens (Flutter `_trayActions`): Photos and Camera where the server takes files and this
 * phone can take pictures, File where the server takes more than pictures, then More. A server that takes no files
 * has no tray: "+" opens the More list straight away, which says why there is nothing to attach.
 */
public object ComposerTrayRules {
    public fun items(
        attachmentsSupported: Boolean,
        imagesOnly: Boolean,
        filesAvailable: Boolean,
        kinds: Set<AttachKind>,
    ): List<TrayItem> {
        if (!attachmentsSupported) return emptyList()
        val media = buildList {
            if (AttachKind.Photos in kinds) add(TrayItem.Photos)
            if (AttachKind.Camera in kinds) add(TrayItem.Camera)
            if (!imagesOnly && filesAvailable) add(TrayItem.File)
        }
        return if (media.isEmpty()) emptyList() else media + TrayItem.More
    }

    /**
     * Attaching waits while a send or a file pick runs, or the draft is not back yet (Flutter `_attachBlocked`:
     * `sending || shelfBusy || (busy && !canSendWhileBusy)`; native sends while busy).
     */
    public fun attachBlocked(state: BasicComposerUi): Boolean =
        state.sending || state.pickingFile || !state.draft.ready
}

/** What the More list can run here; null hides a row the chat does not offer. */
internal class ComposerToolsChoices(
    val state: BasicComposerUi,
    val inTray: List<TrayItem>,
    val agentName: String,
    val attachCount: Int,
    val blockedReason: String?,
    val attach: (() -> Unit)?,
    val photos: (() -> Unit)?,
    val camera: (() -> Unit)?,
    val commands: (() -> Unit)?,
    val reuse: (() -> Unit)?,
    val saved: (() -> Unit)?,
    val sendLater: (() -> Unit)?,
    val clearText: (() -> Unit)?,
    /** The voice slot's own "Voice conversation" row (`KitComposerVoice.Options`); it closes the list itself. */
    val conversation: (@Composable (close: () -> Unit) -> Unit)?,
)

/**
 * The tray's More (Flutter `_openTools` / `_PromptToolsList`), most used first: attach where the tray does not
 * already offer it (or why not), commands; then the prompt shelf and the rarer tools folded under their own rows.
 * A tool this moment cannot run stays in its place and says why. A pick closes the list first, then runs.
 */
@Composable
internal fun ComposerToolsSheet(visible: Boolean, choices: ComposerToolsChoices, onDismiss: () -> Unit) {
    val run: (() -> Unit) -> Unit = { action ->
        onDismiss()
        action()
    }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.chatUiPromptTools),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(Modifier.fillMaxWidth().testTag("composer-tools-sheet")) {
            ToolsBody(choices, run, onDismiss)
        }
    }
}

@Composable
private fun ToolsBody(choices: ComposerToolsChoices, run: (() -> Unit) -> Unit, close: () -> Unit) {
    val state = choices.state
    val blocked = choices.blockedReason
    KitRowGroup {
        if (TrayItem.File !in choices.inTray) {
            val attach = choices.attach
            if (state.attachmentsSupported && !state.imagesOnly && attach != null)
                KitRow(
                    title = stringResource(Res.string.chatUiAttachFile),
                    supporting =
                        if (choices.attachCount == 0) stringResource(Res.string.chatUiAddAnImageOrFileToThe)
                        else stringResource(Res.string.chatUiAttachedCount, choices.attachCount.toString()),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.attach),
                    enabled = blocked == null,
                    disabledReason = blocked,
                    onClick = { run(attach) },
                    modifier = Modifier.testTag("composer-tool-attach"),
                )
            else
                // Flutter `KitRow.unavailable`: the reason takes the supporting line.
                KitRow(
                    title = stringResource(Res.string.chatUiAttachFile),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.attach),
                    enabled = false,
                    disabledReason =
                        if (state.imagesOnly)
                            stringResource(Res.string.composerToolsAgentPicturesOnly, choices.agentName)
                        else stringResource(Res.string.composerToolsTextOnly),
                    modifier = Modifier.testTag("composer-tool-attach"),
                )
        }
        if (state.attachmentsSupported && TrayItem.Photos !in choices.inTray)
            choices.photos?.let { photos ->
                KitRow(
                    title = stringResource(Res.string.photoLibraryAction),
                    supporting = stringResource(Res.string.photoLibraryDescription),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.images),
                    enabled = blocked == null,
                    disabledReason = blocked,
                    onClick = { run(photos) },
                    modifier = Modifier.testTag("composer-tool-gallery"),
                )
            }
        if (state.attachmentsSupported && TrayItem.Camera !in choices.inTray)
            choices.camera?.let { camera ->
                KitRow(
                    title = stringResource(Res.string.photoCameraAction),
                    leading = KitRowLeading.Glyph(KitIcons.camera),
                    enabled = blocked == null,
                    disabledReason = blocked,
                    onClick = { run(camera) },
                    modifier = Modifier.testTag("composer-tool-camera"),
                )
            }
        choices.commands?.let { commands ->
            KitRow(
                title = stringResource(Res.string.composerToolCommandsTitle),
                supporting = stringResource(Res.string.chatUiSlashCommandsAndAgents),
                supportingMaxLines = 2,
                leading = KitRowLeading.Glyph(KitIcons.run),
                onClick = { run(commands) },
                modifier = Modifier.testTag("composer-tool-commands"),
            )
        }
    }
    val reuse = choices.reuse
    val saved = choices.saved
    val later = choices.sendLater
    val clear = choices.clearText
    if (reuse != null || saved != null || later != null || clear != null) {
        var open by remember { mutableStateOf(false) }
        KitExpandRow(
            title = stringResource(Res.string.usagePrompts),
            expanded = open,
            onExpandedChange = { open = it },
            leading = KitIcons.bookmarks,
            modifier = Modifier.testTag("composer-tools-prompts"),
        ) {
            reuse?.let {
                KitRow(
                    title = stringResource(Res.string.composerReuseTitle),
                    supporting = stringResource(Res.string.composerReuseSubtitle),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.history),
                    onClick = { run(it) },
                    modifier = Modifier.testTag("composer-tool-history"),
                )
            }
            saved?.let {
                KitRow(
                    title = stringResource(Res.string.promptStashTitle),
                    supporting = stringResource(Res.string.composerToolSavedSubtitle),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.bookmarks),
                    onClick = { run(it) },
                    modifier = Modifier.testTag("composer-tool-saved"),
                )
            }
            // Native keeps Send later here as the visible twin of holding Send (Flutter offers only the hold).
            later?.let {
                KitRow(
                    title = stringResource(Res.string.tipSendLaterTitle),
                    leading = KitRowLeading.Glyph(KitIcons.clock),
                    onClick = { run(it) },
                    modifier = Modifier.testTag("composer-tool-later"),
                )
            }
            clear?.let {
                KitRow(
                    title = stringResource(Res.string.composerClearTextTitle),
                    supporting = stringResource(Res.string.composerClearTextSubtitle),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.textSnippet),
                    onClick = { run(it) },
                    modifier = Modifier.testTag("composer-tool-clear"),
                )
            }
        }
    }
    choices.conversation?.let { conversation ->
        var open by remember { mutableStateOf(false) }
        KitExpandRow(
            title = stringResource(Res.string.composerToolsMore),
            expanded = open,
            onExpandedChange = { open = it },
            leading = KitIcons.layers,
            modifier = Modifier.testTag("composer-tools-advanced"),
        ) {
            conversation(close)
        }
    }
}
