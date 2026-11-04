package io.github.eslamasabry.oc.ui.kitchat.tool
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Lines of output shown in place before "Show all" (plan 09 §9.5: output is cut at 12 lines before the row). */
public const val KIT_COMMAND_OUTPUT_LINES: Int = 12

/**
 * A shell step's opened body (plan 09 §9.5; Dart `_CommandBox`): one box with the command after a `$` prompt sign and
 * the first [maxLines] lines of what it printed, all mono and left to right in any interface language. The prompt
 * sign is drawn outside the command's own text: selection skips it and a screen reader does not read it. Command and
 * output display redacted (R18). When the output has more lines, [moreLabel] opens the rest ([onMore]); [exitLabel]
 * says how it ended ("Exit code 1").
 *
 * Text: command and output reading mono (full size, left to right); the more button and exit line frame.
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
    val shape = KitShapeId.Code.toShape()
    val printed = output?.let { redacted(it) }?.trimEnd().orEmpty()
    val lines = if (printed.isEmpty()) emptyList() else printed.lines()
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
                            LocalKitTypography.current.style(TypeRole.Mono).copy(
                                color = colors.text3,
                                textDirection = TextDirection.Ltr,
                            ),
                    )
                }
                KitMonoLtr(command, Modifier.weight(1f), TextTone.Primary)
            }
        }
        if (lines.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                // Already redacted above: each line is shown as is (redacting twice changes nothing).
                for (line in lines.take(maxLines)) KitMonoLtr(line, Modifier.fillMaxWidth(), TextTone.Tertiary, maxLines = 1)
            }
        }
        if (!exitLabel.isNullOrEmpty()) {
            KitText(exitLabel, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        }
        if (lines.size > maxLines) {
            KitTappable(onClick = onMore, shape = KitShapeId.Button) {
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
