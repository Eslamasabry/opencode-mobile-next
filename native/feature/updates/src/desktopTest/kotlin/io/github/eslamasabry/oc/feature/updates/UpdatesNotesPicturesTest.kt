package io.github.eslamasabry.oc.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesUi
import io.github.eslamasabry.oc.feature.updates.presentation.updatesUi
import io.github.eslamasabry.oc.feature.updates.ui.UpdatesScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appUpdateAvailableLine
import io.github.eslamasabry.oc.ui.words.res.appUpdateBuildLabel
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckSwitch
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckSwitchBody
import io.github.eslamasabry.oc.ui.words.res.appUpdateCurrentVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdateSizeMb
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.appUpdateWhatsNew
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

private const val WIDTH_DP = 360
private const val HEIGHT_DP = 960
private const val DENSITY = 2f
private const val NOTES_PATH = "docs/releases/v3.0.3+10160.md"

/** Writes BEFORE (plain KitText) and AFTER (KitMarkdown) pictures of UpdatesScreen at 360 dp. */
@OptIn(ExperimentalTestApi::class)
class UpdatesNotesPicturesTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val now = Instant.fromEpochMilliseconds(1_767_225_600_000)

    private data class Variant(val mode: AppearanceMode, val scale: Float, val tag: String)

    private object NoopActions : UpdatesActions {
        override fun onBack() = Unit

        override fun onUpdate() = Unit

        override fun onCancel() = Unit

        override fun onInstall() = Unit

        override fun onTryAgain() = Unit

        override fun onOpenRelease() = Unit

        override fun onCheckNow() = Unit

        override fun onChecks(on: Boolean) = Unit

        override fun onOpenNoteLink(url: String) = Unit

        override fun onConfirmNoteLink() = Unit

        override fun onDismissNoteLink() = Unit

        override fun onDismissLinkNotice() = Unit
    }

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !File(d, NOTES_PATH).isFile) d = d.parentFile
        return checkNotNull(d) { "$NOTES_PATH not found from ${System.getProperty("user.dir")}" }
    }

    @Test
    fun `B-MIGR-012 updates page What's new pictures before and after at 360 dp`() {
        val root = repoRoot()
        val releaseUrl = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v3.0.3"
        val notes =
            File(root, NOTES_PATH).readText().trimEnd() +
                "\n\nSee the [release notes on GitHub]($releaseUrl)."
        val out = File(root, "native/feature/updates/build/renders").apply { mkdirs() }
        val state =
            updatesUi(
                AppUpdateState(
                    phase = UpdatePhase.Available,
                    release = RELEASE.copy(notes = notes),
                    downloadSize = APK_SIZE,
                    installed = INSTALLED,
                ),
                now,
            )
        val testBuildState =
            updatesUi(
                AppUpdateState(
                    phase = UpdatePhase.Available,
                    release = RELEASE.copy(notes = notes),
                    downloadSize = APK_SIZE,
                    installed = InstalledApp(PACKAGE, "3.0.2-test.10142", 10142),
                ),
                now,
            )
        val signerFailureState =
            updatesUi(
                AppUpdateState(
                    phase = UpdatePhase.Failed,
                    failure = UpdateFailure.Signer,
                    detail =
                        "signer 842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C, " +
                            "expected 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C",
                    release = RELEASE.copy(notes = notes),
                    downloadSize = APK_SIZE,
                    installed = INSTALLED,
                ),
                now,
            )
        val variants =
            listOf(
                Variant(AppearanceMode.Light, 1.0f, "light-t10"),
                Variant(AppearanceMode.Light, 1.3f, "light-t13"),
                Variant(AppearanceMode.Dark, 1.0f, "dark-t10"),
                Variant(AppearanceMode.Dark, 1.3f, "dark-t13"),
            )
        for (v in variants) {
            capture(File(out, "before-updates-whats-new-360-${v.tag}.png"), v) {
                BeforeUpdatesScreen(state, NoopActions)
            }
            capture(File(out, "after-updates-whats-new-360-${v.tag}.png"), v) {
                UpdatesScreen(state, NoopActions)
            }
            capture(File(out, "after-updates-test-build-360-${v.tag}.png"), v) {
                UpdatesScreen(testBuildState, NoopActions)
            }
            capture(File(out, "after-updates-signer-failure-360-${v.tag}.png"), v, openDetails = true) {
                UpdatesScreen(signerFailureState, NoopActions)
            }
        }
    }

    private fun capture(
        file: File,
        variant: Variant,
        openDetails: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        runDesktopComposeUiTest((WIDTH_DP * DENSITY).toInt(), (HEIGHT_DP * DENSITY).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, variant.scale)) {
                    KitTheme(
                        appearance = KitAppearance(mode = variant.mode, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(
                            LocalKitWindow provides
                                KitWindowInfo(WIDTH_DP.toFloat(), HEIGHT_DP.toFloat()),
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(LocalKitColors.current.ground),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
            waitForIdle()
            if (openDetails) {
                onNodeWithText("Details").performScrollTo().performClick()
                waitForIdle()
            }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", file)
            assertTrue(file.isFile && file.length() > 0L, "expected picture ${file.path}")
        }
    }

    /** Pre-fix UpdatesScreen for the available state, drawing What's new with plain KitText. */
    @Composable
    private fun BeforeUpdatesScreen(state: UpdatesUi, actions: UpdatesActions) {
        val gutter = LocalKitTokens.current.gutter.dp
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.appUpdatesTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::onBack,
                )
            },
            width = KitScreenWidth.Reading,
            edges = KitScreenEdges.Bleed,
        ) {
            BeforeUpdatesBody(state, actions, gutter)
        }
    }

    @Composable
    private fun BeforeUpdatesBody(
        state: UpdatesUi,
        actions: UpdatesActions,
        gutter: Dp,
    ) {
        val line =
            stringResource(
                Res.string.appUpdateAvailableLine,
                state.releaseVersion,
                stringResource(Res.string.appUpdateSizeMb, (state.sizeMb ?: 0L).toString()),
            )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(kitPageScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.appUpdateCurrentVersion, state.version ?: ""),
                    supporting = line,
                    supportingMaxLines = 3,
                    trailing =
                        KitRowTrailing.Action(
                            KitLineAction(stringResource(Res.string.appUpdateUpdate), actions::onUpdate),
                        ),
                )
            }
            state.build?.let { build ->
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    modifier = Modifier.padding(horizontal = gutter),
                    notes = listOf("${stringResource(Res.string.appUpdateBuildLabel)} · $build"),
                )
            }
            KitRowGroup {
                KitSwitchRow(
                    title = stringResource(Res.string.appUpdateCheckSwitch),
                    checked = state.checksOn,
                    supporting = stringResource(Res.string.appUpdateCheckSwitchBody),
                    onCheckedChange = actions::onChecks,
                )
                KitRow(
                    title = stringResource(Res.string.appUpdateCheckNow),
                    enabled = state.checkNowEnabled,
                    onClick = actions::onCheckNow,
                )
            }
            state.notes?.let { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitSectionLabel(stringResource(Res.string.appUpdateWhatsNew))
                    KitText(notes, Modifier.padding(horizontal = gutter).testTag("updates-notes"))
                }
            }
        }
    }
}
