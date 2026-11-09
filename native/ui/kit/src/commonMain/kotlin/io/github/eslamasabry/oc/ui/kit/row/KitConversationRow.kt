package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowTitle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitPinned
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The small tile a conversation row puts before its project's name (B-161): two letters and a colour, both derived from
 * the project's name alone, so the same project looks the same on every row, every start and every phone.
 */
public object KitProjectMark {
    /**
     * Two letters for [name]: the first letters of its first two words ("opencode-mobile-next" → "OM"), else its
     * first two letters ("api" → "AP"); empty for a blank name. Upper-cased without a locale, so it never changes
     * with the phone's language.
     */
    public fun letters(name: String): String {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val picked =
            when {
                words.isEmpty() -> ""
                words.size == 1 -> words[0].firstCodePoints(2)
                else -> words[0].firstCodePoints(1) + words[1].firstCodePoints(1)
            }
        return picked.uppercase()
    }

    /** A hue in [0, 360) that only [name] decides (FNV-1a over its lower-cased UTF-16 units). */
    public fun hue(name: String): Float {
        var hash = FNV_OFFSET
        for (c in name.trim().lowercase()) {
            hash = hash xor c.code.toUInt()
            hash *= FNV_PRIME
        }
        return (hash % HUES).toFloat() / HUES.toFloat() * 360f
    }

    private fun String.firstCodePoints(count: Int): String {
        var i = 0
        var n = 0
        while (i < length && n < count) {
            i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
            n++
        }
        return substring(0, i)
    }

    private const val FNV_OFFSET: UInt = 2166136261u
    private const val FNV_PRIME: UInt = 16777619u
    private const val HUES: UInt = 360u
}

/**
 * A live run on a [KitConversationRow]: the row trades its time for a small turning mark and [label] in the accent
 * ("Working 34m"). [label] gets the run's length in milliseconds, or null when its start is unknown.
 */
public class KitRowLive(public val sinceMs: Long?, public val nowMs: Long, public val label: (elapsedMs: Long?) -> String)

/**
 * What one [KitConversationRow] shows: the [title]; the [project] (with its tile) and the [time] on the top line, or the
 * [live] run in place of the time; the [state] word, [branch] and [server] on the quiet line; the [agent]'s mark at
 * the end. [done] marks a finished run with a check; [pinned] adds a quiet pin; [growKey] is the conversation the page
 * grows out of.
 */
public data class KitConversation(
    val title: String,
    val project: String,
    val time: String?,
    val state: KitRowState?,
    val branch: String? = null,
    val server: String? = null,
    val live: KitRowLive? = null,
    val agent: KitFeedAgent? = null,
    val done: Boolean = false,
    val pinned: Boolean = false,
    val growKey: Any? = null,
)

/**
 * The home's conversation row (B-161). Top line: the project's tile and name, and the time at the end (while a run is
 * live, a small turning mark and "Working 34m" in the accent instead). Then the title. Then one quiet line: the state
 * word when it matters (needs you, failed, done; the live run already says it works), the branch and the server. With
 * an agent, the agent's small monochrome mark closes the row. The row draws no fill of its own: it lies on the page's
 * ground with its neighbours, so the list reads as one surface (E20-S24). Long-press opens the row's actions; TalkBack
 * hears the whole row as one sentence.
 *
 * States: default, live, needs you, done, failed, pinned, untappable
 * Text: title 1 line (2 at large text), top and meta lines 1 line (wrap at large text)
 * Cap: List
 * Motion: the live mark turns (a still dot when motion is reduced); the run length moves every 30 s
 * Hidden: long-press (the row's menu; the same acts are on the conversation's own page)
 * Replaces: none (Flutter's KitFeedItem keeps one meta line; see the B-161 parity note)
 */
@Composable
public fun KitConversationRow(
    row: KitConversation,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val liveText = row.live?.let { rememberLiveLabel(it) }
    val pinnedWord = if (row.pinned) stringResource(Res.string.kitPinned) else null
    val words = spokenWords(row, liveText, pinnedWord)
    val m =
        modifier
            .kitMaxWidth(CapRole.List)
            .kitGrowSource(row.growKey)
            .testTag("kit-conversation-row")
            .semantics(mergeDescendants = true) { contentDescription = words }
    val body: @Composable () -> Unit = { ConversationBody(row, liveText) }
    if (onClick == null) Box(m) { body() }
    else KitTappable(onClick, m, role = KitTappableRole.Row, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel) { body() }
}

/** The state word the quiet line shows: none while live and working, since "Working 34m" already says it. */
private fun shownWord(row: KitConversation): String? =
    row.state?.word?.takeUnless { row.live != null && row.state.tone == KitRowStateTone.Running }

/** The row heard in reading order; while live, "Working 34m" stands for both the state word and the time. */
private fun spokenWords(row: KitConversation, liveText: String?, pinnedWord: String?): String =
    listOfNotNull(
            row.title,
            pinnedWord,
            row.agent?.name,
            shownWord(row),
            liveText,
            row.project.takeIf { it.isNotBlank() },
            row.branch?.takeIf { it.isNotBlank() },
            row.server?.takeIf { it.isNotBlank() },
            row.time.takeIf { row.live == null },
        )
        .joinToString(", ")

