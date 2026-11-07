package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageMarkup
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_image_markup_golden_test.dart, at phone and PC size, light and dark. */
@OptIn(ExperimentalTestApi::class)
class KitImageMarkupGateTest {
    // open (kit_image_markup_golden_test.dart:71-72): name 'screen.png', Send shown.
    private fun scene(bytes: ByteArray, stillReading: Boolean = false): @Composable () -> Unit = {
        KitImageMarkup(
            bytes = bytes,
            name = "screen.png",
            onResult = {},
            // 'working': the result is still being drawn when the shot is taken.
            encodePng = { awaitCancellation() },
            // 'loading': Flutter's shot is taken before the engine has decoded the picture.
            decode = if (stillReading) { _ -> awaitCancellation() } else { b -> b.decodeToImageBitmap() },
        )
    }

    private val loading = scene(testPicture, stillReading = true)
    private val error = scene(byteArrayOf(1, 2, 3))
    private val working = scene(testPicture)

    // kit_image_markup_golden_test.dart:87-96: a stroke from the canvas centre, by (80, 30) then (40, -60), then Send.
    private val drawThenSend: ComposeUiTest.() -> Unit = {
        val k = 1f // touch offsets arrive in the captured (logical) frame, as Flutter's moveBy
        onNodeWithTag("kit-markup-canvas").performTouchInput {
            down(center)
            moveBy(Offset(80f * k, 30f * k))
            moveBy(Offset(40f * k, -60f * k))
            up()
        }
        onNodeWithTag("kit-markup-send").performClick()
    }

    private fun shot(name: String, act: (ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, Frame.Screen, wrap = { ImageCapture(it) }, act = act, content = content)
    }

    @Test fun kit_image_markup_loading() = shot("kit_image_markup_loading", content = loading)
    @Test fun kit_image_markup_loading_1280x800() = shot("kit_image_markup_loading_1280x800", content = loading)
    @Test fun kit_image_markup_error() = shot("kit_image_markup_error", content = error)
    @Test fun kit_image_markup_error_1280x800() = shot("kit_image_markup_error_1280x800", content = error)
    @Test fun kit_image_markup_working() = shot("kit_image_markup_working", drawThenSend, working)
    @Test fun kit_image_markup_working_1280x800() = shot("kit_image_markup_working_1280x800", drawThenSend, working)
    @Test fun kit_image_markup_working_text2() = shot("kit_image_markup_working_text2", drawThenSend, working)
    @Test fun kit_image_markup_working_text2_1280x800() = shot("kit_image_markup_working_text2_1280x800", drawThenSend, working)

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_image_markup",
            caps = mapOf("KitImageMarkup" to "fill", "showKitImageMarkup" to "fill"),
            frame = Frame.Screen,
            content = scene(testPicture),
        )
    }
}
