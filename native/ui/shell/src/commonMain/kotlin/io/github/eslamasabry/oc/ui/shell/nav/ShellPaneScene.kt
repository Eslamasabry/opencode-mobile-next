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
import androidx.compose.runtime.key
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
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitCapsAtStart
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.LocalRestingBeside

/*
 * Big screens, slice 3 (BS-LIST-PANE): on a window 840 dp wide or more that is not short, a list stays as the start pane
 * and the page opened from it fills the rest. Under that the shell is the phone's single page, unchanged. The back
 * stack is the same in both: [list, page] shows as two panes or as the page alone, so a rotation or a fold changes
 * only how the stack is drawn.
 */

/**
 * One list-detail pair: the list entry, the top entries that open beside it, and the page the detail side shows while
 * only the list is open ([rest]: never an empty pane). A pair needs nothing from its features: the routes say which is
 * which.
 */
internal class ShellPanePair(
    val isList: (NavKey) -> Boolean,
    val isDetail: (NavKey) -> Boolean,
    val rest: NavKey,
)

/** How the top of a back stack is laid out in two panes: the list's index, and whether a detail is open beside it. */
internal class ShellPaneLayout(val pair: ShellPanePair, val listIndex: Int, val detail: Boolean)

internal object ShellPanes {
    /** Metadata the shell puts on every entry: its route (a NavEntry does not show its key to a scene strategy). */
    const val ROUTE_KEY: String = "oc.shell.pane.route"

    /**
     * The list pane's width at text size 1.0: Home exactly as on the smallest phone the app is designed for, where
     * nothing is cut by rule (a row's state line, "Waiting for you: …", stays whole). It grows with large text.
     */
    const val LIST_WIDTH_DP: Int = 360

    /**
     * Every list-detail pair, the first that matches wins. THE one place a pair is added: the next (S6) is the Settings
     * hub (`SettingsRoute.Home`) as the list and a page opened from it (servers, agents, phone setup) as the detail.
     * With no conversation open, Home's pages side is a new conversation, ready to type into.
     */
    val pairs: List<ShellPanePair> =
        listOf(
            ShellPanePair(isList = { it == ChatsRoute.Home }, isDetail = { it is ChatRoute }, rest = ChatsRoute.New(profile = null)),
        )

    /** Lists stand beside their pages on an Expanded or Large window that is not short (`Layout.EXPANDED_FROM`). */
    fun beside(window: KitWindowInfo): Boolean = window.cls.isWide && !window.short

    /** The two-pane layout of [stack]'s top: a list on top, or a detail right above its list; null for a single page. */
    fun layoutOf(stack: List<NavKey>): ShellPaneLayout? {
        val top = stack.lastOrNull() ?: return null
        val below = stack.getOrNull(stack.lastIndex - 1)
        for (pair in pairs) {
            if (pair.isList(top)) return ShellPaneLayout(pair, stack.lastIndex, detail = false)
            if (below != null && pair.isDetail(top) && pair.isList(below)) {
                return ShellPaneLayout(pair, stack.lastIndex - 1, detail = true)
            }
        }
        return null
    }

    /**
     * The stack after [route] opens from a list that shows beside its pages: it takes the detail's place (the list
     * stays, Back returns to the list's empty side, the stack never grows per tap). Null when [route] is not a page
     * of the list now on screen, so it opens as it does on a phone.
     */
    fun openBeside(stack: List<NavKey>, route: NavKey): List<NavKey>? {
        val layout = layoutOf(stack) ?: return null
        if (!layout.pair.isDetail(route)) return null
        return stack.subList(0, layout.listIndex + 1).toList() + route
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
        if (!beside) return null
        val routes = entries.map { it.metadata[ShellPanes.ROUTE_KEY] as? NavKey ?: return null }
        val layout = ShellPanes.layoutOf(routes) ?: return null
        return ShellPaneScene(
            list = entries[layout.listIndex],
            detail = if (layout.detail) entries.last() else null,
            rest = if (layout.detail) null else rest,
            previousEntries = entries.dropLast(1),
        )
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
    // 360 dp at text 1.0; with large text it grows as the sidebar does (Dart `KitLayout.sidebarWidth`, up to 400 dp
    // or a third of the window).
    val scaled = Layout.sidebarWidth(LocalDensity.current.fontScale, LocalKitWindow.current.widthDp)
    val listWidth = maxOf(ShellPanes.LIST_WIDTH_DP.toFloat(), scaled)
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
            KitPaneWindow(Modifier.weight(1f).fillMaxHeight().focusRequester(detailFocus).focusGroup()) {
                // Beside the list, the page's reading column starts at the pane's start (decision (a)).
                CompositionLocalProvider(LocalKitCapsAtStart provides true, LocalRestingBeside provides (detail == null)) {
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
