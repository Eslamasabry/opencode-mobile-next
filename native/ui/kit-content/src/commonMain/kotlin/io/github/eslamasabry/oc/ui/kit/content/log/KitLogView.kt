package io.github.eslamasabry.oc.ui.kit.content.log

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPillLayer
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyAll
import io.github.eslamasabry.oc.ui.words.res.kitLogDropped
import io.github.eslamasabry.oc.ui.words.res.kitLogEmpty
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
import io.github.eslamasabry.oc.ui.words.res.kitLogShowOutput
import io.github.eslamasabry.oc.ui.words.res.kitLogTitle
import io.github.eslamasabry.oc.ui.words.res.kitLogWarningLine
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How the source ended (Dart `KitLogEnd`): "Ended · exit 0", or "Failed · exit 1" with a reason in words. */
@Immutable
public data class KitLogEnd(val exitCode: Int? = null, val failed: Boolean = false, val reason: String? = null)

/** How tall the panel is (Dart `KitLogSize`). */
public enum class KitLogSize {
    /** About [FOLDED_LINES] lines tall: the panel inside a fold or a card. */
    Folded,

    /** Fills its host, which gives it a bounded height. */
    Fill,
}

/** Lines a folded panel shows (Dart `KitTokens.logFoldedLines`). */
private const val FOLDED_LINES = 12 // F: lib/ui/kit/kit_tokens.dart:166

/** The most of the window a folded panel takes. */
private const val FOLDED_WINDOW_SHARE = .45f // F: lib/ui/kit/kit_log_panel.dart:757

/** The panel's corner when folded (Dart `KitTokens.detailsRadius`). */
private const val DETAILS_RADIUS = 14 // F: lib/ui/kit/kit_tokens.dart:820

/** The header glyph buttons' size (Dart `size: 20`). */
private const val HEADER_ICON = 20 // F: lib/ui/kit/kit_log_panel.dart:1149

/** Search marks: active and passive accent washes. */
private const val MARK_ACTIVE = .5f // F: lib/ui/kit/kit_log_panel.dart:1278
private const val MARK_PASSIVE = .24f // F: lib/ui/kit/kit_log_panel.dart:1278

/** Lines shown at most (Dart `KitLogPanel.defaultMaxLines`). */
public const val KIT_LOG_MAX_LINES: Int = 2000 // F: lib/ui/kit/kit_log_panel.dart:292

/** What the header says beside the title (Dart `_Header`'s words), as data. */
internal sealed interface LogState {
    data object None : LogState
    data object Live : LogState
    data class Quiet(val seconds: Long) : LogState
    data class End(val end: KitLogEnd) : LogState
}

/** The header's state words: a failed read hides them, an end wins over live, live goes quiet at 8 s. */
internal fun logState(live: Boolean, ended: KitLogEnd?, readFailed: Boolean, quietSeconds: Long?): LogState = when {
    readFailed -> LogState.None
    ended != null -> LogState.End(ended)
    live && quietSeconds != null -> LogState.Quiet(quietSeconds)
    live -> LogState.Live
    else -> LogState.None
}

/** The shown tail and how many earlier lines it leaves out (Dart `_dropped`, `_shownLines`). */
internal fun logShown(all: List<KitLogLine>, bufferDropped: Int, maxLines: Int): Pair<List<KitLogLine>, Int> {
    val over = maxOf(0, all.size - maxLines)
    return (if (over > 0) all.subList(over, all.size) else all) to bufferDropped + over
}

