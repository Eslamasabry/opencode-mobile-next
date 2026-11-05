package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A key name is protocol data; its face and accessible name come from native string resources. */
@Immutable
public data class KitTerminalKeyChoice(val id: String, val label: String, val selected: Boolean? = null)

/**
 * The one-row bar's layout (Dart `_SingleRow`, lib/ui/kit/terminal_key_bar.dart): [KitTerminalBarKey.SingleRowShown]
 * slots share the width (each at least [TERMINAL_KEY_SINGLE_ROW_MIN_WIDTH]), whole physical pixels wide; the rest
 * follow and the row slides. The end fades while more keys wait past it.
 */
@Composable
internal fun TerminalKeyBarSingleRow(
    keys: List<KitTerminalBarKey>,
    height: Dp,
    inset: Dp,
    key: @Composable (KitTerminalBarKey) -> Unit,
) {
    BoxWithConstraints {
        val tokens = LocalKitTokens.current
        val dpr = LocalDensity.current.density
        val full =
            if (constraints.hasBoundedWidth) {
                maxWidth // F: lib/ui/kit/terminal_key_bar.dart:567-568
            } else {
                Layout.READING_WIDTH.dp // F: lib/ui/kit/terminal_key_bar.dart:569
            }
        // A key grows with the window up to a target and a half wide; where every key then fits (a tablet, a PC
        // window) none waits off the end, and the row centres without sliding.
        val widest = tokens.minTarget.dp * 1.5f // F: lib/ui/kit/terminal_key_bar.dart:573
        val share = (full - inset * 2) / KitTerminalBarKey.SingleRowShown // F: lib/ui/kit/terminal_key_bar.dart:574
        // Whole physical pixels wide (LOOK-21), as the two-row bar snaps its columns.
        val width = (floor(max(TERMINAL_KEY_SINGLE_ROW_MIN_WIDTH.dp, min(share, widest)).value * dpr) / dpr).dp // F: lib/ui/kit/terminal_key_bar.dart:575-582
        val all = width * keys.size + inset * 2 // F: lib/ui/kit/terminal_key_bar.dart:583
        if (all <= full) {
            Box(
                Modifier
                    .align(Alignment.Center) // F: lib/ui/kit/terminal_key_bar.dart:585
                    .padding(horizontal = inset),
            ) {
                Row {
                    keys.forEach { k ->
                        Box(Modifier.size(width, height)) { key(k) } // F: lib/ui/kit/terminal_key_bar.dart:591-592
                    }
                }
            }
            return@BoxWithConstraints
        }
        // The row slides; the end fades while more keys wait past it.
        val scroll = rememberScrollState()
        val more by remember { derivedStateOf { scroll.maxValue == 0 || scroll.value < scroll.maxValue - 1 } } // F: lib/ui/kit/terminal_key_bar.dart:548-552
        // A mask: only the alpha counts, so any opaque role works.
        val solid = Color(LocalKitRoles.current.ground.v) // F: lib/ui/kit/terminal_key_bar.dart:612
        val outer = full
        val edge =
            if (outer <= 0.dp) {
                0f // F: lib/ui/kit/terminal_key_bar.dart:613
            } else {
                (tokens.gutter.dp / outer).coerceIn(0f, 0.5f) // F: lib/ui/kit/terminal_key_bar.dart:613
            }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(outer)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } // F: lib/ui/kit/terminal_key_bar.dart:617-626 (ShaderMask dstIn)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                0f to solid,
                                1f - edge to solid,
                                1f to if (more) Color.Transparent else solid, // F: lib/ui/kit/terminal_key_bar.dart:620-625
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Row(
                Modifier
                    .horizontalScroll(scroll) // F: lib/ui/kit/terminal_key_bar.dart:599-601
                    .padding(horizontal = inset), // F: lib/ui/kit/terminal_key_bar.dart:602
            ) {
                keys.forEach { k ->
                    Box(Modifier.size(width, height)) { key(k) } // F: lib/ui/kit/terminal_key_bar.dart:606-607
                }
            }
        }
    }
}

/**
 * The single terminal key row: minimum kit targets, one shared horizontal scroll, labelled modifier
 * selection.
 */
@Composable
public fun KitTerminalKeybar(
    keys: List<KitTerminalKeyChoice>,
    enabled: KitEnabled,
    onKey: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp), // F: lib/ui/kit/terminal_key_bar.dart:347 (space1 between keys)
    ) {
        keys.forEach { key ->
            KitButton(
                label = key.label,
                onClick = { onKey(key.id) },
                modifier = Modifier.semantics { key.selected?.let { selected = it } },
                role =
                    if (key.selected == true) KitButtonRole.Secondary else KitButtonRole.Tertiary,
                enabled = enabled,
            )
        }
    }
}
