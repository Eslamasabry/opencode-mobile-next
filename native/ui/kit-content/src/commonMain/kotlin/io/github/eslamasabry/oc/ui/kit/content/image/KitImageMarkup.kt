package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.kitGalleryLoading
import io.github.eslamasabry.oc.ui.words.res.kitGallerySend
import io.github.eslamasabry.oc.ui.words.res.kitMarkupApply
import io.github.eslamasabry.oc.ui.words.res.kitMarkupBlack
import io.github.eslamasabry.oc.ui.words.res.kitMarkupCancel
import io.github.eslamasabry.oc.ui.words.res.kitMarkupCanvas
import io.github.eslamasabry.oc.ui.words.res.kitMarkupClear
import io.github.eslamasabry.oc.ui.words.res.kitMarkupColours
import io.github.eslamasabry.oc.ui.words.res.kitMarkupCrop
import io.github.eslamasabry.oc.ui.words.res.kitMarkupCropArea
import io.github.eslamasabry.oc.ui.words.res.kitMarkupCropHandle
import io.github.eslamasabry.oc.ui.words.res.kitMarkupDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkupFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkupFailedBody
import io.github.eslamasabry.oc.ui.words.res.kitMarkupRed
import io.github.eslamasabry.oc.ui.words.res.kitMarkupTitle
import io.github.eslamasabry.oc.ui.words.res.kitMarkupUndo
import io.github.eslamasabry.oc.ui.words.res.kitMarkupWhite
import io.github.eslamasabry.oc.ui.words.res.kitMarkupYellow
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * What the editor hands back (Dart `KitMarkupResult`). [changed] is false when nothing was drawn or cropped, so [bytes]
 * are the original bytes untouched. [send] says the person pressed Send rather than Done.
 */
@Immutable
public class KitMarkupResult(public val bytes: ByteArray, public val send: Boolean, public val changed: Boolean)

/**
 * Encodes the drawn picture as PNG. The platform's encoder (Android `Bitmap.compress`, desktop Skia) comes from the host:
 * common code has none.
 */
public typealias KitPngEncoder = suspend (ImageBitmap) -> ByteArray

/** The largest encoded picture the editor returns (9 MiB, under the 10 MB the composer accepts). */
public const val KIT_MARKUP_MAX_BYTES: Int = 9 * 1024 * 1024 // F: lib/ui/kit/kit_image_markup.dart:61

/** The pen colours (Dart `MarkupInk`): ink, not theme roles, so a mark reads the same in light and dark. */
internal enum class MarkupPen(val colour: Color, val word: StringResource) {
    Red(Color(0xFFE5322D), Res.string.kitMarkupRed), // F: lib/ui/theme_roles.dart:716
    Yellow(Color(0xFFFFD60A), Res.string.kitMarkupYellow), // F: lib/ui/theme_roles.dart:717
    White(Color(0xFFFFFFFF), Res.string.kitMarkupWhite), // F: lib/ui/theme_roles.dart:718
    Black(Color(0xFF000000), Res.string.kitMarkupBlack), // F: lib/ui/theme_roles.dart:719
}

/** One finger stroke in the picture's own pixels; it starts as two copies of the first point (Dart `_Stroke`). */
internal class MarkupStroke(val colour: Color, val width: Float, start: Offset) {
    val points: SnapshotStateList<Offset> = mutableStateListOf(start, start)
}

/** How thick the pen looks on screen, in dp. */
internal const val MARKUP_PEN_DP: Float = 4f // F: lib/ui/kit/kit_image_markup.dart:115

/** The smallest crop side, in dp on screen. */
internal const val MARKUP_MIN_CROP_DP: Float = 48f // F: lib/ui/kit/kit_image_markup.dart:116

/** Room around the picture so a corner handle on its edge stays whole and inside the surface that hit-tests it. */
internal const val MARKUP_HANDLE_ROOM_DP: Float = 24f // F: lib/ui/kit/kit_image_markup.dart:117

/** How the shown rectangle of the picture sits in a box (Dart `_fit`): contained and centred. */
internal data class MarkupFit(val scale: Float, val origin: Offset)

/** Dart `_fit`: the largest scale that shows all of [view] in [box], centred. */
internal fun markupFit(view: Rect, box: Size): MarkupFit {
    val scale = min(box.width / view.width, box.height / view.height)
    return MarkupFit(
        scale,
        Offset((box.width - view.width * scale) / 2, (box.height - view.height * scale) / 2), // F: lib/ui/kit/kit_image_markup.dart:170,171
    )
}