/**
 * A process's output, Flutter's KitLogPanel (kit_log_panel.dart): a header (Output, Live with its dot / "Last line
 * 8 s ago" / "Failed · exit 1" and the reason, Wrap lines and Copy all as glyph buttons, the host's one action) over
 * mono lines on the details fill, warnings and errors marked with a glyph and said in words, "1,240 earlier lines not
 * shown" when the buffer dropped some, "No output yet" when empty. A failed read shows the error with Try again; when
 * the person has scrolled up, new lines collect in a "12 new lines" pill instead of moving the view. Lines are
 * masked with [KitRedact] before they are drawn or copied. [onRefresh] is polled every 2 s while nothing ended.
 *
 * States: empty, live, quiet, ended, failed, read failed, scrolled up (new lines), dropped
 * Text: frame (header), reading (lines, mono)
 * Cap: List (960 dp)
 * Motion: the jump to the newest line animates on KitMotion; Off = at once
 * Hidden: none (every action is a visible button)
 * Replaces: KitLogPanel (lib/ui/kit/kit_log_panel.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitLogPanel(
    lines: KitLogBuffer,
    modifier: Modifier = Modifier,
    title: String? = null,
    live: Boolean = false,
    ended: KitLogEnd? = null,
    onRefresh: (suspend () -> Unit)? = null,
    follow: Boolean = true,
    maxLines: Int = KIT_LOG_MAX_LINES,
    emptyText: String? = null,
    size: KitLogSize = KitLogSize.Folded,
    wrap: Boolean? = null,
    onWrapChanged: ((Boolean) -> Unit)? = null,
    headerAction: KitAction? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val (shown, dropped) = logShown(lines.lines, lines.dropped, maxLines)
    val total = shown.size + dropped
    val extra = if (dropped > 0) 1 else 0
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = KitMotion.state.reduced
    var following by remember(lines) { mutableStateOf(true) }
    var unseen by remember(lines) { mutableIntStateOf(0) }
    var seen by remember(lines) { mutableIntStateOf(total) }
    var readFailed by remember(lines) { mutableStateOf(false) }
    var attempt by remember(lines) { mutableIntStateOf(0) }
    var quiet by remember(lines) { mutableStateOf<Long?>(null) }
    var ownWrap by remember { mutableStateOf<Boolean?>(null) }

    // New lines: follow the newest, or count them for the pill while the person reads above.
    LaunchedEffect(total) {
        val added = maxOf(0, total - seen)
        seen = total
        if (added > 0 && !(following && follow)) unseen += added
        if (following && follow && shown.isNotEmpty()) list.scrollToItem(shown.size + extra - 1)
    }
    // Moving the view decides following (Dart `_onScroll`): away from the end stops it, back at the end resumes it.
    // New output alone does not move the first visible line, so it never counts as the person scrolling.
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect {
            val atEnd = !list.canScrollForward
            following = atEnd
            if (atEnd) unseen = 0
        }
    }
    // "Last line 8 s ago": slow at the escalation time, then counted by the minute (Dart KitSince, ticks minutes).
    LaunchedEffect(total, live, ended) {
        quiet = null
        if (!live || ended != null) return@LaunchedEffect
        delay(MotionTokens.ESCALATE_AFTER.milliseconds)
        var seconds = MotionTokens.ESCALATE_AFTER / 1000L // NV: milliseconds to seconds
        while (true) {
            quiet = seconds
            delay(60_000) // NV: one minute between age updates (KitSinceTicks.minutes)
            seconds += 60 // NV: one minute
        }
    }
    // The poll: every 2 s while nothing ended; a failed read stops it until Try again.
    LaunchedEffect(onRefresh, ended, attempt) {
        val refresh = onRefresh ?: return@LaunchedEffect
        if (ended != null) return@LaunchedEffect
        if (attempt > 0) {
            if (!read(refresh)) return@LaunchedEffect else readFailed = false
        }
        while (true) {
            delay(FlutterKitMotion.LOG_POLL_MS.milliseconds)
            if (!read(refresh)) {
                readFailed = true
                return@LaunchedEffect
            }
            readFailed = false
        }
    }

    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val compact = WindowClass.of(window.width / density.density) == WindowClass.Compact
    val wrapOn = wrap ?: ownWrap ?: compact // F: lib/ui/kit/kit_log_panel.dart:691-694
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    val glyph = logGlyph(tokens.smallIcon, density.fontScale, tokens.maxIconScale) // F: lib/ui/kit/kit_log_panel.dart:678-681
    val extent = maxOf(kotlin.math.ceil(mono.lineHeight.value * density.fontScale), glyph) // F: lib/ui/kit/kit_log_panel.dart:684-689
    val gutter = glyph + Space.S2 // F: lib/ui/kit/kit_log_panel.dart:842
    val folded = size == KitLogSize.Folded
    val shape = if (folded) RoundedCornerShape(DETAILS_RADIUS.dp) else RoundedCornerShape(0.dp)
    val fill = if (colors.dark) colors.surface1 else colors.ground // Dart tokens.detailsSurface (FlutterKitTokenRoles)
    val droppedWords = if (dropped > 0) pluralStringResource(Res.plurals.kitLogDropped, dropped, dropped) else null
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val copyText = { (listOfNotNull(droppedWords) + shown.map { KitRedact.text(it.text) }).joinToString("\n") }

    BoxWithConstraints(modifier.kitMaxWidth(CapRole.List).fillMaxWidth()) { // F: lib/ui/kit/kit_log_panel.dart:716-717
        val windowHeight = window.height / density.density
        Column(
            Modifier.fillMaxWidth().then(if (folded) Modifier else Modifier.fillMaxSize())
                .clip(shape).background(fill, shape).testTag("kit-log-panel"),
        ) {
            LogHeader(
                title = title ?: stringResource(Res.string.kitLogTitle),
                state = logState(live, ended, readFailed, quiet),
                hasLines = shown.isNotEmpty(),
                wrap = wrapOn,
                onWrap = {
                    val next = !wrapOn
                    if (wrap == null) ownWrap = next
                    onWrapChanged?.invoke(next)
                },
                onCopy = { clipboard.setText(AnnotatedString(copyText())) },
                action = headerAction,
                glyph = glyph,
            )
            if (readFailed) {
                KitErrorNotice(
                    stringResource(Res.string.kitLogReadFailed),
                    Modifier.padding(start = Space.S3.dp, top = Space.S2.dp, end = Space.S3.dp) // F: lib/ui/kit/kit_log_panel.dart:793-797
                        .testTag("kit-log-read-failed"),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), { attempt++ }),
                )
            }
            val height = if (folded) minOf(FOLDED_LINES * extent + Space.S2 * 2f, windowHeight * FOLDED_WINDOW_SHARE) else null // F: lib/ui/kit/kit_log_panel.dart:755-759
            val body = if (height != null) Modifier.height(kotlin.math.floor(height).dp) else Modifier.weight(1f)
            KitJumpPillLayer(
                body.fillMaxWidth(),
                pill = {
                    KitJumpPill(
                        pluralStringResource(Res.plurals.kitLogNewLines, unseen, unseen),
                        onClick = {
                            following = true
                            unseen = 0
                            scope.launch {
                                val last = shown.size + extra - 1
                                if (last >= 0) if (reduced) list.scrollToItem(last) else list.animateScrollToItem(last)
                            }
                        },
                        visible = follow && !following && unseen > 0, // F: lib/ui/kit/kit_log_panel.dart:745
                        modifier = Modifier.testTag("kit-log-jump"),
                    )
                },
            ) {
                LogBody(shown, droppedWords, wrapOn, extent, gutter, glyph, list, emptyText)
            }
        }
    }
}

/** The line glyph's size: the small icon scaled with the text, at most [maxScale] (Dart `KitTokens.iconSize`). */
internal fun logGlyph(smallIcon: Int, fontScale: Float, maxScale: Float): Float =
    smallIcon * minOf(fontScale, maxScale) // F: lib/ui/kit/kit_tokens.dart:755-757

