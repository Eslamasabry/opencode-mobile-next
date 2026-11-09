package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cache-first (CF): the source half of the guard; each screen's own test proves its first frame (see the audit). */
class CacheFirstGateTest {
    private val root = File(System.getProperty("oc.root"))
    private val features by lazy { BuildRestGates.production(root, "feature") }

    @Test
    fun `CF-GATE-1 no feature draws an offline, cached or fallback layout of its own, and the old ones only go`() {
        assertTrue(features.size > 300, "scanned ${features.size} feature files")
        val found = features.filter { (p, t) -> CacheFirstGate.alternateLayout(p, t).isNotEmpty() }.keys
        assertEquals(emptySet(), found - CacheFirstGate.ALTERNATE_LAYOUTS.keys, "draw the real layout from last-known data instead")
    }

    @Test
    fun `CF-GATE-2 every loading branch that replaces a body is reviewed, and pending screens only shrink`() {
        val counts = features.mapValues { (p, t) -> CacheFirstGate.bodySwaps(p, t).size }.filterValues { it > 0 }
        val unreviewed = counts.filter { (p, n) -> n > (CacheFirstGate.REVIEWED[p]?.branches ?: 0) }
        assertEquals(
            emptyMap(),
            unreviewed,
            "a new loading body: draw the screen from last-known data (LastKnown), or review it in CacheFirstGate.REVIEWED",
        )
        val pending = CacheFirstGate.REVIEWED.count { (p, r) -> r.kind == CacheFirstGate.Pending && (counts[p] ?: 0) > 0 }
        assertTrue(pending <= CacheFirstGate.PENDING_CEILING, "$pending pending screens, ceiling ${CacheFirstGate.PENDING_CEILING}")
        val cold = CacheFirstGate.REVIEWED.values.mapNotNull { (it.kind as? CacheFirstGate.Cold)?.proof }.toSet()
        val proofs = File(root, "feature").walkTopDown().filter { it.isFile && it.extension == "kt" && "Test" in it.path }
            .map { it.readText() }.joinToString("\n")
        assertEquals(emptySet(), cold.filter { "`$it " !in proofs }.toSet(), "every cache-first screen names its first-frame test")
    }

    @Test
    fun `CF-GATE-3 planted alternate layouts and body swaps are caught, comments and kit parts are not`() {
        val planted =
            """
            @Composable
            internal fun OfflineConversations(rows: List<Row>) {}
            @Composable
            fun HomeOpeningRow(row: Row) {}
            // fun CachedThing() in a comment is not code
            fun cachedRowsFor(id: String) = Unit
            """.trimIndent()
        assertEquals(listOf(2, 4), CacheFirstGate.alternateLayout("feature/x/src/commonMain/X.kt", planted).map { it.line })
        assertEquals(0, CacheFirstGate.alternateLayout("ui/kit/src/commonMain/X.kt", planted).size)

        val swaps =
            """
            Phase.Loading -> KitSkeletonRows(count = 3)
            TeamPhase.Loading -> Unit
            if (state.loading) KitSkeletonTranscript()
            ChatPhase.Loading -> ChatLoadingBody()
            Phase.Live -> Rows()
            """.trimIndent()
        assertEquals(listOf(1, 2, 3, 4), CacheFirstGate.bodySwaps("feature/x/src/commonMain/X.kt", swaps).map { it.line })
    }
}
