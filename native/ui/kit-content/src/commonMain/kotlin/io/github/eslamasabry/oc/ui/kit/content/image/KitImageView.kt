package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitImageUnavailable
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A decoded picture with zoom (the viewer's Image body and a gallery page; plan 10 §11.3 / §9.3). The picture is fitted
 * to the area, never upscaled past it at rest, and zooms from fit to 5 × fit. The visible zoom buttons sit under it
 * unless [showControls] is false (a host that puts them in its own bar).
 *
 * Native: takes a decoded [Painter]. The `KitImage(ref: ImageRef)` loader is `:ui:kit`'s (C10-17) and the Coil loader
 * waits for the catalog's Coil entries; a host passes `painterResource`, `BitmapPainter` or the loader's painter.
 *
 * States: fitted, zoomed
 * Text: frame (the buttons)
 * Cap: Fill
 * Motion: see [KitZoom]
 * Hidden: pinch and double tap; the buttons are the visible path
 * Replaces: KitViewer image body + KitZoom (lib/ui/kit/kit_viewer_build.dart, kit_image.dart)
 */
@Composable
public fun KitImageView(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    state: KitZoomState = rememberKitZoomState(),
    showControls: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitZoom(state, Modifier.fillMaxWidth().weight(1f)) {
            Image(painter, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (showControls) KitZoomControls(state, Modifier.fillMaxWidth())
    }
}

/**
 * Where a [KitImage] or [KitAvatar]'s pixels come from (Dart `KitImageSource`). Never a raw URL: a network image
 * arrives through a [Loader] that a service built (the favicon service, the gateway).
 */
public sealed interface KitImageSource {
    /** Encoded picture bytes (PNG, JPEG, WebP). */
    public class Memory(public val bytes: ByteArray) : KitImageSource

    /** A host's own load: [key] says when it is a new source; a throw is the failure state. */
    public class Loader(public val key: Any, public val load: suspend () -> ImageBitmap) : KitImageSource
}

/** How a [KitImage] fills its box. */
public enum class KitImageFit { Contain, Cover }

/** [KitAvatar]'s fixed sizes: [Tile] is the icon tile (28 dp), [Mark] is the mark size (44 dp). */
public enum class KitAvatarSize { Tile, Mark }

private sealed interface Loaded {
    data object Loading : Loaded

    data object Failed : Loaded

    class Ready(val image: ImageBitmap) : Loaded
}

@Composable
private fun rememberLoaded(source: KitImageSource): Loaded {
    val key: Any = if (source is KitImageSource.Loader) source.key else source
    val result by produceState<Loaded>(Loaded.Loading, key) {
        value =
            try {
                Loaded.Ready(
                    when (source) {
                        is KitImageSource.Memory -> source.bytes.decodeToImageBitmap()
                        is KitImageSource.Loader -> source.load()
                    },
                )
            } catch (_: Exception) { // any failure is the failure state, never a crash
                Loaded.Failed
            }
    }
    return result
}

/** The loaded cross-fade shared by [KitImage] and [KitAvatar]: the first frame fades in on `quick`, none when motion is off. */
@Composable
private fun CrossFade(content: @Composable (Modifier) -> Unit) {
    val specs = KitMotion.specs
    val opacity by animateFloatAsState(1f, specs.quick(), label = "kit-image-fade") // F: lib/ui/kit/kit_image.dart:249-252
    content(Modifier.alpha(opacity))
}

/**
 * Draws a raster image sharply (docs/ux-system/kit-api/KitImage.md): filtered at high quality, clipped to a token
 * [shape], on a `surface2` frame, with honest loading and failure states. [semanticsLabel] is required and may be null
 * (a decorative image).
 *
 * States: loading, error
 * Text: frame (the failure words)
 * Cap: Reading
 * Motion: the loaded frame fades in on quick; Off = instant
 * Replaces: KitImage (lib/ui/kit/kit_image.dart)
 */
@Composable
public fun KitImage(
    source: KitImageSource,
    semanticsLabel: String?,
    modifier: Modifier = Modifier,
    fit: KitImageFit = KitImageFit.Contain,
    shape: KitShapeId = KitShapeId.Square,
    width: Dp? = null,
    height: Dp? = null,
    fallback: (@Composable () -> Unit)? = null,
) {
    val loaded = rememberLoaded(source)
    var frame = modifier
    if (width != null) frame = frame.width(width)
    if (height != null) frame = frame.height(height)
    val semantics =
        if (semanticsLabel == null) Modifier.clearAndSetSemantics {} else Modifier.semantics(mergeDescendants = true) { contentDescription = semanticsLabel }
    val body: @Composable () -> Unit = {
        when (loaded) {
            is Loaded.Ready ->
                CrossFade { fade ->
                    Image(
                        loaded.image,
                        null,
                        fade.fillMaxSize(),
                        contentScale = if (fit == KitImageFit.Cover) ContentScale.Crop else ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                    )
                }
            Loaded.Failed -> if (fallback != null) fallback() else KitImageFailure()
            Loaded.Loading -> Unit
        }
    }
    if (shape == KitShapeId.Circle) {
        // CircleBorder takes the shorter side as its diameter and centres (kit_tokens.dart:599)
        BoxWithConstraints(frame.then(semantics), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            KitSurface(Modifier.size(side), SurfaceLevel.Surface2, shape, KitSurfacePadding.None, content = body)
        }
    } else {
        KitSurface(frame.then(semantics), SurfaceLevel.Surface2, shape, KitSurfacePadding.None, content = body)
    }
}

@Composable
private fun KitImageFailure() {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val words = stringResource(Res.string.kitImageUnavailable)
    val measurer = rememberTextMeasurer()
    val style = LocalKitTypography.current.style(TypeRole.Secondary)
    Box(
        Modifier.fillMaxSize().background(colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints { // the failure state reads its own width
            val maxWidth = this.maxWidth
            val inset = Space.S2.dp // F: lib/ui/kit/kit_image.dart:414
            val density = LocalDensity.current
            val showWords =
                maxWidth.value >= WORDS_MIN_WIDTH && // F: lib/ui/kit/kit_image.dart:48,416
                    !measurer.measure(
                        words,
                        style,
                        maxLines = 2, // F: lib/ui/kit/kit_image.dart:441
                        constraints = Constraints(maxWidth = with(density) { (maxWidth - inset * 2).roundToPx() }.coerceAtLeast(0)), // F: lib/ui/kit/kit_image.dart:420
                    ).didOverflowHeight
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    KitIcons.imageBroken,
                    null,
                    Modifier.size(tokens.smallIcon.dp),
                    colorFilter = ColorFilter.tint(colors.text2),
                )
                if (showWords) {
                    Spacer(Modifier.height(Space.S1.dp)) // F: lib/ui/kit/kit_image.dart:432
                    KitText(words, Modifier.padding(horizontal = inset), role = TypeRole.Secondary, textAlign = TextAlign.Center, maxLines = 2) // F: lib/ui/kit/kit_image.dart:437-442
                }
            }
        }
    }
}

/** KitImage.md "failed": from this many logical px wide the failure state also shows its words. */
private const val WORDS_MIN_WIDTH: Float = 120f // F: lib/ui/kit/kit_image.dart:48

/**
 * The one identity mark (docs/ux-system/kit-api/KitImage.md): an image or icon when given, otherwise the initials of
 * [name] on `surface3`, always named, never coloured by identity. A failed [image] keeps the initials and adds the
 * failure glyph on a `ground` ring at the bottom-end corner, and "Can't show this image" after the name.
 *
 * States: loading, error
 * Text: frame (initials, capped at 1.3 ×)
 * Cap: Glyph
 * Motion: the loaded frame fades in on quick; Off = instant
 * Replaces: KitAvatar (lib/ui/kit/kit_image.dart)
 */
@Composable
public fun KitAvatar(
    name: String,
    modifier: Modifier = Modifier,
    image: KitImageSource? = null,
    icon: ImageVector? = null,
    size: KitAvatarSize = KitAvatarSize.Tile,
    decorative: Boolean = false,
    optionalImage: Boolean = false,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val diameter = if (size == KitAvatarSize.Tile) tokens.iconTile else FlutterKitTokens.MARK_SIZE // F: lib/ui/kit/kit_image.dart:528-530
    val role = if (size == KitAvatarSize.Tile) TypeRole.Label else TypeRole.Headline // F: lib/ui/kit/kit_image.dart:531-533
    val loaded = image?.let { rememberLoaded(it) }
    val failed = loaded == Loaded.Failed && !optionalImage
    val ring = FlutterKitTokens.AVATAR_BADGE_RING
    val badge = FlutterKitTokens.AVATAR_BADGE_SIZE
    val badgeStep = (2 * ring).dp // F: lib/ui/kit/kit_image.dart:552
    val unavailable = stringResource(Res.string.kitImageUnavailable)
    val mark =
        Box(
            Modifier.size(diameter.dp),
        ) {
            Box(Modifier.size(diameter.dp).clip(CircleShape).background(colors.surface3), contentAlignment = Alignment.Center) {
                Box(if (failed) Modifier.padding(end = badgeStep, bottom = badgeStep) else Modifier, contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        Image(icon, null, Modifier.size(tokens.smallIcon.dp), colorFilter = ColorFilter.tint(colors.text1))
                    } else {
                        val density = LocalDensity.current
                        CompositionLocalProvider(
                            LocalDensity provides Density(density.density, minOf(density.fontScale, FlutterKitTokens.MONOGRAM_MAX_TEXT_SCALE)),
                        ) {
                            KitText(avatarInitials(name), Modifier.wrapContentSize(unbounded = true), role = role, tone = TextTone.Primary, maxLines = 1) // F: lib/ui/kit/kit_image.dart:540-546
                        }
                    }
                }
                if (loaded is Loaded.Ready) {
                    CrossFade { fade ->
                        Image(loaded.image, null, fade.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                    }
                }
            }
            if (failed) {
                val inset = avatarBadgeInset(diameter.toFloat(), badge.toFloat(), ring.toFloat())
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-inset).dp, y = (-inset).dp).size(badge.dp)
                        .clip(CircleShape).background(colors.ground),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        KitIcons.error,
                        null,
                        Modifier.size((badge - 2 * ring).dp), // F: lib/ui/kit/kit_image.dart:678
                        colorFilter = ColorFilter.tint(colors.text1),
                    )
                }
            }
        }
    Box(
        if (decorative) modifier.clearAndSetSemantics {}
        else modifier.semantics(mergeDescendants = false) { contentDescription = if (failed) "$name, $unavailable" else name },
    ) { mark }
}

