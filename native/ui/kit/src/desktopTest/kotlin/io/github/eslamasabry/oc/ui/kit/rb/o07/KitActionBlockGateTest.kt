package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.eslamasabry.oc.ui.kit.theme.KitFontFamilies
import io.github.eslamasabry.oc.ui.kit.theme.KitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/**
 * Every KitActionBlock scene of test/goldens/kit/kit_action_golden_test.dart (part-kit_buttons), with the Dart test's
 * own words: default at every window, Arabic and 2.0 text, disabled, working, destructive stack, overflow (More open),
 * copied (the copy pressed) and the shortcut hints at 1280 x 800 with a fine pointer.
 */
@OptIn(ExperimentalTestApi::class)
class KitActionBlockGateTest {
    private val noop: () -> Unit = {}

    /** The `_ar` goldens: Flutter's test loads its Noto Sans Arabic fixture, so the kit's Arabic roles use it here too. */
    private val arabicFixture: @Composable (@Composable () -> Unit) -> Unit = { inner ->
        val regular = Font("NotoSansArabic-Regular", fixtureBytes("NotoSansArabic-Regular.ttf"), FontWeight.Normal)
        val bold = Font("NotoSansArabic-Bold", fixtureBytes("NotoSansArabic-Bold.ttf"), FontWeight.Bold)
        val families = remember { FontFamily(regular, bold).let { KitFontFamilies(it, it) } }
        CompositionLocalProvider(LocalKitTypography provides KitTypography(families, true)) { inner() }
    }

    private fun fixtureBytes(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/fonts/$name")).readBytes()

    private fun blockDefault(ar: Boolean = false): @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction(if (ar) "حفظ" else "Save", noop),
            secondary = KitAction(if (ar) "إلغاء" else "Cancel", noop),
            tertiary = listOf(
                KitAction(if (ar) "تكرار" else "Duplicate", noop),
                KitAction(if (ar) "إعادة التسمية" else "Rename", noop),
            ),
        )
    }

    private val blockDisabled: @Composable () -> Unit = {
        KitActionBlock(
            secondary = KitAction("Cancel", noop),
            tertiary = listOf(KitAction("Duplicate", null, enabled = KitEnabled.Disabled("Fill in the server address first."))),
        )
    }

    private val blockWorking: @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction("Connect", noop, icon = KitIcons.server, working = true),
            secondary = KitAction("Cancel", noop),
        )
    }

    private val blockDestructiveStack: @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction("Update", noop),
            secondary = KitAction("Check now", noop),
            tertiary = listOf(KitAction("Restart", noop), KitAction("Delete server", noop, destructive = true)),
        )
    }

    private val blockOverflow: @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction("Save", noop),
            tertiary = listOf(
                KitAction("Duplicate", noop),
                KitAction("Rename", noop),
                KitAction("Delete", noop, destructive = true),
                KitAction("Copy link", noop, icon = KitIcons.copy),
                KitAction("Archive", noop),
            ),
        )
    }

    private val blockCopied: @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction("Done", noop),
            secondary = rememberKitCopyAction("Copy details", "Copied", { "Server fox at 100.64.0.7:4096" }),
        )
    }

    private val blockShortcut: @Composable () -> Unit = {
        KitActionBlock(
            primary = KitAction("Send", noop, shortcut = "Ctrl+Enter"),
            secondary = KitAction("Cancel", noop, shortcut = "Esc"),
            tertiary = listOf(KitAction("Save draft", noop, shortcut = "Ctrl+S")),
        )
    }

    /** The Dart test calls the copy button's onPressed directly: no pointer, so no pressed or hovered layer. */
    private val pressCopy: ComposeUiTest.() -> Unit = {
        onNodeWithText("Copy details").performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test fun kit_action_block_default() { FlutterGoldenGate.check("kit_action_block_default", content = blockDefault()) }
    @Test fun kit_action_block_default_360x800() { FlutterGoldenGate.check("kit_action_block_default_360x800", content = blockDefault()) }
    @Test fun kit_action_block_default_800x1280() { FlutterGoldenGate.check("kit_action_block_default_800x1280", content = blockDefault()) }
    @Test fun kit_action_block_default_915x412() { FlutterGoldenGate.check("kit_action_block_default_915x412", content = blockDefault()) }
    @Test fun kit_action_block_default_1280x800() { FlutterGoldenGate.check("kit_action_block_default_1280x800", content = blockDefault()) }
    @Test fun kit_action_block_default_1600x1000() { FlutterGoldenGate.check("kit_action_block_default_1600x1000", content = blockDefault()) }
    @Test fun kit_action_block_default_text2() { FlutterGoldenGate.check("kit_action_block_default_text2", content = blockDefault()) }
    @Test fun kit_action_block_default_text2_1280x800() { FlutterGoldenGate.check("kit_action_block_default_text2_1280x800", content = blockDefault()) }
    @Test fun kit_action_block_default_ar() { FlutterGoldenGate.check("kit_action_block_default_ar", wrap = arabicFixture, content = blockDefault(ar = true)) }
    @Test fun kit_action_block_default_ar_1280x800() {
        FlutterGoldenGate.check("kit_action_block_default_ar_1280x800", wrap = arabicFixture, content = blockDefault(ar = true))
    }
    @Test fun kit_action_block_disabled() { FlutterGoldenGate.check("kit_action_block_disabled", content = blockDisabled) }
    @Test fun kit_action_block_working() { FlutterGoldenGate.check("kit_action_block_working", content = blockWorking) }
    @Test fun kit_action_block_destructive_stack() { FlutterGoldenGate.check("kit_action_block_destructive_stack", content = blockDestructiveStack) }
    // Blocked (evidence/07.md): the open "More" menu is a Popup, a second root the gate's one-root capture cannot take
    // (FlutterGoldenGate.render, onRoot()). The shot renders the block with the menu closed until the gate captures
    // every root.
    @Test fun kit_action_block_overflow() { FlutterGoldenGate.check("kit_action_block_overflow", content = blockOverflow) }
    @Test fun kit_action_block_copied() { FlutterGoldenGate.check("kit_action_block_copied", act = pressCopy, content = blockCopied) }
    @Test fun kit_action_block_shortcut_1280x800() { FlutterGoldenGate.check("kit_action_block_shortcut_1280x800", content = blockShortcut) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_buttons",
            caps = mapOf("KitActionBlock" to "reading", "KitButton" to "control", "KitInset" to "wrapper"),
            content = blockDefault(),
        )
    }
}
