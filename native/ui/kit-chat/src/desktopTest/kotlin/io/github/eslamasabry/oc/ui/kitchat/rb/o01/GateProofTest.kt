package io.github.eslamasabry.oc.ui.kitchat.rb.o01

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import kotlin.test.Test

/**
 * The gate's proof on three ported parts, with the inputs of their Flutter galleries
 * (test/goldens/kit/kit_row_golden_test.dart `_defaultRows`, kit_action_golden_test.dart `_stackDefault`,
 * kit_composer_golden_test.dart `idle_empty` with its `_Backdrop`). Evidence only; nothing fails on a diff yet.
 */
class GateProofTest {
    private val redactor: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit =
        { CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() } }

    /** These three goldens belong to other orders (08, 14); the proof runs only where they have been fetched. */
    private fun proof(name: String, frame: FlutterGoldenGate.Frame = FlutterGoldenGate.Frame.Part, content: @androidx.compose.runtime.Composable () -> Unit) {
        if (FlutterGoldenGate.hasGolden(name)) FlutterGoldenGate.check(name, frame, wrap = redactor, content = content)
    }

    @Test
    fun `kit_row_default`() {
        proof("kit_row_default") {
            KitRowGroup(label = "Conversations") {
                KitRow("Fix the login bug", supporting = "Finished 5h ago", leading = KitRowLeading.Glyph(KitIcons.chat), onClick = {})
                KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
                KitRow("Project settings", leading = KitRowLeading.Glyph(KitIcons.settings), trailing = KitRowTrailing.Chevron, onClick = {})
            }
        }
    }

    @Test
    fun `kit_action_stack_default`() {
        proof("kit_action_stack_default") {
            KitActionBlock(
                primary = KitAction("Update", {}),
                secondary = KitAction("Check now", {}),
                tertiary = listOf(KitAction("Restart", {})),
            )
        }
    }

    @Test
    fun `kit_composer_idle_empty`() {
        proof("kit_composer_idle_empty", FlutterGoldenGate.Frame.Screen) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitText("Can you check why the release build fails on the signing step?", role = TypeRole.Body)
                    KitText(
                        "The release config reads android/key.properties, which is missing here, so validateSigningRelease " +
                            "stops the build before packaging. The Kotlin and Dart sides compile.",
                        role = TypeRole.Body, tone = TextTone.Secondary,
                    )
                    KitText(
                        "I will look at the Gradle task graph next and confirm the debug variant is not affected.",
                        role = TypeRole.Body, tone = TextTone.Secondary,
                    )
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 8.dp + 16.dp).widthIn(max = 720.dp).fillMaxWidth()) {
                    KitComposerBox(
                        running = false,
                        field = { KitField(rememberTextFieldState(), "Ask OpenCode…", kind = KitFieldKind.Multiline, multilineLines = 1..6, labelShown = false, framed = false) },
                        leading = { KitComposerAdd("Add", {}) },
                        trailing = { KitComposerSend("Send", {}) },
                    )
                }
            }
        }
    }
}
