package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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

    private fun measure(appearance: KitAppearance): Pair<List<FrameCost>, List<FrameCost>> {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = FakeChatWorld()
        world.transcript.value = stateOf(*(1..40).flatMap { turn(it, "Question $it: why does the checkout test fail?", answer) }.toTypedArray())
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        lateinit var out: Pair<List<FrameCost>, List<FrameCost>>
        try {
            runDesktopComposeUiTest(W, H) {
                mainClock.autoAdvance = false
                val meter = FrameMeter.attach(this)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(appearance = appearance, platform = DesktopKitPlatform(), localeTag = "en") {
                            KitStandaloneRedaction {
                                val state by states.collectAsState()
                                ChatScreen(state, p, host)
                            }
                        }
                    }
                }
                meter.settle()
                val still = meter.frames(120)
                // The transcript rests at its newest line: a finger drags it down to read back.
                val drag = meter.drag(Offset(W / 2f, H * 0.3f), 40, 24f)
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
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
    }
}
