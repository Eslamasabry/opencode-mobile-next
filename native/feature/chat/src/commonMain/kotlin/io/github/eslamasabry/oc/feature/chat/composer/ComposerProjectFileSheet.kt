package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ProjectFilePick
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kitchat.files.KitProjectFileSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.mentionNoMatchWhat
import io.github.eslamasabry.oc.ui.words.res.mentionProjectFolder
import io.github.eslamasabry.oc.ui.words.res.mentionSearchFailedMessage
import io.github.eslamasabry.oc.ui.words.res.mentionSearchFailedTitle
import io.github.eslamasabry.oc.ui.words.res.mentionSearchHint
import io.github.eslamasabry.oc.ui.words.res.mentionSearchLabel
import io.github.eslamasabry.oc.ui.words.res.mentionSheetTitle
import io.github.eslamasabry.oc.ui.words.res.refreshRetry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * S28 story 12 (Flutter `showKitProjectFileSheet` / `KitProjectFileList`): "File from the project" opens the project's
 * files over the whole window; typing searches, a tap on a file picks it ([onPick] writes `@path` and stages it).
 */
@Composable
internal fun ComposerProjectFileSheet(
    visible: Boolean,
    search: suspend (String) -> Outcome<List<String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    KitProjectFileSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.mentionSheetTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        // A new list each time the sheet opens (the words start empty, as in Flutter).
        val pick = remember { ProjectFilePick(search) }
        val state by pick.state.collectAsState()
        val scope = rememberCoroutineScope()
        val field = rememberTextFieldState()
        Column(
            Modifier.fillMaxWidth().testTag("project-file-sheet"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitSearchField(
                label = stringResource(Res.string.mentionSearchLabel),
                onSearch = { words -> scope.launch { pick.run(words) } },
                state = field,
                resultCount = if (state.query.isEmpty() || state.failed) null else state.paths.size,
                modifier = Modifier.testTag("project-file-search"),
            )
            when {
                state.failed ->
                    KitErrorNotice(
                        stringResource(Res.string.mentionSearchFailedMessage),
                        title = stringResource(Res.string.mentionSearchFailedTitle),
                        retry =
                            KitLineAction(
                                stringResource(Res.string.refreshRetry),
                                { scope.launch { pick.run(field.text.toString()) } },
                                testTag = "project-file-retry",
                            ),
                        modifier = Modifier.testTag("project-file-error"),
                    )
                state.query.isEmpty() ->
                    KitText(
                        stringResource(Res.string.mentionSearchHint),
                        Modifier.testTag("project-file-hint"),
                        role = TypeRole.Secondary,
                    )
                state.paths.isEmpty() && !state.searching ->
                    KitSearchNoMatch(
                        query = state.query,
                        what = stringResource(Res.string.mentionNoMatchWhat),
                        onClear = {
                            field.clearText()
                            scope.launch { pick.run("") }
                        },
                    )
                else ->
                    KitRowGroup {
                        val folder = stringResource(Res.string.mentionProjectFolder)
                        state.paths.forEach { path ->
                            KitRow(
                                title = path.substringAfterLast('/'),
                                supporting =
                                    if ('/' in path) KitBidi.ltr(path.substringBeforeLast('/')) else folder,
                                leading = KitRowLeading.Glyph(KitIcons.fileText),
                                titleIsFileName = true,
                                titleMaxLines = 2,
                                onClick = { onPick(path) },
                                modifier = Modifier.testTag("project-file-$path"),
                            )
                        }
                    }
            }
        }
    }
}
