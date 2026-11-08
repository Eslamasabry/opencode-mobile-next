package io.github.eslamasabry.oc.ui.kit.content.log

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyAll
import io.github.eslamasabry.oc.ui.words.res.kitLogShowOutput
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.words.res.kitLogEmpty
import io.github.eslamasabry.oc.ui.words.res.kitLogFollow
import io.github.eslamasabry.oc.ui.words.res.kitLogNextMatch
import io.github.eslamasabry.oc.ui.words.res.kitLogPause
import io.github.eslamasabry.oc.ui.words.res.kitLogPreviousMatch
import io.github.eslamasabry.oc.ui.words.res.kitLogSearch
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPillLayer
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.kitLogDropped
import io.github.eslamasabry.oc.ui.words.res.kitLogEnded
import io.github.eslamasabry.oc.ui.words.res.kitLogEndedExit
import io.github.eslamasabry.oc.ui.words.res.kitLogErrorLine
import io.github.eslamasabry.oc.ui.words.res.kitLogFailed
import io.github.eslamasabry.oc.ui.words.res.kitLogFailedExit
import io.github.eslamasabry.oc.ui.words.res.kitLogLive
import io.github.eslamasabry.oc.ui.words.res.kitLogNewLines
import io.github.eslamasabry.oc.ui.words.res.kitLogQuiet
import io.github.eslamasabry.oc.ui.words.res.kitLogQuietSeconds
import io.github.eslamasabry.oc.ui.words.res.kitLogReadFailed
import io.github.eslamasabry.oc.ui.words.res.kitLogTitle
import io.github.eslamasabry.oc.ui.words.res.kitLogWarningLine
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Searchable process output and report text (Flutter `kit_log_panel_tools.dart`). The host supplies
 * already masked [redactedText] (the diagnostics engine issues redacted report previews); display,
 * search, selection and copy all use that same text. The list is bounded and lazy. Search starts at
 * the newest matching line, Previous/Next wrap, and Pause holds the reading position while new
 * output arrives. Follow resumes at the newest line; scrolling up also stops following. The host
 * supplies updates, never an internal poll.
 *
 * [live] false is a finished document (a report preview): every line is kept, it opens at the top
 * and there is nothing to pause or follow.
 */