@Composable
private fun ConversationBody(row: KitConversation, liveText: String?) {
    val colors = LocalKitColors.current
    val large = LocalDensity.current.fontScale > LARGE_TEXT
    Row(
        Modifier.fillMaxWidth().padding(horizontal = kitRowGutter(), vertical = Space.S3.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            TopLine(row, liveText, large)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                KitText(row.title, Modifier.weight(1f, fill = false).kitGrowTitle(row.growKey), role = TypeRole.RowTitle, maxLines = if (large) 2 else 1)
                if (row.pinned) KitGlyph(KitIcons.pin, colors.text2, 14.dp)
            }
            MetaLine(row, large)
        }
        row.agent?.let { KitProviderLogo(it.id, it.name, agent = true, Modifier.size(28.dp), markSize = 28.dp) }
    }
}

/** The project's tile and name, then the time, or the live mark and "Working 34m". */
@Composable
private fun TopLine(row: KitConversation, liveText: String?, large: Boolean) {
    val colors = LocalKitColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (row.project.isNotBlank()) {
            ProjectTile(row.project)
            BasicText(
                "\u2068${row.project}\u2069",
                Modifier.weight(1f),
                style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2),
                maxLines = if (large) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else Box(Modifier.weight(1f))
        if (row.live != null) {
            Row(
                Modifier.testTag("kit-conversation-live"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                KitWorkingMark(colors.accent, size = 12.dp)
                KitText(liveText.orEmpty(), role = TypeRole.Secondary, tone = TextTone.Accent, maxLines = 1)
            }
        } else if (row.time != null) {
            KitText(row.time.replace(' ', '\u00a0'), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
        }
    }
}

/** The quiet line: the state word in its colour (a check before Done), then the branch and the server. */
@Composable
private fun MetaLine(row: KitConversation, large: Boolean) {
    val colors = LocalKitColors.current
    val word = shownWord(row)
    val tone = row.state?.tone
    val strong = tone == KitRowStateTone.NeedsYou || tone == KitRowStateTone.Running
    val statusColor =
        when (tone) {
            KitRowStateTone.NeedsYou -> colors.attention
            KitRowStateTone.Running -> colors.accent
            else -> colors.text2
        }
    val meta = buildAnnotatedString {
        if (word != null) {
            if (row.done) append("✓ ")
            withStyle(SpanStyle(color = statusColor, fontWeight = if (strong) FontWeight.SemiBold else null)) { append(word) }
        }
        listOfNotNull(row.branch?.takeIf { it.isNotBlank() }, row.server?.takeIf { it.isNotBlank() }).forEach {
            if (length > 0) append("\u00a0· ")
            append("\u2068$it\u2069")
        }
    }
    if (meta.isNotEmpty())
        BasicText(
            meta,
            style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2),
            maxLines = if (large) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
}

/** The project's two letters on its own colour: a soft tint of the project's hue, the letters in the page's ink. */
@Composable
private fun ProjectTile(project: String) {
    val colors = LocalKitColors.current
    val hue = remember(project) { KitProjectMark.hue(project) }
    val letters = remember(project) { KitProjectMark.letters(project) }
    val fill = if (colors.dark) Color.hsl(hue, TILE_SATURATION_DARK, TILE_LIGHTNESS_DARK) else Color.hsl(hue, TILE_SATURATION, TILE_LIGHTNESS)
    val size = (TILE_DP * minOf(LocalDensity.current.fontScale, LARGE_TEXT)).dp
    Box(
        Modifier.size(size).background(fill, KitShapeId.Tile.shape()).testTag("kit-project-tile").clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            letters,
            style =
                LocalKitTypography.current
                    .style(TypeRole.Caption)
                    .copy(color = colors.text1, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, lineHeight = 10.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [live]'s words, moved on every 30 s while the row shows (a monotonic clock: no wall clock in the kit). */
@Composable
private fun rememberLiveLabel(live: KitRowLive): String {
    val started = remember(live.sinceMs, live.nowMs) { TimeSource.Monotonic.markNow() }
    var elapsed by
        remember(live.sinceMs, live.nowMs) {
            mutableLongStateOf(live.sinceMs?.let { (live.nowMs - it).coerceAtLeast(0) } ?: -1L)
        }
    val still = LocalKitMotion.current.stillFrame
    LaunchedEffect(live.sinceMs, live.nowMs, still) {
        // A still frame (tests, screenshots) keeps the length it was given.
        val since = live.sinceMs?.takeUnless { still } ?: return@LaunchedEffect
        while (true) {
            delay(LIVE_TICK_MS)
            elapsed = (live.nowMs - since).coerceAtLeast(0) + started.elapsedNow().inWholeMilliseconds
        }
    }
    return live.label(elapsed.takeIf { it >= 0 })
}

private const val LIVE_TICK_MS = 30_000L
private const val LARGE_TEXT = 1.3f
private const val TILE_DP = 18
private const val TILE_SATURATION = 0.55f
private const val TILE_LIGHTNESS = 0.86f
private const val TILE_SATURATION_DARK = 0.45f
private const val TILE_LIGHTNESS_DARK = 0.30f
