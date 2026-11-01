package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.semantics.SemanticsActions
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.tool.semanticsWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The permission card shell (plan 09 §5.1–§5.2, plan 06 §7.8 `KitRequestCard`): the engine words the ask; the card
 * draws the given title, summary, LTR subject and answers, and calls back once per tap.
 */
@OptIn(ExperimentalTestApi::class)
class KitPermissionCardTest {
    private val title = "Run npm install in neon-breaker?"
    private val summary = "The agent waits until you answer. Nothing is lost."

    @Test
    fun `B-CHAT-123 the approval ask shows its words, subject and three answers and each answer fires once`() =
        runComposeUiTest {
            var once = 0
            var always = 0
            var deny = 0
            setContent {
                ChatTestTheme {
                    KitPermissionCard(
                        title = title, summary = summary, subject = "npm install",
                        allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
                        onAllowOnce = { once++ }, onAlways = { always++ }, onDeny = { deny++ },
                    )
                }
            }
            onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(summary, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("npm install", useUnmergedTree = true).assertIsDisplayed()

            onNodeWithText("Allow once").performClick()
            assertEquals(Triple(1, 0, 0), Triple(once, always, deny))
            onNodeWithText("Always allow").performClick()
            assertEquals(Triple(1, 1, 0), Triple(once, always, deny))
            onNodeWithText("Deny").performClick()
            assertEquals(Triple(1, 1, 1), Triple(once, always, deny))
        }

    @Test
    fun `B-CHAT-123 the subject reads left to right in an Arabic interface`() = runComposeUiTest {
        setContent {
            ChatTestTheme(localeTag = "ar") {
                KitPermissionCard(
                    title = "تشغيل أمر؟", summary = null, subject = "git push origin main",
                    allowOnceLabel = "اسمح مرة", alwaysLabel = null, denyLabel = "ارفض",
                    onAllowOnce = {}, onAlways = null, onDeny = {},
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("git push origin main", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(layouts)
        assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
    }

    @Test
    fun `B-CHAT-123 without Always the card offers two answers, and Deny is never the accent colour`() =
        runComposeUiTest {
            var accent = Color.Unspecified
            setContent {
                ChatTestTheme {
                    accent = LocalKitColors.current.accent
                    KitPermissionCard(
                        title = title, summary = null, subject = null,
                        allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                        onAllowOnce = {}, onAlways = null, onDeny = {},
                    )
                }
            }
            onAllNodesWithText("Always allow").assertCountEquals(0)
            fun accentPixels(label: String): Int {
                val map = onNodeWithText(label).captureToImage().toPixelMap()
                var n = 0
                for (x in 0 until map.width) for (y in 0 until map.height) if (map[x, y] == accent) n++
                return n
            }
            assertTrue(accentPixels("Allow once") > 0, "Allow once is the primary answer")
            assertEquals(0, accentPixels("Deny"), "Deny is never drawn in the primary colour")
        }

    @Test
    fun `B-CHAT-123 a long subject scrolls inside the card, which stays within 45 percent of the window`() =
        runComposeUiTest {
            var windowHeightPx = 0
            var density = 1f
            setContent {
                ChatTestTheme {
                    windowHeightPx = LocalWindowInfo.current.containerSize.height
                    density = androidx.compose.ui.platform.LocalDensity.current.density
                    KitPermissionCard(
                        title = title, summary = summary,
                        subject = (1..80).joinToString("\n") { "packages/app-$it/src/index.ts" },
                        allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
                        onAllowOnce = {}, onAlways = {}, onDeny = {},
                        modifier = Modifier.testTag("card"),
                    )
                }
            }
            assertTrue(windowHeightPx > 0, "the test window has a height")
            val card = onNodeWithTag("card").fetchSemanticsNode().size
            assertTrue(card.height <= windowHeightPx * 0.45f + 1, "card ${card.height} px of window $windowHeightPx px")
            assertTrue(card.width <= 700 * density + 1, "card ${card.width} px wide")
            // The answers stay in sight under the scrolling words.
            onNodeWithText("Allow once").assertIsDisplayed()
            onNodeWithText("Deny").assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-123 a card that cannot answer yet says why and sends nothing`() = runComposeUiTest {
        var calls = 0
        setContent {
            ChatTestTheme {
                KitPermissionCard(
                    title = title, summary = null, subject = "npm install",
                    allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
                    onAllowOnce = { calls++ }, onAlways = { calls++ }, onDeny = { calls++ },
                    enabled = false, disabledReason = "Sending your answer",
                )
            }
        }
        onNodeWithText("Allow once").assertIsNotEnabled().performClick()
        onNodeWithText("Deny").assertIsNotEnabled().performClick()
        onNodeWithText("Always allow").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
    }

    @Test
    fun `B-SEC-094 a registered secret in the asked command never reaches the semantics tree`() = runComposeUiTest {
        val canary = "sk-canary-123456"
        setContent {
            ChatTestTheme(redactor = Redactor().apply { registerSecret(canary) }) {
                KitPermissionCard(
                    title = title, summary = null, subject = "curl -H 'x-key: $canary' https://example.com",
                    allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                    onAllowOnce = {}, onAlways = null, onDeny = {},
                )
            }
        }
        val words = semanticsWords()
        assertFalse(canary in words, "the canary reached the semantics tree:\n$words")
        assertTrue(Redactor.MASK in words)
    }
}
