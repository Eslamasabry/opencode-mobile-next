package io.github.eslamasabry.oc.ui.kit.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreview
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewBackground
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewSize

/**
 * The widget-style screen's preview, drawn as Flutter draws it there: `KitWidgetPreview` (lib/ui/kit/
 * kit_widget_style_form.dart:61-73) — sample conversations in the app's own parts, the chosen [accent], a card
 * (square) or the list layout ([wide]). The RemoteViews palette is kept in the signature for existing callers: [text]
 * and [muted] are the launcher's, not the preview's, and [background] only tells a see-through ground (alpha 0) when
 * [ground] is not given.
 */
@Composable
public fun KitWidgetStylePreview(
    background: Int,
    text: Int,
    muted: Int,
    accent: Int,
    wide: Boolean,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    ground: KitWidgetPreviewBackground? = null,
) {
    Box(
        modifier.fillMaxWidth().testTag("widget-style-preview").clearAndSetSemantics { contentDescription = semanticsLabel },
        contentAlignment = Alignment.Center,
    ) {
        KitWidgetPreview(
            size = widgetStylePreviewSize(wide),
            background = ground ?: widgetStylePreviewGround(background),
            accent = Color(accent),
        )
    }
}

/** Square shows the card layout, wide the list (the launcher's 2x2 and 4x2 cells). */
public fun widgetStylePreviewSize(wide: Boolean): KitWidgetPreviewSize =
    if (wide) KitWidgetPreviewSize.List else KitWidgetPreviewSize.Card

/** A resolved ground with no alpha is the see-through one; any other is drawn solid. */
public fun widgetStylePreviewGround(background: Int): KitWidgetPreviewBackground =
    if (Color(background).alpha == 0f) KitWidgetPreviewBackground.Transparent else KitWidgetPreviewBackground.Solid
