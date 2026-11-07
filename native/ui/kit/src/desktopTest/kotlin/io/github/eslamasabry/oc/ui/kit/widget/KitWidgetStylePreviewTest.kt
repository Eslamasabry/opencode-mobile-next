package io.github.eslamasabry.oc.ui.kit.widget

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewBackground
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** The widget-style screen draws Flutter's KitWidgetPreview (kit_widget_style_form.dart:61-73), not a bar sketch. */
@OptIn(ExperimentalTestApi::class)
class KitWidgetStylePreviewTest {
    @Test fun `square is the card, wide the list, a see-through ground stays see-through`() {
        assertEquals(KitWidgetPreviewSize.Card, widgetStylePreviewSize(wide = false))
        assertEquals(KitWidgetPreviewSize.List, widgetStylePreviewSize(wide = true))
        assertEquals(KitWidgetPreviewBackground.Transparent, widgetStylePreviewGround(0x00112233))
        assertEquals(KitWidgetPreviewBackground.Solid, widgetStylePreviewGround(0xFF112233.toInt()))
    }

    @Test fun `the preview is one node read by the caller's label`() = runComposeUiTest {
        setContent {
            KitTheme {
                KitWidgetStylePreview(0xFF101010.toInt(), -1, -1, 0xFF4187CD.toInt(), wide = true, semanticsLabel = "Widget preview")
            }
        }
        onNodeWithTag("widget-style-preview").assertIsDisplayed()
        onNodeWithContentDescription("Widget preview").assertIsDisplayed()
    }
}
