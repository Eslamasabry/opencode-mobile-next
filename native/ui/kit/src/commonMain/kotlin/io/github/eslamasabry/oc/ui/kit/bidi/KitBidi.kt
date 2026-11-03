package io.github.eslamasabry.oc.ui.kit.bidi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind

/**
 * Keeps technical values (paths, commands, code, URLs, ids, amounts) whole inside right-to-left copy (Flutter
 * `KitBidi`, `KitFactRow.isolateValues`; B-L10N-006). The value is wrapped in a Unicode directional isolate, so an
 * Arabic sentence around it never reorders its slashes, dots or dashes, and the value never reorders the sentence.
 */
public object KitBidi {
    /** LEFT-TO-RIGHT ISOLATE. */
    public const val LRI: Char = '⁦'

    /** FIRST STRONG ISOLATE: the direction follows the value's first strong character. */
    public const val FSI: Char = '⁨'

    /** POP DIRECTIONAL ISOLATE: closes [LRI] or [FSI]. */
    public const val PDI: Char = '⁩'

    /** [s] isolated as left-to-right: paths, commands, code, URLs, hashes. Empty or already isolated text is kept. */
    public fun ltr(s: String): String = if (s.isEmpty() || isolated(s)) s else "$LRI$s$PDI"

    /** [s] isolated in its own first strong direction: names or titles that may be Arabic or Latin. */
    public fun auto(s: String): String = if (s.isEmpty() || isolated(s)) s else "$FSI$s$PDI"

    private fun isolated(s: String): Boolean = (s.first() == LRI || s.first() == FSI) && s.last() == PDI

    /**
     * Amounts, card endings, times, addresses and Latin names in [text], each isolated left to right as one run. Only
     * a right-to-left layout needs the marks, so [rtl] false returns [text] as written.
     */
    public fun isolateValues(text: String, rtl: Boolean): String =
        if (!rtl) text else valueRuns.replace(text) { ltr(it.value) }

    private const val RUN_CHAR = """(?:[A-Za-z0-9_\[\]•@+\-/%$]|[.,:](?=[A-Za-z0-9_\[\]•]))"""
    private val valueRuns = Regex("""$RUN_CHAR+(?: $RUN_CHAR+)*""")
}

/**
 * A [KitText] showing [value] isolated left to right ([KitBidi.ltr]). The paragraph keeps the ambient direction, so it
 * still lines up with the screen; only the value's own order is fixed.
 */
@Composable
public fun KitLtrText(
    value: String,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
    tone: TextTone = TextTone.Primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    KitText(KitBidi.ltr(value), modifier, role, tone, KitTextKind.Frame, maxLines)
}

/** [text] with its values isolated when the layout is right to left ([KitBidi.isolateValues]). */
@Composable
public fun kitIsolatedValues(text: String): String =
    KitBidi.isolateValues(text, rtl = LocalLayoutDirection.current == LayoutDirection.Rtl)
