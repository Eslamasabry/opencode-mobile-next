package io.github.eslamasabry.opencode_mobile.proof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitGlassSource
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.forward
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotion.pop
import io.github.eslamasabry.oc.ui.shell.nav.ShellMotionLevel
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Smoothness proof (benchmark build type only; docs/native/qa/smoothness-2026-11-09): one kit page in a chosen look,
 * scrolled by itself so a Macrobenchmark measures the same frames on every build. `look`: `solid`, `glass` (Glass, no
 * background), `glass-aurora` (Glass over the animated aurora shader) or `glass-drift` (Glass over the painted aurora).
 * `scene`: `list` (a long list of Glass rows under a liquid bar, chips and a liquid +), `chat` (a long transcript of
 * text under a liquid bar and a liquid composer), `menu` (the chat with a reply's menu opened and closed eight times) or
 * `push` (a second page pushed over the chat and popped again, eight times, with the shell's fade-forwards). The
 * benchmark taps Start and waits for Done.
 */
/** Scrolls by [total] px over [ms], one step per display frame (frame-driven: no animation scale applies). */
private suspend fun LazyListState.glide(total: Float, ms: Long) {
    val start = withFrameNanos { it }
    var moved = 0f
    while (true) {
        val now = withFrameNanos { it }
        val t = ((now - start) / 1_000_000f / ms).coerceAtMost(1f)
        scrollBy(total * t - moved)
        moved = total * t
        if (t >= 1f) break
    }
}

class GlassProofActivity : ComponentActivity() {
    private var started by mutableStateOf(false)
    private var done by mutableStateOf(false)
    private var hold = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val look = intent.getStringExtra("look") ?: "glass-aurora"
        val scene = intent.getStringExtra("scene") ?: "list"
        // `hold`: the menu scene opens the menu once and keeps it open (for a still screenshot of the look).
        hold = intent.getBooleanExtra("hold", false)
        val appearance =
            when (look) {
                "solid" -> KitAppearance()
                "glass" -> KitAppearance(style = Style.Glass)
                "glass-drift" -> KitAppearance(style = Style.Glass, liveBackground = true, liveStyle = "auroraImage")
                else -> KitAppearance(style = Style.Glass, liveBackground = true, liveStyle = "aurora")
            }
        // Test Lab devices run with animations off; that would hold the kit's motion (and liquid glass) and end every
        // animation at once, so the page sees the system's animator scale as 1 and scrolls by frames, not animations.
        val android = AndroidKitPlatform(window.decorView)
        val platform =
            object : KitPlatform by android {
                override val signals: StateFlow<KitSystemSignals> =
                    MutableStateFlow(android.signals.value.copy(animatorScale = 1f, powerSave = false, severeHeat = false))
            }
        setContent {
            appGraph.kitChatEnvironment.Provide {
                CompositionLocalProvider(LocalKitPlatform provides platform) {
                    KitTheme(appearance) { if (scene == "push") Pushes() else Page(scene) }
                }
            }
        }
    }

    /** Two pages, each a framed KitScreen with its own ground, pushed and popped as the shell's NavDisplay does it. */
    @Composable
    private fun Pushes() {
        var top by remember { mutableStateOf(false) }
        LaunchedEffect(started) {
            if (!started) return@LaunchedEffect
            repeat(8) {
                top = true
                delay(900)
                top = false
                delay(700)
            }
            done = true
        }
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                if (targetState) forward(RouteMotion.SharedAxisX, ShellMotionLevel.Full)
                else pop(RouteMotion.SharedAxisX, ShellMotionLevel.Full)
            },
            label = "proof.push",
        ) { pushed -> if (pushed) Page("list", showsStart = false, runs = false) else Page("chat", runs = false) }
    }

    @Composable
    private fun Page(scene: String, showsStart: Boolean = true, runs: Boolean = true) {
        val list = rememberLazyListState()
        val source = rememberKitFrostState()
        var menu by remember { mutableStateOf(false) }
        LaunchedEffect(started) {
            if (!started || !runs) return@LaunchedEffect
            if (scene == "menu" && hold) {
                menu = true
                delay(1_500)
                done = true
                return@LaunchedEffect
            }
            if (scene == "menu") {
                // The owner's path (2026-11-09): a reply's ⋯ menu opening and closing over the chat, eight times.
                repeat(8) {
                    menu = true
                    delay(900)
                    menu = false
                    delay(700)
                }
                done = true
                return@LaunchedEffect
            }
            // Down and back up twice at a steady reading-scroll speed, then four quick flick-speed passes.
            repeat(2) {
                list.glide(9_000f, 3_000)
                list.glide(-9_000f, 3_000)
            }
            repeat(4) {
                list.glide(4_000f, 450)
                list.glide(-4_000f, 450)
            }
            done = true
        }
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = if (scene != "list") source else null,
            topBar = { KitTopBar(if (done) "Done" else if (started) "Scrolling" else "Ready") },
            bottom =
                if (scene != "list") {
                    {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row { KitChip("Ask first", icon = KitIcons.add); KitChip("build · Model") }
                            val face = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, RoundedCornerShape(28.dp))
                            Box(Modifier.fillMaxWidth().height(64.dp).then(face ?: Modifier)) {
                                KitText("Ask OpenCode…", KitTextRole.Supporting, Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
                            }
                        }
                    }
                } else null,
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().then(if (scene != "list") Modifier.kitFrostSource(source) else Modifier.kitGlassSource(source)),
                ) {
                    item(key = "start", contentType = "start") {
                        Row(Modifier.padding(16.dp)) {
                            if (showsStart) KitButton(label = "Start", onClick = { started = true })
                            if (scene == "menu" && hold) {
                menu = true
                delay(1_500)
                done = true
                return@LaunchedEffect
            }
            if (scene == "menu") {
                                KitMenu(
                                    expanded = menu,
                                    items = listOf(KitMenuItem("Copy", {}), KitMenuItem("Share", {}), KitMenuItem("Fork from here", {}), KitMenuItem("Retry", {})),
                                    onDismissRequest = { menu = false },
                                ) { KitText("More", KitTextRole.Supporting, Modifier.padding(16.dp)) }
                            }
                        }
                    }
                    items(200, key = { it }, contentType = { "row" }) { i ->
                        if (scene != "list") {
                            KitText(
                                if (i % 2 == 0) "Question $i: what does the parser do with an empty line in a fenced block?"
                                else "Answer $i: it keeps the line inside the block and closes the fence only on a line of three backticks.",
                                KitTextRole.Body,
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        } else {
                            KitSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Column {
                                    KitText("Refactor step $i: tidy the parser module", KitTextRole.Body)
                                    KitText("app · My computer · 1h ago", KitTextRole.Supporting)
                                }
                            }
                        }
                    }
                }
                if (scene != "chat") KitFab("New", KitIcons.add, {}, Modifier.align(Alignment.BottomEnd).padding(16.dp), backdrop = source)
            }
        }
    }
}
