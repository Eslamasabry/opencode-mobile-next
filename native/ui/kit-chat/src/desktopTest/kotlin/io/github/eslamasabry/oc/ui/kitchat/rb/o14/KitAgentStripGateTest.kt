package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * test/goldens/kit/kit_agent_strip_golden_test.dart: a KitTopBar-height header ("Fix the login redirect") with
 * KitAgentStrip under it, six states at 412x915 (a horizontal scroller) and 1280x800 (a chip wrap), light and dark.
 * Frame: kitGalleryPart (Part).
 *
 * Mismatch: native has no KitAgentStrip / agent chip. Each agent is a KitChip whose glyph slot holds the agent's mark
 * (KitTaskMark, or KitNeedsYouMark for needsYou), Action when it opens a conversation, Plain for the lead. Flutter's
 * chip is a 48 dp `surface3` pill (start space2, end space3, mark, space2, the name in the label role, primary tone);
 * KitChip is a chipHeight pill with its own insets, and its Plain kind draws the lead's name in text2 semibold.
 * Mismatch: the strip's arrangement (gutter, space2 gaps, scroller on a phone, KitChipWrap when wide) is built here
 * from layout parts, following `_KitAgentStripState._buildContent`.
 */
class KitAgentStripGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit =
        { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { FlutterCapture(c) } }

    /** Flutter `KitAgent` (kit_agent_strip.dart:21-57). */
    private class Agent(val name: String, val role: String, val state: KitTaskState, val paused: Boolean = false, val opens: Boolean = true)

    private val lead = Agent("mayor", "Lead", KitTaskState.Working, opens = false) // F: test/goldens/kit/kit_agent_strip_golden_test.dart:49-54

    /** Flutter `_states` (kit_agent_strip_golden_test.dart:56-150). */
    private val states: Map<String, List<Agent>> = linkedMapOf(
        "mixed" to listOf(lead, Agent("furiosa", "Worker", KitTaskState.Waiting), Agent("nux", "Reviewer", KitTaskState.Done)),
        "needs_you" to listOf(lead, Agent("furiosa", "Worker", KitTaskState.NeedsYou), Agent("capable", "Worker", KitTaskState.Working)),
        "paused" to listOf(
            Agent("mayor", "Lead", KitTaskState.Working, paused = true, opens = false),
            Agent("furiosa", "Worker", KitTaskState.Waiting, paused = true),
        ),
        "done" to listOf(
            Agent("mayor", "Lead", KitTaskState.Done, opens = false),
            Agent("furiosa", "Worker", KitTaskState.Done),
            Agent("nux", "Reviewer", KitTaskState.Done),
        ),
        "lead_only" to listOf(lead),
        "overflow" to listOf(lead) + listOf("furiosa", "nux", "capable", "toast", "dag", "cheedo", "slit").mapIndexed { i, n ->
            Agent(n, if (i == 6) "Reviewer" else "Worker", KitTaskState.entries[i % KitTaskState.entries.size]) // F: test/goldens/kit/kit_agent_strip_golden_test.dart:145-146
        },
    )

    @Composable
    private fun AgentChip(a: Agent) {
        val mark: @Composable () -> Unit = {
            if (a.state == KitTaskState.NeedsYou) KitNeedsYouMark() else KitTaskMark(a.state, paused = a.paused)
        }
        if (a.opens) KitChip(a.name, kind = KitChipKind.Action, glyph = mark, onClick = {})
        else KitChip(a.name, glyph = mark)
    }

    /** Flutter `_scene` (kit_agent_strip_golden_test.dart:27-47) and `_KitAgentStripState._buildContent` (98-150). */
    @Composable
    private fun Scene(agents: List<Agent>, wide: Boolean) {
        val tokens = LocalKitTokens.current
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(tokens.navHeight.dp).padding(horizontal = tokens.gutter.dp), contentAlignment = Alignment.CenterStart) { // F: test/goldens/kit/kit_agent_strip_golden_test.dart:34-36
                KitText("Fix the login redirect", KitTextRole.Title)
            }
            Box(Modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
                if (wide) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp), contentAlignment = Alignment.CenterStart) { // F: lib/ui/kit/chat/kit_agent_strip.dart:120-121
                        KitChipWrap { agents.forEach { AgentChip(it) } }
                    }
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = tokens.gutter.dp), // F: lib/ui/kit/chat/kit_agent_strip.dart:130-131
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/chat/kit_agent_strip.dart:136
                        verticalAlignment = Alignment.CenterVertically,
                    ) { agents.forEach { AgentChip(it) } }
                }
            }
        }
    }

    /** [stem] at 412x915 (a scroller) and at 1280x800 (wide: a chip wrap). */
    private fun shots(stem: String, state: String) {
        val agents = states.getValue(state)
        FlutterGoldenGate.check(stem, wrap = wrap) { Scene(agents, wide = false) }
        FlutterGoldenGate.check(stem + "_1280x800", wrap = wrap) { Scene(agents, wide = true) }
    }

    @Test fun mixed_needs_you_paused() {
        shots("kit_agent_strip_mixed", "mixed")
        shots("kit_agent_strip_needs_you", "needs_you")
        shots("kit_agent_strip_paused", "paused")
    }

    @Test fun done_lead_only_overflow() {
        shots("kit_agent_strip_done", "done")
        shots("kit_agent_strip_lead_only", "lead_only")
        shots("kit_agent_strip_overflow", "overflow")
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_agent_strip",
            caps = mapOf("KitAgentStrip" to "conversation"),
            wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } },
        ) { Scene(states.getValue("mixed"), wide = false) }
    }
}
