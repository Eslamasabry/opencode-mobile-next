package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.tipBackReturnsTo
import org.jetbrains.compose.resources.stringResource

/**
 * The message box's hint right after an edge-light switch ("Reply in ‹title›"): [text] stands where "Ask ‹agent›…"
 * would, until the person types; [typed] tells the page so. Null = the usual hint.
 */
internal class ReplyHint(val text: String, val typed: () -> Unit)

internal val LocalReplyHint = compositionLocalOf<ReplyHint?> { null }

/**
 * "Back returns to ‹[leftTitle]›, where you left it." as a tip under the page's top bar while [leftTitle] is set (the
 * first three edge-light switches on this device). It never blocks: any touch or a few seconds end it, and
 * [onDismissed] hears that once. The top bar stays one composition either way.
 */
@Composable
internal fun BackNoteTip(leftTitle: String?, onDismissed: () -> Unit, bar: @Composable () -> Unit) {
    KitTip(
        message = stringResource(Res.string.tipBackReturnsTo, leftTitle ?: "·"),
        visible = leftTitle != null,
        onDismissed = onDismissed,
        anchor = bar,
    )
}