/**
 * Dart `_dragCorner`: corner 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right moves by [dx], [dy] (picture
 * pixels), clamped to [full] and never smaller than [min] on a side.
 */
internal fun markupDragCorner(rect: Rect, full: Rect, corner: Int, dx: Float, dy: Float, min: Float): Rect {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    if (corner == 0 || corner == 2) { // F: lib/ui/kit/kit_image_markup.dart:234
        left = (left + dx).coerceIn(full.left, right - min)
    } else {
        right = (right + dx).coerceIn(left + min, full.right)
    }
    if (corner == 0 || corner == 1) {
        top = (top + dy).coerceIn(full.top, bottom - min)
    } else {
        bottom = (bottom + dy).coerceIn(top + min, full.bottom)
    }
    return Rect(left, top, right, bottom)
}

/** Dart `_moveCrop`: the crop moves by [dx], [dy] (picture pixels) and stays inside [full]. */
internal fun markupMoveCrop(rect: Rect, full: Rect, dx: Float, dy: Float): Rect =
    rect.translate(
        dx.coerceIn(full.left - rect.left, full.right - rect.right),
        dy.coerceIn(full.top - rect.top, full.bottom - rect.bottom),
    )

/** Dart `_render`: the next scale factor while the PNG is over [KIT_MARKUP_MAX_BYTES], or null to keep this one. */
internal fun markupNextFactor(bytes: Int, factor: Float): Float? =
    if (bytes <= KIT_MARKUP_MAX_BYTES || factor < 0.2f) null else factor * 0.75f // F: lib/ui/kit/kit_image_markup.dart:316,317

private fun paintStrokes(canvas: Canvas, strokes: List<MarkupStroke>) {
    for (stroke in strokes) {
        val paint = Paint().apply {
            color = stroke.colour
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            strokeWidth = stroke.width
            isAntiAlias = true
        }
        val points = stroke.points.toList()
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
        canvas.drawPath(path, paint)
    }
}

/**
 * Shows [KitImageMarkup] full screen over [content] while [bytes] is not null (Dart `showKitImageMarkup`, a full-screen
 * page). [onResult] gets null when the person leaves with Close or Escape (system Back is the host page's).
 */
@Composable
public fun KitImageMarkupHost(
    bytes: ByteArray?,
    name: String,
    onResult: (KitMarkupResult?) -> Unit,
    encodePng: KitPngEncoder,
    modifier: Modifier = Modifier,
    canSend: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (bytes != null) {
            KitImageMarkup(bytes, name, onResult, encodePng, Modifier.fillMaxSize(), canSend)
        }
    }
}

/**
 * A picture to draw on and crop (docs/ux-system/kit-api/KitImageMarkup.md): a finger pen in four colours, Undo, Clear
 * and a crop rectangle with corner handles. The result is drawn at the picture's own resolution, not the screen's, and
 * encoded by [encodePng]; [decode] reads [bytes] (the platform decoder by default).
 *
 * States: loading, error, working
 * Text: frame
 * Cap: Fill
 * Motion: none of its own
 * Hidden: none (every act is a visible control; drawing is the canvas itself)
 * Replaces: KitImageMarkup, showKitImageMarkup (lib/ui/kit/kit_image_markup.dart)
 */
