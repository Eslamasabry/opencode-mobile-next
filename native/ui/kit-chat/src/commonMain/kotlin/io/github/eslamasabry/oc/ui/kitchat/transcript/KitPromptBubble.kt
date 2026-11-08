package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitAttachmentImage
import io.github.eslamasabry.oc.ui.words.res.kitAttachmentOpen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.words.res.kitAttachmentFile
import io.github.eslamasabry.oc.ui.words.res.kitAttachmentFolder
import io.github.eslamasabry.oc.ui.words.res.kitAttachmentReference
import kotlin.math.ceil
import org.jetbrains.compose.resources.stringResource

/** A sent photo's square tile in a prompt bubble (Flutter `KitLayout.promptPhotoSize`): three fit side by side. */
internal const val PROMPT_PHOTO_SIZE: Int = 96

/** The bubble's vertical padding (Flutter `KitLayout.bubblePaddingVertical`); the horizontal is `space3`. */
internal const val BUBBLE_PADDING_VERTICAL: Int = 10

/** The bubble's share of the column (Flutter `KitLayout.bubbleMaxShare`, owner decision 04A). */
private const val BUBBLE_MAX_SHARE = 0.85f

/**
 * The bubble's corners (Flutter `_bubbleRadius`, kit_message.dart:309-320): 20 everywhere but the bottom end, the
 * tail (6). Start and end follow the layout direction, so the tail mirrors in Arabic.
 */
internal fun kitBubbleShape(): RoundedCornerShape =
    RoundedCornerShape(
        topStart = FlutterKitTokens.BUBBLE_RADIUS.dp,
        topEnd = FlutterKitTokens.BUBBLE_RADIUS.dp,
        bottomEnd = FlutterKitTokens.BUBBLE_TAIL_RADIUS.dp,
        bottomStart = FlutterKitTokens.BUBBLE_RADIUS.dp,
    )

/**
 * The bubble's fill (Flutter `kitPromptBubbleFill`, kit_message.dart:303-306). Dark: `surface2`. Light: `surface2` is
 * white, the page's own colour, so the bubble would vanish; it is `text1` at 7 % over the ground instead.
 */
internal fun kitPromptBubbleFill(colors: KitColors): Color =
    if (colors.dark) colors.surface2 else colors.text1.copy(alpha = 0.07f).compositeOver(colors.ground)

/**
 * The person's prompt (Flutter `_Prompt` + `_Bubble`, kit_message.dart:322-640): a bubble at the end side, at most
 * 85 % of the column (R13), hugging its longest line. Corners 20/20/20 with a 6 dp tail at the bottom end, padding
 * 12 × 10, the bubble fill ([kitPromptBubbleFill]) with a hairline edge. Graphite, never mint: mint is kept for
 * actions and live things, never the person's words.
 *
 * [photos] (C-010) show under the words as 96 dp square tiles (tile corners, cropped to fill), 8 dp below the words
 * and 8 dp apart; a tile whose picture is still loading is a quiet square that fades into the picture.
 */
@Composable
public fun KitPromptBubble(
    text: String,
    modifier: Modifier = Modifier,
    photos: List<KitPromptPhoto> = emptyList(),
    attachments: List<KitPromptAttachment> = emptyList(),
    time: String? = null,
) {
    val colors = LocalKitColors.current
    val shape = kitBubbleShape()
    Box(modifier.then(column()), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.fillMaxWidth(BUBBLE_MAX_SHARE), horizontalAlignment = Alignment.End) {
            Column(
                // Plain words hug their longest line; anything with attachments takes the whole share
                // (kit_message.dart:629-641).
                (if (attachments.isEmpty()) Modifier else Modifier.fillMaxWidth())
                    .testTag(PROMPT_BUBBLE_TAG)
                    .background(kitPromptBubbleFill(colors), shape)
                    .border(kitHairline(), colors.hairline, shape)
                    .padding(horizontal = Space.S3.dp, vertical = BUBBLE_PADDING_VERTICAL.dp)
            ) {
                if (text.isNotEmpty()) PromptWords(text)
                if (photos.isNotEmpty()) {
                    if (text.isNotEmpty()) Spacer(Modifier.height(Space.S2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        photos.forEach { PromptPhoto(it) }
                    }
                }
                if (attachments.isNotEmpty()) {
                    // F: lib/ui/kit/chat/kit_message.dart:613-620 — read-only KitComposerChips.attachments.
                    Spacer(Modifier.height(Space.S2.dp))
                    KitChipWrap { attachments.forEach { PromptAttachmentChip(it) } }
                }
            }
            if (time != null) {
                // F: lib/ui/kit/chat/kit_message.dart:396-410 — caption, tertiary, space1 under the bubble, space2 from its end.
                KitText(
                    time,
                    Modifier.padding(top = Space.S1.dp, end = Space.S2.dp),
                    role = TypeRole.Caption,
                    tone = TextTone.Tertiary,
                )
            }
        }
    }
}

/** What a read-only attachment chip in a sent prompt is (Dart `KitAttachmentKind`). */
public enum class KitPromptAttachmentKind { File, Image, Folder, Reference }

