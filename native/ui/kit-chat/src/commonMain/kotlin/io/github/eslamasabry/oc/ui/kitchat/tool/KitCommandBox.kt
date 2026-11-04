package io.github.eslamasabry.oc.ui.kitchat.tool
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.terminal.TerminalRole
import io.github.eslamasabry.oc.core.common.terminal.TerminalSpan
import io.github.eslamasabry.oc.core.common.terminal.TerminalText
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/**
 * Lines of output shown in place before "Show all" (plan 09 §9.5: output is cut at 12 lines before
 * the row).
 */
public const val KIT_COMMAND_OUTPUT_LINES: Int = 12

/** Lines "Show all" expands in place; longer output opens in full instead (Dart `inlineCap`). */
public const val KIT_COMMAND_INLINE_CAP: Int = 200

/**
 * A shell step's opened body (plan 09 §9.5; Dart `_CommandBox`): one box with the command after a
 * `$` prompt sign and the first [maxLines] lines of what it printed, all mono and left to right in
 * any interface language. The prompt sign is drawn outside the command's own text: selection skips
 * it and a screen reader does not read it. Command and output display redacted (R18). When the
 * output has more lines, [moreLabel] opens the rest ([onMore]); [exitLabel] says how it ended
 * ("Exit code 1").
 *
 * Text: command and output reading mono (full size, left to right); the more button and exit line
 * frame.
 */
@Composable
public fun KitCommandBox(
    command: String,
    output: String?,
    maxLines: Int = KIT_COMMAND_OUTPUT_LINES,
    moreLabel: String,
    onMore: () -> Unit,
    exitLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.shape()
    val redactor = LocalKitRedactor.current
    // The whole output is masked first (a private key spans lines), then each line again across
    // its ANSI runs; masking twice changes nothing.
    val raw = output?.let(redactor::redact)?.trimEnd().orEmpty()
    val lines = if (raw.isEmpty()) emptyList() else raw.lines()
    var expanded by remember(command, output) { mutableStateOf(false) }
    // Dart `_CommandBox`: the first lines, "Show all" expands in place up to the inline cap and
    // opens the full output past it.
    val cap = KIT_COMMAND_INLINE_CAP
    val shown = lines.take(if (expanded) cap else maxLines.coerceIn(0, cap))
    val commandSpans = TerminalText.command(redactor.redact(TerminalText.strip(command))).drop(1)
    Column(
        modifier
            .fillMaxWidth()
            .kitSurface(kitSurfaceLook(SurfaceRequest(SurfaceRole.Code), colors.surface1, colors.hairline, 1f), shape)
            .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth()) {
                DisableSelection {
                    BasicText(
                        "$ ",
                        Modifier.clearAndSetSemantics {},
                        style =
                            LocalKitTypography.current
                                .style(TypeRole.Mono)
                                .copy(
                                    color = colors.text3,
                                    textDirection = TextDirection.Ltr,
                                ),
                    )
                }
                SelectionContainer(Modifier.weight(1f)) {
                    TerminalStyledText(commandSpans, colors)
                }
            }
        }
        if (lines.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        for (line in shown) TerminalStyledText(
                            TerminalText.line(line, redactor::redact),
                            colors,
                        )
                    }
                }
            }
        }
        if (!exitLabel.isNullOrEmpty()) {
            KitText(
                exitLabel,
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
        if (!expanded && lines.size > maxLines) {
            KitTappable(
                onClick = { if (lines.size > cap) onMore() else expanded = true },
                shape = KitShapeId.Button,
            ) {
                KitText(
                    moreLabel,
                    Modifier.padding(horizontal = Space.S2.dp),
                    role = TypeRole.Label,
                    tone = TextTone.Accent,
                    kind = KitTextKind.Frame,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Mask first across ANSI boundaries, then apply only semantic theme roles to the safe spans. */
@Composable
private fun TerminalStyledText(spans: List<TerminalSpan>, colors: KitColors) {
    val text = buildAnnotatedString {
        spans.forEach { span ->
            val color =
                when (span.role) {
                    TerminalRole.Text1,
                    null -> colors.text1
                    TerminalRole.Text2 -> colors.text2
                    TerminalRole.Text3 -> colors.text3
                    TerminalRole.Success -> colors.success
                    TerminalRole.Accent -> colors.accent
                    TerminalRole.CodeType -> colors.codeType
                    TerminalRole.CodeString -> colors.codeString
                }
            pushStyle(SpanStyle(color = color, fontWeight = FontWeight(span.weight)))
            append(span.text)
            pop()
        }
    }
    BasicText(
        text,
        style =
            LocalKitTypography.current.style(TypeRole.Mono).copy(textDirection = TextDirection.Ltr),
    )
}
