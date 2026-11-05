package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** One whole-block plan target in a KitRowGroup (Flutter kit_plan_block.dart:51-110). */
@Composable
public fun KitPlanBlock(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleNote: String? = null,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val nameStyle = typography.style(TypeRole.RowTitle).copy(color = colors.text1)
    val noteStyle = typography.style(TypeRole.Secondary).copy(color = colors.text2).toSpanStyle()
    val name = buildAnnotatedString {
        append(title)
        titleNote?.let { withStyle(noteStyle) { append(" $it") } }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    KitTappable(
        onClick = onClick,
        modifier =
            modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().semantics {
                contentDescription = listOfNotNull(title, titleNote, summary).joinToString(", ")
            },
        role = KitTappableRole.Row,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.rowHeightOne.dp)
                .padding(
                    start = Space.S4.dp,
                    end = Space.S4.dp,
                    top = Space.S3.dp,
                    bottom = Space.S2.dp,
                )
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val summaryWidth =
                    summary?.let {
                        measurer.measure(it, typography.style(TypeRole.Secondary)).size.width
                    } ?: 0
                val nameWidth = measurer.measure(name, nameStyle).size.width
                val fits =
                    nameWidth + summaryWidth + with(density) { Space.S3.dp.toPx() } <=
                        constraints.maxWidth
                if (summary == null) {
                    BasicText(name, style = nameStyle)
                } else if (fits) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                    ) {
                        BasicText(name, Modifier.weight(1f), style = nameStyle)
                        KitText(summary, role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                } else {
                    Column {
                        BasicText(name, style = nameStyle)
                        KitText(summary, role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
            }
            content()
        }
    }
}
