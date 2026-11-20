package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout

/**
 * True inside a pane that stands beside a list (big screens, decision (a)): the page's capped columns (a centred cap at
 * least as wide as the conversation's, [CapsAtStart.follows]: the conversation, reading and list columns, the chat's
 * transcript and message box) sit at the pane's start, right after the page's gutter, and the room the cap leaves goes
 * to the end side (mirrored in right-to-left), instead of half on each side. Narrower centred parts (a state, a dialog,
 * a sheet, a control) stay centred. False everywhere else, so a window that is one page (every phone) lays out as
 * before. The shell provides it for the pane beside its list.
 */
public val LocalKitCapsAtStart: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

internal object CapsAtStart {
    /**
     * A cap follows [LocalKitCapsAtStart] when it is centred and at least the conversation's width
     * ([Layout.CONVERSATION_WIDTH]): [CapRole.Conversation], [CapRole.Reading] and [CapRole.List] are, and every modal
     * or small part ([CapRole.Sheet] and narrower) is not.
     */
    fun follows(maxWidth: Dp, align: CapAlign): Boolean = align == CapAlign.Center && maxWidth >= Layout.CONVERSATION_WIDTH.dp
}
