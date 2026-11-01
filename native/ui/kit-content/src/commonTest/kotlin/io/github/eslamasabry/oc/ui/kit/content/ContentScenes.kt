package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPage
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPageState
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerBinaryBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerCodeBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerFrame
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody

/**
 * Scenes shared by the goldens and the desktop gallery. Host words (labels a feature passes in) come from here in
 * English and Arabic; the kit's own words come from `:ui:words`.
 */
data class ContentWords(
    val qrLabel: String,
    val ask: String,
    val save: String,
    val shareWifi: String,
    val prose: String,
) {
    companion object {
        val En = ContentWords(
            qrLabel = "QR code to open this chat on your phone",
            ask = "Ask about this file",
            save = "Save",
            shareWifi = "Share on Wi-Fi",
            prose = "Notes for the release.\n\nThe viewer keeps each paragraph in its own direction.\nA mixed line keeps its order: مرحبا.",
        )
        val Ar = ContentWords(
            qrLabel = "رمز QR لفتح هذه المحادثة على هاتفك",
            ask = "اسأل عن هذا الملف",
            save = "حفظ",
            shareWifi = "مشاركة عبر Wi-Fi",
            prose = "ملاحظات الإصدار.\n\nيحافظ العارض على اتجاه كل فقرة.\nEnglish lines stay left to right.",
        )

        fun of(tag: String): ContentWords = if (tag.startsWith("ar")) Ar else En
    }
}

const val SCENE_LINK: String = "https://handoff.example.invalid/s/42?t=abc"

val SceneCode: String =
    """
    package demo

    /** Adds two numbers. */
    fun add(a: Int, b: Int): Int {
        val sum = a + b
        return sum
    }

    fun main() {
        println(add(2, 3))
    }
    """.trimIndent()

@Composable
fun QrScene(words: ContentWords) {
    Column(Modifier.testTag("qr").background(LocalKitColors.current.ground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KitQr(SCENE_LINK, words.qrLabel)
        KitQr("a".repeat(3000), words.qrLabel) // too long: words instead of a code
    }
}

@Composable
fun TextViewerScene(words: ContentWords) {
    KitViewerFrame(
        "RELEASE.md", Modifier.fillMaxSize().testTag("viewer"), path = "docs/RELEASE.md",
        primary = KitViewerAction(words.ask, {}), secondary = KitViewerAction(words.shareWifi, {}), onClose = {},
    ) { KitViewerTextBody(words.prose) }
}

@Composable
fun CodeViewerScene(words: ContentWords) {
    KitViewerFrame(
        "Main.kt", Modifier.fillMaxSize().testTag("viewer"), path = "src/demo/Main.kt · Line 5",
        primary = KitViewerAction(words.ask, {}), onClose = {},
    ) { KitViewerCodeBody(SceneCode, initialLine = 5) }
}

@Composable
fun BinaryViewerScene(words: ContentWords) {
    KitViewerFrame("build.zip", Modifier.fillMaxSize().testTag("viewer"), path = "out/build.zip", onClose = {}) {
        KitViewerBinaryBody("application/zip", 2_516_582, action = KitViewerAction(words.save, {}))
    }
}

@Composable
fun ImageViewerScene() {
    val painter = remember { BitmapPainter(scenePicture(320, 200)) }
    KitViewerFrame("diagram.png", Modifier.fillMaxSize().testTag("viewer"), path = "assets/diagram.png", onClose = {}) {
        KitImageView(painter, "diagram", Modifier.fillMaxSize().padding(bottom = 8.dp))
    }
}

@Composable
fun PdfPagesScene() {
    val page = remember { scenePage(170, 220) }
    Row(
        Modifier.fillMaxWidth().testTag("pdf").background(LocalKitColors.current.ground).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KitPdfPage(1, 12, KitPdfPageState.Ready(page), Modifier.weight(1f))
        KitPdfPage(2, 12, KitPdfPageState.Loading(aspect = 170f / 220f), Modifier.weight(1f))
        KitPdfPage(3, 12, KitPdfPageState.Failed(), Modifier.weight(1f), onRetry = {})
    }
}

/** A flat test picture: sky, ground and a sun, drawn without any image file. */
fun scenePicture(w: Int, h: Int): ImageBitmap =
    ImageBitmap(w, h).also { image ->
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), Paint().apply { color = Color(0xFF8EC5E8) })
        canvas.drawRect(Rect(0f, h * 0.65f, w.toFloat(), h.toFloat()), Paint().apply { color = Color(0xFF5E8C4A) })
        canvas.drawCircle(Offset(w * 0.75f, h * 0.3f), h * 0.12f, Paint().apply { color = Color(0xFFF2C14E) })
    }

/** A white "page" with grey text bars, as the renderer would return. */
fun scenePage(w: Int, h: Int): ImageBitmap =
    ImageBitmap(w, h).also { image ->
        val canvas = Canvas(image)
        canvas.drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), Paint().apply { color = Color.White })
        val bar = Paint().apply { color = Color(0xFF9A9A9A) }
        var y = 18f
        while (y < h - 18f) {
            canvas.drawRect(Rect(14f, y, w - 14f - (y.toInt() % 40), y + 5f), bar)
            y += 14f
        }
    }
