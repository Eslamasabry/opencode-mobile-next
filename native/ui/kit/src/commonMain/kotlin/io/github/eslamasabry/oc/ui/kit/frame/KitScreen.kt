package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLineFold
import io.github.eslamasabry.oc.ui.kit.status.KitStatusSlot
import io.github.eslamasabry.oc.ui.kit.status.kitStatusSlotAbove
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/** Optional host inset source; null uses the platform's safe drawing area and keyboard. */
internal val LocalKitScreenInsets = compositionLocalOf<WindowInsets?> { null }

private val LocalChromePadding = compositionLocalOf { PaddingValues() }

/** Space kept inside a scrolling body for the overlaid chat header and composer. */
@Composable public fun kitScreenChromePadding(): PaddingValues = LocalChromePadding.current

/** How wide a single-pane body may grow on medium and wider windows (Dart `KitScreenWidth`). */
public enum class KitScreenWidth(internal val cap: CapRole) {
    /** The whole width (the default). */
    Full(CapRole.Fill),

    /** Forms, settings, prose: centred at 720. */
    Reading(CapRole.Reading),

    /** A list on its own: centred at 960. */
    List(CapRole.List),
}

/**
 * The one screen frame (Dart `KitScreen` v2). With [topBar] (or [page]) it is a page: the ground,
 * the bar in the top safe area, and the bottom block lifted above the keyboard; without, it is a
 * body inside a host and builds no frame. Top to bottom: bar → the one status line → [search] →
 * [header] → the one loading line → [body] → [bottom]. There is no other slot (R9: no new bar
 * levels). A screen with no status slot above it owns the window's slot; one under a slot (a tab
 * body, a pane) contributes its [status] instead of drawing a second line. It hosts the screen's
 * sheets.
 *
 * States: default, loading, status, search, keyboard Text: none (its slots bring their own) Cap:
 * Fill (the body is capped by [width] from medium up) Motion: none of its own (the status line
 * folds in through the slot) Hidden: none Replaces: KitScreen (lib/ui/kit/kit_screen.dart)
 */
