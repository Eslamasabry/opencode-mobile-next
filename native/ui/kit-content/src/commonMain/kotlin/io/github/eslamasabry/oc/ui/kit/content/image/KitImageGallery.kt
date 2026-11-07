package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.animation.core.snap
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
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
import io.github.eslamasabry.oc.ui.words.res.kitTopBarMore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * One picture of a [KitImageGallery] (Dart `KitGalleryPicture`). [load] is called lazily, only for the picture shown
 * and its neighbours, and again after "Try again"; it returns the picture's bytes, or null when it cannot be read.
 * [id] is stable within one gallery (an edit is remembered under it); [name] is the file's name, as written.
 */
@Immutable
public class KitGalleryPicture(
    public val id: String,
    public val name: String,
    public val load: suspend () -> ByteArray?,
)

/**
 * Puts a picture in the host's message field (Dart `KitGallerySend`). [edited] says the bytes are the person's markup (a
 * PNG named `…-edited.png`). Returns true when the picture was added, which closes the gallery; false keeps it open
 * (the host has already said why).
 */
public typealias KitGallerySend = suspend (bytes: ByteArray, name: String, edited: Boolean) -> Boolean

/** Where one picture's read stands. A new read is a new [Reading], so a late answer to a retried read is ignored. */
private sealed interface PictureLoad {
    class Reading : PictureLoad

    data object Failed : PictureLoad

    class Ready(val bytes: ByteArray) : PictureLoad
}

/** Dart `_editedName`: `shot.png` → `shot-edited.png`; a name without a dot (or starting with one) keeps all of it. */
internal fun galleryEditedName(name: String): String {
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    return "$stem-edited.png"
}

/** Dart `_prefetch`: the shown picture and the two beside it, no others. */
internal fun galleryPrefetch(index: Int, count: Int): List<Int> = listOf(index - 1, index, index + 1).filter { it in 0 until count }

/** Dart `_GalleryPageState._changed`: a picture counts as zoomed in past this scale (pages stop swiping). */
internal const val GALLERY_ZOOMED_SCALE: Float = 1.01f // F: lib/ui/kit/kit_image_gallery.dart:338

/**
 * Shows [KitImageGallery] full screen over [content] while [open] (Dart `showKitImageGallery`, a full-screen page).
 * Escape and Close call [onDismissRequest] (system Back is the host page's, as Flutter's route); a picture the host
 * took ([onSend] true) also does.
 */
@Composable
public fun KitImageGalleryHost(
    open: Boolean,
    pictures: List<KitGalleryPicture>,
    onDismissRequest: () -> Unit,
    encodePng: KitPngEncoder,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    onSend: KitGallerySend? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (open) {
            KitImageGallery(pictures, onDismissRequest, encodePng, Modifier.fillMaxSize(), initialIndex, onSend)
        }
    }
}

/**
 * The pictures of a conversation, one swipe apart, in the order they were shown (docs/ux-system/kit-api/
 * KitImageGallery.md). Each picture pinches and pans like [KitZoomView]; a swipe changes picture only while the shown
 * one is not zoomed in. Edit opens [KitImageMarkup]; Send hands the (edited) picture to [onSend] and closes through
 * [onClose]. Arrow keys on a PC step to the picture on that side; Escape closes.
 *
 * States: loading, error, disabled, working
 * Text: frame
 * Cap: Fill
 * Motion: an arrow-key step slides on `standard`; reduced motion jumps
 * Hidden: swipe and pinch; the arrows, the zoom buttons and the position line are the visible paths
 * Replaces: KitImageGallery, showKitImageGallery (lib/ui/kit/kit_image_gallery.dart)
 */
