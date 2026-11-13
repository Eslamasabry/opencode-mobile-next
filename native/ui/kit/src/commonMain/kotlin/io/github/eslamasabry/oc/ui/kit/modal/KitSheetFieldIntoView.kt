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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.withFrameNanos

/**
 * A sheet's scrolling body that fades out above pinned actions: a part it is asked to bring into view (a field's
 * caret line) must come to rest above the fade, not inside it. Asks for [fadePx] more room below the part, and
 * remembers the last part asked for, so [rememberSheetFieldIntoView] can ask again when the keyboard resizes the body.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SheetFieldIntoView(private val fadePx: () -> Float) : BringIntoViewResponder {
    var last: Rect? = null

    override fun calculateRectForParent(localRect: Rect): Rect {
        val extra = fadePx()
        return if (extra <= 0f) localRect else Rect(localRect.left, localRect.top, localRect.right, localRect.bottom + extra)
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        last = localRect() ?: return
    }
}

/** Keeps a sheet body's field above the fade: [viewport] goes before the scroll, [content] after it. */
@OptIn(ExperimentalFoundationApi::class)
internal class SheetBodyIntoView(fadePx: () -> Float) {
    internal val responder = SheetFieldIntoView(fadePx)
    internal val requester = BringIntoViewRequester()
    internal var focused by mutableStateOf(false)
    internal var height by mutableStateOf(0)

    /** Before the scroll: reports the body's height (the keyboard changes it). */
    fun viewport(): Modifier = Modifier.onSizeChanged { height = it.height }

    /** After the scroll: asks for the fade's room below a part brought into view, and tracks focus inside. */
    fun content(): Modifier =
        Modifier.bringIntoViewResponder(responder)
            .bringIntoViewRequester(requester)
            .onFocusChanged { focused = it.hasFocus }
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
    LaunchedEffect(state.height) {
        val rect = state.responder.last
        if (state.focused && rect != null) {
            // One frame: the scroll has taken the new size first, then this asks again with the fade's room.
            withFrameNanos {}
            state.requester.bringIntoView(rect)
        }
    }
    return state
}
