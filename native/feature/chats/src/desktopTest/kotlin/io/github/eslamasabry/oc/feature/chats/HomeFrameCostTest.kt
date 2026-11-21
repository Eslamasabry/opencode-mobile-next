package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.gate.FrameCost
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.gate.summary
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toPersistentList

/**
 * c2-performance: what each frame of the Conversations home costs a new person (the default appearance: Solid, dark,
 * Full motion, no live background), at a phone's size and density, with forty conversations: frames with nothing
 * changing, and frames of a finger dragging the list. Counted work, not time ([FrameMeter]).
 */
@OptIn(ExperimentalTestApi::class)
class HomeFrameCostTest {
    private val rows =
        List(40) { i ->
            ConversationRowUi(
                "c$i",
                ConversationRef(ProfileId("p"), SessionId("s$i")),
                RowTitle.Text("Conversation number $i about the checkout flow"),
                listOf("api", "web", "mobile", "infra")[i % 4],
                RowStatus.None,
            )
        }

    private fun measure(
        appearance: KitAppearance,
        platform: io.github.eslamasabry.oc.ui.kit.platform.KitPlatform = DesktopKitPlatform(),
        /** Divides the window and density, for a quicker survey. */
        shrink: Int = 1,
    ): Pair<List<FrameCost>, List<FrameCost>> {
        Locale.setDefault(Locale.forLanguageTag("en"))
        lateinit var out: Pair<List<FrameCost>, List<FrameCost>>
        kitUiTest(W / shrink, H / shrink) {
            mainClock.autoAdvance = false
            val meter = FrameMeter.attach(this)
            val state = ChatsUi(phase = ChatsPhase.List, recent = rows.toPersistentList())
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY / shrink)) {
                    ChatEnvironment {
                        KitTheme(appearance = appearance, platform = platform, localeTag = "en") {
                            KitSheetHost { ChatsScreen(state, RecordingChatsActions()) }
                        }
                    }
                }
            }
            meter.settle()
            println("PERF-HOME open scopes ${meter.scopesSoFar}")
            val still = meter.frames(120 / shrink)
            meter.traceCauses = true
            val drag = meter.drag(Offset(W / 2f / shrink, H * 0.7f / shrink), 40 / shrink, -24f / shrink)
            println("PERF-HOME frames " + drag.joinToString(",") { it.scopes.toString() })
            println("PERF-HOME causes " + meter.causes.entries.sortedByDescending { it.value }.take(15).joinToString(" || ") { "${it.value}x ${it.key}" })
            meter.close()
            out = still to drag
        }
        return out
    }

    @Test
    fun `PERF-HOME-1 at the default look the home composites no page-sized layer, still or dragged`() {
        val (still, drag) = measure(KitAppearance())
        val numbers = still.summary("still") + " | " + drag.summary("drag")
        println("PERF-HOME-1 $numbers")
        assertEquals(0, still.count { it.busy }, "a still home asks for no frames: $numbers")
        // Before 2026-11-15 the page's scroll edges composited the whole scrolling part offscreen (about 2.2 M px here)
        // on every frame it moved; what may remain are small parts (a chip row's fade).
        val most = (still + drag).maxOf { it.offscreenPx }
        assertTrue(most <= W.toLong() * H / 8, "offscreen pixels per frame $most over an eighth of the window: $numbers")
        // While the title and chips slid away under the bar the whole page frame composed again on every frame (23 scopes a
        // frame, 299 over the drag: the frame read the header's position to know whether anything lay under the bar).
        // The one frame where something first lies under the bar composes the frame again (a crossing); no other.
        assertTrue(drag.count { it.scopes > 10 } <= 1, "drag frames recomposing over 10 scopes: ${drag.count { it.scopes > 10 }}: $numbers")
        assertTrue(drag.sumOf { it.scopes } <= 60, "a 40-frame drag recomposed ${drag.sumOf { it.scopes }} scopes: $numbers")
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
    }
}
