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
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2 (Notifications): an opened expander's card stood 85 px in while the page's cards stood 42. */
@OptIn(ExperimentalTestApi::class)
class KitExpandRowGutterTest {
    @Test
    fun `W2-022 a group of rows inside an opened expander stands where the page's groups stand`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off, style = Style.Soft),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Column(Modifier.width(412.dp)) {
                    KitRowGroup { KitRow("Outside the expander") }
                    KitExpandRow("This phone", expanded = true, onExpandedChange = {}) {
                        KitRowGroup { KitRow("Inside the expander") }
                    }
                }
            }
        }
        val outside = onNodeWithText("Outside the expander").getUnclippedBoundsInRoot()
        val inside = onNodeWithText("Inside the expander").getUnclippedBoundsInRoot()
        assertTrue(
            abs((outside.left - inside.left).value) < 0.5f,
            "outside starts at ${outside.left}, inside at ${inside.left}",
        )
    }
}
