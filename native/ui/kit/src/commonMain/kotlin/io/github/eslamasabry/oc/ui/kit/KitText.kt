package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Frame text is capped at 1.3×; reading text follows the person's size up to 2.5× (plan 06 §9). */
public enum class KitTextKind { Frame, Reading }

/**
 * Text in one of the ten roles and seven tones (Dart `KitText`; plan 06 §7.1). Reading text picks its direction per
 * paragraph (an English reply in an Arabic interface reads left to right, K17); frame text follows the app locale.
 *
 * Text: frame or reading, by [kind].
 */
@Composable
public fun KitText(
    text: String,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
    tone: TextTone = defaultTone(role),
    kind: KitTextKind = KitTextKind.Reading,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    pageTitle: Boolean = false,
    sectionLabel: Boolean = false,
) {
    // pageTitle / sectionLabel are role hints; the head typography resolves the role alone.
    val base = LocalKitTypography.current.style(role)
    // A value that ends its row, or words centred on a page, keep their edge when the box is wider than the reading measure.
    val align = when (textAlign) {
        TextAlign.End -> CapAlign.End
        TextAlign.Center -> CapAlign.Center
        else -> CapAlign.Start
    }
    val modifier = modifier.kitMaxWidth(CapRole.Reading, align)
    val style =
        base.copy(
            color = LocalKitColors.current.tone(tone),
            textDirection = if (kind == KitTextKind.Reading) TextDirection.Content else TextDirection.Unspecified,
            textAlign = textAlign ?: TextAlign.Start,
        )
    if (kind == KitTextKind.Reading) {
        BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    // Frame text, the Flutter way: min(platform size at the current scale, 1.3 × spec), per role size. Compose text
    // reads only a density's font scale, so the words are laid out at that exact size under font scale 1 (1 sp = 1 dp),
    // with line height and tracking scaled in proportion. Measured in P7; see KitTextScale.kt.
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val specSp = style.fontSize.value
    val target = TextScalePolicy.frameDp(specSp, with(app) { specSp.sp.toDp().value })
    val k = target / specSp
    val sized =
        style.copy(
            fontSize = target.sp,
            lineHeight = (style.lineHeight.value * k).sp,
            letterSpacing = (style.letterSpacing.value * k).sp,
        )
    CompositionLocalProvider(LocalDensity provides Density(app.density, 1f)) {
        BasicText(text, modifier, style = sized, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** The tone a role takes when none is given: secondary for [TypeRole.Secondary], [TypeRole.Label] and [TypeRole.Caption] (Dart `KitText.defaultTone`). */
public fun defaultTone(role: TypeRole): TextTone =
    when (role) {
        TypeRole.Secondary, TypeRole.Label, TypeRole.Caption -> TextTone.Secondary
        else -> TextTone.Primary
    }

/** What [KitMonoText] does when the value does not fit (Dart `KitMonoCut`): wrap, one line with an end ellipsis, or a middle one. */
public enum class KitMonoCut { Wrap, End, Middle }

/**
 * A technical value the app did not write: a path, command, host, id, branch, URL or version (Dart `KitText.mono`). It
 * uses the mono role and is laid out left to right whatever the reading direction, aligned to the start of the ambient
 * direction. [KitMonoCut.Middle] keeps a path's root and its file name.
 *
 * Text: reading. Cap: reading (720).
 */
@Composable
public fun KitMonoText(
    text: String,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Primary,
    cut: KitMonoCut = KitMonoCut.Wrap,
    maxLines: Int = Int.MAX_VALUE,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val style =
        LocalKitTypography.current.style(TypeRole.Mono).copy(
            color = LocalKitColors.current.tone(tone),
            textDirection = TextDirection.Ltr,
            textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
        )
    val box = modifier.kitMaxWidth(CapRole.Reading, CapAlign.Start)
    when (cut) {
        KitMonoCut.Wrap -> BasicText(text, box, style = style, maxLines = maxLines, overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis)
        KitMonoCut.End -> BasicText(text, box, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        KitMonoCut.Middle -> {
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(box) {
                val display =
                    if (constraints.hasBoundedWidth) {
                        kitMonoTruncate(text, { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width.toFloat() }, constraints.maxWidth.toFloat(), keepTail = true)
                    } else text
                BasicText(display, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val MONO_ELLIPSIS = "…"

/** `_kitMonoTruncate` of kit_text.dart: the longest [text] that fits [maxWidth], cut at the end or in the middle. */
internal fun kitMonoTruncate(text: String, widthOf: (String) -> Float, maxWidth: Float, keepTail: Boolean): String {
    if (widthOf(text) <= maxWidth) return text
    if (!keepTail) {
        var low = 0
        var high = text.length
        var best = MONO_ELLIPSIS
        while (low <= high) {
            val mid = (low + high) / 2 // F: lib/ui/kit/kit_text.dart:1016
            val candidate = text.substring(0, mid) + MONO_ELLIPSIS
            if (widthOf(candidate) <= maxWidth) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }
    // The last segment with its separator ('/main.dart'), or nothing when the value has no separator.
    val lastSeparator = text.lastIndexOfAny(charArrayOf('/', '\\'))
    val fileName = if (lastSeparator > 0) text.length - lastSeparator else 0
    fun candidateOf(kept: Int): String {
        val even = kept - kept / 2 // F: lib/ui/kit/kit_text.dart:1033
        val tail = if (kept <= 1) 0 else (if (fileName < kept) fileName else kept - 1).coerceIn(even, kept - 1)
        val head = kept - tail
        return text.substring(0, head) + MONO_ELLIPSIS + text.substring(text.length - tail)
    }
    var low = 1
    var high = text.length - 1
    var best = MONO_ELLIPSIS
    while (low <= high) {
        val mid = (low + high) / 2 // F: lib/ui/kit/kit_text.dart:1046
        val candidate = candidateOf(mid)
        if (widthOf(candidate) <= maxWidth) {
            best = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

/** Lays [content] out left to right whatever the reading direction (Dart `KitLtr`): a path or a command never reorders. */
@Composable
public fun KitLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** How a [KitSelectable] region turns on: always, or only while a fine pointer is in use (Dart `KitSelectMode`). */
public enum class KitSelectMode { Always, FinePointer }

/** Makes the text under it selectable and copyable (Dart `KitSelectable`); long-press on touch, drag with a mouse. */
@Composable
public fun KitSelectable(modifier: Modifier = Modifier, mode: KitSelectMode = KitSelectMode.Always, content: @Composable () -> Unit) {
    SelectionContainer(modifier) { content() }
}

/** Text under it is left out of selection (Dart `KitSelectable.excluded`). */
@Composable
public fun KitSelectableExcluded(content: @Composable () -> Unit) {
    DisableSelection(content)
}

/** Slice-0 roles the first feature screens use. Kept so P1's screens compile; new code passes a [TypeRole]. */
public enum class KitTextRole { Title, Body, Supporting }

@Composable
public fun KitText(text: String, role: KitTextRole, modifier: Modifier = Modifier) {
    when (role) {
        KitTextRole.Title -> KitText(text, modifier, TypeRole.Title, kind = KitTextKind.Frame)
        KitTextRole.Body -> KitText(text, modifier, TypeRole.Body)
        KitTextRole.Supporting -> KitText(text, modifier, TypeRole.Secondary, TextTone.Secondary)
    }
}
