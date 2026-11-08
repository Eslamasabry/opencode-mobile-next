package io.github.eslamasabry.oc.ui.kit.rb.o05

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.SecretFieldPresence
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_field_golden_test.dart (28 goldens): the field in the page's 16 dp side gutter
 * (`_scene`), every state at 412x915, the default at 1280x800 and at text 2.0 on both sizes.
 *
 * Gap kept visible, not hidden: `checking_slow` renders the checking field, as native KitField has no slow-check
 * escalation (`checkingSince` + `onSlow` "Skip the check") yet.
 */
class KitFieldGateTest {
    @Composable
    private fun Scene(content: @Composable () -> Unit) {
        // A secret field needs the app's redactor sink (the app provides it); the gate's sink keeps nothing.
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            Box(Modifier.padding(horizontal = 16.dp)) { content() } // F: test/goldens/kit/kit_field_golden_test.dart:22-23
        }
    }

    private val default: @Composable () -> Unit = {
        Scene { KitField(rememberTextFieldState(), "Project name", placeholder = "my-app", helper = "Letters, digits and dashes.") }
    }

    private val focused: @Composable () -> Unit = {
        Scene {
            val focus = remember { FocusRequester() }
            KitField(rememberTextFieldState(), "Project name", placeholder = "my-app", helper = "Letters, digits and dashes.", focusRequester = focus)
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }

    private val filled: @Composable () -> Unit = {
        Scene {
            KitField(
                rememberTextFieldState("https://studio.local:4096"),
                "Server address",
                kind = KitFieldKind.Url,
                helper = "The address the server printed when it started.",
            )
        }
    }

    private val error: @Composable () -> Unit = {
        Scene { KitField(rememberTextFieldState("99999"), "Port", kind = KitFieldKind.Number, error = "Enter a port between 1 and 65535.") }
    }

    private val disabled: @Composable () -> Unit = {
        Scene {
            KitField(
                rememberTextFieldState("feature/login"),
                "Branch",
                kind = KitFieldKind.Mono,
                enabled = KitEnabled.Disabled("The branch is set when the workspace is created."),
            )
        }
    }

    private val checking: @Composable () -> Unit = {
        Scene { KitField(rememberTextFieldState("https://studio.local:4096"), "Server address", kind = KitFieldKind.Url, checking = true) }
    }

    private val counter: @Composable () -> Unit = {
        Scene { KitField(rememberTextFieldState("Fix the login redirect on the tablet sign-in"), "Conversation title", maxLength = 50) }
    }

    /** `_TypedSecret`: a secret is never prefilled, so the value is typed in after the first frame. */
    private val secretMasked: @Composable () -> Unit = {
        Scene {
            val state = rememberTextFieldState()
            KitField(state, "API key", kind = KitFieldKind.Secret, helper = "Stored on this phone only.", secretPresence = SecretFieldPresence.Editing)
            LaunchedEffect(Unit) { state.setTextAndPlaceCursorAtEnd("sk-gallery-0123456789") }
        }
    }

    private val secretSaved: @Composable () -> Unit = {
        Scene { KitField(rememberTextFieldState(), "API key", kind = KitFieldKind.Secret, secretPresence = SecretFieldPresence.Saved, onReplaceSecret = {}) }
    }

    private val multiline: @Composable () -> Unit = {
        Scene {
            KitField(
                rememberTextFieldState(
                    "Move the settings screen to the new kit.\nKeep every setting where it is.\nAdd the missing labels.\n" +
                        "Check it at text 2.0.\nRecord screenshots.",
                ),
                "Objective",
                kind = KitFieldKind.Multiline,
                helper = "Ctrl+Enter starts the run.",
            )
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) = FlutterGoldenGate.check(name, content = content)

    @Test fun kit_field_default() { shot("kit_field_default", default) }
    @Test fun kit_field_default_1280x800() { shot("kit_field_default_1280x800", default) }
    @Test fun kit_field_default_text2() { shot("kit_field_default_text2", default) }
    @Test fun kit_field_default_text2_1280x800() { shot("kit_field_default_text2_1280x800", default) }
    @Test fun kit_field_focused() { shot("kit_field_focused", focused) }
    @Test fun kit_field_filled() { shot("kit_field_filled", filled) }
    @Test fun kit_field_error() { shot("kit_field_error", error) }
    @Test fun kit_field_disabled() { shot("kit_field_disabled", disabled) }
    @Test fun kit_field_checking() { shot("kit_field_checking", checking) }
    @Test fun kit_field_checking_slow() { shot("kit_field_checking_slow", checking) }
    @Test fun kit_field_counter() { shot("kit_field_counter", counter) }
    @Test fun kit_field_secret_masked() { shot("kit_field_secret_masked", secretMasked) }
    @Test fun kit_field_secret_saved() { shot("kit_field_secret_saved", secretSaved) }
    @Test fun kit_field_multiline() { shot("kit_field_multiline", multiline) }
}
