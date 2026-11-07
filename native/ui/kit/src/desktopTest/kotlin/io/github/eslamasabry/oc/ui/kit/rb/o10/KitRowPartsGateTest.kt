package io.github.eslamasabry.oc.ui.kit.rb.o10

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.test.Test

/**
 * The KitSwitchRow and KitExpandRow scenes of test/goldens/kit/kit_row_parts_golden_test.dart. Its `_shot` is not
 * kitGalleryPart: a Scaffold whose body is a ListView padded by the gutter on every side holding one unlabelled
 * KitRowGroup, captured at the view's pixel ratio 3 (render.tsv gives the density), so these run as `Frame.Screen`.
 *
 * Approximations (native inputs missing): the Flutter `KitRisk` (scope "Every conversation on this server", on-label
 * "Auto-approve is on", until off / conversation / hour) is a plain risk line here. `risk_on` (current: hour) has the
 * Flutter supporting line "<scope> · For an hour" passed as `supporting`; `risk_step` unfolds the native step, which
 * offers "Turn on" / "Not now" instead of the three "until" buttons and "Not now".
 */
@OptIn(ExperimentalTestApi::class)
class KitRowPartsGateTest {
    private val scope = "Every conversation on this server"

    @Composable private fun Shot(rows: @Composable () -> Unit) {
        val gutter = LocalKitTokens.current.gutter.dp
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(gutter)) { KitRowGroup(rows = rows) }
    }

    private val off: @Composable () -> Unit = {
        Shot { KitSwitchRow("Sounds", checked = false, supporting = "Play a sound when a reply arrives") {} }
    }
    private val on: @Composable () -> Unit = {
        Shot { KitSwitchRow("Sounds", checked = true, supporting = "Play a sound when a reply arrives") {} }
    }
    private val disabled: @Composable () -> Unit = {
        Shot { KitSwitchRow("Sounds", checked = false, enabled = KitEnabled.Disabled("Turn on notifications first")) {} }
    }
    private val riskStep: @Composable () -> Unit = {
        Shot { KitSwitchRow("Approve everything", checked = false, supporting = "Tools run without asking", risk = scope) {} }
    }
    private val riskOn: @Composable () -> Unit = {
        Shot { KitSwitchRow("Approve everything", checked = true, supporting = "$scope · For an hour", risk = scope) {} }
    }
    private val locked: @Composable () -> Unit = {
        Shot {
            KitSwitchRow(
                "Core tools",
                checked = true,
                supporting = "Read, edit and run commands",
                locked = true,
                lockedLabel = "Always included",
            ) {}
        }
    }
    private val default: @Composable () -> Unit = {
        Shot {
            KitSwitchRow("Sounds", checked = false) {}
            KitSwitchRow("Vibrate", checked = true) {}
        }
    }

    private fun expand(open: Boolean): @Composable () -> Unit = {
        Shot {
            KitExpandRow(
                "Built-in plugins",
                expanded = open,
                supporting = "3 plugins, all on",
                onExpandedChange = {},
                leading = KitIcons.more,
            ) {
                KitRow("Formatter")
                KitRow("Linter")
                KitRow("Git")
            }
        }
    }

    /** The scene's `then`: a tap on the title opens the risk step. */
    private val tapTitle: androidx.compose.ui.test.ComposeUiTest.() -> Unit = { onNodeWithText("Approve everything").performClick() }

    @Test fun kit_switch_row_off() { FlutterGoldenGate.check("kit_switch_row_off", Frame.Screen, content = off) }
    @Test fun kit_switch_row_on() { FlutterGoldenGate.check("kit_switch_row_on", Frame.Screen, content = on) }
    @Test fun kit_switch_row_disabled() { FlutterGoldenGate.check("kit_switch_row_disabled", Frame.Screen, content = disabled) }
    @Test fun kit_switch_row_risk_step() { FlutterGoldenGate.check("kit_switch_row_risk_step", Frame.Screen, act = tapTitle, content = riskStep) }
    @Test fun kit_switch_row_risk_on() { FlutterGoldenGate.check("kit_switch_row_risk_on", Frame.Screen, content = riskOn) }
    @Test fun kit_switch_row_locked() { FlutterGoldenGate.check("kit_switch_row_locked", Frame.Screen, content = locked) }
    @Test fun kit_switch_row_default_1280x800() { FlutterGoldenGate.check("kit_switch_row_default_1280x800", Frame.Screen, content = default) }
    @Test fun kit_expand_row_folded() { FlutterGoldenGate.check("kit_expand_row_folded", Frame.Screen, content = expand(false)) }
    @Test fun kit_expand_row_open() { FlutterGoldenGate.check("kit_expand_row_open", Frame.Screen, content = expand(true)) }
    @Test fun kit_expand_row_open_1280x800() { FlutterGoldenGate.check("kit_expand_row_open_1280x800", Frame.Screen, content = expand(true)) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_row_parts",
            caps = mapOf(
                "KitChevron" to "glyph", "KitExpandRow" to "list", "KitRowIcon" to "glyph", "KitRowMenu" to "glyph",
                "KitSwitchRow" to "list",
            ),
            frame = Frame.Screen,
        ) {
            Shot {
                KitSwitchRow("Sounds", checked = false, supporting = "Play a sound when a reply arrives") {}
                KitExpandRow("Built-in plugins", expanded = true, supporting = "3 plugins, all on", onExpandedChange = {}, leading = KitIcons.more) {
                    KitRow("Formatter")
                }
            }
        }
    }
}
