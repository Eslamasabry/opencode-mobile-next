package io.github.eslamasabry.oc.ui.kitchat.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/** Three dots on a 24-unit grid: the conversation menu's glyph (not mirrored; it points nowhere). */
public val KitMoreGlyph: ImageVector by lazy {
    ImageVector.Builder("kit-chat-more", 24.dp, 24.dp, 24f, 24f).apply {
        listOf(5f, 12f, 19f).forEach { x ->
            path(fill = SolidColor(Color.Black)) {
                moveTo(x + 1.75f, 12f)
                arcToRelative(1.75f, 1.75f, 0f, true, true, -3.5f, 0f)
                arcToRelative(1.75f, 1.75f, 0f, true, true, 3.5f, 0f)
                close()
            }
        }
    }.build()
}

/**
 * The conversation's ⋯ button and its one menu (P12 CHAT-HEADER; Flutter chat title bar overflow): a labelled icon
 * button that opens [items] as screen state. Choosing an item closes the menu first ([KitMenu]).
 *
 * States: closed, open
 * Text: frame
 * Motion: [KitMenu]'s
 */
@Composable
public fun KitChatMoreMenu(
    label: String,
    expanded: Boolean,
    items: List<KitMenuItem>,
    onOpen: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    KitMenu(
        expanded = expanded,
        items = items,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
    ) {
        KitIconButton(KitMoreGlyph, label, onOpen, Modifier.testTag("chat-session-menu"))
    }
}

/**
 * Puts [text] on the clipboard once per [id], then calls [onDone] (state, never a one-shot channel; ADR §9.1). The text
 * must already be masked where it is not the person's own.
 */
@Composable
public fun KitClipboardEffect(id: Long?, text: String?, onDone: (Long) -> Unit) {
    @Suppress("DEPRECATION") // the platform clip-entry API lands with KitCopy (K1); this is the kit's one call site
    val clipboard = LocalClipboardManager.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(id) {
        if (id != null && text != null) {
            clipboard.setText(AnnotatedString(text))
            done(id)
        }
    }
}

/**
 * The app's redactor as a plain mask (Flutter `KitRedact.text`) for text a feature hands to the clipboard, so a key a
 * tool printed never leaves the app; features never see the security service itself.
 */
@Composable
public fun rememberKitMask(): (String) -> String {
    val redactor = LocalKitRedactor.current
    return androidx.compose.runtime.remember(redactor) { { text: String -> redactor.redact(text) } }
}
