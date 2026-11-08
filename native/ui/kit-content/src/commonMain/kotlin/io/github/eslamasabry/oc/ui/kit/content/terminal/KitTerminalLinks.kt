package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * What a tapped terminal link offers (Dart `showKitTerminalLinkSheet`, `kit_terminal_links.dart`):
 * the sheet first, then Open (through the app's link policy) or Copy. Pure state, so the whole flow
 * is testable without a window.
 */
public class KitTerminalLinkFlow {
    /** The link the sheet shows, or null when it is closed. */
    public var sheet: String? by mutableStateOf(null)
        private set

    /** The address kept for the sheet's closing animation. */
    public var shown: String by mutableStateOf("")
        private set

    /** The policy's question before an address opens, or null. */
    public var review: TerminalLinkReview? by mutableStateOf(null)
        private set

    /** A tapped link opens the sheet with its whole address. */
    public fun tap(url: String) {
        shown = url
        sheet = url
    }

    /**
     * Open link: the sheet closes, then the address goes to [host] (Dart: pop, then
     * `openExternalLink`), which opens it or asks first.
     */
    public fun open(host: TerminalLinkHost?) {
        val url = sheet ?: return
        sheet = null
        review = host?.review(url)
    }

    /** Copy link: the sheet closes and the address, as shown, goes to [write] (never redacted). */
    public fun copy(write: (String) -> Unit) {
        val url = sheet ?: return
        sheet = null
        write(url)
    }

    public fun dismiss() {
        sheet = null
    }

    public fun confirm() {
        val selected = review
        review = null
        selected?.confirm?.invoke()
    }

    public fun cancel() {
        review = null
    }
}

/**
 * The sheet a tapped terminal link opens (Dart `showKitTerminalLinkSheet`): titled "Link in the
 * terminal", the whole address in mono, left to right and selectable, then Open link (the link
 * glyph) and Copy link (the copy glyph). Open goes through the app's link policy ([KitConfirm] when
 * the policy asks first), like every address the app did not write.
 *
 * States: closed, open, asking
 * Text: frame (header), mono (the address)
 * Cap: Sheet (640)
 * Motion: [KitSheet]'s; Off = instant
 * Replaces: showKitTerminalLinkSheet (lib/ui/kit/kit_terminal_links.dart)
 */
@Composable
public fun KitTerminalLinkSheet(flow: KitTerminalLinkFlow) {
    val host = LocalTerminalLinkHost.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    KitSheet(
        visible = flow.sheet != null,
        onDismissRequest = flow::dismiss,
        title = stringResource(Res.string.terminalLinkTitle),
        primary =
            KitAction(
                stringResource(Res.string.terminalLinkOpen),
                { flow.open(host) },
                icon = KitIcons.link,
            ),
        secondary =
            KitAction(
                stringResource(Res.string.terminalLinkCopy),
                { flow.copy { clipboard.setText(AnnotatedString(it)) } },
                icon = KitIcons.copy,
            ),
    ) {
        // An address reads left to right in every language (Dart: Directionality ltr).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                KitText(
                    flow.shown,
                    Modifier.testTag("terminal-link-url"),
                    role = TypeRole.Mono,
                )
            }
        }
    }
    KitConfirm(
        spec =
            flow.review?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.terminalLinkReviewTitle),
                    body =
                        stringResource(
                            if (it.insecure) Res.string.terminalLinkReviewInsecure
                            else Res.string.terminalLinkReviewBody,
                            it.host,
                        ),
                    confirmLabel = stringResource(Res.string.terminalLinkOpen),
                    kind = if (it.insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                )
            },
        onConfirm = flow::confirm,
        onCancel = flow::cancel,
    )
}

/**
 * A visible way to open detected addresses, also usable without hitting a terminal grid cell. A tap
 * opens the same sheet a tapped link in the grid does ([KitTerminalLinkSheet]).
 */
@Composable
public fun KitTerminalLinks(
    transcript: String,
    modifier: Modifier = Modifier,
    columns: Int = 80, // NV: the classic terminal width when the grid gives none
) {
    LocalTerminalLinkHost.current ?: return
    val urls =
        remember(transcript, columns) {
            TerminalLinks.urls(
                TerminalText.strip(transcript.takeLast(100_000)), // NV: bounded scan of the tail
                columns.coerceAtLeast(1),
            )
        }
    if (urls.isEmpty()) return
    val flow = remember { KitTerminalLinkFlow() }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        urls.forEach { url ->
            KitButton(
                label = stringResource(Res.string.terminalParityOpenAddress, shownHost(url)),
                role = KitButtonRole.Tertiary,
                onClick = { flow.tap(url) },
            )
        }
    }
    KitTerminalLinkSheet(flow)
}

/** The host a link button names: the authority only, never a path or query. */
internal fun shownHost(url: String): String =
    url.substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .take(160) // NV: label length bound
