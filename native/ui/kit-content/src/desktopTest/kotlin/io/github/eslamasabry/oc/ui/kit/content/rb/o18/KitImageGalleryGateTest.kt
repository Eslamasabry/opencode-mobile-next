package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.content.image.KitGalleryPicture
import io.github.eslamasabry.oc.ui.kit.content.image.KitGallerySend
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageGallery
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_image_gallery_golden_test.dart, at phone and PC size, light and dark. */
@OptIn(ExperimentalTestApi::class)
class KitImageGalleryGateTest {
    // _pictures (kit_image_gallery_golden_test.dart:35-41): three pictures sharing one load.
    private fun pictures(load: suspend () -> ByteArray?): List<KitGalleryPicture> =
        List(3) { i -> KitGalleryPicture(id = "p$i", name = "screen-$i.png", load = load) }

    private val never: suspend () -> ByteArray? = { awaitCancellation() }
    private val sendNow: KitGallerySend = { _, _, _ -> true }
    private val sendNever: KitGallerySend = { _, _, _ -> awaitCancellation() }

    // open (kit_image_gallery_golden_test.dart:70-83): initialIndex 1, Send unless `send: false`.
    private fun scene(load: suspend () -> ByteArray?, send: KitGallerySend?): @Composable () -> Unit = {
        KitImageGallery(pictures(load), onClose = {}, encodePng = ::encodeTestPng, initialIndex = 1, onSend = send)
    }

    private val loading = scene(never, null)
    private val error = scene({ null }, sendNow)
    private val disabled = scene(never, sendNow)
    private val working = scene({ testPicture }, sendNever)
    private val tapSend: ComposeUiTest.() -> Unit = { onNodeWithTag("kit-gallery-send").performClick() }

    private fun shot(name: String, act: (ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, Frame.Screen, wrap = { ImageCapture(it) }, act = act, content = content)
    }

    @Test fun kit_image_gallery_loading() = shot("kit_image_gallery_loading", content = loading)
    @Test fun kit_image_gallery_loading_1280x800() = shot("kit_image_gallery_loading_1280x800", content = loading)
    @Test fun kit_image_gallery_error() = shot("kit_image_gallery_error", content = error)
    @Test fun kit_image_gallery_error_1280x800() = shot("kit_image_gallery_error_1280x800", content = error)
    @Test fun kit_image_gallery_disabled() = shot("kit_image_gallery_disabled", content = disabled)
    @Test fun kit_image_gallery_disabled_1280x800() = shot("kit_image_gallery_disabled_1280x800", content = disabled)
    @Test fun kit_image_gallery_working() = shot("kit_image_gallery_working", tapSend, working)
    @Test fun kit_image_gallery_working_1280x800() = shot("kit_image_gallery_working_1280x800", tapSend, working)
    @Test fun kit_image_gallery_working_text2() = shot("kit_image_gallery_working_text2", tapSend, working)
    @Test fun kit_image_gallery_working_text2_1280x800() = shot("kit_image_gallery_working_text2_1280x800", tapSend, working)

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_image_gallery",
            caps = mapOf("KitImageGallery" to "fill", "showKitImageGallery" to "fill"),
            frame = Frame.Screen,
            content = scene({ testPicture }, sendNow),
        )
    }
}
