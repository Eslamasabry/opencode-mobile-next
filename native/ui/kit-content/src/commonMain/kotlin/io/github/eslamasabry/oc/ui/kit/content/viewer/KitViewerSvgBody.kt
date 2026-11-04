package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageView

/** Only sanitized static SVG from the host's bounded parser. This loader has no network fetcher. */
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
