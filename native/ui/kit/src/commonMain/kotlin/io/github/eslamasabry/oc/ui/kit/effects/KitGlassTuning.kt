package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.eslamasabry.oc.ui.design.effects.GlassKnobs
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.effects.LiquidHold

/*
 * The Glass lab's tuning as the kit draws it (owner 2026-11-13). `KitTheme` provides the app's tuning (the person's
 * "Use in the whole app" switch, `KitAppearance.glassTuning`); the lab provides its draft over its own preview
 * ([KitGlassTuned]). Null, the default, draws the shipped glass. Every liquid pane's spec goes through [tuned]: a knob
 * that equals the shipped one leaves the spec as it was, so an untouched tuning draws exactly today's glass.
 */

/** The glass tuning in force here; null draws the shipped glass. Dynamic: a slider moves it at frame rate. */
public val LocalKitGlassTuning: ProvidableCompositionLocal<GlassTuning?> = compositionLocalOf { null }

/** [content] drawn with [tuning]'s glass (the Glass lab's preview of its draft). */
@Composable
public fun KitGlassTuned(tuning: GlassTuning?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitGlassTuning provides tuning, content = content)
}

/**
 * This spec with every knob of [t] that differs from [shipped] (the same kind and theme's shipped knobs) put in its
 * place. [platformResolution] re-decides the backdrop's resolution for the tuned blur ([liquidResolutionOf]).
 */
internal fun KitLiquidPaneSpec.tuned(t: GlassKnobs, shipped: GlassKnobs, platformResolution: Float): KitLiquidPaneSpec {
    if (t == shipped) return this
    fun pick(tuned: Float, ship: Float, now: Float) = if (tuned != ship) tuned else now
    val r = recipe
    // "Light passing through" sets the softening as a whole; a fine softening knob someone moved wins over it.
    val pass = if (t.passThrough != shipped.passThrough) GlassTuning.passThrough(t.passThrough) else null
    val softenOn = if (t.soften != shipped.soften) t.soften >= .5f else pass?.let { it[0] >= .5f }
    val limit = if (t.colourLimit != shipped.colourLimit) t.colourLimit else pass?.get(3)
    val localMoved = t.localDp != shipped.localDp || t.localFree != shipped.localFree || t.localSlope != shipped.localSlope
    val holdMoved = softenOn != null || limit != null || localMoved
    val recipe =
        r.copy(
            saturation = pick(t.saturation, shipped.saturation, r.saturation),
            contrast = pick(t.contrast, shipped.contrast, r.contrast),
            brightness = pick(t.brightness, shipped.brightness, r.brightness),
            blurDp = pick(t.blurDp, shipped.blurDp, r.blurDp),
            refractionDp = pick(t.lensStrengthDp, shipped.lensStrengthDp, r.refractionDp),
            shadowAlpha = pick(t.shadow, shipped.shadow, r.shadowAlpha),
            rimMaxDp = pick(t.lensWidthDp, shipped.lensWidthDp, r.rimMaxDp),
            rimShare = pick(t.lensShare, shipped.lensShare, r.rimShare),
            depth = if (t.lensMiddle != shipped.lensMiddle) t.lensMiddle >= .5f else r.depth,
            tint = pick(t.tint, shipped.tint, r.tint),
            hold =
                if (holdMoved)
                    LiquidHold(
                        knee = softenOn ?: r.hold.knee,
                        chroma = limit ?: r.hold.chroma,
                        localDp = pick(t.localDp, shipped.localDp, r.hold.localDp),
                        localFree = pick(t.localFree, shipped.localFree, r.hold.localFree),
                        localSlope = pick(t.localSlope, shipped.localSlope, r.hold.localSlope),
                    )
                else r.hold,
        )
    val tintMoved = t.tint != shipped.tint || t.tintLightness != shipped.tintLightness
    fun face(c: Color): Color {
        if (!tintMoved) return c
        val shade =
            when {
                t.tintLightness > 0f -> lerp(c.copy(alpha = 1f), Color.White, t.tintLightness)
                t.tintLightness < 0f -> lerp(c.copy(alpha = 1f), Color.Black, -t.tintLightness)
                else -> c.copy(alpha = 1f)
            }
        return shade.copy(alpha = if (t.tint != shipped.tint) t.tint.coerceIn(0f, 1f) else c.alpha)
    }
    val sheenMoved = t.sheen != shipped.sheen
    val sheen = if (sheenMoved) Color.White.copy(alpha = t.sheen.coerceIn(0f, 1f)) else this.sheen
    return copy(
        recipe = recipe,
        fill = face(fill),
        fillStart = face(fillStart),
        flipFill = face(flipFill),
        flipFillStart = face(flipFillStart),
        rim = if (t.rimBrightness != shipped.rimBrightness) rim.copy(alpha = t.rimBrightness.coerceIn(0f, 1f)) else rim,
        rimDp = pick(t.rimWidthDp, shipped.rimWidthDp, rimDp),
        shadow = if (t.shadow != shipped.shadow) Color.Black.copy(alpha = t.shadow.coerceIn(0f, 1f)) else shadow,
        sheen = sheen,
        flipSheen = if (sheenMoved) sheen else flipSheen,
        rimFalloff = pick(t.rimFocus, shipped.rimFocus, rimFalloff),
        rimFloor = pick(t.rimFloor, shipped.rimFloor, rimFloor),
        lightDeg = pick(t.lightDeg, shipped.lightDeg, lightDeg),
        knee = pick(t.softenKnee, shipped.softenKnee, pass?.get(1) ?: knee),
        kneeSlope = pick(t.softenKeep, shipped.softenKeep, pass?.get(2) ?: kneeSlope),
        dispersion = pick(t.dispersion, shipped.dispersion, dispersion),
        bendShape = pick(t.bendShape, shipped.bendShape, bendShape),
        specular = if (t.specular != shipped.specular) Color.White.copy(alpha = t.specular.coerceIn(0f, 1f)) else specular,
        glintShare = pick(t.specularLine, shipped.specularLine, glintShare),
        specularReachDp = pick(t.specularReachDp, shipped.specularReachDp, specularReachDp),
        innerShadow = pick(t.innerShadow, shipped.innerShadow, innerShadow),
        innerShadowDp = pick(t.innerShadowDp, shipped.innerShadowDp, innerShadowDp),
        resolution = if (recipe.blurDp != r.blurDp) liquidResolutionOf(recipe, platformResolution) else resolution,
    )
}
