package io.github.eslamasabry.oc.ui.kitchat.rb.o21

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCard
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCardMode
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardNode
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** test/goldens/kit/kit_agent_card_golden_test.dart: `_card` in `_pad` (16 dp each side), five states. */
class KitAgentCardGateTest {
    @Composable
    private fun card(mode: KitAgentCardMode, undo: Boolean = false) {
        val asks = mode == KitAgentCardMode.Ask
        KitAgentCard(
            eyebrow = if (asks) "Claude Code asks" else "Claude Code reports",
            title = if (asks) "Which branch should I deploy?" else "The tests finished",
            mode = mode,
            modifier = Modifier.padding(horizontal = 16.dp),
            body = persistentListOf(KitCardNode.KeyValue(persistentListOf("Duration" to "4 min 12 s", "Files changed" to "7"))),
            ask = if (asks) ({ KitButton("Deploy main", {}, role = KitButtonRole.Secondary, fullWidth = true) }) else null,
            receiptLabel = "Sent: main",
            undoLabel = "Undo",
            onUndo = if (undo) ({}) else null,
            expandLabel = "Show the card",
            passedOverLabel = "Not answered",
            unreadableLabel = "The card could not be shown",
            detailsLabel = "Details",
            details = "unknown key \"x\" at nodes[2]",
        )
    }

    private val wrap: @Composable (@Composable () -> Unit) -> Unit =
        { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { FlutterCapture(c) } }

    private fun shot(name: String, mode: KitAgentCardMode, undo: Boolean = false, modes: List<String> = listOf("light", "dark")) =
        FlutterGoldenGate.check(wrap = wrap, name = name, modes = modes) { card(mode, undo) }

    @Test fun kit_agent_card_default() {
        shot("kit_agent_card_default", KitAgentCardMode.Ask)
        shot("kit_agent_card_default_1280x800", KitAgentCardMode.Ask)
    }
    @Test fun kit_agent_card_default_text2() {
        shot("kit_agent_card_default_text2", KitAgentCardMode.Ask, modes = listOf("dark"))
        shot("kit_agent_card_default_text2_1280x800", KitAgentCardMode.Ask, modes = listOf("dark"))
    }
    @Test fun kit_agent_card_default_ar() { shot("kit_agent_card_default_ar", KitAgentCardMode.Ask, modes = listOf("dark")) }
    @Test fun kit_agent_card_report() { shot("kit_agent_card_report", KitAgentCardMode.Report) }
    @Test fun kit_agent_card_answered() { shot("kit_agent_card_answered", KitAgentCardMode.Receipt, undo = true) }
    @Test fun kit_agent_card_disabled() { shot("kit_agent_card_disabled", KitAgentCardMode.PassedOver) }
    @Test fun kit_agent_card_unreadable() { shot("kit_agent_card_unreadable", KitAgentCardMode.Unreadable) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_agent_card",
            caps = mapOf("KitAgentCard" to "reading"),
            wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } },
        ) { card(KitAgentCardMode.Ask) }
    }
}
