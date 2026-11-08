package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent
import io.github.eslamasabry.oc.engine.analytics.health.ReportClose
import io.github.eslamasabry.oc.engine.analytics.health.ReportConnection
import io.github.eslamasabry.oc.engine.analytics.health.ReportProblem
import io.github.eslamasabry.oc.feature.settings.presentation.ReportStage
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * R1 Report a problem, drawn from canned state: the three states of the owner's mockups (the form, "What's included",
 * the sent code), the build that cannot send, and the nine removed controls. With `R1_RENDER_DIR` set it also writes
 * the QA renders (form, sheet and sent × Solid and Glass × light and dark, plus the no-config form) there.
 */
@OptIn(ExperimentalTestApi::class)
class ReportProblemR1Test {
    private val savedLocale: Locale = Locale.getDefault()

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest fun restore() = Locale.setDefault(savedLocale)

    private val made = System.currentTimeMillis()
    private val content =
        ProblemReportContent(
            lastClose = ReportClose("crash", made - 47 * 60_000),
            appVersion = "2.1.0",
            appBuild = "57",
            android = "15",
            abi = "arm64",
            ramRange = "8-12",
            connection = ReportConnection("OpenCode", "2.1.1"),
            problems = listOf(ReportProblem("timeout", 2), ReportProblem("auth", 1)),
            madeMs = made,
        )
    private val words = "I sent a photo in the chat and the app closed."

    private fun state(report: ReportUi, description: String = "") =
        SettingsUi(stack = persistentListOf(SettingsPage.Hub, SettingsPage.ReportProblem), report = report, reportDescription = description)

    private val form = state(ReportUi(content = content, canSend = true))
    private val sheet = state(ReportUi(content = content.withWords(words), canSend = true, showIncluded = true), words)
    private val sent = state(ReportUi(content = content, canSend = true, stage = ReportStage.Sent("OC-7K4Q-2M")))
    private val noConfig = state(ReportUi(content = content, canSend = false))

    @Test
    fun `R1-020 the form asks what happened, says what is included, and sends with one primary`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { SettingsThemed { SettingsScreen(form, actions) } }
        onNodeWithText("What happened?").assertExists()
        onNodeWithText("Optional").assertExists()
        onNodeWithText("What you did, what you expected, what you got instead").assertExists()
        onNodeWithText("App and phone details, and the crash at", substring = true).assertExists()
        onNodeWithText("Send report").assertExists()
        onNodeWithText("Copy text").assertExists()
        onNodeWithText("Post publicly on GitHub").assertExists()
        onAllNodesWithText("This version can't send reports", substring = true).assertCountEquals(0)
        for (gone in
            listOf(
                "Share report", "Copy", "Send as a GitHub issue", "Everything this phone kept", "Details",
                "Keep crash categories on this phone", "Preview crash report", "Clear saved evidence",
                "Refresh exit history",
            )) {
            onAllNodesWithText(gone).assertCountEquals(0)
        }
    }

    @Test
    fun `R1-021 What's included lists exactly what is sent and what never is`() = runComposeUiTest {
        setContent { SettingsThemed { SettingsScreen(sheet, RecordingActions()) } }
        waitForIdle()
        onNodeWithTag("report-included-sheet").assertExists()
        onNodeWithText("This is exactly what gets sent.").assertExists()
        onNode(hasText(words) and hasAnyAncestor(hasTestTag("report-included-sheet"))).assertExists()
        onNodeWithText("Closed unexpectedly", substring = true).assertExists()
        onNodeWithText("OpenCode Mobile 2.1.0 (57)").assertExists()
        onNodeWithText("Android 15 · arm64 · 8-12 GB memory").assertExists()
        onNodeWithText("OpenCode 2.1.1 · address taken out").assertExists()
        onNodeWithText("Timed out (2) · Sign-in refused (1)").assertExists()
        onNodeWithText("Never sent: your conversations, files, passwords, keys and server addresses.").assertExists()
    }

    @Test
    fun `R1-022 the included line opens the sheet; Send report, GitHub and Done reach the presenter`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { SettingsThemed { SettingsScreen(form, actions) } }
        onNodeWithTag("report-included").performClick()
        onNodeWithText("Send report").performClick()
        onNodeWithText("Post publicly on GitHub").performClick()
        assertEquals(listOf("included:true", "send", "askPublic"), actions.calls)
    }

    @Test
    fun `R1-023 after a send the page shows Report sent, the code with its copy, and Done`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { SettingsThemed { SettingsScreen(sent, actions) } }
        waitForIdle()
        onNodeWithText("Report sent").assertExists()
        onNodeWithTag("report-code").assertExists()
        onNodeWithText("OC-7K4Q-2M").assertExists()
        onNodeWithText("Send report").assertDoesNotExist()
        onNodeWithText("Done").performClick()
        assertEquals(listOf("back"), actions.calls)
    }

    @Test
    fun `R1-024 a build that cannot send leads with Copy text and says why in one line`() = runComposeUiTest {
        setContent { SettingsThemed { SettingsScreen(noConfig, RecordingActions()) } }
        onNodeWithText("Send report").assertDoesNotExist()
        onNodeWithText("Copy text").assertExists()
        onNodeWithText("Post publicly on GitHub").assertExists()
        onNodeWithText("This version can't send reports. Copy the text, or post it on GitHub.").assertExists()
    }

    // ---- QA renders: R1_RENDER_DIR=<dir> ----

    private val outDir: File? = System.getenv("R1_RENDER_DIR")?.let(::File)

    @Test
    fun `R1 renders for QA (only with R1_RENDER_DIR)`() {
        val dir = outDir ?: return
        dir.mkdirs()
        val shots = listOf("form" to form, "sheet" to sheet, "sent" to sent)
        for (style in listOf(Style.Solid, Style.Glass)) for (dark in listOf(true, false)) {
            for ((name, ui) in shots) render(File(dir, "r1-$name-${style.storageName}-${if (dark) "dark" else "light"}.png"), style, dark, ui)
        }
        render(File(dir, "r1-form-noconfig-solid-dark.png"), Style.Solid, true, noConfig)
        render(File(dir, "r1-form-ar-glass-dark.png"), Style.Glass, true, form, locale = "ar")
    }

    private fun render(file: File, style: Style, dark: Boolean, ui: SettingsUi, locale: String = "en") =
        runDesktopComposeUiTest(WIDTH_PX, HEIGHT_PX) {
            Locale.setDefault(Locale.forLanguageTag(locale))
            setContent { Phone(style, dark, locale) { SettingsScreen(ui, RecordingActions()) } }
            waitForIdle()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
            Locale.setDefault(Locale.ENGLISH)
        }

    @Composable
    private fun Phone(style: Style, dark: Boolean, locale: String, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
            KitTheme(
                appearance =
                    KitAppearance(
                        mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                        style = style,
                        motion = MotionLevel.Off,
                    ),
                platform = DesktopKitPlatform(),
                localeTag = locale,
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    private companion object {
        const val DENSITY = 2.5f
        const val WIDTH_PX = 1030
        const val HEIGHT_PX = 2288
    }
}
