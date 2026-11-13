package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The find bar (Dart `_findBar`): the field, Previous, Next and Close; the count under them once there is a query. */
@Composable
internal fun ViewerFindBar(
    query: String,
    count: String,
    hasMarks: Boolean,
    onQuery: (String) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val field = rememberTextFieldState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val close = {
        field.edit { replace(0, length, "") }
        // Closing gives the keyboard back.
        focusManager.clearFocus()
        keyboard?.hide()
        onClose()
    }
    Column(Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, top = Space.S2.dp, end = Space.S2.dp, bottom = Space.S2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitSearchField(
                stringResource(Res.string.kitViewerFind),
                onQuery,
                Modifier.weight(1f).testTag(KitViewerKeys.FIND),
                state = field,
                onSubmit = { onStep(1) },
                // Find takes focus as it opens, so the keyboard comes up with it.
                autoFocus = true,
            )
            Spacer(Modifier.width(Space.S1.dp))
            // Up and down: never mirrored.
            val off = KitEnabled.Disabled(stringResource(Res.string.kitViewerFindNone))
            KitIconButton(
                KitIcons.chevronUp, stringResource(Res.string.kitViewerFindPrevious), { onStep(-1) },
                Modifier.testTag(KitViewerKeys.FIND_PREVIOUS), enabled = if (hasMarks) KitEnabled.Enabled else off, shortcut = "Shift+Enter",
            )
            KitIconButton(
                KitIcons.chevronDown, stringResource(Res.string.kitViewerFindNext), { onStep(1) },
                Modifier.testTag(KitViewerKeys.FIND_NEXT), enabled = if (hasMarks) KitEnabled.Enabled else off, shortcut = "Enter",
            )
            KitIconButton(
                KitIcons.close, stringResource(Res.string.kitViewerFindClose), { close() },
                Modifier.testTag(KitViewerKeys.FIND_CLOSE), shortcut = "Esc",
            )
        }
        // One Back closes Find, whatever is typed (the field's own clear button clears). Registered after the field's own
        // Back, and again with each keystroke (the field re-registers its own then), so it stays the one that answers.
        androidx.compose.runtime.key(field.text.toString()) { io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard(enabled = true, onBack = close) }
        if (query.isNotEmpty()) {
            KitText(
                count,
                Modifier.padding(top = Space.S2.dp).testTag(KitViewerKeys.FIND_COUNT),
                role = TypeRole.Secondary,
                tabular = true,
            )
        }
    }
}