@Composable
public fun KitScreen(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    status: KitStatus? = null,
    search: (@Composable () -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    loading: Boolean = false,
    loadingLabel: String? = null,
    bottom: (@Composable () -> Unit)? = null,
    width: KitScreenWidth = KitScreenWidth.Full,
    page: Boolean = false,
    overlayChrome: Boolean = false,
    body: @Composable () -> Unit,
) {
    val framed = topBar != null || page
    val window = LocalKitWindow.current
    val cap = if (window.cls == WindowClass.Compact) CapRole.Fill else width.cap
    // Overlaid chrome swaps between the overlay Box and the plain Column (a status line, Find,
    // loading): the bar, body and bottom move between them instead of being rebuilt, so the
    // composer keeps its field, focus and keyboard and the body keeps its scroll position.
    val latestTop = rememberUpdatedState(topBar)
    val latestBottom = rememberUpdatedState(bottom)
    val latestBody = rememberUpdatedState(body)
    val movableTop = remember { movableContentOf { latestTop.value?.invoke() } }
    val movableBottom = remember { movableContentOf { latestBottom.value?.invoke() } }
    val movableBody = remember { movableContentOf { latestBody.value() } }
    val topPart: (@Composable () -> Unit)? = topBar?.let { if (overlayChrome) movableTop else it }
    val bottomPart: (@Composable () -> Unit)? =
        bottom?.let { if (overlayChrome) movableBottom else it }
    val bodyPart: @Composable () -> Unit = if (overlayChrome) movableBody else body
    val inner: @Composable (line: KitStatus?, ownsLine: Boolean) -> Unit = { line, ownsLine ->
        val insets =
            LocalKitScreenInsets.current ?: WindowInsets.safeDrawing.union(WindowInsets.ime)
        if (overlayChrome && line == null && search == null && header == null && !loading) {
            var topPx by remember { mutableIntStateOf(0) }
            var bottomPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val padding =
                with(density) { PaddingValues(top = topPx.toDp(), bottom = bottomPx.toDp()) }
            Box(
                Modifier.fillMaxSize()
                    .then(if (framed) Modifier.windowInsetsPadding(insets) else Modifier)
                    .testTag("kit-screen")
            ) {
                CompositionLocalProvider(LocalChromePadding provides padding) {
                    Box(Modifier.fillMaxSize().kitMaxWidth(cap)) { bodyPart() }
                }
                topPart?.let {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged {
                            topPx = it.height
                        }
                    ) {
                        it()
                    }
                }
                bottomPart?.let {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomPx = it.height }
                            .padding(
                                horizontal = LocalKitTokens.current.gutter.dp,
                                vertical = Space.S2.dp,
                            )
                            .testTag("kit-screen-bottom")
                    ) {
                        it()
                    }
                }
            }
        } else
            Column(
                Modifier.fillMaxSize()
                    .then(if (framed) Modifier.windowInsetsPadding(insets) else Modifier)
                    .testTag("kit-screen")
            ) {
                if (topPart != null) {
                    Box(
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                    ) {
                        topPart()
                    }
                }
                if (ownsLine) KitStatusLineFold(line)
                Column(Modifier.fillMaxWidth().weight(1f).kitMaxWidth(cap).fillMaxWidth()) {
                    search?.let {
                        Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                            it()
                        }
                    }
                    header?.invoke(this)
                    if (loading) ScreenLoadingLine(loadingLabel)
                    Box(Modifier.fillMaxWidth().weight(1f)) { bodyPart() }
                    bottomPart?.let {
                        Box(
                            Modifier.fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.ime
                                        .union(WindowInsets.navigationBars)
                                        .only(WindowInsetsSides.Bottom)
                                )
                                .padding(
                                    horizontal = LocalKitTokens.current.gutter.dp,
                                    vertical = Space.S2.dp,
                                )
                                .testTag("kit-screen-bottom")
                        ) {
                            it()
                        }
                    }
                }
            }
    }
    val content: @Composable () -> Unit = {
        if (kitStatusSlotAbove()) {
            KitStatusContribution(status)
            inner(null, false)
        } else {
            KitStatusSlot(status) { shown -> inner(shown, true) }
        }
    }
    if (framed) {
        ScreenSheetHost(modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
            content()
        }
    } else {
        Box(modifier) { content() }
    }
}

/** Pane and nested page frames use the encompassing window's modal layer. */
@Composable
private fun ScreenSheetHost(modifier: Modifier, content: @Composable () -> Unit) {
    if (LocalKitSheetHost.current == null) {
        KitSheetHost(modifier, content)
    } else {
        Box(modifier) { content() }
    }
}

/**
 * The screen's one loading line, 2 dp under the header (STATE-4): a still accent band with progress
 * semantics. A placeholder for `KitLoadingBar` (S1-KITA2), which replaces it with the moving bar.
 */
@Composable
private fun ScreenLoadingLine(label: String?) {
    val colors = LocalKitColors.current
    val accent = colors.accent
    Box(
        Modifier.fillMaxWidth()
            .height(LocalKitTokens.current.loadingBar.dp)
            .background(colors.surface3)
            .drawBehind {
                drawLine(
                    accent,
                    Offset(0f, size.height / 2),
                    Offset(size.width / 3, size.height / 2),
                    size.height,
                )
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                label?.let { contentDescription = it }
            }
            .testTag("kit-screen-loading")
    )
}

/**
 * The adaptive panes of a screen (Dart `KitScreen.twoPane` / `.threePane`): from expanded (not
 * short) [list] at 296 dp at the start, a hairline, and [detail] filling the rest with its content
 * capped at 700 and centred; [emptyDetail] when nothing is selected. On large, [side] at 340 dp at
 * the end. Panes follow the body, not the window: the detail shows only while it keeps its 360 dp
 * minimum ([Layout.showsDetail], [Layout.showsSide]). Below expanded only the list shows; opening a
 * row pushes the detail as a page (the screen's navigator). Each pane is its own traversal group:
 * list → detail → side.
 *
 * States: list, detail empty, detail selected, side Text: none Cap: Fill Motion: none (panes swap
 * at once, MOT-5) Hidden: none Replaces: KitScreen.twoPane, KitScreen.threePane
 * (lib/ui/kit/kit_screen.dart)
 */
