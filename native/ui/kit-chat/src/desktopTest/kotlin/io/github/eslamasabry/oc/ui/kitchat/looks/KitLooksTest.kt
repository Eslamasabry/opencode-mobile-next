package io.github.eslamasabry.oc.ui.kitchat.looks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStopRing
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Looks-pass evidence, not goldens: each kit part group drawn in a phone frame (412 × 915 dp at 2.625, so
 * 1081 × 2402 px), light and dark, written to `looks-out/kit/<part>-native-after-<mode>.png` for the side-by-side
 * with the Flutter goldens. Nothing is compared. Run: `./gradlew :ui:kit-chat:desktopTest --tests '*KitLooksTest*'`.
 */
@OptIn(ExperimentalTestApi::class)
class KitLooksTest {
    private val out = File(System.getProperty("user.dir"), "../../../looks-out/kit").also { it.mkdirs() }

    private fun shoot(part: String, motion: MotionLevel = MotionLevel.Off, content: @Composable ColumnScope.() -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(1081, 2402) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = motion),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Column(Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(top = 32.dp), content = content)
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_200)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$part-native-after-${if (dark) "dark" else "light"}.png"))
            }
        }
    }

    @Test
    fun `top bar and large title`() =
        shoot("top-bar") {
            KitTopBar(
                title = "Conversations",
                large = true,
                actions = listOf(KitLineAction("Search", {}, icon = KitIcons.search), KitLineAction("Settings", {}, icon = KitIcons.settingsGear)),
            )
            Spacer(Modifier.height(32.dp))
            KitTopBar(
                title = "Fix flaky checkout test",
                subtitle = "shopfront · Claude Opus 5.5",
                exit = KitTopBarExit.Back,
                exitLabel = "Back",
                onExit = {},
                actions = listOf(KitLineAction("Files", {}, icon = KitIcons.files)),
            )
            Spacer(Modifier.height(32.dp))
            KitTopBar(title = "Settings", large = true, exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {})
            Spacer(Modifier.height(32.dp))
            KitTopBar(title = "Servers", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}, actions = listOf(KitLineAction("Add server", {})))
        }

    @Test
    fun `search field`() =
        shoot("search-field") {
            KitTopBar(title = "Settings", large = true, exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {})
            Box(Modifier.padding(horizontal = 16.dp)) { KitSearchField(label = "Search settings", onSearch = {}) }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitSearchField(label = "Search models", onSearch = {}, state = rememberTextFieldState("opus"), resultCount = 2)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { KitField(rememberTextFieldState("laptop.tail1234.ts.net"), "Address", kind = KitFieldKind.Url) }
        }

    @Test
    fun `rows with icons values state and time`() =
        shoot("rows") {
            KitSectionLabel("Server")
            KitRow("Laptop", leading = KitRowLeading.Glyph(KitIcons.computer), trailing = KitRowTrailing.Value("Connected"), onClick = {})
            KitSectionLabel("Agent")
            KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Value("Opus 5.5"), onClick = {})
            KitRow("Approvals", leading = KitRowLeading.Glyph(KitIcons.shield), trailing = KitRowTrailing.Value("Ask first"), onClick = {})
            KitRow("Keep running", supporting = "What to allow so Android doesn't close the app", leading = KitRowLeading.Glyph(KitIcons.batteryCharging), trailing = KitRowTrailing.Chevron, onClick = {})
            KitSectionLabel("Needs you")
            KitRow("Fix the login bug", supporting = "shopfront · Allow running the tests?", state = KitRowState("Needs you", KitRowStateTone.NeedsYou), meta = "2 min", onClick = {})
            KitSectionLabel("Today")
            KitRow("Add dark mode", supporting = "notes-app · Editing lib/ui/theme.dart", state = KitRowState("Working", KitRowStateTone.Running), meta = "9 min", onClick = {})
            KitRow("Explain the build", supporting = "shopfront", state = KitRowState("Done", KitRowStateTone.Quiet), meta = "3 h", onClick = {})
        }

    @Test
    fun `buttons`() =
        shoot("buttons") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KitButton("Set up on this phone", {}, fullWidth = true)
                KitButton("Copy command", {}, role = KitButtonRole.Secondary, icon = KitIcons.copy, fullWidth = true)
                KitButton("I already have a server", {}, role = KitButtonRole.Tertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
                KitButton("Save & connect", {}, enabled = KitEnabled.Disabled("Fill in the address first"), fullWidth = true)
                KitButton("Connecting", {}, working = true, fullWidth = true)
                KitButton("Remove this server", {}, destructive = true, fullWidth = true)
            }
        }

    @Test
    fun `chip row`() =
        shoot("chips") {
            KitFilterChips {
                KitChip("All projects", kind = KitChipKind.Action, onClick = {}, opensMenu = true)
                KitChip("Needs you", kind = KitChipKind.Count, count = 2, tone = KitChipTone.Attention, onClick = {})
                KitChip("Running", kind = KitChipKind.Count, count = 1, onClick = {})
                KitChip("Done", onClick = {}, kind = KitChipKind.Action)
            }
            Spacer(Modifier.height(24.dp))
            KitFilterChips {
                KitChip("All", kind = KitChipKind.Action, selected = true, onClick = {})
                KitChip("Favorites", kind = KitChipKind.Action, selected = false, onClick = {})
                KitChip("Recent", kind = KitChipKind.Action, selected = false, onClick = {})
            }
        }

    @Test
    fun `message box idle and running`() =
        shoot("composer", motion = MotionLevel.Calm) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitComposerBox(
                    running = false,
                    field = { KitField(rememberTextFieldState(), "Ask Claude Code…", kind = KitFieldKind.Multiline, multilineLines = 1..6, labelShown = false, framed = false) },
                    leading = { KitComposerAdd("Add", {}) },
                    chips = {
                        KitComposerChip("Opus 5.5", {})
                        KitComposerChip("Asks first", {}, icon = KitIcons.shield, opensPicker = false)
                    },
                    trailing = { KitComposerSend("Send", {}, enabled = KitEnabled.Disabled("Type a message")) },
                )
            }
            Spacer(Modifier.height(48.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitComposerBox(
                    running = true,
                    field = { KitField(rememberTextFieldState("Also check the refund flow"), "Ask Claude Code…", kind = KitFieldKind.Multiline, multilineLines = 1..6, labelShown = false, framed = false) },
                    leading = { KitComposerAdd("Add", {}) },
                    chips = {
                        KitComposerChip("Opus 5.5", {})
                        KitComposerChip("Asks first", {}, icon = KitIcons.shield, opensPicker = false)
                    },
                    trailing = { KitComposerStopRing("Stop Claude Code", {}) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
}
