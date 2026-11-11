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
}
