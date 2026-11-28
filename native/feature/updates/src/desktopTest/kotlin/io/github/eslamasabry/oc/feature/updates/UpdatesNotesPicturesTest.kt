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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.presentation.NoteLinkAction
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
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
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
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

internal const val WIDTH_DP = 360
internal const val HEIGHT_DP = 960
internal const val DENSITY = 2f
private const val NOTES_PATH = "docs/releases/v3.0.3+10160.md"
private const val EDGE_DP = 16

/** The one https link the notes carry (B-628). */
internal const val NOTE_LINK_TEXT = "release notes on GitHub"
internal const val NOTE_LINK_URL = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v3.0.3"

/** One picture of the 360 dp screen: light or dark, text at 1.0 or 1.3. */
internal data class Variant(val mode: AppearanceMode, val scale: Float, val tag: String)

/** The four pictures of the 360 dp screen: light and dark, text at 1.0 and 1.3. */
internal val VARIANTS =
    listOf(
        Variant(AppearanceMode.Light, 1.0f, "light-t10"),
        Variant(AppearanceMode.Light, 1.3f, "light-t13"),
        Variant(AppearanceMode.Dark, 1.0f, "dark-t10"),
        Variant(AppearanceMode.Dark, 1.3f, "dark-t13"),
    )

/** The part of a 360 dp screen a link must sit in to be seen whole: a finger's width from every edge. */
private val FRAME = DpRect(EDGE_DP.dp, EDGE_DP.dp, (WIDTH_DP - EDGE_DP).dp, (HEIGHT_DP - EDGE_DP).dp)

/** The repository root, found from the folder the test runs in. */
internal fun repoRoot(): File {
    var d: File? = File(System.getProperty("user.dir")).absoluteFile
    while (d != null && !File(d, NOTES_PATH).isFile) d = d.parentFile
    return checkNotNull(d) { "$NOTES_PATH not found from ${System.getProperty("user.dir")}" }
}

/** The real 3.0.3 notes with one https link in them (B-628). */
internal fun notesWithLink(): String =
    File(repoRoot(), NOTES_PATH).readText().trimEnd() + "\n\nSee the [$NOTE_LINK_TEXT]($NOTE_LINK_URL)."

/** Raw Markdown that reached the screen: bold or link marks, a list marker or a heading's hashes. */
internal fun showsRawMarkdown(text: String): Boolean =
    "**" in text ||
        "[" in text ||
        text.lines().any { it.trimStart().startsWith("#") || it.trimStart().startsWith("- ") }

/** The What's new link as the screen draws it right now. */
internal data class DrawnLink(
    val annotation: LinkAnnotation.Clickable,
    val layout: TextLayoutResult,
    /** Its first and last letter, as offsets in the paragraph it sits in. */
    val first: Int,
    val last: Int,
    /** The link on the screen, in dp from the window's corner. */
    val box: DpRect,
    /** The middle of its first letter, in dp from the window's corner: where a finger lands. */
    val tap: Offset,
    /** The part of its paragraph the window shows, in dp from the window's corner. */
    val seenText: DpRect,
)

/** True when every line the link fills to its end breaks at the space after a word, never inside one. */
private fun DrawnLink.breaksBetweenWords(): Boolean {
    val words = layout.layoutInput.text.text
    val lines = layout.getLineForOffset(first) until layout.getLineForOffset(last)
    return lines.all { line ->
        val end = layout.getLineEnd(line, visibleEnd = true)
        end in (first + 1)..(last + 1) && words[end].isWhitespace()
    }
}

/** True when the whole link is on screen, inside [FRAME] and inside all that could hide it. */
private fun DrawnLink.isShownWhole(): Boolean = liesInside(FRAME, box) && liesInside(seenText, box, 1.dp)

/** True when [inner] lies wholly inside [outer], give or take [slack]; their edges may touch. */
private fun liesInside(outer: DpRect, inner: DpRect, slack: Dp = 0.dp): Boolean =
    inner.left >= outer.left - slack &&
        inner.right <= outer.right + slack &&
        inner.top >= outer.top - slack &&
        inner.bottom <= outer.bottom + slack

