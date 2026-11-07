package io.github.eslamasabry.oc.ui.kit.rb.o10

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRisk
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitUntil
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_row_parts_golden_test.dart (KitSwitchRow and KitExpandRow): a Scaffold body with
 * a ListView padded by the gutter on every side, holding one unlabelled KitRowGroup of the scene's rows (`_shot`).
 * The views run at DPR 3 and are captured at 1x, as `FlutterCapture` repeats.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowPartsGateTest {
    /**
     * `_shot`: ListView(padding: EdgeInsetsDirectional.all(tokens.gutter), children: [KitRowGroup(children: rows)]),
     * full window width. It is gallery scaffolding, not a screen, so it runs as `Frame.Part` (the alignment gate's
     * page columns are for screens; ALIGN-RULES.md): the part frame already gives the 16 dp top, and the scene
     * takes the whole window width back from the frame's 720 dp gallery column, as Flutter's ListView does.
     */
    private fun page(rows: @Composable () -> Unit): @Composable () -> Unit = {
        Column(Modifier.requiredWidth(LocalKitWindow.current.widthDp.dp).padding(horizontal = 16.dp)) { KitRowGroup { rows() } }
    }

    private fun risk(current: KitUntil? = null) = KitRisk(
        scope = "Every conversation on this server",
        onLabel = "Auto-approve is on",
        icon = KitIcons.check,
        until = listOf(KitUntil.Off, KitUntil.Conversation, KitUntil.Hour),
        current = current,
    )

    private val scenes: Map<String, @Composable () -> Unit> = mapOf(
        "off" to { KitSwitchRow("Sounds", checked = false, supporting = "Play a sound when a reply arrives") {} },
        "on" to { KitSwitchRow("Sounds", checked = true, supporting = "Play a sound when a reply arrives") {} },
        "disabled" to { KitSwitchRow("Sounds", checked = false, enabled = KitEnabled.Disabled("Turn on notifications first")) {} },
        "risk_step" to { KitSwitchRow("Approve everything", checked = false, supporting = "Tools run without asking", riskSpec = risk()) {} },
        "risk_on" to { KitSwitchRow("Approve everything", checked = true, riskSpec = risk(current = KitUntil.Hour)) {} },
        "locked" to {
            KitSwitchRow("Core tools", checked = true, supporting = "Read, edit and run commands", locked = true, lockedLabel = "Always included") {}
        },
        "default" to {
            KitSwitchRow("Sounds", checked = false) {}
            KitSwitchRow("Vibrate", checked = true) {}
        },
    )

    private fun shot(name: String, scene: String) {
        FlutterGoldenGate.check(
            name,
            wrap = { FlutterCapture(it) },
            act = if (scene == "risk_step") ({ onNodeWithText("Approve everything").performSemanticsAction(SemanticsActions.OnClick) }) else null,
            content = page(scenes.getValue(scene)),
        )
    }

    @Test fun kit_switch_row_off() = shot("kit_switch_row_off", "off")
    @Test fun kit_switch_row_on() = shot("kit_switch_row_on", "on")
    @Test fun kit_switch_row_disabled() = shot("kit_switch_row_disabled", "disabled")
    @Test fun kit_switch_row_risk_step() = shot("kit_switch_row_risk_step", "risk_step")
    @Test fun kit_switch_row_risk_on() = shot("kit_switch_row_risk_on", "risk_on")
    @Test fun kit_switch_row_locked() = shot("kit_switch_row_locked", "locked")
    @Test fun kit_switch_row_default_1280x800() = shot("kit_switch_row_default_1280x800", "default")

    /** `_expandRows`: "Built-in plugins", "3 plugins, all on", the more glyph, and three plain rows inside. */
    private fun expand(open: Boolean): @Composable () -> Unit = page { expandRow(open) }

    @Composable private fun expandRow(open: Boolean) {
        KitExpandRow(
            "Built-in plugins", open, Modifier, "3 plugins, all on", {}, KitIcons.more,
            inset = true, titleMaxLines = 1, supportingMaxLines = 1, bare = true,
        ) {
            KitRow("Formatter")
            KitRow("Linter")
            KitRow("Git")
        }
    }

    private fun expandShot(name: String, open: Boolean) {
        FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }, content = expand(open))
    }

    @Test fun kit_expand_row_folded() = expandShot("kit_expand_row_folded", open = false)
    @Test fun kit_expand_row_open() = expandShot("kit_expand_row_open", open = true)
    @Test fun kit_expand_row_open_1280x800() = expandShot("kit_expand_row_open_1280x800", open = true)

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_row_parts",
            caps = mapOf("KitChevron" to "glyph", "KitExpandRow" to "list", "KitRowIcon" to "glyph", "KitRowMenu" to "glyph", "KitSwitchRow" to "list"),
            content = page {
                scenes.getValue("off")()
                scenes.getValue("risk_on")()
                scenes.getValue("locked")()
                expandRow(open = true)
            },
        )
    }
}
