package io.github.eslamasabry.oc.ui.kit.log

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Bounded, selectable log tail. Pause freezes the visible evidence; Follow resumes the latest tail.
 */
@Composable
public fun KitLogPanel(
    text: String,
    mask: (String) -> String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = rememberTextFieldState()
    var paused by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    var frozen by remember { mutableStateOf("") }
    var match by remember { mutableStateOf(0) }
    val latest =
        remember(text, mask) {
            mask(text).lineSequence().toList().takeLast(2000).joinToString("\n")
        }
    LaunchedEffect(latest, paused) { if (!paused) frozen = latest }
    val rows = remember(frozen) { frozen.lines() }
    val needle = query.text.toString()
    val matches =
        remember(frozen, needle) {
            if (needle.isBlank()) emptyList()
            else rows.indices.filter { rows[it].contains(needle, ignoreCase = true) }
        }
    val current = if (matches.isEmpty()) null else matches[Math.floorModCompat(match, matches.size)]
    val scroll = rememberLazyListState()
    LaunchedEffect(frozen, paused) {
        if (!paused && rows.isNotEmpty() && needle.isBlank()) scroll.scrollToItem(rows.lastIndex)
    }
    LaunchedEffect(current) { current?.let { scroll.scrollToItem(it) } }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitField(query, stringResource(Res.string.p12LogSearch), onTextChanged = { match = 0 })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitButton(
                stringResource(if (paused) Res.string.p12LogFollow else Res.string.p12LogPause),
                { paused = !paused },
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.commonRetry),
                onRefresh,
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.p12LogCopy),
                { clipboard.setText(AnnotatedString(frozen)) },
                role = KitButtonRole.Tertiary,
            )
        }
        KitSwitchRow(
            title = stringResource(Res.string.p12LogWrap),
            checked = wrap,
            onCheckedChange = { wrap = it },
        )
        if (needle.isNotBlank()) {
            KitText(
                stringResource(
                    Res.string.p12LogMatches,
                    if (matches.isEmpty()) 0 else Math.floorModCompat(match, matches.size) + 1,
                    matches.size,
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                KitButton(
                    stringResource(Res.string.p12LogPrevious),
                    { match-- },
                    role = KitButtonRole.Tertiary,
                )
                KitButton(
                    stringResource(Res.string.p12LogNext),
                    { match++ },
                    role = KitButtonRole.Tertiary,
                )
            }
        }
        if (frozen.isBlank()) KitText(stringResource(Res.string.servicesLogEmpty))
        val colors = LocalKitColors.current
        val style = LocalKitTypography.current.style(TypeRole.Mono).copy(color = colors.text1)
        val selectedColor = colors.accent
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().copyOnly()) {
            LazyColumn(
                state = scroll,
                modifier =
                    Modifier.fillMaxSize()
                        .then(
                            if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
                        ),
            ) {
                itemsIndexed(rows) { index, line ->
                    val marked =
                        remember(line, needle, selectedColor) {
                            buildAnnotatedString {
                                var from = 0
                                if (needle.isNotBlank())
                                    while (from < line.length) {
                                        val at = line.indexOf(needle, from, ignoreCase = true)
                                        if (at < 0) break
                                        append(line.substring(from, at))
                                        withStyle(
                                            SpanStyle(background = selectedColor.copy(alpha = .25f))
                                        ) {
                                            append(line.substring(at, at + needle.length))
                                        }
                                        from = at + needle.length
                                    }
                                append(line.substring(from))
                            }
                        }
                    Box(
                        Modifier.fillMaxWidth()
                            .then(
                                if (index == current)
                                    Modifier.background(selectedColor.copy(alpha = .12f))
                                else Modifier
                            )
                            .padding(vertical = 1.dp)
                    ) {
                        BasicText(marked, style = style, softWrap = wrap)
                    }
                }
            }
        }
    }
}

/**
 * Selectable reading text for an exact report preview. Selection offers the system's Copy and
 * Select all.
 */
@Composable
public fun KitSelectableText(
    text: String,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
) {
    SelectionContainer(modifier.copyOnly()) { KitText(text, role = role) }
}

private object Math {
    fun floorModCompat(value: Int, divisor: Int): Int = ((value % divisor) + divisor) % divisor
}

private fun Modifier.copyOnly(): Modifier = filterTextContextMenuComponents {
    it.key == TextContextMenuKeys.CopyKey || it.key == TextContextMenuKeys.SelectAllKey
}
