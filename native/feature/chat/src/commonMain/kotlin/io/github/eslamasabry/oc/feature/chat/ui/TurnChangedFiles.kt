package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ChangedFile
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatTurnAllFiles
import io.github.eslamasabry.oc.ui.words.res.chatTurnAllFilesMore
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** F: lib/ui/screens/chat/chat_files.dart:465 `_maxTurnFileRows`: a turn lists at most this many changed files. */
internal const val MAX_TURN_FILE_ROWS: Int = 5 // F: lib/ui/screens/chat/chat_files.dart:465

/** F: lib/ui/kit/kit_layout.dart paneDetailMaxWidth: the reply column's cap (plan 09 R13). */
private const val REPLY_COLUMN_DP: Int = 700 // F: lib/ui/kit/kit_layout.dart:48

/**
 * The files a finished turn changed, as rows under its reply, and the project's Files under them (Flutter
 * `_turnChangedFiles`, chat_files.dart:329-461, concept 9). Each row opens the file; "All files" (with how many more
 * changed past the five shown) opens the project's Files. Nothing is drawn when the turn changed nothing.
 */
@Composable
internal fun TurnChangedFiles(
    files: List<ChangedFile>,
    onOpenFile: ((ToolArtifact) -> Unit)?,
    onAllFiles: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    val shown = files.take(MAX_TURN_FILE_ROWS)
    val allFiles =
        if (files.size > shown.size)
            pluralStringResource(Res.plurals.chatTurnAllFilesMore, files.size - shown.size, files.size - shown.size)
        else stringResource(Res.string.chatTurnAllFiles)
    // The reply column (plan 09 R13): 700 dp cap, on the transcript's gutter.
    Column(
        modifier.fillMaxWidth().widthIn(max = REPLY_COLUMN_DP.dp).padding(horizontal = Space.S4.dp).testTag("chat-turn-files")
    ) {
        // F: kit_turn.dart:498 space3 from the reply's last block, plus chat_files.dart:356 `margin: top space2`; the
        // reply block above already brings space1.
        KitRowGroup(Modifier.padding(top = Space.S4.dp)) {
            for (file in shown) {
                val counts =
                    listOfNotNull(
                        file.added?.takeIf { it > 0 }?.let { "+$it" },
                        file.removed?.takeIf { it > 0 }?.let { "−$it" },
                    )
                KitRow(
                    title = file.name,
                    titleIsFileName = true,
                    leading = KitRowLeading.Glyph(KitIcons.fileText),
                    trailing =
                        if (counts.isEmpty()) KitRowTrailing.Chevron
                        else KitRowTrailing.Value(counts.joinToString(" ")),
                    onClick = onOpenFile?.let { open -> { open(ToolArtifact(file.path, null, null, file.name)) } },
                    modifier = Modifier.testTag("chat-turn-file-${file.path}"),
                )
            }
        }
        if (onAllFiles != null)
            // A quiet link under the rows (concept 9): the folder glyph and the words in the secondary tone, one
            // 48 dp target whose glyph starts on the rows' own edge (chat_files.dart:411-454).
            KitTappable(onClick = onAllFiles, onClickLabel = allFiles, modifier = Modifier.testTag("chat-turn-all-files")) {
                Row(
                    Modifier.heightIn(min = LocalKitTokens.current.minTarget.dp).padding(end = Space.S2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitIcon(KitIcons.folderOpen, size = KitIconSize.Small, tone = TextTone.Secondary)
                    Spacer(Modifier.width(Space.S2.dp))
                    KitText(allFiles, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            }
    }
}
