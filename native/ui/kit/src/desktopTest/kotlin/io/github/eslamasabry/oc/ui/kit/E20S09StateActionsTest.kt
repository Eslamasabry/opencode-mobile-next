package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import kotlin.test.Test
import kotlin.test.assertEquals

/** E20-S09: the home's "isn't answering" page; every action of a failure shares one edge and one width. */
@OptIn(ExperimentalTestApi::class)
class E20S09StateActionsTest {
    @Test
    fun `E20-S09 Try again, Change server and Copy details share one edge and one width`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(412, 915) {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = "This phone isn’t answering",
                            body = "Check that the server is on and this phone can reach it, then try again.",
                            failure = Failure.Network(details = Redacted.shape(IllegalStateException(), 503)),
                            primary = KitAction("Try again", {}),
                            tertiary = listOf(KitAction("Change server", {})),
                        )
                    }
                }
            }
        }
        val tryAgain = onNodeWithText("Try again").fetchSemanticsNode().boundsInRoot
        for (label in listOf("Change server", "Copy details")) {
            val action = onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            // Before the fix a text action reached past the gutter on the start side (8 dp further out).
            assertEquals(tryAgain.left, action.left, "$label starts where Try again starts: $action vs $tryAgain")
            assertEquals(tryAgain.right, action.right, "$label ends where Try again ends: $action vs $tryAgain")
        }
    }
}
