package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Capture only (no comparison): the chat while the second reply is awaited, with the one-time GitHub star ask under
 * the live line, on the real screen with the real composer, in Solid and Glass, light and dark. Writes PNGs to
 * `STAR_PROMPT_OUT`; does nothing when that is unset, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class StarPromptRenders {
    private val out = System.getenv("STAR_PROMPT_OUT")?.let { File(it).also(File::mkdirs) }

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) {}

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val voice =
        object : KitComposerVoice {
            @Composable
            override fun Mic(field: KitVoiceField, modifier: Modifier) {
                KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
            }

            @Composable override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "checkout",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                    voice = voice,
                )
            }
        }

    @Composable
    private fun Screen(style: Style, dark: Boolean, ask: Boolean, tag: String) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
            KitTheme(
                appearance =
                    KitAppearance(
                        mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                        style = style,
                        motion = MotionLevel.Off,
                    ),
                platform = DesktopKitPlatform(),
                localeTag = tag,
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        val rows: List<TranscriptRow> = listOf(prompt("u2", SECOND)) + turn(1, ask = FIRST, answer = ANSWER).asReversed()
                        val state =
                            ChatUi.loading(Ref).copy(
                                title = "Fix the flaky checkout test",
                                phase = ChatPhase.Live,
                                rows = rows.toPersistentList(),
                                live = LiveTurnUi(stopping = false),
                                starAsk = ask,
                            )
                        ChatScreen(state, ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate), host)
                    }
                }
            }
        }
    }

    private fun shoot(style: Style, dark: Boolean, name: String, tag: String = "en") {
        Locale.setDefault(Locale.forLanguageTag(tag))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { Screen(style, dark, ask = true, tag = tag) }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(out, "$name.png"))
        }
        Locale.setDefault(Locale.forLanguageTag("en"))
    }

    @Test
    fun `star prompt renders`() {
        if (out == null) return
        for (style in listOf(Style.Solid, Style.Glass)) for (dark in listOf(false, true)) {
            shoot(style, dark, "variant-d-${style.storageName}-${if (dark) "dark" else "light"}")
        }
        shoot(Style.Solid, dark = false, name = "variant-d-rtl-ar-solid-light", tag = "ar")
    }

    private companion object {
        const val WIDTH = 1081
        const val HEIGHT = 2402
        const val DENSITY = 2.625f
        const val FIRST = "Why does the checkout test fail one run in five?"
        const val ANSWER = "The coupon total is read before the network call settles. Waiting on the settled state fixes it."
        const val SECOND = "Good, now make the same fix in the cart test."
    }
}
