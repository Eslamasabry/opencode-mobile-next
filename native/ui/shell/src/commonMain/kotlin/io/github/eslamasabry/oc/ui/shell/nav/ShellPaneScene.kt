package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumn
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumnPage
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.LocalRestingBeside

/*
 * Big screens, slice 3 (BS-LIST-PANE): on a window 840 dp wide or more that is not short, a list stays as the start
 * pane and the page opened from it fills the rest. Under that the shell is the phone's single page, unchanged. The back
 * stack is the same in both: [list, page] shows as two panes or as the page alone, so a rotation or a fold changes
 * only how the stack is drawn.
 */

/**
 * One list-detail pair: the list entry, the top entries that open beside it, and the page the detail side shows while
 * only the list is open ([rest] makes a fresh one: never an empty pane). A pair needs nothing from its features: the
 * routes say which is which.
 */
internal class ShellPanePair(
    val isList: (NavKey) -> Boolean,
    val isDetail: (NavKey) -> Boolean,
    val rest: () -> NavKey,
    /** A route that asks for what the resting page already is (the list's +): opened beside a page it closes that page. */
    val isRest: (NavKey) -> Boolean = { false },
)

/**
 * The resting pages beside the lists (Home: New conversation). Not on the back stack. When a page opens beside the list
 * while one rests there, that one retires: it leaves the screen, a fresh one takes its place for next time, and the
 * retired one keeps its view model for a while (its first message may still be on its way: the phone keeps its New
 * conversation under the conversation it opened until that settles), the last [KEEP] of them.
 */
@Stable
internal class ShellRestPages {
    private val current =
        mutableStateMapOf<ShellPanePair, NavKey>().apply { ShellPanes.pairs.forEach { put(it, it.rest()) } }

    /** Retired resting pages, oldest first; still decorated (their view models live), never drawn. */
    val retired: SnapshotStateList<NavKey> = mutableStateListOf()

    fun current(pair: ShellPanePair): NavKey = current.getValue(pair)

    fun retire(pair: ShellPanePair) {
        retired.add(current(pair))
        while (retired.size > KEEP) retired.removeAt(0)
        current[pair] = pair.rest()
    }

    private companion object {
        const val KEEP = 3
    }
}

/** What the shell's navigation display needs about panes: whether lists stand beside pages, and the resting pages. */
@Immutable
internal class ShellPanesState(val beside: Boolean, val rest: ShellRestPages)

/** Provided by NavShell; null (no panes) in a display composed on its own. */
internal val LocalShellPanes: ProvidableCompositionLocal<ShellPanesState?> = staticCompositionLocalOf { null }

/** How the top of a back stack is laid out in two panes: the list's index, and whether a detail is open beside it. */
internal class ShellPaneLayout(val pair: ShellPanePair, val listIndex: Int, val detail: Boolean)

internal object ShellPanes {
    /** Metadata the shell puts on every entry: its route (a NavEntry does not show its key to a scene strategy). */
    const val ROUTE_KEY: String = "oc.shell.pane.route"

    /** The list pane's narrowest width, dp (an 840 dp window). */
    const val LIST_MIN_DP: Int = 320

    /** The list pane's width on a 1280 dp window, dp: Home as on the smallest phone the app is designed for. */
    const val LIST_DP: Int = 360

    /** The list pane's widest, dp: it reaches it before the conversation's column passes its cap. */
    const val LIST_MAX_DP: Int = 420

    /** The window share the list takes until it reaches [LIST_DP] (360 of 1280). */
    private const val LIST_SHARE: Float = LIST_DP / 1280f

    /**
     * The list pane's width on a [windowDp] window (the panes always use the whole width): 320 dp at 840, growing with
     * the window to 360 at 1280; past that the conversation pane takes what comes, until its column would pass its cap
     * ([KitPaneColumn.PAGE_MAX_DP]), where the list grows first, to 420; only then two equal margins.
     * Large text widens it as the sidebar grows ([Layout.sidebarWidth]), never past 420.
     */
    fun listWidth(windowDp: Float, fontScale: Float): Float {
        val atLeast = (windowDp * LIST_SHARE).coerceIn(LIST_MIN_DP.toFloat(), LIST_DP.toFloat())
        val beforeMargins = windowDp - KitPaneColumn.PAGE_MAX_DP
        val forWindow = beforeMargins.coerceIn(atLeast, LIST_MAX_DP.toFloat())
        return maxOf(forWindow, Layout.sidebarWidth(fontScale, windowDp)).coerceAtMost(LIST_MAX_DP.toFloat())
    }

