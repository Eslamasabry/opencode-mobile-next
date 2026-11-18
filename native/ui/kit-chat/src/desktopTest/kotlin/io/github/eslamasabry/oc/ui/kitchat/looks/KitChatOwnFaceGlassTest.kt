@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.looks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormCard
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * glass-truth 2026-11-16, item 1 (chat kit): the voice pill and the form card kept their Solid fill under Glass. Under
 * Solid most of the face is exactly the part's own fill (unchanged); under Glass almost none of it is.
 */
class KitChatOwnFaceGlassTest {
    private class Part(val name: String, val tag: String, val fill: (KitColors) -> Color, val content: @Composable () -> Unit)

    private val parts =
        listOf(
            Part("form card", "own-face", { it.attentionSurface.compositeOver(it.surface1) }) {
                Box(Modifier.width(360.dp).testTag("own-face")) { KitFormCard("A few questions", null, "Answer", {}) }
            },
        )

    private fun ownShare(style: Style, part: Part): Float {
        var share = 0f
        runDesktopComposeUiTest(420, 600) {
            var fill = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(KitAppearance(style = style, mode = AppearanceMode.Dark, motion = MotionLevel.Full), ChatFakePlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            fill = part.fill(LocalKitColors.current).compositeOver(LocalKitColors.current.ground)
                            Box(Modifier.fillMaxSize()) { part.content() }
                        }
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag(part.tag, useUnmergedTree = true).captureToImage().toPixelMap()
            var same = 0
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img[x, y]
                if (abs(c.red - fill.red) < .006f && abs(c.green - fill.green) < .006f && abs(c.blue - fill.blue) < .006f) same++
            }
            share = same.toFloat() / (img.width * img.height).coerceAtLeast(1)
        }
        return share
    }

    @Test
    fun `GLASS-TRUTH-1 under Glass the voice pill and the form card are the style's glass, under Solid their own face`() {
        val failures =
            parts.mapNotNull { part ->
                val solid = ownShare(Style.Solid, part)
                val glass = ownShare(Style.Glass, part)
                println("GLASS-TRUTH-1 ${part.name}: own fill under Solid %.2f, under Glass %.2f".format(solid, glass))
                when {
                    solid < .5f -> "${part.name}: Solid no longer paints its own fill (%.2f)".format(solid)
                    glass > .15f -> "${part.name}: Glass still paints the Solid fill (%.2f)".format(glass)
                    else -> null
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
