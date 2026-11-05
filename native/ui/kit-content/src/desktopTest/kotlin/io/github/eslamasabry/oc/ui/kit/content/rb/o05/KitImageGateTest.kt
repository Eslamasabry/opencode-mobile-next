package io.github.eslamasabry.oc.ui.kit.content.rb.o05

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.image.KitAvatar
import io.github.eslamasabry.oc.ui.kit.content.image.KitAvatarSize
import io.github.eslamasabry.oc.ui.kit.content.image.KitImage
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageFit
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageSource
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomView
import io.github.eslamasabry.oc.ui.kit.content.image.rememberKitZoomState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_image_golden_test.dart (KitImage, KitAvatar, KitZoom). */
class KitImageGateTest {
    // The 1x1 opaque PNG of the Dart test (kit_image_golden_test.dart:28-38).
    private val onePixelPng = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00,
        0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
        0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
        0x18, 0xDD.toByte(), 0x8D.toByte(), 0xB0.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )
    private val memory get() = KitImageSource.Memory(onePixelPng)
    private val failing = KitImageSource.Loader("failing") { error("kit_image_golden_test: forced failure") }
    private val pending = KitImageSource.Loader("pending") { awaitCancellation() }

    private fun list(entries: List<@Composable () -> Unit>): @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { // Flutter's Column centres its children across
            for (entry in entries) {
                entry()
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    private fun box(child: @Composable () -> Unit): @Composable () -> Unit = {
        androidx.compose.foundation.layout.Box(Modifier.width(120.dp).height(90.dp)) { child() }
    }

    private val loaded = list(
        listOf(
            box { KitImage(memory, "A photo", Modifier.size(120.dp, 90.dp)) },
            box { KitImage(memory, "A photo", Modifier.size(120.dp, 90.dp), fit = KitImageFit.Cover) },
            box { KitImage(memory, "A photo", Modifier.size(120.dp, 90.dp), shape = KitShapeId.Panel) },
            box { KitImage(memory, "A photo", Modifier.size(120.dp, 90.dp), shape = KitShapeId.Circle) },
        ),
    )
    private val loading: @Composable () -> Unit = { KitImage(failing, "A photo", Modifier.size(160.dp, 120.dp)) }
    private fun failed(wide: Boolean): @Composable () -> Unit = { KitImage(failing, "A photo", Modifier.size(if (wide) 200.dp else 80.dp, 120.dp)) }

    private fun avatars(arabic: Boolean = false): @Composable () -> Unit {
        val name = if (arabic) "محمد علي" else "Open AI"
        return list(
            listOf(
                { KitAvatar(name) },
                { KitAvatar(name, size = KitAvatarSize.Mark) },
                { KitAvatar(if (arabic) "هذا الهاتف" else "This phone", icon = KitIcons.phone) },
                { KitAvatar(if (arabic) "زميل" else "A teammate", image = memory) },
            ),
        )
    }

    private fun avatarStates(failed: Boolean): @Composable () -> Unit = list(
        listOf(
            { KitAvatar("A teammate", image = if (failed) failing else pending) },
            { KitAvatar("A teammate", size = KitAvatarSize.Mark, image = if (failed) failing else pending) },
        ),
    )

    private fun zoom(zoomed: Boolean): @Composable () -> Unit = {
        val state = rememberKitZoomState()
        if (zoomed) LaunchedEffect(Unit) { state.zoomIn() }
        androidx.compose.foundation.layout.Box(Modifier.size(280.dp)) {
            KitZoomView("Screenshot.png", Modifier.size(280.dp), state) {
                androidx.compose.foundation.layout.Box(Modifier.size(160.dp, 120.dp).background(Color(0xFF3D6BFF)))
            }
        }
    }

    private val default = list(listOf(box { KitImage(memory, "A photo", Modifier.size(120.dp, 90.dp)) }, { KitAvatar("Open AI") }))

    @Test fun states() {
        FlutterGoldenGate.check("kit_image_loaded", content = loaded)
        FlutterGoldenGate.check("kit_image_loading", content = loading)
        FlutterGoldenGate.check("kit_image_error_narrow", content = failed(false))
        FlutterGoldenGate.check("kit_image_error_wide", content = failed(true))
        FlutterGoldenGate.check("kit_image_avatar", content = avatars())
        FlutterGoldenGate.check("kit_image_avatar_loading", content = avatarStates(false))
        FlutterGoldenGate.check("kit_image_avatar_error", content = avatarStates(true))
        FlutterGoldenGate.check("kit_image_zoom_rest", content = zoom(false))
        FlutterGoldenGate.check("kit_image_zoom_zoomed", content = zoom(true))
    }

    @Test fun default_sizes() {
        for (s in listOf("", "_360x800", "_800x1280", "_1280x800", "_1600x1000", "_915x412")) {
            FlutterGoldenGate.check("kit_image_default$s", content = default)
        }
    }

    @Test fun scaled_and_arabic() {
        for (s in listOf("", "_1280x800")) {
            val dark = listOf("dark")
            FlutterGoldenGate.check("kit_image_error_wide_text2$s", modes = dark, content = failed(true))
            FlutterGoldenGate.check("kit_image_avatar_text2$s", modes = dark, content = avatars())
            FlutterGoldenGate.check("kit_image_zoom_rest_text2$s", modes = dark, content = zoom(false))
            FlutterGoldenGate.check("kit_image_error_wide_ar$s", modes = dark, content = failed(true))
            FlutterGoldenGate.check("kit_image_avatar_ar$s", modes = dark, content = avatars(arabic = true))
            FlutterGoldenGate.check("kit_image_zoom_rest_ar$s", modes = dark, content = zoom(false))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_image",
            caps = mapOf("KitAvatar" to "glyph", "KitImage" to "reading", "KitZoom" to "fill"),
            content = { KitAvatar("Open AI") },
        )
    }
}
