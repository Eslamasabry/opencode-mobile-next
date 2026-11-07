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
 * Decorative monochrome provider or agent mark. While it loads, or when there is none, the slot stays empty at the same
 * size: no stand-in letter (owner 2026-11-06), so rows keep one edge and never show a stray glyph.
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
    var svg by remember(id, agent, source) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, agent, source) { svg = KitProviderLogoRules.load(source, id, agent) }
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
    Box(modifier.size(maxOf(28.dp, markSize)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (request != null)
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.size(markSize),
                colorFilter = ColorFilter.tint(LocalKitColors.current.text1),
            )
    }
}