/** One file, picture, folder or reference a sent prompt carried, drawn as a read-only chip in the bubble. */
public data class KitPromptAttachment(val label: String, val kind: KitPromptAttachmentKind = KitPromptAttachmentKind.File)

/**
 * A read-only attachment chip (kit_composer_chips.dart `_AttachmentChip` without onRemove/onOpen): the touch row of
 * the minimum target, the `surface3` pill of the pill height inside it, space3 either side, the small glyph in text1,
 * space2, the name in mono cut in the middle. Spoken as "File, checkout_test.dart".
 */
@Composable
private fun PromptAttachmentChip(item: KitPromptAttachment) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val spoken =
        when (item.kind) {
            KitPromptAttachmentKind.Image -> stringResource(Res.string.kitAttachmentImage, item.label)
            KitPromptAttachmentKind.File -> stringResource(Res.string.kitAttachmentFile, item.label)
            KitPromptAttachmentKind.Folder -> stringResource(Res.string.kitAttachmentFolder, item.label)
            KitPromptAttachmentKind.Reference -> stringResource(Res.string.kitAttachmentReference, item.label)
        }
    val glyph =
        when (item.kind) {
            KitPromptAttachmentKind.Image -> KitIcons.image
            KitPromptAttachmentKind.File -> KitIcons.file
            KitPromptAttachmentKind.Folder -> KitIcons.folders
            KitPromptAttachmentKind.Reference -> KitIcons.bookmark
        }
    Box(
        Modifier.heightIn(min = tokens.minTarget.dp).widthIn(min = tokens.minTarget.dp).clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.background(colors.surface3, CircleShape)
                .heightIn(min = FlutterKitTokens.PILL_HEIGHT.dp)
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(glyph, size = KitIconSize.Small, tone = TextTone.Primary)
            Spacer(Modifier.width(Space.S2.dp))
            KitMonoText(item.label, cut = KitMonoCut.Middle)
        }
    }
}

public const val PROMPT_BUBBLE_TAG: String = "kit-prompt-bubble"
public const val PROMPT_PHOTO_TAG: String = "kit-prompt-photo"

/**
 * The prompt's words, hugging their longest line (Flutter `_hugWidth`): a wrapped paragraph would otherwise take the
 * whole share and leave an empty band at its end.
 */
@Composable
private fun PromptWords(text: String) {
    val colors = LocalKitColors.current
    val holder = remember { LayoutHolder() }
    BasicText(
        text = text,
        style =
            // QA wave 4 B12: the person's own words read in their own direction (an English prompt in an
            // Arabic interface is left to right, its full stop at the end).
            LocalKitTypography.current.style(TypeRole.Body).copy(
                color = colors.text1,
                textDirection = TextDirection.Content,
            ),
        onTextLayout = { holder.layout = it },
        modifier = Modifier.hugLongestLine(holder),
    )
}

/** Narrows a wrapped text to its longest line, keeping each line where the paragraph's own direction puts it. */
private fun Modifier.hugLongestLine(holder: LayoutHolder): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val text = holder.layout
    val width =
        if (text == null || text.lineCount < 2) placeable.width
        else
            ceil((0 until text.lineCount).maxOf { text.getLineRight(it) - text.getLineLeft(it) })
                .toInt()
                .coerceIn(constraints.minWidth, placeable.width)
    val rtl = text != null && text.getParagraphDirection(0) == ResolvedTextDirection.Rtl
    layout(width, placeable.height) { placeable.place(if (rtl) width - placeable.width else 0, 0) }
}

@Composable
private fun PromptPhoto(photo: KitPromptPhoto) {
    val colors = LocalKitColors.current
    val corner = RoundedCornerShape(FlutterKitTokens.ICON_TILE_RADIUS.dp)
    val described = stringResource(Res.string.kitAttachmentImage, photo.label)
    val tile =
        Modifier.size(PROMPT_PHOTO_SIZE.dp).clip(corner).background(colors.surface3).testTag(PROMPT_PHOTO_TAG)
    val picture =
        @Composable {
            PhotoFade(photo.image) { image ->
                if (image != null)
                    Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize())
            }
        }
    val open = photo.onOpen
    if (open == null) {
        Box(tile.semantics { contentDescription = described }) { picture() }
    } else {
        val openLabel = stringResource(Res.string.kitAttachmentOpen, photo.label)
        KitTappable(
            onClick = open,
            modifier = tile.semantics { contentDescription = openLabel },
            shape = KitShapeId.Tile,
        ) {
            picture()
        }
    }
}

/** The picture fades in over the quiet tile when it arrives (QUICK, 150 ms); reduced motion swaps at once. */
@Composable
private fun <T> PhotoFade(target: T, content: @Composable (T) -> Unit) {
    val spec: FiniteAnimationSpec<Float> = if (KitMotion.state.reduced) snap() else KitMotion.specs.pace(KitPace.Quick)
    Crossfade(target, animationSpec = spec, label = "photo") {
        content(it)
    }
}
