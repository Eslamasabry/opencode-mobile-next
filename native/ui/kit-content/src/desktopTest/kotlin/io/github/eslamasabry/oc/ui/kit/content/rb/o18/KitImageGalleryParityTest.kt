package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import io.github.eslamasabry.oc.ui.kit.content.image.GALLERY_ZOOMED_SCALE
import io.github.eslamasabry.oc.ui.kit.content.image.galleryEditedName
import io.github.eslamasabry.oc.ui.kit.content.image.galleryPrefetch
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rules of lib/ui/kit/kit_image_gallery.dart at 8302de537 that are not pictures. */
class KitImageGalleryParityTest {
    @Test fun edited_names_keep_the_stem_and_become_png() {
        // _editedName (kit_image_gallery.dart:157-161)
        assertEquals("screen-1-edited.png", galleryEditedName("screen-1.png"))
        assertEquals("photo.final-edited.png", galleryEditedName("photo.final.jpg"))
        assertEquals("README-edited.png", galleryEditedName("README"))
        assertEquals(".hidden-edited.png", galleryEditedName(".hidden"))
    }

    @Test fun only_the_shown_picture_and_its_neighbours_are_read() {
        // _prefetch (kit_image_gallery.dart:132-136)
        assertEquals(listOf(0, 1, 2), galleryPrefetch(1, 3))
        assertEquals(listOf(0, 1), galleryPrefetch(0, 3))
        assertEquals(listOf(1, 2), galleryPrefetch(2, 3))
        assertEquals(listOf(0), galleryPrefetch(0, 1))
    }

    @Test fun a_picture_counts_as_zoomed_past_one_point_zero_one() {
        // _GalleryPageState._changed (kit_image_gallery.dart:338)
        assertEquals(1.01f, GALLERY_ZOOMED_SCALE)
    }
}
