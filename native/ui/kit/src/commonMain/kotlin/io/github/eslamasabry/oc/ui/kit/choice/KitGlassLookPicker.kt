package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidInkRule
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidWords
import io.github.eslamasabry.oc.ui.kit.effects.LiquidFlip
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitGlassLookOverride
import io.github.eslamasabry.oc.ui.kit.effects.kitFrostPane
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidPlateHost
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.channels.Channel
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer

/*
 * The Glass look picker's parts (docs/designs/glass-looks-2026-11-09.md v2, §4-§5; D1 B: a 2×2 grid; D2 B: colour plus
 * drifting lines of text behind the previews, with the disclosure line). One field ([KitLookField]) is recorded once
 * per field step into one half-resolution layer; every preview ([KitGlassLookPreview]) draws its own look over that one
 * recording, through the per-pane look override ([LocalKitGlassLookOverride]) and never through the page's own look.
 *
 * The previews' own budget: at most [LookFieldPolicy.MAX_PREVIEWS] panes, outside the window's liquid budget (they are
 * in a sheet; nothing on the page drops to frost for them). The field steps at most 60 times a second (45 on a 90 Hz
 * panel: the panel's rate divided by the smallest whole number that brings it to 60 or below), rests 30 s after the
 * last touch, stops when the picker leaves the screen (its frame loop ends with it; a stopped app sends no frames), and
 * is a still picture under reduced motion.
 */

/** The field's numbers. */
public object LookFieldPolicy {
    /** The most the field steps per second. */
    public const val MAX_FPS: Float = 60f

    /** The field rests this long after the last touch, until the next one. */
    public const val IDLE_AFTER_NANOS: Long = 30_000_000_000L

    /** The most previews one field feeds (the picker's own budget). */
    public const val MAX_PREVIEWS: Int = 4

    /** The most a preview's face may hold: light milk, dark glass, and Smoked's smoke (always over the field). */
    public fun faceCap(smoked: Boolean, dark: Boolean, lens: Boolean = true): Float = when {
        smoked -> .62f
        !lens -> if (dark) .50f else .46f // Frosted: milky
        else -> if (dark) .34f else .26f // Glossy: clear glass, the field shows through
    }

    /** A lens look's blur over the field (Glossy, Smoked): light, so the text behind shows and visibly bends. */
    public const val LENS_BLUR_DP: Float = 2.5f

    /** The field is recorded at this share of its size on each side. */
    public const val RECORD_SCALE: Float = .5f

    /** The drifting lines' ink, as a share of the theme's text colour: faint, like the mockup's code behind the glass. */
    public const val INK_ALPHA: Float = .34f

    /** The code drifting behind the previews (the mockup's): any text with long and short lines does. */
    internal val CODE: List<String> =
        listOf(
            "val sunset = Palette(ground = Color(0xFF0E1220)),",
            "    accent = Color(0xFF3DDC97), glow = .42f)",
            "fun Modifier.glass(look: GlassLook) = this",
            "    .blur(4.dp).lens(24.dp, 48.dp)",
            "@Composable fun Preview() { KitTheme { Chat() } }",
            "  partner = Color(0xFFFFC857), // late sun gold",
            "when (look) { Glossy -> shine(); else -> frost() }",
            "  val face = tint.copy(alpha = .22f)",
            "LaunchedEffect(Unit) { field.start() }",
            "  rows.forEach { row -> Card(row) }",
        )

    /** A look without a lens (Frosted) blurs its preview's field this many times its role's blur (glass looks §3). */
    public const val FROSTED_BLUR_FACTOR: Float = 2f

    /**
     * How many display frames one field step lasts at [panelHz]: the smallest whole number that brings the rate to
     * [MAX_FPS] or below (60 and 120 Hz: 60 fps; 90 Hz: 45; 144 Hz: 48).
     */
    public fun divisor(panelHz: Float): Int {
        if (panelHz <= 0f) return 1
        var n = 1
        while (panelHz / n > MAX_FPS + .5f) n++
        return n
    }
}

