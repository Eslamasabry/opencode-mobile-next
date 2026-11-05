package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Flutter kit_ask_line.dart: an in-flow question, with both answers visible, stacked when needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitAskLine(
    icon: ImageVector,
    question: String,
    accept: KitAction,
    decline: KitAction,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val density = LocalDensity.current
    val measure = rememberTextMeasurer()
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().semantics {
        if (semanticsLabel != null) contentDescription = semanticsLabel
    }) {
        fun width(text: String, role: TypeRole): Float = with(density) {
            measure.measure(text, typography.style(role), softWrap = false, maxLines = 1).size.width.toDp().value
        }
        val buttons = width(accept.label, TypeRole.Button) + width(decline.label, TypeRole.Button) + 4 * 8f // F: lib/ui/kit/kit_ask_line.dart:55-58 lib/ui/kit/kit_buttons.dart:307
        val reserved = tokens.gutter + tokens.smallIcon + Space.S3 + Space.S2
        val stacked = reserved + width(question, TypeRole.Body) / 2 > maxWidth.value - buttons || density.fontScale > 1.5f // F: lib/ui/kit/kit_ask_line.dart:60-63
        val inset = (Space.S1 / 2f).dp // F: lib/ui/kit/kit_ask_line.dart:93
        val lineHeight = with(density) {
            measure.measure(question, typography.style(TypeRole.Body), maxLines = 1).size.height.toDp()
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = tokens.statusLineMin.dp)
                .padding(start = tokens.gutter.dp, end = Space.S2.dp, top = if (stacked) Space.S3.dp else inset, bottom = inset)
                .testTag(if (stacked) "kit-ask-line-stacked" else "kit-ask-line-inline"),
            verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(if (stacked) Modifier.height(lineHeight) else Modifier, contentAlignment = Alignment.Center) {
                KitGlyph(icon, colors.text2, tokens.smallIcon.dp)
            }
            Spacer(Modifier.width(Space.S3.dp))
            if (stacked) {
                Column(Modifier.weight(1f)) {
                    KitText(question, role = TypeRole.Body, maxLines = 2) // F: lib/ui/kit/kit_ask_line.dart:106
                    FlowRow(Modifier.offset(x = -Space.S2.dp), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        KitButton(accept, role = KitButtonRole.Tertiary)
                        KitButton(decline, role = KitButtonRole.Tertiary)
                    }
                }
            } else {
                KitText(question, Modifier.weight(1f), role = TypeRole.Body, maxLines = 2) // F: lib/ui/kit/kit_ask_line.dart:106
                Row {
                    KitButton(decline, role = KitButtonRole.Tertiary)
                    KitButton(accept, role = KitButtonRole.Tertiary)
                }
            }
        }
    }
}