private suspend fun read(refresh: suspend () -> Unit): Boolean =
    try {
        refresh()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) { // a failed read is the notice with Try again, never a crash
        false
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogHeader(
    title: String,
    state: LogState,
    hasLines: Boolean,
    wrap: Boolean,
    onWrap: () -> Unit,
    onCopy: () -> Unit,
    action: KitAction?,
    glyph: Float,
) {
    val colors = LocalKitColors.current
    val hairline = kitHairline()
    BoxWithConstraints(
        Modifier.fillMaxWidth().drawBehind {
            val w = hairline.toPx()
            drawLine(colors.hairline, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
        }.padding(start = Space.S3.dp, top = Space.S1.dp, end = Space.S2.dp, bottom = Space.S1.dp), // F: lib/ui/kit/kit_log_panel.dart:1051-1056
    ) {
        // The host's action shares the row when there is room for the words, the two glyphs and the action.
        val inline = action != null && maxWidth.value >= 160 * LocalDensity.current.fontScale + 48 * (if (hasLines) 2 else 1) + 160 // F: lib/ui/kit/kit_log_panel.dart:1123-1135
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f).padding(vertical = Space.S2.dp), // F: lib/ui/kit/kit_log_panel.dart:1146
                    horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/kit_log_panel.dart:1150
                    verticalArrangement = Arrangement.spacedBy(Space.S1.dp), // F: lib/ui/kit/kit_log_panel.dart:1151
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    KitText(title, role = TypeRole.Label)
                    LogStateWords(state, glyph)
                }
                KitIconButton(
                    KitIcons.wrapText, stringResource(Res.string.kitWrapLines), onWrap,
                    Modifier.testTag("kit-log-wrap"), selected = wrap, size = HEADER_ICON,
                )
                if (hasLines) {
                    KitIconButton(KitIcons.copy, stringResource(Res.string.kitCopyAll), onCopy, Modifier.testTag("kit-log-copy-all"), size = HEADER_ICON)
                }
                if (inline) KitButton(action!!, role = KitButtonRole.Tertiary, expand = false)
            }
            if (action != null && !inline) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    KitButton(action, role = KitButtonRole.Tertiary, expand = false) // F: lib/ui/kit/kit_log_panel.dart:1109-1113
                }
            }
        }
    }
}

