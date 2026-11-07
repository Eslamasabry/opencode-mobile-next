package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokenRoles
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTerminalViewShowingLast
import io.github.eslamasabry.oc.ui.words.res.terminalOpenFull
import io.github.eslamasabry.oc.ui.words.res.terminalShowEarlier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Test tags of the output form (Dart's value keys). */
public object KitTerminalOutputTags {
    public const val VIEW: String = "terminal-view" // Dart ValueKey('terminal-view')
    public const val SHOW_EARLIER: String = "terminal-show-earlier"
    public const val OPEN_FULL: String = "terminal-open-full"
}

/** Past this many lines the output opens elsewhere (Dart `KitTerminalView.inlineLineCap`). */
public const val KIT_TERMINAL_INLINE_LINE_CAP: Int = 2000 // F: lib/ui/kit/kit_terminal_view.dart:186

/** How many of the last lines show before "Show earlier" by default. */
public const val KIT_TERMINAL_TAIL_LINES: Int = 40 // F: lib/ui/kit/kit_terminal_view.dart:85

/** Which lines the output form shows (pure; Dart `_KitTerminalOutputState.build`). */
public data class KitTerminalOutputWindow(
    /** The first shown line. */
    val start: Int,
    /** "Show N earlier lines" is offered, with this N. */
    val earlier: Int,
    /** The output is past the inline cap and the shown part reaches the cap. */
    val capped: Boolean,
    val total: Int,
) {
    public val canExpand: Boolean get() = earlier > 0

    public companion object {
        public fun of(total: Int, tailLines: Int, all: Boolean, cap: Int = KIT_TERMINAL_INLINE_LINE_CAP): KitTerminalOutputWindow {
            val tooLong = total > cap
            val tailStart = maxOf(0, total - maxOf(0, tailLines))
            val allStart = if (tooLong) total - cap else 0
            val start = if (all) allStart else maxOf(tailStart, allStart)
            val canExpand = !all && tailStart > allStart
            return KitTerminalOutputWindow(
                start = start,
                earlier = if (canExpand) tailStart - allStart else 0,
                capped = tooLong && start == allStart,
                total = total,
            )
        }
    }
}

/**
 * The terminal's output form (Dart `KitTerminalView.output`, lib/ui/kit/kit_terminal_view.dart): a command after `$`
 * and what it printed, drawn the way a terminal does. The last [tailLines] lines show first (where a build or a test
 * run says how it went), "Show {n} earlier lines" opens the rest in place, and past [KIT_TERMINAL_INLINE_LINE_CAP]
 * lines "Open all {n} lines" hands the whole output (escapes stripped, secrets masked) to [onOpenFull]; without it the
 * part says it shows only the last lines. Nothing at all is drawn for an empty output with no command.
 *
 * Every output line and the command pass through [redact] before they are shown (SEC-2, SEC-4).
 *
 * States: tail, all, too long, empty
 * Text: reading (mono, capped at 2x, always left to right); the buttons are frame
 * Cap: Fill
 * Motion: none (lines appear in place: a long list must not animate layout, MOT-5)
 * Hidden: none
 * Replaces: KitTerminalView.output (lib/ui/kit/kit_terminal_view.dart)
 */
@Composable
public fun KitTerminalOutput(
    output: String,
    modifier: Modifier = Modifier,
    command: String? = null,
    tailLines: Int = KIT_TERMINAL_TAIL_LINES,
    framed: Boolean = true, // F: lib/ui/kit/kit_terminal_view.dart:86
    wrap: Boolean = true, // F: lib/ui/kit/kit_terminal_view.dart:87
    onOpenFull: ((String) -> Unit)? = null,
    redact: (String) -> String = KitRedact::text,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val cmd = redact(command?.trim().orEmpty())
    val lines = remember(output) { if (output.isEmpty()) emptyList() else output.split('\n') }
    if (lines.isEmpty() && cmd.isEmpty()) return
    var all by remember(output) { mutableStateOf(false) }
    val window = KitTerminalOutputWindow.of(lines.size, tailLines, all)
    val shown = lines.subList(window.start, lines.size)
    val text = remember(cmd, shown, colors) { KitTerminalSpans.block(cmd, shown, colors, redact) }
    val mono = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text1, textDirection = TextDirection.Ltr)
    val density = LocalDensity.current
    // Flutter's tertiary button keeps its space2 on both sides here (no KitInset): its words start space2 inside the
    // block, where the kit's tertiary would otherwise put them on the block's edge.
    val tertiaryInset = Modifier.padding(start = Space.S2.dp) // F: lib/ui/kit/kit_buttons.dart:728
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (window.canExpand) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    KitButton(
                        pluralStringResource(Res.plurals.terminalShowEarlier, window.earlier, Numbers.format(window.earlier)),
                        { all = true },
                        tertiaryInset.testTag(KitTerminalOutputTags.SHOW_EARLIER),
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
            // An LTR region aligned left in both directions (COPY-30, KIT-32); the face grows to 2x at most.
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, minOf(density.fontScale, FlutterKitTokens.TERMINAL_MAX_TEXT_SCALE.toFloat())),
            ) {
                SelectionContainer(
                    Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp) // F: lib/ui/kit/kit_terminal_view.dart:511
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                ) {
                    BasicText(text, style = mono, softWrap = wrap)
                }
            }
            if (window.capped && onOpenFull != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    KitButton(
                        stringResource(Res.string.terminalOpenFull, Numbers.format(window.total)),
                        { onOpenFull(redact(TerminalText.strip(output))) },
                        tertiaryInset.testTag(KitTerminalOutputTags.OPEN_FULL),
                        role = KitButtonRole.Tertiary,
                    )
                }
            } else if (window.capped) {
                KitText(
                    pluralStringResource(Res.plurals.kitTerminalViewShowingLast, KIT_TERMINAL_INLINE_LINE_CAP, Numbers.format(KIT_TERMINAL_INLINE_LINE_CAP)),
                    Modifier.padding(top = Space.S2.dp), // F: lib/ui/kit/kit_terminal_view.dart:552
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
        }
    }
    if (!framed) {
        Box(modifier.testTag(KitTerminalOutputTags.VIEW)) { body() }
        return
    }
    val fill = Color(FlutterKitTokenRoles.detailsSurface(LocalKitRoles.current).v) // F: lib/ui/kit/kit_terminal_view.dart:561
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Code, KitShapeId.Code, ownFill = fill))
    Box(
        modifier.fillMaxWidth().testTag(KitTerminalOutputTags.VIEW).then(look.modifier)
            .padding(Space.S2.dp), // F: lib/ui/kit/kit_terminal_view.dart:563
    ) { body() }
}