@Composable
public fun KitLogPanel(
    redactedText: String,
    modifier: Modifier = Modifier,
    live: Boolean = true,
) {
    val safe = redactedText
    val lines =
        remember(safe, live) {
            if (live) safe.lines().takeLast(KitLogBuffer.DEFAULT_CAPACITY) else safe.lines()
        }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(live) }
    var wrap by remember { mutableStateOf(true) }
    val matches =
        remember(lines, query) {
            if (query.isBlank()) emptyList()
            else lines.indices.filter { lines[it].contains(query, ignoreCase = true) }
        }
    val list = rememberLazyListState()
    val colors = LocalKitColors.current
    val style =
        LocalKitTypography.current
            .style(TypeRole.Mono)
            .copy(color = colors.text1, textDirection = TextDirection.Ltr)
    @Suppress(
        "DEPRECATION"
    ) // Kit copy boundary; the clipboard receives only the masked text above.
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(query) {
        current = if (matches.isEmpty()) -1 else matches.lastIndex
        if (current >= 0) {
            following = false
            list.scrollToItem(matches[current])
        }
    }
    LaunchedEffect(safe, paused, following) {
        if (live && !paused && following && safe.isNotEmpty()) list.scrollToItem(lines.lastIndex)
    }
    LaunchedEffect(current) {
        if (current in matches.indices) list.scrollToItem(matches[current])
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }
            .collect { (scrolling, more) ->
                if (scrolling && live) following = !more
            }
    }
    val active = matches.getOrNull(current)
    Column(
        modifier.fillMaxWidth().testTag("kit-log-panel"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitSearchField(
            label = stringResource(Res.string.kitLogSearch),
            modifier = Modifier.testTag("kit-log-search"),
            onSearch = { query = it.trim() },
            resultCount = if (query.isEmpty()) null else matches.size,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (matches.size > 1) {
                KitButton(
                    stringResource(Res.string.kitLogPreviousMatch),
                    onClick = {
                        following = false
                        current = (current - 1 + matches.size) % matches.size
                    },
                    modifier = Modifier.weight(1f).testTag("kit-log-prev"),
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.kitLogNextMatch),
                    onClick = {
                        following = false
                        current = (current + 1) % matches.size
                    },
                    modifier = Modifier.weight(1f).testTag("kit-log-next"),
                    role = KitButtonRole.Tertiary,
                )
            }
            if (live) KitButton(
                stringResource(
                    if (paused || !following) Res.string.kitLogFollow else Res.string.kitLogPause
                ),
                onClick = {
                    if (paused || !following) {
                        paused = false
                        following = true
                    } else {
                        paused = true
                        following = false
                    }
                },
                modifier = Modifier.weight(1f).testTag("kit-log-pause"),
                role = KitButtonRole.Secondary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitButton(
                stringResource(Res.string.kitWrapLines),
                onClick = { wrap = !wrap },
                modifier = Modifier.weight(1f).testTag("kit-log-wrap"),
                role = if (wrap) KitButtonRole.Secondary else KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.kitCopyAll),
                onClick = { clipboard.setText(AnnotatedString(safe)) },
                modifier = Modifier.weight(1f).testTag("kit-log-copy-all"),
                role = KitButtonRole.Tertiary,
            )
        }
        if (safe.isEmpty()) {
            KitText(stringResource(Res.string.kitLogEmpty), tone = TextTone.Secondary)
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer {
                    LazyColumn(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 120.dp, max = 400.dp)
                            .testTag("kit-log-lines")
                            .then(
                                if (wrap) Modifier
                                else Modifier.horizontalScroll(rememberScrollState())
                            ),
                        state = list,
                    ) {
                        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                            val marked = buildAnnotatedString {
                                var start = 0
                                while (query.isNotEmpty()) {
                                    val at = line.indexOf(query, start, ignoreCase = true)
                                    if (at < 0) break
                                    append(line.substring(start, at))
                                    withStyle(
                                        SpanStyle(
                                            background =
                                                colors.accent.copy(
                                                    alpha = if (index == active) .5f else .24f
                                                )
                                        )
                                    ) {
                                        append(line.substring(at, at + query.length))
                                    }
                                    start = at + query.length
                                }
                                append(
                                    line.substring(start).ifEmpty {
                                        if (line.isEmpty()) " " else ""
                                    }
                                )
                            }
                            BasicText(
                                marked,
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (index == active) colors.surface3 else colors.ground
                                    )
                                    .padding(horizontal = Space.S2.dp, vertical = 2.dp),
                                style = style,
                                softWrap = wrap,
                                maxLines = if (wrap) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bounded selectable output. Levels are spoken in words as well as shown in the attention tone. */
@Composable
public fun KitLogPanel(
    lines: List<KitLogLine>,
    title: String,
    warningLabel: String,
    errorLabel: String,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberLazyListState()
    val follow = !scroll.canScrollForward
    LaunchedEffect(lines) {
        if (follow && lines.isNotEmpty()) scroll.scrollToItem(lines.lastIndex)
    }
    Column(modifier.fillMaxWidth()) {
        KitText(title, role = TypeRole.Headline)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), state = scroll) {
                    itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                        val label =
                            when (line.level) {
                                KitLogLevel.Normal -> line.text
                                KitLogLevel.Warning -> "$warningLabel: ${line.text}"
                                KitLogLevel.Error -> "$errorLabel: ${line.text}"
                            }
                        KitText(
                            label.ifEmpty { " " },
                            role = TypeRole.Mono,
                            tone =
                                if (line.level == KitLogLevel.Normal) TextTone.Secondary
                                else TextTone.Attention,
                        )
                    }
                }
            }
        }
    }
}

/** How tall a [KitLogPanel] over a [KitLogBuffer] is (Dart `KitLogSize`). */
public enum class KitLogSize {
    /** About 12 lines tall: the panel inside a details fold or a card. */
    Folded,

    /** Fills its host (a page whose job is the log, a full-height sheet); the host bounds its height. */
    Fill,
}

/** How the source ended (Dart `KitLogEnd`): "Ended · exit 1", or "Failed" with one [reason] in words. */
public data class KitLogEnd(val exitCode: Int? = null, val failed: Boolean = false, val reason: String? = null)

