package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalTestApi::class)
class LimitNotModelGoneCardTest {
    private val free =
        "Free usage limit reached. Connect a provider or subscribe to OpenCode Go to keep going, or switch models."
    private val country = "This model is not available in your country."

    private class ModelWorld(choices: List<ComposerModel>) : FakeChatWorld() {
        override val models: StateFlow<ComposerModels> = MutableStateFlow(ComposerModels(choices = choices))
    }

    @Composable
    private fun Root(scale: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    private fun collectTags(node: SemanticsNode, out: MutableList<String>) {
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }
        for (child in node.children) collectTags(child, out)
    }

    private fun checkFailedCard(
        rows: List<TranscriptRow>,
        choices: List<ComposerModel>,
        scale: Float,
        verify: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) {
        val beforeLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            kitUiTest(360, 800) {
                val world = ModelWorld(choices)
                world.transcript.value = stateOf(*rows.toTypedArray())
                val scene = ChatScene(world = world)
                val state = scene.live().copy(status = null, rows = world.transcript.value.rows)
                setContent {
                    Root(scale) {
                        ChatScreen(
                            state,
                            scene.actions,
                            scene.host,
                            header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                            facts = TurnFactsUi.Empty,
                            modelChoices = choices,
                            canGoBack = false,
                        )
                    }
                }
                waitForIdle()
                verify()
            }
        } finally {
            Locale.setDefault(beforeLocale)
        }
    }

    @Test
    fun `B-523-018 the free daily limit card never says the model is gone and leads with Connect a provider`() {
        val rows =
            listOf(
                prompt("u1", "go"),
                FooterRow(
                    "footer:u1",
                    "u1",
                    failed = true,
                    reason = free,
                    createdMs = 200L,
                    providerId = "opencode",
                    modelId = "big-pickle",
                ),
            )
        val choices =
            listOf(
                ComposerModel(
                    "opencode",
                    "deepseek-v4.1-flash-free",
                    "DeepSeek V4.1 Flash Free",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
            )
        for (scale in listOf(1.0f, 1.3f)) {
            checkFailedCard(rows, choices, scale) {
                onAllNodesWithText("any more", substring = true).assertCountEquals(0)
                onAllNodesWithTag("error-action-use-model").assertCountEquals(0)
                onAllNodesWithTag("error-action-choose-model").assertCountEquals(0)
                val tags = mutableListOf<String>()
                collectTags(onRoot().fetchSemanticsNode(), tags)
                val firstAction = tags.firstOrNull { it.startsWith("error-action-") }
                assertEquals("error-action-connect-provider", firstAction, "scale=$scale tags=$tags")
                onNodeWithText("This model's usage limit is reached for now.").assertExists()
                onAllNodesWithText("The agent has stopped. Send a message to ask it to carry on.")
                    .assertCountEquals(0)
            }
        }
    }

    @Test
    fun `B-523-019 a country refusal offers one tap to a model not refused in this chat`() {
        val rows =
            listOf(
                prompt("u1", "first"),
                FooterRow(
                    "footer:u1",
                    "u1",
                    failed = true,
                    reason = country,
                    errorKind = MessageErrorKind.ProviderAuth,
                    createdMs = 100L,
                    providerId = "opencode",
                    modelId = "beam-free",
                ),
                prompt("u2", "second"),
                FooterRow(
                    "footer:u2",
                    "u2",
                    failed = true,
                    reason = country,
                    errorKind = MessageErrorKind.ProviderAuth,
                    createdMs = 200L,
                    providerId = "opencode",
                    modelId = "muse-spark-1.3-contributor-free",
                ),
            )
        val choices =
            listOf(
                ComposerModel(
                    "opencode",
                    "beam-free",
                    "Beam Free",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
                ComposerModel(
                    "opencode",
                    "big-pickle",
                    "Big Pickle",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
            )
        for (scale in listOf(1.0f, 1.3f)) {
            checkFailedCard(rows, choices, scale) {
                onAllNodesWithText("Use Big Pickle and resend").onFirst().assertExists()
                onAllNodesWithText("Use Beam Free and resend").assertCountEquals(0)
                onAllNodesWithTag("error-action-providers").assertCountEquals(0)
            }
        }
    }
}
