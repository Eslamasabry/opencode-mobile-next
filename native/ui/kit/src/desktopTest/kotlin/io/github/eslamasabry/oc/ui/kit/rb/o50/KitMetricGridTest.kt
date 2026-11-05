package io.github.eslamasabry.oc.ui.kit.rb.o50

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.row.KitMetricGrid
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitMetricGridTest {
    @Test
    fun `B-KIT-035 large text gives each labelled metric a separate row`() = runComposeUiTest {
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    KitMetricGrid(listOf("Conversations" to "12", "Prompts" to "38"))
                }
            }
        }
        val first =
            onNodeWithText("Conversations", substring = true).fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Prompts", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(second.top >= first.bottom)
    }
}
