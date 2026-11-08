package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.settings.teaching.ActiveNudge
import io.github.eslamasabry.oc.engine.settings.teaching.NudgeId
import io.github.eslamasabry.oc.feature.chat.composer.ComposerProjectFileSheet
import io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeActions
import io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeSlot
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContinuedLine
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * S28 captures for the side-by-sides (docs/design/native-ui-rebuild/visual/native-s28-chat-tools). Capture only, no
 * comparison: runs only when OC_S28_SHOTS names an output folder, otherwise each test returns at once.
 */
@OptIn(ExperimentalTestApi::class)
class S28ShotsTest {
    private val out = System.getenv("OC_S28_SHOTS")?.let(::File)

    private fun shoot(name: String, height: Int = 2402, act: ComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit) {
        val dir = out ?: return
        dir.mkdirs()
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(1081, height) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            KitChatEnvironment(Redactor()).Provide {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    KitSheetHost(Modifier.fillMaxSize()) { content() }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                act()
                waitForIdle()
                mainClock.advanceTimeBy(1_500)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name-${if (dark) "dark" else "light"}.png"))
            }
        }
    }

    private val request =
        PermissionRequest(
            RequestId("p1"),
            SessionId("ses_1"),
            "bash",
            persistentListOf("npm test -- --watch=false", "npm *"),
            JsonObject(
                mapOf(
                    "command" to JsonPrimitive("npm test -- --watch=false"),
                    "cwd" to JsonPrimitive("/home/me/neon-breaker"),
                )
            ),
            persistentListOf("npm *"),
            null,
            true,
            null,
        )

    private val card =
        RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request, project = "neon-breaker", directory = "/home/me/neon-breaker"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(request),
        )

    @Test
    fun `S28 permission details sheet`() =
        shoot("permission-details", act = { onAllNodesWithText("Details").onFirst().performClick() }) {
            Column(Modifier.padding(top = 280.dp)) {
                ChatRequestSlot(RequestSlotUi(card), NoActions, nowMs = null)
            }
        }

    @Test
    fun `S28 project file sheet`() =
        shoot(
            "project-file-sheet",
            act = { onAllNodes(hasSetTextAction()).onFirst().performTextInput("game") },
        ) {
            ComposerProjectFileSheet(
                visible = true,
                search = { Outcome.Ok(listOf("src/game.js", "src/game.test.js", "docs/game-design.md", "README.md")) },
                onPick = {},
                onDismiss = {},
            )
        }

    @Test
    fun `S28 nudge and continued line`() =
        shoot("nudge-continued", height = 900) {
            Column(Modifier.padding(16.dp)) {
                KitContinuedLine("Continued from Design review", onOpen = {})
                ChatNudgeSlot(
                    ActiveNudge(NudgeId.ReviewChanges, "ses_1"),
                    filesChanged = 3,
                    actions = ChatNudgeActions(approvals = null, review = {}, compact = {}, done = {}),
                )
                ChatNudgeSlot(
                    ActiveNudge(NudgeId.Compact, "ses_1", detail = "85"),
                    filesChanged = 0,
                    actions = ChatNudgeActions(approvals = null, review = {}, compact = {}, done = {}),
                )
            }
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
}
