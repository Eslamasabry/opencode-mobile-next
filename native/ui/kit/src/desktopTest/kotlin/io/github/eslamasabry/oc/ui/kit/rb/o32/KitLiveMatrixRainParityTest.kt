package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveMatrixRain
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart at 8302de537. */
class KitLiveMatrixRainParityTest {
    private val style = KitLiveMatrixRain(KitLiveMatrixRain.ASCII_GLYPHS)

    @Test fun idAndConstants() {
        LiveChecks.registered(KitLiveMatrixRain(), "matrixRain")
        assertEquals(2.0, style.ceilingFactor)
        assertEquals(12, KitLiveMatrixRain.LEVELS)
        assertEquals(14, KitLiveMatrixRain.TRAIL)
        assertEquals("ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:.=*+<>", KitLiveMatrixRain.DEFAULT_GLYPHS)
        assertEquals("0123456789:.=*+<>|/ZXYWMK", KitLiveMatrixRain.ASCII_GLYPHS)
        assertEquals(KitLiveMatrixRain.DEFAULT_GLYPHS, KitLiveMatrixRain().glyphs)
    }

    @Test fun columnSpeedsFollowTheDartPattern() {
        // 2.6 * (.6 + ((i * 7) % 5) / 8): five speeds in the order 0, 2, 4, 1, 3 eighths.
        val expected = listOf(0, 2, 4, 1, 3).map { 2.6 * (.6 + it / 8.0) }
        for (i in 0 until 10) assertEquals(expected[i % 5], KitLiveMatrixRain.speedOf(i), 1e-12)
    }

    @Test fun theTrailFadesFromTheHead() {
        // Idle head level .85 → round(.85 * 12) = 10; the next ones (1 - k/15)^1.5 * .5 of that.
        assertEquals(10, KitLiveMatrixRain.levelOf(.85, 0))
        assertEquals(12, KitLiveMatrixRain.levelOf(1.0, 0))
        assertEquals(Math.round(.85 * Math.pow(1 - 1 / 15.0, 1.5) * .5 * 12).toInt(), KitLiveMatrixRain.levelOf(.85, 1))
        // The tail fades out before the trail's end.
        assertEquals(0, KitLiveMatrixRain.levelOf(.85, 13))
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)

    @Test fun underItsCeiling() = LiveChecks.underCeiling(style)
}
