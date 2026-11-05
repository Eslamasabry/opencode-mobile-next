package io.github.eslamasabry.oc.feature.widgetactions.rb.o52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetActionScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WidgetComposePanelTest {
    @Test
    fun `B-NAV-021 uncertain widget send retains its draft and never resends`() = runComposeUiTest {
        var state by mutableStateOf(WidgetActionUi())
        val sent = mutableListOf<String>()
        var closed = 0
        val actions =
            object : WidgetActionActions {
                override fun dictate() {}

                override fun finishDictation() {}

                override fun takeDictatedWords() {}

                override fun send(text: String) {
                    sent += text
                    state = state.copy(phase = WidgetActionPhase.Uncertain)
                }

                override fun close() {
                    closed++
                }

                override fun openConversation() {}
            }
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 915.dp)) { WidgetActionScreen(state, actions) }
            }
        }
        onNodeWithTag("widget-send").assertIsNotEnabled()
        onNodeWithTag("widget-message").performTextInput("Keep the draft")
        onNodeWithTag("widget-send").performClick()
        onNodeWithText("Keep the draft").assertExists()
        onNodeWithTag("widget-send").assertIsNotEnabled()
        assertEquals(listOf("Keep the draft"), sent)
        assertEquals(0, closed)
        runOnIdle { state = state.copy(phase = WidgetActionPhase.Sending) }
        onNodeWithTag("floating-panel-scrim").assertIsNotEnabled().performClick()
        assertEquals(0, closed)
    }
}
