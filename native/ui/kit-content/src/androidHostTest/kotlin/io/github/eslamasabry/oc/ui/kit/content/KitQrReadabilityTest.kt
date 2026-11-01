package io.github.eslamasabry.oc.ui.kit.content

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.pdf.CappedOutputStream
import io.github.eslamasabry.oc.ui.kit.content.qr.KIT_QR_TAG
import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plan 10 §14.1: every QR the app draws for the fixture payloads is rendered on Android graphics and decoded with ZXing
 * core 3.5.4, in light and dark themes (the card stays light). Plus the rasterizer's output cap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class KitQrReadabilityTest {
    @get:Rule val compose = createComposeRule()

    private val camera = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.TRY_HARDER to true)
    private val pure = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.PURE_BARCODE to true)

    /** Payloads up to this length must also pass ZXing's camera-style finder search (Wi-Fi URLs, pairing links). */
    private val cameraLimit = 500

    private fun readAll(dark: Boolean) {
        var data by mutableStateOf(QrFixtures.payloads.first())
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                localeTag = "en",
                stillFrame = true,
            ) { Box(Modifier.padding(16.dp)) { KitQr(data, "code") } }
        }
        val unread = mutableListOf<Int>()
        val missedByCamera = mutableListOf<Int>()
        for (payload in QrFixtures.payloads) {
            data = payload
            compose.waitForIdle()
            val image = compose.onNodeWithTag(KIT_QR_TAG).captureToImage()
            val map = image.toPixelMap()
            val pixels = IntArray(image.width * image.height) { i ->
                val c = map[i % image.width, i / image.width]
                fun ch(v: Float) = ((v * c.alpha + (1f - c.alpha)) * 255).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (ch(c.red) shl 16) or (ch(c.green) shl 8) or ch(c.blue)
            }
            fun read(hints: Map<DecodeHintType, Any>) = runCatching {
                QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels))), hints).text
            }.getOrNull()
            val byCamera = read(camera)
            if (byCamera != payload && payload.length <= cameraLimit) missedByCamera += payload.length
            if (byCamera != payload && read(pure) != payload) unread += payload.length
        }
        assertEquals("payload lengths that did not decode", emptyList<Int>(), unread)
        assertEquals("payload lengths the camera-style search missed", emptyList<Int>(), missedByCamera)
    }

    @Test
    fun `MV-8 every fixture code drawn in the light theme scans`() = readAll(dark = false)

    @Test
    fun `MV-8 every fixture code drawn in the dark theme scans`() = readAll(dark = true)

    @Test
    fun `MEDIA-VOICE-40 the PNG stops at its byte budget`() {
        val sink = ByteArrayOutputStream()
        val capped = CappedOutputStream(sink, 8)
        capped.write(ByteArray(8))
        val overflow = runCatching { capped.write(1) }.exceptionOrNull()
        assertTrue(overflow is IOException)
        assertTrue(capped.overflowed)
        assertEquals(8, sink.size())
    }
}

private typealias AndroidKitPlatform = io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
