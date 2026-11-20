package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockKeys
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListState
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampDims
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRules
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampFace
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampMark
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampMarkKind
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute

/**
 * The edge light's device-wide saved keys. None has a profile id, so the profile deletion sweep leaves them alone (like
 * `oc.dockSide`); [AppEdgeDock]'s key store writes every one of them on the app lane.
 */
internal object EdgeLightKeys {
    /** The Other conversations choice: "rings", "edge-light" or "hidden". Absent: see [EdgeLightPrefs.loadMode]. */
    const val MODE = "oc.otherConversations"

    /** The dock's old on/off switch ("false" = off, absent or anything else = on); read once, then removed. */
    const val LEGACY_SHOWN = "oc.dockShown"

    /** The light's edge, "start" or "end" (reading order, so it follows the language). */
    const val SIDE = "oc.edgeLight.side"

    /** The light's height, "low", "middle" or "high". */
    const val HEIGHT = "oc.edgeLight.height"

    /** How many "Back returns to ..." notes were shown (the first three switches show one). */
    const val BACK_NOTES = "oc.edgeLight.backNotes"

    /** How many of the light's first-use tips were shown. */
    const val TIPS = "oc.edgeLight.tips"

    val all: Set<String> = setOf(MODE, LEGACY_SHOWN, SIDE, HEIGHT, BACK_NOTES, TIPS)

    /** True for a key that is saved for the device and written with no profile lane. */
    fun isDeviceKey(key: String): Boolean = key in all
}

enum class EdgeLightHeight(val stored: String) {
    Low("low"),
    Middle("middle"),
    High("high"),
    ;

    companion object {
        fun fromStored(value: String?): EdgeLightHeight = entries.firstOrNull { it.stored == value } ?: Low
    }
}

/** Where the light sits: the default is the thumb's edge (end), low. */
data class EdgeLightPlace(val side: KitLampSide = KitLampSide.End, val height: EdgeLightHeight = EdgeLightHeight.Low)

/** What the light taught so far: Back notes shown and tips shown. Only counts; the words are the screen's. */
data class EdgeLightTaught(val backNotes: Int = 0, val tips: Int = 0)

/**
 * The light's saved choices over the app's key store. One instance per app; every write is device-wide.
 * [loadMode] is also the one-time migration of the dock's old on/off switch.
 */
internal class EdgeLightPrefs(private val store: AttentionKeyStore) {
    /**
     * The choice shown on this device. `"hidden"` stays [OtherConversationsMode.Hidden]; `"edge-light"`, retired
     * `"rings"`, absent and legacy `"true"` become [OtherConversationsMode.EdgeLight]; legacy `"false"` stays
     * [OtherConversationsMode.Hidden]. Non-destructive: old keys (`oc.dockShown`) are never deleted, and re-reading
     * gives the same answer.
     */
    suspend fun loadMode(): OtherConversationsMode {
        val saved = store.read(EdgeLightKeys.MODE)
        val legacy = store.read(EdgeLightKeys.LEGACY_SHOWN)
        val mode = OtherConversationsMode.migrate(saved, legacy)
        if (saved == "rings" || (saved == null && legacy != null)) {
            store.write(EdgeLightKeys.MODE, mode.stored)
        }
        return mode
    }

    suspend fun saveMode(mode: OtherConversationsMode): Boolean =
        store.write(EdgeLightKeys.MODE, mode.stored)

    /**
     * Where the light sits. If the light's own side was never saved and the retired dock was placed on `"start"`,
     * carry `"start"` across once without deleting `oc.dockSide`.
     */
    suspend fun loadPlace(): EdgeLightPlace {
        val savedSide = store.read(EdgeLightKeys.SIDE)
        val side =
            when {
                savedSide == "start" -> KitLampSide.Start
                savedSide == null && store.read(DockKeys.SIDE) == "start" -> {
                    store.write(EdgeLightKeys.SIDE, "start")
                    KitLampSide.Start
                }
                else -> KitLampSide.End
            }
        return EdgeLightPlace(
            side = side,
            height = EdgeLightHeight.fromStored(store.read(EdgeLightKeys.HEIGHT)),
        )
    }

    suspend fun savePlace(place: EdgeLightPlace): Boolean =
        store.write(EdgeLightKeys.SIDE, if (place.side == KitLampSide.Start) "start" else "end") &&
            store.write(EdgeLightKeys.HEIGHT, place.height.stored)

