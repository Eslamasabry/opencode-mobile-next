package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/**
 * The one status line per window (plan 06 §7.3 `KitStatusSlot`; Dart `KitStatusLineSlot` + `KitStatusScope` +
 * `KitStatusContribution`). The shell owns the slot of a window; a screen with no slot above it (a pushed page) owns
 * one; a screen with a slot above it (a tab body, a pane) contributes its status instead of drawing.
 */
@Stable
public class KitStatusSlotState internal constructor() {
    private class Entry(val owner: Any, var status: KitStatus?)

    private val entries = mutableStateListOf<Entry>()

    internal fun put(owner: Any, status: KitStatus?) {
        val i = entries.indexOfFirst { it.owner === owner }
        if (i < 0) entries += Entry(owner, status) else if (entries[i].status != status) entries[i] = Entry(owner, status)
    }

    internal fun remove(owner: Any) {
        entries.removeAll { it.owner === owner }
    }

    /** Contributions in registration order. */
    internal val contributions: List<KitStatus?> get() = entries.map { it.status }
}

/** App-wide conditions (connection, Android stopped the app, heat, update ready), provided once above the screens. */
public val LocalKitStatusConditions: ProvidableCompositionLocal<List<KitStatus>> = staticCompositionLocalOf { emptyList() }

internal val LocalKitStatusSlot: ProvidableCompositionLocal<KitStatusSlotState?> = staticCompositionLocalOf { null }

/** Provides the app-wide [conditions] to every slot below (the shell, once). */
@Composable
public fun KitStatusScope(conditions: List<KitStatus>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitStatusConditions provides conditions, content = content)
}

/**
 * The window's one status slot: it picks the highest of the app-wide conditions, this slot's own [status] and every
 * contribution from below (ties keep the first: app-wide, then own, then contributions in order) and hands it to
 * [content], which draws it with [KitStatusLineFold] where its frame puts the line (under the bar). Contributors
 * live inside [content].
 *
 * States: condition, empty
 * Text: reading
 * Cap: Reading
 * Motion: see [KitStatusLineFold]
 * Hidden: none
 * Replaces: KitStatusLineSlot (lib/ui/kit/kit_status_slot.dart)
 */
@Composable
public fun KitStatusSlot(status: KitStatus?, content: @Composable (shown: KitStatus?) -> Unit) {
    val state = remember { KitStatusSlotState() }
    val conditions = LocalKitStatusConditions.current
    CompositionLocalProvider(LocalKitStatusSlot provides state) {
        content(KitStatus.highest(conditions + status + state.contributions))
    }
}

/**
 * The slot's line: folds in (STANDARD on Enter) when a condition appears and out (QUICK) when it goes; instant under
 * reduced motion. Nothing to show → nothing drawn.
 *
 * States: condition, empty
 * Text: reading
 * Cap: Reading
 * Motion: fold in STANDARD/Enter, out QUICK; Off = instant
 * Hidden: none
 * Replaces: KitReveal around KitStatusLine (lib/ui/kit/kit_status_slot.dart)
 */
@Composable
public fun KitStatusLineFold(shown: KitStatus?, modifier: Modifier = Modifier) {
    val specs = KitMotion.specs
    val reduced = specs.state.reduced
    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
        exit = if (reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
    ) {
        // Keeps the last condition drawn while it folds away.
        val last = remember { mutableStateOf(shown) }
        if (shown != null) last.value = shown
        last.value?.let { KitStatusLine(it) }
    }
}

/** True when a slot is above: a screen then contributes instead of drawing. */
@Composable
public fun kitStatusSlotAbove(): Boolean = LocalKitStatusSlot.current != null

/**
 * Contributes [status] (null = nothing) to the nearest slot above for as long as it is composed. Used by `KitScreen`;
 * public for parts that are not screens (a composer's risky-switch line). Without a slot above it does nothing.
 */
@Composable
public fun KitStatusContribution(status: KitStatus?) {
    val slot = LocalKitStatusSlot.current ?: return
    val owner = remember { Any() }
    val current by rememberUpdatedState(status)
    SideEffect { slot.put(owner, current) }
    DisposableEffect(slot) { onDispose { slot.remove(owner) } }
}
