package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.KitLtr
import io.github.eslamasabry.oc.ui.kit.KitSelectable
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.animateKitValue
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTerminalViewShowingLast
import io.github.eslamasabry.oc.ui.words.res.kitTerminalViewShowingLast__eq1
import io.github.eslamasabry.oc.ui.words.res.terminalCopiedToClipboard
import io.github.eslamasabry.oc.ui.words.res.terminalOpenFull
import io.github.eslamasabry.oc.ui.words.res.terminalShowEarlier
import io.github.eslamasabry.oc.ui.words.res.terminalShowEarlier__eq1
import kotlin.math.truncate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Test tags of the terminal parts (the accessible-mode switch keeps Dart's key, plan 06 §7.7). */
public object KitTerminalTags {
    public const val ACCESSIBLE_MODE: String = "terminal-accessible-mode"
    public const val TRANSCRIPT: String = "terminal-accessible-transcript"
    public const val INPUT: String = "terminal-accessible-input"
    public const val SEND: String = "terminal-accessible-send"
    public const val ANNOUNCER: String = "terminal-accessible-announcer"
}

/**
 * The words a terminal shows; the host screen passes them from `:ui:words` (no copy in the kit).
 */
@Immutable
public data class KitTerminalWords(
    /** The grid's one accessibility description ("Terminal"). */
    val terminal: String,
    /** The switch ("Accessible mode"). */
    val accessibleMode: String,
    /** The transcript list's description ("Terminal transcript"). */
    val transcript: String,
    /** Shown while the transcript is empty ("No terminal output yet."). */
    val noOutput: String,
    /** The field's label ("Send to terminal"). */
    val sendToTerminal: String,
    /** The send button ("Send"). */
    val send: String,
    /** Why input is off (the terminal ended or is not connected). */
    val inputUnavailable: String,
)

/**
 * The newest lines a screen reader hears as they arrive (06 §7.7: the last 3, one announcement per
 * second).
 */
internal const val ANNOUNCED_LINES: Int = 3
internal const val ANNOUNCE_EVERY_MS: Long = 1_000

/**
 * A live terminal with accessible mode (plan 06 §7.7 `KitTerminalView` `.live` + `.accessible`;
 * DESKTOP-WEB-CROSS-30).
 *
 * The visible switch [KitTerminalTags.ACCESSIBLE_MODE] chooses between the live grid
 * ([KitTerminalLive], one semantics node) and the accessible view ([KitTerminalAccessible]: the
 * transcript as text and a "Send to terminal" field). [accessibleMode] is the person's choice; null
 * follows the system: on by itself while TalkBack or Switch Access is on
 * (`KitSystemSignals.accessibleNavigation`), and the person can turn it off. Without a live
 * [platformView] (`PlatformViewHandle.native`) (not started yet, or a platform without a terminal
 * view) only the accessible view can show.
 *
 * [onSend] receives exactly what to write: the field's value and a carriage return. [above] sits
 * above the grid or the transcript in both modes (the sign-in terminal's "Open sign-in page" and
 * "Copy code", 17 §1.5). [defaultKeyBar] shows the Ctrl-C / Ctrl-D / Esc / Tab bar; a screen that
 * supplies its own [KitTerminalKeyBar] turns it off so the keys are not shown twice.
 */
@Composable
public fun KitTerminalView(
    platformView: Any?,
    title: String,
    transcript: String,
    accessibleMode: Boolean?,
    onAccessibleModeChange: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    words: KitTerminalWords,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
    defaultKeyBar: Boolean = true,
    /** False when the host exposes this choice in its visible terminal menu, as Flutter does. */
    showAccessibleSwitch: Boolean = true,
    above: @Composable ColumnScope.() -> Unit = {},
) {
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val on =
        KitTerminalRules.accessibleOn(
            accessibleMode,
            signals.accessibleNavigation,
            platformView != null,
        )
    Column(modifier) {
        if (showAccessibleSwitch)
            KitSwitchRow(
                title = words.accessibleMode,
                checked = on,
                modifier = Modifier.testTag(KitTerminalTags.ACCESSIBLE_MODE),
                enabled =
                    if (platformView == null) KitEnabled.Disabled(words.inputUnavailable)
                    else KitEnabled.Enabled,
                onCheckedChange = onAccessibleModeChange,
            )
        above()
        if (on || platformView == null) {
            KitTerminalAccessible(
                transcript = transcript,
                onSend = onSend,
                words = words,
                inputEnabled = inputEnabled,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            KitTerminalLive(platformView, title, words.terminal, Modifier.weight(1f).fillMaxWidth())
        }
        KitTerminalLinks(
            transcript,
            columns =
                (platformView as? io.github.eslamasabry.oc.core.common.terminal.TerminalSurface)
                    ?.columns
                    ?.takeIf { it > 0 } ?: 80,
        )
        if (defaultKeyBar)
            KitTerminalKeyBar(
                onSend = onSend,
                enabled =
                    if (inputEnabled) KitEnabled.Enabled
                    else KitEnabled.Disabled(words.inputUnavailable),
            )
    }
}

/** Pure rules of [KitTerminalView] (unit-tested). */
public object KitTerminalRules {
    /**
     * Accessible mode is the person's choice when made, else on with a screen reader; always on
     * without a grid.
     */
    public fun accessibleOn(choice: Boolean?, screenReader: Boolean, hasGrid: Boolean): Boolean =
        !hasGrid || (choice ?: screenReader)

    /** The lines a screen reader hears for [lines]: the newest [ANNOUNCED_LINES], joined. */
    public fun announcement(lines: List<String>): String =
        lines.takeLast(ANNOUNCED_LINES).joinToString("\n")
}

/**
 * The accessible terminal (plan 06 §7.7 `.accessible`): the transcript stripped and capped
 * ([TerminalTranscript]) as a selectable list, one item per line (each merged into one node),
 * newest at the bottom; the newest three lines are a polite live region throttled to one
 * announcement per second; and a mono "Send to terminal" field with a Send button that writes the
 * value and a carriage return, then clears the field.
 */
@Composable
public fun KitTerminalAccessible(
    transcript: String,
    onSend: (String) -> Unit,
    words: KitTerminalWords,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
) {
    val lines =
        remember(transcript) { TerminalTranscript.lines(TerminalTranscript.forDisplay(transcript)) }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    val latest by rememberUpdatedState(KitTerminalRules.announcement(lines))
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }
            .conflate()
            .collect {
                spoken = it
                delay(ANNOUNCE_EVERY_MS)
            }
    }
    val field = rememberTextFieldState()
    val send = {
        if (inputEnabled) {
            onSend(TerminalTranscript.sendLine(field.text.toString()))
            field.clearText()
        }
    }
    val enabled =
        if (inputEnabled) KitEnabled.Enabled else KitEnabled.Disabled(words.inputUnavailable)
    Column(modifier) {
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().testTag(KitTerminalTags.TRANSCRIPT).semantics {
                    contentDescription = words.transcript
                },
                state = listState,
            ) {
                if (lines.isEmpty()) {
                    item { KitText(words.noOutput, tone = TextTone.Secondary) }
                } else {
                    itemsIndexed(lines) { _, line ->
                        KitText(
                            line,
                            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                            role = TypeRole.Mono,
                        )
                    }
                }
            }
        }
        Spacer(
            Modifier.size(1.dp).testTag(KitTerminalTags.ANNOUNCER).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            KitField(
                state = field,
                label = words.sendToTerminal,
                modifier = Modifier.weight(1f).testTag(KitTerminalTags.INPUT),
                kind = KitFieldKind.Mono,
                imeAction = ImeAction.Send,
                enabled = enabled,
                onSubmit = send,
            )
            Spacer(Modifier.size(8.dp))
            KitButton(
                label = words.send,
                onClick = send,
                modifier = Modifier.testTag(KitTerminalTags.SEND),
                role = KitButtonRole.Secondary,
                enabled = enabled,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The Flutter surface (Dart `KitTerminalView`, lib/ui/kit/kit_terminal_view.dart): the live form drawn by the kit
// ([KitTerminalLiveView] over a [KitTerminalScreen]) and the output form ([KitTerminalOutput]).
// ---------------------------------------------------------------------------------------------------------------------

/** Dart `KitTerminalView`'s constants. */
public object KitTerminalViewDefaults {
    /** The calm view's face size (Dart `calmFontSize`): a comfortable read on a phone, about 45 columns at 412 dp. */
    public const val CALM_FONT_SIZE: Float = 14f // F: lib/ui/kit/kit_terminal_view.dart:139

    /** The calm view's line height (Dart `calmLineHeight`). */
    public const val CALM_LINE_HEIGHT: Float = 1.5f // F: lib/ui/kit/kit_terminal_view.dart:140

    /** Past this many lines the full output opens elsewhere instead of inline (Dart `inlineLineCap`). */
    public const val INLINE_LINE_CAP: Int = 2000 // F: lib/ui/kit/kit_terminal_view.dart:183

    /** How many of the last lines the output form shows before "Show earlier" (Dart `tailLines`). */
    public const val TAIL_LINES: Int = 40 // F: lib/ui/kit/kit_terminal_view.dart:87

    /** The selection's share of the accent (Dart `_selectionAlpha`). */
    public const val SELECTION_ALPHA: Float = .28f // F: lib/ui/kit/kit_terminal_view.dart:186

    /** A faint cell's foreground alpha (xterm `paintCellForeground`). */
    public const val FAINT_ALPHA: Float = 0.5f // F: third_party/xterm/lib/src/ui/painter.dart:181
}

/** Test tags of the Flutter surface (the Dart `ValueKey`s). */
public object KitTerminalViewTags {
    public const val VIEW: String = "terminal-view"
    public const val SHOW_EARLIER: String = "terminal-show-earlier"
    public const val OPEN_FULL: String = "terminal-open-full"
    public const val SIDEWAYS: String = "terminal-view-sideways"
    public const val COPIED_TOAST: String = "terminal-copied-toast"
    public const val GRID: String = "terminal-grid"
}

/**
 * The terminal colours (Dart `KitTerminalView.themeOf`); both forms and every theme pack use them. [normal] and
 * [bright] are the eight ANSI colours each: black, red, green, yellow, blue, magenta, cyan, white. Errors are never
 * `danger`: red is `text1`.
 */
@Immutable
public data class KitTerminalTheme(
    val cursor: Color,
    val selection: Color,
    val foreground: Color,
    val background: Color,
    val normal: List<Color>,
    val bright: List<Color>,
)

/**
 * [KitTerminalTheme] for [colors] (Dart `themeOf`); [calm] clears the background (Dart `calmThemeOf`), so the screen's
 * ground shows behind the text.
 */
public fun kitTerminalTheme(colors: KitColors, calm: Boolean = false): KitTerminalTheme =
    KitTerminalTheme(
        cursor = colors.accent,
        selection = colors.accent.copy(alpha = KitTerminalViewDefaults.SELECTION_ALPHA),
        foreground = colors.text1,
        background = if (calm) colors.ground.copy(alpha = 0f) else colors.ground,
        normal =
            listOf(
                colors.surface3,
                colors.text1,
                colors.success,
                colors.codeString,
                colors.codeType,
                colors.codeKeyword,
                colors.codeType,
                colors.text2,
            ),
        bright =
            listOf(
                colors.text3,
                colors.text1,
                colors.success,
                colors.codeString,
                colors.codeType,
                colors.codeKeyword,
                colors.codeType,
                colors.text1,
            ),
    )

/**
 * The colour of palette entry [index] (xterm `PaletteBuilder.paletteColor`): the sixteen theme colours, the 6×6×6
 * cube, then the grey ramp. Entry 15 is the theme's white, as xterm builds it.
 */
internal fun KitTerminalTheme.palette(index: Int): Color =
    when {
        index < 0 -> foreground
        index < BRIGHT_FROM -> normal[index]
        index < WHITE_AGAIN -> bright[index - BRIGHT_FROM]
        index == WHITE_AGAIN -> normal[WHITE]
        index < GREY_FROM -> {
            var r = 0
            var g = 0
            var b = 0
            repeat(index - CUBE_FROM) {
                if (b == 0) b = CUBE_FIRST
                else if (b < CHANNEL_MAX) b += CUBE_STEP
                else {
                    b = 0
                    if (g == 0) g = CUBE_FIRST
                    else if (g < CHANNEL_MAX) g += CUBE_STEP
                    else {
                        g = 0
                        if (r == 0) r = CUBE_FIRST else if (r < CHANNEL_MAX) r += CUBE_STEP
                    }
                }
            }
            rgb(r, g, b)
        }
        else -> {
            val v = GREY_BASE + (minOf(index, CHANNEL_MAX) - GREY_FROM) * GREY_STEP
            rgb(v, v, v)
        }
    }

/** A program's own colour, drawn as sent (xterm `resolveForegroundColor`, `CellColor.rgb`). */
internal fun rgb(r: Int, g: Int, b: Int): Color =
    Color(red = r / CHANNEL_MAX.toFloat(), green = g / CHANNEL_MAX.toFloat(), blue = b / CHANNEL_MAX.toFloat())

private const val BRIGHT_FROM = 8 // F: third_party/xterm/lib/src/ui/palette_builder.dart:33
private const val WHITE = 7 // F: third_party/xterm/lib/src/ui/palette_builder.dart:31
private const val WHITE_AGAIN = 15 // F: third_party/xterm/lib/src/ui/palette_builder.dart:47
private const val CUBE_FROM = 16 // F: third_party/xterm/lib/src/ui/palette_builder.dart:56
private const val GREY_FROM = 232 // F: third_party/xterm/lib/src/ui/palette_builder.dart:51
private const val CUBE_FIRST = 95 // F: third_party/xterm/lib/src/ui/palette_builder.dart:60
private const val CUBE_STEP = 40 // F: third_party/xterm/lib/src/ui/palette_builder.dart:62
private const val CHANNEL_MAX = 255 // F: third_party/xterm/lib/src/ui/palette_builder.dart:61
private const val GREY_BASE = 8 // NV: xterm's grey ramp (palette_builder.dart:89-113) starts at 0x08
private const val GREY_STEP = 10 // NV: and climbs 0x0a per entry

/** The xterm cell probe: the cell is the average width of these ten letters, one line high (`_measureCharSize`). */
private const val CELL_PROBE = "mmmmmmmmmm"

/**
 * The live character grid drawn by the kit (Dart `xterm.TerminalView` as `KitTerminalView.live` configures it): the
 * [screen]'s buffer in the theme's colours, one cell at a time, at the mono role's size (the calm face at
 * [KitTerminalViewDefaults.CALM_FONT_SIZE] on [KitTerminalViewDefaults.CALM_LINE_HEIGHT]), growing with the text size up
 * to `terminalMaxTextScale`. The view sizes the screen to its box (`autoResize`), keeps to the bottom unless scrolled,
 * and draws the cursor as a block while focused and as an outline otherwise. Keys typed while focused reach [onInput]
 * as the bytes a terminal sends; read-only drops them. Ctrl+Tab and Ctrl+Shift+Tab move focus out (LAY-10).
 */
@Composable
public fun KitTerminalGrid(
    screen: KitTerminalScreen,
    modifier: Modifier = Modifier,
    calm: Boolean = false,
    readOnly: Boolean = false,
    autofocus: Boolean = true,
    onInput: (String) -> Unit = {},
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val theme = remember(colors, calm) { kitTerminalTheme(colors, calm) }
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    // xterm's TerminalStyle keeps the face's family, size and height only.
    val face =
        remember(mono, calm) {
            TextStyle(
                fontFamily = mono.fontFamily,
                fontSize = if (calm) KitTerminalViewDefaults.CALM_FONT_SIZE.sp else mono.fontSize,
                lineHeight =
                    if (calm) (KitTerminalViewDefaults.CALM_FONT_SIZE * KitTerminalViewDefaults.CALM_LINE_HEIGHT).sp
                    else mono.lineHeight,
                lineHeightStyle = mono.lineHeightStyle,
                platformStyle = mono.platformStyle,
                fontWeight = FontWeight.Normal,
            )
        }
    val density = LocalDensity.current
    // The face grows with the person's text size up to 2x: beyond it, 40 columns no longer fit a phone (A11Y-8).
    val scaled =
        Density(density.density, minOf(density.fontScale, FlutterKitTokens.TERMINAL_MAX_TEXT_SCALE.toFloat()))
    val measurer = rememberTextMeasurer()
    val cell =
        remember(face, scaled) {
            // Compose rounds a paragraph's width up to a whole pixel; the probe repeated a hundredfold keeps that
            // rounding out of the cell (xterm divides the exact width).
            val letters = CELL_PROBE.repeat(CELL_PROBE.length * CELL_PROBE.length)
            val probe = measurer.measure(letters, face, softWrap = false, density = scaled)
            Size(probe.multiParagraph.intrinsics.maxIntrinsicWidth / letters.length, probe.multiParagraph.height)
        }
    val glyphs = remember(face, scaled, theme) { HashMap<Pair<String, GlyphKey>, TextLayoutResult>() }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // How far the view is scrolled up from the bottom, in px; 0 follows the output.
    var back by remember(screen) { mutableFloatStateOf(0f) }
    val scroll =
        rememberScrollableState { delta ->
            val extent = maxOf(screen.bufferLines.size * cell.height - viewport.height, 0f)
            val old = back
            back = (back + delta).coerceIn(0f, extent)
            back - old
        }
    LaunchedEffect(autofocus) { if (autofocus) runCatching { focus.requestFocus() } }
    val pad =
        if (calm) Modifier.padding(start = tokens.gutter.dp, top = Space.S2.dp, end = tokens.gutter.dp, bottom = Space.S2.dp)
        else Modifier.padding(Space.S2.dp)
    Box(modifier.drawBehind { drawRect(theme.background) }.then(pad)) {
        Spacer(
            Modifier.fillMaxSize()
                .testTag(KitTerminalViewTags.GRID)
                .onSizeChanged { size ->
                    viewport = size
                    // xterm `_updateViewportSize`: a box no bigger than one cell leaves the size alone.
                    if (size.width > cell.width || size.height > cell.height)
                        screen.resize((size.width / cell.width).toInt(), (size.height / cell.height).toInt())
                }
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Tab && event.isCtrlPressed) {
                        if (event.type == KeyEventType.KeyDown)
                            focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                        return@onPreviewKeyEvent true
                    }
                    if (onKeyEvent?.invoke(event) == true) return@onPreviewKeyEvent true
                    if (readOnly) return@onPreviewKeyEvent true
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val bytes = terminalKeyBytes(event, screen.applicationCursorKeys) ?: return@onPreviewKeyEvent false
                    onInput(bytes)
                    true
                }
                .focusable()
                .scrollable(scroll, Orientation.Vertical, reverseDirection = true)
                .drawBehind {
                    screen.version
                    drawGrid(screen, theme, cell, face, scaled, measurer, glyphs, back, focused)
                },
        )
    }
}

