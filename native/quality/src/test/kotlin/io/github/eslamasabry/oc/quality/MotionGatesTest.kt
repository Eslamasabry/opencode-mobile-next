package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Motion review 2026-11-09: one gate per motion class it fixed (see [MotionGates]). */
class MotionGatesTest {
    private val root = File(System.getProperty("oc.root"))
    private val ui by lazy { BuildRestGates.production(root, "feature", "ui") }
    private val kits by lazy { ui.filterKeys { it.startsWith("ui/kit") || it.startsWith("ui/shell") } }

    private fun assertNone(hits: List<MotionGates.Hit>, what: String) =
        assertTrue(hits.isEmpty(), "$what:\n" + hits.joinToString("\n") { "${it.path}:${it.line} ${it.rule}" })

    @Test
    fun `MOT-GATE-1 kit motion specs come from KitMotion, and the reviewed exceptions only shrink`() {
        assertTrue(kits.size > 100, "scanned ${kits.size} kit files")
        val raw = kits.filter { (p, t) -> MotionGates.rawSpec(p, t).isNotEmpty() }.keys
        assertEquals(emptySet(), raw - MotionGates.RAW_SPEC_FILES.keys, "build the spec with KitMotion.specs")
        assertEquals(emptySet(), MotionGates.RAW_SPEC_FILES.keys - raw, "an entry no longer builds its own spec: remove it")
    }

    @Test
    fun `MOT-GATE-1 planted raw specs are caught, the motion core and comments are not`() {
        val planted =
            """
            val a by animateFloatAsState(1f, tween(300))
            // tween(300) in a comment is not code
            Modifier.animateContentSize()
            Modifier.animateContentSize(KitMotion.specs.quick())
            """.trimIndent()
        assertEquals(listOf(1, 3), MotionGates.rawSpec("ui/kit/X.kt", planted).map { it.line })
        assertEquals(0, MotionGates.rawSpec(MotionGates.MOTION_CORE.first(), planted).size)
    }

    @Test
    fun `MOT-GATE-2 every kit loop is gated on ambientLoops`() {
        val checked = kits.filterKeys { it !in MotionGates.LOOPS_GATED_BY_CALLER }
        assertNone(checked.flatMap { (p, t) -> MotionGates.ungatedLoop(p, t) }, "loops that keep running under Calm or still frames")
        assertTrue(MotionGates.LOOPS_GATED_BY_CALLER.keys.all { it in kits }, "every reviewed caller-gated loop still exists")
    }

    @Test
    fun `MOT-GATE-2 a planted ungated loop is caught`() {
        val ungated = "val still = KitMotion.state.reduced\nval t = rememberInfiniteTransition(label = \"x\")"
        val gated = "val still = !KitMotion.state.ambientLoops\nval t = rememberInfiniteTransition(label = \"x\")"
        assertEquals(listOf(2), MotionGates.ungatedLoop("ui/kit/X.kt", ungated).map { it.line })
        assertEquals(0, MotionGates.ungatedLoop("ui/kit/X.kt", gated).size)
    }

    @Test
    fun `MOT-GATE-3 horizontal slides follow the reading direction`() {
        val hits = ui.filterKeys { it !in MotionGates.PHYSICAL_SLIDES }.flatMap { (p, t) -> MotionGates.unmirroredSlide(p, t) }
        assertNone(hits, "slides that come from the wrong edge in Arabic")
        assertTrue(MotionGates.PHYSICAL_SLIDES.keys.all { File(root, it).isFile }, "every reviewed physical slide still exists")
    }

    @Test
    fun `MOT-GATE-3 a planted unmirrored slide is caught`() {
        val physical = "enter = slideInHorizontally(spec) { it }"
        val mirrored = "val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1\nenter = slideInHorizontally(spec) { sign * it }"
        assertEquals(listOf(1), MotionGates.unmirroredSlide("ui/kit/X.kt", physical).map { it.line })
        assertEquals(0, MotionGates.unmirroredSlide("ui/kit/X.kt", mirrored).size)
    }

    @Test
    fun `MOT-GATE-4 a dialog or sheet never scales in`() {
        assertNone(kits.flatMap { (p, t) -> MotionGates.modalScale(p, t) }, "modals that scale in (MOT-2)")
        val planted = "enter = fadeIn(spec) + scaleIn(spec, initialScale = 0.96f)"
        assertEquals(1, MotionGates.modalScale("ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitDialog.kt", planted).size)
    }
}
