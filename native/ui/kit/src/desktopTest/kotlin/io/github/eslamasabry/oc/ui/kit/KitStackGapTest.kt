package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.row.KIT_CONTAINER_GAP_DP
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitStack
import kotlin.test.Test
import kotlin.test.assertEquals

/** The gate for the owner's rule: sibling containers are always the kit's gap apart, whoever lays them out. */
@OptIn(ExperimentalTestApi::class)
class KitStackGapTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.gaps(): List<Int> {
        waitForIdle()
        val n = onAllNodesWithTag("kit-row-group").fetchSemanticsNodes()
        return n.zipWithNext { a, b -> (b.boundsInRoot.top - a.boundsInRoot.bottom).toInt() }
    }

    @Test
    fun `the gap is the space4 token`() = assertEquals(Space.S4, KIT_CONTAINER_GAP_DP)

    @Test
    fun `two row groups in a stack are one token gap apart`() = runComposeUiTest {
        setContent {
            Themed {
                KitStack {
                    KitRowGroup { KitRow("Model", onClick = {}) }
                    KitRowGroup { KitRow("File from the project", onClick = {}) }
                    KitRowGroup { KitRow("Prompt editor", onClick = {}) }
                }
            }
        }
        assertEquals(listOf(KIT_CONTAINER_GAP_DP, KIT_CONTAINER_GAP_DP), gaps())
    }

    @Test
    fun `a gap the screen already left is not doubled`() = runComposeUiTest {
        setContent {
            Themed {
                KitStack {
                    KitRowGroup { KitRow("A", onClick = {}) }
                    Spacer(Modifier.height(24.dp))
                    KitRowGroup { KitRow("B", onClick = {}) }
                }
            }
        }
        assertEquals(listOf(24), gaps())
    }
}