/**
 * Terminal text as styled text, port of Dart `KitTerminalText` with `KitTerminalView.themeOf`'s colours: the 16 ANSI
 * colours map to theme roles (red reads as text1, never as an alarm), errors are text1 semibold, warnings text2
 * semibold, passes success. A line holding a secret drops its colours and shows the masked text (SEC-2, SEC-4).
 */
public object KitTerminalSpans {
    private val SGR = Regex("""\u001B\[([0-9;]*)m""")
    private const val BOLD = 700 // F: lib/ui/kit/kit_terminal_view.dart:741
    private const val ANSI_NORMAL = 30 // F: lib/ui/kit/kit_terminal_view.dart:757
    private const val ANSI_NORMAL_END = 37 // F: lib/ui/kit/kit_terminal_view.dart:757
    private const val ANSI_BRIGHT = 90 // F: lib/ui/kit/kit_terminal_view.dart:759
    private const val ANSI_BRIGHT_END = 97 // F: lib/ui/kit/kit_terminal_view.dart:759
    private const val SGR_BOLD = 1 // F: lib/ui/kit/kit_terminal_view.dart:751
    private const val SGR_FAINT = 2 // F: lib/ui/kit/kit_terminal_view.dart:753
    private const val SGR_NORMAL = 22 // F: lib/ui/kit/kit_terminal_view.dart:755
    private const val SGR_DEFAULT = 39 // F: lib/ui/kit/kit_terminal_view.dart:761
    private const val SGR_FG_EXT = 38 // F: lib/ui/kit/kit_terminal_view.dart:763
    private const val SGR_BG_EXT = 48 // F: lib/ui/kit/kit_terminal_view.dart:763
    private const val EXT_256 = 5 // F: lib/ui/kit/kit_terminal_view.dart:765

