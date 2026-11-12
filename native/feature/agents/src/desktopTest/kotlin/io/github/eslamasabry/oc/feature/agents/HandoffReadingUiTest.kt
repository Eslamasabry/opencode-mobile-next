package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.agents.hub.AgentDraftRecord
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentDraftPanel
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * AG5 (gate, w4-settings-truth): the conversation-copy page draws the record as the conversation, leaves tool output
 * and errors out of that reading, keeps the whole record under Details, and passes both through the display
 * redactor, so a key the record builder missed is still never drawn.
 */
@OptIn(ExperimentalTestApi::class)
class HandoffReadingUiTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun restore() = Locale.setDefault(previousLocale)

    // A fake key (the shape of an Anthropic key, never a real one), placed raw in the record on purpose.
    private val fakeKey = "sk-ant-api03-FAKEFIXTURE01234"

    private val record =
        listOf(
                "# Conversation handoff",
                "",
                "Source: \"ses_e3477280fffe1UFTUl2SW5rj2r\"",
                "Title: \"List what's in this folder\"",
                "Last state: \"observed\"",
                "",
                "## Conversation",
                "",
                "Older messages omitted for size: 0.",
                "",
                "### User",
                "```",
                "Use the key $fakeKey for this",
                "```",
                "",
                "### Assistant",
                "- Tool \"bash\"; status: \"completed\"; command: \"ls\"; outcome: \"TOOLOUTPUTLINE\"",
                "```",
                "The folder holds two files.",
                "```",
                "Message ended with an error:",
                "```",
                "ProviderError: upstream 502",
                "```",
            )
            .joinToString("\n")

    private fun actions(): AgentsActions =
        Proxy.newProxyInstance(AgentsActions::class.java.classLoader, arrayOf(AgentsActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ReadingActions"
                else -> null
            }
        } as AgentsActions

    @Test
    fun `AG5 the reading shows what was said, never tool output, errors or a key, and Details holds the masked record`() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        KitChatEnvironment(Redactor()).Provide {
                            Box(Modifier.width(412.dp)) {
                                AgentDraftPanel(
                                    AgentsUi(draftRecord = AgentDraftRecord(record, 0, false, record.length)),
                                    actions(),
                                )
                            }
                        }
                    }
                }
            }
            onNodeWithText("List what's in this folder").assertExists()
            onNodeWithText("The folder holds two files.", substring = true).assertExists()
            for (hidden in listOf("TOOLOUTPUTLINE", "ProviderError", "ses_e347", "FAKEFIXTURE"))
                onAllNodesWithText(hidden, substring = true).assertCountEquals(0)

            onNodeWithText("The record as it will be copied").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("ses_e3477280fffe1UFTUl2SW5rj2r", substring = true).assertExists()
            onAllNodesWithText("FAKEFIXTURE", substring = true).assertCountEquals(0)
        }
}
