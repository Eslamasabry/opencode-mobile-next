package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

/**
 * The live terminal's platform slot (plan 06 §7.7 `.live`, ADR §7.3 item 3): draws the platform view a terminal host
 * made (on Android a Termux `TerminalView`) from the `native` value of its `PlatformViewHandle` (`:core:common`). Only
 * kit `androidMain` casts that value; the Android app root provides `AndroidKitTerminalSlot` through [LocalKitTerminalSlot]. Desktop and
 * iOS provide none: there the live grid draws nothing and accessible mode is the only view.
 */
@Stable
public interface KitTerminalSlot {
    @Composable
    public fun Live(platformView: Any, modifier: Modifier)
}

/** The platform's live terminal slot; null where the platform has no terminal view. */
public val LocalKitTerminalSlot: ProvidableCompositionLocal<KitTerminalSlot?> = staticCompositionLocalOf { null }

/** Test tag of the live grid's box (the only node the grid has in the semantics tree). */
public const val KIT_TERMINAL_LIVE_TAG: String = "terminal-live"

/**
 * The live character grid ([platformView] = `PlatformViewHandle.native`; kit-content does not see `:core:common`
 * yet, S3-TERMMIN report). A screen reader cannot read a grid, so the whole box is one node: [terminalLabel] as its
 * description and [title] as its state (`clearAndSetSemantics`); no grid cell reaches the semantics tree. The platform
 * slot also hides the view's own accessibility children (06 §7.7 "The grid").
 */
@Composable
public fun KitTerminalLive(platformView: Any, title: String, terminalLabel: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .testTag(KIT_TERMINAL_LIVE_TAG)
            .clearAndSetSemantics {
                contentDescription = terminalLabel
                stateDescription = title
            },
    ) {
        LocalKitTerminalSlot.current?.Live(platformView, Modifier.fillMaxSize())
    }
}
