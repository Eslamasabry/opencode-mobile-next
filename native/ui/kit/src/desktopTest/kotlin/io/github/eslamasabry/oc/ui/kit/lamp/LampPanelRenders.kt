@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the edge light's open list for the coordinator to judge, at the phone's 2.625 px/dp (412 × 915 dp). Not a
 * test: it runs only when `LAMP_RENDER_OUT` names a folder, and writes there:
 * - `panel-{style}-{dark|light}.png`: the list (Recent, Working with the nested parallel block, Needs you) attached to
 *   the light, over reply text, in the five styles; `panel-solid-dark-rtl.png` in Arabic;
 * - `nested-parallel-{style}.png`: a sub-agent waiting for the person inside its parallel block (dark);
 * - `sequence-{style}.png`: one agent's steps as a sequence (dark);
 * - `edit-{style}.png`: Edit mode with pins, handles and the edge light strip (dark);
 * - `stop-{style}.png`: a stop held with its draining receipt, 1 s in (dark);
 * - `place-solid-dark.png`: the six-spot picker.
 */
class LampPanelRenders {
    private fun ComposeUiTest.shoot(out: File, name: String) {
        ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
    }

    @Composable
    private fun Page(content: @Composable BoxScope.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                repeat(PAGE_LINES) { KitText(PAGE_WORDS, role = TypeRole.Body, maxLines = 1) }
            }
            content()
        }
    }

    private fun ComposeUiTest.scene(style: Style, dark: Boolean, rtl: Boolean, model: KitLampPanelModel) {
        k2Host(style, dark, rtl, MotionLevel.Full, density = DENSITY, widthDp = W, heightDp = H) {
            Page {
                KitLampPanelHost(
                    open = true,
                    anchorInWindow = { Rect((W - 24) * DENSITY, (H - 300) * DENSITY, W * DENSITY, (H - 244) * DENSITY) },
                    side = KitLampSide.End,
                    onClose = {},
                    modifier = Modifier.padding(top = 120.dp, bottom = 24.dp),
                ) {
                    KitLampPanel(model, K2.panel, KitLampPanelActions())
                }
                KitLamp(KitLampFace(count = 1, open = true), "Close conversations", {}, Modifier.align(Alignment.BottomEnd).padding(bottom = 244.dp))
            }
        }
    }

    @Test
    fun `lamp panel renders`() {
        val out = System.getenv("LAMP_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (style in K2.styles) for (dark in listOf(true, false)) {
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                scene(style, dark, false, K2.model())
                waitForIdle()
                shoot(out, "panel-${style.name.lowercase()}-${if (dark) "dark" else "light"}.png")
            }
        }
        runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
            scene(Style.Solid, true, true, K2.model())
            waitForIdle()
            shoot(out, "panel-solid-dark-rtl.png")
        }
        for (style in K2.styles) {
            val waiting =
                KitLampPanelModel(
                    listOf(
                        KitLampSection(
                            KitLampGroup.NeedsYou, "Needs you",
                            listOf(
                                KitLampRow(
                                    "deploy", "Deploy preview to staging", KitLampRowMark.NeedsYou, state = "1 waiting for you · 2 still working",
                                    stateTone = KitLampTone.Attention, newest = "Building the preview image", stoppable = true,
                                    steps = KitStepTreeKind.Parallel, children = K2.deployChildren(live = true, review = KitTreeStepState.NeedsYou),
                                ),
                            ),
                        ),
                    ),
                    all = 7,
                    youAreIn = "You are in “name two animals”",
                )
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                scene(style, true, false, waiting)
                waitForIdle()
                shoot(out, "nested-parallel-${style.name.lowercase()}.png")
            }
            val sequence =
                KitLampPanelModel(
                    listOf(
                        KitLampSection(
                            KitLampGroup.Working, "Working",
                            listOf(
                                KitLampRow(
                                    "fix", "Fix the checkout crash", KitLampRowMark.Working, progress = 0.4f, state = "2 of 5 done",
                                    newest = "Opening the pull request", stoppable = true, steps = KitStepTreeKind.Sequence, children = K2.checkoutSteps,
                                ),
                            ),
                        ),
                    ),
                    all = 8,
                    youAreIn = "You are in “name two animals”",
                )
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                scene(style, true, false, sequence)
                waitForIdle()
                shoot(out, "sequence-${style.name.lowercase()}.png")
            }
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                scene(style, true, false, K2.model(editing = true, pinned = true))
                waitForIdle()
                shoot(out, "edit-${style.name.lowercase()}.png")
            }
            val stopping =
                K2.model().let { m ->
                    m.copy(sections = m.sections.map { s -> s.copy(rows = s.rows.map { if (it.key == "proof") it.copy(stopping = KitHeld("stop", "Stopped “create a file proof.txt with one line”", allowed = false)) else it }) })
                }
            runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
                mainClock.autoAdvance = false
                scene(style, true, false, stopping)
                mainClock.advanceTimeBy(1_000)
                shoot(out, "stop-${style.name.lowercase()}.png")
            }
        }
        runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
            k2Host(Style.Solid, true, false, density = DENSITY, widthDp = W, heightDp = H) {
                Page { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { KitLampPlacePicker(KitLampPlace(), K2.place, {}, {}) } }
            }
            waitForIdle()
            shoot(out, "place-solid-dark.png")
        }
    }

    private companion object {
        const val W = 412
        const val H = 915
        const val DENSITY = 2.625f
        const val PAGE_LINES = 30
        const val PAGE_WORDS = "The koala sleeps up to twenty hours a day, and the otter holds hands while it naps."
    }
}
