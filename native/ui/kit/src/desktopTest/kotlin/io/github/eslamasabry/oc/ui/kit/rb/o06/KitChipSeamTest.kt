package io.github.eslamasabry.oc.ui.kit.rb.o06

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.*
import io.github.eslamasabry.oc.ui.kit.icon.*
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** Order 24's task-chip seam: decorative glyph, replacement spoken words and opt-in polite announcements. */
@OptIn(ExperimentalTestApi::class)
class KitChipSeamTest {
    @Test fun custom_glyph_and_spoken_live_region() = runDesktopComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform()) {
                KitChip("Fast", kind = KitChipKind.Action, onClick = {}, spoken = "Fast mode, on", liveRegion = true,
                    glyph = { KitIcon(KitIcons.sync, Modifier.testTag("custom-progress"), label = "Decorative progress") })
            }
        }
        onNodeWithTag("custom-progress", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Fast mode, on").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertHasClickAction()
        onNodeWithContentDescription("Decorative progress").assertDoesNotExist()
        onNodeWithText("Fast").assertDoesNotExist()
    }
    @Test fun existing_call_keeps_live_region_off() = runDesktopComposeUiTest {
        setContent { KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform()) { KitChip("Open", kind = KitChipKind.Action, onClick = {}) } }
        onNodeWithContentDescription("Open").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }
}
