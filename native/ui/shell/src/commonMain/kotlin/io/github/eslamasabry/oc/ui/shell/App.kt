package io.github.eslamasabry.oc.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.RouteSaving
import io.github.eslamasabry.oc.ui.shell.nav.RowTransformGate
import io.github.eslamasabry.oc.ui.shell.nav.ShellNavDisplay
import io.github.eslamasabry.oc.ui.shell.nav.motionLevelOf

/**
 * The root both apps call (plan 03 §21.1). Boot/migration gate and safe mode join with
 * :engine:entry (slice 2). [appearance] and [localeTag] are the person's stored choices (Settings ›
 * Appearance, Language), applied here so every screen follows them live; a host that passes no
 * [localeTag] gets the platform's. A host passes a tag always or never.
 */
@Composable
public fun App(
    deps: ShellDependencies,
    platform: KitPlatform,
    viewModels: MetroViewModelFactory,
    appearance: KitAppearance = KitAppearance.Default,
    localeTag: String? = null,
) {
    CompositionLocalProvider(
        LocalKitPlatform provides platform,
        LocalMetroViewModelFactory provides viewModels,
    ) {
        if (localeTag == null) {
            KitTheme(appearance = appearance) { NavShell(start = ChatsRoute.Home, deps = deps) }
        } else {
            KitTheme(appearance = appearance, localeTag = localeTag) {
                NavShell(start = ChatsRoute.Home, deps = deps)
            }
        }
    }
}

/**
 * Nav3 with a user-owned back stack, per-entry ViewModel stores and contributed entry installers
 * (plan 03 §15.4), route motion and predictive back with the row → chat transform, option A (P2;
 * plan 07 §3.2, plan 08 §5.4).
 */
@Composable
public fun NavShell(
    start: AppRoute,
    deps: ShellDependencies,
    gate: RowTransformGate = remember { RowTransformGate() },
) {
    val backStack = rememberNavBackStack(RouteSaving.configuration, start)
    DisposableEffect(backStack, gate) {
        deps.navigator.attach(backStack)
        deps.navigator.gateListener = gate::on
        onDispose {
            deps.navigator.gateListener = null
            deps.navigator.detach(backStack)
        }
    }
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }.collect { deps.navigator.publish() }
    }
    val provider =
        remember(deps.installers) {
            entryProvider<NavKey> { deps.installers.forEach { with(it) { install() } } }
        }
    val scale by deps.motion.animatorScale.collectAsState()
    val conditions = deps.statusSources.mapNotNull { it.status() }
    KitStatusScope(conditions) {
        // App-wide questions get their own modal host above every route (each KitScreen hosts its
        // own modals inside); with none contributed the tree is unchanged.
        if (deps.overlays.isEmpty()) {
            ShellNav(backStack, gate, scale, deps, provider)
        } else {
            KitSheetHost(Modifier.fillMaxSize()) {
                ShellNav(backStack, gate, scale, deps, provider)
                deps.overlays.forEach { it.Content() }
            }
        }
    }
}

@Composable
private fun ShellNav(
    backStack: List<NavKey>,
    gate: RowTransformGate,
    scale: Float,
    deps: ShellDependencies,
    provider: (NavKey) -> NavEntry<NavKey>,
) {
    ShellNavDisplay(
        backStack = backStack,
        gate = gate,
        level = motionLevelOf(scale),
        onSystemBack = deps.navigator::systemBack,
        baseDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = provider,
    )
}
