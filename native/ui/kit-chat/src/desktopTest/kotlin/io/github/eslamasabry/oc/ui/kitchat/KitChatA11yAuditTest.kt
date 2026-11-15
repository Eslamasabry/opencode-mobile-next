@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.A11yAudit
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-A11Y-CHAT (UX-22, UX-36, UX-37): the accessibility audit ([A11yAudit], in the gate module) over the chat's own kit
 * parts, at 360 dp, in eight settings each: light and dark, English and Arabic (right to left), and the person's system
 * text size 1.0, 1.3 and 2.0. The scenes: the message box at rest (attach, model and mode chips, the field, dictate and
 * send neighbours), the message box while a reply runs (Stop with its hold action), the approval card in its standard
 * look and as the conversation's question, and a turn's step list (a folded-away failure, a failed step, a running one).
 *
 * Text size 2.0 does not ask the frame to grow past the owner's 1.3x cap (`TextScalePolicy.FRAME_CAP`); it proves the
 * capped words still sit whole in their targets while reading text scales fully.
 */
class KitChatA11yAuditTest {
    private data class Variant(val dark: Boolean, val localeTag: String, val fontScale: Float) {
        override fun toString(): String = (if (dark) "dark" else "light") + "-" + localeTag + "-text" + (fontScale * 100).toInt()
    }

    private val variants =
        listOf(
            Variant(false, "en", 1f),
            Variant(true, "en", 1f),
            Variant(false, "ar", 1f),
            Variant(true, "ar", 1f),
            Variant(false, "en", 1.3f),
            Variant(false, "ar", 1.3f),
            Variant(true, "en", 2f),
            Variant(false, "ar", 2f),
        )

    private fun check(scene: String, content: @Composable () -> Unit) {
        val found = ArrayList<A11yAudit.Offender>()
        for (v in variants) {
            runDesktopComposeUiTest(412, 1400) {
                setContent {
                    ChatTestTheme(dark = v.dark, localeTag = v.localeTag, fontScale = v.fontScale) {
                        Box(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { content() }
                    }
                }
                found += A11yAudit.run("$scene $v", this, 360f)
            }
        }
        assertEquals("", found.joinToString("\n"), "$scene: ${found.size} accessibility offenders")
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

    @Test fun `B-A11Y-CHAT message box at rest`() =
        check("message box") {
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

    @Test fun `B-A11Y-CHAT message box while a reply runs`() =
        check("message box running") {
            Column {
                KitComposerStop("Stop", "Stop Claude Code", onStop = {}, stopAfterStep = KitStopAfterStep("Stop after this step") {}, afterStepButton = false)
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

    @Test fun `B-A11Y-CHAT approval card`() =
        check("approval card") {
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

    @Test fun `B-A11Y-CHAT approval card as the conversation's question`() =
        check("question card") {
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

    @Test fun `B-A11Y-CHAT step list`() =
        check("step list") {
            KitStepList(
                fold = KitStepFoldLine("Show 2 earlier steps", "1 declined · 1 failed", open = false, onToggle = {}),
                earlier = { KitToolRow(step("e", KitToolStatus.Failed, "Failed"), false, {}) },
            ) {
                KitToolRow(step("a", KitToolStatus.Done, "Done"), opened = false, onToggle = {})
                KitToolRow(step("b", KitToolStatus.Failed, "Failed", "Ran a command"), opened = false, onToggle = {})
                KitToolRow(step("c", KitToolStatus.Running, "Working", "Edit main.kt"), opened = false, onToggle = {})
            }
        }
}
