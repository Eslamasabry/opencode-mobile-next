package io.github.eslamasabry.oc.ui.kit.content.log

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
import io.github.eslamasabry.oc.ui.words.res.kitLogEmpty
import io.github.eslamasabry.oc.ui.words.res.kitLogFollow
import io.github.eslamasabry.oc.ui.words.res.kitLogNextMatch
import io.github.eslamasabry.oc.ui.words.res.kitLogPause
import io.github.eslamasabry.oc.ui.words.res.kitLogPreviousMatch
import io.github.eslamasabry.oc.ui.words.res.kitLogSearch
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import org.jetbrains.compose.resources.stringResource

/**
 * Searchable process output and report text (Flutter `kit_log_panel_tools.dart`). The host supplies
 * already masked [redactedText] (the diagnostics engine issues redacted report previews); display,
 * search, selection and copy all use that same text. The list is bounded and lazy. Search starts at
 * the newest matching line, Previous/Next wrap, and Pause holds the reading position while new
 * output arrives. Follow resumes at the newest line; scrolling up also stops following. The host
 * supplies updates, never an internal poll.
 */
@Composable
public fun KitLogPanel(
    redactedText: String,
    modifier: Modifier = Modifier,
) {
    val safe = redactedText
    val lines = remember(safe) { safe.lines().takeLast(KitLogBuffer.DEFAULT_CAPACITY) }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
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
        if (!paused && following && safe.isNotEmpty()) list.scrollToItem(lines.lastIndex)
    }
    LaunchedEffect(current) {
        if (current in matches.indices) list.scrollToItem(matches[current])
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }
            .collect { (scrolling, more) ->
                if (scrolling) following = !more
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
            KitButton(
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
                            )
                        }
                    }
                }
            }
        }
    }
}
