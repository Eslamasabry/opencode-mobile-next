@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.onboarding.rb.o40

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.onboarding.presentation.*
import io.github.eslamasabry.oc.feature.onboarding.ui.ManagedMoveScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagedMoveTest {
    @Test fun `separate history offers a safe exit and never a retry`() = runComposeUiTest {
        var switched = 0
        val actions = object : ManagedMoveActions {
            override fun retry() { error("A separate history cannot retry") }
            override fun changeServer() { switched++ }
        }
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            ManagedMoveScreen(ManagedMoveUi(failure = ManagedMoveResult.Failed(ManagedMoveReason.SeparateHistory)), actions)
        } }
        onNodeWithText("Try again").assertDoesNotExist()
        onNodeWithText("Servers").performClick()
        assertEquals(1, switched)
    }
}
