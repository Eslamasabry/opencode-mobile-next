package io.github.eslamasabry.oc.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.nav.RowTransformGate
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotionLevel
import io.github.eslamasabry.oc.ui.shell.nav.ShellNavDisplay

/** A secondary window's theme, without the main app's graph or navigation attachment. */
@Composable
public fun StandaloneRoot(
    platform: KitPlatform,
    appearance: KitAppearance = KitAppearance.Default,
    localeTag: String? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKitPlatform provides platform) {
        if (localeTag == null) KitTheme(appearance = appearance, content = content)
        else KitTheme(appearance = appearance, localeTag = localeTag, content = content)
    }
}

/**
 * The one-entry variant reuses contributed feature entries but never attaches a global navigator.
 */
@Composable
public fun StandaloneRoot(
    start: AppRoute,
    installers: Set<EntryInstaller>,
    platform: KitPlatform,
    appearance: KitAppearance = KitAppearance.Default,
    localeTag: String? = null,
    onFinish: () -> Unit,
) {
    StandaloneRoot(platform, appearance, localeTag) {
        val provider =
            remember(installers) {
                entryProvider<NavKey> { installers.forEach { with(it) { install() } } }
            }
        ShellNavDisplay(
            backStack = listOf(start),
            gate = remember { RowTransformGate() },
            level = ShellMotionLevel.Off,
            onSystemBack = onFinish,
            baseDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = provider,
        )
    }
}
