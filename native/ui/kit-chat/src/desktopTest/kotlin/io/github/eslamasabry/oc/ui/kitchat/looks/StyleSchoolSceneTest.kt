@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.looks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.JUMP_PILL_FACE_TAG
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuPanel
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.composer.COMPOSER_CHIP_FACE_TAG
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.PROMPT_BUBBLE_TAG
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14, finding 9 (MAJOR): Brutal and Paper were only half applied in a conversation. Brutal outlined the
 * message box and the phrase chips but left the model chip, Send, "Jump to latest", the person's bubble, code and the
 * menu soft and borderless; Paper squared some parts and left others round. One scene, as on the phone (2.625 px/dp):
 * the message box with its model chip, its wordless approval chip and Send, a standing jump pill, a bubble, a code block
 * and an open menu. Read from the pixels: in Brutal every part has its ink edge, the parts standing on the page cast
 * the hard shadow and the parts inside the box cast none (shadows never stack); in Paper every part is square and every
 * part but the accent disc is ruled.
 */
class StyleSchoolSceneTest {
    private class Shot(val image: PixelMap, val parts: Map<String, Rect>)

    private fun shot(style: Style, dark: Boolean): Shot {
        var out: Shot? = null
        runDesktopComposeUiTest(W, H) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D), LocalKitRedactor provides Redactor()) {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        val ground = LocalKitColors.current.ground.copy(alpha = 1f)
                        Column(
                            Modifier.fillMaxSize().background(ground).padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            Box(Modifier.width(370.dp)) {
                                KitComposerBox(
                                    running = false,
                                    stacked = true,
                                    field = { KitText("Ask OpenCode") },
                                    hasLeading = true,
                                    leading = { KitComposerAdd("Add", {}) },
                                    chips = {
                                        KitComposerChip("Big Pickle", {}, icon = KitIcons.model, shrinks = true)
                                        KitComposerChip("Ask first", {}, icon = KitIcons.shield, opensPicker = false, semanticsLabel = "Ask first", wordless = true)
                                    },
                                    trailing = { KitComposerSend("Send", {}) },
                                )
                            }
                            KitJumpPill("Jump to latest", {}, visible = true)
                            Box(Modifier.width(370.dp)) { KitPromptBubble("Make the file") }
                            Box(Modifier.width(370.dp)) {
                                KitCodeBlock("print(1)\nprint(2)", KitCodeKind.Code, Modifier.testTag(CODE))
                            }
                            Box(Modifier.padding(start = 40.dp)) {
                                KitMenuPanel(listOf(KitMenuItem("Find", {}), KitMenuItem("Rename", {})), onChoose = {})
                            }
                        }
                    }
                }
            }
            waitForIdle()
            fun bounds(tag: String, index: Int = 0): Rect =
                onAllNodes(hasTestTag(tag), useUnmergedTree = true)[index].fetchSemanticsNode().boundsInRoot
            out =
                Shot(
                    onAllNodes(isRoot())[0].captureToImage().toPixelMap(),
                    mapOf(
                        "message box" to bounds("kit-composer-box"),
                        "model chip" to bounds(COMPOSER_CHIP_FACE_TAG, 0),
                        "approval chip" to bounds(COMPOSER_CHIP_FACE_TAG, 1),
                        "Send disc" to bounds("kit-composer-send-disc"),
                        "jump pill" to bounds(JUMP_PILL_FACE_TAG),
                        "bubble" to bounds(PROMPT_BUBBLE_TAG),
                        "code block" to bounds(CODE),
                        "menu" to bounds("kit-menu"),
                    ),
                )
        }
        return out!!
    }

    private fun rgb(image: PixelMap, x: Float, y: Float): FloatArray {
        val c = image[x.toInt().coerceIn(0, image.width - 1), y.toInt().coerceIn(0, image.height - 1)]
        return floatArrayOf(c.red * 255f, c.green * 255f, c.blue * 255f)
    }

    private fun distance(a: FloatArray, b: FloatArray): Float =
        sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))

    /** A line along the part's top edge: a pixel near it unlike both the face just inside and the page just outside. */
    private fun outlined(image: PixelMap, r: Rect): Boolean {
        val x = r.center.x
        val inside = rgb(image, x, r.top + 5f * D)
        val outside = rgb(image, x, r.top - 5f * D)
        var y = r.top - 2f * D
        while (y <= r.top + 2f * D) {
            val p = rgb(image, x, y)
            if (distance(p, inside) >= LINE && distance(p, outside) >= LINE) return true
            y += .5f
        }
        return false
    }

    /**
     * Brutal's hard shadow lies toward the bottom end: 2 dp under the part's bottom edge, under its middle, a block
     * unlike what lies at that height just before the part's start (where no shadow reaches).
     */
    private fun shadowed(image: PixelMap, r: Rect): Boolean {
        val y = r.bottom + 2f * D
        return distance(rgb(image, r.center.x + 2f * D, y), rgb(image, r.left - 2f * D, y)) >= LINE
    }

    /**
     * A square corner: right at the corner (a third and two thirds of a dp in) the part is drawn, edge or face, not
     * what lies around it. A rounded corner (6 dp or more) leaves the page showing there.
     */
    private fun square(image: PixelMap, r: Rect): Boolean {
        val outside = rgb(image, r.left - 3f * D, r.top - 3f * D)
        return listOf(.33f, .66f).any { t -> distance(rgb(image, r.left + t * D, r.top + t * D), outside) >= LINE }
    }

    private fun brutal(dark: Boolean) {
        val s = shot(Style.Brutal, dark)
        val mode = if (dark) "dark" else "light"
        val faults = mutableListOf<String>()
        for ((name, r) in s.parts) if (!outlined(s.image, r)) faults += "$name has no ink edge"
        for (name in listOf("message box", "jump pill", "bubble", "menu")) if (!shadowed(s.image, s.parts.getValue(name))) faults += "$name on the page casts no hard shadow"
        for (name in listOf("model chip", "approval chip", "Send disc")) if (shadowed(s.image, s.parts.getValue(name))) faults += "$name inside the box casts a second shadow"
        assertTrue(faults.isEmpty(), "Brutal $mode is half applied: ${faults.joinToString("; ")}")
    }

    private fun paper(dark: Boolean) {
        val s = shot(Style.Paper, dark)
        val mode = if (dark) "dark" else "light"
        val faults = mutableListOf<String>()
        for ((name, r) in s.parts) if (!square(s.image, r)) faults += "$name is rounded"
        // Paper's primary is an accent face with no rule; every other part is ruled.
        for ((name, r) in s.parts) if (name != "Send disc" && !outlined(s.image, r)) faults += "$name has no rule"
        for ((name, r) in s.parts) if (shadowed(s.image, r)) faults += "$name casts a shadow"
        assertTrue(faults.isEmpty(), "Paper $mode is half applied: ${faults.joinToString("; ")}")
    }

    @Test fun `B-KIT-STY-05 Brutal light - every part in a conversation has the ink edge, shadows only on the page`() = brutal(dark = false)

    @Test fun `B-KIT-STY-06 Brutal dark - every part in a conversation has the ink edge, shadows only on the page`() = brutal(dark = true)

    @Test fun `B-KIT-STY-07 Paper light - every part in a conversation is square and ruled, none casts a shadow`() = paper(dark = false)

    @Test fun `B-KIT-STY-08 Paper dark - every part in a conversation is square and ruled, none casts a shadow`() = paper(dark = true)

    private companion object {
        const val W = 1080
        const val H = 2100
        const val D = 2.625f
        const val CODE = "scene-code"

        /** Colour distance (0-255 per channel, Euclidean) that reads as a drawn line or block. */
        const val LINE = 40f
    }
}