    /**
     * Every list-detail pair, the first that matches wins. THE one place a pair is added: the next (S6) is the Settings
     * hub (`SettingsRoute.Home`) as the list and a page opened from it (servers, agents, phone setup) as the detail.
     * With no conversation open, Home's pages side is a new conversation, ready to type into.
     */
    val pairs: List<ShellPanePair> =
        listOf(
            ShellPanePair(
                isList = { it == ChatsRoute.Home },
                isDetail = { it is ChatRoute },
                rest = { ChatsRoute.New(profile = null) },
                isRest = {
                    it is ChatsRoute.New && it.profile == null && it.draftKey == null && it.initialText.isEmpty() &&
                        it.reportKey == null && it.phoneAgent == null && it.directory == null
                },
            ),
        )

    /** Lists stand beside their pages on an Expanded or Large window that is not short (`Layout.EXPANDED_FROM`). */
    fun beside(window: KitWindowInfo): Boolean = window.cls.isWide && !window.short

    /** The two panes of [stack]'s top: a list on top, or a detail right above its list; null for a single page. */
    fun layoutOf(stack: List<NavKey>): ShellPaneLayout? {
        val top = stack.lastOrNull()
        val below = stack.getOrNull(stack.lastIndex - 1)
        return pairs.firstNotNullOfOrNull { pair ->
            when {
                top == null -> null
                pair.isList(top) -> ShellPaneLayout(pair, stack.lastIndex, detail = false)
                below != null && pair.isDetail(top) && pair.isList(below) ->
                    ShellPaneLayout(pair, stack.lastIndex - 1, detail = true)
                else -> null
            }
        }
    }

    /**
     * The stack after [route] opens on a window where lists stand beside their pages: it goes right above its list,
     * whatever opened it (a row, All conversations, Search, a notification) and whatever was over the list, so it
     * always lands beside the list; the stack never grows per tap and Back returns to the list. Null when [route] is
     * no list's page or its list is not in the stack: it opens as it does on a phone.
     */
    fun openBeside(stack: List<NavKey>, route: NavKey): List<NavKey>? =
        pairs.firstOrNull { it.isDetail(route) }?.let { pair ->
            val listIndex = stack.indexOfLast(pair.isList)
            if (listIndex < 0) null else stack.subList(0, listIndex + 1).toList() + route
        }
}

/** True for an entry drawn inside a pane: it does not grow from a row, and the shell does not round it under Back. */
internal val LocalShellInPane: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * The Navigation 3 strategy: two panes while [beside] and the stack's top is a pair; otherwise null, and the next
 * strategy (the single page) draws it. Its own, not material3-adaptive's list-detail strategy: that one needs pane
 * metadata on every feature's entries and the Material adaptive scaffold; this needs the routes only. [rest]: the
 * list's resting page, decorated by the shell outside the back stack (`ShellNavDisplay`), while only the list is open.
 */
