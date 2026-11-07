package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageMarkup
import io.github.eslamasabry.oc.ui.kit.content.image.KitMarkupTags
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_image_markup_golden_test.dart, `screen.png` full screen (Frame.Screen): loading
 * (the picture not read yet: native's host passes null), error (bytes 1, 2, 3: not a picture) and working (a stroke
 * from the canvas centre by (80, 30) then (40, -60), then Send).
 */
@OptIn(ExperimentalTestApi::class)
class KitImageMarkupGateTest {
    private fun shot(name: String, state: String) {
        val bytes = when (state) {
            "loading" -> null
            "error" -> byteArrayOf(1, 2, 3)
            else -> GalleryPictures.png
        }
        FlutterGoldenGate.check(
            name,
            frame = FlutterGoldenGate.Frame.Screen,
            act = if (state != "working") null else ({
                onNodeWithTag(KitMarkupTags.CANVAS).performTouchInput {
                    down(center)
                    moveBy(Offset(80f, 30f))
                    moveBy(Offset(40f, -60f))
                    up()
                }
                onNodeWithText("Send").performClick()
            }),
        ) { KitImageMarkup(bytes, "screen.png", onResult = {}, work = inFlight) }
    }

    /**
     * The Dart scene captures while the result is still being drawn (`toImage` is never pumped): a worker that never
     * gets to it holds Send in its working state the same way.
     */
    private val inFlight by lazy {
        val gate = CountDownLatch(1)
        Executors.newSingleThreadExecutor { Thread(it).apply { isDaemon = true } }.asCoroutineDispatcher().also { d ->
            d.executor.execute { gate.await() }
        }
    }

    @Test fun states() {
        for (size in listOf("", "_1280x800")) {
            for (state in listOf("loading", "error", "working")) shot("kit_image_markup_$state$size", state)
            shot("kit_image_markup_working_text2$size", "working")
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_image_markup",
            caps = mapOf("KitImageMarkup" to "fill", "showKitImageMarkup" to "fill"),
            frame = FlutterGoldenGate.Frame.Screen,
        ) { KitImageMarkup(GalleryPictures.png, "screen.png", onResult = {}) }
    }
}
