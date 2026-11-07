package io.github.eslamasabry.oc.ui.gate

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * The layout guard (E02-S13; rules and numbers: native/docs/kit/LAYOUT-RULES.md). It renders a scene at a phone
 * (412 x 915 dp) and a tablet (1280 x 800 dp) window, light and dark, walks the Compose semantics tree and checks:
 *
 * - `edge-text` / `edge-tap`: words keep the 16 dp page gutter; a control's box keeps at least [Space.S1] from the
 *   window edge (a 48 dp icon button's glyph then lands on the gutter). Screens only.
 * - `title-edge`: rows stacked in one list column start their titles on one edge. Screens only.
 * - `off-window`: a floating panel the kit marks (a menu) fits inside the window; a longer one scrolls.
 * - `twin-row`: one list never shows two rows with the same words (a project listed twice). Screens only.
 * - `content-edge`: words the kit marks for the page's content edge (a bar's title and subtitle) start on the 16 dp
 *   gutter, where the transcript and the rows start. Screens on a phone only.
 * - `tight` / `overlap` / `off-scale`: a control and its neighbour above or below are at least [Space.S1] apart and
 *   never overlap; two controls are spaced on the 4 dp grid of the spacing scale.
 * - `small-target`: every tappable is at least 48 dp on both sides.
 * - `ellipsis` / `clipped`: words end in "…" only where the kit part declares the slot may
 *   ([KitLayoutMarks.MayTruncate]), and words are never cut off without a sign.
 * - `wide-text` / `wide-control` / `wide-row` (tablet): words and rows stay within the list width (960 dp), a
 *   button within the control width (400 dp).
 *
 * Exceptions are declared in the kit next to the part ([KitLayoutMarks]); a screen's own leftovers live in the
 * per-module baseline `native/config/layout/baseline/<module>.tsv`, which may only shrink
 * (tool/qa/layout_baseline.sh). `OC_LAYOUT_BASELINE=update` rewrites the baseline lines of the shots a run checked to
 * what it found.
 */
@OptIn(ExperimentalTestApi::class)
object LayoutGuard {
    private const val PHONE_W = 412 // F: test/goldens/kit/kit_gallery.dart:109, the census phone
    private const val PHONE_H = 915 // F: test/goldens/kit/kit_gallery.dart:109
    private const val TABLET_W = 1280 // F: kit-v2.md §8.5 G4 "1280×800 (tablet landscape or PC)"
    private const val TABLET_H = 800 // F: kit-v2.md §8.5 G4

    /** The windows a scene is checked at (story E02-S13). */
    enum class Window(val w: Int, val h: Int) { Phone(PHONE_W, PHONE_H), Tablet(TABLET_W, TABLET_H) }

    /** What to render: the gate's frame, style, wrapper, interaction and content. */
    class Scene(
        val frame: FlutterGoldenGate.Frame = FlutterGoldenGate.Frame.Screen,
        val style: Style = Style.Solid,
        val wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
        val act: (ComposeUiTest.() -> Unit)? = null,
        val content: @Composable () -> Unit,
    )

    /** One broken rule. [key] (without [detail], which carries coordinates) is what the baseline stores. */
    data class Finding(val window: Window, val rule: String, val subject: String, val detail: String) {
        val key: String get() = "${window.name.lowercase(Locale.ROOT)}\t$rule\t$subject"
    }

    private val root: File = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val native: File = File(root, "native")
    private val module: String =
        File(System.getProperty("user.dir")).canonicalFile.relativeToOrNull(native.canonicalFile)?.path
            ?.replace(File.separatorChar, '-')?.ifEmpty { null } ?: "unknown"
    private val baselineFile: File = File(native, "config/layout/baseline/$module.tsv")
    private val out: File = File(root, "looks-out/layout/$module")

    /**
     * Audits [scene] (see the class comment) and fails with every finding the module's baseline does not list.
     * [FlutterGoldenGate.check] calls it for every gate shot, so every screen and gallery scene with a golden is held
     * to the rules; a screen without a golden can call it directly.
     */
    fun check(shot: String, scene: Scene) {
        val found = audit(scene)
        val keys = found.map { "$shot\t${it.key}" }.toSortedSet()
        out.mkdirs()
        File(out, "$shot.tsv").writeText(found.joinToString("") { "$shot\t${it.key}\t${it.detail}\n" })
        if (System.getenv("OC_LAYOUT_BASELINE") == "update") {
            rewriteBaseline(shot, keys)
            return
        }
        val listed = readBaseline().filter { it.startsWith("$shot\t") }.toSet()
        val stale = listed - keys
        if (stale.isNotEmpty()) {
            System.err.println("layout guard: $shot no longer breaks ${stale.size} baselined rule(s); shrink it")
            stale.forEach { System.err.println("  - $it") }
        }
        val fresh = found.filter { "$shot\t${it.key}" !in listed }
        if (fresh.isNotEmpty()) {
            throw AssertionError(
                "layout guard: $shot breaks ${fresh.size} layout rule(s) (native/docs/kit/LAYOUT-RULES.md):\n" +
                    fresh.joinToString("\n") { "  ${it.window.name} ${it.rule} \"${it.subject}\": ${it.detail}" } +
                    "\nFix the screen, or the kit part when one cause breaks many screens. The baseline only shrinks.",
            )
        }
    }

    /** Every finding for [scene] at [windows] in light and dark, once each (light and dark usually agree). */
    fun audit(scene: Scene, windows: List<Window> = Window.entries): List<Finding> {
        val all = LinkedHashMap<String, Finding>()
        val screen = scene.frame == FlutterGoldenGate.Frame.Screen
        for (window in windows) for (dark in listOf(false, true)) {
            val axes = FlutterGoldenGate.Axes(rtl = false, scale = 1f, w = window.w, h = window.h)
            val read: ComposeUiTest.() -> Unit = {
                val roots = onAllNodes(isRoot(), useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                for (f in inspect(roots, window, screen)) all.putIfAbsent(f.key, f)
            }
            FlutterGoldenGate.render(
                axes, dark, MotionLevel.Off, scene.style, scene.frame, scene.wrap, scene.content,
                act = scene.act, onRoot = read,
            )
        }
        return all.values.toList()
    }

    /** The checks over one rendered tree; [screen] adds the page rules (gutter, title edge) a lone part cannot meet. */
    internal fun inspect(roots: List<SemanticsNode>, window: Window, screen: Boolean): List<Finding> =
        LayoutWalk(window, roots).run(screen)

    // --------------------------------------------------------------------------------------------- the baseline

    private fun readBaseline(): List<String> =
        if (!baselineFile.isFile) emptyList()
        else baselineFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    /** Replaces [shot]'s lines with [keys] under a file lock (test classes of one module run in parallel forks). */
    private fun rewriteBaseline(shot: String, keys: Set<String>) {
        baselineFile.parentFile.mkdirs()
        RandomAccessFile(File(out.also { it.mkdirs() }, ".baseline.lock"), "rw").use { lock ->
            lock.channel.lock().use {
                val lines = (readBaseline().filterNot { it.startsWith("$shot\t") } + keys).toSortedSet()
                if (lines.isEmpty()) {
                    baselineFile.delete()
                } else {
                    baselineFile.writeText(
                        "# Layout guard baseline of one module (native/docs/kit/LAYOUT-RULES.md). Only shrinks.\n" +
                            "# shot\twindow\trule\tsubject\n" + lines.joinToString("\n", postfix = "\n"),
                    )
                }
            }
        }
    }
}
