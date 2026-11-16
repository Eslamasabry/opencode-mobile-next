package io.github.eslamasabry.oc.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** K20's planted sources, run against [KitSurfaces] (SURFACES-SPEC.md §5 item 6). */
class KitSurfacesPlantedTest {
    private val path = "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/x/KitPlanted.kt"

    private fun counted(text: String, allow: String = ""): Int =
        KitSurfaces.count(mapOf(path to text), KitSurfaces.parseAllow(allow).first).perFile[path] ?: 0

    @Test
    fun `K20 a hand-painted fill, edge, shadow, drawn surface or glass face counts once per line`() {
        val planted =
            """
            Box(Modifier.background(colors.surface2))
            Box(Modifier.background(colors.surface1.copy(alpha = 1f), CircleShape))
            Box(Modifier.background(if (stopping) colors.surface3 else colors.text1, CircleShape))
            Box(Modifier.background(LocalKitColors.current.ground.copy(alpha = 1f)))
            Box(Modifier.background(colors.surface1).border(1.dp, colors.hairline, shape))
            Modifier.dropShadow(shape, shadow)
            drawRoundRect(colors.surface3, cornerRadius = r)
            val face = kitLiquidFace(LiquidRole.Pill, backdrop, shape)
            val decoration = remember(request, state) { state.spec.resolve(request) }
            """.trimIndent()
        assertEquals(9, counted(planted))
    }

    @Test
    fun `K20 the resolver's request, comments, other colours and definitions do not count`() {
        val clean =
            """
            val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, ownFill = colors.surface2))
            Box(Modifier.then(look.modifier))
            // Box(Modifier.background(colors.surface2)) in a comment is not code
             * .border(1.dp, colors.hairline) in KDoc is not code
            Box(Modifier.background(colors.accent))
            drawRoundRect(colors.accent, cornerRadius = r)
            public fun Modifier.kitSurfaceGlass(face: SurfaceDecoration?): Modifier = this
            internal fun kitLiquidFace(role: LiquidRole): Modifier? = null
            """.trimIndent()
        assertEquals(0, counted(clean))
    }

    @Test
    fun `K20 an allowlisted line does not count, and an entry that allows nothing is stale`() {
        val text = "Box(Modifier.background(colors.surface2))\nBox(Modifier.background(colors.ground))"
        assertEquals(1, counted(text, "$path | background\\(colors\\.ground\\) | a scrim over the page"))
        val stale = "$path | background\\(colors\\.surface3\\) | nothing paints this any more"
        val result = KitSurfaces.count(mapOf(path to text), KitSurfaces.parseAllow(stale).first)
        assertEquals(listOf(stale), result.stale.map { it.source })
    }

    @Test
    fun `K20 an allowlist line without a reason is refused`() {
        val (entries, errors) = KitSurfaces.parseAllow("$path | background\\(colors\\.ground\\) | ")
        assertTrue(entries.isEmpty())
        assertEquals(1, errors.size)
    }

    @Test
    fun `K20 scans kit production sources only, never tests or the glass engine`() {
        assertTrue(KitSurfaces.inScope(path))
        assertTrue(KitSurfaces.inScope("ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/tool/KitCommandBox.kt"))
        assertTrue(!KitSurfaces.inScope("ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/x/KitPlantedTest.kt"))
        assertTrue(!KitSurfaces.inScope("ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/effects/KitLiquidGlass.kt"))
        assertTrue(!KitSurfaces.inScope("feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt"))
    }

    @Test
    fun `K20b a part that paints its own face and never asks the style is caught, unless listed with a reason`() {
        // The voice pill before glass-truth: its own fill and border, no style path at all.
        val own = "Column(Modifier.background(colors.surface2, shape).border(1.dp, colors.hairline, shape)) {}"
        assertEquals(listOf(path), KitSurfaces.ownFaces(mapOf(path to own), emptyList(), emptyList()).unasked)
        // Dressing for Brutal and Paper alone is not enough: Glass still draws the Solid face.
        val dressedOnly = "val d = kitDressedLook(input)\nRow(Modifier.then(d?.let { Modifier.kitSurface(it) } ?: Modifier.background(colors.surface3)))"
        assertEquals(listOf(path), KitSurfaces.ownFaces(mapOf(path to dressedOnly), emptyList(), emptyList()).unasked)
        // Asking a path Glass answers clears it (the raw face stays for Solid).
        val asked = "val s = kitStyledFace(input)\nRow(Modifier.then(s?.let { Modifier.kitSurface(it) } ?: Modifier.background(colors.surface3)))"
        assertEquals(emptyList(), KitSurfaces.ownFaces(mapOf(path to asked), emptyList(), emptyList()).unasked)
        val wrapped = "val look = terminalSurfaceLook(role)\nBox(Modifier.background(colors.surface2))"
        assertEquals(emptyList(), KitSurfaces.ownFaces(mapOf(path to wrapped), emptyList(), emptyList()).unasked)
        // A comment that names the resolver asks nothing.
        val commented = "// kitStyledFace(input) one day\nBox(Modifier.background(colors.surface2))"
        assertEquals(listOf(path), KitSurfaces.ownFaces(mapOf(path to commented), emptyList(), emptyList()).unasked)
        // Listed with a reason: allowed; listed but no longer painting: stale.
        val (listed, errors) = KitSurfaces.parseOwnFaces("$path | an inset inside the message box")
        assertEquals(emptyList(), errors)
        assertEquals(emptyList(), KitSurfaces.ownFaces(mapOf(path to own), emptyList(), listed).unasked)
        assertEquals(listed, KitSurfaces.ownFaces(mapOf(path to asked), emptyList(), listed).stale)
        assertEquals(1, KitSurfaces.parseOwnFaces("$path | ").second.size)
    }
}