@Composable
public fun KitImageMarkup(
    bytes: ByteArray,
    name: String,
    onResult: (KitMarkupResult?) -> Unit,
    encodePng: KitPngEncoder,
    modifier: Modifier = Modifier,
    canSend: Boolean = true,
    decode: suspend (ByteArray) -> ImageBitmap = { it.decodeToImageBitmap() },
) {
    val scope = rememberCoroutineScope()
    var image by remember(bytes) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val strokes = remember(bytes) { mutableStateListOf<MarkupStroke>() }
    var pen by remember { mutableStateOf(MarkupPen.Red) }
    var crop by remember(bytes) { mutableStateOf<Rect?>(null) }
    var draft by remember(bytes) { mutableStateOf<Rect?>(null) }
    val latestResult by rememberUpdatedState(onResult)

    LaunchedEffect(bytes) {
        try {
            image = decode(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { // any undecodable file is the error state, never a crash
            failed = true
        }
    }

    fun full(): Rect = image!!.let { Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }
    val cropping = draft != null
    val changed = strokes.isNotEmpty() || crop != null

    suspend fun render(): ByteArray {
        val picture = image!!
        val area = crop ?: full()
        var factor = 1f
        while (true) {
            val w = max(1, (area.width * factor).roundToInt())
            val h = max(1, (area.height * factor).roundToInt())
            val out = ImageBitmap(w, h)
            val canvas = Canvas(out)
            canvas.scale(w / area.width, h / area.height)
            canvas.translate(-area.left, -area.top)
            canvas.drawImage(picture, Offset.Zero, Paint().apply { filterQuality = FilterQuality.High })
            paintStrokes(canvas, strokes)
            val png = encodePng(out)
            factor = markupNextFactor(png.size, factor) ?: return png
        }
    }

    fun finish(send: Boolean) {
        if (busy) return
        if (!changed) {
            latestResult(KitMarkupResult(bytes, send, changed = false))
            return
        }
        busy = true
        scope.launch {
            val out =
                try {
                    render()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { // the notice says it could not be made; the person can retry or leave
                    null
                }
            if (out == null) {
                busy = false
                failed = true
            } else {
                latestResult(KitMarkupResult(out, send, changed = true))
            }
        }
    }

    val cancelCrop = { draft = null }
    val leave = { latestResult(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val closeLabel = stringResource(Res.string.kitTopBarClose)
    Box(
        modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    // Esc leaves a crop first, then the editor.
                    if (draft != null) cancelCrop() else leave()
                    true
                } else false
            }
            .focusable(),
    ) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = KitBidi.auto(name),
                    subtitle = stringResource(Res.string.kitMarkupTitle),
                    exit = KitTopBarExit.Close,
                    exitLabel = closeLabel,
                    onExit = leave,
                )
            },
            bottom = if (image == null) null else {
                {
                    MarkupBottom(
                        failed = failed,
                        cropping = cropping,
                        pen = pen,
                        onPen = { pen = it },
                        canUndo = strokes.isNotEmpty(),
                        changed = changed,
                        onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        onClear = {
                            strokes.clear()
                            crop = null
                        },
                        onCrop = { draft = crop ?: full() },
                        onApply = {
                            val d = draft!!
                            crop = if (d == full()) null else d
                            draft = null
                        },
                        onCancel = cancelCrop,
                        canSend = canSend,
                        busy = busy,
                        onFinish = ::finish,
                    )
                }
            },
        ) {
            val shown = image
            when {
                failed && shown == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.kitGalleryLoadFailed),
                            body = stringResource(Res.string.kitGalleryLoadFailedBody),
                            icon = KitIcons.imageBroken,
                        )
                    }
                shown == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KitImageWorkingMark(stringResource(Res.string.kitGalleryLoading))
                    }
                else ->
                    Box(Modifier.fillMaxSize().padding(Space.S2.dp)) {
                        MarkupCanvas(
                            image = shown,
                            strokes = strokes,
                            pen = pen,
                            busy = busy,
                            crop = crop,
                            draft = draft,
                            onDraft = { draft = it },
                        )
                    }
            }
        }
    }
}

