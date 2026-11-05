package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.max
import kotlin.math.min

/**
 * Brutal: a 2 dp ink edge and a hard no-blur shadow toward the bottom end that sits down when pressed; cream paper
 * ground in light, near-black in dark. Port of Dart `KitStyleBrutal`.
 */
public object BrutalSpec : StyleSpec {
    public const val EDGE: Float = 2f
    public const val FOCUS_RING: Float = 3f

    override val tokens: StyleTokens =
        StyleTokens(
            defaults = Knobs(corners = Corners.Soft, depth = Depth.Raised),
            keepsUnderHighContrast = true,
            live = LivePolicy(maxStrength = .7f),
            glow = StyleGlow(halo = false, lineWidthDp = 2f),
            flattensNested = true,
            linkUnderline = EDGE,
            needsYou = NeedsYouLook(tag = NeedsYouTag.Pill, tint = false, ring = false, startEdge = false),
            type = StyleType(displayWeight = 800, displayTracking = -.03f, bodyWeight = 500, monoLabels = true, firmWeights = true),
        )

    public fun offsetOf(role: SurfaceRole, compact: Boolean = false): Float =
        when (role) {
            SurfaceRole.Content, SurfaceRole.Sheet -> if (compact) 3f else 4f
            SurfaceRole.Control -> if (compact) 2f else 3f
            SurfaceRole.Floating -> if (compact) 4f else 6f
            else -> 0f
        }

    private val warmth = floatArrayOf(.12f, .09f, .06f, .03f)
    private const val PAPER_SHARE = .35f
    private const val MINT_WORDS = 7.0

    public fun shadowInk(roles: Roles): Argb =
        if (roles.dark) lerp(roles.text3, roles.ground, .4f).opaque() else roles.text1

    /** The accent as a light face the ink reads on at AAA (dark keeps the accent itself). */
    public fun mintOf(roles: Roles): Argb {
        if (roles.dark) return roles.accent
        val tone = min(hslSaturation(roles.accent), .7f)
        var l = max(hslLightness(roles.accent), .55f)
        while (l <= .85f) {
            val face = fromHsl(hslHue(roles.accent), tone, l)
            if (contrast(roles.text1, face) >= MINT_WORDS) return face
            l += .03f
        }
        return roles.accent
    }

    private fun acts(r: SurfaceRequest): Boolean = r.color == r.roles.accent && !r.selected

    private fun own(r: SurfaceRequest): Argb? = if (acts(r)) mintOf(r.roles) else r.color

    override fun onColor(r: SurfaceRequest): Argb? {
        if (!acts(r)) return null
        return if (mintOf(r.roles) == r.roles.accent) null else r.roles.text1
    }

    override fun resolveFilledLayer(r: SurfaceRequest): SurfaceDecoration =
        SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill = if (r.knobs.depth == Depth.Raised) r.roles.clear() else r.roles.text1.withAlpha(.12f),
        )

    override fun adjustRoles(roles: Roles): Roles {
        if (roles.dark) return roles.copy(surface1 = roles.surface2)
        for (w in warmth) {
            val warm = roles.attentionFill.opaque()
            fun stir(c: Argb, share: Float) = warm.withAlpha(share).over(c)
            val paper = stir(roles.surface2, w * PAPER_SHARE)
            val next = roles.copy(ground = stir(roles.ground, w), surface1 = paper, surface2 = paper, surface3 = stir(roles.surface3, w))
            if (readable(next)) return next
        }
        return roles
    }

    private fun readable(r: Roles): Boolean =
        listOf(r.ground, r.surface1, r.surface2, r.surface3).all { fill ->
            listOf(r.text1, r.text2, r.text3, r.attention).all { ink -> contrast(ink, fill) >= 4.5 }
        }

    override fun resolve(r: SurfaceRequest): SurfaceDecoration {
        part(r)?.let { return it }
        // The page itself is the paper the surfaces sit on: it must not become the one outer surface (hook H9).
        if (r.level == SurfaceLevel.Ground && r.color == null) return SurfaceDecoration(radii = r.radii, circle = r.circle, fill = r.roles.ground)
        val roles = r.roles
        val ink = roles.text1
        val raised = r.knobs.depth == Depth.Raised
        val fall = offsetOf(r.role, r.knobs.density == io.github.eslamasabry.oc.ui.design.tokens.Density.Compact)
        val even = r.role != SurfaceRole.Sheet || r.radii.uniform
        val down = r.selected || r.pressed
        val sink = if (r.pressed && raised && even) fall else 0f
        val rest = if (r.role == SurfaceRole.Control) roles.surface2 else r.levelFill
        if (r.disabled && r.role == SurfaceRole.Control) {
            return SurfaceDecoration(
                radii = r.radii,
                circle = r.circle,
                fill = r.color ?: roles.surface3,
                edgeStart = roles.text3,
                edgeEnd = roles.text3,
                edgeWidthDp = EDGE,
            )
        }
        val fill = own(r) ?: (if (down) roles.surface3 else rest)
        val keepsEdge = r.knobs.edges == Edges.Hairline || r.role == SurfaceRole.Control || r.role == SurfaceRole.Track
        val offset = if (raised && !down && even) fall else 0f
        val ringed = roles.dark && r.selected && r.color == null && (r.role == SurfaceRole.Content || r.role == SurfaceRole.Control)
        val line = if (ringed) roles.accent else ink
        val edged = keepsEdge || ringed
        return SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill = fill,
            edgeStart = if (edged) line else null,
            edgeEnd = if (edged) line else null,
            edgeWidthDp = if (edged) EDGE else 0f,
            hardShadow = if (offset > 0f) shadowInk(roles) else null,
            hardOffsetDp = offset,
            faceShiftDp = sink,
        )
    }

    private fun part(r: SurfaceRequest): SurfaceDecoration? {
        val roles = r.roles
        val ink = roles.text1
        val edged = r.knobs.edges == Edges.Hairline
        fun box(fill: Argb, keep: Boolean = true, ring: List<Shadow> = emptyList()) =
            SurfaceDecoration(
                radii = r.radii,
                circle = r.circle,
                fill = fill,
                edgeStart = if (keep) ink else null,
                edgeEnd = if (keep) ink else null,
                edgeWidthDp = if (keep) EDGE else 0f,
                shadows = ring,
            )
        return when (r.role) {
            SurfaceRole.Ground -> styleGround(r)
            SurfaceRole.Field -> box(r.color ?: r.levelFill, ring = if (r.selected) listOf(Shadow(0f, 0f, 0f, roles.accent, spreadDp = FOCUS_RING)) else emptyList())
            SurfaceRole.Toggle -> box((if (r.selected) r.color else null) ?: r.levelFill)
            SurfaceRole.Code -> box(r.color ?: r.levelFill, keep = edged)
            SurfaceRole.Divider ->
                SurfaceDecoration(fill = if (edged && !r.withinGroup) ink.withAlpha(.9f) else roles.clear()) // F: lib/ui/kit/style/kit_style_brutal.dart:307
            SurfaceRole.StateLayer ->
                SurfaceDecoration(radii = r.radii, circle = r.circle, fill = if (r.pressed) roles.surface3 else ink.withAlpha(.04f))
            else -> null
        }
    }
}
