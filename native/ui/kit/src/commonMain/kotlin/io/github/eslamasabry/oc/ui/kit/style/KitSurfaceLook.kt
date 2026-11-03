package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over

/** Where a surface sits, which tunes its depth (Dart `KitSurfaceRole`, `lib/ui/kit/kit_surface_look.dart`). */
public enum class KitSurfaceRole {
    /** Cards, grouped rows, panels and the answer cards: content in a scrolling page. */
    Content,

    /** A small resting control: a chip, a secondary button, a segment, an icon tile. */
    Control,

    /** The bed a row of controls sits in: a segmented track, a tab strip. */
    Track,

    /** A bounded piece that floats over the page: the dock, the top bar's pills, the message box, the jump pill. */
    Floating,

    /** A sheet, a dialog or a menu. */
    Sheet,

    /** The page itself. Fill only in every style: no edge, no shadow. */
    Ground,

    /** A text field, a search field, the ask field. `selected` while it has focus. */
    Field,

    /** A code block, log, terminal, diff or command box. Its fill stays opaque in every style. */
    Code,

    /** A divider line. */
    Divider,

    /** The hover and pressed highlight laid over a row or a tappable surface. */
    StateLayer,

    /** A switch's track; `selected` when it is on, with the on fill as `color`. */
    Toggle,
    ;

    public companion object {
        /** The roles a pane, panel or piece takes; the others are a part's own surface. */
        public val panes: List<KitSurfaceRole> = listOf(Content, Control, Track, Floating, Sheet)
    }
}

/** The four corners of a surface in dp. Light comes from the top start, so the corners are directional. */
public data class KitRadii(val topStart: Float, val topEnd: Float, val bottomEnd: Float, val bottomStart: Float) {
    val uniform: Boolean get() = topStart == topEnd && topStart == bottomEnd && topStart == bottomStart

    public fun map(f: (Float) -> Float): KitRadii = KitRadii(f(topStart), f(topEnd), f(bottomEnd), f(bottomStart))

    public companion object {
        public val Zero: KitRadii = all(0f)

        /** Corners that make any box a stadium (Dart `KitSurfaceLook.stadium`). */
        public const val STADIUM: Float = 9999f

        public fun all(dp: Float): KitRadii = KitRadii(dp, dp, dp, dp)

        /** Rounded on top only: a bottom sheet. */
        public fun top(dp: Float): KitRadii = KitRadii(dp, dp, 0f, 0f)

        public fun lerp(a: KitRadii, b: KitRadii, t: Float): KitRadii =
            KitRadii(
                lerpF(a.topStart, b.topStart, t),
                lerpF(a.topEnd, b.topEnd, t),
                lerpF(a.bottomEnd, b.bottomEnd, t),
                lerpF(a.bottomStart, b.bottomStart, t),
            )
    }
}

/** A plain drop shadow (Dart `BoxShadow`). [dx] points to the end; it mirrors in right-to-left. */
public data class KitShadow(
    val color: Argb,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val blur: Float = 0f,
    val spread: Float = 0f,
) {
    /** Dart `BoxShadow.scale`: offset, blur and spread scaled, the colour kept. */
    public fun scale(f: Float): KitShadow = KitShadow(color, dx * f, dy * f, blur * f, spread * f)

    public companion object {
        public fun lerp(a: KitShadow, b: KitShadow, t: Float): KitShadow =
            KitShadow(
                lerp(a.color, b.color, t), lerpF(a.dx, b.dx, t), lerpF(a.dy, b.dy, t),
                lerpF(a.blur, b.blur, t), lerpF(a.spread, b.spread, t),
            )

        /** Dart `BoxShadow.lerpList`: pairs lerp; a shadow only one side has grows from or shrinks to nothing. */
        public fun lerpList(a: List<KitShadow>, b: List<KitShadow>, t: Float): List<KitShadow> {
            val common = minOf(a.size, b.size)
            return buildList {
                for (i in 0 until common) add(lerp(a[i], b[i], t))
                for (i in common until a.size) add(a[i].scale(1f - t))
                for (i in common until b.size) add(b[i].scale(t))
            }
        }
    }
}

/** No colour at all. */
public val Clear: Argb = Argb(0)

/**
 * One resolved surface (Dart `KitSurfaceDecoration`): a fill with a lit top-start corner, paired soft shadows raised
 * out of ([inset] 0) or pressed into ([inset] 1) the ground, plain drop shadows, a blur-free hard shadow towards the
 * bottom end, and an inner edge that fades from the top start to the bottom end. Pure data: the kit paints it; a style
 * change fades between two looks with [lerp].
 */
