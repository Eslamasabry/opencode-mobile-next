package io.github.eslamasabry.oc.ui.kit.rb.o52

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitSteerBoxAvailabilityTest {
    @Test
    fun `B-KIT-051 unavailable dictation holds only the microphone and keeps typed sending usable`() =
        runComposeUiTest {
            val sent = mutableListOf<String>()
            var dictated = 0
            val field = TextFieldState("  Typed message  ")
            setContent {
                KitTheme(platform = FakeKitPlatform()) {
                    KitSteerBox(
                        "New message",
                        field,
                        KitSteerBoxPhase.Ready,
                        onSend = sent::add,
                        onDictate = { dictated++ },
                        onClose = {},
                        dictationEnabled = KitEnabled.Disabled("Dictation is unavailable"),
                    )
                }
            }
            onNodeWithTag("widget-mic").assertIsNotEnabled().performTouchInput { click() }
            assertEquals(0, dictated)
            onNodeWithTag("widget-send").assertIsEnabled().performClick()
            assertEquals(listOf("Typed message"), sent)
        }

    @Test
    fun `B-KIT-051 empty composer shows both label and in field hint then removes only the hint`() =
        runComposeUiTest {
            val field = TextFieldState()
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitSteerBox(
                        "Add to this turn",
                        field,
                        KitSteerBoxPhase.Ready,
                        onSend = {},
                        onDictate = {},
                        onClose = {},
                    )
                }
            }
            onAllNodesWithText("Write a message").assertCountEquals(2)
            onNode(hasSetTextAction()).performTextInput("Keep this draft")
            onAllNodesWithText("Write a message").assertCountEquals(1)
            onNodeWithText("Keep this draft").assertExists()
        }
}
