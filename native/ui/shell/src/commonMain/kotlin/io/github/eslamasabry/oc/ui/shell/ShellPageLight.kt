package io.github.eslamasabry.oc.ui.shell

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.routes.AppRoute

/**
 * The edge light on the pages that have no frame of their own for it (a conversation's Files, Changes and Git pages).
 * The shell hosts every contributed one above the page and under the app-wide overlays, and passes the route on top;
 * an implementation draws nothing for a route it has no light for.
 */
public interface ShellPageLight {
    @Composable public fun Content(top: AppRoute?)
}
