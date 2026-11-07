package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_widget_preview.dart `_extent` and the stored names kit_widget_style_form.dart maps (lines 62-71). */
class KitWidgetPreviewTest {
    @Test fun `each size draws the launcher cells' extent, the list capped at 320`() {
        assertEquals(112.dp to 112.dp, widgetPreviewExtent(KitWidgetPreviewSize.Compact, 400.dp))
        assertEquals(212.dp to 168.dp, widgetPreviewExtent(KitWidgetPreviewSize.Card, 400.dp))
        assertEquals(320.dp to 132.dp, widgetPreviewExtent(KitWidgetPreviewSize.List, 400.dp))
        assertEquals(280.dp to 132.dp, widgetPreviewExtent(KitWidgetPreviewSize.List, 280.dp))
    }

    @Test fun `stored names map as the form maps them, unknown names to card and solid`() {
        assertEquals(KitWidgetPreviewSize.Compact, widgetPreviewSizeOf("compact"))
        assertEquals(KitWidgetPreviewSize.List, widgetPreviewSizeOf("list"))
        assertEquals(KitWidgetPreviewSize.Card, widgetPreviewSizeOf("card"))
        assertEquals(KitWidgetPreviewSize.Card, widgetPreviewSizeOf("huge"))
        assertEquals(KitWidgetPreviewBackground.Tinted, widgetPreviewBackgroundOf("tinted"))
        assertEquals(KitWidgetPreviewBackground.Transparent, widgetPreviewBackgroundOf("transparent"))
        assertEquals(KitWidgetPreviewBackground.Solid, widgetPreviewBackgroundOf("solid"))
        assertEquals(KitWidgetPreviewBackground.Solid, widgetPreviewBackgroundOf("glass"))
    }
}
