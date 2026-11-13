package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E06-S01 / E06-S03 request slot goldens on the desktop (Skiko software raster), light/dark × en plus dark ar, as
 * the composer goldens do. A missing golden is recorded and the test fails once: look at it, then run again. Files:
 * `src/desktopTest/goldens/requests/<scene>-<variant>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class RequestSlotGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String)

    private val variants =
        listOf(Variant("light-en", false, "en"), Variant("dark-en", true, "en"), Variant("dark-ar", true, "ar"))
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens", "requests")
    private val record = System.getProperty("oc.goldens.record") == "true"
    private val session = SessionId("ses_1")

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun permission(id: String, permission: String, pattern: String, state: CardState = CardState.Waiting): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, permission, persistentListOf(pattern), JsonObject(emptyMap()),
                persistentListOf("$pattern *"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = state,
            ask = PermissionAsk.of(request, project = "neon-breaker", directory = "/home/me/neon-breaker"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(request),
        )
    }

    private fun question(id: String): RequestCardUi.Question =
        RequestCardUi.Question(
            id = RequestId(id),
            waitingSinceMs = null,
            state = CardState.Waiting,
            title = "Deploy",
            question = "Ship the fix to staging now?",
            options = persistentListOf("Yes", "Not yet"),
            oneAnswer = true,
            custom = false,
        )

    private val shell = permission("p1", "bash", "npm test")
    private val edit = permission("p2", "edit", "src/game.js")
    private val fetch = permission("p3", "webfetch", "https://example.com/docs")
    private val ask = question("q1")

    private fun scene(scene: String, cards: List<RequestCardUi>, check: ComposeUiTest.() -> Unit = {}) {
        for (variant in variants) {
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runDesktopComposeUiTest(412, 2200) {
                setContent { Themed(variant) { ChatRequestSlot(RequestSlotUi(cards.toImmutableList()), NoActions, nowMs = null) } }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                if (variant.locale == "en") check()
                compare("$scene-${variant.name}")
            }
        }
    }

    @Composable
    private fun Themed(variant: Variant, content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
            platform = DesktopKitPlatform(),
            localeTag = variant.locale,
            stillFrame = true,
        ) {
            KitChatEnvironment(Redactor()).Provide {
                Box(Modifier.width(412.dp).background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    private fun ComposeUiTest.compare(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(golden.width == image.width && golden.height == image.height, "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}")
        val differing = differingPixels(golden, image)
        val share = differing.toDouble() / (image.width * image.height)
        if (share > MAX_SHARE) {
            val actual = File("build/golden-actual/requests/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ (limit ${MAX_SHARE * 100} %); actual: $actual")
        }
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            val far = (0..24 step 8).any { shift -> abs((p shr shift and 0xFF) - (q shr shift and 0xFF)) > CHANNEL_SLACK }
            if (far) count++
        }
        return count
    }

    @Test
    fun `E06-S01 one card`() = scene("one-card", listOf(shell)) {
        onNodeWithContentDescription("Request 1 of 1", substring = true).assertExists()
    }

    @Test
    fun `E06-S01 P-103 two cards, the first in full and the second one line`() = scene("two-cards", listOf(shell, edit)) {
        assertEquals(1, onAllNodesWithText("Allow once").fetchSemanticsNodes().size, "only the first card is open")
        assertEquals(1, onAllNodesWithTag("chat-request-folded").fetchSemanticsNodes().size)
        onNodeWithContentDescription("Request 2 of 2", substring = true).assertExists()
    }

    @Test
    fun `P-103 a one-line request opens in full on tap and the rest stay one line`() {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(412, 2200) {
            setContent {
                Themed(variants.first()) {
                    ChatRequestSlot(RequestSlotUi(listOf(shell, ask, edit, fetch).toImmutableList()), NoActions, nowMs = null)
                }
            }
            waitForIdle()
            assertEquals(3, onAllNodesWithTag("chat-request-folded").fetchSemanticsNodes().size)
            onAllNodesWithTag("chat-request-folded")[1].performClick()
            waitForIdle()
            assertEquals(2, onAllNodesWithTag("chat-request-folded").fetchSemanticsNodes().size)
            assertEquals(2, onAllNodesWithText("Allow once").fetchSemanticsNodes().size, "the opened card has its own answers")
        }
    }

    @Test
    fun `P-103 the card a link opened the chat for is the one in full`() {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(412, 2200) {
            setContent {
                Themed(variants.first()) {
                    ChatRequestSlot(
                        RequestSlotUi(listOf(shell, edit).toImmutableList(), focused = edit.id),
                        NoActions,
                        nowMs = null,
                    )
                }
            }
            waitForIdle()
            assertEquals(1, onAllNodesWithTag("chat-request-folded").fetchSemanticsNodes().size)
            onAllNodesWithText("npm test", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let {
                assertTrue(it.isNotEmpty(), "the first card is the folded one, still named")
            }
        }
    }

    @Test
    fun `E06-S01 four cards in arrival order, each announced with its place and target`() =
        scene("four-cards", listOf(shell, ask, edit, fetch)) {
            for ((index, words) in listOf("npm test", "Ship the fix to staging now?", "game.js", "example.com").withIndex())
                onNodeWithContentDescription("Request ${index + 1} of 4", substring = true).let { node ->
                    val label = node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
                    assertTrue(words in label, "card ${index + 1} is announced with its target: $label")
                }
        }

    @Test
    fun `E06-S03 a stopped run's card says No longer needed, never Needs your decision`() =
        scene("no-longer-needed", listOf(permission("p1", "bash", "npm test", CardState.NoLongerNeeded), edit)) {
            onAllNodesWithText("No longer needed", substring = true).fetchSemanticsNodes().let { assertEquals(1, it.size) }
            // P-109: a plain row on the page's 16 dp edge, not the card's inset (its words sat at 77 dp).
            onNodeWithTag("chat-request-no-longer-needed").assertExists()
            assertEquals(1, onAllNodesWithText("Allow once").fetchSemanticsNodes().size, "only the waiting card has answers")
        }

    private object NoActions : ChatRequestActions {
        override fun shown(id: RequestId) = Unit
        override fun allowOnce(id: RequestId) = Unit
        override fun reject(id: RequestId) = Unit
        override fun askAlwaysAllow(id: RequestId) = Unit
        override fun confirmAlwaysAllow() = Unit
        override fun cancelAlwaysAllow() = Unit
        override fun keepAsking(id: RequestId) = Unit
        override fun choose(id: RequestId, option: String) = Unit
        override fun openOther(id: RequestId) = Unit
        override fun editOther(id: RequestId, text: String) = Unit
        override fun sendOther(id: RequestId) = Unit
        override fun openSheet(id: RequestId) = Unit
        override fun closeSheet(id: RequestId) = Unit
        override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
        override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
        override fun sendSheet(id: RequestId) = Unit
        override fun undo(id: RequestId) = Unit
        override fun retry(id: RequestId) = Unit
    }

    private companion object {
        const val CHANNEL_SLACK = 24
        const val MAX_SHARE = 0.005
    }
}
