package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the editor hands back (Dart `KitMarkupResult`). [changed] is false when nothing was drawn or cropped, so [bytes]
 * are the original bytes untouched. [send] says the person pressed Send rather than Done.
 */
@Immutable
public class KitMarkupResult(public val bytes: ByteArray, public val send: Boolean, public val changed: Boolean)

/** The largest encoded picture the editor returns (9 MiB, under the 10 MB the composer accepts). */
public const val KIT_MARKUP_MAX_BYTES: Int = 9 * 1024 * 1024 // F: lib/ui/kit/kit_image_markup.dart:61

/** Encodes [image] as PNG on the platform (Skia on desktop and iOS, Bitmap.compress on Android). */
internal expect fun encodeMarkupPng(image: ImageBitmap): ByteArray

/** The four pen colours: markup ink, not theme roles (Dart `MarkupInk`, lib/ui/theme_roles.dart:715-720). */
public enum class MarkupPen(public val colour: Color, internal val word: StringResource) {
    Red(Color(0xFFE5322D), Res.string.kitMarkupRed), // F: lib/ui/theme_roles.dart:716
    Yellow(Color(0xFFFFD60A), Res.string.kitMarkupYellow), // F: lib/ui/theme_roles.dart:717
    White(Color(0xFFFFFFFF), Res.string.kitMarkupWhite), // F: lib/ui/theme_roles.dart:718
    Black(Color(0xFF000000), Res.string.kitMarkupBlack), // F: lib/ui/theme_roles.dart:719
}

/** One stroke in the picture's own pixels. */
internal class MarkupStroke(val colour: Color, val width: Float, start: Offset) {
    val points = mutableStateListOf(start, start)
}

/** Pure geometry of [KitImageMarkup] (lib/ui/kit/kit_image_markup.dart), tested without Compose. */
public object MarkupGeometry {
    /** How thick the pen looks on screen, in dp. */
    public const val PEN_DP: Float = 4f // F: lib/ui/kit/kit_image_markup.dart:115

    /** The smallest crop side on screen, in dp. */
    public const val MIN_CROP_DP: Float = 48f // F: lib/ui/kit/kit_image_markup.dart:116

    /** Room around the picture so a corner handle on its edge stays whole. */
    public const val HANDLE_ROOM_DP: Float = 24f // F: lib/ui/kit/kit_image_markup.dart:117

    /** How the shown rectangle [view] of the picture sits in [box]: the scale and its top-left (Dart `_fit`). */
    public fun fit(view: Rect, box: Size): Pair<Float, Offset> {
        val scale = min(box.width / view.width, box.height / view.height)
        return scale to Offset((box.width - view.width * scale) / 2f, (box.height - view.height * scale) / 2f) // F: lib/ui/kit/kit_image_markup.dart:170,171
    }

    /** A corner of the crop moved by [delta] screen px at [scale], never past the picture or under [minPx] (`_dragCorner`). */
    public fun dragCorner(rect: Rect, full: Rect, corner: Int, delta: Offset, scale: Float, minPx: Float): Rect {
        val minSide = minPx / scale
        val dx = delta.x / scale
        val dy = delta.y / scale
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        if (corner == 0 || corner == 2) left = (left + dx).coerceIn(full.left, right - minSide) // F: lib/ui/kit/kit_image_markup.dart:234
        else right = (right + dx).coerceIn(left + minSide, full.right)
        if (corner == 0 || corner == 1) top = (top + dy).coerceIn(full.top, bottom - minSide) // F: lib/ui/kit/kit_image_markup.dart:239
        else bottom = (bottom + dy).coerceIn(top + minSide, full.bottom)
        return Rect(left, top, right, bottom)
    }

    /** The crop moved by [delta] screen px at [scale], kept inside the picture (`_moveCrop`). */
    public fun moveCrop(rect: Rect, full: Rect, delta: Offset, scale: Float): Rect {
        val dx = (delta.x / scale).coerceIn(full.left - rect.left, full.right - rect.right)
        val dy = (delta.y / scale).coerceIn(full.top - rect.top, full.bottom - rect.bottom)
        return rect.translate(dx, dy)
    }

    /** Each try at encoding scales the picture by a quarter less until it fits (`_render`). */
    public const val SHRINK: Float = 0.75f // F: lib/ui/kit/kit_image_markup.dart:317

    /** Below this factor the editor stops shrinking and returns what it has. */
    public const val SHRINK_FLOOR: Float = 0.2f // F: lib/ui/kit/kit_image_markup.dart:316
}

