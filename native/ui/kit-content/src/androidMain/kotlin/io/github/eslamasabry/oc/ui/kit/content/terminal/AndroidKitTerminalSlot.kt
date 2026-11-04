package io.github.eslamasabry.oc.ui.kit.content.terminal

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.eslamasabry.oc.core.common.terminal.TerminalPalette
import io.github.eslamasabry.oc.core.common.terminal.TerminalSurface
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Android's live terminal slot (plan 06 §7.7 `.live`, 11 W11-08a): hosts the terminal host's view —
 * a Termux `TerminalView` over the PTY — in an `AndroidView`. The kit never names the Termux type;
 * it only needs an Android [View], so the same slot shows a local PTY now and the forked
 * `RemoteTerminalSession` view later (S5-TERMFORK).
 *
 * The view's own accessibility children are hidden
 * (`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`): together with [KitTerminalLive]'s
 * `clearAndSetSemantics`, TalkBack finds one "Terminal" node and never the grid. Sessions outlive
 * screens (17 §2.6), so a view still attached to an old parent is detached before it is hosted
 * again.
 */
public object AndroidKitTerminalSlot : KitTerminalSlot {
    @Composable
    override fun Live(platformView: Any, modifier: Modifier) {
        val colors = LocalKitColors.current
        val linkHost = LocalTerminalLinkHost.current
        var review by remember(platformView) { mutableStateOf<TerminalLinkReview?>(null) }
        val palette =
            TerminalPalette(
                listOf(
                        colors.surface3,
                        colors.text1,
                        colors.success,
                        colors.codeString,
                        colors.codeType,
                        colors.codeKeyword,
                        colors.codeType,
                        colors.text2,
                        colors.text3,
                        colors.text1,
                        colors.success,
                        colors.codeString,
                        colors.codeType,
                        colors.codeKeyword,
                        colors.codeType,
                        colors.text1,
                    )
                    .map { it.toArgb() },
                colors.text1.toArgb(),
                android.graphics.Color.TRANSPARENT,
                colors.accent.toArgb(),
            )
        DisposableEffect(platformView, linkHost) {
            val surface = platformView as? TerminalSurface
            surface?.links(linkHost?.let { host -> { url -> review = host.review(url) } })
            onDispose { surface?.links(null) }
        }
        key(platformView) {
            AndroidView(
                factory = { _ -> hostable(platformView) },
                modifier = modifier,
                update = { (it as? TerminalSurface)?.appearance(palette) },
            )
        }
        KitConfirm(
            spec =
                review?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.terminalLinkReviewTitle),
                        body =
                            stringResource(
                                if (it.insecure) Res.string.terminalLinkReviewInsecure
                                else Res.string.terminalLinkReviewBody,
                                it.host,
                            ),
                        confirmLabel = stringResource(Res.string.terminalLinkOpen),
                    )
                },
            onConfirm = {
                val selected = review
                review = null
                selected?.confirm?.invoke()
            },
            onCancel = { review = null },
        )
    }

    /** The terminal's view, detached from any earlier parent and hidden from accessibility. */
    internal fun hostable(platformView: Any): View {
        val view =
            requireNotNull(platformView as? View) {
                "KitTerminalLive: the terminal handle must carry an android.view.View"
            }
        (view.parent as? ViewGroup)?.removeView(view)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        return view
    }
}
