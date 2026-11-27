package io.github.eslamasabry.oc.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.NoteLinkPrompt
import io.github.eslamasabry.oc.feature.updates.presentation.NoticeUi
import io.github.eslamasabry.oc.feature.updates.presentation.OfferUi
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatePromptActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdateNoticeActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesUi
import io.github.eslamasabry.oc.feature.updates.presentation.updatesUi
import io.github.eslamasabry.oc.feature.updates.ui.UPDATED_HOLD_MS
import io.github.eslamasabry.oc.feature.updates.ui.UpdateNotice
import io.github.eslamasabry.oc.feature.updates.ui.UpdateOfferSheet
import io.github.eslamasabry.oc.feature.updates.ui.UpdatePromptSheet
import io.github.eslamasabry.oc.feature.updates.ui.UpdatesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
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

        override fun onOpenNoteLink(url: String) { calls += "noteLink:$url" }

        override fun onConfirmNoteLink() { calls += "confirmNoteLink" }

        override fun onDismissNoteLink() { calls += "dismissNoteLink" }

        override fun onDismissLinkNotice() { calls += "dismissLinkNotice" }

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

    private fun ComposeUiTest.page(state: AppUpdateState, actions: Recording = Recording()): Recording =
        pageUi(updatesUi(state, now), actions)

    private fun ComposeUiTest.pageUi(ui: UpdatesUi, actions: Recording = Recording()): Recording {
        setContent { Themed { UpdatesScreen(ui, actions) } }
        waitForIdle()
        return actions
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun releaseNotes(): String {
        val path = "docs/releases/v3.0.3+10160.md"
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, path).isFile) d = d.parentFile
        val root = checkNotNull(d) { "$path not found from ${System.getProperty("user.dir")}" }
        return File(root, path).readText()
    }

    private fun hasRawMarkdown(text: String): Boolean =
        "**" in text ||
            text.lines().any { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("#") || trimmed.startsWith("- ")
            }

    private fun ComposeUiTest.textLayouts(): List<TextLayoutResult> =
        onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                    val out = mutableListOf<TextLayoutResult>()
                    get(out)
                    out.firstOrNull()
                }
            }

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
        // The size reads the decimal megabyte Android shows (3d4953e58c): the fixture bytes over 1,000,000, rounded up.
        val megabytes = kotlin.math.ceil(APK_SIZE / 1_000_000.0).toLong()
        onNodeWithText("2.2.0 is ready · $megabytes MB").assertExists()
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
        onNodeWithText("The download didn't match its checksum, so try downloading it again.").assertExists()
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
    fun `B-MIGR-012 a signer failure offers View release on GitHub, not Try again`() = runComposeUiTest {
        val actions =
            page(available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Signer, detail = "signer BB22, expected AA11"))
        onNodeWithText(
                "This download isn't signed like your app, so view the release on GitHub or install a matching build.",
            )
            .assertExists()
        assertTrue(!shows("Try again"))
        assertTrue(onAllNodesWithText("BB22", substring = true).fetchSemanticsNodes().isEmpty())
        onNodeWithText("View release on GitHub").performClick()
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
        // The status line's dismiss is the kit's close mark, read aloud as "Not now" (a content description, not text).
        onNodeWithContentDescription("Not now").performClick()
        onNodeWithText("Update").performClick()
        assertEquals(listOf("notNow", "update"), actions.calls)
    }

    @Test
    fun `B-217 after the update the line says the version once and leaves by itself`() = runComposeUiTest {
        val actions = Recording()
        mainClock.autoAdvance = false
        setContent { Themed { UpdateNotice(NoticeUi.Updated("2.2.0"), actions) } }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Updated to version 2.2.0").assertExists()
        assertTrue(actions.calls.isEmpty(), "it holds while the person reads it")
        mainClock.advanceTimeBy(UPDATED_HOLD_MS)
        assertEquals(listOf("notNow"), actions.calls, "it does not stay on every screen")
    }

    @Test
    fun `B-217 the updated line can be dismissed at once`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed { UpdateNotice(NoticeUi.Updated("2.2.0"), actions) } }
        waitForIdle()
        onNodeWithContentDescription("Dismiss").performClick()
        assertEquals("notNow", actions.calls.first())
    }

    @Test
    fun `B-BUILD-045 the start-up prompt asks Update now or Not now`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions =
            object : UpdatePromptActions {
                override fun onPromptUpdate() { calls += "update" }

                override fun onPromptLater() { calls += "later" }

                override fun onPromptClose() { calls += "close" }
            }
        val prompt = OfferUi("2.2.0", "This release is faster.")
        setContent { Themed { KitSheetHost { UpdatePromptSheet(prompt, actions) } } }
        waitForIdle()
        onNodeWithText("Version 2.2.0 is ready").assertExists()
        onNodeWithText("This release is faster.").assertExists()
        onNodeWithText("Not now").performClick()
        onNodeWithText("Update now").performClick()
        assertEquals(listOf("later", "update"), calls)
    }

    @Test
    fun `B-MIGR-012 What's new shows the real release notes as headings, bold and lists, never raw Markdown`() =
        runComposeUiTest {
            val notes = releaseNotes()
            page(available.copy(release = RELEASE.copy(notes = notes)))
            val texts =
                onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                    .map { it.text }
            val bad = texts.filter(::hasRawMarkdown)
            assertTrue(
                bad.isEmpty(),
                "raw Markdown reached the screen: ${bad.map { it.take(60) }}",
            )
            val headings =
                listOf(
                    "OpenCode Mobile 3.0.3+10160",
                    "In short",
                    "Agents on this phone",
                    "Known in this version",
                )
            for (h in headings) {
                onNode(hasText(h) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                    .assertExists()
            }
            onNodeWithText(
                    "Get latest works on a branch the remote already has.",
                    useUnmergedTree = true,
                )
                .assertExists()
            val bullets = onAllNodesWithText("•", useUnmergedTree = true).fetchSemanticsNodes().size
            assertEquals(notes.lines().count { it.startsWith("- ") }, bullets)
            val boldLead = "The app no longer closes when left open for hours."
            val hasBold =
                textLayouts().any { layout ->
                    val content = layout.layoutInput.text
                    content.spanStyles.any { span ->
                        span.item.fontWeight == FontWeight.Bold &&
                            content.text.substring(span.start, span.end) == boldLead
                    }
                }
            assertTrue(hasBold, "expected bold span for '$boldLead'")
        }

    @Test
    fun `B-218 test build explains up front before any download and offers no Update`() = runComposeUiTest {
        val testInstalled = InstalledApp(PACKAGE, "3.0.2-test.10142", 10142)
        page(available.copy(installed = testInstalled))
        onNodeWithText(
                "This is a test build. Public updates can't install over it.",
                substring = true,
            )
            .assertExists()
        onNodeWithText("data", substring = true).assertExists()
        assertTrue(!shows("Update"), "test build must not offer Update")
    }

    @Test
    fun `B-218 signer failure shows both signatures only under Details with plain labels`() = runComposeUiTest {
        val own = "1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
        val update = "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
        page(
            available.copy(
                phase = UpdatePhase.Failed,
                failure = UpdateFailure.Signer,
                detail = "signer $update, expected $own",
            ),
        )
        assertTrue(onAllNodesWithText(own, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText(update, substring = true).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Details").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("This app's signature", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("The update's signature", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText(own, substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText(update, substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-627 signer failure names what the release action does and failures say why and what next`() =
        runComposeUiTest {
            val actions =
                page(
                    available.copy(
                        phase = UpdatePhase.Failed,
                        failure = UpdateFailure.Signer,
                        detail = "signer BB22, expected AA11",
                    ),
                )
            assertTrue(!shows("Open on GitHub"), "Open on GitHub does not say what you get there")
            onNodeWithText("View release on GitHub").performClick()
            assertEquals(listOf("openRelease"), actions.calls)
            val failureText =
                onNodeWithTag("updates-failure")
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString(" ") { it.text }
            assertTrue(". " !in failureText, "expected one plain sentence: $failureText")
            assertTrue("install" in failureText.lowercase(), "expected next step in: $failureText")
        }

    @Test
    fun `B-627 every update failure says why and what next in one plain sentence`() = runComposeUiTest {
        val current = mutableStateOf(UpdateFailure.Network)
        setContent {
            Themed {
                UpdatesScreen(
                    updatesUi(
                        available.copy(phase = UpdatePhase.Failed, failure = current.value),
                        now,
                    ),
                    Recording(),
                )
            }
        }
        for (why in UpdateFailure.entries) {
            current.value = why
            waitForIdle()
            val text =
                onNodeWithTag("updates-failure")
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString(" ") { it.text }
            assertTrue(text.isNotBlank(), "$why had blank failure text")
            assertTrue(". " !in text, "$why must be one plain sentence: $text")
            assertTrue("so " in text.lowercase(), "$why must include what next: $text")
        }
    }

    @Test
    fun `B-628 What's new links are visibly styled and tapping one routes to onOpenNoteLink`() =
        runComposeUiTest {
            val url = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v3.0.3"
            val notes = "Read the [release notes]($url) for details."
            val actions = page(available.copy(release = RELEASE.copy(notes = notes)))
            onNodeWithText("Read the release notes for details.", useUnmergedTree = true)
                .performScrollTo()
                .assertExists()
            val linkAnnotation =
                textLayouts()
                    .flatMap { layout ->
                        val text = layout.layoutInput.text
                        text.getLinkAnnotations(0, text.length).map { it.item }
                    }
                    .filterIsInstance<LinkAnnotation.Clickable>()
                    .single()
            assertEquals(
                TextDecoration.Underline,
                linkAnnotation.styles?.style?.textDecoration,
                "links in What's new must stay visually clear",
            )
            linkAnnotation.linkInteractionListener?.onClick(linkAnnotation)
            assertEquals(listOf("noteLink:$url"), actions.calls)
        }

    @Test
    fun `B-628 link confirmation names the host and missing browser shows one plain dismissible message`() =
        runComposeUiTest {
            val url = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v3.0.3"
            val actions =
                pageUi(
                    updatesUi(available, now)
                        .copy(linkPrompt = NoteLinkPrompt(url, "github.com"), noBrowser = true),
                )
            onNodeWithText("Open link?", useUnmergedTree = true).assertExists()
            onNodeWithText("This opens github.com outside the app.", useUnmergedTree = true)
                .assertExists()
            onNode(
                    hasText("Open") and
                        (hasAnyAncestor(hasTestTag("kit-dialog")) or
                            hasAnyAncestor(hasTestTag("kit-sheet"))),
                )
                .performClick()
            onNodeWithText("No browser on this device can open that link.", useUnmergedTree = true)
                .assertExists()
            onNodeWithContentDescription("Dismiss").performClick()
            assertEquals(listOf("confirmNoteLink", "dismissLinkNotice"), actions.calls)
        }
}
