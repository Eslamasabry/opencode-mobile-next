package io.github.eslamasabry.oc.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The launcher's preset colours, from widgets_overlay/widget_accents.dart, never provider data. */
@Composable
public fun widgetAccentColor(id: String): Color {
    val colors = LocalKitColors.current
    return when (id) {
        "green" -> Color(0xFF48AC6F) // F: lib/widgets_overlay/widget_accents.dart:11
        "teal" -> Color(0xFF258783) // F: lib/widgets_overlay/widget_accents.dart:12
        "blue" -> Color(0xFF4187CD) // F: lib/widgets_overlay/widget_accents.dart:13
        "amber" -> Color(0xFFBA8420) // F: lib/widgets_overlay/widget_accents.dart:14
        "wallpaper" ->
            lerp(colors.accent, colors.text1, .4f) // F: lib/widgets_overlay/widget_accents.dart:15
        else -> colors.accent
    }
}

/** A fixed, private sample of the three Flutter widget layouts over a sample wallpaper. */
@Composable
public fun KitWidgetPreview(
    title: String,
    background: String,
    accent: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val ink by
        animateColorAsState(widgetAccentColor(accent), specs.enter(), label = "widget-accent")
    val label = stringResource(Res.string.widgetV2PreviewLabel)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, 1.2f))
    ) { // F: lib/ui/kit/kit_widget_preview.dart:58
        BoxWithConstraints(
            modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val width by
                animateDpAsState(
                    if (compact) 112.dp
                    else if (wide) minOf(maxWidth, 320.dp)
                    else 212.dp, // F: lib/ui/kit/kit_widget_preview.dart:47-49
                    specs.enter(),
                    label = "widget-preview-width",
                )
            val bodyHeight =
                if (compact) 112
                else if (wide) 132 else 168 // F: lib/ui/kit/kit_widget_preview.dart:47-49
            val height by
                animateDpAsState(
                    (bodyHeight + Space.S6).dp,
                    specs.enter(),
                    label = "widget-preview-height",
                )
            val fill by
                animateColorAsState(
                    when (background) {
                        "transparent" -> Color.Transparent
                        "tinted" ->
                            lerp(
                                colors.surface1,
                                ink,
                                .16f,
                            ) // F: lib/ui/kit/kit_widget_preview.dart:120
                        else -> colors.surface1
                    },
                    specs.enter(),
                    label = "widget-preview-ground",
                )
            Box(
                Modifier.size(width, height)
                    .background(
                        Brush.linearGradient(listOf(colors.surface3, colors.surface1)),
                        RoundedCornerShape(FlutterKitTokens.SHEET_RADIUS.dp),
                    )
                    .padding(Space.S3.dp)
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .background(fill, RoundedCornerShape(FlutterKitTokens.CARD_RADIUS.dp))
                        .padding(Space.S3.dp),
                    verticalArrangement =
                        if (wide) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                ) {
                    if (compact) {
                        PreviewDot(colors.success)
                        Column {
                            KitText(
                                2.toString(),
                                role = TypeRole.Title,
                            ) // F: lib/ui/kit/kit_widget_preview.dart:179
                            KitText(
                                stringResource(Res.string.widgetV2PreviewWorking),
                                role = TypeRole.Caption,
                                maxLines = 1,
                            )
                        }
                    } else if (wide) {
                        PreviewListRow(
                            colors.success,
                            title,
                            stringResource(Res.string.widgetV2PreviewStop),
                            ink,
                        )
                        PreviewListRow(
                            colors.attention,
                            stringResource(Res.string.widgetV2PreviewSecond),
                            stringResource(Res.string.widgetV2PreviewAllow),
                            ink,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PreviewDot(colors.success)
                            Spacer(Modifier.width(Space.S2.dp))
                            KitText(
                                title,
                                Modifier.weight(1f),
                                role = TypeRole.Label,
                                tone = TextTone.Primary,
                                maxLines = 1,
                            )
                        }
                        KitText(
                            stringResource(Res.string.widgetV2PreviewStep),
                            role = TypeRole.Caption,
                            maxLines = 1,
                        )
                        Box(
                            Modifier.fillMaxWidth()
                                .height((FlutterKitTokens.LOADING_BAR_HEIGHT + 1).dp)
                                .clip(RoundedCornerShape(FlutterKitTokens.MARK_RADIUS.dp))
                                .background(colors.hairline)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(.6f).fillMaxHeight().background(ink)
                            ) // F: lib/ui/kit/kit_widget_preview.dart:289
                        }
                        Row {
                            PreviewChip(stringResource(Res.string.widgetV2PreviewStop), ink)
                            Spacer(Modifier.width(Space.S2.dp))
                            PreviewChip(stringResource(Res.string.widgetV2PreviewSteer), ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewDot(color: Color) {
    Box(Modifier.size(Space.S3.dp).background(color, CircleShape))
}

@Composable
private fun PreviewChip(text: String, accent: Color) {
    KitText(
        text,
        Modifier.background(
                accent.copy(alpha = .2f),
                RoundedCornerShape(FlutterKitTokens.BUTTON_RADIUS.dp),
            ) // F: lib/ui/kit/kit_widget_preview.dart:234
            .padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
        role = TypeRole.Caption,
        tone = TextTone.Primary,
        maxLines = 1,
    )
}

@Composable
private fun PreviewListRow(mark: Color, title: String, action: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PreviewDot(mark)
        Spacer(Modifier.width(Space.S2.dp))
        KitText(
            title,
            Modifier.weight(1f),
            role = TypeRole.Caption,
            tone = TextTone.Primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(Space.S2.dp))
        PreviewChip(action, accent)
    }
}
