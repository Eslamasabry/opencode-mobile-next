package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.agent.AgentMark
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitMonochromeMarks
import io.github.eslamasabry.oc.ui.kit.logo.agentMarkArt
import io.github.eslamasabry.oc.ui.kit.logo.forGround
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ORANGE = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"#D97757\" d=\"M0 0h24v24H0z\"/></svg>"
private const val MONO = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M0 0h24v24H0z\"/></svg>"

/** Agent logos keep their brand colours (owner 2026-11-09); single-colour brands stay in the text colour. */
@OptIn(ExperimentalTestApi::class)
class KitAgentMarkColorTest {
    @Test
    fun `B-KIT-290 a colour mark is untinted, a mono one takes the text colour, a missing colour file falls back to mono`() {
        val colour = agentMarkArt(ORANGE, MONO, monochrome = false, dark = false)
        assertNotNull(colour)
        assertFalse(colour.tinted)
        val fallback = agentMarkArt(null, MONO, monochrome = false, dark = false)
        assertNotNull(fallback)
        assertTrue(fallback.tinted)
        assertEquals(MONO, fallback.svg)
        assertNull(agentMarkArt(null, null, monochrome = false, dark = true))
    }

    @Test
    fun `B-KIT-291 a monochrome appearance mode falls back to the mono mark`() {
        val art = agentMarkArt(ORANGE, MONO, monochrome = true, dark = false)
        assertNotNull(art)
        assertTrue(art.tinted)
        assertEquals(MONO, art.svg)
    }

    @Test
    fun `B-KIT-292 a brand blue too dark for a dark ground is lifted there and left as shipped on light`() {
        val qwen = "<path fill=\"#082dff\"/>"
        assertEquals(qwen, forGround(qwen, dark = false))
        assertEquals("<path fill=\"#7a90ff\"/>", forGround(qwen, dark = true))
        assertEquals(ORANGE, forGround(ORANGE, dark = true))
    }

    private fun centre(monochrome: Boolean): Color {
        var out = Color.Unspecified
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                    CompositionLocalProvider(
                        LocalKitLogoLoader provides
                            object : KitLogoLoader {
                                override suspend fun svg(id: String, agent: Boolean) = MONO

                                override suspend fun colorSvg(id: String) = ORANGE
                            },
                        LocalKitMonochromeMarks provides monochrome,
                    ) {
                        Box(Modifier.testTag("mark")) { AgentMark("claude", Color.Blue, 48.dp) }
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("mark").captureToImage().toPixelMap()
            out = img[img.width / 2, img.height / 2]
        }
        return out
    }

    @Test
    fun `B-KIT-293 drawn, a colour mark shows its brand colour and not the tint`() {
        val c = centre(monochrome = false)
        assertTrue(c.red > 0.75f && c.green in 0.3f..0.55f && c.blue < 0.5f, "brand orange, got $c")
    }

    @Test
    fun `B-KIT-294 drawn in monochrome mode the same mark takes the tint`() {
        val c = centre(monochrome = true)
        assertTrue(c.blue > 0.9f && c.red < 0.1f, "tint blue, got $c")
    }
}
