package io.github.eslamasabry.oc.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.NoticeUi
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.updatesUi
import io.github.eslamasabry.oc.feature.updates.ui.UpdateNotice
import io.github.eslamasabry.oc.feature.updates.ui.UpdateOfferSheet
import io.github.eslamasabry.oc.feature.updates.ui.UpdatesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The Updates page, the notice and the offer sheet on the desktop JVM, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class UpdatesScreenUiTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val now = Instant.fromEpochMilliseconds(1_767_225_600_000)
    private val available =
        AppUpdateState(phase = UpdatePhase.Available, release = RELEASE, downloadSize = APK_SIZE, installed = INSTALLED)

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Recording : UpdatesActions, UpdateNoticeActions {
        val calls = mutableListOf<String>()

        override fun onBack() { calls += "back" }

        override fun onUpdate() { calls += "update" }

        override fun onCancel() { calls += "cancel" }

        override fun onInstall() { calls += "install" }

        override fun onTryAgain() { calls += "tryAgain" }

        override fun onOpenRelease() { calls += "openRelease" }

        override fun onCheckNow() { calls += "checkNow" }

        override fun onChecks(on: Boolean) { calls += "checks:$on" }

        override fun onNotNow() { calls += "notNow" }
    }

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

    private fun ComposeUiTest.page(state: AppUpdateState, actions: Recording = Recording()): Recording {
        setContent { Themed { UpdatesScreen(updatesUi(state, now), actions) } }
        waitForIdle()
        return actions
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `B-MIGR-012 up to date shows the version and when it looked, and no Update`() = runComposeUiTest {
        page(AppUpdateState(installed = INSTALLED, checkedAt = now))
        onNodeWithText("Version 2.1.0").assertExists()
        onNodeWithText("Up to date · checked just now").assertExists()
        assertTrue(!shows("Update"))
        assertTrue(!shows("Build · 2.1.0+57"), "the build waits under Details")
    }

    @Test
    fun `B-MIGR-012 up to date minutes ago reads the time plainly`() = runComposeUiTest {
        page(AppUpdateState(installed = INSTALLED, checkedAt = now - 5.minutes))
        onNodeWithText("Up to date · checked 5 min ago").assertExists()
    }

    @Test
    fun `B-MIGR-012 available shows the version, its size and Update`() = runComposeUiTest {
        val actions = page(available)
        onNodeWithText("2.2.0 is ready · 39 MB").assertExists()
        onNodeWithText("Check GitHub for new versions").assertExists()
        onNodeWithText("The app asks GitHub for the latest version number and sends nothing about you.").assertExists()
        onNodeWithText("What's new").performScrollTo().assertExists()
        onNodeWithTag("updates-notes").performScrollTo().assertExists()
        onNodeWithText("Update").performClick()
        assertEquals(listOf("update"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 downloading shows Cancel and the percent`() = runComposeUiTest {
        val actions = page(available.copy(phase = UpdatePhase.Downloading, progress = 0.425f))
        // The disabled Check now row repeats the line as its reason, so read it from the status itself.
        onNodeWithTag("updates-state").assertTextContains("Downloading version 2.2.0")
        // The kit draws the percent under the bar and reads it once, as the bar's label.
        onNodeWithTag("kit-state-progress").assertContentDescriptionContains("42% done")
        onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 a checksum failure says so in plain words and offers Try again`() = runComposeUiTest {
        val detail = "sha256 ${"b".repeat(64)}, expected $GOOD_HASH"
        val actions =
            page(available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Checksum, detail = detail))
        onNodeWithText("2.2.0 couldn't be installed").assertExists()
        onNodeWithText("The download didn't match its checksum, so it wasn't installed.").assertExists()
        assertTrue(!shows("is ready"))
        assertTrue(!shows(detail), "technical text stays folded")
        onNodeWithText("Details").performScrollTo().performClick()
        waitForIdle()
        assertTrue(shows(detail))
        assertTrue(shows("Build · 2.1.0+57"))
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("tryAgain"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 a signer failure offers Open on GitHub, not Try again`() = runComposeUiTest {
        val actions =
            page(available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Signer, detail = "signer BB22, expected AA11"))
        onNodeWithText("This download isn't signed like your app, so it wasn't installed.").assertExists()
        assertTrue(!shows("Try again"))
        assertTrue(onAllNodesWithText("BB22", substring = true).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Open on GitHub").performClick()
        assertEquals(listOf("openRelease"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 the switch turns checking off`() = runComposeUiTest {
        val actions = page(available)
        onNodeWithText("Check GitHub for new versions").performClick()
        assertEquals(listOf("checks:false"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 the permission note shows while Android must allow the install`() = runComposeUiTest {
        val actions = page(available.copy(phase = UpdatePhase.ReadyToInstall, needsPermission = true))
        onNodeWithText("Version 2.2.0 is downloaded and checked").assertExists()
        onNodeWithText("To install it, allow this app to install updates, then come back.").assertExists()
        onNodeWithText("Install").performClick()
        assertEquals(listOf("install"), actions.calls)
    }

    @Test
    fun `B-MIGR-012 the gear's offer sheet asks Update now or Open Settings`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            Themed {
                KitSheetHost {
                    UpdateOfferSheet(
                        version = "2.2.0",
                        onUpdateNow = { calls += "now" },
                        onOpenSettings = { calls += "settings" },
                        onDismiss = { calls += "dismiss" },
                        summary = "This release is faster.",
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Version 2.2.0 is ready").assertExists()
        onNodeWithText("This release is faster.").assertExists()
        onNodeWithText("Update now").assertExists()
        onNodeWithText("Open Settings").performClick()
        assertEquals(listOf("dismiss", "settings"), calls)
    }

    @Test
    fun `B-MIGR-012 the notice offers Update and Not now`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed { UpdateNotice(NoticeUi.Ready("2.2.0"), actions) } }
        waitForIdle()
        onNodeWithText("Version 2.2.0 is ready").assertExists()
        onNodeWithText("Not now").performClick()
        onNodeWithText("Update").performClick()
        assertEquals(listOf("notNow", "update"), actions.calls)
    }
}