@Composable
private fun LogStateWords(state: LogState, glyph: Float) {
    val colors = LocalKitColors.current
    val (text, reason) = when (state) {
        LogState.None -> return
        LogState.Live -> stringResource(Res.string.kitLogLive) to null
        is LogState.Quiet ->
            (if (state.seconds < 60) stringResource(Res.string.kitLogQuietSeconds, state.seconds.toInt()) // NV: under a minute
            else stringResource(Res.string.kitLogQuiet, kitDurationWords(state.seconds.seconds))) to null
        is LogState.End -> {
            val code = state.end.exitCode?.let { "⁦$it⁩" }
            (if (state.end.failed) code?.let { stringResource(Res.string.kitLogFailedExit, it) } ?: stringResource(Res.string.kitLogFailed)
            else code?.let { stringResource(Res.string.kitLogEndedExit, it) } ?: stringResource(Res.string.kitLogEnded)) to state.end.reason
        }
    }
    val failed = state is LogState.End && state.end.failed
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = if (reason == null) text else "$text. $reason"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == LogState.Live) {
                Box(Modifier.size(Space.S2.dp).clip(CircleShape).background(colors.accent).testTag("kit-log-live-dot")) // F: lib/ui/kit/kit_log_panel.dart:1209-1216
                Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/kit_log_panel.dart:1218
            }
            if (failed) {
                Image(KitIcons.error, null, Modifier.size(glyph.dp), colorFilter = ColorFilter.tint(colors.text3)) // F: lib/ui/kit/kit_log_panel.dart:1221-1225
                Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/kit_log_panel.dart:1226
            }
            KitText(text, role = TypeRole.Caption, tone = TextTone.Primary)
        }
        if (reason != null) KitText(reason, role = TypeRole.Caption)
    }
}