    suspend fun loadTaught(): EdgeLightTaught =
        EdgeLightTaught(
            backNotes = store.read(EdgeLightKeys.BACK_NOTES)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            tips = store.read(EdgeLightKeys.TIPS)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )

    suspend fun saveTaught(taught: EdgeLightTaught): Boolean =
        store.write(EdgeLightKeys.BACK_NOTES, taught.backNotes.toString()) &&
            store.write(EdgeLightKeys.TIPS, taught.tips.toString())
}

/** What the person chose, as the parts the app draws. [rings] is the dock's host; with [light] the rings are not mounted. */
internal data class EdgeMounts(val rings: Boolean, val light: Boolean)

/** How many of each kind the face counts (also what the spoken label says). */
internal data class EdgeLightCounts(val needs: Int, val failed: Int, val working: Int) {
    /** The number on the face: who needs you plus what failed. */
    val number: Int get() = needs + failed
}

/**
 * Whether the light mounts at all: the Edge light is chosen and no list stands beside the conversation. On a wide window
 * the conversation list is in view beside it (big screens), so the light, the phone's way between conversations, stays
 * hidden there; it comes back when the window shows the conversation alone.
 */
internal fun edgeLightShows(mode: OtherConversationsMode, listShown: Boolean): Boolean =
    EdgeLightRules.mounts(mode).light && !listShown

/** The light's pure rules: which parts mount, where, when it hides and what its face says. */
internal object EdgeLightRules {
    /**
     * The Rings dock is never mounted in any mode. Only the Edge light mounts, and only when
     * [OtherConversationsMode.EdgeLight] is chosen.
     */
    fun mounts(mode: OtherConversationsMode): EdgeMounts =
        EdgeMounts(rings = false, light = mode == OtherConversationsMode.EdgeLight)

    /**
     * The conversation whose page [route] is, for the pages that carry the light: a conversation, its Files, its
     * Changes and its Git page. Null for everything else (Home, Terminal, Preview, Settings, a project's own Files,
     * a diff, forms): no light there. A conversation's pages are told apart from the others by this reference, which
     * is also the one conversation the face leaves out.
     */
    fun pageConversation(route: AppRoute?): ConversationRef? =
        when (route) {
            is ChatRoute -> route.ref
            is FilesRoute.Folder -> route.from
            is FilesRoute.File -> route.from
            is ReviewRoute.Changes -> route.ref
            is GitRoute -> route.ref
            else -> null
        }

    /** Pages the shell mounts the light on. A conversation's own page mounts it inside its frame (under its sheets). */
    fun shellPageConversation(route: AppRoute?): ConversationRef? = if (route is ChatRoute) null else pageConversation(route)

    /**
     * Hidden while the keyboard is up (the kit's one value; its own settle hold applies, no extra delay here) and while
     * any kit modal part (sheet, dialog, menu) is open over the page.
     */
    fun visible(keyboardUp: Boolean, modalOpen: Boolean = false): Boolean = !keyboardUp && !modalOpen

