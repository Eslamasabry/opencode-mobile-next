package io.github.eslamasabry.oc.ui.kitchat.header

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.coroutines.delay

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
 *
 * [announcement] (Flutter `KitCopy.copy(announcement:)`): said once after the write ([KitCopyAnnouncement]). The caller
 * words it, and says so when the redactor hid something (E15-S05).
 */
@Composable
public fun KitClipboardEffect(id: Long?, text: String?, onDone: (Long) -> Unit, announcement: String? = null) {
    @Suppress("DEPRECATION") // the platform clip-entry API lands with KitCopy (K1); this is the kit's one call site
    val clipboard = LocalClipboardManager.current
    val done by rememberUpdatedState(onDone)
    val said by rememberUpdatedState(announcement)
    var copied by remember { mutableStateOf<Long?>(null) }
    var words by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id != null && text != null) {
            clipboard.setText(AnnotatedString(text))
            words = said
            copied = id
            done(id)
        }
    }
    if (announcement != null) KitCopyAnnouncement(copied, words)
}

/**
 * The single "copied" announcement (Flutter `KitCopy.copy`: one screen-reader announcement, never a SnackBar): a polite
 * live region that says [words] once each time [id] changes to a new copy, and is quiet again after the copied hold.
 * Compose it before the first copy (an always-present node whose words change is what TalkBack announces).
 */
@Composable
public fun KitCopyAnnouncement(id: Long?, words: String?) {
    var shown by remember { mutableStateOf<String?>(null) }
    val said by rememberUpdatedState(words)
    LaunchedEffect(id) {
        if (id != null && said != null) {
            shown = said
            delay(MotionTokens.COPIED_HOLD.toLong())
            shown = null
        }
    }
    Box(
        Modifier.size(1.dp).testTag("kit-copy-announcement").semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = shown.orEmpty()
        }
    )
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