    /**
     * Dart `KitTerminalView.themeOf`: the colour of ANSI colour [index] (0-7 normal, 8-15 bright) in [colors].
     */
    public fun ansiColor(index: Int, colors: KitColors): Color =
        when (index) { // F: lib/ui/kit/kit_terminal_view.dart:196-214
            0 -> colors.surface3
            1, 9, 15 -> colors.text1
            2, 10 -> colors.success
            3, 11 -> colors.codeString
            4, 6, 12, 14 -> colors.codeType
            5, 13 -> colors.codeKeyword
            7 -> colors.text2
            8 -> colors.text3
            else -> colors.text1
        }

    /** The colour of a role from `TerminalText` (the tint of plain lines). */
    public fun roleColor(role: TerminalRole?, colors: KitColors): Color =
        when (role) {
            null, TerminalRole.Text1 -> colors.text1
            TerminalRole.Text2 -> colors.text2
            TerminalRole.Text3 -> colors.text3
            TerminalRole.Success -> colors.success
            TerminalRole.Accent -> colors.accent
            TerminalRole.CodeType -> colors.codeType
            TerminalRole.CodeString -> colors.codeString
        }

    /** The command, a blank line, then the shown lines (Dart `_KitTerminalOutputState.build` spans). */
    public fun block(command: String, lines: List<String>, colors: KitColors, redact: (String) -> String): AnnotatedString =
        buildAnnotatedString {
            if (command.isNotEmpty()) {
                for (s in TerminalText.command(command)) append(s.text, roleColor(s.role, colors), s.weight)
            }
            if (command.isNotEmpty() && lines.isNotEmpty()) append("\n\n")
            lines.forEachIndexed { i, raw ->
                if (i > 0) append("\n")
                append(line(raw, colors, redact))
            }
        }

    /** One output line (Dart `KitTerminalText.line`). */
    public fun line(line: String, colors: KitColors, redact: (String) -> String): AnnotatedString {
        val stripped = TerminalText.strip(line)
        if ("\u001B[" in line && redact(stripped) == stripped) return ansi(line, colors, redact, fallback = null)
        return buildAnnotatedString {
            for (s in TerminalText.line(line, redact)) append(s.text, roleColor(s.role, colors), s.weight)
        }
    }

    /**
     * The line's own ANSI colours (Dart `KitTerminalText._ansi`). [fallback] is the colour of uncoloured text (null:
     * the surrounding style's).
     */
    public fun ansi(line: String, colors: KitColors, redact: (String) -> String, fallback: Color?): AnnotatedString =
        buildAnnotatedString {
            var color: Color? = null
            var bold = false
            var faint = false
            var at = 0
            fun emit(text: String) {
                val clean = redact(TerminalText.strip(text))
                if (clean.isEmpty()) return
                append(clean, if (faint) colors.text3 else color ?: fallback, if (bold) BOLD else null)
            }
            for (m in SGR.findAll(line)) {
                emit(line.substring(at, m.range.first))
                at = m.range.last + 1
                val codes = if (m.groupValues[1].isEmpty()) listOf(0) else m.groupValues[1].split(';').map { it.toIntOrNull() ?: 0 }
                var i = 0
                while (i < codes.size) {
                    val c = codes[i]
                    when {
                        c == 0 -> { color = null; bold = false; faint = false }
                        c == SGR_BOLD -> bold = true
                        c == SGR_FAINT -> faint = true
                        c == SGR_NORMAL -> { bold = false; faint = false }
                        c in ANSI_NORMAL..ANSI_NORMAL_END -> color = ansiColor(c - ANSI_NORMAL, colors)
                        c in ANSI_BRIGHT..ANSI_BRIGHT_END -> color = ansiColor(c - ANSI_BRIGHT + 8, colors) // NV: bright colours follow the eight normal ones
                        c == SGR_DEFAULT -> color = null
                        c == SGR_FG_EXT || c == SGR_BG_EXT ->
                            i += if (i + 1 < codes.size && codes[i + 1] == EXT_256) 2 else 4 // F: lib/ui/kit/kit_terminal_view.dart:765
                    }
                    i++
                }
            }
            emit(line.substring(at))
        }

    private fun AnnotatedString.Builder.append(text: String, color: Color?, weight: Int?) {
        pushStyle(SpanStyle(color = color ?: Color.Unspecified, fontWeight = weight?.takeIf { it != 400 }?.let(::FontWeight))) // NV: 400 is the regular weight
        append(text)
        pop()
    }
}
