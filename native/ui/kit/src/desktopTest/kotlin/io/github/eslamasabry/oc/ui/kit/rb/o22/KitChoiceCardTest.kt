package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.ChoiceCardRules
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCardItem
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_choice_cards.dart: the line, the tap rule while busy, the disabled reason and the read-aloud name. */
@OptIn(ExperimentalTestApi::class)
class KitChoiceCardTest {
    @Composable
    private fun themed(content: @Composable () -> Unit) =
        KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), stillFrame = true) { content() }

    private val phone = KitChoiceCardItem("phone", "On this phone", supporting = "About 4 minutes")
    private val computer = KitChoiceCardItem("computer", "On my computer", supporting = "Pair with a code")

    @Test
    fun `B-KIT-014 choice card line puts a disabled reason first and joins with a middle dot`() {
        assertEquals("About 4 minutes", ChoiceCardRules.line("About 4 minutes", KitEnabled.Enabled))
        assertEquals("Not on this device · About 4 minutes", ChoiceCardRules.line("About 4 minutes", KitEnabled.Disabled("Not on this device")))
        assertEquals("Not on this device", ChoiceCardRules.line("", KitEnabled.Disabled("Not on this device")))
        assertEquals("", ChoiceCardRules.line(null, KitEnabled.Enabled))
        assertEquals("On this phone. About 4 minutes", ChoiceCardRules.label("On this phone", "About 4 minutes"))
        assertEquals("On this phone", ChoiceCardRules.label("On this phone", ""))
    }

    @Test
    fun `B-KIT-014 choice card takes a tap only when enabled and nothing is busy`() {
        assertTrue(ChoiceCardRules.tappable(KitEnabled.Enabled, waiting = false))
        assertFalse(ChoiceCardRules.tappable(KitEnabled.Enabled, waiting = true))
        assertFalse(ChoiceCardRules.tappable(KitEnabled.Disabled("No"), waiting = false))
        assertTrue(ChoiceCardRules.showsBusy("phone", "phone"))
        assertFalse(ChoiceCardRules.showsBusy("computer", "phone"))
        assertFalse(ChoiceCardRules.showsBusy("phone", null))
    }

    @Test
    fun `B-KIT-014 a tap on a choice card chooses it once`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { themed { KitChoiceCards(listOf(phone, computer), "phone", { calls += it }) } }
        onNodeWithContentDescription("On my computer. Pair with a code").performClick()
        assertEquals(listOf("computer"), calls)
        onNodeWithContentDescription("On this phone. About 4 minutes").assertIsSelected()
    }

    @Test
    fun `B-KIT-014 while one choice is busy every card waits and the busy card shows progress`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { themed { KitChoiceCards(listOf(phone, computer), "phone", { calls += it }, busy = "phone") } }
        onNodeWithContentDescription("On my computer. Pair with a code").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), calls)
        onNodeWithTag("kit-choice-card-busy", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-KIT-014 a disabled choice card states its reason and takes no tap`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val off = phone.copy(enabled = KitEnabled.Disabled("Not on this device"))
        setContent { themed { KitChoiceCards(listOf(off, computer), "computer", { calls += it }) } }
        onNodeWithContentDescription("On this phone. Not on this device · About 4 minutes")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not on this device"))
            .performClick()
        assertEquals(emptyList(), calls)
    }
}