/** What a glyph's look depends on, besides its character. */
private data class GlyphKey(val color: Color, val bold: Boolean, val italic: Boolean, val underline: Boolean)

/** Dart xterm `RenderTerminal._paint` and `TerminalPainter`: the visible lines, then the cursor. */
@Suppress("LongParameterList")
private fun DrawScope.drawGrid(
    screen: KitTerminalScreen,
    theme: KitTerminalTheme,
    cell: Size,
    face: TextStyle,
    scaled: Density,
    measurer: TextMeasurer,
    glyphs: HashMap<Pair<String, GlyphKey>, TextLayoutResult>,
    back: Float,
    focused: Boolean,
) {
    val lines = screen.bufferLines
    if (lines.isEmpty() || cell.width <= 0f || cell.height <= 0f) return
    val extent = maxOf(lines.size * cell.height - size.height, 0f)
    val scroll = (extent - back).coerceIn(0f, extent)
    val first = (scroll / cell.height).toInt().coerceIn(0, lines.lastIndex)
    val last = ((scroll + size.height) / cell.height).toInt().coerceIn(0, lines.lastIndex)
    // xterm truncates each line's top to a whole logical pixel.
    fun top(row: Int): Float = truncate((row * cell.height - scroll) / density) * density
    for (row in first..last) {
        val line = lines[row]
        val y = top(row)
        for (col in 0 until line.width) {
            val c = line[col] ?: continue
            val x = col * cell.width
            val st = c.style
            val bg = if (st.inverse) resolve(st.foreground, theme, theme.foreground) else st.background.takeIf { it != KitTerminalColor.Default }?.let { resolve(it, theme, theme.background) }
            if (bg != null) drawRect(bg, Offset(x, y), Size(cell.width + density, cell.height))
            if (c.char.isBlank() && !st.underline) continue
            var fg = if (st.inverse) resolve(st.background, theme, theme.background) else resolve(st.foreground, theme, theme.foreground)
            if (st.faint) fg = fg.copy(alpha = fg.alpha * KitTerminalViewDefaults.FAINT_ALPHA)
            val key = c.char to GlyphKey(fg, st.bold, st.italic, st.underline)
            val glyph =
                glyphs.getOrPut(key) {
                    measurer.measure(
                        // A space under an underline is drawn as a no-break space so the line shows (xterm).
                        if (st.underline && c.char == " ") " " else c.char,
                        face.copy(
                            color = fg,
                            fontWeight = if (st.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (st.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (st.underline) TextDecoration.Underline else TextDecoration.None,
                        ),
                        softWrap = false,
                        density = scaled,
                    )
                }
            drawText(glyph, topLeft = Offset(x, y))
        }
    }
    val cursorRow = screen.absoluteCursorY
    if (screen.cursorVisible && cursorRow in first..last) {
        val at = Offset(screen.cursorX * cell.width, top(cursorRow))
        if (focused) drawRect(theme.cursor, at, cell)
        else drawRect(theme.cursor, at, cell, style = Stroke(width = density))
    }
}

private fun resolve(color: KitTerminalColor, theme: KitTerminalTheme, default: Color): Color =
    when (color) {
        KitTerminalColor.Default -> default
        is KitTerminalColor.Palette -> theme.palette(color.index)
        is KitTerminalColor.Rgb -> rgb(color.r, color.g, color.b)
    }

/** The bytes a hardware key sends to the shell (xterm's default key table for the keys a line-mode shell reads). */
private fun terminalKeyBytes(event: KeyEvent, applicationCursorKeys: Boolean): String? {
    val ctrl = event.isCtrlPressed
    val alt = event.isAltPressed
    fun bar(key: KitTerminalBarKey) = terminalBarKeyInput(key, ctrl, alt, applicationCursorKeys)
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> "\r"
        Key.Backspace -> "\u007f"
        Key.Tab -> "\t"
        Key.Escape -> "\u001b"
        Key.DirectionUp -> bar(KitTerminalBarKey.Up)
        Key.DirectionDown -> bar(KitTerminalBarKey.Down)
        Key.DirectionLeft -> bar(KitTerminalBarKey.Left)
        Key.DirectionRight -> bar(KitTerminalBarKey.Right)
        else -> {
            val cp = event.utf16CodePoint
            if (cp <= 0 || cp == '￿'.code) null
            else terminalModifiedText(cp.toChar().toString(), ctrl = ctrl, alt = alt)
        }
    }
}

/**
 * A live session drawn by the kit (Dart `KitTerminalView.live`): [screen]'s grid with the key bar a phone keyboard
 * lacks under it. The view fills its box. The bar sits under it on phones and tablets (also with a mouse); a wide
 * window that takes hardware keys only ([hardwareKeysOnly], by default a desktop build) leaves it out. The bar is one
 * sideways row when the window left above the keyboard is short. [calm] is the Terminal screen's own look: the roomier
 * face on the gutter, the screen's ground behind the text, and the one-row bar on that ground.
 *
 * [readOnly]: not connected, or the shell ended — the last screen stays readable, input is dropped (not queued) and
 * the keys are disabled; the host says why. [keys] null draws no bar. [interruptKeys] leads the bar with Ctrl-C and
 * Ctrl-D (server terminals). Bar keys with a byte sequence reach [onInput]; the named keys without one (Esc, Tab, Home,
 * End, PgUp, PgDn) reach [onNamedKey] for the host terminal's own key input. [keyboardInset] is the keyboard's height
 * when the host knows it better than the window insets (the larger counts).
 *
 * A screen reader hears one node, [semanticsLabel]; the host offers the accessible transcript. OSC 52 clipboard writes
 * from the shown session are copied and "Copied to clipboard" floats over the top for two seconds.
 */
@Composable
public fun KitTerminalLiveView(
    screen: KitTerminalScreen,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    onInput: (String) -> Unit = {},
    onNamedKey: (key: KitTerminalBarKey, ctrl: Boolean, alt: Boolean) -> Unit = { _, _, _ -> },
    readOnly: Boolean = false,
    keys: TerminalKeyBarController? = null,
    interruptKeys: Boolean = false,
    calm: Boolean = false,
    autofocus: Boolean = true,
    hardwareKeysOnly: Boolean? = null,
    keyboardInset: Dp? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val window = LocalKitWindow.current
    val hardwareOnly = hardwareKeysOnly ?: (LocalKitPlatform.current.family == KitPlatformFamily.Desktop)
    val bar = if (hardwareOnly && window.cls.isWide) null else keys
    val view: @Composable (Modifier) -> Unit = { m ->
        TerminalOscHost(screen, m) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = semanticsLabel }) {
                KitLtr {
                    KitTerminalGrid(
                        screen = screen,
                        modifier = Modifier.fillMaxSize(),
                        calm = calm,
                        readOnly = readOnly,
                        autofocus = autofocus,
                        onInput = { if (!readOnly) onInput(it) },
                        onKeyEvent = onKeyEvent,
                    )
                }
            }
        }
    }
    if (bar == null) {
        view(modifier)
        return
    }
    // One sideways row when the window left above the keyboard is short (a phone in landscape with the keyboard up).
    val density = LocalDensity.current
    val ime = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboard = maxOf(ime, keyboardInset ?: 0.dp)
    val compact = window.heightDp - keyboard.value < Layout.SHORT_HEIGHT
    val send: (KitTerminalBarKey, Boolean, Boolean) -> Unit = { key, ctrl, alt ->
        if (!readOnly) {
            val bytes = terminalBarKeyInput(key, ctrl, alt, screen.applicationCursorKeys)
            if (bytes != null) onInput(bytes) else onNamedKey(key, ctrl, alt)
        }
    }
    if (calm) {
        Column(modifier) {
            // A line scrolled half out of view past the box stays off the header.
            view(Modifier.weight(1f).fillMaxWidth().clipToBounds())
            TerminalKeyBar(
                controller = bar,
                onKey = send,
                modifier =
                    Modifier.kitEdge(KitEdge.Chrome)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                enabled = !readOnly,
                compact = compact,
                singleRow = true,
            )
        }
        return
    }
    Column(modifier) {
        view(Modifier.weight(1f).fillMaxWidth())
        // The bar's band reaches the screen's edge: one surface, the safe area inside it.
        TerminalKeyBar(
            controller = bar,
            onKey = send,
            // Window chrome: the band and its keys meet the screen's edges, the safe area inside it.
            modifier = Modifier.kitEdge(KitEdge.Chrome),
            enabled = !readOnly,
            compact = compact,
            interruptKeys = interruptKeys,
            safeArea = true,
        )
    }
}

