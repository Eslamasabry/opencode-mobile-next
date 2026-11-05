package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** One inset and one section rhythm for plain, explained and trailing section captions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    explanation: String? = null,
    margin: Dp? = null,
    gapBefore: Dp? = null,
) {
    val tokens = LocalKitTokens.current
    val style = LocalKitStyle.current
    val request = SurfaceRequest(LocalKitRoles.current, SurfaceRole.Divider, style.knobs)
    val rowRule = style.spec.resolve(request)
    val sectionRule = style.spec.resolve(request.copy(selected = true))
    val ruled = sectionRule != null && sectionRule != rowRule && (sectionRule.fill?.alpha ?: 0) > 0
    val spacing = remember { object : Arrangement.Horizontal {
        override val spacing: Dp = Space.S2.dp
        override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
            with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
        }
    } }
    val shown = if (style.spec.tokens.type.engravedLabels) text.uppercase() else text
    var hasAbove by remember { mutableStateOf(false) }
    var explaining by remember { mutableStateOf(false) }
    val line = with(LocalDensity.current) { LocalKitTypography.current.style(TypeRole.Label).lineHeight.toDp() }
    val reach = if (explanation == null) 0.dp else ((tokens.minTarget.dp - line) / 2).coerceAtLeast(0.dp) // F: lib/ui/kit/kit_section_label.dart:132
    val gap = ((gapBefore ?: if (hasAbove) tokens.sectionGap.dp else 0.dp) - reach).coerceAtLeast(0.dp)
    val words: @Composable () -> Unit = {
        KitText(shown, Modifier.semantics { heading(); contentDescription = text }, role = TypeRole.Label, tone = TextTone.Secondary)
    }
    Column(modifier.kitMaxWidth(CapRole.List).fillMaxWidth().onGloballyPositioned { hasAbove = it.positionInParent().y > 0f }.padding(horizontal = margin ?: tokens.gutter.dp).padding(top = gap, bottom = (tokens.labelGap.dp - reach).coerceAtLeast(0.dp)).testTag("kit-section-label")) {
        if (ruled) {
            KitDivider(section = true)
            Spacer(Modifier.height((Space.S2.dp - reach).coerceAtLeast(0.dp)))
        }
        FlowRow(Modifier.fillMaxWidth(), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = spacing, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (explanation == null) words() else {
                KitTip(message = explanation, visible = explaining, onDismissed = { explaining = false }, anchor = {
                    KitTappable(onClick = { explaining = true }, onLongClick = { explaining = true }, modifier = Modifier.offset(x = -Space.S2.dp)) { Box(Modifier.padding(horizontal = Space.S2.dp)) { words() } }
                })
            }
            if (trailing != null) trailing()
        }
    }
}
