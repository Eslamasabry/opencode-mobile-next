package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.gate.FrameCost
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.gate.summary
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted

/**
 * c2-performance: what each frame of a long conversation costs a new person (the default appearance: Solid, dark, Full
 * motion, no live background), at a phone's size and density, with forty finished turns and the real message box:
 * frames with nothing changing, and frames of a finger dragging the transcript. Counted work, not time ([FrameMeter]).
 */
@OptIn(ExperimentalTestApi::class)
class ChatFrameCostTest {
    private val answer =
        "Here is what I changed. The checkout total now settles before the coupon applies, so the test that failed " +
            "one run in five passes every time. I ran it ten times in a row and it passed each time. **Two** files " +
            "changed: `cart.ts` and `coupon.ts`.\n\nNext I would look at the retry around the payment call."

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) {}

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val voice =
        object : KitComposerVoice {
            @Composable
            override fun Mic(field: KitVoiceField, modifier: Modifier) {
                KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
            }

            @Composable override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "checkout",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                    voice = voice,
                )
            }
        }

    private val code =
        "```kotlin\nfun total(cart: Cart, coupon: Coupon?): Money = cart.lines.sumOf { it.price * it.quantity }.let { sum -> coupon?.applyTo(sum) ?: sum }\n" +
            "val settled = checkout.settle(total(cart, coupon)).also { log.info(\"settled \${it.amount} for \${cart.id} with \${coupon?.code}\") }\n```"

    /** Forty finished turns; with [withCode] each reply also carries a code block wider than the screen. */
    private fun rows(withCode: Boolean): Array<io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow> =
        (1..40).flatMap { n ->
            val base = turn(n, "Question $n: why does the checkout test fail?", answer)
            if (!withCode) base
            else
                base.take(2) +
                    io.github.eslamasabry.oc.engine.transcript.state.BlockRow(
                        "b:a$n/c$n:0", "u$n", io.github.eslamasabry.oc.engine.transcript.state.RowKind.Code, "c$n", 0, code,
                        false, false, io.github.eslamasabry.oc.engine.transcript.state.Origin.Loaded,
                    ) + base.drop(2)
        }.toTypedArray()

    private fun measure(
        appearance: KitAppearance,
        withCode: Boolean = false,
        platform: io.github.eslamasabry.oc.ui.kit.platform.KitPlatform = DesktopKitPlatform(),
        /** Divides the window and density, for a quicker survey. */
        shrink: Int = 1,
    ): Pair<List<FrameCost>, List<FrameCost>> {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = FakeChatWorld()
        world.transcript.value = stateOf(*rows(withCode))
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        lateinit var out: Pair<List<FrameCost>, List<FrameCost>>
        try {
            kitUiTest(W / shrink, H / shrink) {
                mainClock.autoAdvance = false
                val meter = FrameMeter.attach(this)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY / shrink)) {
                        KitTheme(appearance = appearance, platform = platform, localeTag = "en") {
                            KitStandaloneRedaction {
                                val state by states.collectAsState()
                                ChatScreen(state, p, host)
                            }
                        }
                    }
                }
                meter.settle()
                println("PERF-CHAT open scopes ${meter.scopesSoFar}")
                val still = meter.frames(120 / shrink)
                // The transcript rests at its newest line: a finger drags it down to read back.
                meter.traceCauses = true
                val drag = meter.drag(Offset(W / 2f / shrink, H * 0.3f / shrink), 40 / shrink, 24f / shrink)
                println("PERF-CHAT frames " + drag.joinToString(",") { it.scopes.toString() })
                println("PERF-CHAT causes " + meter.causes.entries.sortedByDescending { it.value }.take(15).joinToString(" || ") { "${it.value}x ${it.key}" })
                meter.close()
                out = still to drag
            }
        } finally {
            scope.cancel()
        }
        return out
    }

    @Test
    fun `PERF-CHAT-1 at the default look a long conversation composites no page-sized layer, still or dragged`() {
        val (still, drag) = measure(KitAppearance())
        val numbers = still.summary("still") + " | " + drag.summary("drag")
        println("PERF-CHAT-1 $numbers")
        assertEquals(0, still.count { it.busy }, "a still conversation asks for no frames: $numbers")
        // Before 2026-11-15 the frame's scroll edges and the transcript's own end each composited the whole transcript
        // offscreen, one inside the other, on every frame it moved.
        val most = (still + drag).maxOf { it.offscreenPx }
        assertTrue(most <= W.toLong() * H / 8, "offscreen pixels per frame $most over an eighth of the window: $numbers")
        // Leaving the newest line recomposed the whole page (130 scopes in one frame, 34 in the next) for a state that had
        // not changed; a frame of the drag now composes at most the rows coming into view and the jump control.
        assertTrue(drag.maxOf { it.scopes } <= 60, "a drag frame recomposed ${drag.maxOf { it.scopes }} scopes: $numbers")
    }

    @Test
    fun `PERF-CHAT-2 survey of a long conversation with code at the default look`() {
        val (still, drag) = measure(KitAppearance(), withCode = true)
        val numbers = still.summary("still") + " | " + drag.summary("drag")
        println("PERF-CHAT-2 $numbers")
        assertEquals(0, still.count { it.busy }, "a still conversation asks for no frames: $numbers")
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
    }
}