@Composable
private fun MarkupCanvas(
    image: ImageBitmap,
    strokes: SnapshotStateList<MarkupStroke>,
    pen: MarkupPen,
    busy: Boolean,
    crop: Rect?,
    draft: Rect?,
    onDraft: (Rect) -> Unit,
) {
    val colors = LocalKitColors.current
    val density = LocalDensity.current
    val full = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
    val view = if (draft != null) full else (crop ?: full)
    val canvasLabel = stringResource(Res.string.kitMarkupCanvas)
    val handleLabel = stringResource(Res.string.kitMarkupCropHandle)
    val areaLabel = stringResource(Res.string.kitMarkupCropArea)
    // The picture's pixels are physical: the handles and crop sit at x from the left whatever the reading direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val room = with(density) { MARKUP_HANDLE_ROOM_DP.dp.toPx() }
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val fitted = markupFit(view, Size(max(1f, boxW - room * 2), max(1f, boxH - room * 2))) // F: lib/ui/kit/kit_image_markup.dart:480,481
            val scale = fitted.scale
            val origin = fitted.origin + Offset(room, room)
            val size = Size(view.width * scale, view.height * scale)
            fun toScreen(p: Offset): Offset = (p - view.topLeft) * scale + origin
            val geometry by rememberUpdatedState(Triple(view, scale, origin))
            val drawingPen by rememberUpdatedState(pen)
            val blocked by rememberUpdatedState(busy || draft != null)
            var drawing by remember { mutableStateOf<PointerId?>(null) }
            var current by remember { mutableStateOf<MarkupStroke?>(null) }
            Box(
                Modifier.fillMaxSize()
                    .testTag("kit-markup-canvas")
                    .semantics {
                        contentDescription = canvasLabel
                        role = Role.Image
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (c in event.changes) {
                                    val (v, s, o) = geometry
                                    when {
                                        c.changedToDown() -> {
                                            if (drawing != null || blocked) continue
                                            val p = (c.position - o) / s + v.topLeft
                                            val stroke = MarkupStroke(drawingPen.colour, with(density) { MARKUP_PEN_DP.dp.toPx() } / s, p)
                                            drawing = c.id
                                            current = stroke
                                            strokes.add(stroke)
                                        }
                                        c.changedToUp() || !c.pressed -> if (c.id == drawing) {
                                            drawing = null
                                            current = null
                                        }
                                        c.positionChanged() -> if (c.id == drawing) {
                                            current?.points?.add((c.position - o) / s + v.topLeft)
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val shown = Rect(origin, size)
                    clipRect(shown.left, shown.top, shown.right, shown.bottom) {
                        withTransform({
                            translate(origin.x, origin.y)
                            scale(scale, scale, Offset.Zero)
                            translate(-view.left, -view.top)
                        }) {
                            drawImage(
                                image,
                                dstSize = IntSize(image.width, image.height),
                                filterQuality = FilterQuality.Medium,
                            )
                            drawIntoCanvas { paintStrokes(it, strokes) }
                        }
                    }
                    if (draft != null) {
                        val rect = Rect(toScreen(draft.topLeft), toScreen(draft.bottomRight))
                        val outside = Path.combine(
                            PathOperation.Difference,
                            Path().apply { addRect(shown) },
                            Path().apply { addRect(rect) },
                        )
                        drawPath(outside, colors.scrim)
                        drawRect(colors.accent, rect.topLeft, rect.size, style = Stroke(2.dp.toPx())) // F: lib/ui/kit/kit_image_markup.dart:675
                    }
                }
                if (draft != null) {
                    val rect = Rect(toScreen(draft.topLeft), toScreen(draft.bottomRight))
                    val latestDraft by rememberUpdatedState(draft)
                    Box(
                        Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                            .testTag("kit-markup-crop-area")
                            .semantics { contentDescription = areaLabel }
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val (_, s, _) = geometry
                                    onDraft(markupMoveCrop(latestDraft, full, drag.x / s, drag.y / s))
                                }
                            },
                    )
                    for (corner in 0 until 4) { // F: lib/ui/kit/kit_image_markup.dart:545
                        val point = when (corner) {
                            0 -> draft.topLeft // F: lib/ui/kit/kit_image_markup.dart:564
                            1 -> draft.topRight
                            2 -> draft.bottomLeft // F: lib/ui/kit/kit_image_markup.dart:566
                            else -> draft.bottomRight
                        }
                        val at = toScreen(point)
                        val target = with(density) { 48.dp.toPx() } // F: lib/ui/kit/kit_image_markup.dart:570
                        Box(
                            Modifier.offset { IntOffset((at.x - target / 2).roundToInt(), (at.y - target / 2).roundToInt()) } // F: lib/ui/kit/kit_image_markup.dart:572,573
                                .size(48.dp) // F: lib/ui/kit/kit_image_markup.dart:570
                                .testTag("kit-markup-handle-$corner")
                                .semantics { contentDescription = handleLabel }
                                .pointerInput(corner) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val (_, s, _) = geometry
                                        val min = with(density) { MARKUP_MIN_CROP_DP.dp.toPx() } / s
                                        onDraft(markupDragCorner(latestDraft, full, corner, drag.x / s, drag.y / s, min))
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(20.dp)) { drawCircle(colors.accent) } // F: lib/ui/kit/kit_image_markup.dart:588
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkupBottom(
    failed: Boolean,
    cropping: Boolean,
    pen: MarkupPen,
    onPen: (MarkupPen) -> Unit,
    canUndo: Boolean,
    changed: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onCrop: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    canSend: Boolean,
    busy: Boolean,
    onFinish: (send: Boolean) -> Unit,
) {
    val colours = stringResource(Res.string.kitMarkupColours)
    val undo = stringResource(Res.string.kitMarkupUndo)
    val clear = stringResource(Res.string.kitMarkupClear)
    val done = stringResource(Res.string.kitMarkupDone)
    val primary =
        when {
            cropping -> KitAction(stringResource(Res.string.kitMarkupApply), onApply, icon = KitIcons.check)
            canSend -> KitAction(stringResource(Res.string.kitGallerySend), { onFinish(true) }, icon = KitIcons.send, working = busy)
            else -> KitAction(done, { onFinish(false) }, working = busy)
        }
    val secondary =
        when {
            cropping -> KitAction(stringResource(Res.string.kitMarkupCancel), onCancel)
            canSend ->
                if (busy) KitAction(done, null, enabled = KitEnabled.Disabled(done))
                else KitAction(done, { onFinish(false) })
            else -> null
        }
    Column(Modifier.fillMaxWidth().padding(Space.S4.dp)) {
        if (failed) {
            KitNotice(
                message = stringResource(Res.string.kitMarkupFailedBody),
                modifier = Modifier.padding(bottom = Space.S2.dp),
                title = stringResource(Res.string.kitMarkupFailed),
            )
        }
        if (!cropping) {
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = colours },
                horizontalArrangement = Arrangement.Center,
            ) {
                for (each in MarkupPen.entries) {
                    PenSwatch(each, selected = each == pen, onTap = { onPen(each) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                KitIconButton(
                    KitIcons.undo, undo, onUndo, Modifier.testTag("kit-markup-undo"),
                    enabled = KitEnabled.of(canUndo, undo),
                )
                KitIconButton(
                    KitIcons.clearAll, clear, onClear, Modifier.testTag("kit-markup-clear"),
                    enabled = KitEnabled.of(changed, clear),
                )
                KitIconButton(
                    KitIcons.crop, stringResource(Res.string.kitMarkupCrop), onCrop, Modifier.testTag("kit-markup-crop"),
                )
            }
        }
        KitImageActions(
            primary,
            secondary,
            primaryTag = when {
                cropping -> "kit-markup-apply"
                canSend -> "kit-markup-send"
                else -> "kit-markup-done"
            },
        )
    }
}

/** One pen colour (Dart `_PenSwatch`): a 48 dp target around a filled circle, ringed in the accent while in use. */
@Composable
private fun PenSwatch(pen: MarkupPen, selected: Boolean, onTap: () -> Unit) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val label = stringResource(pen.word)
    val ring = if (selected) colors.accent else colors.text3
    val ringWidth = if (selected) kitFocusRingWidth() else kitHairline()
    KitTappable(
        onClick = onTap,
        modifier = Modifier.sizeIn(minWidth = tokens.minTarget.dp, minHeight = tokens.minTarget.dp)
            .testTag("kit-markup-${pen.name.lowercase()}")
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        shape = KitShapeId.Tile,
    ) {
        Canvas(Modifier.size(28.dp)) { // F: lib/ui/kit/kit_image_markup.dart:728
            drawCircle(pen.colour)
            val w = ringWidth.toPx()
            drawCircle(ring, radius = (size.minDimension - w) / 2, style = Stroke(w)) // NV: CircleBorder's side sits inside the circle
        }
    }
}

/**
 * The editor's and the gallery's action block (Dart `KitActionBlock` with a primary and an optional secondary): held to
 * the reading width; compact windows stack full-width buttons, primary first; wider ones end-align one row, primary at
 * the end. A disabled action's reason stays in its semantics, as Flutter's actions here carry none to show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KitImageActions(primary: KitAction, secondary: KitAction?, primaryTag: String, secondaryTag: String? = null) {
    val stacked = LocalKitWindow.current.cls == WindowClass.Compact
    // Flutter's bottom column stretches the block, so KitMaxWidth.reading centres it on wide windows.
    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitButton(primary, Modifier.fillMaxWidth().testTag(primaryTag), KitButtonRole.Primary)
                secondary?.let { KitButton(it, Modifier.fillMaxWidth().then(secondaryTag?.let { t -> Modifier.testTag(t) } ?: Modifier), KitButtonRole.Secondary) }
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                secondary?.let { KitButton(it, secondaryTag?.let { t -> Modifier.testTag(t) } ?: Modifier, KitButtonRole.Secondary) }
                KitButton(primary, Modifier.testTag(primaryTag), KitButtonRole.Primary)
            }
        }
    }
}

/**
 * The working mark of a picture being read (Dart `KitStatusMark(state: working)`): the kit's small spinner in its 32 dp
 * slot, a still accent dot under reduced motion. [label] is what a screen reader says.
 */
@Composable
internal fun KitImageWorkingMark(label: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Box(
        modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (KitMotion.state.reduced) {
            Image(
                KitIcons.statusDot,
                null,
                Modifier.size(FlutterKitTokens.MARK_DOT_SIZE.dp),
                colorFilter = ColorFilter.tint(colors.accent),
            )
        } else {
            KitWorkingMark(colors.accent, LocalKitTokens.current.smallIcon.dp, strokeWidth = FlutterKitTokens.SPINNER_STROKE.dp)
        }
    }
}
