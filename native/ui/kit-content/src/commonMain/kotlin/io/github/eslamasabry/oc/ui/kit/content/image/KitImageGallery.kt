package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitGalleryEdit
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoading
import io.github.eslamasabry.oc.ui.words.res.kitGalleryPictureLabel
import io.github.eslamasabry.oc.ui.words.res.kitGalleryPosition
import io.github.eslamasabry.oc.ui.words.res.kitGallerySend
import io.github.eslamasabry.oc.ui.words.res.kitGalleryTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * One picture of a [KitImageGallery] (Dart `KitGalleryPicture`). [load] is called lazily, only for the picture shown
 * and its neighbours, and again after "Try again"; it returns the encoded bytes, or null when the picture cannot be read.
 */
@Immutable
public class KitGalleryPicture(
    public val id: String,
    public val name: String,
    public val load: suspend () -> ByteArray?,
)

/**
 * Puts a picture in the host's message field (Dart `KitGallerySend`). [edited] says the bytes are the person's markup
 * (a PNG named `…-edited.png`). True when the picture was added, which closes the gallery; false keeps it open.
 */
public typealias KitGallerySend = suspend (bytes: ByteArray, name: String, edited: Boolean) -> Boolean

/** Pure rules of [KitImageGallery] (lib/ui/kit/kit_image_gallery.dart), tested without Compose. */
public object GalleryRules {
    /** The shown index, kept inside the list (Dart `initialIndex.clamp(0, length - 1)`). */
    public fun clampIndex(index: Int, count: Int): Int = index.coerceIn(0, maxOf(0, count - 1))

    /** The pictures read for [index]: it and the two beside it, no others (Dart `_prefetch`). */
    public fun prefetch(index: Int, count: Int): List<Int> = listOf(index - 1, index, index + 1).filter { it in 0 until count }

    /** "screen.png" → "screen-edited.png"; a name without a dot keeps it whole (Dart `_editedName`). */
    public fun editedName(name: String): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        return "$stem-edited.png"
    }

    /** The bar's "2 of 3" only when there is more than one picture. */
    public fun showsPosition(count: Int): Boolean = count > 1
}

/** Test tags of the gallery (Dart value keys). */
public object KitGalleryTags {
    public const val EDIT: String = "kit-gallery-edit"
    public const val SEND: String = "kit-gallery-send"
    public const val PAGER: String = "kit-gallery-pager"
    public const val IMAGE: String = "kit-gallery-image"
}

private sealed interface Shown {
    data object Loading : Shown
    data object Failed : Shown
    class Ready(val bytes: ByteArray, val image: ImageBitmap) : Shown
}

@OptIn(ExperimentalResourceApi::class)
private fun decodeOrNull(bytes: ByteArray?): ImageBitmap? =
    if (bytes == null || bytes.isEmpty()) null
    else try {
        bytes.decodeToImageBitmap()
    } catch (_: RuntimeException) { // an unreadable picture is the error state, never a crash
        null
    }