/**
 * Listens for OSC 52 clipboard writes from the shown session and says so (Dart `_KitTerminalOscHost`): only a composed
 * (foreground) view listens, and the listener it replaced is put back when it goes. A copy shows "Copied to clipboard"
 * over the top of the terminal for `terminalCopiedNote`, in the style's floating look (Solid: `surface3`).
 */
@Composable
private fun TerminalOscHost(screen: KitTerminalScreen, modifier: Modifier, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copies by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    DisposableEffect(screen, clipboard) {
        val previous = screen.onOsc52
        screen.onOsc52 = { args ->
            val text = TerminalOsc52.parse(args)
            if (text != null) {
                clipboard.setText(AnnotatedString(text))
                shown = true
                copies++
            }
        }
        onDispose { screen.onOsc52 = previous }
    }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(FlutterKitMotion.TERMINAL_COPIED_NOTE_MS.toLong())
            shown = false
        }
    }
    val words = stringResource(Res.string.terminalCopiedToClipboard)
    val alpha = animateKitValue(if (shown) 1f else 0f, KitPace.Standard)
    Box(modifier) {
        content()
        if (shown || alpha > 0f) {
            val colors = LocalKitColors.current
            val look =
                kitSurfaceLook(
                    KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Pill, SurfaceLevel.Surface3, ownFill = colors.surface3),
                )
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = Space.S3.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .testTag(KitTerminalViewTags.COPIED_TOAST)
                    .clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = if (shown) words else ""
                    }
                    .then(look.modifier)
                    .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
            ) {
                KitText(words, role = TypeRole.Secondary)
            }
        }
    }
}

