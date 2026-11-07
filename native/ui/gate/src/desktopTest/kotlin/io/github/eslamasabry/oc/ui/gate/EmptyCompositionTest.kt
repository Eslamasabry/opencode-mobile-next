package io.github.eslamasabry.oc.ui.gate

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Scoreboard 2026-11-07 A7: the confirm sheet gate case composed `KitConfirm` with no `KitSheetHost`, the desktop kit
 * (not debug) only logged the missing host, and every shot was an empty frame reported as a 8-87 % box. The gate now
 * names an empty composition where the golden has a picture.
 */
class EmptyCompositionTest {
    private val root = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val name = "gate_selftest_empty"
    private val golden = File(root, "looks-gate/flutter/${GateRef.Product.dir}/${name}_light.png")
    private val spec = KitConfirmSpec(title = "Share conversation?", confirmLabel = "Share conversation")

    @BeforeTest
    fun plantGolden() {
        golden.parentFile.mkdirs()
        val img = BufferedImage(412, 915, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 915) for (x in 0 until 412) img.setRGB(x, y, if (y in 500..900 && x in 0..411) 0x202020 else 0xF4F4F4)
        ImageIO.write(img, "png", golden)
    }

    @AfterTest
    fun removeGolden() {
        golden.delete()
    }

    @Test
    fun `G0-12 a confirm composed without its sheet host is reported as an empty composition`() {
        val thrown = assertFailsWith<AssertionError> {
            FlutterGoldenGate.check(name, Frame.Screen, modes = listOf("light")) {
                KitConfirm(spec = spec, onConfirm = {}, onCancel = {})
            }
        }
        val all = listOf(thrown) + thrown.suppressed
        assertTrue(all.any { "empty composition" in it.message.orEmpty() && "KitSheetHost" in it.message.orEmpty() }, "${all.map { it.message }}")
    }

    @Test
    fun `G0-13 the same confirm inside a KitSheetHost draws a picture`() {
        // The picture differs from the planted golden and the layout guard may object; only the emptiness matters.
        val failure: Throwable? = try {
            FlutterGoldenGate.check(name, Frame.Screen, modes = listOf("light")) {
                KitSheetHost { KitConfirm(spec = spec, onConfirm = {}, onCancel = {}) }
            }
            null
        } catch (e: AssertionError) {
            e
        }
        val messages = failure?.let { listOf(it) + it.suppressed }?.map { it.message.orEmpty() }.orEmpty()
        assertTrue(messages.none { "empty composition" in it }, "$messages")
    }
}