@Composable
public fun KitImageGallery(
    pictures: List<KitGalleryPicture>,
    onClose: () -> Unit,
    encodePng: KitPngEncoder,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    onSend: KitGallerySend? = null,
) {
    require(pictures.isNotEmpty()) { "KitImageGallery needs a picture" }
    val scope = rememberCoroutineScope()
    val total = pictures.size
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, total - 1)) { total }
    val loads = remember { mutableStateMapOf<Int, PictureLoad>() }
    val edited = remember { mutableStateMapOf<String, ByteArray>() }
    val readable = remember { mutableStateMapOf<String, Boolean>() }
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    var pointers by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var markup by remember { mutableStateOf<ByteArray?>(null) }
    val latestClose by rememberUpdatedState(onClose)
    val latestSend by rememberUpdatedState(onSend)
    val index = pager.currentPage

    fun read(i: Int) {
        if (i !in pictures.indices || loads.containsKey(i)) return
        val reading = PictureLoad.Reading()
        loads[i] = reading
        scope.launch {
            val bytes =
                try {
                    pictures[i].load()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { // a failed read is the error state with Try again
                    null
                }
            if (loads[i] !== reading) return@launch
            if (bytes != null && bytes.isNotEmpty()) {
                readable[pictures[i].id] = true
                loads[i] = PictureLoad.Ready(bytes)
            } else {
                loads[i] = PictureLoad.Failed
            }
        }
    }

    LaunchedEffect(index) { galleryPrefetch(index, total).forEach(::read) }

    val picture = pictures[index]
    val ready = readable[picture.id] == true || edited.containsKey(picture.id)
    val locked = zoomed[index] == true || pointers > 1

    fun bytesOf(i: Int): ByteArray? = edited[pictures[i].id] ?: (loads[i] as? PictureLoad.Ready)?.bytes

    fun send() {
        val host = latestSend ?: return
        if (sending) return
        val shown = pictures[pager.currentPage]
        val mine = edited[shown.id]
        val bytes = bytesOf(pager.currentPage) ?: return
        sending = true
        scope.launch {
            var added = false
            try {
                added = host(bytes, if (mine == null) shown.name else galleryEditedName(shown.name), mine != null)
            } finally {
                sending = false
            }
            if (added) latestClose()
        }
    }

    val reduced = KitMotion.state.reduced
    val enter = KitMotion.specs.enter<Float>()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun step(towardsEnd: Boolean) {
        if (zoomed[pager.currentPage] == true) return
        val to = pager.currentPage + if (towardsEnd) 1 else -1
        if (to < 0 || to >= total) return
        scope.launch { pager.animateScrollToPage(to, animationSpec = if (reduced) snap() else enter) }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val editLabel = stringResource(Res.string.kitGalleryEdit)
    val loadingLabel = stringResource(Res.string.kitGalleryLoading)
    val moreLabel = stringResource(Res.string.kitTopBarMore)
    val edit = { if (ready) markup = bytesOf(pager.currentPage) }
    Box(
        modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || markup != null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Escape -> latestClose()
                    Key.DirectionLeft -> step(rtl)
                    Key.DirectionRight -> step(!rtl)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = KitBidi.auto(picture.name),
                    subtitle = if (total > 1) stringResource(Res.string.kitGalleryPosition, index + 1, total) else null,
                    exit = KitTopBarExit.Close,
                    exitLabel = stringResource(Res.string.kitTopBarClose),
                    onExit = { latestClose() },
                    // A disabled action is not shown in the bar; it waits in the overflow (kit_top_bar.dart:937-945).
                    actions = if (ready) listOf(KitLineAction(editLabel, edit, testTag = "kit-gallery-edit", icon = KitIcons.edit)) else emptyList(),
                    more = if (ready) null else { _ ->
                        var open by remember { mutableStateOf(false) }
                        KitMenu(
                            expanded = open,
                            items = listOf(KitMenuItem(editLabel, {}, icon = KitIcons.edit, enabled = KitEnabled.Disabled(loadingLabel))),
                            onDismissRequest = { open = false },
                        ) {
                            KitIconButton(KitIcons.more, moreLabel, { open = true }, Modifier.testTag("kit-gallery-more"))
                        }
                    },
                )
            },
            bottom = if (onSend == null) null else {
                {
                    Box(Modifier.fillMaxWidth().padding(Space.S4.dp)) {
                        KitImageActions(
                            KitAction(
                                stringResource(Res.string.kitGallerySend),
                                if (ready) ::send else null,
                                enabled = if (ready) KitEnabled.Enabled else KitEnabled.Disabled(loadingLabel),
                                icon = KitIcons.send,
                                working = sending,
                            ),
                            secondary = null,
                            primaryTag = "kit-gallery-send",
                        )
                    }
                }
            },
        ) {
            val pictureLabel = stringResource(Res.string.kitGalleryPictureLabel, index + 1, total, picture.name)
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        // Two fingers lock the pager while they pinch (Dart `_pointers`).
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.count { it.pressed }
                                if (down != pointers) pointers = down
                            }
                        }
                    }
                    .semantics {
                        isTraversalGroup = true
                        contentDescription = pictureLabel
                    },
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().testTag("kit-gallery-pager"),
                    userScrollEnabled = !locked,
                    key = { pictures[it].id },
                ) { i ->
                    val item = pictures[i]
                    val load = loads[i]
                    LaunchedEffect(load == null) { if (load == null) read(i) }
                    GalleryPage(
                        picture = item,
                        load = load,
                        edited = edited[item.id],
                        onZoom = { z -> if (z != (zoomed[i] == true)) zoomed[i] = z },
                        onRetry = { loads.remove(i) },
                    )
                }
            }
        }
        val opened = markup
        if (opened != null) {
            KitImageMarkup(
                bytes = opened,
                name = picture.name,
                onResult = { result ->
                    markup = null
                    if (result != null) {
                        if (result.changed) edited[picture.id] = result.bytes
                        if (result.send) send()
                    }
                },
                encodePng = encodePng,
                modifier = Modifier.fillMaxSize(),
                canSend = onSend != null,
            )
        }
    }
}

