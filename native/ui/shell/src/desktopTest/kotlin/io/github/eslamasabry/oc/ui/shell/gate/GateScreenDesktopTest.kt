package io.github.eslamasabry.oc.ui.shell.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** S2-GATEUI: the gate screens headless on desktop, behaviour and words only (pixels are the Android goldens). */
@OptIn(ExperimentalTestApi::class)
class GateScreenDesktopTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val taps = mutableListOf<String>()

    private fun ui(localeTag: String = "en", content: @Composable () -> Unit) = @Composable {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = DesktopKitPlatform(),
            localeTag = localeTag,
            stillFrame = true,
            content = content,
        )
    }

    @Composable
    private fun Gate(state: GateUi) =
        GateScreen(
            ui = state,
            onRow = { taps += "row:$it" },
            onConfirm = { taps += "confirm" },
            onCancelConfirm = { taps += "cancel" },
            onReentry = { taps += "reentry:${it.targetName}" },
            onReentryLater = { taps += "later" },
            content = { KitText("App content") },
        )

    @Test
    fun `B-MIGR-010 the failure screen lists every way out as a row that acts`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val state = GateUi.Failed(
            GateFailureScreen.Failed,
            GatePresenter.rowsFor(GateBlockReason.StepCrashed, restoreAvailable = true),
            detail = "M2 · UnwrapFailed ×1",
        )
        setContent(ui { Gate(state) })
        onNodeWithText("The update couldn't finish moving your data").assertExists()
        onNodeWithText("Nothing was deleted. Your data from before the update is safe on this phone.").assertExists()
        onNodeWithText("Try moving your data again").performClick()
        onNodeWithText("Restore data from before the update").performClick()
        onNodeWithText("Start fresh (keeps a copy of your old data)").performClick()
        onNodeWithText("Report a problem").performClick()
        onNodeWithText("Details · M2 · UnwrapFailed ×1").assertExists()
        assertEquals(listOf("row:TryAgain", "row:Restore", "row:StartFresh", "row:Report"), taps)
    }

    @Test
    fun `B-KIT-015 Start fresh confirms with its consequence before acting`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val rows = GatePresenter.rowsFor(GateBlockReason.PrefsUnreadable, restoreAvailable = false)
        setContent(ui { Gate(GateUi.Failed(GateFailureScreen.Failed, rows, confirming = GateRow.StartFresh)) })
        waitForIdle()
        onNodeWithText(
            "You'll start with no saved servers and default settings. A copy of your old data stays on this phone.",
        ).assertExists()
        onNodeWithText("Start fresh and keep a copy").performClick()
        assertEquals(listOf("confirm"), taps)
    }

    @Test
    fun `B-MIGR-010 low space names the space it needs and offers storage settings`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val rows = GatePresenter.rowsFor(GateBlockReason.LowSpace, restoreAvailable = false)
        setContent(ui { Gate(GateUi.Failed(GateFailureScreen.LowSpace, rows, neededBytes = 120L * 1024 * 1024)) })
        onNodeWithText("Your phone is almost full").assertExists()
        onNodeWithText(
            "The update needs 120 MB of free space to keep a safety copy of your data. Free up space, then try again.",
        ).assertExists()
        onNodeWithText("Open storage settings").performClick()
        assertEquals(listOf("row:OpenStorage"), taps)
    }

    @Test
    fun `B-MIGR-010 Updating shows Still updating and Try again only when offered`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var state: GateUi by mutableStateOf(GateUi.Updating(offerRetry = false))
        setContent(ui { Gate(state) })
        onNodeWithText("Updating your app…").assertExists()
        onAllNodesWithText("Try moving your data again").assertCountEquals(0)
        state = GateUi.Updating(offerRetry = true)
        waitForIdle()
        onNodeWithText("Still updating. The first start after an update can take a little longer.").assertExists()
        onNodeWithText("Try moving your data again").performClick()
        assertEquals(listOf("row:TryAgain"), taps)
    }

    @Test
    fun `B-L10N-010 the wordless Opening mark shows no words, the worded one says Opening`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var state: GateUi by mutableStateOf(GateUi.Opening(wordless = true, offerRetry = false))
        setContent(ui { Gate(state) })
        onAllNodesWithText("Opening…").assertCountEquals(0)
        state = GateUi.Opening(wordless = false, offerRetry = false)
        waitForIdle()
        onNodeWithText("Opening…").assertExists()
    }

    @Test
    fun `B-MIGR-011 re-enter rows name their target and what is needed, never a value`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val items = listOf(
            GateReentry("p1", "Home server", GateReentryKind.Password),
            GateReentry("p2", "Work", GateReentryKind.Token),
            GateReentry(null, "GitHub", GateReentryKind.GitHubSignIn),
            GateReentry("p3", "Laptop", GateReentryKind.ByoSetup),
        )
        setContent(ui { Gate(GateUi.Open(items, deferredNotice = false)) })
        waitForIdle()
        onNodeWithText("A few sign-ins need you again").assertExists()
        onNodeWithText("Home server · Enter password").assertExists()
        onNodeWithText("Work · Enter token").assertExists()
        onNodeWithText("GitHub · Sign in to GitHub again").assertExists()
        onNodeWithText("Laptop · Set up again").performClick()
        onNodeWithText("Sign in to these later").performClick()
        onNodeWithText("App content").assertExists()
        assertEquals(listOf("reentry:Laptop", "later"), taps)
    }

    @Test
    fun `02-3_7-4 the deferred-features notice reads above the app`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        setContent(ui { Gate(GateUi.Open(emptyList(), deferredNotice = true)) })
        onNodeWithTag("gate-notice").assertExists()
        onNodeWithText("AI Team and Memory aren't in this version yet. Your projects and notes stay on this phone.")
            .assertExists()
        onNodeWithText("App content").assertExists()
    }

    @Test
    fun `B-L10N-010 the gate reads Arabic in an Arabic app`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val rows = GatePresenter.rowsFor(GateBlockReason.WriteRefused, restoreAvailable = false)
        setContent(ui("ar") { Gate(GateUi.Failed(GateFailureScreen.SaveFailed, rows)) })
        onNodeWithText("تعذّر الحفظ على هذا الهاتف").assertExists()
        onNodeWithText("فتح إعدادات التخزين").assertExists()
    }
}