/**
 * The field's clock, plain code driven by frame times so tests run it without a display: [frame] says whether the
 * field steps on this display frame. It reads the panel's rate from the frames themselves, keeps every [LookFieldPolicy
 * .divisor]-th one, and rests [LookFieldPolicy.IDLE_AFTER_NANOS] after the last [touch].
 */
public class LookFieldClock {
    /** Field time in seconds. */
    public var seconds: Float = 0f
        private set

    /** True after the idle timeout until the next [touch]. */
    public var resting: Boolean = false
        private set

    private var last = NONE
    private var lastTouch = NONE
    private var interval = 0.0
    private var skipped = 0

    /** A touch on the picker: wakes a resting field and restarts the idle countdown. */
    public fun touch() {
        lastTouch = NONE
        resting = false
        last = NONE
    }

    /** One display frame at [nanos]; true when the field steps. */
    public fun frame(nanos: Long): Boolean {
        if (lastTouch == NONE) lastTouch = nanos
        if (nanos - lastTouch >= LookFieldPolicy.IDLE_AFTER_NANOS) {
            resting = true
            last = NONE
            return false
        }
        if (last == NONE) {
            last = nanos
            skipped = 0
            return true
        }
        val dt = nanos - last
        if (dt <= 0) return false
        // The panel's frame interval, smoothed (a dropped frame barely moves it).
        val gap = (dt.toDouble() / (skipped + 1)).coerceAtMost(MAX_GAP)
        interval = if (interval == 0.0) gap else interval * .9 + gap * .1
        val hz = (1e9 / interval).toFloat()
        val n = LookFieldPolicy.divisor(hz)
        skipped++
        if (skipped < n) return false
        seconds += (minOf(dt, MAX_STEP_NANOS) / 1e9).toFloat()
        last = nanos
        skipped = 0
        return true
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val MAX_STEP_NANOS = 100_000_000L
        const val MAX_GAP = 50_000_000.0
    }
}

/** One field and the previews over it: their shared recording. */
@Stable
public class KitLookFieldState internal constructor() {
    internal val clock = LookFieldClock()
    internal var layer: GraphicsLayer? = null
    internal var coordinates: LayoutCoordinates? = null
    internal val wakes = Channel<Unit>(Channel.CONFLATED)

    /** Bumps once per field step: the previews draw again only then. Snapshot state. */
    internal var version: Int by mutableIntStateOf(0)

    /** True while the field moves (not closed, not resting, not still under reduced motion). */
    public var moving: Boolean by mutableStateOf(false)
        internal set

    /** Field time, read in the draw. */
    internal var seconds: Float by mutableStateOf(0f)

    /** A touch on the picker wakes a resting field. */
    public fun touch() {
        clock.touch()
        wakes.trySend(Unit)
    }
}

@Composable
public fun rememberKitLookFieldState(): KitLookFieldState = remember { KitLookFieldState() }

/** The field's colours: bright on purpose, so the looks' differences show (the disclosure line says so). */
internal object LookFieldPalette {
    private val hues = listOf(0xFF3DDC97.toInt(), 0xFF8B6CF6.toInt(), 0xFFF2A65A.toInt(), 0xFF3FA7F5.toInt())

    /** The blobs' colours for a page of [ground] (dark: lifted towards the hue; light: dyed). */
    fun blobs(ground: Argb, dark: Boolean): List<Argb> = hues.map { lerp(ground, Argb(it), if (dark) .72f else .80f) }

    /** Every colour a preview can find behind it, for its face's measure (the ground and each blob at its peak). */
    fun spots(ground: Argb, dark: Boolean): List<Argb> = listOf(ground) + blobs(ground, dark)
}

/**
 * Draws the field at [t] seconds over [area] (the field's own size, whatever the scope it is drawn in: the recording is
 * smaller): four soft colour blobs drifting over the ground, and faint lines of text drifting up.
 */
