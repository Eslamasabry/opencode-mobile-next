package io.github.eslamasabry.oc.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.RouteSaving

/** The root both apps call (plan 03 §21.1). Boot/migration gate and safe mode join with :engine:entry (slice 2). */
@Composable
public fun App(deps: ShellDependencies, platform: KitPlatform, viewModels: MetroViewModelFactory) {
    CompositionLocalProvider(LocalKitPlatform provides platform, LocalMetroViewModelFactory provides viewModels) {
        KitTheme { NavShell(start = ChatsRoute.Home, deps = deps) }
    }
}

/**
 * Nav3 with a user-owned back stack, per-entry ViewModel stores and contributed entry installers (plan 03 §15.4).
 * Scenes and RouteMotion transitions are P2's spike; P1 proves the wiring.
 */
@Composable
public fun NavShell(start: AppRoute, deps: ShellDependencies) {
    val backStack = rememberNavBackStack(RouteSaving.configuration, start)
    DisposableEffect(backStack) {
        deps.navigator.attach(backStack)
        onDispose { deps.navigator.detach(backStack) }
    }
    LaunchedEffect(backStack) { snapshotFlow { backStack.toList() }.collect { deps.navigator.publish() } }
    val provider = remember(deps.installers) { entryProvider<NavKey> { deps.installers.forEach { with(it) { install() } } } }
    NavDisplay(
        backStack = backStack,
        onBack = { deps.navigator.back() },
        entryDecorators =
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = provider,
    )
}
