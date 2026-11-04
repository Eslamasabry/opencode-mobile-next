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
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
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

/** Decorative monochrome provider or agent mark, with initials while loading or unavailable. */
@Composable
public fun KitProviderLogo(
    id: String,
    name: String,
    agent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val source = LocalKitLogoLoader.current
    var svg by remember(id, agent, source) { mutableStateOf<String?>(null) }
    var drawn by remember(id, agent, source) { mutableStateOf(false) }
    LaunchedEffect(id, agent, source) { svg = source?.svg(id, agent) }
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
    val initials =
        remember(name) {
            name
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { word ->
                    word.take(
                        if (
                            word.length > 1 && word[0].isHighSurrogate() && word[1].isLowSurrogate()
                        )
                            2
                        else 1
                    )
                }
                .uppercase()
        }
    Box(modifier.size(28.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (!drawn) KitText(initials, role = TypeRole.Label, maxLines = 1)
        if (request != null)
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(LocalKitColors.current.text1),
                onSuccess = { drawn = true },
                onError = { drawn = false },
            )
    }
}
