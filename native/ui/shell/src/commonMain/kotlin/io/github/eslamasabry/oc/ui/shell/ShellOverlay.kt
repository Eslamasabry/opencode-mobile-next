package io.github.eslamasabry.oc.ui.shell

import androidx.compose.runtime.Composable

/**
 * One app-wide question the person answers wherever they are (Flutter asks the phone server's
 * first-start questions on top of any screen). It draws only kit modals (KitConfirm, KitSheet),
 * hosted above every route, and nothing while it has nothing to ask.
 */
public interface ShellOverlay {
    @Composable public fun Content()
}
