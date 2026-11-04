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