@OptIn(ExperimentalResourceApi::class)
private fun decodeOrNull(bytes: ByteArray): ImageBitmap? =
    if (bytes.isEmpty()) null
    else try {
        bytes.decodeToImageBitmap()
    } catch (_: RuntimeException) { // a file that is not a picture is the error state, never a crash
        null
    }

private fun DrawScope.strokesOf(strokes: List<MarkupStroke>) {
    for (stroke in strokes) {
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (p in stroke.points.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, stroke.colour, style = Stroke(stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun paintStrokes(canvas: BitmapCanvas, strokes: List<MarkupStroke>) {
    for (stroke in strokes) {
        val paint = Paint().apply {
            color = stroke.colour
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            strokeWidth = stroke.width
            isAntiAlias = true
        }
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (p in stroke.points.drop(1)) lineTo(p.x, p.y)
        }
        canvas.drawPath(path, paint)
    }
}

/** The picture with its marks at its own resolution, cropped, as PNG; shrunk until it fits [KIT_MARKUP_MAX_BYTES]. */
internal fun renderMarkup(image: ImageBitmap, crop: Rect, strokes: List<MarkupStroke>): ByteArray {
    var factor = 1f
    while (true) {
        val w = max(1, (crop.width * factor).roundToInt())
        val h = max(1, (crop.height * factor).roundToInt())
        val out = ImageBitmap(w, h)
        val canvas = BitmapCanvas(out)
        canvas.scale(w / crop.width, h / crop.height)
        canvas.translate(-crop.left, -crop.top)
        canvas.drawImage(image, Offset.Zero, Paint().apply { filterQuality = FilterQuality.High })
        paintStrokes(canvas, strokes)
        val png = encodeMarkupPng(out)
        if (png.size <= KIT_MARKUP_MAX_BYTES || factor < MarkupGeometry.SHRINK_FLOOR) return png
        factor *= MarkupGeometry.SHRINK
    }
}

/** Test tags (Dart value keys). */
public object KitMarkupTags {
    public const val CANVAS: String = "kit-markup-canvas"
    public const val SEND: String = "kit-markup-send"
    public const val DONE: String = "kit-markup-done"
    public const val UNDO: String = "kit-markup-undo"
    public const val CLEAR: String = "kit-markup-clear"
    public const val CROP: String = "kit-markup-crop"
    public const val APPLY: String = "kit-markup-apply"
    public const val CANCEL: String = "kit-markup-cancel"
}

/**
 * A picture to draw on and crop (Dart `KitImageMarkup`, docs/ux-system/kit-api/KitImageMarkup.md): a finger pen in four
 * colours, Undo, Clear and a crop rectangle with corner handles. The result is drawn at the picture's own resolution,
 * not the screen's. [bytes] is null while the host still reads the picture (the loading state). [onResult] gets null when the person leaves with Close. The host shows it full screen (Dart
 * `showKitImageMarkup`); [KitImageGallery] opens it in place for Edit.
 *
 * States: loading, error, working (drawing and cropping are modes, not states)
 * Text: frame
 * Cap: Fill
 * Motion: none of its own (the button's working mark); Off = still
 * Hidden: drawing with a finger and dragging the crop; the canvas is labelled and Crop, Apply, Undo and Clear are buttons
 * Replaces: KitImageMarkup, showKitImageMarkup (lib/ui/kit/kit_image_markup.dart)
 */
@Composable
public fun KitImageMarkup(
    bytes: ByteArray?,
    name: String,
    onResult: (KitMarkupResult?) -> Unit,
    modifier: Modifier = Modifier,
    canSend: Boolean = true,
    /**
     * Where the result is drawn and encoded (a big PNG takes a moment): the host passes its `AppDispatchers` default
     * context (rule 6: no dispatcher singletons outside `:core:common`). Empty = the composition's own context.
     */
    work: CoroutineContext = EmptyCoroutineContext,
) {
    val image = remember(bytes) { bytes?.let(::decodeOrNull) }
    val strokes = remember(bytes) { mutableStateListOf<MarkupStroke>() }
    var pen by remember { mutableStateOf(MarkupPen.Red) }
    var crop by remember(bytes) { mutableStateOf<Rect?>(null) }
    var draft by remember(bytes) { mutableStateOf<Rect?>(null) }
    var busy by remember { mutableStateOf(false) }
    var renderFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cropping = draft != null
    val changed = strokes.isNotEmpty() || crop != null

    fun finish(send: Boolean) {
        if (busy || image == null || bytes == null) return
        if (!changed) {
            onResult(KitMarkupResult(bytes, send, changed = false))
            return
        }
        busy = true
        scope.launch {
            val full = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
            val marks = strokes.toList()
            val out = try {
                withContext(work) { renderMarkup(image, crop ?: full, marks) }
            } catch (_: RuntimeException) { // an encoder failure is the notice, the original stays sendable
                null
            }
            busy = false
            if (out == null) renderFailed = true else onResult(KitMarkupResult(out, send, changed = true))
        }
    }

    KitScreen(
        modifier = modifier,
        page = true,
        topBar = {
            KitTopBar(
                title = KitBidi.auto(name),
                subtitle = stringResource(Res.string.kitMarkupTitle),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitTopBarClose),
                onExit = { if (cropping) draft = null else onResult(null) },
            )
        },
        bottom = if (image == null) null else {
            {
                Column(Modifier.fillMaxWidth().padding(Space.S4.dp)) { // F: lib/ui/kit/kit_image_markup.dart:372
                    if (renderFailed) {
                        KitNotice(
                            stringResource(Res.string.kitMarkupFailedBody),
                            Modifier.padding(bottom = Space.S2.dp), // F: lib/ui/kit/kit_image_markup.dart:379
                            title = stringResource(Res.string.kitMarkupFailed),
                        )
                    }
                    if (!cropping) {
                        val colours = stringResource(Res.string.kitMarkupColours)
                        Row(
                            Modifier.fillMaxWidth().semantics { contentDescription = colours },
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            for (each in MarkupPen.entries) PenSwatch(each, each == pen) { pen = each }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            KitIconButton(
                                KitIcons.undo, stringResource(Res.string.kitMarkupUndo),
                                { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                                Modifier.testTag(KitMarkupTags.UNDO),
                                enabled = KitEnabled.of(strokes.isNotEmpty(), stringResource(Res.string.kitMarkupUndo)),
                            )
                            KitIconButton(
                                KitIcons.clearAll, stringResource(Res.string.kitMarkupClear),
                                {
                                    strokes.clear()
                                    crop = null
                                },
                                Modifier.testTag(KitMarkupTags.CLEAR),
                                enabled = KitEnabled.of(changed, stringResource(Res.string.kitMarkupClear)),
                            )
                            KitIconButton(
                                KitIcons.crop, stringResource(Res.string.kitMarkupCrop),
                                { draft = crop ?: Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()) },
                                Modifier.testTag(KitMarkupTags.CROP),
                            )
                        }
                    }
                    val send = stringResource(Res.string.kitGallerySend)
                    val done = stringResource(Res.string.kitMarkupDone)
                    val full = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
                    // From medium up the block is one row at the end of its 720 dp reading cap (KitActionBlock's row
                    // wraps its content, so the cap's end is set here).
                    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading), contentAlignment = Alignment.CenterEnd) {
                        KitActionBlock(
                            primary = when {
                                cropping -> KitAction(
                                    stringResource(Res.string.kitMarkupApply),
                                    {
                                        crop = draft.takeIf { it != full }
                                        draft = null
                                    },
                                    icon = KitIcons.check,
                                )
                                canSend -> KitAction(send, { finish(send = true) }, icon = KitIcons.send, working = busy)
                                else -> KitAction(done, { finish(send = false) }, working = busy)
                            },
                            secondary = when {
                                cropping -> KitAction(stringResource(Res.string.kitMarkupCancel), { draft = null })
                                canSend -> KitAction(done, if (busy) null else ({ finish(send = false) }))
                                else -> null
                            },
                        )
                    }
                }
            }
        },
    ) {
        when {
            bytes == null -> {
                val loading = stringResource(Res.string.kitGalleryLoading)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KitTaskMark(KitTaskState.Working, label = loading)
                }
            }
            image == null ->
                KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.kitGalleryLoadFailed),
                    body = stringResource(Res.string.kitGalleryLoadFailedBody),
                    icon = KitIcons.imageBroken,
                )
            else ->
                Box(Modifier.fillMaxSize().padding(Space.S2.dp)) { // F: lib/ui/kit/kit_image_markup.dart:366
                    MarkupCanvas(
                        image = image,
                        strokes = strokes,
                        pen = pen,
                        crop = crop,
                        draft = draft,
                        busy = busy,
                        onDraft = { draft = it },
                    )
                }
        }
    }
}

