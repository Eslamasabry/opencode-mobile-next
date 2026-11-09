package io.github.eslamasabry.oc.ui.kit.logo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
 * The brand's colour mark drawn untinted when it has one and colour is allowed; otherwise the monochrome mark tinted in
 * the text colour (OpenCode and Codex are single-colour by design; a missing colour file falls back the same way).
 */
internal fun agentMarkArt(color: String?, mono: String?, monochrome: Boolean, dark: Boolean): AgentMarkArt? =
    when {
        !monochrome && color != null -> AgentMarkArt(forGround(color, dark), tinted = false)
        mono != null -> AgentMarkArt(mono, tinted = true)
        else -> null
    }

/**
 * A brand colour too dark for a dark ground is lifted to a lighter tint of the same hue (Qwen's #082dff is about 2.4:1
 * on near-black); on a light ground every colour mark reads as shipped.
 */
internal fun forGround(svg: String, dark: Boolean): String =
    if (dark) svg.replace("#082dff", "#7a90ff", ignoreCase = true) else svg

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
    var color by remember(id, loader) { mutableStateOf<String?>(null) }
    var mono by remember(id, loader) { mutableStateOf<String?>(null) }
    var looked by remember(id, loader) { mutableStateOf(id.isEmpty()) }
    if (id.isNotEmpty()) LaunchedEffect(id, loader) {
        color = KitProviderLogoRules.loadColor(loader, id)
        mono = KitProviderLogoRules.load(loader, id, agent = true)
        looked = true
    }
    val art = remember(color, mono, monochrome, dark) { agentMarkArt(color, mono, monochrome, dark) }
    return art to looked
}