/**
 * A command and what it printed (Dart `KitTerminalView.output`): the command after `$` (the program picked out of its
 * path, flags and strings apart), then the output's last [tailLines] lines with its own ANSI colours, or a tint from
 * what a line says (errors `text1` semibold, warnings `text2`, passes `success`, test progress). "Show {n} earlier lines"
 * reveals the rest in place; past [KitTerminalViewDefaults.INLINE_LINE_CAP] lines "Open all {n} lines" hands the whole
 * output to [onOpenFull] (ANSI stripped, secrets masked), and without [onOpenFull] the view says it shows the last
 * lines only. Every line and the command pass through `KitRedact.text` first (SEC-2, SEC-4).
 *
 * The block is a left-to-right region aligned left in every language, selectable, never under 48 dp, and its face
 * grows with the text size up to 2x. [framed] draws its own inset box (off where the host frames it already); [wrap]
 * off keeps each line on one line and lets the block scroll sideways. An empty output with no command draws nothing.
 */
@Composable
public fun KitTerminalOutput(
    output: String,
    modifier: Modifier = Modifier,
    command: String? = null,
    tailLines: Int = KitTerminalViewDefaults.TAIL_LINES,
    framed: Boolean = true,
    wrap: Boolean = true,
    onOpenFull: ((String) -> Unit)? = null,
    redact: (String) -> String = KitRedact::text,
) {
    var all by remember { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shownCommand = redact(command?.trim() ?: "")
    val lines = remember(output) { if (output.isEmpty()) emptyList() else output.split('\n') }
    if (lines.isEmpty() && shownCommand.isEmpty()) return
    val cap = KitTerminalViewDefaults.INLINE_LINE_CAP
    val total = lines.size
    val tooLong = total > cap
    // Where the collapsed view and the expanded one start.
    val tailStart = maxOf(0, total - maxOf(0, tailLines))
    val allStart = if (tooLong) total - cap else 0
    val start = if (all) allStart else maxOf(tailStart, allStart)
    val canExpand = !all && tailStart > allStart
    val capped = tooLong && start == allStart
    val theme = remember(colors) { kitTerminalTheme(colors) }
    val text =
        remember(shownCommand, lines, start, colors, redact) {
            terminalOutputText(shownCommand, lines.subList(start, total), colors, theme, redact)
        }
    val mono = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text1, textAlign = TextAlign.Start)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (canExpand) {
                val n = tailStart - allStart
                KitButton(
                    // The count is what a tap reveals: back to the cap, not the whole output, when it is too long.
                    label =
                        if (n == 1) stringResource(Res.string.terminalShowEarlier__eq1)
                        else pluralStringResource(Res.plurals.terminalShowEarlier, n, Numbers.format(n)),
                    // In place, no animation: tens of lines in a scrolling list must not animate layout (MOT-5).
                    onClick = { all = true },
                    modifier = Modifier.align(Alignment.Start).testTag(KitTerminalViewTags.SHOW_EARLIER),
                    role = KitButtonRole.Tertiary,
                )
            }
            // An LTR region aligned left in both directions (COPY-30, KIT-32).
            KitLtr {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(density.density, minOf(density.fontScale, FlutterKitTokens.TERMINAL_MAX_TEXT_SCALE.toFloat())),
                ) {
                    // The selectable text is a touch target: never under 48 dp, even for one line (LAY-9).
                    // One block for a screen reader and for the layout rules: the button above is a 48 dp target
                    // whose label sits in its middle, so the block starts right under its box, as in Flutter.
                    Box(Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).semantics { isTraversalGroup = true }) {
                        KitSelectable {
                            if (wrap) {
                                BasicText(text, style = mono)
                            } else {
                                Box(Modifier.horizontalScroll(rememberScrollState()).testTag(KitTerminalViewTags.SIDEWAYS)) {
                                    BasicText(text, style = mono, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
            if (capped && onOpenFull != null) {
                KitButton(
                    label = stringResource(Res.string.terminalOpenFull, Numbers.format(total)),
                    onClick = { onOpenFull(redact(TerminalText.strip(output))) },
                    modifier = Modifier.align(Alignment.Start).testTag(KitTerminalViewTags.OPEN_FULL),
                    role = KitButtonRole.Tertiary,
                )
            } else if (capped) {
                KitText(
                    if (cap == 1) stringResource(Res.string.kitTerminalViewShowingLast__eq1)
                    else pluralStringResource(Res.plurals.kitTerminalViewShowingLast, cap, Numbers.format(cap)),
                    Modifier.padding(top = Space.S2.dp),
                    role = TypeRole.Secondary,
                )
            }
        }
    }
    if (!framed) {
        Box(modifier.testTag(KitTerminalViewTags.VIEW)) { body() }
        return
    }
    // The frame's fill is `detailsSurface`: surface1 in the dark, the ground in the light.
    val fill = if (colors.dark) colors.surface1 else colors.ground // F: lib/ui/kit/kit_tokens.dart:805
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Code, KitShapeId.Code, ownFill = fill, color = fill))
    Box(modifier.testTag(KitTerminalViewTags.VIEW).fillMaxWidth().then(look.modifier).padding(Space.S2.dp)) { body() }
}

/** The output form's text (Dart `_KitTerminalOutputState.build` spans): the command, a blank line, then each line. */
internal fun terminalOutputText(
    command: String,
    shown: List<String>,
    colors: KitColors,
    theme: KitTerminalTheme,
    redact: (String) -> String,
): AnnotatedString =
    buildAnnotatedString {
        fun span(s: TerminalSpan) {
            val color = s.role?.let { colors.of(it) } ?: Color.Unspecified
            withStyle(SpanStyle(color = color, fontWeight = FontWeight(s.weight))) { append(s.text) }
        }
        if (command.isNotEmpty()) TerminalText.command(command).forEach(::span)
        if (command.isNotEmpty() && shown.isNotEmpty()) append("\n\n")
        shown.forEachIndexed { i, line ->
            if (i > 0) append("\n")
            if (TerminalAnsi.keepsColours(line, redact)) {
                for (s in TerminalAnsi.spans(line, redact)) {
                    val color =
                        when {
                            s.faint -> colors.text3
                            s.color == null -> Color.Unspecified
                            s.bright -> theme.bright[s.color]
                            else -> theme.normal[s.color]
                        }
                    withStyle(SpanStyle(color = color, fontWeight = if (s.bold) FontWeight.Bold else null)) { append(s.text) }
                }
            } else {
                TerminalText.line(line, redact).forEach(::span)
            }
        }
    }

private fun KitColors.of(role: TerminalRole): Color =
    when (role) {
        TerminalRole.Text1 -> text1
        TerminalRole.Text2 -> text2
        TerminalRole.Text3 -> text3
        TerminalRole.Success -> success
        TerminalRole.Accent -> accent
        TerminalRole.CodeType -> codeType
        TerminalRole.CodeString -> codeString
    }
