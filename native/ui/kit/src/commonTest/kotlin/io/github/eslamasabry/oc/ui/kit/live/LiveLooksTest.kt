package io.github.eslamasabry.oc.ui.kit.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E20-S28: stored ids map to looks, the picker offers what is drawn, and the renderer follows the API level. */
class LiveLooksTest {
    private val builderA = listOf("aurora", "mesh", "silk", "caustics", "liquidBlobs", "glowSpill")

    @Test
    fun `B-KIT-LIVE-010 every Flutter look id is kept and each shader look answers to its own id`() {
        assertEquals(21, LiveLooks.ids.size)
        assertEquals(LiveLooks.ids.toSet().size, LiveLooks.ids.size)
        for (id in builderA) assertEquals(id, LiveLooks.shader(id)?.id, id)
        for (id in LiveLooks.ids) LiveLooks.shader(id)?.let { assertEquals(id, it.id) }
    }

    @Test
    fun `B-KIT-LIVE-011 the painted aurora, unknown ids and null are not shader looks`() {
        assertNull(LiveLooks.shader("auroraImage"))
        assertNull(LiveLooks.shader("noSuchLook"))
        assertNull(LiveLooks.shader(null))
        assertTrue(LiveLooks.draws("auroraImage"))
        assertFalse(LiveLooks.draws("noSuchLook"))
    }

    @Test
    fun `B-KIT-LIVE-012 the picker offers the painted aurora and every shader look in Flutter's order`() {
        val offered = LiveLooks.offered
        assertTrue(offered.containsAll(builderA + "auroraImage"))
        assertEquals(LiveLooks.ids.filter { it in offered }, offered)
        assertEquals(offered.size, offered.map(LiveLooks::label).toSet().size, "each look has its own name")
    }

    @Test
    fun `B-KIT-LIVE-013 shaders run from Android 13, below it the look shows its still fallback`() {
        assertFalse(androidRunsLiveShaders(26))
        assertFalse(androidRunsLiveShaders(32))
        assertTrue(androidRunsLiveShaders(33))
        assertTrue(androidRunsLiveShaders(37))
        assertEquals(LiveRender.Shader, liveRenderFor("mesh", shows = true, shadersSupported = androidRunsLiveShaders(33)))
        assertEquals(LiveRender.Still, liveRenderFor("mesh", shows = true, shadersSupported = androidRunsLiveShaders(32)))
    }

    @Test
    fun `B-KIT-LIVE-014 the painted aurora draws on every API, off and unknown looks keep the flat ground`() {
        assertEquals(LiveRender.Painted, liveRenderFor("auroraImage", shows = true, shadersSupported = false))
        assertEquals(LiveRender.Painted, liveRenderFor("auroraImage", shows = true, shadersSupported = true))
        assertEquals(LiveRender.Flat, liveRenderFor("mesh", shows = false, shadersSupported = true))
        assertEquals(LiveRender.Flat, liveRenderFor("noSuchLook", shows = true, shadersSupported = true))
        assertEquals(LiveRender.Flat, liveRenderFor(null, shows = true, shadersSupported = true))
    }

    @Test
    fun `B-KIT-LIVE-015 every look's shader starts from the shared prelude contract`() {
        for (id in builderA) {
            val look = LiveLooks.shader(id)!!
            assertTrue("half4 main(float2 fragCoord)" in look.shader, id)
            assertTrue("liveOut(" in look.shader, "$id ends through liveOut (dither and contrast cap)")
            assertFalse("uniform float uTime" in look.shader, "$id must not redeclare shared uniforms")
        }
    }
}
