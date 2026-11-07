package io.github.eslamasabry.oc.ui.kit

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** How big the home-screen widget is drawn: about 1x1, 2x2 or 4x2 launcher cells (Dart `KitWidgetPreviewSize`). */
public enum class KitWidgetPreviewSize { Compact, Card, List }

/** How the widget's ground is drawn (Dart `KitWidgetPreviewBackground`). */
public enum class KitWidgetPreviewBackground { Solid, Tinted, Transparent }

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

/** The stored size name (`compact`, `card`, `list`) as a preview size; anything else is a card (kit_widget_style_form.dart). */
public fun widgetPreviewSizeOf(name: String): KitWidgetPreviewSize =
    when (name) {
        "compact" -> KitWidgetPreviewSize.Compact
        "list" -> KitWidgetPreviewSize.List
        else -> KitWidgetPreviewSize.Card
    }

/** The stored ground name (`solid`, `tinted`, `transparent`) as a preview ground; anything else is solid. */
public fun widgetPreviewBackgroundOf(name: String): KitWidgetPreviewBackground =
    when (name) {
        "tinted" -> KitWidgetPreviewBackground.Tinted
        "transparent" -> KitWidgetPreviewBackground.Transparent
        else -> KitWidgetPreviewBackground.Solid
    }

/** The widget's drawn width and height for [size] in a box [maxWidth] wide (Dart `KitWidgetPreview._extent`). */
public fun widgetPreviewExtent(size: KitWidgetPreviewSize, maxWidth: Dp): Pair<Dp, Dp> =
    when (size) {
        KitWidgetPreviewSize.Compact -> 112.dp to 112.dp // F: lib/ui/kit/kit_widget_preview.dart:41
        KitWidgetPreviewSize.Card -> 212.dp to 168.dp // F: lib/ui/kit/kit_widget_preview.dart:42
        KitWidgetPreviewSize.List ->
            maxWidth.coerceIn(0.dp, 320.dp) to 132.dp // F: lib/ui/kit/kit_widget_preview.dart:43
    }

/**
 * A fixed, private sample of the three Flutter widget layouts over a sample wallpaper (string ids, as the settings store
 * them). [title] stays for existing callers; the drawing is [KitWidgetPreview] with the enum sizes.
 */
@Composable
public fun KitWidgetPreview(
    title: String,
    background: String,
    accent: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    KitWidgetPreview(
        size =
            when {
                compact -> KitWidgetPreviewSize.Compact
                wide -> KitWidgetPreviewSize.List
                else -> KitWidgetPreviewSize.Card
            },
        background = widgetPreviewBackgroundOf(background),
        accent = widgetAccentColor(accent),
        modifier = modifier,
        title = title,
    )
}

/**
 * A small picture of a home-screen widget in the style being chosen: the ground (solid, tinted or see-through, over a
 * sample wallpaper), the accent colour and the layout. It shows sample conversations in plain words, never the
 * person's own. A change of ground, accent or size eases over the standard duration; under reduced motion it changes at
 * once. lib/ui/kit/kit_widget_preview.dart.
 */
