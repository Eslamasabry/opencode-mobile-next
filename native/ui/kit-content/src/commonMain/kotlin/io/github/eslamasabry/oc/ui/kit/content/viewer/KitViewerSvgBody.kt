package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageView
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoom
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomControls
import io.github.eslamasabry.oc.ui.kit.content.image.rememberKitZoomState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitViewerCantShow
import org.jetbrains.compose.resources.stringResource

/**
 * A static SVG already validated by the media engine. Bytes only: no remote loads or external
 * references.
 */
@Composable
public fun KitViewerSvgBody(safeSource: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val loader =
        remember(context) {
            ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
        }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    var failed by remember(safeSource) { mutableStateOf(false) }
    val bytes = remember(safeSource) { safeSource.encodeToByteArray() }
    if (failed) {
        KitText(stringResource(Res.string.kitViewerCantShow), modifier)
        return
    }
    val zoom = rememberKitZoomState()
    Column(modifier.fillMaxSize()) {
        KitZoom(zoom, Modifier.fillMaxWidth().weight(1f)) {
            AsyncImage(
                model = bytes,
                imageLoader = loader,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        }
        KitZoomControls(zoom, Modifier.fillMaxWidth())
    }
}

/**
 * PORT-SCREENS-2 overload (with an [unreadable] fallback). Only sanitized static SVG from the host's bounded parser. This loader has no network fetcher. */
@Composable
public fun KitViewerSvgBody(
    source: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    unreadable: @Composable () -> Unit,
) {
    val context = LocalPlatformContext.current
    val loader =
        remember(context) {
            ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
        }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val bytes = remember(source) { source.encodeToByteArray() }
    val request =
        remember(bytes, context) {
            ImageRequest.Builder(context).data(bytes).size(1440, 1440).build()
        }
    val painter = rememberAsyncImagePainter(request, loader)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) unreadable()
    else KitImageView(painter, contentDescription, modifier)
}

/**
 * A picture from a sanitised SVG in the viewer (Dart `KitZoom` over a centred, space4-padded `SvgPicture.string`): drawn
 * at its view box's size, as flutter_svg sizes it, and "Can't show this file" when it does not decode.
 */
@Composable
internal fun ViewerSvg(safeSource: String, name: String) {
    val context = LocalPlatformContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val bytes = remember(safeSource) { safeSource.encodeToByteArray() }
    val box = remember(safeSource) { viewBoxOf(safeSource) }
    var failed by remember(safeSource) { mutableStateOf(false) }
    io.github.eslamasabry.oc.ui.kit.content.image.KitZoomView(name, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(Modifier.padding(io.github.eslamasabry.oc.ui.design.tokens.Space.S4.dp)) {
                if (failed) {
                    KitText(stringResource(Res.string.kitViewerCantShow), tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Tertiary)
                } else {
                    AsyncImage(
                        model = bytes,
                        imageLoader = loader,
                        contentDescription = name,
                        modifier = if (box != null) Modifier.size(box.first.dp, box.second.dp) else Modifier,
                        contentScale = ContentScale.Fit,
                        onError = { failed = true },
                    )
                }
            }
        }
    }
}

/** The `viewBox` width and height of an SVG's root element, or null. */
internal fun viewBoxOf(svg: String): Pair<Float, Float>? {
    val box = Regex("viewBox\\s*=\\s*\"([^\"]*)\"").find(svg)?.groupValues?.get(1) ?: return null
    val parts = box.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
    return if (parts.size == 4) parts[2] to parts[3] else null // NV: viewBox is min-x, min-y, width, height
}
