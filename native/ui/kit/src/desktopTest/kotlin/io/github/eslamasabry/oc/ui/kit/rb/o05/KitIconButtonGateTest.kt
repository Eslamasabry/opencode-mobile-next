package io.github.eslamasabry.oc.ui.kit.rb.o05

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_icon_button_golden_test.dart: the button on ground and on a surface1 panel. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitIconButtonGateTest {
    private fun scene(
        icon: ImageVector = KitIcons.retry,
        tooltip: String = "Retry connection",
        shortcut: String? = null,
        destructive: Boolean = false,
        working: Boolean = false,
        selected: Boolean? = null,
        disabled: Boolean = false,
        copy: Boolean = false,
    ): @Composable () -> Unit = {
        val roles = LocalKitColors.current
        @Composable fun button(tag: String) {
            if (copy) {
                KitIconButtonCopy(text = { "flutter test test/checkout_test.dart" }, modifier = Modifier.testTag(tag))
            } else {
                KitIconButton(
                    icon, tooltip, onClick = {}, modifier = Modifier.testTag(tag), shortcut = shortcut, destructive = destructive,
                    working = working, selected = selected,
                    enabled = if (disabled) KitEnabled.Disabled("Read-only connection") else KitEnabled.Enabled,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            button("ground")
            Spacer(Modifier.width(56.dp))
            Box(Modifier.background(roles.surface1, RoundedCornerShape(18.dp)).padding(20.dp)) { button("panel") }
        }
    }

    /** A pushed route is a Scaffold body: the scene is centred in the whole window. */
    private fun pushed(content: @Composable () -> Unit): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }

    /** debugPlatformCapabilities = null in the Dart test: a touch window, no fine pointer. */
    private val touchWindow: @Composable (@Composable () -> Unit) -> Unit = { inner ->
        CompositionLocalProvider(LocalKitWindow provides LocalKitWindow.current.copy(finePointer = false)) { inner() }
    }

    private val hover: ComposeUiTest.() -> Unit = { onNodeWithTag("ground").performMouseInput { moveTo(center) } }
    private val focus: ComposeUiTest.() -> Unit = { onRoot().performKeyInput { pressKey(Key.Tab) } }
    private val tap: ComposeUiTest.() -> Unit = { onNodeWithTag("ground").performTouchInput { click() } }
    private val longPress: ComposeUiTest.() -> Unit = {
        onNodeWithTag("ground").performTouchInput { down(center) }
        mainClock.advanceTimeBy(LONG_PRESS_MS)
    }

    @Test fun states() {
        FlutterGoldenGate.check("kit_icon_button_default", content = scene())
        FlutterGoldenGate.check("kit_icon_button_selected_on", content = scene(KitIcons.wrapText, "Wrap lines", selected = true))
        FlutterGoldenGate.check("kit_icon_button_selected_off", content = scene(KitIcons.wrapText, "Wrap lines", selected = false))
        FlutterGoldenGate.check("kit_icon_button_destructive", content = scene(KitIcons.delete, "Delete", destructive = true))
        FlutterGoldenGate.check("kit_icon_button_disabled", frame = FlutterGoldenGate.Frame.Screen, content = pushed(scene(disabled = true)))
        FlutterGoldenGate.check("kit_icon_button_working", frame = FlutterGoldenGate.Frame.Screen, content = pushed(scene(working = true)))
    }

    @Test fun interactions() {
        FlutterGoldenGate.check("kit_icon_button_hover", frame = FlutterGoldenGate.Frame.Screen, act = hover, content = pushed(scene()))
        FlutterGoldenGate.check("kit_icon_button_focused", frame = FlutterGoldenGate.Frame.Screen, act = focus, content = pushed(scene()))
        FlutterGoldenGate.check("kit_icon_button_copied", frame = FlutterGoldenGate.Frame.Screen, act = tap, content = pushed(scene(copy = true)))
    }

    @Test fun default_sizes() {
        for (s in listOf("_360x800", "_800x1280", "_1280x800", "_1600x1000", "_915x412")) {
            FlutterGoldenGate.check("kit_icon_button_default$s", content = scene())
        }
    }

    @Test fun scaled_wide() {
        FlutterGoldenGate.check(
            "kit_icon_button_default_text2_1280x800", frame = FlutterGoldenGate.Frame.Screen, act = hover,
            content = pushed(scene(shortcut = "Ctrl+R")),
        )
        FlutterGoldenGate.check(
            "kit_icon_button_default_ar_1280x800", frame = FlutterGoldenGate.Frame.Screen, act = hover,
            content = pushed(scene(shortcut = "Ctrl+R")),
        )
    }

    @Test fun scaled_narrow() {
        FlutterGoldenGate.check(
            "kit_icon_button_default_text2", frame = FlutterGoldenGate.Frame.Screen, act = longPress, wrap = touchWindow,
            content = pushed(scene(tooltip = "Retry the connection to this server", shortcut = "Ctrl+R")),
        )
        FlutterGoldenGate.check("kit_icon_button_default_ar", content = scene(shortcut = "Ctrl+R"))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_icon_button", caps = mapOf("KitIconButton" to "glyph")) {
            KitIconButton(KitIcons.retry, "Retry connection", onClick = {})
        }
    }

    private companion object {
        const val LONG_PRESS_MS = 550L // NV: Flutter's kLongPressTimeout (500 ms) plus the golden's 50 ms hold, kit_icon_button_golden_test.dart:176
    }
}
