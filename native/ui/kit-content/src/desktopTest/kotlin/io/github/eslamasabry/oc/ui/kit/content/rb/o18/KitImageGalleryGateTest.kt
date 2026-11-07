package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.image.KitGalleryPicture
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageGallery
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_image_gallery_golden_test.dart: three pictures `screen-0..2.png` opened on the
 * second as a full-screen page (`kitGalleryShot` → Frame.Screen), loading (reads never finish, no Send), error (reads
 * fail), disabled (reads never finish, with Send) and working (Send tapped on a loaded picture, never answered).
 */
@OptIn(ExperimentalTestApi::class)
class KitImageGalleryGateTest {
    private fun pictures(load: suspend () -> ByteArray?) = List(3) { KitGalleryPicture("p$it", "screen-$it.png", load) }
    private val never: suspend () -> ByteArray? = { awaitCancellation() }
    private val failing: suspend () -> ByteArray? = { null }
    private val loaded: suspend () -> ByteArray? = { GalleryPictures.png }
    private val pending: suspend (ByteArray, String, Boolean) -> Boolean = { _, _, _ -> awaitCancellation() }

    private fun scene(state: String) = when (state) {
        "loading" -> pictures(never) to null
        "error" -> pictures(failing) to pending
        "disabled" -> pictures(never) to pending
        else -> pictures(loaded) to pending
    }

    private fun shot(name: String, state: String) {
        val (list, send) = scene(state)
        FlutterGoldenGate.check(
            name,
            frame = FlutterGoldenGate.Frame.Screen,
            act = if (state == "working") ({ onNodeWithText("Send").performClick() }) else null,
        ) { KitImageGallery(list, onClose = {}, initialIndex = 1, onSend = send) }
    }

    @Test fun states() {
        for (size in listOf("", "_1280x800")) {
            for (state in listOf("loading", "error", "disabled", "working")) shot("kit_image_gallery_$state$size", state)
            shot("kit_image_gallery_working_text2$size", "working")
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_image_gallery",
            caps = mapOf("KitImageGallery" to "fill", "showKitImageGallery" to "fill"),
            frame = FlutterGoldenGate.Frame.Screen,
        ) { KitImageGallery(pictures(loaded), onClose = {}, initialIndex = 1, onSend = { _, _, _ -> true }) }
    }
}
