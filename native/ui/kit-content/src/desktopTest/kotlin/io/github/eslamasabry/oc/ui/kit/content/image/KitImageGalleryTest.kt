package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals

/** KitImageGallery's rules (lib/ui/kit/kit_image_gallery.dart) and its send path. */
@OptIn(ExperimentalTestApi::class)
class KitImageGalleryTest {
    @Test fun `the shown index stays inside the list`() {
        assertEquals(0, GalleryRules.clampIndex(-2, 3))
        assertEquals(2, GalleryRules.clampIndex(9, 3))
        assertEquals(1, GalleryRules.clampIndex(1, 3))
    }

    @Test fun `only the shown picture and its neighbours are read`() {
        assertEquals(listOf(0, 1), GalleryRules.prefetch(0, 3))
        assertEquals(listOf(0, 1, 2), GalleryRules.prefetch(1, 5))
        assertEquals(listOf(3, 4), GalleryRules.prefetch(4, 5))
        assertEquals(listOf(0), GalleryRules.prefetch(0, 1))
    }

    @Test fun `an edited picture is a png named after the original`() {
        assertEquals("screen-edited.png", GalleryRules.editedName("screen.jpg"))
        assertEquals("notes-edited.png", GalleryRules.editedName("notes"))
        assertEquals(".hidden-edited.png", GalleryRules.editedName(".hidden"))
    }

    @Test fun `one picture shows no position`() {
        assertEquals(false, GalleryRules.showsPosition(1))
        assertEquals(true, GalleryRules.showsPosition(2))
    }

    @Test fun `send hands over the shown picture and closes when it was added`() = runComposeUiTest {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(), 0, 0, 0, 0x0C, 0x49, 0x44, 0x41, 0x54, 8, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0, 0, 3, 1, 1, 0, 0x18, 0xDD.toByte(), 0x8D.toByte(), 0xB0.toByte(), 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())
        var sent: Pair<String, Boolean>? = null
        var closed = 0
        val pictures = listOf(KitGalleryPicture("a", "a.png") { png }, KitGalleryPicture("b", "b.png") { png })
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitImageGallery(pictures, onClose = { closed++ }, initialIndex = 1, onSend = { _, name, edited -> sent = name to edited; true })
            }
        }
        waitForIdle()
        onNodeWithText("Send").performClick()
        waitForIdle()
        assertEquals("b.png" to false, sent)
        assertEquals(1, closed)
    }

    @Test fun `send waits for the picture`() = runComposeUiTest {
        val pictures = listOf(KitGalleryPicture("a", "a.png") { awaitCancellation() })
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitImageGallery(pictures, onClose = {}, onSend = { _, _, _ -> CompletableDeferred<Boolean>().await() })
            }
        }
        waitForIdle()
        onNodeWithText("Send").assertIsNotEnabled()
    }
}