@Composable
public fun KitWidgetPreview(
    size: KitWidgetPreviewSize,
    background: KitWidgetPreviewBackground,
    accent: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val ink by animateColorAsState(accent, specs.enter(), label = "widget-accent")
    val label = stringResource(Res.string.widgetV2PreviewLabel)
    val heading = title ?: stringResource(Res.string.widgetV2PreviewTitle)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, 1.2f)) // F: lib/ui/kit/kit_widget_preview.dart:58
    ) {
        BoxWithConstraints(
            modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val (extentW, extentH) = widgetPreviewExtent(size, maxWidth)
            val width by animateDpAsState(extentW, specs.enter(), label = "widget-preview-width")
            val height by
                animateDpAsState(extentH + Space.S6.dp, specs.enter(), label = "widget-preview-height")
            val fill by
                animateColorAsState(
                    when (background) {
                        KitWidgetPreviewBackground.Solid -> colors.surface1
                        KitWidgetPreviewBackground.Tinted ->
                            lerp(colors.surface1, ink, .16f) // F: lib/ui/kit/kit_widget_preview.dart:117-121
                        KitWidgetPreviewBackground.Transparent ->
                            colors.surface1.copy(alpha = 0f)
                    },
                    specs.enter(),
                    label = "widget-preview-ground",
                )
            val sheet = RoundedCornerShape(FlutterKitTokens.SHEET_RADIUS.dp)
            Box(
                Modifier.size(width, height)
                    .background(Brush.linearGradient(listOf(colors.surface3, colors.surface1)), sheet)
                    .padding(Space.S3.dp)
            ) {
                // AnimatedSwitcher keyed by size: the layouts cross-fade (instant under reduced motion).
                // The switcher's stack loosens the width: the compact body hugs its words, centred; the
                // stretched card and list columns fill it.
                Crossfade(size, Modifier.fillMaxSize(), label = "widget-preview-layout", animationSpec = specs.enter()) { shown ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        (if (shown == KitWidgetPreviewSize.Compact) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
                            .background(fill, RoundedCornerShape(FlutterKitTokens.CARD_RADIUS.dp))
                            .padding(Space.S3.dp)
                    ) {
                        when (shown) {
                            KitWidgetPreviewSize.Compact -> PreviewCompact()
                            KitWidgetPreviewSize.Card -> PreviewCard(heading, ink)
                            KitWidgetPreviewSize.List -> PreviewList(heading, ink)
                        }
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
private fun PreviewCompact() {
    val colors = LocalKitColors.current
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        PreviewDot(colors.success)
        Column {
            KitText(
                2.toString(), // F: lib/ui/kit/kit_widget_preview.dart:177
                role = TypeRole.Title,
                tone = TextTone.Primary,
                tabular = true,
            )
            KitText(
                stringResource(Res.string.widgetV2PreviewWorking),
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewCard(title: String, accent: Color) {
    val colors = LocalKitColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PreviewDot(colors.success)
            Spacer(Modifier.width(Space.S2.dp))
            KitText(title, Modifier.weight(1f), role = TypeRole.Label, tone = TextTone.Primary, maxLines = 1)
        }
        KitText(
            stringResource(Res.string.widgetV2PreviewStep),
            Modifier.fillMaxWidth(),
            role = TypeRole.Caption,
            tone = TextTone.Secondary,
            maxLines = 1,
        )
        PreviewBar(accent, .6f) // F: lib/ui/kit/kit_widget_preview.dart:289
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PreviewChip(stringResource(Res.string.widgetV2PreviewStop), accent, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Space.S2.dp))
            PreviewChip(stringResource(Res.string.widgetV2PreviewSteer), accent, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun PreviewList(title: String, accent: Color) {
    val colors = LocalKitColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        PreviewListRow(colors.success, title, stringResource(Res.string.widgetV2PreviewStop), accent)
        PreviewListRow(
            colors.attention,
            stringResource(Res.string.widgetV2PreviewSecond),
            stringResource(Res.string.widgetV2PreviewAllow),
            accent,
        )
    }
}

/** The progress line: a hairline track, [fraction] of it in [color], loadingBarHeight + 1 tall, mark-rounded. */
@Composable
private fun PreviewBar(color: Color, fraction: Float) {
    val colors = LocalKitColors.current
    Box(
        Modifier.fillMaxWidth()
            .height((FlutterKitTokens.LOADING_BAR_HEIGHT + 1).dp) // F: lib/ui/kit/kit_widget_preview.dart:207
            .clip(RoundedCornerShape(FlutterKitTokens.MARK_RADIUS.dp))
            .background(colors.hairline)
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
    }
}

@Composable
private fun PreviewChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    KitText(
        text,
        modifier
            .background(
                accent.copy(alpha = .2f), // F: lib/ui/kit/kit_widget_preview.dart:234
                RoundedCornerShape(FlutterKitTokens.BUTTON_RADIUS.dp),
            )
            .padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
        role = TypeRole.Caption,
        tone = TextTone.Primary,
        maxLines = 1,
    )
}

@Composable
private fun PreviewListRow(mark: Color, title: String, action: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PreviewDot(mark)
        Spacer(Modifier.width(Space.S2.dp))
        KitText(title, Modifier.weight(1f), role = TypeRole.Caption, tone = TextTone.Primary, maxLines = 1)
        Spacer(Modifier.width(Space.S2.dp))
        PreviewChip(action, accent)
    }
}
