package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.max
import kotlin.math.min

/**
 * Brutal (Dart `KitStyleBrutal`, neo-brutalism): ink and paper. A bold 2 dp ink edge on everything that holds content
 * or takes input, a hard offset shadow with no blur towards the bottom end, flat opaque fills on a cream paper ground
 * (light). A pressed control sinks into its shadow; a chosen one already sits down. Only the outer surface casts.
 */
public object KitStyleBrutal : KitStyleSpec {
    /** The ink edge, in dp. */
    public const val EDGE: Float = 2f

    /** The accent ring around a focused field, outside its ink edge. */
    public const val FOCUS_RING: Float = 3f

    override val tokens: KitStyleTokens =
        KitStyleTokens(
            defaults = KitKnobs(corners = KitCorners.Soft, depth = KitDepth.Raised),
            keepsUnderHighContrast = true,
            live = KitLivePolicy(maxStrength = .7f),
            glow = KitStyleGlow(halo = false, lineWidth = 2f),
            flattensNested = true,
            linkUnderline = EDGE,
            needsYou = KitNeedsYouLook(tag = KitNeedsYouTag.Pill, tint = false, ring = false, startEdge = false),
            type = KitStyleType(displayWeight = 800, displayTracking = -.03f, bodyWeight = 500, monoLabels = true, firmWeights = true),
        )

    /** How far the hard shadow falls at Comfortable, per role (0: none). */
    public fun offsetOf(role: KitSurfaceRole): Float =
        when (role) {
            KitSurfaceRole.Content -> 4f
            KitSurfaceRole.Control -> 3f
            KitSurfaceRole.Floating -> 6f
            KitSurfaceRole.Sheet -> 4f
            else -> 0f
        }

    /** [offsetOf] under Density › Compact: a step tighter. */
    public fun compactOffsetOf(role: KitSurfaceRole): Float =
        when (role) {
            KitSurfaceRole.Content -> 3f
            KitSurfaceRole.Control -> 2f
            KitSurfaceRole.Floating -> 4f
            KitSurfaceRole.Sheet -> 3f
            else -> 0f
        }

    private val warmth = listOf(.12f, .09f, .06f, .03f)
    private const val PAPER_SHARE = .35f
    private const val MINT_WORDS = 7.0

    /** The shadow colour: ink in light; in dark an ink-grey (`text3` 40 % back towards the ground). */
    public fun shadowInk(roles: Roles): Argb =
        if (roles.dark) mix(roles.text3, roles.ground, .4).withAlpha(1f) else roles.text1

    /**
     * The main action's face: in light, the accent lifted to its bright hue until ink reads on it at 7:1; the dark
     * accent as it is; the accent itself where no step gets there.
     */
    public fun mintOf(roles: Roles): Argb {
        if (roles.dark) return roles.accent
        val hsl = Hsl.of(roles.accent)
        val tone = hsl.copy(saturation = min(hsl.saturation, .7))
        var l = max(hsl.lightness, .55)
        while (l <= .85) {
            val face = tone.copy(lightness = l).toArgb()
            if (contrast(roles.text1, face) >= MINT_WORDS) return face
            l += .03
        }
        return roles.accent
    }

    private fun acts(r: KitSurfaceRequest) = r.color == r.roles.accent && !r.selected

    private fun own(r: KitSurfaceRequest): Argb? = if (acts(r)) mintOf(r.roles) else r.color

    override fun onColor(r: KitSurfaceRequest): Argb? {
        if (!acts(r)) return null
        return if (mintOf(r.roles) == r.roles.accent) null else r.roles.text1
    }

    /** A held control with a fill of its own keeps its colour: raised, the sink is the answer; flat, a faint ink. */
    override fun resolveFilledLayer(r: KitSurfaceRequest): KitSurfaceLook =
        KitSurfaceLook(
            radius = r.radius,
            circle = r.circle,
            fill = if (r.knobs.depth == KitDepth.Raised) Clear else r.roles.text1.withAlpha(.12f),
        )