/** The pure rules of the [KitLogBuffer] panel (Dart `_KitLogPanelState`), kept apart so they are tested alone. */
public object KitLogRules {
    /** Lines the panel does not show: the buffer's dropped ones plus those past [maxLines]. */
    public fun dropped(buffered: Int, lines: Int, maxLines: Int): Int = buffered + maxOf(0, lines - maxLines)

    /** A folded panel's body height: 12 lines and its padding, never more than .45 of the window. */
    public fun foldedHeight(extent: Float, window: Float, space2: Float): Float =
        kotlin.math.floor(minOf(FlutterKitTokens.LOG_FOLDED_LINES * extent + space2 * 2, window * .45f)) // F: lib/ui/kit/kit_log_panel.dart:762,765

    /** Null wraps on a compact window and scrolls sideways from medium; the person's toggle wins (Dart `_wrapOn`). */
    public fun wraps(controlled: Boolean?, own: Boolean?, compact: Boolean): Boolean = controlled ?: own ?: compact

    /** The jump pill shows once the person left the newest line (or paused) and new lines arrived. */
    public fun showsJump(follow: Boolean, following: Boolean, unseen: Int): Boolean = follow && !following && unseen > 0
}

/**
 * The one log view (Dart `KitLogPanel`, lib/ui/kit/kit_log_panel.dart; K2 §1.11): a header with the title, the state in
 * words (Live with its dot, "Last line 8 s ago" when quiet, Ended or Failed with the exit code and reason), Wrap, Copy
 * all and the host's [headerAction]; then the lines in mono, left to right in every locale, a neutral gutter glyph on
 * warnings and errors, and "N earlier lines not shown" when the buffer dropped some. It follows the newest line until
 * the person scrolls up; then a jump pill counts the new lines. [onRefresh] is called every 2 s until [ended], and a
 * failed read (it returns false) shows "Couldn't read the output" with Try again. Every line is masked by [KitRedact]
 * before it is drawn or copied.
 *
 * States: empty, live, quiet, ended, failed, failed-to-read, scrolled-up, dropped
 * Text: reading (mono)
 * Cap: List (960)
 * Motion: the jump pill's own; the jump to the newest line is instant under reduced motion
 * Replaces: KitLogPanel (lib/ui/kit/kit_log_panel.dart)
 */
