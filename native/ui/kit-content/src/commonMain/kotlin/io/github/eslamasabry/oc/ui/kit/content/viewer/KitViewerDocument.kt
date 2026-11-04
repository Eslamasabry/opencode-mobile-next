package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Reader controls shared by text, code, Markdown, SVG and tables. The source stays exact for Copy;
 * Find is literal, case insensitive and moves the lazy listing to the active match. A formatted
 * body is supplied by the host's kit. All controls are visible, and a source switch keeps Find
 * available even for formatted documents.
 */
@Composable
public fun KitViewerDocument(
    source: String,
    modifier: Modifier = Modifier,
    code: Boolean = false,
    initialLine: Int? = null,
    formatted: (@Composable () -> Unit)? = null,
    lineContent: (@Composable (AnnotatedString, Modifier, Boolean) -> Unit)? = null,
) {
    val cut = remember(source) { ViewerText.cut(source) }
    val lines = remember(cut) { ViewerText.lines(cut.text) }
    var wrap by remember(source) { mutableStateOf(!code) }
    var showSource by remember(source) { mutableStateOf(false) }
    var finding by remember(source) { mutableStateOf(false) }
    var query by remember(source) { mutableStateOf("") }
    var active by remember(source, query) { mutableIntStateOf(0) }
    val matches =
        remember(lines, query) {
            if (query.isEmpty()) emptyList()
            else
                buildList {
                    lines.forEachIndexed { line, text ->
                        var from = 0
                        while (from <= text.length - query.length) {
                            val at = text.indexOf(query, from, ignoreCase = true)
                            if (at < 0) break
                            add(Triple(line, at, at + query.length))
                            from = at + query.length
                        }
                    }
                }
        }
    val list = rememberLazyListState()
    LaunchedEffect(source, initialLine) {
        val at = initialLine?.minus(1)?.takeIf { it in lines.indices }
        if (at != null) list.scrollToItem(maxOf(0, at - 3))
    }
    LaunchedEffect(matches, active, finding) {
        if (finding && matches.isNotEmpty())
            list.scrollToItem(maxOf(0, matches[active.coerceIn(matches.indices)].first - 3))
    }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val colors = LocalKitColors.current
    val style =
        LocalKitTypography.current
            .style(if (code || showSource) TypeRole.Mono else TypeRole.Body)
            .copy(color = colors.text1)
    val rendering = formatted != null && !showSource && !finding
    Column(modifier.fillMaxSize()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.S3.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            KitButton(
                stringResource(Res.string.kitViewerCopyContents),
                { clipboard.setText(AnnotatedString(source)) },
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.kitViewerFind),
                { finding = !finding },
                role = KitButtonRole.Tertiary,
            )
            if (formatted != null)
                KitButton(
                    stringResource(
                        if (showSource) Res.string.kitDiffShowFormatted
                        else Res.string.kitViewerShowSource
                    ),
                    { showSource = !showSource },
                    role = KitButtonRole.Tertiary,
                )
            if (!rendering)
                KitButton(
                    stringResource(Res.string.kitWrapLines),
                    { wrap = !wrap },
                    modifier = Modifier.semantics { selected = wrap },
                    role = KitButtonRole.Tertiary,
                )
        }
        if (finding) {
            KitSearchField(
                stringResource(Res.string.kitViewerFind),
                { query = it },
                Modifier.padding(horizontal = Space.S3.dp),
                onSubmit = {
                    if (matches.isNotEmpty()) active = (active + 1) % matches.size
                },
            )
            FlowRow(
                Modifier.padding(horizontal = Space.S3.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                KitButton(
                    stringResource(Res.string.kitViewerFindPrevious),
                    {
                        if (matches.isNotEmpty())
                            active = (active + matches.size - 1) % matches.size
                    },
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.kitViewerFindNext),
                    { if (matches.isNotEmpty()) active = (active + 1) % matches.size },
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.kitViewerFindClose),
                    {
                        finding = false
                        query = ""
                    },
                    role = KitButtonRole.Tertiary,
                )
            }
            if (query.isNotEmpty())
                KitText(
                    if (matches.isEmpty()) stringResource(Res.string.kitViewerFindNone)
                    else
                        stringResource(
                            Res.string.kitViewerFindCount,
                            (active + 1).toString(),
                            matches.size.toString(),
                        ),
                    Modifier.padding(horizontal = Space.S3.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
        }
        if (cut.truncated)
            KitText(
                stringResource(Res.string.kitViewerPartial),
                Modifier.padding(horizontal = Space.S3.dp),
            )
        if (rendering) {
            Box(Modifier.weight(1f)) { formatted?.invoke() }
        } else if (lines.isEmpty()) {
            KitText(stringResource(Res.string.kitViewerEmpty), Modifier.padding(Space.S3.dp))
        } else
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize()
                        .then(
                            if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
                        ),
                    state = list,
                    contentPadding = PaddingValues(Space.S3.dp),
                ) {
                    itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                        val annotated = buildAnnotatedString {
                            append(line.ifEmpty { " " })
                            matches.forEachIndexed { n, match ->
                                if (match.first == i)
                                    addStyle(
                                        SpanStyle(
                                            background =
                                                colors.accent.copy(
                                                    alpha = if (n == active) .4f else .18f
                                                )
                                        ),
                                        match.second,
                                        match.third,
                                    )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (initialLine == i + 1 && !finding)
                                        Modifier.background(colors.accent.copy(alpha = .18f))
                                    else Modifier
                                )
                        ) {
                            if (code || showSource)
                                androidx.compose.foundation.text.selection.DisableSelection {
                                    KitText(
                                        (i + 1).toString().padStart(lines.size.toString().length),
                                        Modifier.padding(end = Space.S3.dp),
                                        role = TypeRole.Mono,
                                    )
                                }
                            val textModifier = if (wrap) Modifier.weight(1f) else Modifier
                            if (lineContent != null) lineContent(annotated, textModifier, wrap)
                            else BasicText(annotated, textModifier, style = style, softWrap = wrap)
                        }
                    }
                }
            }
    }
}