internal fun DrawScope.drawLookField(t: Float, ground: Color, blobs: List<Color>, ink: Color, area: Size, lines: List<TextLayoutResult> = emptyList()) {
    val w = area.width
    val h = area.height
    drawRect(ground, Offset.Zero, area)
    // Colour edge to edge (the mockup's field): a glow of the hues in every corner under the drifting blobs, so no corner
    // shows the bare page ground (a white corner under Smoked's dark sheet).
    if (blobs.size >= 4) {
        // One soft glow per corner (radial, like the blobs; a linear wash crashed the emulator's renderer).
        val corners = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
        val r = maxOf(w, h) * .95f
        corners.forEachIndexed { i, o ->
            val c = blobs[(i + 1) % blobs.size]
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), o, r), r, o)
        }
    }
    blobs.forEachIndexed { i, c ->
        val p = i * 1.7f
        val cx = w * (.5f + .38f * sin(t * .21f + p).toFloat())
        val cy = h * (.5f + .36f * cos(t * .17f + p * 1.3f).toFloat())
        val r = maxOf(w, h) * .42f
        drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
    }
    // Faint lines of code drifting up (the mockup's): they show the blur and the bend at the rim.
    if (lines.isEmpty()) return
    val pitch = lines.first().size.height * 1.45f
    val drift = (t * 9.dp.toPx()) % (pitch * lines.size)
    var y = -drift
    var i = 0
    while (y < h + pitch) {
        if (y > -pitch) drawText(lines[i % lines.size], ink, Offset(-((i * 37) % 23).dp.toPx(), y))
        y += pitch
        i++
    }
}

/**
 * The moving field behind the previews, recorded once per step into one half-resolution layer the previews share. It
 * draws [content] (the previews) over it and is hidden from TalkBack. [still] holds one still picture (reduced motion).
 */
@Composable
public fun KitLookField(
    state: KitLookFieldState,
    modifier: Modifier = Modifier,
    still: Boolean = LocalKitMotion.current.let { it.reduced || it.stillFrame },
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val layer = rememberGraphicsLayer()
    state.layer = layer
    val ground = colors.ground.copy(alpha = 1f)
    val blobs = remember(roles.ground, roles.dark) { LookFieldPalette.blobs(roles.ground, roles.dark).map { Color(it.v) } }
    // Faint: the lines show the blur and the bend, never compete with the previews' words.
    val ink = colors.text1.copy(alpha = LookFieldPolicy.INK_ALPHA)
    val measurer = rememberTextMeasurer()
    val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
    val lines = remember(measurer) { LookFieldPolicy.CODE.map { measurer.measure(it, codeStyle, softWrap = false, maxLines = 1) } }
    LaunchedEffect(state, still) {
        if (still) {
            state.moving = false
            state.version++
            return@LaunchedEffect
        }
        state.clock.touch()
        try {
            while (true) {
                if (state.clock.resting) {
                    state.moving = false
                    state.wakes.receive()
                    continue
                }
                state.moving = true
                val stepped = withFrameNanos { state.clock.frame(it) }
                if (stepped) {
                    state.seconds = state.clock.seconds
                    state.version++
                }
            }
        } finally {
            state.moving = false
        }
    }
    Box(
        modifier
            .onGloballyPositioned { state.coordinates = it }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        state.touch()
                    }
                }
            }
    ) {
        Box(
            Modifier.matchParentSize()
                .clearAndSetSemantics {}
                .testTag("kit-look-field")
                .drawBehind {
                    // Read here: a step records the field again and redraws only this layer and the previews.
                    state.version
                    val t = state.seconds
                    val k = LookFieldPolicy.RECORD_SCALE
                    val area = size
                    val small = IntSize((area.width * k).toInt().coerceAtLeast(1), (area.height * k).toInt().coerceAtLeast(1))
                    // The recording's scope is `small`: the field is drawn over its own full size, scaled down into it
                    // (it once filled only the recording's size, scaled down again: a quarter of the field).
                    layer.kitRecord(this, layoutDirection, small) {
                        scale(k, k, Offset.Zero) { drawLookField(t, ground, blobs, ink, area, lines) }
                    }
                    scale(1f / k, 1f / k, Offset.Zero) { kitDrawLayer(layer) }
                }
        )
        content()
    }
}

