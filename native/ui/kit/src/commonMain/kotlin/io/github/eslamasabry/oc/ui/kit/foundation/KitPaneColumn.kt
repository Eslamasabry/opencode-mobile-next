package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout

/**
 * The conversation column's cap inside a pane that stands beside a list (big screens), or null everywhere else. There
 * the column (header capsule, transcript, message box: everything capped at [CapRole.Conversation] or wider and
 * centred) fills the pane between the page's gutters, up to this cap; only past it do two equal margins appear. The
 * shell provides it for the pane beside its list; a phone, and any window showing one page, never sees it and lays out
 * as before.
 */
public val LocalKitPaneColumn: ProvidableCompositionLocal<Dp?> = staticCompositionLocalOf { null }

public object KitPaneColumn {
    /** The widest the conversation's column grows in a pane beside a list, dp (the kit's widest frame cap). */
    public const val MAX_DP: Int = Layout.LIST_WIDTH

    /** The side margin of the chat's column (the message box's and the header capsule's), dp. */
    public const val SIDE_DP: Int = 20

    /** The page round that column: the column and the message box's side margin (20 dp) on each side. */
    public const val PAGE_MAX_DP: Int = MAX_DP + 2 * SIDE_DP
}

/**
 * The page in a pane beside a list (big screens): it fills the pane up to [KitPaneColumn.PAGE_MAX_DP] and is centred
 * past it, so a wider pane leaves two equal margins, never one band. Inside, the page is its own window
 * ([KitPaneWindow]) and every centred cap at least the conversation's width grows to the page ([LocalKitPaneColumn]):
 * the header, the transcript and the message box share the page's edges at every width.
 */
@androidx.compose.runtime.Composable
public fun KitPaneColumnPage(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow(
            androidx.compose.ui.Modifier.widthIn(max = KitPaneColumn.PAGE_MAX_DP.dp).fillMaxWidth().fillMaxHeight(),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalKitPaneColumn provides KitPaneColumn.PAGE_MAX_DP.dp,
                content = content,
            )
        }
    }
}

internal object PaneColumn {
    /**
     * A cap follows [LocalKitPaneColumn] when it is centred and at least the conversation's width
     * ([Layout.CONVERSATION_WIDTH]): [CapRole.Conversation], [CapRole.Reading], [CapRole.List] and the chat's own
     * column with its gutters do; every modal or small part ([CapRole.Sheet] and narrower) does not.
     */
    fun follows(maxWidth: Dp, align: CapAlign): Boolean =
        align == CapAlign.Center && maxWidth >= Layout.CONVERSATION_WIDTH.dp

    /**
     * [maxWidth] grown by what the pane's column adds to the conversation's width, so every part of the column keeps
     * the edges the parts share at the conversation's width.
     */
    fun widened(maxWidth: Dp, column: Dp): Dp = maxWidth + (column - Layout.CONVERSATION_WIDTH.dp)
}

/**
 * The conversation's width for a part that holds itself to it with a plain `widthIn` (a reply's words, a request card,
 * a reply's footer): 700 dp, or the pane's column beside a list (big screens).
 */
@androidx.compose.runtime.Composable
public fun kitConversationCap(): Dp = LocalKitPaneColumn.current ?: Layout.CONVERSATION_WIDTH.dp
