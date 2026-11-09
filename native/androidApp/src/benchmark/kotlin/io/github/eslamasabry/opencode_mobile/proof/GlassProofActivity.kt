package io.github.eslamasabry.opencode_mobile.proof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/**
 * Smoothness proof (benchmark build type only; docs/native/qa/smoothness-2026-11-09): one kit page in a chosen look,
 * scrolled by itself so a Macrobenchmark measures the same frames on every build. `look`: `solid`, `glass` (Glass, no
 * background), `glass-aurora` (Glass over the animated aurora shader) or `glass-drift` (Glass over the painted aurora).
 * `scene`: `list` (a long list of Glass rows under a liquid bar, chips and a liquid +) or `chat` (a long transcript of
 * text under a liquid bar and a liquid composer). The benchmark taps Start and waits for Done.
 */
class GlassProofActivity : ComponentActivity() {
    private var started by mutableStateOf(false)
    private var done by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val look = intent.getStringExtra("look") ?: "glass-aurora"
        val scene = intent.getStringExtra("scene") ?: "list"
        val appearance =
            when (look) {
                "solid" -> KitAppearance()
                "glass" -> KitAppearance(style = Style.Glass)
                "glass-drift" -> KitAppearance(style = Style.Glass, liveBackground = true, liveStyle = "auroraImage")
                else -> KitAppearance(style = Style.Glass, liveBackground = true, liveStyle = "aurora")
            }
        val platform = AndroidKitPlatform(window.decorView)
        setContent {
            appGraph.kitChatEnvironment.Provide {
                CompositionLocalProvider(LocalKitPlatform provides platform) {
                    KitTheme(appearance) { Page(scene) }
                }
            }
        }
    }

    @Composable
    private fun Page(scene: String) {
        val list = rememberLazyListState()
        val source = rememberKitFrostState()
        LaunchedEffect(started) {
            if (!started) return@LaunchedEffect
            // Down and back up twice at a steady reading-scroll speed, then once more as quick flings.
            repeat(2) {
                list.animateScrollBy(9_000f, tween(3_000, easing = LinearEasing))
                list.animateScrollBy(-9_000f, tween(3_000, easing = LinearEasing))
            }
            repeat(4) {
                list.animateScrollBy(4_000f, tween(450))
                list.animateScrollBy(-4_000f, tween(450))
            }
            done = true
        }
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = if (scene == "chat") source else null,
            topBar = { KitTopBar(if (done) "Done" else if (started) "Scrolling" else "Ready") },
            bottom =
                if (scene == "chat") {
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
                    modifier = Modifier.fillMaxSize().then(if (scene == "chat") Modifier.kitFrostSource(source) else Modifier.kitGlassSource(source)),
                ) {
                    item(key = "start", contentType = "start") {
                        Row(Modifier.padding(16.dp)) { KitButton(label = "Start", onClick = { started = true }) }
                    }
                    items(200, key = { it }, contentType = { "row" }) { i ->
                        if (scene == "chat") {
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
