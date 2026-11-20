@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRow
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import kotlin.test.Test
import kotlin.test.assertEquals

/** F26: the kit parts that draw a changing number keep equal-width digits inside the part, with no parameter. */
class TabularPartsTest {
    private fun ComposeUiTest.assertTabular(text: String) {
        val layout = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals("tnum", layout.first().layoutInput.style.fontFeatureSettings)
    }

    private fun check(text: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform()) { KitSheetHost { content() } }
        }
        assertTabular(text)
    }

    @Test
    fun `B-KIT-295 KitStatus message and supporting draw equal-width digits`() {
        val status = KitStatus(KitStatusKind.Info, "Copying 3 of 5", supporting = "12 MB left")
        check("Copying 3 of 5") { KitStatusLine(status) }
        check("12 MB left") { KitStatusLine(status) }
    }

    @Test
    fun `B-KIT-296 KitRow supporting slot draws equal-width digits`() =
        check("3 of 5 done") { KitRow("Tasks", supporting = "3 of 5 done") }

    @Test
    fun `B-KIT-297 KitProgressRow value slot draws equal-width digits`() =
        check("42 of 100") { KitProgressRow("Quota", 0.42f, valueLabel = "42 of 100") }

    @Test
    fun `B-KIT-298 KitRowTrailing Value slot draws equal-width digits`() =
        check("12:30") { KitRow("Time", trailing = KitRowTrailing.Value("12:30"), onClick = {}) }

    @Test
    fun `B-KIT-299 KitSheet subtitle slot draws equal-width digits`() =
        check("3 of 5 steps") { KitSheet(true, {}, "Tasks", subtitle = "3 of 5 steps") {} }

    @Test
    fun `B-KIT-300 KitLineAction label draws equal-width digits`() =
        check("Retry in 30 s") { KitInlineAction(KitLineAction("Retry in 30 s", {})) }

    @Test
    fun `B-KIT-301 KitChip label draws equal-width digits`() =
        check("3 files") { KitChip("3 files") }
}
