package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.gate.LayoutGuard
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTab
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTabs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compact width (360 dp wide at text 1.0 and 1.3, and 320 dp wide): the kit parts every screen reuses keep their words
 * whole there. The class rules live in each part's doc; these hold them through the layout guard's compact windows.
 */
class CompactWidthKitTest {
    private val compact = listOf(Window.Compact, Window.CompactLarge, Window.Narrow)

    private fun found(content: @Composable () -> Unit): List<String> =
        LayoutGuard.audit(LayoutGuard.Scene(Frame.Part, content = content), compact)
            .map { "${it.window.name} ${it.rule} ${it.subject}" }

    @Test
    fun `COMPACT-010 a button in a narrow slot wraps its label and never ends it in an ellipsis`() {
        val scene: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp)) {
                // The agents grid's card action: two columns leave it about 150 dp.
                KitButton("New conversation", onClick = {}, Modifier.width(120.dp), maxLines = 1, expand = false)
                KitButton("Delete this conversation for good", onClick = {}, Modifier.width(150.dp), role = KitButtonRole.Secondary)
                KitButton("Open project files", onClick = {}, Modifier.width(110.dp), role = KitButtonRole.Tertiary, maxLines = 1)
            }
        }
        val bad = found(scene).filter { it.contains("ellipsis") || it.contains("clipped") || it.contains("cut-off") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-011 a filter chip line that scrolls says so and a session strip fades at its edge`() {
        val scene: @Composable () -> Unit = {
            Column {
                KitFilterChips {
                    for (label in listOf("This phone", "All projects", "All connections", "Running now")) KitChip(label, onClick = {})
                }
                KitSessionTabs(
                    tabs = listOf("Build", "Review the change", "Tests", "Deploy to the phone").mapIndexed { i, l ->
                        KitSessionTab(label = l, selected = i == 0, ended = false, onClick = {})
                    },
                    label = "Sessions",
                    endedLabel = "ended",
                )
            }
        }
        val bad = found(scene).filter { it.contains("scroll-cue") }
        assertTrue(bad.isEmpty(), bad.toString())
    }
}
