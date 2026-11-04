package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSignalAspect
import io.github.eslamasabry.oc.ui.kit.foundation.KitSignalHead
import io.github.eslamasabry.oc.ui.kit.foundation.KitSignalSlip
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRequestNeedsYouLabel
import org.jetbrains.compose.resources.stringResource

/** The request's own Style frame: wash/ring, Soft/Paper start edge, Brutal ink+pill, or Signal printer slip. */
@Composable
public fun KitRequestSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val label = stringResource(Res.string.kitRequestNeedsYouLabel)
    if (style.style == SurfaceStyle.Signal) {
        val palette = SignalPalette.of(colors.dark)
        KitSignalSlip(modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitSignalHead(KitSignalAspect.Stop)
                    CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = Color(palette.alertInk.v))) {
                        KitText(label.uppercase(), role = TypeRole.Caption)
                    }
                }
                KitSurfaceScope { content() }
            }
        }
        return
    }
    val fill = if (style.style == SurfaceStyle.Solid) colors.attentionSurface.compositeOver(colors.surface1) else colors.surface1
    val look = kitSurfaceLook(SurfaceRequest(SurfaceRole.Content), fill, colors.attentionLine, 1f)
    val edge = style.style == SurfaceStyle.Soft || style.style == SurfaceStyle.Paper
    Column(
        modifier.fillMaxWidth().kitSurface(look, KitShapeId.Card.toShape()).then(
            if (edge) Modifier.drawBehind {
                val width = 4.dp.toPx()
                drawRect(colors.attention, Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, 0f), Size(width, size.height))
            } else Modifier
        ).padding(Space.S4.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (style.style == SurfaceStyle.Brutal) {
            val pill = kitSurfaceLook(SurfaceRequest(SurfaceRole.Control, SurfaceLevel.Surface2, fill = Argb(colors.attentionFill.toArgb())), colors.attentionFill)
            Row(Modifier.kitSurface(pill, KitShapeId.Pill.toShape()).padding(horizontal = Space.S2.dp, vertical = 2.dp)) {
                CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = colors.onAttentionFill)) { KitText(label, role = TypeRole.Caption) }
            }
        } else if (style.style != SurfaceStyle.Solid) {
            KitText(label, role = TypeRole.Caption, tone = TextTone.Attention)
        }
        KitSurfaceScope { content() }
    }
}
