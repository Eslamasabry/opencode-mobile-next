package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Background CPU 2026-11-14: with the app behind the launcher it used about 40 % of a core, because a `delay` loop in a
 * composition's coroutine (a `LaunchedEffect`, a modifier node's `coroutineScope`) keeps waking after the window stops
 * drawing: every Glass pane's words' ink sampler woke ten times a second. A UI loop that repeats work for pixels waits
 * through `KitVisibility.tick` (ui/kit `platform/KitVisibility.kt`), which parks while the app is hidden.
 *
 * This gate counts, in every production file under `ui/` and `feature/`, the `while (…)` loops whose body waits with a
 * bare `delay(` (within the next [BODY] code lines). The counts below are the loops that were there on 2026-11-14 (most
 * are bounded: a countdown, an 8 s escalation, a view model's poll); a file may only go down. A new UI loop waits
 * through the visibility tick, or, when it must run while hidden (it is not about pixels), lives in an engine on the
 * `Pacer` (core/common `pace`).
 *
 * It also holds the number of `.collectAsState(` (not lifecycle-aware) calls in UI code to its count of that day: a
 * flow that ticks is collected with `collectAsStateWithLifecycle`, which stops below STARTED.
 */
class PixelTickerGateTest {
    private val root = File(System.getProperty("oc.root"))

    private val loop = Regex("""(?<![\w.])while\s*\(""")
    private val delay = Regex("""(?<!\w)delay\(""")
    private val collect = Regex("""\.collectAsState\(""")

    /** Raw `delay` loops per file on 2026-11-14 (paths from the native root). Lower a number when you remove one. */
    private val baseline =
        mapOf(
            "feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/hub/AgentsPresenter.kt" to 2,
            "feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/remote_terminal/presentation/RemoteTerminalViewModel.kt" to 1,
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/work/ChatWorkModel.kt" to 1,
            "feature/preview/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/preview/ui/DevPreviewScreen.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/fleet/FleetGateSheet.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/FleetAgentsViewModels.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/TeamPresent.kt" to 1,
            "feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/presentation/TeamSetup.kt" to 1,
            "feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/presentation/LocalTerminalViewModel.kt" to 1,
            "feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/presentation/TerminalViewModel.kt" to 1,
            "ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/log/KitLogPanel.kt" to 1,
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitField.kt" to 1,
            "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusLine.kt" to 1,
        )

    /** `.collectAsState(` calls in UI code (ui/, feature/, androidApp/) on 2026-11-14. */
    private val collectBaseline = 37

    private fun code(text: String): List<String> =
        text.lines().mapNotNull { raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else line
        }

    /** The `while` loops in [text] whose next [BODY] code lines wait with a bare `delay(`. */
    fun rawDelayLoops(text: String): Int {
        val lines = code(text)
        return lines.indices.count { i -> loop.containsMatchIn(lines[i]) && lines.subList(i, minOf(lines.size, i + BODY)).any { delay.containsMatchIn(it) } }
    }

    fun collects(text: String): Int = code(text).sumOf { collect.findAll(it).count() }

    @Test
    fun `BG-CPU-GATE no UI file gains a raw delay loop, new ones wait through the visibility tick`() {
        val files = BuildRestGates.production(root, "ui", "feature")
        assertTrue(files.size > 200, "scanned ${files.size} files")
        val over =
            files.mapNotNull { (path, text) ->
                val n = rawDelayLoops(text)
                val allowed = baseline[path] ?: 0
                if (n > allowed) "$path: $n raw delay loops (allowed $allowed)" else null
            }
        assertTrue(
            over.isEmpty(),
            "a UI loop that repeats work for pixels waits with KitVisibility.tick (it parks while the app is hidden):\n" +
                over.joinToString("\n"),
        )
    }

    @Test
    fun `BG-CPU-GATE UI code gains no collectAsState that keeps collecting below STARTED`() {
        val files = BuildRestGates.production(root, "ui", "feature", "androidApp")
        val n = files.values.sumOf { collects(it) }
        assertTrue(n <= collectBaseline, "$n .collectAsState( calls (allowed $collectBaseline): use collectAsStateWithLifecycle")
    }

    @Test
    fun `BG-CPU-GATE planted loops are counted, the visibility tick and comments are not`() {
        val planted =
            """
            LaunchedEffect(Unit) {
                while (true) {
                    now = clock()
                    delay(1_000)
                }
            }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = clock() } }
            LaunchedEffect(Unit) {
                while (true) {
                    visibility.tick(1_000)
                    now = clock()
                }
            }
            // while (true) { delay(1) }
            val s by flow.collectAsState()
            val t by flow.collectAsStateWithLifecycle()
            """.trimIndent()
        assertEquals(2, rawDelayLoops(planted))
        assertEquals(1, collects(planted))
    }

    private companion object {
        const val BODY = 8
    }
}