/**
 * The pictures of a conversation, one swipe apart, in the order they were shown (Dart `KitImageGallery`,
 * docs/ux-system/kit-api/KitImageGallery.md). Each picture pinches and pans like [KitZoomView], with the zoom buttons
 * as the visible path; a swipe changes picture only while the shown one is not zoomed in. Edit opens [KitImageMarkup]
 * in place; Send hands the (edited) picture to [onSend] and closes. The host shows it as a full-screen page (Dart
 * `showKitImageGallery`).
 *
 * States: loading, error, disabled, working
 * Text: frame (bar, buttons), reading (the error state)
 * Cap: Fill
 * Motion: the pager's own settle; the picture fades in on `quick` (KitImage); Off = instant
 * Hidden: swipe and pinch; the bar's position, the zoom buttons and Close are the visible paths
 * Replaces: KitImageGallery, showKitImageGallery (lib/ui/kit/kit_image_gallery.dart)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun KitImageGallery(
    pictures: List<KitGalleryPicture>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    onSend: KitGallerySend? = null,
) {
    require(pictures.isNotEmpty()) { "KitImageGallery needs a picture" } // F: lib/ui/kit/kit_image_gallery.dart:83
    val count = pictures.size
    val pager = rememberPagerState(GalleryRules.clampIndex(initialIndex, count)) { count }
    val index = pager.currentPage.coerceIn(0, count - 1)
    val loads = remember(pictures) { mutableStateMapOf<Int, Shown>() }
    var generation by remember { mutableIntStateOf(0) }
    val edited = remember(pictures) { mutableStateMapOf<String, ByteArray>() }
    val zoomed = remember(pictures) { mutableStateMapOf<Int, Boolean>() }
    var sending by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pictures, index, generation) {
        for (n in GalleryRules.prefetch(index, count)) {
            if (loads[n] != null) continue
            loads[n] = Shown.Loading
            launch {
                val bytes = try {
                    pictures[n].load()
                } catch (e: CancellationException) {
                    loads.remove(n)
                    throw e
                } catch (_: Exception) { // a failed read is the error state with Try again
                    null
                }
                val image = decodeOrNull(bytes)
                loads[n] = if (bytes != null && image != null) Shown.Ready(bytes, image) else Shown.Failed
            }
        }
    }

    val picture = pictures[index]
    val shown = loads[index]
    val ready = shown is Shown.Ready || edited.containsKey(picture.id)
    fun bytesOf(i: Int): ByteArray? = edited[pictures[i].id] ?: (loads[i] as? Shown.Ready)?.bytes
    fun send() {
        val handler = onSend ?: return
        if (sending) return
        val item = pictures[index]
        val changed = edited[item.id]
        val bytes = bytesOf(index) ?: return
        sending = true
        scope.launch {
            val added = try {
                handler(bytes, if (changed == null) item.name else GalleryRules.editedName(item.name), changed != null)
            } finally {
                sending = false
            }
            if (added) onClose()
        }
    }

    editing?.let { original ->
        KitImageMarkup(
            bytes = original,
            name = picture.name,
            canSend = onSend != null,
            modifier = modifier,
            onResult = { result ->
                editing = null
                if (result != null) {
                    if (result.changed) edited[picture.id] = result.bytes
                    if (result.send) send()
                }
            },
        )
        return
    }

    val editLabel = stringResource(Res.string.kitGalleryEdit)
    val sendLabel = stringResource(Res.string.kitGallerySend)
    KitScreen(
        modifier = modifier,
        page = true,
        topBar = {
            KitTopBar(
                title = KitBidi.auto(picture.name),
                subtitle = if (GalleryRules.showsPosition(count)) stringResource(Res.string.kitGalleryPosition, index + 1, count) else null,
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitTopBarClose),
                onExit = onClose,
                actions = listOf(
                    KitLineAction(
                        label = editLabel,
                        onClick = { editing = bytesOf(index) },
                        enabled = ready,
                        disabledReason = if (ready) null else editLabel,
                        testTag = KitGalleryTags.EDIT,
                        icon = KitIcons.edit,
                    ),
                ),
            )
        },
        bottom = if (onSend == null) null else {
            {
                // From medium up the block is one row at the end of its 720 dp reading cap (KitActionBlock's row wraps
                // its content, so the cap's end is set here).
                Box(
                    Modifier.fillMaxWidth().padding(Space.S4.dp).kitMaxWidth(CapRole.Reading), // F: lib/ui/kit/kit_image_gallery.dart:286
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    KitActionBlock(
                        primary = KitAction(
                            label = sendLabel,
                            onClick = if (ready) ::send else null,
                            icon = KitIcons.send,
                            working = sending,
                        ),
                    )
                }
            }
        },
    ) {
        val pictureLabel = stringResource(Res.string.kitGalleryPictureLabel, index + 1, count, picture.name)
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().testTag(KitGalleryTags.PAGER).semantics { contentDescription = pictureLabel },
            userScrollEnabled = zoomed[index] != true,
            key = { pictures[it].id },
        ) { page ->
            val item = pictures[page]
            val own = edited[item.id]
            when (val state = if (own != null) decodeOrNull(own)?.let { Shown.Ready(own, it) } ?: Shown.Failed else loads[page]) {
                is Shown.Ready -> GalleryPicture(item, state.image) { zoomed[page] = it }
                Shown.Failed ->
                    KitStateView(
                        kind = KitStateKind.Error,
                        title = stringResource(Res.string.kitGalleryLoadFailed),
                        body = stringResource(Res.string.kitGalleryLoadFailedBody),
                        icon = KitIcons.imageBroken,
                        primary = KitAction(
                            label = stringResource(Res.string.kitGalleryTryAgain),
                            onClick = {
                                loads.remove(page)
                                generation++
                            },
                            icon = KitIcons.retry,
                        ),
                    )
                else -> {
                    val loading = stringResource(Res.string.kitGalleryLoading)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KitTaskMark(KitTaskState.Working, label = loading)
                    }
                }
            }
        }
    }
}

/** One loaded page: the picture fitted and centred, zoomable, its zoom state told to the pager. */
@Composable
private fun GalleryPicture(picture: KitGalleryPicture, image: ImageBitmap, onZoom: (Boolean) -> Unit) {
    val zoom = rememberKitZoomState()
    LaunchedEffect(zoom) { snapshotFlow { !zoom.isFitted }.collect(onZoom) }
    KitZoomView(picture.name, Modifier.fillMaxSize(), zoom) {
        // Flutter's `Center(KitImage)`: the picture at its own size in logical pixels, scaled down (never up) to fit.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fit = minOf(1f, maxWidth.value / image.width, maxHeight.value / image.height)
            Image(
                image,
                picture.name,
                Modifier.size((image.width * fit).dp, (image.height * fit).dp).testTag(KitGalleryTags.IMAGE),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

