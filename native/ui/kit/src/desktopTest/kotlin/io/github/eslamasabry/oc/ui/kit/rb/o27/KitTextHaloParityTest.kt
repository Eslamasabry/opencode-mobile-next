package io.github.eslamasabry.oc.ui.kit.rb.o27

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.text.KitTextHalo
import io.github.eslamasabry.oc.ui.kit.text.KitTextHaloCover
import io.github.eslamasabry.oc.ui.kit.text.KitTextHaloSpec
import io.github.eslamasabry.oc.ui.kit.text.kitTextHaloShadows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** part-kit_text_halo: every constant and rule of `lib/ui/kit/kit_text_halo.dart` at 8302de537. */
@OptIn(ExperimentalTestApi::class)
class KitTextHaloParityTest {
    private val ground = Color(0xFF0B0C0E)

    @Test
    fun `constants cover 55 percent six layers blur 3 solid above 90 percent`() {
        assertEquals(.55f, KitTextHaloSpec.COVER)
        assertEquals(6, KitTextHaloSpec.LAYERS)
        assertEquals(3f, KitTextHaloSpec.BLUR_RADIUS)
        assertEquals(.9f, KitTextHaloSpec.SOLID_ALPHA)
    }

    @Test
    fun `shadowsFor is one soft shadow in the ground six times with no offset`() {
        val shadows = KitTextHaloSpec.shadowsFor(ground)
        assertEquals(List(6) { Shadow(ground, blurRadius = 3f) }, shadows)
    }

    @Test
    fun `a fill covers only when it is more than 90 percent opaque`() {
        assertFalse(KitTextHaloSpec.coversFill(null))
        // Compose keeps an sRGB alpha in 8 bits, so .9f is stored as 230/255 (above .9): test clearly either side.
        assertFalse(KitTextHaloSpec.coversFill(Color.White.copy(alpha = .85f)))
        assertTrue(KitTextHaloSpec.coversFill(Color.White.copy(alpha = .95f)))
        assertTrue(KitTextHaloSpec.coversFill(Color.White))
    }

    @Test
    fun `a mask covers unless it only cuts dstIn or dstOut`() {
        assertFalse(KitTextHaloSpec.coversMask(BlendMode.DstIn))
        assertFalse(KitTextHaloSpec.coversMask(BlendMode.DstOut))
        assertTrue(KitTextHaloSpec.coversMask(BlendMode.SrcIn))
        assertTrue(KitTextHaloSpec.coversMask(BlendMode.Modulate))
    }

    @Test
    fun `no ground or a covered place gives no halo`() {
        assertNull(KitTextHaloSpec.shadows(null, covered = false))
        assertNull(KitTextHaloSpec.shadows(ground, covered = true))
        assertEquals(6, KitTextHaloSpec.shadows(ground, covered = false)?.size)
    }

    @Test
    fun `the composition reads the nearest halo and a solid cover hides it`() = runComposeUiTest {
        val seen = mutableMapOf<String, List<Shadow>?>()
        @Composable fun Probe(name: String) { seen[name] = kitTextHaloShadows() }
        setContent {
            Probe("outside")
            KitTextHalo(ground) {
                Probe("ground")
                KitTextHaloCover(Color.White.copy(alpha = .5f)) { Probe("glass") }
                KitTextHaloCover(Color.White) {
                    Probe("card")
                    // A nearer halo puts the words back on its own ground (Dart visit stops there).
                    KitTextHalo(ground) { Probe("inner") }
                }
                KitTextHalo(null) { Probe("off") }
            }
        }
        waitForIdle()
        assertNull(seen.getValue("outside"))
        assertEquals(6, seen.getValue("ground")?.size)
        assertEquals(6, seen.getValue("glass")?.size)
        assertNull(seen.getValue("card"))
        assertEquals(6, seen.getValue("inner")?.size)
        assertNull(seen.getValue("off"))
    }
}