public data class KitSurfaceLook(
    val radius: KitRadii = KitRadii.Zero,
    val circle: Boolean = false,
    val fill: Argb,
    /** The fill at the top-start corner; it fades to [fill] at the bottom end. */
    val fillStart: Argb = fill,
    val edgeStart: Argb = Clear,
    val edgeEnd: Argb = Clear,
    val edgeWidth: Float = 0f,
    val lightShadow: Argb = Clear,
    val darkShadow: Argb = Clear,
    val distance: Float = 0f,
    val blur: Float = 0f,
    val inset: Float = 0f,
    val shadows: List<KitShadow> = emptyList(),
    val hardShadow: Argb = Clear,
    val hardOffset: Float = 0f,
    /** How far the face and its content sit moved towards the bottom end while pressed (hook H2). */
    val faceShift: Float = 0f,
    /** Whether a `content` surface inside this one sits on this one's face (the nesting rule). */
    val hosts: Boolean = false,
) {
    /** Whether anything falls outside the surface. */
    val casts: Boolean
        get() = (inset < 1f && distance > 0f && (lightShadow.alpha > 0 || darkShadow.alpha > 0)) ||
            shadows.any { it.color.alpha > 0 } || (hardOffset > 0f && hardShadow.alpha > 0)

    /** Whether it paints nothing around what it holds: no fill, edge or shadow. */
    val isBare: Boolean
        get() = fill.alpha == 0 && fillStart.alpha == 0 &&
            (edgeWidth == 0f || (edgeStart.alpha == 0 && edgeEnd.alpha == 0)) &&
            shadows.isEmpty() && hardShadow.alpha == 0 && distance == 0f

    /** The same with nothing falling outside the surface (hook H9: a surface nested in one that casts). */
    val withoutCast: KitSurfaceLook
        get() = copy(
            lightShadow = if (inset > 0f) lightShadow else Clear,
            darkShadow = if (inset > 0f) darkShadow else Clear,
            distance = if (inset > 0f) distance else 0f,
            blur = if (inset > 0f) blur else 0f,
            shadows = emptyList(),
            hardShadow = Clear,
            hardOffset = 0f,
        )

    /** A surface nested in one that hosts it: nothing of its own; it keeps its shape and goes on hosting. */
    val nested: KitSurfaceLook get() = KitSurfaceLook(radius = radius, circle = circle, fill = fill.withAlpha(0f), hosts = hosts)

    /** The fill alone: no edge, no shadow (the page). */
    val fillOnly: KitSurfaceLook get() = KitSurfaceLook(radius = radius, circle = circle, fill = fill, fillStart = fillStart)

    /** The same, hosting the surfaces nested in it. */
    val hosting: KitSurfaceLook get() = if (hosts) this else copy(hosts = true)

    /** The same with its fill made opaque over [ground] (code surfaces). */
    public fun opaqueOver(ground: Argb): KitSurfaceLook {
        fun solid(c: Argb) = if (c.alpha >= 255) c else c.over(ground.withAlpha(1f))
        if (fill.alpha >= 255 && fillStart.alpha >= 255) return this
        return copy(fill = solid(fill), fillStart = solid(fillStart))
    }

    public companion object {
        /** A panel with nothing of its own (Dart `KitSurfaceLook.bare`). */
        public val Bare: KitSurfaceLook = KitSurfaceLook(fill = Clear)

        /**
         * The fade between two looks (Dart `KitSurfaceDecoration.lerpSurface`): colours and widths move together; a
         * shadow fades rather than slides in from nothing (a distance, blur or offset only one side has is kept).
         */
        public fun lerp(a: KitSurfaceLook, b: KitSurfaceLook, t: Float): KitSurfaceLook {
            fun c(x: Argb, y: Argb) = lerp(x, y, t)
            fun d(x: Float, y: Float) = lerpF(x, y, t)
            fun kept(x: Float, y: Float) = if (x == 0f) y else if (y == 0f) x else d(x, y)
            return KitSurfaceLook(
                radius = KitRadii.lerp(a.radius, b.radius, t),
                circle = if (t < .5f) a.circle else b.circle,
                fill = c(a.fill, b.fill),
                fillStart = c(a.fillStart, b.fillStart),
                edgeStart = c(a.edgeStart, b.edgeStart),
                edgeEnd = c(a.edgeEnd, b.edgeEnd),
                edgeWidth = d(a.edgeWidth, b.edgeWidth),
                lightShadow = c(a.lightShadow, b.lightShadow),
                darkShadow = c(a.darkShadow, b.darkShadow),
                distance = kept(a.distance, b.distance),
                blur = kept(a.blur, b.blur),
                inset = d(a.inset, b.inset),
                shadows = KitShadow.lerpList(a.shadows, b.shadows, t),
                hardShadow = c(a.hardShadow, b.hardShadow),
                hardOffset = kept(a.hardOffset, b.hardOffset),
                faceShift = d(a.faceShift, b.faceShift),
                hosts = if (t < .5f) a.hosts else b.hosts,
            ).let { if (t == 0f) a else if (t == 1f) b else it }
        }
    }
}

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
