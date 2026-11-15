package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible
import kotlinx.coroutines.delay

/**
 * A notice with its ways forward on a scrolling page (issue 147, device finding: with the keyboard up a notice showed one
 * line and none of its actions). When the notice appears, and again once the keyboard has settled, the page scrolls to
 * bring it whole into view; when the room cannot hold the notice and its actions together, the actions win, because they
 * are the way out. [content] receives a modifier to put on the block of actions.
 *
 * States: shown, keyboard up, keyboard down
 * Text: none (content decides)
 * Cap: none
 * Motion: the page's own scroll; Off = instant
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
public fun KitNoticeInView(key: Any?, content: @Composable (ways: Modifier) -> Unit) {
    val whole = remember { BringIntoViewRequester() }
    val ways = remember { BringIntoViewRequester() }
    val keyboard = kitKeyboardVisible()
    LaunchedEffect(key, keyboard) {
        withFrameNanos {}
        whole.bringIntoView()
        ways.bringIntoView()
        // Again once the keyboard has finished sliding: the room changes while it moves.
        delay(400)
        whole.bringIntoView()
        ways.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(whole), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        content(Modifier.bringIntoViewRequester(ways))
    }
}