@Composable
private fun LogBody(
    shown: List<KitLogLine>,
    droppedWords: String?,
    wrap: Boolean,
    extent: Float,
    gutter: Float,
    glyph: Float,
    list: LazyListState,
    emptyText: String?,
) {
    val colors = LocalKitColors.current
    val mono = LocalKitTypography.current.style(TypeRole.Mono)
    if (shown.isEmpty() && droppedWords == null) {
        KitText(
            emptyText ?: stringResource(Res.string.kitLogEmpty),
            Modifier.padding(horizontal = Space.S3.dp, vertical = Space.S2.dp), // F: lib/ui/kit/kit_log_panel.dart:832-835
            role = TypeRole.Secondary,
            tone = TextTone.Tertiary,
            maxLines = 1,
        )
        return
    }
    val warnWords = stringResource(Res.string.kitLogWarningLine, "\u0000")
    val errorWords = stringResource(Res.string.kitLogErrorLine, "\u0000")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionContainer(Modifier.fillMaxSize().padding(vertical = Space.S2.dp)) { // F: lib/ui/kit/kit_log_panel.dart:938-941
            LazyColumn(
                Modifier.fillMaxSize().testTag("kit-log-lines").then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                state = list,
                contentPadding = PaddingValues(horizontal = Space.S3.dp), // F: lib/ui/kit/kit_log_panel.dart:843
            ) {
                if (droppedWords != null) {
                    item(key = "dropped") {
                        BasicText(
                            droppedWords,
                            Modifier.padding(start = gutter.dp).testTag("kit-log-dropped"), // F: lib/ui/kit/kit_log_panel.dart:1373-1376
                            style = mono.copy(color = colors.text3),
                            maxLines = if (wrap) Int.MAX_VALUE else 1,
                            softWrap = wrap,
                            overflow = if (wrap) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    }
                }
                items(shown.size, key = { it }) { i ->
                    val line = shown[i]
                    val text = KitRedact.text(line.text)
                    val icon = when (line.level) {
                        KitLogLevel.Normal -> null
                        KitLogLevel.Warning -> KitIcons.warning
                        KitLogLevel.Error -> KitIcons.error
                    }
                    val label = when (line.level) {
                        KitLogLevel.Normal -> text
                        KitLogLevel.Warning -> warnWords.replace("\u0000", text)
                        KitLogLevel.Error -> errorWords.replace("\u0000", text)
                    }
                    Row(Modifier.heightIn(min = extent.dp).testTag("kit-log-line-$i")) { // F: lib/ui/kit/kit_log_panel.dart:1316-1318
                        Box(Modifier.width(gutter.dp)) {
                            if (icon != null) {
                                Image(icon, null, Modifier.size(glyph.dp).clearAndSetSemantics {}, colorFilter = ColorFilter.tint(colors.text3)) // F: lib/ui/kit/kit_log_panel.dart:1322-1329
                            }
                        }
                        BasicText(
                            text.ifEmpty { " " },
                            Modifier.clearAndSetSemantics { contentDescription = label },
                            style = mono.copy(color = if (line.level == KitLogLevel.Normal) colors.text2 else colors.text1), // F: lib/ui/kit/kit_log_panel.dart:867-872
                            softWrap = wrap,
                            maxLines = if (wrap) Int.MAX_VALUE else 1,
                            overflow = if (wrap) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The panel folded under "Show output" (Dart `KitLogPanel.fold`): a [KitDetailsFold] holding a folded panel.
 */
@Composable
public fun KitLogPanelFold(
    lines: KitLogBuffer,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    ended: KitLogEnd? = null,
    onRefresh: (suspend () -> Unit)? = null,
    label: String? = null,
) {
    KitDetailsFold(
        label = label ?: stringResource(Res.string.kitLogShowOutput),
        copyLabel = stringResource(Res.string.kitCopyAll),
        modifier = modifier.testTag("kit-log-fold"),
        child = { KitLogPanel(lines, live = live, ended = ended, onRefresh = onRefresh) },
    )
}
