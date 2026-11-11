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
import kotlin.test.assertTrue

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
        // 0fe8167abe: stacked text actions sit in one wrapping row, pulled back 8 dp (Dart `KitInset`) and hugging their
        // width, so the first one's WORDS (not its box) start on Try again's edge and the next one follows on the line.
        val change = onNodeWithText("Change server", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(tryAgain.left, change.left, 1f, "Change server's words start where Try again starts: $change vs $tryAgain")
        val copy = onNodeWithText("Copy details", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val wrapped = copy.top >= change.bottom
        if (wrapped) assertEquals(tryAgain.left, copy.left, 1f, "Copy details wrapped onto its own line: $copy vs $tryAgain")
        else assertTrue(copy.left >= change.right, "Copy details follows Change server on the same line: $copy vs $change")
        assertTrue(copy.right <= tryAgain.right, "Copy details stays inside Try again's width: $copy vs $tryAgain")
    }
}
