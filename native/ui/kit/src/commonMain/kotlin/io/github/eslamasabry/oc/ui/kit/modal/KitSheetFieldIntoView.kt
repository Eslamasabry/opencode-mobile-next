package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.withFrameNanos

/**
 * A sheet's scrolling body that fades out above pinned actions: a part it is asked to bring into view (a field's
 * caret line) must come to rest above the fade, not inside it. Asks for [fadePx] more room below the part, and
 * remembers the last part asked for, so [rememberSheetBodyIntoView] can ask again when the keyboard resizes the body.
 * Only while a text field of the kit in the body has focus ([SheetBodyIntoView.fieldFocused]); a clickable row that
 * takes focus (mouse, keyboard) never changes the layout or scrolls the body.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SheetFieldIntoView(
    private val fadePx: () -> Float,
    private val typing: () -> Boolean,
    /** The focused field's whole box in this node's space, when it fits the viewport with the fade's room; else null. */
    private val whole: () -> Rect?,
) : BringIntoViewResponder {
    var last: Rect? = null

    override fun calculateRectForParent(localRect: Rect): Rect {
        if (!typing()) return localRect
        // The caret line of a field that fits whole asks for the whole field instead.
        val box = whole()?.takeIf { it.overlaps(localRect) }
        val want = box ?: localRect
        val extra = fadePx()
        return if (extra <= 0f) want else Rect(want.left, want.top, want.right, want.bottom + extra)
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        if (typing()) last = localRect() ?: return
    }
}

/** Keeps a sheet body's field above the fade: [viewport] goes before the scroll, [content] after it. */
@OptIn(ExperimentalFoundationApi::class)
internal class SheetBodyIntoView(fadePx: () -> Float) {
    /** True while a text field of the kit inside the body has focus (the field reports it; see [LocalKitSheetTyping]). */
    internal var fieldFocused by mutableStateOf(false)
    internal val fadePx = fadePx
    internal val responder = SheetFieldIntoView(fadePx, { fieldFocused }, { wholeField() })
    internal val requester = BringIntoViewRequester()
    internal var height by mutableStateOf(0)

    /** The focused field's box, reported by the field ([sheetTypingBox]), and the body's own coordinates. */
    internal var fieldBox: LayoutCoordinates? = null
    internal var contentBox: LayoutCoordinates? = null

    /** Bumps when the focused field's size changes (typing grows it), so the body asks again. */
    internal var fieldSize by mutableStateOf(0)

    internal fun wholeField(): Rect? {
        val field = fieldBox?.takeIf { it.isAttached } ?: return null
        val content = contentBox?.takeIf { it.isAttached } ?: return null
        val rect = content.localBoundingBoxOf(field, false)
        return rect.takeIf { it.height + fadePx() <= height }
    }

    /** Before the scroll: reports the body's height (the keyboard changes it). */
    fun viewport(): Modifier = Modifier.onSizeChanged { height = it.height }

    /** After the scroll: asks for the fade's room below a part brought into view,  */
    fun content(): Modifier =
        Modifier.bringIntoViewResponder(responder)
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { contentBox = it }
}

/**
 * A sheet body's scroll column wears [SheetBodyIntoView]: a part brought into view leaves [fadePx] of room below it
 * (0 when the body has no pinned actions), and when the body's height changes while a field in it has focus (the
 * keyboard opening or closing) the field's last caret line is brought into view again.
 */
@Composable
internal fun rememberSheetBodyIntoView(fadePx: () -> Float): SheetBodyIntoView {
    val latest by rememberUpdatedState(fadePx)
    val state = remember { SheetBodyIntoView { latest() } }
    LaunchedEffect(state.height, state.fieldFocused, state.fieldSize) {
        val rect = state.wholeField() ?: state.responder.last
        if (state.fieldFocused && rect != null) {
            // One frame: the scroll has taken the new size first, then this asks again with the fade's room.
            withFrameNanos {}
            state.requester.bringIntoView(rect)
        }
    }
    return state
}

/** The sheet body's typing hook: the kit's text fields report their focus into it. Null outside a sheet body. */
internal val LocalKitSheetTyping = androidx.compose.runtime.staticCompositionLocalOf<SheetBodyIntoView?> { null }

/**
 * A kit text field wears this on its whole part (label included): while it has focus inside a sheet body it reports
 * its box and size, so the body can bring the whole field into view when it fits, rather than only the caret line.
 */
@Composable
internal fun Modifier.sheetTypingBox(): Modifier {
    val typing = LocalKitSheetTyping.current ?: return this
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mine by remember { mutableStateOf(false) }
    return this.onFocusChanged {
            mine = it.hasFocus
            if (mine) typing.fieldBox = box
        }
        .onGloballyPositioned {
            box = it
            if (mine) typing.fieldBox = it
        }
        .onSizeChanged { if (mine) typing.fieldSize = it.height }
}