@Composable
public fun KitLogPanel(
    lines: KitLogBuffer,
    modifier: Modifier = Modifier,
    title: String? = null,
    live: Boolean = false,
    ended: KitLogEnd? = null,
    onRefresh: (suspend () -> Boolean)? = null,
    follow: Boolean = true,
    maxLines: Int = KitLogBuffer.DEFAULT_CAPACITY,
    emptyText: String? = null,
    size: KitLogSize = KitLogSize.Folded,
    wrap: Boolean? = null,
    onWrapChanged: ((Boolean) -> Unit)? = null,
    headerAction: KitAction? = null,
) {
    val all = lines.lines
    val shown = if (all.size > maxLines) all.takeLast(maxLines) else all
    val dropped = KitLogRules.dropped(lines.dropped, all.size, maxLines)
    val total = all.size + dropped
    val safe = remember(shown) { shown.map { KitRedact.text(it.text) } }
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val density = LocalDensity.current
    val window = LocalKitWindow.current
    val folded = size == KitLogSize.Folded
    var ownWrap by remember { mutableStateOf<Boolean?>(null) }
    val wrapOn = KitLogRules.wraps(wrap, ownWrap, window.cls == WindowClass.Compact)
    val glyph = tokens.smallIcon.dp * minOf(kitTextScale(), tokens.maxIconScale)
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val extent = with(density) { maxOf(mono.lineHeight.toDp(), glyph) }.value.let { kotlin.math.ceil(it) }.dp

    // Following and the new-line count (Dart `_onLines`, `_onScroll`).
    val list = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    var seen by remember(lines) { mutableIntStateOf(total) }
    var unseen by remember(lines) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val extra = if (dropped > 0) 1 else 0
    LaunchedEffect(total) {
        val added = maxOf(0, total - seen)
        seen = total
        if (added > 0 && (!following || !follow)) unseen += added
        if (following && follow && shown.isNotEmpty()) list.scrollToItem(shown.size + extra - 1)
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, more) ->
            if (scrolling) {
                following = !more
                if (!more) unseen = 0
            }
        }
    }

    // "Last line 8 s ago" once the source has been quiet for the escalation (Dart `KitSince`, minute ticks).
    var quiet by remember { mutableStateOf<kotlin.time.Duration?>(null) }
    LaunchedEffect(total, live, ended) {
        quiet = null
        if (!live || ended != null) return@LaunchedEffect
        delay(FlutterKitMotion.ESCALATE_AFTER_MS.toLong())
        var waited = FlutterKitMotion.ESCALATE_AFTER_MS.milliseconds
        while (true) {
            quiet = waited
            delay(1.minutes)
            waited += 1.minutes
        }
    }

    // Polling (PERF-4): every 2 s while the panel is composed, never overlapping, until it ends or a read fails.
    var readFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(onRefresh, ended, readFailed, attempt) {
        val refresh = onRefresh ?: return@LaunchedEffect
        if (ended != null || readFailed) return@LaunchedEffect
        while (true) {
            delay(FlutterKitMotion.LOG_POLL_MS.toLong())
            if (!refresh()) {
                readFailed = true
                return@LaunchedEffect
            }
        }
    }

    val droppedWords = if (dropped > 0) pluralStringResource(Res.plurals.kitLogDropped, dropped, dropped) else null
    val copyText = { (listOfNotNull(droppedWords) + safe).joinToString("\n") }
    val detailsFill = if (colors.dark) colors.surface1 else colors.ground // Dart `KitTokens.detailsSurface`
    val look = kitSurfaceLook(
        KitSurfaceInput(
            SurfaceRole.Code,
            KitShapeId.Code,
            ownFill = detailsFill,
            color = detailsFill,
            radius = if (folded) FlutterKitTokens.DETAILS_RADIUS.dp else 0.dp,
        ),
    )
    Column(
        modifier
            .kitMaxWidth(CapRole.List, CapAlign.Start)
            .fillMaxWidth()
            .then(if (folded) Modifier else Modifier.fillMaxHeight())
            .then(look.modifier)
            .clip(look.shape)
            .testTag("kit-log-panel"),
    ) {
        LogHeader(
            title = title ?: stringResource(Res.string.kitLogTitle),
            live = live,
            ended = ended,
            readFailed = readFailed,
            quiet = quiet,
            hasLines = shown.isNotEmpty(),
            wrap = wrapOn,
            onWrap = {
                val next = !wrapOn
                if (wrap == null) ownWrap = next
                onWrapChanged?.invoke(next)
            },
            copyText = copyText,
            action = headerAction,
        )
        if (readFailed) {
            Box(Modifier.padding(start = Space.S3.dp, top = Space.S2.dp, end = Space.S3.dp).testTag("kit-log-read-failed")) {
                KitErrorNotice(
                    stringResource(Res.string.kitLogReadFailed),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), onClick = {
                        scope.launch {
                            val refresh = onRefresh ?: return@launch
                            if (refresh()) {
                                readFailed = false
                                attempt++
                            }
                        }
                    }),
                )
            }
        }
        val windowHeight = window.heightDp
        val bodyModifier =
            if (folded) Modifier.height(KitLogRules.foldedHeight(extent.value, windowHeight, Space.S2.toFloat()).dp)
            else Modifier.weight(1f)
        KitJumpPillLayer(
            bodyModifier.fillMaxWidth(),
            pill = {
                KitJumpPill(
                    label = pluralStringResource(Res.plurals.kitLogNewLines, unseen, unseen),
                    onClick = {
                        following = true
                        unseen = 0
                        scope.launch { if (shown.isNotEmpty()) list.scrollToItem(shown.size + extra - 1) }
                    },
                    visible = KitLogRules.showsJump(follow, following, unseen),
                    modifier = Modifier.testTag("kit-log-jump"),
                )
            },
        ) {
            LogBody(
                shown = shown,
                safe = safe,
                droppedWords = droppedWords,
                empty = emptyText ?: stringResource(Res.string.kitLogEmpty),
                wrap = wrapOn,
                glyph = glyph,
                extent = extent,
                list = list,
            )
        }
    }
}