/** The field seen from a preview at [at]: the shared recording, at the preview's place. */
private fun fieldBackdrop(state: KitLookFieldState): KitLiquidBackdrop =
    KitLiquidBackdrop({ state.version }) { at ->
        state.version
        val layer = state.layer ?: return@KitLiquidBackdrop
        val field = state.coordinates?.takeIf { it.isAttached } ?: return@KitLiquidBackdrop
        val shift = at.localPositionOf(field, Offset.Zero)
        translate(shift.x, shift.y) {
            scale(1f / LookFieldPolicy.RECORD_SCALE, 1f / LookFieldPolicy.RECORD_SCALE, Offset.Zero) { kitDrawLayer(layer) }
        }
    }

/** The field's shape: one rounded panel behind every preview. */
private val FieldShape: CornerBasedShape = RoundedCornerShape(28.dp)

/** A preview's shape: the kit's card corner. */
private val PreviewShape: CornerBasedShape = RoundedCornerShape(22.dp)

/** The liquid role whose numbers a preview draws: the message box's (a pane carrying words over moving text). */
private val PREVIEW_ROLE: LiquidRole = LiquidRole.Composer

/**
 * One look's preview: a pane of that look's glass over the field, its name and line on the glass, a radio with the
 * chosen ring and a check on the selected one. The look applies to this pane only; the page keeps the person's.
 */
@Composable
public fun KitGlassLookPreview(
    look: GlassLookId,
    name: String,
    line: String,
    selected: Boolean,
    onSelect: () -> Unit,
    field: KitLookFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val preset = GlassLooks.of(look)
    val roles = LocalKitRoles.current
    val appearance = LocalKitAppearance.current
    val colors = LocalKitColors.current
    val platform = LocalKitPlatform.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val backdrop = remember(field) { fieldBackdrop(field) }
    val other = remember(roles, appearance.pack, appearance.accent) {
        ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null)
    }
    val smoke = if (preset.smoked && !roles.dark) other else roles
    val spec =
        remember(roles, smoke, preset, rtl) {
            val picture = LookFieldPalette.spots(roles.ground, roles.dark)
            val face = LiquidEdge.face(smoke, PREVIEW_ROLE, picture = picture, look = preset, page = roles)
            // A preview is glass, never a solid plate: the face may thicken for its words only up to the look's cap
            // (measured over the field's brightest spot it would fall to the solid surface, the owner's "black block").
            val cap = LookFieldPolicy.faceCap(preset.smoked, smoke.dark, preset.lens)
            val fill = face.fill?.let { Color(it.v) }?.let { it.copy(alpha = minOf(it.alpha, cap)) } ?: Color.Transparent
            val dark = smoke.dark
            KitLiquidPaneSpec(
                // A lens look keeps the field readable under it, so the bend at the rim shows (Kyant's guide: ~4 dp).
                recipe = LiquidEdge.recipe(PREVIEW_ROLE, dark, preset).let { if (preset.lens) it.copy(blurDp = LookFieldPolicy.LENS_BLUR_DP) else it },
                fill = fill,
                fillStart = face.fillStart?.let { Color(it.v) }?.let { it.copy(alpha = minOf(it.alpha, cap + .06f)) } ?: fill,
                rtl = rtl,
                rim = Color.White.copy(alpha = if (dark) preset.spec.liquidRimDark else preset.spec.liquidRimLight),
                rimAdds = dark,
                rimDp = 1f,
                shadow = Color.Transparent,
                // A look without a lens is milky frost: its role's blur, twice over (glass looks §3, "Frosted ×2").
                frostBlurDp = LiquidEdge.recipe(PREVIEW_ROLE, dark, preset).blurDp * LookFieldPolicy.FROSTED_BLUR_FACTOR,
                sheen = Color(face.sheen.v),
                specular = Color.White.copy(alpha = if (dark) preset.spec.liquidSpecularDark else preset.spec.liquidSpecularLight),
                keepRim = true,
                glintShare = preset.spec.glintShare,
            )
        }
    val lens = preset.lens
    val glass =
        if (platform.liquidGlass && lens) {
            platform.liquidPane(spec, backdrop, PreviewShape) { 0f }
        } else {
            // A look without a lens, or no lens on this platform: the field blurred under the look's face, as the
            // Frosted look's panels draw the page (the same kit frost).
            Modifier.kitFrostPane(spec, backdrop, PreviewShape, if (lens) spec.recipe.blurDp else spec.frostBlurDp, preset.spec.frostSaturation)
        }
    // The words on the pane: the theme's ink, or Smoked's dark-theme ink in a light theme. Never sampled: the face is
    // measured over every colour of the field.
    val words =
        remember(other, smoke, roles) {
            KitLiquidWords(
                flipInk = Color(other.text1.v),
                themeInk = LiquidInkRule.of(roles.dark),
                backdrop = null,
                flip = LiquidFlip(),
                fixedFlip = if (smoke !== roles) 1f else 0f,
            )
        }
    KitTappable(
        onClick = onSelect,
        modifier = modifier.heightIn(min = 148.dp).radioSemantics(name, line, selected),
        enabled = enabled,
        role = KitTappableRole.Button,
        haptic = KitHaptic.Select,
        shape = KitShapeId.Card,
        pressClip = PreviewShape,
    ) {
        Box(
            Modifier.matchParentSize()
                .then(glass)
                .then(if (selected) Modifier.chosenRing(colors.accent) else Modifier)
                .testTag("kit-glass-look-${look.storageName}")
        )
        CompositionLocalProvider(LocalKitGlassLookOverride provides preset) {
            Column(
                Modifier.fillMaxSize().kitLiquidPlateHost(words).padding(Space.S4.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitText(name, Modifier.weight(1f), role = TypeRole.RowTitle, maxLines = 2)
                    if (selected) ChosenCheck(colors.accent, colors.onAccent)
                }
                Spacer(Modifier.height(Space.S1.dp))
                KitText(line, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 3)
            }
        }
    }
}

