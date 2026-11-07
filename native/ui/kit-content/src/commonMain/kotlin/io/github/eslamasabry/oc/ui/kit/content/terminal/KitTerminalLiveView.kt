package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Test tag of the live form's character screen. */
public const val KIT_TERMINAL_SCREEN_TAG: String = "terminal-screen"

/** Measured to find one cell's width to the fraction. */
private const val CELL_PROBE = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"

/** A terminal screen laid out on its character grid (pure: the rows and where the cursor sits). */
public data class KitTerminalGrid(val rows: List<AnnotatedString>, val cursorRow: Int, val cursorColumn: Int) {
    public companion object {
        /**
         * The rows of [screen] (the ANSI text a session printed) at [columns] characters a row: a line longer than a
         * row continues on the next, as a terminal wraps at its edge (never at a space). The cursor is where the text
         * ends. The live session is the person's own shell: it is drawn as sent, not masked.
         */
        public fun of(screen: String, columns: Int, colors: KitColors): KitTerminalGrid {
            val cols = maxOf(1, columns)
            val rows = mutableListOf<AnnotatedString>()
            val lines = screen.replace("\r\n", "\n").split('\n').map { it.replace("\r", "") }
            for (line in lines) {
                val text = KitTerminalSpans.ansi(line, colors, { it }, fallback = null)
                if (text.isEmpty()) {
                    rows += text
                    continue
                }
                var at = 0
                while (at < text.length) {
                    rows += text.subSequence(at, minOf(text.length, at + cols))
                    at += cols
                }
            }
            val last = rows.lastOrNull()?.length ?: 0
            // A full last row puts the cursor at the start of the next one.
            return if (last >= cols) KitTerminalGrid(rows, rows.size, 0) else KitTerminalGrid(rows, maxOf(0, rows.lastIndex), last)
        }
    }
}

/** True when the window left above the keyboard is short: the key bar takes one sideways row. */
public fun kitTerminalCompactKeys(windowHeightDp: Float, keyboardDp: Float): Boolean =
    WindowClass.isShort(windowHeightDp - keyboardDp) // F: lib/ui/kit/kit_terminal_view.dart:357-358

/** A wide window on a desktop takes hardware keys only: no key bar (Dart `desktopInteractions && isWide`). */
public fun kitTerminalShowsKeyBar(hardwareKeysOnly: Boolean, windowWidthDp: Float): Boolean =
    !(hardwareKeysOnly && WindowClass.of(windowWidthDp).isWide) // F: lib/ui/kit/kit_terminal_view.dart:279-281

/**
 * The terminal's live form (Dart `KitTerminalView.live`, lib/ui/kit/kit_terminal_view.dart): a session painted in the
 * theme's colours on its character grid, with the [TerminalKeyBar] a phone keyboard lacks under it.
 *
 * Where the platform has a terminal view ([platformView] with a [LocalKitTerminalSlot], Android's Termux view) that view
 * is the grid; elsewhere (desktop, iOS, previews) the kit draws [screen], the text the session printed, on the same
 * grid: the theme's ANSI colours, a cell-wide accent cursor where the text ends, and lines wrapped at the grid's edge.
 * A screen reader hears [semanticsLabel] for the whole grid (a grid of characters cannot be read); the host offers the
 * accessible transcript.
 *
 * The key bar shows when [keys] is given, except on a wide desktop window ([hardwareKeysOnly], by default the
 * platform's): there the hardware keyboard is the input. It takes one sideways row when the window above the keyboard
 * is short. [readOnly] (not connected, or the shell ended) keeps the last screen readable and disables the keys; the
 * host says why.
 *
 * States: live, read-only, keys latched, keys compact
 * Text: reading (mono, capped at 2x, always left to right)
 * Cap: Fill
 * Motion: none of its own (the key bar's latch is the bar's)
 * Hidden: none
 * Replaces: KitTerminalView.live (lib/ui/kit/kit_terminal_view.dart)
 */