/** Title, the state words (one polite live region), Wrap, Copy all and the host's action (Dart `_Header`). */
@Composable
private fun LogHeader(
    title: String,
    live: Boolean,
    ended: KitLogEnd?,
    readFailed: Boolean,
    quiet: kotlin.time.Duration?,
    hasLines: Boolean,
    wrap: Boolean,
    onWrap: () -> Unit,
    copyText: () -> String,
    action: KitAction?,
) {
    val tokens = LocalKitTokens.current
    val stateText: String? =
        when {
            readFailed -> null
            ended != null -> when {
                ended.failed && ended.exitCode == null -> stringResource(Res.string.kitLogFailed)
                ended.failed -> stringResource(Res.string.kitLogFailedExit, KitBidi.ltr("${ended.exitCode}"))
                ended.exitCode == null -> stringResource(Res.string.kitLogEnded)
                else -> stringResource(Res.string.kitLogEndedExit, KitBidi.ltr("${ended.exitCode}"))
            }
            live && quiet == null -> stringResource(Res.string.kitLogLive)
            live && quiet != null ->
                if (quiet.inWholeMinutes < 1) stringResource(Res.string.kitLogQuietSeconds, quiet.inWholeSeconds.toInt())
                else stringResource(Res.string.kitLogQuiet, kitDurationWords(quiet))
            else -> null
        }
    val words: (@Composable () -> Unit)? =
        stateText?.let { text ->
            @Composable {
                StateWords(
                    text,
                    reason = ended?.reason,
                    dot = ended == null && quiet == null,
                    failed = ended?.failed == true,
                )
            }
        }
    val line = kitHairline()
    val hairline = LocalKitColors.current.hairline
    val measurer = rememberTextMeasurer()
    val buttonStyle = LocalKitTypography.current.style(TypeRole.Button)
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().drawBehind {
            // The line under the header, inside its box (Dart `Border(bottom:)`): the hairline at the view's pixel ratio.
            val width = line.toPx()
            drawRect(hairline, topLeft = Offset(0f, size.height - width), size = Size(size.width, width))
        }.padding(start = Space.S3.dp, top = Space.S1.dp, end = Space.S2.dp, bottom = Space.S1.dp),
    ) {
        val inline =
            action == null ||
                maxWidth >= run {
                    val label = with(density) { measurer.measure(action.label, buttonStyle, maxLines = 1).size.width.toDp() }
                    val actionWidth = label + (tokens.smallIcon + Space.S2 * 3 + Space.S1).dp // F: lib/ui/kit/kit_log_panel.dart:1133
                    val controls = (tokens.minTarget * (if (hasLines) 2 else 1)).dp // F: lib/ui/kit/kit_log_panel.dart:1134
                    (160 * kitTextScale()).dp + controls + actionWidth // F: lib/ui/kit/kit_log_panel.dart:1135
                }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f).padding(vertical = Space.S2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    KitText(title, role = TypeRole.Label)
                    words?.invoke()
                }
                KitIconButton(
                    KitIcons.wrapText,
                    stringResource(Res.string.kitWrapLines),
                    onWrap,
                    Modifier.testTag("kit-log-wrap"),
                    selected = wrap,
                    size = 20, // F: lib/ui/kit/kit_log_panel.dart:1165
                )
                if (hasLines) LogCopyButton(copyText)
                if (inline && action != null) KitButton(action, role = KitButtonRole.Tertiary)
            }
            if (!inline && action != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    KitButton(action, role = KitButtonRole.Tertiary)
                }
            }
        }
    }
}

/** Copy all at the header's glyph size, with the check while the copy is held (Dart `KitIconButton.copy`). */
@Composable
private fun LogCopyButton(text: () -> String) {
    @Suppress("DEPRECATION") // Kit copy boundary; the clipboard receives only the masked lines.
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(MotionTokens.COPIED_HOLD.toLong())
            copied = false
        }
    }
    KitIconButton(
        if (copied) KitIcons.check else KitIcons.copy,
        stringResource(Res.string.kitCopyAll),
        onClick = {
            clipboard.setText(AnnotatedString(text()))
            copied = true
        },
        modifier = Modifier.testTag("kit-log-copy-all"),
        haptic = KitHaptic.Send,
        size = 20, // F: lib/ui/kit/kit_log_panel.dart:1173
    )
}