@Composable
private fun GalleryPage(
    picture: KitGalleryPicture,
    load: PictureLoad?,
    edited: ByteArray?,
    onZoom: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    if (edited != null) {
        GalleryPicture(picture, edited, onZoom)
        return
    }
    when (load) {
        null, is PictureLoad.Reading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KitImageWorkingMark(stringResource(Res.string.kitGalleryLoading))
            }
        PictureLoad.Failed ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KitStateView(
                    kind = KitStateKind.Empty,
                    title = stringResource(Res.string.kitGalleryLoadFailed),
                    body = stringResource(Res.string.kitGalleryLoadFailedBody),
                    icon = KitIcons.imageBroken,
                    primary = KitAction(stringResource(Res.string.kitGalleryTryAgain), onRetry, icon = KitIcons.retry),
                )
            }
        is PictureLoad.Ready -> GalleryPicture(picture, load.bytes, onZoom)
    }
}

/**
 * One picture in its zoom (Dart `_GalleryPageState._picture`): centred at its own size (one picture pixel per dp, as
 * Flutter's `Image.memory` at scale 1), shrunk to fit and never enlarged; the zoom resets per picture.
 */
@Composable
private fun GalleryPicture(picture: KitGalleryPicture, bytes: ByteArray, onZoom: (Boolean) -> Unit) {
    val zoom = remember(picture.id) { KitZoomState() }
    val latestZoom by rememberUpdatedState(onZoom)
    LaunchedEffect(zoom) { snapshotFlow { zoom.scale > GALLERY_ZOOMED_SCALE }.collect { latestZoom(it) } }
    val decoded by produceState<ImageBitmap?>(null, bytes) {
        value =
            try {
                bytes.decodeToImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { // KitImage shows its own failure state for bytes it cannot read
                null
            }
    }
    KitZoomView(label = picture.name, modifier = Modifier.fillMaxSize().testTag("kit-gallery-zoom"), state = zoom) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = decoded
            if (bitmap != null) {
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                val fit = min(1f, min(maxWidth.value / w, maxHeight.value / h))
                KitImage(
                    KitImageSource.Loader(bytes) { bitmap },
                    picture.name,
                    Modifier.size((w * fit).dp, (h * fit).dp).testTag("kit-gallery-image"),
                )
            }
        }
    }
}