/** The badge's offset from the bottom-end corner: whole logical px (Dart `_avatarBadgeInset`, kit_image.dart:688-694). */
internal fun avatarBadgeInset(diameter: Float, badge: Float, ring: Float): Float {
    val radius = diameter / 2f // F: lib/ui/kit/kit_image.dart:690
    val centre = radius + (radius + badge / 2f - ring) * sqrt(1f / 2f) // F: lib/ui/kit/kit_image.dart:691-692
    return (diameter - centre - badge / 2f).roundToInt().toFloat() // F: lib/ui/kit/kit_image.dart:693
}

/** The first grapheme of [name]'s first two words, folded to upper case (Dart `_avatarInitials`, kit_image.dart:701-714). */
internal fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2) // F: lib/ui/kit/kit_image.dart:706
    return words.joinToString("") { firstGrapheme(it).uppercase() }
}

/** The first user-perceived character: a code point with its combining marks (a stand-in for `characters.first`). */
private fun firstGrapheme(word: String): String {
    var end = word.offsetByCodePoints0(0)
    while (end < word.length && isCombining(word[end])) end++
    return word.substring(0, end)
}

private fun String.offsetByCodePoints0(index: Int): Int = if (this[index].isHighSurrogate() && index + 1 < length) index + 2 else index + 1 // NV: a surrogate pair is two UTF-16 units

private fun isCombining(c: Char): Boolean = c in '̀'..'ͯ' || c in 'ً'..'ٟ' || c == '‍' || c in '︀'..'️'
