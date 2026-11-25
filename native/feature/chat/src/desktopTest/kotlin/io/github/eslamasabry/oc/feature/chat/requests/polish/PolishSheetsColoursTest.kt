package io.github.eslamasabry.oc.feature.chat.requests.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

private const val PHASE = "before"

private const val W_PX = 1080
private const val H_PX = 2400

/**
 * B-587 (sweep 2026-11-25, finding 44): a Run request's Details sheet says the tool in one plain line, with no Details
 * fold inside, and the pictures of that sheet at 360 dp, in Solid, dark and light, at text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class PolishSheetsColoursTest {
    private var previousLocale: Locale = Locale.getDefault()

    private val out = File("build/polish-render").also { it.mkdirs() }

    private val inSheet = hasAnyAncestor(hasTestTag("chat-permission-details"))

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private fun card(permission: String, patterns: List<String>, always: Boolean): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId("p1"), SessionId("ses_1"), permission, patterns.toImmutableList(),
                JsonObject(emptyMap()), persistentListOf("*"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request),
            canAlwaysAllow = always,
            captured = PendingRequest.Permission(request),
        )
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

    @Composable
    private fun Slot(state: RequestSlotUi, mode: AppearanceMode) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                KitChatEnvironment(Redactor()).Provide {
                    KitSheetHost { ChatRequestSlot(state, NoActions, nowMs = null) }
                }
            }
        }
    }

    private fun ComposeUiTest.openRunSheet(mode: AppearanceMode, fontScale: Float) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                Slot(RequestSlotUi(card("bash", listOf("npm test"), always = false)), mode)
            }
        }
        onAllNodesWithText("Details").onFirst().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.greenCount(node: SemanticsNodeInteraction): Int {
        val map = node.captureToImage().toPixelMap()
        var green = 0
        for (x in 0 until map.width)
            for (y in 0 until map.height) {
                val p = map[x, y].toArgb()
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                if (g - r >= 40 && g - b >= 40) green++
            }
        return green
    }

    private fun shot(mode: AppearanceMode, scale: Float) {
        val look = if (mode == AppearanceMode.Dark) "dark" else "light"
        val text = if (scale == 1.3f) "text13" else "text10"
        val file = File(out, "44-$PHASE-details-run-$look-$text.png")
        kitUiTest(W_PX, H_PX) {
            openRunSheet(mode, scale)
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
        assertTrue(file.isFile, "the picture ${file.name} was written")
    }

    @Test
    fun `B-587 the Details sheet says the tool in one line with no Details fold inside`() = kitUiTest(W_PX, H_PX) {
        openRunSheet(AppearanceMode.Dark, 1f)
        onNode(hasText("Tool: Terminal command") and inSheet, useUnmergedTree = true).assertExists()
        onNode(hasText("Tool: Terminal command") and inSheet).assertExists()
        onNode(hasText("Details") and inSheet).assertDoesNotExist()
        onNode(hasText("Details") and inSheet, useUnmergedTree = true).assertDoesNotExist()
        val allow = onNode(hasText("Allow once") and hasClickAction())
        val green = greenCount(allow)
        assertTrue(green > 0, "Allow once is the sheet's green answer ($green px)")
    }

    @Test
    fun pictures() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            shot(mode, 1f)
            shot(mode, 1.3f)
        }
    }
}
