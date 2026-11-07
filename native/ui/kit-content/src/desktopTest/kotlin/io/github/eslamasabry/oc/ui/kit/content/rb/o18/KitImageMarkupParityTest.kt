package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.content.image.KIT_MARKUP_MAX_BYTES
import io.github.eslamasabry.oc.ui.kit.content.image.MARKUP_HANDLE_ROOM_DP
import io.github.eslamasabry.oc.ui.kit.content.image.MARKUP_MIN_CROP_DP
import io.github.eslamasabry.oc.ui.kit.content.image.MARKUP_PEN_DP
import io.github.eslamasabry.oc.ui.kit.content.image.MarkupPen
import io.github.eslamasabry.oc.ui.kit.content.image.markupDragCorner
import io.github.eslamasabry.oc.ui.kit.content.image.markupFit
import io.github.eslamasabry.oc.ui.kit.content.image.markupMoveCrop
import io.github.eslamasabry.oc.ui.kit.content.image.markupNextFactor
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The rules of lib/ui/kit/kit_image_markup.dart at 8302de537 that are not pictures. */
class KitImageMarkupParityTest {
    private val full = Rect(0f, 0f, 800f, 600f)

    @Test fun constants() {
        assertEquals(9 * 1024 * 1024, KIT_MARKUP_MAX_BYTES) // :61
        assertEquals(4f, MARKUP_PEN_DP) // :115
        assertEquals(48f, MARKUP_MIN_CROP_DP) // :116
        assertEquals(24f, MARKUP_HANDLE_ROOM_DP) // :117
    }

    @Test fun pens_are_markup_ink_in_order() {
        // _penColours (kit_image_markup.dart:64-69), MarkupInk (theme_roles.dart:715-720)
        assertEquals(
            listOf(0xFFE5322D.toInt(), 0xFFFFD60A.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
            MarkupPen.entries.map { it.colour.toArgb() },
        )
    }

    @Test fun the_picture_is_contained_and_centred() {
        // _fit (kit_image_markup.dart:165-174)
        val fit = markupFit(full, Size(400f, 600f))
        assertEquals(0.5f, fit.scale)
        assertEquals(Offset(0f, 150f), fit.origin)
        val tall = markupFit(full, Size(1600f, 600f))
        assertEquals(1f, tall.scale)
        assertEquals(Offset(400f, 0f), tall.origin)
    }

    @Test fun corners_resize_within_the_picture_and_the_minimum() {
        // _dragCorner (kit_image_markup.dart:224-245)
        assertEquals(Rect(100f, 50f, 800f, 600f), markupDragCorner(full, full, 0, 100f, 50f, 48f))
        assertEquals(Rect(0f, 0f, 800f, 600f), markupDragCorner(full, full, 0, -100f, -50f, 48f))
        assertEquals(Rect(0f, 10f, 48f, 600f), markupDragCorner(full, full, 1, -2000f, 10f, 48f))
        assertEquals(Rect(0f, 0f, 800f, 48f), markupDragCorner(full, full, 3, 0f, -2000f, 48f))
        assertEquals(Rect(752f, 0f, 800f, 552f), markupDragCorner(Rect(0f, 0f, 800f, 552f), full, 2, 2000f, 0f, 48f))
    }

    @Test fun the_crop_moves_but_stays_inside() {
        // _moveCrop (kit_image_markup.dart:247-259)
        val crop = Rect(100f, 100f, 300f, 300f)
        assertEquals(Rect(150f, 80f, 350f, 280f), markupMoveCrop(crop, full, 50f, -20f))
        assertEquals(Rect(0f, 400f, 200f, 600f), markupMoveCrop(crop, full, -500f, 900f))
    }

    @Test fun a_big_result_is_scaled_down_by_a_quarter_until_it_fits() {
        // _render (kit_image_markup.dart:316-317)
        assertNull(markupNextFactor(KIT_MARKUP_MAX_BYTES, 1f))
        assertEquals(0.75f, markupNextFactor(KIT_MARKUP_MAX_BYTES + 1, 1f))
        assertNull(markupNextFactor(KIT_MARKUP_MAX_BYTES + 1, 0.19f))
    }
}