/** Every word the screen draws, the way a person reads it. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.screenWords(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .map { it.text }

/** The What's new link as the screen draws it right now; fails when the notes on screen have none. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.drawnNoteLink(): DrawnLink {
    val node =
        onNode(
                hasText(NOTE_LINK_TEXT, substring = true) and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    val layout = results.single()
    val text = layout.layoutInput.text
    val link = text.getLinkAnnotations(0, text.length).single()
    var glyphs = layout.getBoundingBox(link.start)
    for (at in link.start + 1 until link.end) glyphs = glyphs.expandToInclude(layout.getBoundingBox(at))
    val origin = node.positionInRoot
    val perDp = density.density
    val letter = layout.getBoundingBox(link.start)
    return DrawnLink(
        annotation = link.item as LinkAnnotation.Clickable,
        layout = layout,
        first = link.start,
        last = link.end - 1,
        box =
            DpRect(
                origin.x + (glyphs.left / perDp).dp,
                origin.y + (glyphs.top / perDp).dp,
                origin.x + (glyphs.right / perDp).dp,
                origin.y + (glyphs.bottom / perDp).dp,
            ),
        tap = Offset(origin.x + (letter.center.x / perDp).dp, origin.y + (letter.center.y / perDp).dp),
        seenText = node.boundsInRoot,
    )
}

/**
 * B-628: the What's new link as drawn now is styled, no raw Markdown is on the screen, and the link is whole
 * inside the 360 dp frame. [at] names the picture in every failure.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertNoteLinkShown(at: String): DrawnLink {
    val link = drawnNoteLink()
    val because = "$at: "
    val look = link.annotation.styles?.style
    assertEquals(NOTE_LINK_URL, link.annotation.tag, because + "the link keeps its address")
    assertEquals(TextDecoration.Underline, look?.textDecoration, because + "the link is underlined")
    assertTrue(look?.color?.isSpecified == true, because + "the link has a colour of its own")
    assertTrue(screenWords().none(::showsRawMarkdown), because + "raw Markdown reached the screen")
    assertTrue(!link.layout.hasVisualOverflow, because + "the link's paragraph is cut")
    assertTrue(link.breaksBetweenWords(), because + "the link breaks inside a word")
    assertTrue(link.isShownWhole(), because + "the link is not whole inside the frame: ${link.box}")
    return link
}

/** Writes BEFORE (plain KitText) and AFTER (KitMarkdown) pictures of UpdatesScreen at 360 dp. */
@OptIn(ExperimentalTestApi::class)
class UpdatesNotesPicturesTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val now = Instant.fromEpochMilliseconds(1_767_225_600_000)

    private object NoopActions : UpdatesActions {
        override fun onBack() = Unit

        override fun onUpdate() = Unit

        override fun onCancel() = Unit

        override fun onInstall() = Unit

        override fun onTryAgain() = Unit

        override fun onOpenRelease() = Unit

        override fun onCheckNow() = Unit

        override fun onChecks(on: Boolean) = Unit

        override fun onNoteLink(action: NoteLinkAction) = Unit
    }

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private data class PictureStates(
        val available: UpdatesUi,
        val testBuild: UpdatesUi,
        val signerFailure: UpdatesUi,
    )

    private fun pictureStates(notes: String): PictureStates {
        val rel = RELEASE.copy(notes = notes)
        val detail =
            "signer 842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C, " +
                "expected 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
        return PictureStates(
            available =
                updatesUi(
                    AppUpdateState(
                        phase = UpdatePhase.Available,
                        release = rel,
                        downloadSize = APK_SIZE,
                        installed = INSTALLED,
                    ),
                    now,
                ),
            testBuild =
                updatesUi(
                    AppUpdateState(
                        phase = UpdatePhase.Available,
                        release = rel,
                        downloadSize = APK_SIZE,
                        installed = InstalledApp(PACKAGE, "3.0.2-test.10142", 10142),
                    ),
                    now,
                ),
            signerFailure =
                updatesUi(
                    AppUpdateState(
                        phase = UpdatePhase.Failed,
                        failure = UpdateFailure.Signer,
                        detail = detail,
                        release = rel,
                        downloadSize = APK_SIZE,
                        installed = INSTALLED,
                    ),
                    now,
                ),
        )
    }

    @Test
    fun `B-MIGR-012 updates page What's new pictures before and after at 360 dp`() {
        val out = File(repoRoot(), "native/feature/updates/build/renders").apply { mkdirs() }
        val states = pictureStates(notesWithLink())
        for (v in VARIANTS) {
            capture(File(out, "before-updates-whats-new-360-${v.tag}.png"), v) {
                BeforeUpdatesScreen(states.available, NoopActions)
            }
            capture(File(out, "after-updates-test-build-360-${v.tag}.png"), v) {
                UpdatesScreen(states.testBuild, NoopActions)
            }
            capture(File(out, "after-updates-signer-failure-360-${v.tag}.png"), v, openDetails = true) {
                UpdatesScreen(states.signerFailure, NoopActions)
            }
        }
    }

    /** Each What's new picture is written only after its link passed [assertNoteLinkShown] in the same frame. */
    @Test
    fun `B-628 What's new link is styled and whole inside the frame in all four 360 dp pictures`() {
        val out = File(repoRoot(), "native/feature/updates/build/renders").apply { mkdirs() }
        val available = pictureStates(notesWithLink()).available
        for (v in VARIANTS) {
            capture(
                File(out, "after-updates-whats-new-360-${v.tag}.png"),
                v,
                assertions = { assertNoteLinkShown(v.tag) },
            ) {
                UpdatesScreen(available, NoopActions)
            }
        }
    }

    private fun capture(
        file: File,
        variant: Variant,
        openDetails: Boolean = false,
        assertions: ComposeUiTest.() -> Unit = {},
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
                            LocalKitRedactor provides Redactor(),
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
            assertions()
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