@Composable
private fun StateWords(text: String, reason: String? = null, dot: Boolean = false, failed: Boolean = false) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val glyph = tokens.smallIcon.dp * minOf(kitTextScale(), tokens.maxIconScale)
    val spoken = if (reason == null) text else "$text. $reason"
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot) {
                Box(Modifier.size(Space.S2.dp).background(colors.accent, CircleShape).testTag("kit-log-live-dot"))
                Spacer(Modifier.width(Space.S1.dp))
            }
            if (failed) {
                KitIcon(KitIcons.error, size = KitIconSize.Small, tone = TextTone.Tertiary, growsWithText = true)
                Spacer(Modifier.width(Space.S1.dp))
            }
            KitText(text, role = TypeRole.Caption, tone = TextTone.Primary)
        }
        if (reason != null) KitText(reason, role = TypeRole.Caption)
    }
}

/** The mono lines, left to right in every locale (Dart `_body`, `_LineRow`, `_DroppedRow`). */
@Composable
private fun LogBody(
    shown: List<KitLogLine>,
    safe: List<String>,
    droppedWords: String?,
    empty: String,
    wrap: Boolean,
    glyph: Dp,
    extent: Dp,
    list: LazyListState,
) {
    if (shown.isEmpty() && droppedWords == null) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)) {
            KitText(empty, role = TypeRole.Secondary, tone = TextTone.Tertiary, maxLines = 1)
        }
        return
    }
    val colors = LocalKitColors.current
    val gutter = glyph + Space.S2.dp
    val warning = stringResource(Res.string.kitLogWarningLine, "%s")
    val error = stringResource(Res.string.kitLogErrorLine, "%s")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp)) {
            val width = maxWidth
            val sideways = if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
            Box(Modifier.fillMaxWidth().then(sideways)) {
                LazyColumn(
                    Modifier.then(if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = width)).testTag("kit-log-lines"),
                    state = list,
                    contentPadding = PaddingValues(horizontal = Space.S3.dp),
                ) {
                    if (droppedWords != null) {
                        item(key = "dropped") {
                            Box(Modifier.padding(start = gutter).testTag("kit-log-dropped")) {
                                KitText(droppedWords, role = TypeRole.Mono, tone = TextTone.Tertiary, maxLines = if (wrap) Int.MAX_VALUE else 1)
                            }
                        }
                    }
                    itemsIndexed(shown) { i, line ->
                        val text = safe[i]
                        val spoken = when (line.level) {
                            KitLogLevel.Normal -> text
                            KitLogLevel.Warning -> warning.replace("%s", text)
                            KitLogLevel.Error -> error.replace("%s", text)
                        }
                        Row(Modifier.heightIn(min = extent)) {
                            Box(Modifier.width(gutter)) {
                                val icon = when (line.level) {
                                    KitLogLevel.Normal -> null
                                    KitLogLevel.Warning -> KitIcons.warning
                                    KitLogLevel.Error -> KitIcons.error
                                }
                                if (icon != null) KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Tertiary, growsWithText = true)
                            }
                            KitText(
                                text,
                                Modifier.semantics { contentDescription = spoken },
                                role = TypeRole.Mono,
                                tone = if (line.level == KitLogLevel.Normal) TextTone.Secondary else TextTone.Primary,
                                maxLines = if (wrap) Int.MAX_VALUE else 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The folded panel inside the one details fold (Dart `KitLogPanel.fold`, lib/ui/kit/kit_log_panel.dart:296-319; K2 §4.4:
 * folded under Details unless the page exists to show the log): a [KitDetailsFold] labelled [label] ("Show output")
 * whose child is a folded [KitLogPanel]. A page that already has a details fold passes a [KitLogPanel] as that fold's
 * child instead (one fold per page, KIT-33).
 *
 * States: collapsed, open (and the panel's own)
 * Text: the fold's and the panel's
 * Cap: Reading (the fold's)
 * Motion: the fold's reveal
 * Hidden: none
 * Replaces: KitLogPanel.fold (lib/ui/kit/kit_log_panel.dart)
 */
@Composable
public fun KitLogPanelFold(
    lines: KitLogBuffer,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    ended: KitLogEnd? = null,
    onRefresh: (suspend () -> Boolean)? = null,
    label: String? = null,
) {
    KitDetailsFold(
        label = label ?: stringResource(Res.string.kitLogShowOutput),
        copyLabel = stringResource(Res.string.kitCopyAll),
        modifier = modifier,
        child = { KitLogPanel(lines, live = live, ended = ended, onRefresh = onRefresh) },
    )
}
