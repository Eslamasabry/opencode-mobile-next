package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

/** A platform kit part for the desktop OS menu. Labels come from the native word resources. */
@Composable
public fun FrameWindowScope.KitDesktopSettingsMenu(
    settingsLabel: String,
    updatesLabel: String,
    onUpdates: () -> Unit,
) {
    MenuBar {
        Menu(settingsLabel) { Item(updatesLabel, onClick = onUpdates) }
    }
}
