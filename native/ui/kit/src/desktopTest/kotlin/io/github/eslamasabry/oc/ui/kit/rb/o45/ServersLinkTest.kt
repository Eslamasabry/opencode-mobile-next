@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.setup.KitServersLink
import io.github.eslamasabry.oc.ui.kit.setup.KitServersLinkState
import kotlin.test.Test

class ServersLinkTest {
    @Test fun `all link states draw a decorative still frame with motion off`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
            Column { KitServersLinkState.entries.forEach { state -> KitServersLink(state, Modifier.testTag(state.name)) } }
        } }
        KitServersLinkState.entries.forEach { state -> onNodeWithTag(state.name).assertWidthIsEqualTo(160.dp) }
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