/** The picture fitted in the room, with its strokes, the crop shade and the four handles. Always left to right. */
@Composable
private fun MarkupCanvas(
    image: ImageBitmap,
    strokes: MutableList<MarkupStroke>,
    pen: MarkupPen,
    crop: Rect?,
    draft: Rect?,
    busy: Boolean,
    onDraft: (Rect) -> Unit,
) {
    val colors = LocalKitColors.current
    val density = LocalDensity.current
    val full = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
    val view = if (draft != null) full else (crop ?: full)
    val canvasLabel = stringResource(Res.string.kitMarkupCanvas)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { // the picture's pixels are physical
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val room = with(density) { MarkupGeometry.HANDLE_ROOM_DP.dp.toPx() }
            val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val inner = Size(max(1f, box.width - room * 2), max(1f, box.height - room * 2)) // F: lib/ui/kit/kit_image_markup.dart:480,481
            val (scale, fitted) = MarkupGeometry.fit(view, inner)
            val origin = fitted + Offset(room, room)
            val shown = Rect(origin, Size(view.width * scale, view.height * scale))
            fun toScreen(p: Offset): Offset = (p - view.topLeft) * scale + origin
            val penPx = with(density) { MarkupGeometry.PEN_DP.dp.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag(KitMarkupTags.CANVAS)
                    .semantics { contentDescription = canvasLabel }
                    .pointerInput(view, scale, origin, pen, draft != null, busy) {
                        if (draft != null || busy) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = (down.position - origin) / scale + view.topLeft
                            val stroke = MarkupStroke(pen.colour, penPx / scale, start)
                            strokes.add(stroke)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                stroke.points.add((change.position - origin) / scale + view.topLeft)
                                change.consume()
                            }
                        }
                    },
            ) {
                clipRect(shown.left, shown.top, shown.right, shown.bottom) {
                    withTransform({
                        translate(origin.x, origin.y)
                        scale(scale, scale, Offset.Zero)
                        translate(-view.left, -view.top)
                    }) {
                        drawImage(image, filterQuality = FilterQuality.Medium)
                        strokesOf(strokes)
                    }
                }
                if (draft != null) {
                    val rect = Rect(toScreen(draft.topLeft), toScreen(draft.bottomRight))
                    clipRect(rect.left, rect.top, rect.right, rect.bottom, ClipOp.Difference) {
                        drawRect(colors.scrim, shown.topLeft, shown.size)
                    }
                    drawRect(colors.accent, rect.topLeft, rect.size, style = Stroke(2.dp.toPx())) // F: lib/ui/kit/kit_image_markup.dart:675
                }
            }
            if (draft != null) {
                val rect = Rect(toScreen(draft.topLeft), toScreen(draft.bottomRight))
                val areaLabel = stringResource(Res.string.kitMarkupCropArea)
                val minPx = with(density) { MarkupGeometry.MIN_CROP_DP.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        .semantics { contentDescription = areaLabel }
                        .pointerInput(draft, scale) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                onDraft(MarkupGeometry.moveCrop(draft, full, delta, scale))
                            }
                        },
                )
                val handleLabel = stringResource(Res.string.kitMarkupCropHandle)
                val target = with(density) { 48.dp.toPx() } // F: lib/ui/kit/kit_image_markup.dart:570
                for (corner in 0 until 4) { // F: lib/ui/kit/kit_image_markup.dart:545
                    val at = when (corner) {
                        0 -> rect.topLeft
                        1 -> rect.topRight
                        2 -> rect.bottomLeft // F: lib/ui/kit/kit_image_markup.dart:566
                        else -> rect.bottomRight
                    }
                    Box(
                        Modifier
                            .offset { IntOffset((at.x - target / 2).roundToInt(), (at.y - target / 2).roundToInt()) } // F: lib/ui/kit/kit_image_markup.dart:572,573
                            .size(48.dp) // F: lib/ui/kit/kit_image_markup.dart:570
                            .semantics { contentDescription = handleLabel }
                            .pointerInput(draft, scale, corner) {
                                detectDragGestures { change, delta ->
                                    change.consume()
                                    onDraft(MarkupGeometry.dragCorner(draft, full, corner, delta, scale, minPx))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(20.dp).background(colors.accent, CircleShape)) // F: lib/ui/kit/kit_image_markup.dart:588
                    }
                }
            }
        }
    }
}

/** One pen colour: a 48 dp target around a 28 dp filled circle, ringed in the accent while it is the pen in use. */
@Composable
private fun PenSwatch(pen: MarkupPen, selected: Boolean, onTap: () -> Unit) {
    val colors = LocalKitColors.current
    val label = stringResource(pen.word)
    KitTappable(
        onClick = onTap,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }, // NV: the kit's 48 dp minimum target around the swatch (KitTappable)
        onClickLabel = label,
        shape = KitShapeId.Tile,
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(28.dp) // F: lib/ui/kit/kit_image_markup.dart:728
                .background(pen.colour, CircleShape)
                .border(if (selected) kitFocusRingWidth() else kitHairline(), if (selected) colors.accent else colors.text3, CircleShape),
        )
    }
}

