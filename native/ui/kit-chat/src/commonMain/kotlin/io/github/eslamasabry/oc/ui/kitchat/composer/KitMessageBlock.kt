package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible

/**
 * Where a conversation's message block stands, for a part that floats over the page and has to keep clear of it (the
 * edge light sits just above the block). The block is the message box with its phrase chips and whatever stands over
 * the box (the status strip, a limit note, an attachment failure): the top of all of it.
 *
 * It travels the way the kit's bottom clearance does (`KitBottomInset`): a holder provided from above the page
 * ([KitProvideMessageBlock]) and read by name, never a second channel. The message box writes it ([Modifier.kitReportMessageBlock])
 * and clears it when it leaves, so a page without a box (Files, Git, Home) reads "none". The keyboard is the kit's one
 * value, `kitKeyboardVisible()`, copied here only so a reader outside the page's own window insets sees the same answer.
 */
@Stable
public class KitMessageBlock {
    /** The top edge of the block in root pixels; NaN while no message box is on screen. */
    public var topPx: Float by mutableFloatStateOf(Float.NaN)
        internal set

    /** Whether the keyboard is up (the kit's `kitKeyboardVisible()` at the box). */
    public var keyboardVisible: Boolean by mutableStateOf(false)
        internal set

    /** Whether a message box is on screen at all. */
    public val present: Boolean get() = !topPx.isNaN()

    /** The block's top in dp from the root's top; null while none is on screen. */
    public fun topDp(density: Float): Dp? = if (present && density > 0f) Dp(topPx / density) else null

    /**
     * How far (dp) the block's top stands above the bottom of a root that is [rootHeightPx] tall; null while none is
     * on screen. The light's bottom edge is placed [gap] above this.
     */
    public fun heightAboveBottomDp(rootHeightPx: Float, density: Float): Dp? =
        if (present && density > 0f) Dp((rootHeightPx - topPx).coerceAtLeast(0f) / density) else null
}

private val LocalKitMessageBlock = staticCompositionLocalOf<KitMessageBlock?> { null }

/** Provides [block] to every page under it; the message box reports into it. Cap: wrapper */
@Composable
public fun KitProvideMessageBlock(block: KitMessageBlock, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitMessageBlock provides block, content = content)
}

/**
 * Put this on the column that holds the message box and its chips: it writes the column's top into the holder from
 * [KitProvideMessageBlock] (nothing happens outside one) and clears it when the column leaves.
 */
@Composable
public fun Modifier.kitReportMessageBlock(): Modifier {
    val block = LocalKitMessageBlock.current ?: return this
    val keyboard = kitKeyboardVisible()
    SideEffectKeyboard(block, keyboard)
    DisposableEffect(block) {
        onDispose {
            block.topPx = Float.NaN
            block.keyboardVisible = false
        }
    }
    return onGloballyPositioned { c -> block.topPx = c.positionInRoot().y }
}

@Composable
private fun SideEffectKeyboard(block: KitMessageBlock, keyboard: Boolean) {
    androidx.compose.runtime.SideEffect { if (block.keyboardVisible != keyboard) block.keyboardVisible = keyboard }
}