@Composable
public fun KitPaneScreen(
    list: @Composable () -> Unit,
    detail: (@Composable () -> Unit)?,
    emptyDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    side: (@Composable () -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    status: KitStatus? = null,
    search: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
    loadingLabel: String? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = LocalKitWindow.current
        val body = maxWidth.value
        val showsDetail = Layout.showsDetail(window.cls, window.short, body)
        val panes: @Composable (KitStatus?, Boolean) -> Unit = { line, ownsLine ->
            Panes(
                list,
                detail,
                emptyDetail,
                side,
                topBar,
                status,
                search,
                loading,
                loadingLabel,
                bottom,
                showsDetail = showsDetail,
                showsSide = side != null && Layout.showsSide(window.cls, window.short, body),
                line = line,
                ownsLine = ownsLine,
            )
        }
        if (showsDetail && !kitStatusSlotAbove()) {
            KitStatusSlot(status) { panes(it, true) }
        } else {
            panes(null, false)
        }
    }
}

@Composable
private fun Panes(
    list: @Composable () -> Unit,
    detail: (@Composable () -> Unit)?,
    emptyDetail: @Composable () -> Unit,
    side: (@Composable () -> Unit)?,
    topBar: (@Composable () -> Unit)?,
    status: KitStatus?,
    search: (@Composable () -> Unit)?,
    loading: Boolean,
    loadingLabel: String?,
    bottom: (@Composable () -> Unit)?,
    showsDetail: Boolean,
    showsSide: Boolean,
    line: KitStatus?,
    ownsLine: Boolean,
) {
    val modifier = Modifier
    val hairline = LocalKitColors.current.hairline
    val listPane: @Composable () -> Unit = {
        KitScreen(
            topBar =
                if (ownsLine)
                    ({
                        Column {
                            topBar?.invoke()
                            KitStatusLineFold(line)
                        }
                    })
                else topBar,
            status = status,
            search = search,
            loading = loading,
            loadingLabel = loadingLabel,
            bottom = bottom,
            page = showsDetail && topBar == null,
            body = list,
        )
    }
    if (!showsDetail) {
        Box(
            modifier
                .fillMaxSize()
                .semantics { isTraversalGroup = true }
                .testTag("kit-screen-pane-list")
        ) {
            listPane()
        }
        return
    }
    fun Modifier.endRule() = drawBehind {
        val x =
            if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 0.5f
            else size.width - 0.5f
        drawLine(hairline, Offset(x, 0f), Offset(x, size.height), 1f)
    }
    ScreenSheetHost(modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.width(Layout.PANE_LIST_WIDTH.dp)
                    .fillMaxHeight()
                    .endRule()
                    .semantics { isTraversalGroup = true }
                    .testTag("kit-screen-pane-list")
            ) {
                listPane()
            }
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .semantics { isTraversalGroup = true }
                    .testTag("kit-screen-pane-detail"),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = Layout.PANE_DETAIL_MAX_WIDTH.dp).fillMaxSize()) {
                    (detail ?: emptyDetail)()
                }
            }
            if (showsSide && side != null) {
                Box(
                    Modifier.width(Layout.PANE_SIDE_WIDTH.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            val x =
                                if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl)
                                    size.width - 0.5f
                                else 0.5f
                            drawLine(hairline, Offset(x, 0f), Offset(x, size.height), 1f)
                        }
                        .semantics { isTraversalGroup = true }
                        .testTag("kit-screen-pane-side")
                ) {
                    side()
                }
            }
        }
    }
}
