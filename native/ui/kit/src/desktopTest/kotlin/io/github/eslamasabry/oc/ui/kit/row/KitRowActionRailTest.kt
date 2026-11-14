package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2 (Agents on computers): a row's trailing text action ended 12 dp from the edge, a chevron's ink 16. */
@OptIn(ExperimentalTestApi::class)
class KitRowActionRailTest {
    @Test
    fun `W2-024 a trailing text action ends its words on the page gutter`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Column(Modifier.width(412.dp)) {
                    KitRow(
                        title = "Paseo",
                        trailing = KitRowTrailing.Action(KitLineAction("Update and import", {})),
                    )
                }
            }
        }
        val words = onNodeWithText("Update and import").getUnclippedBoundsInRoot()
        assertTrue(abs((412.dp - words.right).value - 16f) < 1f, "the words end ${412.dp - words.right} from the edge")
    }
}
