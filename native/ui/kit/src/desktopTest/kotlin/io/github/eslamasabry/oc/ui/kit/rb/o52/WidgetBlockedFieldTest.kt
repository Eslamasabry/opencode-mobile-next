package io.github.eslamasabry.oc.ui.kit.rb.o52

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WidgetBlockedFieldTest {
    @Test
    fun `B-KIT-051 blocked composer keeps its draft and gives the reason on the field`() =
        runComposeUiTest {
            val field = TextFieldState("Keep my words")
            var sent = 0
            setContent {
                KitTheme(
                    platform = FakeKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                ) {
                    KitSteerBox(
                        "Add to this turn",
                        field,
                        KitSteerBoxPhase.Ready,
                        { sent++ },
                        {},
                        {},
                        blockedReason = "Check this conversation first",
                    )
                }
            }
            onNode(
                    hasContentDescription("Write a message") and
                        hasAnyAncestor(hasTestTag("widget-message"))
                )
                .assertIsNotEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        "Check this conversation first",
                    )
                )
            onNodeWithTag("widget-send").assertIsNotEnabled().performClick()
            assertEquals("Keep my words", field.text.toString())
            assertEquals(0, sent)
            onNodeWithTag("kit-notice").assertDoesNotExist()
        }

    @Test
    fun `B-KIT-051 disabled field replaces its helper and restores it after the hold ends`() =
        runComposeUiTest {
            var held by mutableStateOf(true)
            setContent {
                KitTheme(
                    platform = FakeKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    KitField(
                        field,
                        "Message",
                        helper = "Write what should change",
                        enabled =
                            if (held) KitEnabled.Disabled("Message on its way")
                            else KitEnabled.Enabled,
                    )
                }
            }
            onNodeWithText("Write what should change").assertDoesNotExist()
            onNodeWithContentDescription("Message")
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        "Message on its way",
                    )
                )
            runOnIdle { held = false }
            onNodeWithText("Write what should change").assertExists()
            onNodeWithContentDescription("Message")
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
}
