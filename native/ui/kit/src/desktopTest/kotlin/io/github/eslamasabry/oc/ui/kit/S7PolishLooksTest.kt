package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the batch-B3 parts at font scale 1.0 and 1.3, light, in `looks-out/s7/` (gitignored) for a person to
 * look at. They compare against nothing; the behaviour is held by [S7PolishKitTest].
 */
@OptIn(ExperimentalTestApi::class)
class S7PolishLooksTest {
    private val out = File(System.getProperty("user.dir"), "../../../looks-out/s7").canonicalFile.also { it.mkdirs() }

    /** One picture: its file name, window height in dp, direction and an optional gesture before the capture. */
    private class Shot(
        val name: String,
        val h: Int = 915,
        val rtl: Boolean = false,
        val act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
    )

    private fun shoot(shot: Shot, content: @Composable () -> Unit) {
        for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(412, shot.h) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f, scale),
                        LocalLayoutDirection provides if (shot.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        KitTheme(
                            KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                            FakeKitPlatform(),
                            if (shot.rtl) "ar" else "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                        }
                    }
                }
                waitForIdle()
                shot.act?.invoke(this)
                waitForIdle()
                // A popup (an open menu) is a second root over the window: draw every root in order.
                val roots = onAllNodes(isRoot()).fetchSemanticsNodes().indices.map { onAllNodes(isRoot())[it] }
                val first = roots.first().captureToImage().toAwtImage()
                val picture = BufferedImage(first.width, first.height, BufferedImage.TYPE_INT_ARGB)
                picture.createGraphics().apply {
                    roots.forEach { drawImage(it.captureToImage().toAwtImage(), 0, 0, null) }
                    dispose()
                }
                ImageIO.write(picture, "png", File(out, "${shot.name}-$scale.png"))
            }
        }
    }

    @Composable
    private fun Home() {
        KitScreen(
            topBar = {
                KitTopBar("Workspace", actions = listOf(KitLineAction("Settings", {}, icon = KitIcons.settingsGear)))
            },
            header = {
                Column {
                    KitText("Conversations", Modifier.padding(horizontal = 16.dp), role = TypeRole.LargeTitle)
                    KitText("2 need you", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            },
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                items(40) { KitRow("Conversation $it", supporting = "laptop · 2 min", onClick = {}) }
            }
        }
    }

    @Test fun `P-006 home at rest and scrolled`() {
        shoot(Shot("p006-home-rest")) { Home() }
        val scroll: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
            onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY, endY = centerY - 150f) }
        }
        shoot(Shot("p006-home-scrolled", act = scroll)) { Home() }
    }

    @Test fun `P-008 model sheet`() {
        val models = (1..9).map { KitModelOption("anthropic", "m$it", "Model $it", providerName = "Anthropic") }
        shoot(Shot("p008-model-sheet")) {
            KitSheetHost {
                KitModelSheet(true, {}, models, models[2].key, {}, favorites = listOf(models[4]), onToggleFavorite = {})
            }
        }
    }

    @Test fun `P-014 notices and status line`() {
        shoot(Shot("p014-notices", h = 400)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                val paused = KitStatus(KitStatusKind.Info, "Updates are paused here", tone = StatusTone.Neutral)
                KitStatusLine(paused)
                KitNotice("Your phone keeps these conversations", Modifier.padding(horizontal = 16.dp))
                KitRowGroup { KitRow("A row title beside it", onClick = {}) }
            }
        }
    }

    @Test fun `P-016 sub-page bars`() {
        shoot(Shot("p016-bars", h = 400)) {
            Column {
                KitTopBar("Settings", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {})
                KitTopBar("This phone", exit = KitTopBarExit.Close, exitLabel = "Close", onExit = {})
                KitRowGroup { KitRow("Appearance", onClick = {}) }
            }
        }
    }

    @Test fun `P-017 menu from a bottom chip and from a start-side button`() {
        val items =
            listOf(
                KitMenuItem("Ask each time", {}, checked = true),
                KitMenuItem("Approve edits", {}, checked = false),
                KitMenuItem("Approve everything", {}, checked = false),
            )
        shoot(Shot("p017-menu-bottom")) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                KitMenu(true, items, {}) { KitButton("Ask each time", onClick = {}) }
            }
        }
        shoot(Shot("p017-menu-start")) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterStart) {
                KitMenu(true, items, {}) { KitButton("⋯", onClick = {}) }
            }
        }
    }

    @Test fun `P-027 tall segmented, Dark chosen, both directions`() {
        val seg: @Composable () -> Unit = {
            Box(Modifier.padding(16.dp).height(120.dp)) {
                KitSegmented(
                    segments =
                        listOf(
                            KitSegment("auto", "Auto", KitIcons.systemTheme),
                            KitSegment("light", "Light", KitIcons.lightMode),
                            KitSegment("dark", "Dark", KitIcons.darkMode),
                        ),
                    selected = "dark",
                    semanticsLabel = "Light or dark",
                    tall = true,
                    onChange = {},
                )
            }
        }
        shoot(Shot("p027-segmented-en", h = 160), seg)
        shoot(Shot("p027-segmented-ar", h = 160, rtl = true), seg)
    }
}
