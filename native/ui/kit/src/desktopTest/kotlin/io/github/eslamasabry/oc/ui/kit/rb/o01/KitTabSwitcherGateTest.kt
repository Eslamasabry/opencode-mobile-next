package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabSwitcher
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_tab_switcher_golden_test.dart: the strip over a page stub. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitTabSwitcherGateTest {
    private val columns = listOf("Backlog", "Ready", "Working", "Review", "Done")
    private val eight = columns + listOf("Blocked", "Archived", "Waiting on review")
    private val counts = listOf(4, 2, 4, 2, 12)
    private val eightCounts = listOf(4, 2, 4, 2, 12, 1, 38, 3)

    private fun tabs(state: String): List<KitTab> = when (state) {
        "default" -> columns.mapIndexed { i, w -> KitTab(label = w, count = counts[i]) }
        "counts_loading" -> columns.map { KitTab(label = it) }
        "needs_you" -> columns.mapIndexed { i, w -> KitTab(label = w, count = counts[i], needsYou = if (i == 2) 1 else 0) }
        else -> eight.mapIndexed { i, w -> KitTab(label = w, count = eightCounts[i]) }
    }

    @Composable
    private fun PageStub(name: String) {
        Column(Modifier.padding(16.dp)) {
            KitText(name, role = TypeRole.Headline)
            Spacer(Modifier.height(8.dp))
            KitText("Cards in this column keep their place when you switch.", role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }

    private fun scene(state: String): @Composable () -> Unit = {
        val tabs = tabs(state)
        val selected = if (state == "overflowing") tabs.size - 1 else 2
        KitTabSwitcher(
            tabs = tabs, index = selected, onSelected = {}, semanticsLabel = "Columns",
            bodies = tabs.map { tab -> @Composable { PageStub(tab.label) } },
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
    }

    private fun shot(name: String, act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, act = act, content = content)
    }

    @Test fun states() {
        for (size in listOf("", "_1280x800")) for (state in listOf("default", "counts_loading", "needs_you", "overflowing")) shot("kit_tab_switcher_$state$size", content = scene(state))
    }

    @Test fun default_text2() {
        for (size in listOf("", "_1280x800")) shot("kit_tab_switcher_default_text2$size", content = scene("default"))
    }

    @Test fun focused() = shot(
        "kit_tab_switcher_focused",
        act = { onRoot().performKeyInput { pressKey(Key.Tab); pressKey(Key.DirectionRight) } },
        content = scene("default"),
    )

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_tab_switcher", caps = mapOf("KitTabStrip" to "fill", "KitTabSwitcher" to "fill"), content = scene("default"))
    }
}
