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
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2 (Settings > Conversations): "Permissions and automati…" was cut while a second line was free. */
@OptIn(ExperimentalTestApi::class)
class KitRowTitleWrapTest {
    @Test
    fun `W2-025 a row title too long for its line wraps to a second line`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Column(Modifier.width(260.dp)) {
                    KitRow("Permissions", Modifier)
                    KitRow("Permissions and automatic actions")
                }
            }
        }
        val short = onNodeWithText("Permissions").getUnclippedBoundsInRoot()
        val long = onNodeWithText("Permissions and automatic actions").getUnclippedBoundsInRoot()
        val shortH = (short.bottom - short.top).value
        val longH = (long.bottom - long.top).value
        assertTrue(longH > shortH * 1.5f, "short $shortH, long $longH")
    }
}
