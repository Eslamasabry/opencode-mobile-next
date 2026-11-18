package io.github.eslamasabry.oc.ui.kit.logo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.eslamasabry.oc.ui.kit.platform.isStaticKitSvg
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * Set true by a monochrome or no-colour appearance mode: every agent mark then falls back to its single-colour file in
 * the text colour. The app has no such mode yet; the switch is here so one can flip it in one place.
 */
public val LocalKitMonochromeMarks: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** What to draw for an agent: the picture, and whether it takes the text colour ([tinted]) or keeps its own colours. */
internal class AgentMarkArt(val svg: String, val tinted: Boolean)

/**
 * The brand's colour mark drawn untinted, exactly as shipped, when it has one and colour is allowed; otherwise the
 * monochrome mark tinted in the text colour (OpenCode and Codex are single-colour by design; a missing colour file
 * falls back the same way). A brand's colours are never altered (owner 2026-11-18): on a dark [ground] where none of
 * the mark's own colours reaches [MARK_CONTRAST] (Qwen's #082dff is about 2.6:1 on near-black), the mark is drawn in
 * the ink instead. On a light ground every colour mark is drawn as shipped.
 */
internal fun agentMarkArt(
    color: String?,
    mono: String?,
    monochrome: Boolean,
    dark: Boolean,
    ground: Color = Color.Black,
): AgentMarkArt? =
    when {
        !monochrome && color != null && (!dark || mono == null || markReadsOn(color, ground)) ->
            AgentMarkArt(color, tinted = false)
        mono != null -> AgentMarkArt(mono, tinted = true)
        else -> null
    }

/** The contrast a mark's strongest colour needs against its ground (WCAG's 3:1 for graphics). */
internal const val MARK_CONTRAST: Double = 3.0

/** Whether at least one of [svg]'s own colours (fills and gradient stops) reaches [MARK_CONTRAST] on [ground]. */
internal fun markReadsOn(svg: String, ground: Color): Boolean {
    val colors = MARK_COLOR.findAll(svg).mapNotNull { parseHex(it.groupValues[1]) }.toList()
    if (colors.isEmpty()) return true
    val g = ground.copy(alpha = 1f).luminance().toDouble()
    return colors.any { c ->
        val l = c.luminance().toDouble()
        (maxOf(l, g) + WCAG_FLARE) / (minOf(l, g) + WCAG_FLARE) >= MARK_CONTRAST
    }
}

private val MARK_COLOR = Regex("""(?:fill|stop-color)\s*=\s*"#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})"""")

/** WCAG's flare term in the contrast ratio. */
private const val WCAG_FLARE = 0.05
private const val SHORT_HEX = 3
private const val HEX = 16
private const val OPAQUE = 0xFF000000

private fun parseHex(hex: String): Color? {
    val six = if (hex.length == SHORT_HEX) hex.map { "$it$it" }.joinToString("") else hex
    val v = six.toLongOrNull(HEX) ?: return null
    return Color(OPAQUE or v)
}

/** Loads [id]'s colour mark through the app's loader; null when the brand has none or the file is not a plain SVG. */
public suspend fun KitProviderLogoRules.loadColor(source: KitLogoLoader?, id: String): String? {
    val svg =
        try {
            source?.colorSvg(id)
        } catch (cancel: kotlin.coroutines.cancellation.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    return svg?.takeIf(::isStaticKitSvg)
}

/** The art for an agent mark: both files are read, the colour one wins unless colour is switched off. Null while loading. */
@Composable
internal fun rememberAgentMarkArt(id: String): Pair<AgentMarkArt?, Boolean> {
    val loader = LocalKitLogoLoader.current
    val monochrome = LocalKitMonochromeMarks.current
    val dark = LocalKitColors.current.dark
    val ground = LocalKitColors.current.ground
    var color by remember(id, loader) { mutableStateOf<String?>(null) }
    var mono by remember(id, loader) { mutableStateOf<String?>(null) }
    var looked by remember(id, loader) { mutableStateOf(id.isEmpty()) }
    if (id.isNotEmpty()) LaunchedEffect(id, loader) {
        color = KitProviderLogoRules.loadColor(loader, id)
        mono = KitProviderLogoRules.load(loader, id, agent = true)
        looked = true
    }
    val art = remember(color, mono, monochrome, dark, ground) { agentMarkArt(color, mono, monochrome, dark, ground) }
    return art to looked
}
