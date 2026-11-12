package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.ui.kit.dock.KitDockInsets
import io.github.eslamasabry.oc.ui.kit.dock.KitEdgeDock
import io.github.eslamasabry.oc.ui.kit.dock.KitEdgeDockState
import io.github.eslamasabry.oc.ui.kit.dock.rememberKitEdgeDockState
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatHeaderRenameFailed
import io.github.eslamasabry.oc.ui.words.res.dockAnswerFailed
import io.github.eslamasabry.oc.ui.words.res.dockRemoved
import io.github.eslamasabry.oc.ui.words.res.dockStaysWhileOpen
import io.github.eslamasabry.oc.ui.words.res.dockStopFailed
import io.github.eslamasabry.oc.ui.words.res.r40RelationsUntitled
import org.jetbrains.compose.resources.stringResource

private const val NAME = "\u0000"

/**
 * The edge dock on a screen: the kit's [KitEdgeDock] fed from [port], over the screen's content, under its sheets,
 * dialogs and menus (the host places it inside the screen's own modal host). Arranges kit parts only; paints nothing.
 * It places two layers in the caller's box (the dock, then the Undo bar), so call it where the page is a sibling.
 *
 * The model is mapped once per change of the dock's flows (collected once at app scope by the port; here only read).
 * [insets] keeps the dock clear of the bars, the keyboard and the screen's own foot; [undoBottom] lifts the Undo bar
 * above that foot. [exclusion] is the platform's back-gesture exclusion for the dock's scrub zone (Android:
 * `systemGestureExclusion` with [KitEdgeDockState.gestureExclusion]); none elsewhere.
 */
@Composable
public fun EdgeDockHost(
    port: EdgeDockPort,
    modifier: Modifier = Modifier,
    insets: KitDockInsets = KitDockInsets(),
    undoBottom: Dp = 0.dp,
    exclusion: (KitEdgeDockState) -> Modifier = { Modifier },
    /** False on a screen that has its own "new conversation" control: the dock then shows no "+". */
    offersNew: Boolean = true,
    /** The room (px) the Undo bar takes while an offer stands, 0 when it goes; the screen keeps that much room clear. */
    onUndoHeight: ((Int) -> Unit)? = null,
) {
    val shown by port.shown.collectAsStateWithLifecycle()
    if (!shown) return
    val ui by port.ui.collectAsStateWithLifecycle()
    val peeks by port.peeks.collectAsStateWithLifecycle()
    val untitled = stringResource(Res.string.r40RelationsUntitled)
    val frame = remember(ui, peeks, untitled, offersNew) { DockKitMapper.map(ui, peeks, untitled, offersNew = offersNew) }
    val latest = rememberUpdatedState(frame)
    val words = rememberDockKitWords(frame.model)
    val toasts = LocalKitToasts.current
    val removedWords = rememberUpdatedState(stringResource(Res.string.dockRemoved, NAME))
    val staysWords = rememberUpdatedState(stringResource(Res.string.dockStaysWhileOpen, NAME))
    val stopFailedWords = rememberUpdatedState(stringResource(Res.string.dockStopFailed, NAME))
    val answerFailedWords = rememberUpdatedState(stringResource(Res.string.dockAnswerFailed, NAME))
    val renameFailedWords = rememberUpdatedState(stringResource(Res.string.chatHeaderRenameFailed))
    var undo by remember { mutableStateOf<Pair<String, KitUndoOffer>?>(null) }
    val router =
        remember(port, toasts) {
            DockIntentRouter(
                port,
                { latest.value },
                object : DockFeedback {
                    override fun removed(key: String, name: String) {
                        undo = key to KitUndoOffer("dock-removed-$key", removedWords.value.replace(NAME, name))
                    }

                    override fun staysWhileOpen(name: String) {
                        toasts?.show(staysWords.value.replace(NAME, name))
                    }

                    override fun stopFailed(name: String) {
                        toasts?.show(stopFailedWords.value.replace(NAME, name))
                    }

                    override fun answerFailed(name: String) {
                        toasts?.show(answerFailedWords.value.replace(NAME, name))
                    }

                    override fun renameFailed() {
                        toasts?.show(renameFailedWords.value)
                    }
                },
            )
        }
    val state = rememberKitEdgeDockState()
    // Two layers of the screen side by side, not one box: the dock's area stands beside the page, so a touch it lets
    // through (a scroll from the tucked band, a tap during an alert) reaches the page; the Undo bar's layer is above it.
    KitEdgeDock(
        model = frame.model,
        intents = router,
        words = words,
        modifier = modifier.fillMaxSize().then(exclusion(state)),
        insets = insets,
        state = state,
    )
    Box(modifier.fillMaxSize()) {
        val offer = undo
        KitUndoBar(
            offer = offer?.second,
            onUndo = {
                offer?.let { router.undoRemove(it.first) }
                undo = null
            },
            onTimeout = { undo = null },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = undoBottom),
            onHeight = onUndoHeight,
        )
    }
}
