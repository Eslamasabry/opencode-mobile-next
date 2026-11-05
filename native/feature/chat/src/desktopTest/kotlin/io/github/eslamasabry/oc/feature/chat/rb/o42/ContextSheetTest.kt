package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.turn.KitContextSheet
import io.github.eslamasabry.oc.ui.kitchat.turn.KitContextLevel
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ContextSheetTest {
    @Test fun summaryShowsActualWindowModelAndMeasurement() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform()) { KitContextSheet(42000, 100000, "Sonnet 4", 42, KitContextLevel.Calm) } }
        onNodeWithText("Sonnet 4").assertIsDisplayed()
        onNodeWithText("42,000 of 100,000 tokens").assertIsDisplayed()
        onNodeWithText("After the last reply").assertIsDisplayed()
    }
}
