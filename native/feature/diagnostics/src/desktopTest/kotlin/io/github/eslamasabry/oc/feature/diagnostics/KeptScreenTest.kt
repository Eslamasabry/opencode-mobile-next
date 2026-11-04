package io.github.eslamasabry.oc.feature.diagnostics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptActions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptCrashRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExitRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExits
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptNotice
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptPreview
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptUi
import io.github.eslamasabry.oc.feature.diagnostics.ui.KeptScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Everything this phone kept on the desktop JVM, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class KeptScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) { content() }
        }
    }

    private class Taps : KeptActions {
        val taps = mutableListOf<String>()

        override fun onBack() { taps += "back" }

        override fun onCrashCapture(on: Boolean) { taps += "capture:$on" }

        override fun onPreviewCrashReport() { taps += "preview" }

        override fun onShareCrashReport() { taps += "share" }

        override fun onClosePreview() { taps += "close" }

        override fun onDeleteCrashReports() { taps += "delete" }

        override fun onConfirmDelete() { taps += "confirmDelete" }

        override fun onClearErrors() { taps += "clear" }

        override fun onConfirmClear() { taps += "confirmClear" }

        override fun onCancelConfirm() { taps += "cancel" }

        override fun onRetryExits() { taps += "retry" }

        override fun onDismissNotice() { taps += "dismiss" }
    }

    @Test
    fun `B-SEC-042 crash reports are off by default with the switch and its privacy line`() = runComposeUiTest {
        val taps = Taps()
        setContent { Themed { KitSheetHost { KeptScreen(KeptUi(crashAvailable = true, exits = KeptExits.Ready(emptyList(), 0)), taps) } } }
        onNodeWithText("Everything this phone kept").assertExists()
        onNodeWithText("Save crash reports on this phone").performClick()
        assertEquals(listOf("capture:true"), taps.taps)
        onNodeWithText("Kept on this phone. Never sent automatically.").assertExists()
    }

    @Test
    fun `B-SEC-047 the preview shows only the report text and Share report is a separate tap`() = runComposeUiTest {
        val taps = Taps()
        val ui =
            KeptUi(
                crashAvailable = true,
                crashOn = true,
                crashRows = listOf(KeptCrashRow("Today 12:00", "Invalid state")),
                preview = KeptPreview("OpenCode Mobile crash report", 1, 28),
                exits = KeptExits.Ready(listOf(KeptExitRow(ExitKind.Crash, "Today 11:00")), 1),
            )
        setContent { Themed { KitSheetHost { KeptScreen(ui, taps) } } }
        onNodeWithText("Category · Invalid state").assertExists()
        onNodeWithText("OpenCode Mobile crash report").performScrollTo().assertExists()
        assertEquals(emptyList(), taps.taps)
        onNodeWithText("Share report").performScrollTo().performClick()
        assertEquals(listOf("share"), taps.taps)
        onNodeWithText("App stopped unexpectedly").performScrollTo().assertExists()
    }

    @Test
    fun `B-SEC-053 a failure reads as one plain line with no details`() = runComposeUiTest {
        setContent { Themed { KitSheetHost { KeptScreen(KeptUi(notice = KeptNotice.ShareFailed), Taps()) } } }
        onNodeWithText("Couldn't open sharing. Try again.").assertExists()
    }

    @Test
    fun `B-SEC-059 unsupported exit history says so plainly`() = runComposeUiTest {
        setContent { Themed { KitSheetHost { KeptScreen(KeptUi(exits = KeptExits.Unsupported), Taps()) } } }
        onNodeWithText("Not available on this phone").performScrollTo().assertExists()
    }
}