@Composable
public fun KitTerminalLiveView(
    screen: String,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    keys: TerminalKeyBarController? = null,
    onKey: (key: KitTerminalBarKey, ctrl: Boolean, alt: Boolean) -> Unit = { _, _, _ -> },
    readOnly: Boolean = false, // F: lib/ui/kit/kit_terminal_view.dart:60
    interruptKeys: Boolean = false, // F: lib/ui/kit/kit_terminal_view.dart:62
    platformView: Any? = null,
    hardwareKeysOnly: Boolean? = null,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val widthDp = window.width / density.density
    val keyboardDp = WindowInsets.ime.getBottom(density) / density.density
    val desktop = hardwareKeysOnly ?: (LocalKitPlatform.current.family == KitPlatformFamily.Desktop)
    val bar = keys?.takeIf { kitTerminalShowsKeyBar(desktop, widthDp) }
    // The room the view has is the window left above the keyboard (Dart reads the window's height less the keyboard;
    // the view's own height is that room on a terminal page, and stays true in a split window).
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedHeight
        val roomDp = if (bounded) maxHeight.value else window.height / density.density
        Column(Modifier.fillMaxSize()) {
            val slot = LocalKitTerminalSlot.current
            val grid = Modifier.weight(1f).fillMaxWidth()
            if (platformView != null && slot != null) {
                KitTerminalLive(platformView, semanticsLabel, semanticsLabel, grid)
            } else {
                KitTerminalScreen(screen, semanticsLabel, grid)
            }
            if (bar != null) {
                TerminalKeyBar(
                    controller = bar,
                    onKey = onKey,
                    compact = kitTerminalCompactKeys(roomDp, if (bounded) 0f else keyboardDp),
                    enabled = !readOnly,
                    interruptKeys = interruptKeys,
                    safeArea = true, // F: lib/ui/kit/kit_terminal_view.dart:388
                )
            }
        }
    }
}

/**
 * The kit's character grid for [screen] (see [KitTerminalLiveView]): `space2` around it (F:
 * lib/ui/kit/kit_terminal_view.dart:332), the ground behind it, one semantics node.
 */
@Composable
public fun KitTerminalScreen(screen: String, semanticsLabel: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val density = LocalDensity.current
    // The face grows with the person's text size up to 2x (A11Y-8).
    val scaled = Density(density.density, minOf(density.fontScale, FlutterKitTokens.TERMINAL_MAX_TEXT_SCALE.toFloat())) // F: lib/ui/kit/kit_terminal_view.dart:323-325
    CompositionLocalProvider(LocalDensity provides scaled, LocalLayoutDirection provides LayoutDirection.Ltr) {
        val style: TextStyle = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text1, textDirection = TextDirection.Ltr)
        val measurer = rememberTextMeasurer()
        // One cell of the monospace face, to the fraction: the grid's columns and the cursor sit on it.
        val cellWidth = remember(style, scaled) { measurer.measure(CELL_PROBE, style).size.width / CELL_PROBE.length.toFloat() }
        val pad = Space.S2.dp // F: lib/ui/kit/kit_terminal_view.dart:332
        // One node for the whole grid: a screen reader cannot read a character grid (the host offers the transcript),
        // so the rows are painted, not laid out as text nodes.
        BoxWithConstraints(modifier.clipToBounds().clearAndSetSemantics { contentDescription = semanticsLabel }) {
            val padPx = with(scaled) { pad.toPx() }
            val columns = ((with(scaled) { maxWidth.toPx() } - 2 * padPx) / cellWidth).toInt()
            val grid = remember(screen, columns, colors) { KitTerminalGrid.of(screen, columns, colors) }
            val lineHeight = with(scaled) { style.lineHeight.toPx() }
            val rows = remember(grid, style) { grid.rows.map { measurer.measure(it, style, softWrap = false, maxLines = 1) } }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        colors.accent, // F: lib/ui/kit/kit_terminal_view.dart:191 (the cursor is the accent)
                        topLeft = Offset(padPx + grid.cursorColumn * cellWidth, padPx + grid.cursorRow * lineHeight),
                        size = Size(cellWidth, lineHeight),
                    )
                    rows.forEachIndexed { i, row -> drawText(row, topLeft = Offset(padPx, padPx + i * lineHeight)) }
                },
            )
        }
    }
}
