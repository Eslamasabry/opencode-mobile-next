package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_tappable_golden_test.dart: a row-shaped tappable on a panel, per state. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitTappableGateTest {
    /**
     * [asRow]: the gallery shots (Frame.Part) declare the row role, the row-shaped tappable the Dart scene draws, so the
     * layout guard reads it as a bleeding row and not as a 680 dp button. The interactive shots keep the default role:
     * they pump a bare Scaffold (Frame.Screen), where a row is held to the page's list cap and text columns, which the
     * Flutter gallery row (words at 70 dp, 1240 dp wide) does not follow.
     */
    private fun row(enabled: Boolean, disabledReason: String? = null, asRow: Boolean = false): @Composable () -> Unit = {
        Box(Modifier.padding(20.dp).fillMaxWidth()) {
            val roles = LocalKitRoles.current
            KitSurfacePanel(Modifier.fillMaxWidth(), padding = KitSurfacePadding.None) {
                KitTappable(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = enabled, disabledReason = disabledReason, shape = KitShapeId.Panel, role = if (asRow) KitTappableRole.Row else KitTappableRole.Button) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Image(KitIcons.archive, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color((if (enabled) roles.text2 else roles.text3).v)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            KitText("Archive \"Fix the login bug\"", role = TypeRole.RowTitle, tone = if (enabled) TextTone.Primary else TextTone.Tertiary)
                            if (!enabled && disabledReason != null) KitText(disabledReason, role = TypeRole.Secondary, tone = TextTone.Secondary)
                        }
                    }
                }
            }
        }
    }

    private val enabled = row(true)
    private val enabledRow = row(true, asRow = true)
    private val disabled = row(false, "Needs a connection", asRow = true)

    /** The interactive shots pump a bare Scaffold (no gallery frame, no 16 dp part padding), so they take the whole window. */
    private fun shot(name: String, act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, frame = if (act == null) FlutterGoldenGate.Frame.Part else FlutterGoldenGate.Frame.Screen, act = act, content = content)
    }

    @Test fun static_states() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_tappable_enabled$size", content = enabledRow)
            shot("kit_tappable_disabled$size", content = disabled)
        }
    }

    @Test fun enabled_text2() {
        for (size in listOf("", "_1280x800")) shot("kit_tappable_enabled_text2$size", content = enabledRow)
    }

    @Test fun hovered() = shot("kit_tappable_hovered", act = { onRoot().performMouseInput { moveTo(center) } }, content = enabled)
    @Test fun pressed() = shot("kit_tappable_pressed", act = { onRoot().performMouseInput { moveTo(center); press() } }, content = enabled)
    @Test fun focused() {
        for (size in listOf("", "_1280x800")) shot("kit_tappable_focused$size", act = { onRoot().performKeyInput { pressKey(Key.Tab) } }, content = enabled)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_tappable", caps = mapOf("KitTappable" to "wrapper"), content = enabled)
    }
}