    override fun adjustRoles(roles: Roles): Roles {
        if (roles.dark) return roles.copy(surface1 = roles.surface2)
        val warm = roles.attentionFill.withAlpha(1f)
        fun stir(c: Argb, share: Float) = warm.withAlpha(share).over(c)
        for (w in warmth) {
            val paper = stir(roles.surface2, w * PAPER_SHARE)
            val next = roles.copy(ground = stir(roles.ground, w), surface1 = paper, surface2 = paper, surface3 = stir(roles.surface3, w))
            if (readable(next)) return next
        }
        return roles
    }

    /** Every word role keeps 4.5:1 on every step it may sit on. */
    internal fun readable(r: Roles): Boolean =
        listOf(r.ground, r.surface1, r.surface2, r.surface3).all { fill ->
            listOf(r.text1, r.text2, r.text3, r.attention).all { contrast(it, fill) >= 4.5 }
        }

    override fun resolve(r: KitSurfaceRequest): KitSurfaceLook {
        part(r)?.let { return it }
        if (r.level == SurfaceLevel.Ground && r.color == null) return r.solid
        val roles = r.roles
        val ink = roles.text1
        val raised = r.knobs.depth == KitDepth.Raised
        val compact = r.knobs.density == Density.Compact
        val fall = if (compact) compactOffsetOf(r.role) else offsetOf(r.role)
        val radius = r.radius
        // A bottom sheet (rounded on top only) sits on the scrim without a shadow; a dialog or a menu casts.
        val even = r.role != KitSurfaceRole.Sheet || radius.uniform
        val down = r.selected || r.pressed
        val sink = if (r.pressed && raised && even) fall else 0f
        val rest = if (r.role == KitSurfaceRole.Control) roles.surface2 else r.levelFill
        if (r.disabled && r.role == KitSurfaceRole.Control) {
            return KitSurfaceLook(
                radius = radius, circle = r.circle, fill = r.color ?: roles.surface3,
                edgeStart = roles.text3, edgeEnd = roles.text3, edgeWidth = EDGE,
            )
        }
        val fill = own(r) ?: (if (down) roles.surface3 else rest)
        val keepsEdge = r.knobs.edges == KitEdges.Hairline || r.role == KitSurfaceRole.Control || r.role == KitSurfaceRole.Track
        val offset = if (raised && !down && even) fall else 0f
        val ringed = roles.dark && r.selected && r.color == null &&
            (r.role == KitSurfaceRole.Content || r.role == KitSurfaceRole.Control)
        val line = if (ringed) roles.accent else ink
        val edged = keepsEdge || ringed
        return KitSurfaceLook(
            radius = radius,
            circle = r.circle,
            fill = fill,
            edgeStart = if (edged) line else Clear,
            edgeEnd = if (edged) line else Clear,
            edgeWidth = if (edged) EDGE else 0f,
            hardShadow = if (offset > 0f) shadowInk(roles) else Clear,
            hardOffset = offset,
            faceShift = sink,
        )
    }

    /** A part's own surface, or null for a pane. None casts. */
    private fun part(r: KitSurfaceRequest): KitSurfaceLook? {
        val roles = r.roles
        val ink = roles.text1
        val edged = r.knobs.edges == KitEdges.Hairline
        fun box(fill: Argb, keep: Boolean = true, ring: List<KitShadow> = emptyList()) =
            KitSurfaceLook(
                radius = r.radius, circle = r.circle, fill = fill,
                edgeStart = if (keep) ink else Clear, edgeEnd = if (keep) ink else Clear,
                edgeWidth = if (keep) EDGE else 0f, shadows = ring,
            )
        return when (r.role) {
            KitSurfaceRole.Ground -> kitStyleGround(r)
            KitSurfaceRole.Field ->
                box(r.color ?: r.levelFill, ring = if (r.selected) listOf(KitShadow(roles.accent, spread = FOCUS_RING)) else emptyList())
            KitSurfaceRole.Toggle -> box((if (r.selected) r.color else null) ?: r.levelFill)
            KitSurfaceRole.Code -> box(r.color ?: r.levelFill, keep = edged)
            KitSurfaceRole.Divider -> KitSurfaceLook(fill = if (edged && !r.withinGroup) ink.withAlpha(.9f) else Clear)
            KitSurfaceRole.StateLayer ->
                KitSurfaceLook(radius = r.radius, circle = r.circle, fill = if (r.pressed) roles.surface3 else ink.withAlpha(.04f))
            else -> null
        }
    }
}