internal class ShellPaneSceneStrategy(private val beside: Boolean, private val rest: NavEntry<NavKey>?) :
    SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val routes = entries.mapNotNull { it.metadata[ShellPanes.ROUTE_KEY] as? NavKey }
        val layout = ShellPanes.layoutOf(routes).takeIf { beside && routes.size == entries.size }
        return layout?.let {
            ShellPaneScene(
                list = entries[it.listIndex],
                detail = if (it.detail) entries.last() else null,
                rest = if (it.detail) null else rest,
                previousEntries = entries.dropLast(1),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ShellPaneSceneStrategy && other.beside == beside && other.rest == rest

    override fun hashCode(): Int = beside.hashCode() * 31 + rest.hashCode()
}

/**
 * The list and its open page. Keyed by the list, so opening, replacing and closing the page is one scene: no page
 * motion runs between panes and the list keeps its place, scroll and focus. The tree never changes shape: the detail
 * pane holds the open page or the list's resting page in the same parent (the lookahead rule, `ShellNavDisplay`). The
 * resting page is not on the back stack: Back with only the list open leaves, as Home does on a phone.
 */
internal class ShellPaneScene(
    val list: NavEntry<NavKey>,
    val detail: NavEntry<NavKey>?,
    private val rest: NavEntry<NavKey>?,
    override val previousEntries: List<NavEntry<NavKey>>,
) : Scene<NavKey> {
    override val key: Any = PaneSceneKey(list.contentKey)
    override val entries: List<NavEntry<NavKey>> = listOfNotNull(list, detail)
    override val content: @Composable () -> Unit = { PanesRow(list, detail, rest) }

    override fun equals(other: Any?): Boolean =
        other is ShellPaneScene && other.list == list && other.detail == detail && other.rest == rest &&
            other.previousEntries == previousEntries

    override fun hashCode(): Int =
        ((list.hashCode() * 31 + detail.hashCode()) * 31 + rest.hashCode()) * 31 + previousEntries.hashCode()
}

private data class PaneSceneKey(val list: Any)

@Composable
private fun PanesRow(list: NavEntry<NavKey>, detail: NavEntry<NavKey>?, rest: NavEntry<NavKey>?) {
    val detailFocus = remember { FocusRequester() }
    val listWidth = ShellPanes.listWidth(LocalKitWindow.current.widthDp, LocalDensity.current.fontScale)
    val page = detail ?: rest
    val open = page?.metadata?.get(ShellPanes.ROUTE_KEY) as? NavKey
    CompositionLocalProvider(LocalShellInPane provides true) {
        Row(Modifier.fillMaxSize().typingLandsBeside(detailFocus)) {
            KitPaneWindow(Modifier.width(listWidth.dp).fillMaxHeight()) {
                Box {
                    // The list shows the open page's row as the chosen one (and, with the resting page open, no second
                    // way to it).
                    CompositionLocalProvider(LocalOpenBeside provides open) { list.Content() }
                    KitDivider(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), vertical = true)
                }
            }
            // Beside the list the page fills the pane up to its column's cap (header, transcript and message box on
            // one pair of edges); past the cap it is centred: two equal margins, never one band.
            KitPaneColumnPage(Modifier.weight(1f).fillMaxHeight().focusRequester(detailFocus).focusGroup()) {
                CompositionLocalProvider(LocalRestingBeside provides (detail == null)) {
                    if (page != null) key(page.contentKey) { page.Content() }
                }
            }
        }
    }
}

/**
 * N-55 beside a list: a letter typed with nothing focused is steered by the shell's park with `moveFocus(Enter)`, which
 * enters the top-left child, the list. This stop takes that steer once and hands it to the detail pane, where the
 * conversation's own landing puts it in the message box. Tab and arrows (any other direction) stop here once, as on
 * the park, and go on to the list; a request for a node inside never stops here. Pattern: `chatTypingLanding`.
 */
private fun Modifier.typingLandsBeside(detail: FocusRequester): Modifier {
    var steered = false
    return focusProperties {
            canFocus = false
            onEnter = { steered = requestedFocusDirection == FocusDirection.Enter }
        }
        .focusTarget()
        .onFocusChanged {
            val steer = steered
            steered = false
            if (it.isFocused && steer) detail.requestFocus(FocusDirection.Enter)
        }
        .focusTarget()
}

/**
 * The scene strategies for the shell's display: two panes where [panes] says lists stand beside their pages, then the
 * single page. The resting pages (the current one while only its list is open, and the retired ones) are not on the
 * back stack, so they get their own saved state and view models here; a retired one keeps its view model until it
 * drops out of [ShellRestPages.retired] or the window stops showing panes.
 */
@Composable
internal fun rememberPaneStrategies(
    panes: ShellPanesState?,
    backStack: List<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<SceneStrategy<NavKey>> {
    val beside = panes?.beside == true
    val layout = if (beside) ShellPanes.layoutOf(backStack) else null
    val current = layout?.let { panes?.rest?.current(it.pair) }
    val keys = if (current == null) emptyList() else listOf(current) + panes?.rest?.retired.orEmpty()
    val entries = remember(keys, entryProvider) { keys.mapNotNull { entryProvider.entryOrNull(it) } }
    val decorated =
        rememberDecoratedNavEntries(
            entries,
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        )
    // Drawn only while the list is alone on top: the current resting page.
    val rest =
        decorated.firstOrNull { it.metadata[ShellPanes.ROUTE_KEY] == current }.takeIf { layout?.detail == false }
    return remember(beside, rest) { listOf(ShellPaneSceneStrategy(beside, rest), SinglePaneSceneStrategy()) }
}

/**
 * The entry for [key], or null when no feature installed one: a shell without the resting page's feature (a test, a
 * gallery) shows the list with nothing beside it. Nav3's entry provider refuses an unknown key with an
 * IllegalStateException.
 */
private fun ((NavKey) -> NavEntry<NavKey>).entryOrNull(key: NavKey): NavEntry<NavKey>? =
    try {
        this(key)
    } catch (_: IllegalStateException) {
        null
    }
