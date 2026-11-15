package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStop
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopAfterStep
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFoldLine
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepList
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B-A11Y-CHAT (UX-22, UX-36, UX-37): the Android Accessibility Test Framework over the chat's own kit parts, at 360 dp,
 * in eight settings each: light and dark, English and Arabic (right to left), the person's system text size 1.0, 1.3 and
 * 2.0. The scenes: the message box at rest (attach, model and mode chips, the field, dictate and send neighbours), the
 * message box while a reply runs (Stop and its hold action), the approval card in its standard look and as the
 * conversation's question, and a turn's step list (a folded-away failure, a failed step, a running one).
 *
 * ATF fails a touch target under 48 dp, a missing label, a duplicate clickable bounds, a low contrast and a clipped text.
 * Text size 2.0 does not ask the frame to grow past the owner's 1.3x cap (`TextScalePolicy.FRAME_CAP`); it proves the
 * capped words still sit whole in their targets while reading text scales fully.
 * Run: `:ui:kit-chat:testAndroidHostTest --tests '*KitChatAccessibilityChecksTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class KitChatAccessibilityChecksTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String, val fontScale: Float) {
        override fun toString(): String =
            (if (dark) "dark" else "light") + "-" + localeTag + "-text" + (fontScale * 100).toInt()
    }

    @get:Rule val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            ChatTestTheme(
                dark = variant.dark,
                localeTag = variant.localeTag,
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                fontScale = variant.fontScale,
            ) {
                Box(Modifier.width(360.dp)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Composable
    private fun MessageField() {
        KitField(
            state = rememberTextFieldState(""),
            label = "Message",
            modifier = Modifier.width(360.dp),
            kind = KitFieldKind.Multiline,
            placeholder = "Ask OpenCode…",
            enabled = KitEnabled.Enabled,
            labelShown = false,
            framed = false,
            composer = true,
            multilineLines = 1..8,
        )
    }

    @Test fun `B-A11Y-CHAT message box at rest passes ATF`() =
        check {
            KitComposerBox(
                running = false,
                hasLeading = true,
                field = { MessageField() },
                leading = { KitComposerAdd("Attach and more", {}) },
                chips = {
                    KitComposerChip("DeepSeek V4.1 Flash", onClick = {})
                    KitComposerChip("Ask first", onClick = {})
                },
                trailing = {
                    KitComposerMic("Talk instead of typing", {})
                    KitComposerSend("Send", {})
                },
            )
        }

    @Test fun `B-A11Y-CHAT message box while a reply runs passes ATF`() =
        check {
            Column {
                KitComposerStop("Stop", "Stop Claude Code", onStop = {}, stopAfterStep = KitStopAfterStep("Stop after this step") {})
                KitComposerBox(
                    running = true,
                    hasLeading = true,
                    field = { MessageField() },
                    leading = { KitComposerAdd("Attach and more", {}) },
                    chips = { KitComposerChip("Ask first", onClick = {}) },
                    trailing = { KitComposerSend("Add to this turn", {}) },
                )
            }
        }

    @Test fun `B-A11Y-CHAT approval card passes ATF`() =
        check {
            KitPermissionCard(
                title = "Create a1.txt?",
                summary = "The agent waits until you answer. Nothing is lost.",
                subject = "a1.txt",
                allowOnceLabel = "Allow once",
                alwaysLabel = "Always allow here",
                denyLabel = "Deny",
                onAllowOnce = {},
                onAlways = {},
                onDeny = {},
                onDetails = {},
            )
        }

    @Test fun `B-A11Y-CHAT approval card as the conversation's question passes ATF`() =
        check {
            KitPermissionCard(
                title = "Run this command?",
                summary = null,
                subject = null,
                targets = listOf(KitRequestTarget("flutter test --concurrency=1")),
                allowOnceLabel = "Allow once",
                alwaysLabel = "Always allow flutter",
                denyLabel = "Reject",
                onAllowOnce = {},
                onAlways = {},
                onDeny = {},
                look = KitRequestLook.Question,
                waitingAge = "3 s",
                onDetails = {},
            )
        }

    private fun step(id: String, status: KitToolStatus, statusLabel: String, title: String = "Read app.js") =
        KitToolView(id, KitToolKind.Read, title, "app.js", null, status, "0.2s", null, null, statusLabel)

    @Test fun `B-A11Y-CHAT step list passes ATF`() =
        check {
            KitStepList(
                fold = KitStepFoldLine("Show 2 earlier steps", "1 declined · 1 failed", open = false, onToggle = {}),
                earlier = { KitToolRow(step("e", KitToolStatus.Failed, "Failed"), false, {}) },
            ) {
                KitToolRow(step("a", KitToolStatus.Done, "Done"), opened = false, onToggle = {})
                KitToolRow(step("b", KitToolStatus.Failed, "Failed", "Ran a command"), opened = false, onToggle = {})
                KitToolRow(step("c", KitToolStatus.Running, "Working", "Edit main.kt"), opened = false, onToggle = {})
            }
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> =
            listOf(
                Variant(dark = false, localeTag = "en", fontScale = 1f),
                Variant(dark = true, localeTag = "en", fontScale = 1f),
                Variant(dark = false, localeTag = "ar", fontScale = 1f),
                Variant(dark = true, localeTag = "ar", fontScale = 1f),
                Variant(dark = false, localeTag = "en", fontScale = 1.3f),
                Variant(dark = false, localeTag = "ar", fontScale = 1.3f),
                Variant(dark = true, localeTag = "en", fontScale = 2f),
                Variant(dark = false, localeTag = "ar", fontScale = 2f),
            )
    }
}
