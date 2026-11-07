package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import io.github.eslamasabry.oc.feature.chats.presentation.HomeBackExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceBackExit
import org.jetbrains.compose.resources.stringResource

/**
 * E05-S21: Back on the bare conversation list asks for a second Back before the app closes (Flutter
 * home_screen.dart `_onRootPop`). Holds Back only while [root] (the list is the whole back stack) and the hint is
 * not showing; anything opened over the list (a sheet, a dialog, a search with words in it) registers its own Back
 * after this one and closes first. While the hint shows Back is left to the system, so the second Back (a key, a
 * button or a predictive swipe) closes the app the system's own way. Returns the status line to show, or null.
 */
@Composable
internal fun homeBackExitStatus(root: Boolean): KitStatus? {
    var exit by remember { mutableStateOf(HomeBackExit.Idle) }
    KitBackGuard(enabled = root && exit.holdsBack) { exit = exit.afterBack() }
    // The window: the hint folds away after 2 s (the kit's status line animates it out), and leaving the bare list
    // (a page pushed over it) starts over, as Flutter's _clearBackExit does.
    LaunchedEffect(exit.hintShowing, root) {
        if (!exit.hintShowing) return@LaunchedEffect
        if (root) {
            // Waited on the frame clock (features don't import coroutine timers, K1); a test clock drives it too.
            val start = withFrameMillis { it }
            var now = start
            while (now - start < HomeBackExit.WINDOW_MS) now = withFrameMillis { it }
        }
        exit = exit.cleared()
    }
    if (!exit.hintShowing || !root) return null
    return KitStatus(
        kind = KitStatusKind.Info,
        message = stringResource(Res.string.e7WorkspaceBackExit),
        id = "home-shell-back-exit",
        icon = KitIcons.back,
    )
}