    /**
     * The face for [model]: a mark per other conversation that needs the person, failed, is working or finished, and
     * the number needs + failed. The conversation on screen and [page] (the conversation a Files, Changes or Git page
     * belongs to) are left out: it is "other conversations".
     */
    fun face(model: DockListModel, page: ConversationRef?): KitLampFace {
        val others = model.rows.filter { !it.isOpen && it.ref != page }
        val marks =
            others.mapNotNull { row ->
                when (row.state) {
                    // The pulse rises each time a conversation needs the person again: a new request pulses once.
                    DockListState.NeedsYou -> KitLampMark(row.ref to row.pulse, KitLampMarkKind.NeedsYou)
                    DockListState.Failed -> KitLampMark(row.ref to row.pulse, KitLampMarkKind.Failed)
                    DockListState.Working ->
                        KitLampMark(
                            row.ref,
                            KitLampMarkKind.Working,
                            row.progress?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) },
                        )
                    DockListState.Done -> KitLampMark(row.ref, KitLampMarkKind.Finished)
                    DockListState.Idle -> null
                }
            }
        return KitLampFace(count = counts(model, page).number, marks = marks)
    }

    /** First-use tips shown per device: one short tip beside the closed light, tracked in `oc.edgeLight.tips`. */
    const val FIRST_USE_TIPS: Int = 1

    /** Keep the panel overlay briefly after close so its fold and hide-Undo bar finish, then unmount it. */
    const val PANEL_LINGER_MS: Long = 8_500L

    /** True while the list panel or place picker needs the list controller, list state and panel strings. */
    fun mountOverlay(open: Boolean, placing: Boolean, lingering: Boolean): Boolean = open || placing || lingering

    /**
     * True when the one-time tip beside the closed light may appear: prefs are loaded (no cold-start flash for people
     * who already saw it), fewer than [FIRST_USE_TIPS] shown, the light is visible, neither panel nor move is open
     * ([away]), and the page is [calm] (no request card, no other tip).
     */
    fun showTip(prefsLoaded: Boolean, tipsShown: Int, visible: Boolean, away: Boolean, calm: Boolean = true): Boolean =
        prefsLoaded && tipsShown < FIRST_USE_TIPS && visible && !away && calm

    fun counts(model: DockListModel, page: ConversationRef?): EdgeLightCounts {
        val others = model.rows.filter { !it.isOpen && it.ref != page }
        return EdgeLightCounts(
            needs = others.count { it.state == DockListState.NeedsYou },
            failed = others.count { it.state == DockListState.Failed },
            working = others.count { it.state == DockListState.Working },
        )
    }

    /** Low on a page with no message box: the bottom of the touch box this far above the safe area (plan v3.1 §2). */
    const val LOW_ABOVE_SAFE_DP: Float = 196f

    /** Low on a conversation page: the gap above the page's bottom chrome (the message box block). */
    const val LOW_ABOVE_CHROME_DP: Float = 8f

    /** Middle and High: how far up the free height the bottom of the touch box sits. */
    const val MIDDLE_SHARE: Float = 0.45f
    const val HIGH_SHARE: Float = 0.70f

    /**
     * Distance from the bottom of the page box to the bottom of the touch box. [pageDp] is the page's height under its
     * top chrome, [chromeDp] the bottom chrome's height (the message block with its chips), [lowDp] the gap Low keeps
     * above that chrome. Low follows the chrome; Middle and High are shares of the whole page box, so they stay where
     * they are while the phrase chips, a status strip or a request card come and go below them (device 2026-11-14: at
     * Middle the light jumped by the chips' height whenever a run started or ended). Neither sinks below Low, and the
     * tallest touch box never leaves the page.
     */
    fun bottomFromPageDp(height: EdgeLightHeight, pageDp: Float, chromeDp: Float, lowDp: Float): Float {
        val touchMax = KitLampRules.touchHeightDp(KitLampDims.MAX_H).toFloat()
        val low = chromeDp + lowDp
        val top = maxOf(low, pageDp - touchMax)
        return when (height) {
            EdgeLightHeight.Low -> minOf(low, top)
            EdgeLightHeight.Middle -> (pageDp * MIDDLE_SHARE).coerceIn(minOf(low, top), top)
            EdgeLightHeight.High -> (pageDp * HIGH_SHARE).coerceIn(minOf(low, top), top)
        }
    }

    /**
     * Distance from the bottom of the free area to the bottom of the touch box. [areaDp] is the free height (between
     * the top chrome and the bottom chrome), [lowDp] what Low is on this page. The light never goes below Low and
     * never above the area (a short window, a landscape phone, puts all three where it fits).
     */
    fun bottomOffsetDp(height: EdgeLightHeight, areaDp: Float, lowDp: Float): Float {
        val touchMax = KitLampRules.touchHeightDp(KitLampDims.MAX_H).toFloat()
        val top = maxOf(lowDp, areaDp - touchMax)
        return when (height) {
            EdgeLightHeight.Low -> minOf(lowDp, top)
            EdgeLightHeight.Middle -> (areaDp * MIDDLE_SHARE).coerceIn(minOf(lowDp, top), top)
            EdgeLightHeight.High -> (areaDp * HIGH_SHARE).coerceIn(minOf(lowDp, top), top)
        }
    }
}

/** True while the conversation on screen waits for the person's answer: its request card is on the page. */
internal fun edgeLightRequestOnPage(model: DockListModel, page: ConversationRef?): Boolean =
    model.rows.any { (it.isOpen || it.ref == page) && it.state == DockListState.NeedsYou }