/**
 * The chosen preview's check: a filled accent disc with the check in the accent's ink, so it reads over any colour of
 * the field behind the glass (a bare accent check vanished over the field's own accent-coloured blob).
 */
@Composable
private fun ChosenCheck(accent: Color, onAccent: Color) {
    Box(
        Modifier.size(Space.S6.dp).drawBehind { drawCircle(accent) }.testTag("kit-glass-look-check"),
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(KitIcons.check, onAccent, Space.S4.dp)
    }
}

/** The chosen preview's accent ring, inside its edge. */
private fun Modifier.chosenRing(accent: Color): Modifier =
    drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        val r = 22.dp.toPx()
        drawRoundRect(accent, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), CornerRadius(r - w / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }

private fun Modifier.radioSemantics(name: String, line: String, selected: Boolean): Modifier =
    semantics {
        role = Role.RadioButton
        this.selected = selected
        contentDescription = "$name. $line"
    }

/** One look as the picker lists it. */
public data class KitGlassLookOption(val look: GlassLookId, val name: String, val line: String)

/**
 * The Glass look picker's body (D1 B): the looks' previews two to a row over one shared moving field, then the
 * disclosure line. A tap applies the look at once (the page behind the sheet is the real result).
 */
@Composable
public fun KitGlassLookPicker(
    options: List<KitGlassLookOption>,
    selected: GlassLookId,
    onSelect: (GlassLookId) -> Unit,
    disclosure: String,
    modifier: Modifier = Modifier,
    field: KitLookFieldState = rememberKitLookFieldState(),
) {
    val shown = options.take(LookFieldPolicy.MAX_PREVIEWS)
    Column(modifier.fillMaxWidth()) {
        KitLookField(field, Modifier.fillMaxWidth().clip(FieldShape)) {
            Column(Modifier.fillMaxWidth().padding(Space.S3.dp), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                // Two to a row; an odd last look takes the whole row, so the field never shows an empty cell.
                shown.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                        pair.forEach { o ->
                            KitGlassLookPreview(
                                look = o.look,
                                name = o.name,
                                line = o.line,
                                selected = o.look == selected,
                                onSelect = { onSelect(o.look) },
                                field = field,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        KitGroupNote(disclosure)
    }
}
