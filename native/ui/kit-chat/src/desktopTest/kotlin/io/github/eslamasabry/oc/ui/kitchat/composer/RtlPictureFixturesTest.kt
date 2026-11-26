@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-606: RTL chat test picture fixtures use real Arabic text instead of forced-RTL English strings
 * (which produce backwards `…Ask OpenCode`), and ellipsis/truncation sits at the visual end (left).
 */
class RtlPictureFixturesTest {
    @Test
    fun `B-606 RTL chat render fixtures use real Arabic text instead of forced-RTL English`() {
        val base = File(System.getProperty("user.dir"), "src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kitchat")
        val polishFile = File(base, "composer/PolishMessageBoxRenderTest.kt")
        val dockFile = File(base, "transcript/ChatJumpDockRenders.kt")
        val polishText = polishFile.readText()
        val dockText = dockFile.readText()

        assertTrue(
            polishText.contains("إيقاف"),
            "PolishMessageBoxRenderTest RTL scene must use real Arabic stop copy instead of English 'Stop'",
        )
        assertFalse(
            Regex("""LayoutDirection\.Rtl[\s\S]{0,300}KitComposerStop\("Stop"""").containsMatchIn(polishText),
            "PolishMessageBoxRenderTest must not force LayoutDirection.Rtl on English 'Stop'",
        )
        assertTrue(
            dockText.contains("اسأل OpenCode…"),
            "ChatJumpDockRenders RTL scene must use real Arabic 'اسأل OpenCode…' instead of English 'Ask OpenCode…'",
        )
        assertTrue(
            dockText.contains("الانتقال إلى الأحدث"),
            "ChatJumpDockRenders RTL scene must use real Arabic jump label",
        )
    }

    @Test
    fun `B-606 Arabic composer hint in RTL puts ellipsis and truncation at the visual end`() {
        runDesktopComposeUiTest(360, 240) {
            val hint = "اسأل OpenCode…"
            setContent {
                RtlComposerTheme {
                    Box(Modifier.width(360.dp)) {
                        KitComposerBox(
                            running = false,
                            stacked = true,
                            hasLeading = true,
                            field = {
                                KitText(
                                    hint,
                                    Modifier.testTag("rtl-hint"),
                                    role = TypeRole.Body,
                                    tone = TextTone.Tertiary,
                                    kind = KitTextKind.Frame,
                                    maxLines = 1,
                                )
                            },
                            leading = { KitComposerAdd("الأدوات", onClick = {}, open = false) },
                            trailing = { KitComposerSend("إرسال", onClick = {}) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            waitForIdle()
            val layout = layoutOf("rtl-hint")
            assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0), "Arabic hint must be RTL")
            val arabicStart = layout.getBoundingBox(0)
            val latinO = layout.getBoundingBox(hint.indexOf('O'))
            val ellipsis = layout.getBoundingBox(hint.lastIndex)
            assertTrue(
                latinO.right <= arabicStart.left + 1f,
                "OpenCode ($latinO) should sit visually after Arabic start ($arabicStart) in RTL",
            )
            assertTrue(
                ellipsis.right <= latinO.left + 1f,
                "Ellipsis ($ellipsis) must sit at visual end (left of OpenCode $latinO) in RTL",
            )
        }
    }

    @Test
    fun `B-606 truncated Arabic composer hint keeps start on right and ellipsis on visual left end`() {
        runDesktopComposeUiTest(200, 200) {
            val longArabic = "اسأل OpenCode عن حالة البناء والاختبارات والتعديلات في هذا المشروع الآن"
            setContent {
                RtlComposerTheme {
                    Box(Modifier.width(140.dp)) {
                        KitText(
                            longArabic,
                            Modifier.fillMaxWidth().testTag("rtl-truncated"),
                            role = TypeRole.Body,
                            tone = TextTone.Tertiary,
                            kind = KitTextKind.Frame,
                            maxLines = 1,
                        )
                    }
                }
            }
            waitForIdle()
            val layout = layoutOf("rtl-truncated")
            assertTrue(layout.isLineEllipsized(0), "Long Arabic hint must ellipsize in a 140 dp box")
            assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0), "Paragraph direction must be RTL")
            val first = layout.getBoundingBox(0)
            val width = layout.size.width.toFloat()
            assertTrue(
                first.right >= width - 2f,
                "First Arabic glyph must anchor at right edge (${first.right} vs $width), leaving ellipsis at left end",
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun RtlComposerTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f, 1f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            KitTheme(
                KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                DesktopKitPlatform(),
                "ar",
                stillFrame = true,
                content = content,
            )
        }
    }

    private fun ComposeUiTest.layoutOf(tag: String): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(out)
        return out.first()
    }
}
