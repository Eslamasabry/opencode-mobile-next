package io.github.eslamasabry.oc.ui.kit.logo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import io.github.eslamasabry.oc.ui.kit.platform.isStaticKitSvg
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The app supplies public logo bytes; the kit never fetches provider URLs or sees account
 * credentials.
 */
public fun interface KitLogoLoader {
    public suspend fun svg(id: String, agent: Boolean): String?

    /** The agent's full-colour mark, or null when its brand has none (the monochrome [svg] then stands in). */
    public suspend fun colorSvg(id: String): String? = null
}

public val LocalKitLogoLoader: ProvidableCompositionLocal<KitLogoLoader?> =
    staticCompositionLocalOf {
        null
    }

/** The rules of Dart `_KitVectorLogo` (`lib/ui/kit/kit_vector_logo.dart`) that do not draw. */
public object KitProviderLogoRules {
    /**
     * The mark to draw, or null (Dart `_start`: a load that fails is no logo, never an error
     * state). A picture that is not a plain static SVG (`isStaticKitSvg`) is no logo either.
     */
    public suspend fun load(source: KitLogoLoader?, id: String, agent: Boolean): String? {
        val svg =
            try {
                source?.svg(id, agent)
            } catch (cancel: kotlin.coroutines.cancellation.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null // F: lib/ui/kit/kit_vector_logo.dart:54-56
            }
        return svg?.takeIf(::isStaticKitSvg)
    }
}

/**
 * Decorative monochrome provider or agent mark. While it loads the slot is held empty at the same
 * size; when there is none it takes no room (C14). No stand-in letter (owner 2026-11-06).
 */
@Composable
public fun KitProviderLogo(
    id: String,
    name: String,
    agent: Boolean = false,
    modifier: Modifier = Modifier,
    /** The mark's own size; a row's tile passes its glyph size so the mark sits inside it like a glyph. */
    markSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val source = LocalKitLogoLoader.current
    var providerSvg by remember(id, agent, source) { mutableStateOf<String?>(null) }
    var providerLooked by remember(id, agent, source) { mutableStateOf(false) }
    if (!agent) LaunchedEffect(id, source) {
        providerSvg = KitProviderLogoRules.load(source, id, agent = false)
        providerLooked = true
    }
    // An agent keeps its brand colours when it has a colour mark; otherwise (and for every provider) the mark takes the text colour.
    val agentResult = if (agent) rememberAgentMarkArt(id) else null
    val art = if (agent) agentResult?.first else providerSvg?.let { AgentMarkArt(it, tinted = true) }
    val svg = art?.svg
    // C14 (audit 2026-11-09): once the source says there is no mark, the slot is given back; a row never keeps an empty
    // leading indent. While the mark loads the slot is held so the row does not jump.
    val missing = art == null && (if (agent) agentResult?.second == true else providerLooked)
    val context = LocalPlatformContext.current
    val request =
        remember(svg, context) {
            svg?.let {
                ImageRequest.Builder(context)
                    .data(it.encodeToByteArray())
                    .decoderFactory(SvgDecoder.Factory())
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
        }
    if (missing) return
    Box(modifier.size(maxOf(28.dp, markSize)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (request != null)
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.size(markSize),
                colorFilter = if (art?.tinted != false) ColorFilter.tint(LocalKitColors.current.text1) else null,
            )
    }
}
