package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListState
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampDims
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
     * The choice shown on this device. The saved choice wins. Without one, the dock's old switch decides: "false" is
     * [OtherConversationsMode.Hidden], anything else [OtherConversationsMode.Rings]. When the old switch is there it is
     * moved once: the new key is written, and only after that write is acknowledged is the old key removed (a refused
     * write keeps the old key and tries again next start).
     */
    suspend fun loadMode(): OtherConversationsMode {
        val saved = store.read(EdgeLightKeys.MODE)
        val legacy = store.read(EdgeLightKeys.LEGACY_SHOWN)
        val mode = OtherConversationsMode.migrate(saved, legacy)
        if (legacy != null) {
            val moved = OtherConversationsMode.fromStored(saved) != null || store.write(EdgeLightKeys.MODE, mode.stored)
            if (moved) store.remove(EdgeLightKeys.LEGACY_SHOWN)
        }
        return mode
    }

    suspend fun saveMode(mode: OtherConversationsMode): Boolean {
        val ok = store.write(EdgeLightKeys.MODE, mode.stored)
        // The old switch must not come back after the choice was made (it would win on a failed new write).
        if (ok) store.remove(EdgeLightKeys.LEGACY_SHOWN)
        return ok
    }

    suspend fun loadPlace(): EdgeLightPlace =
        EdgeLightPlace(
            side = if (store.read(EdgeLightKeys.SIDE) == "start") KitLampSide.Start else KitLampSide.End,
            height = EdgeLightHeight.fromStored(store.read(EdgeLightKeys.HEIGHT)),
        )

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

/** The light's pure rules: which parts mount, where, when it hides and what its face says. */
internal object EdgeLightRules {
    /**
     * Rings (and Hidden, which is the rings' own "draw nothing") mount the dock host exactly as before the light
     * existed. Only the Edge light choice leaves the dock host out and mounts the light.
     */
    fun mounts(mode: OtherConversationsMode): EdgeMounts =
        EdgeMounts(rings = mode != OtherConversationsMode.EdgeLight, light = mode == OtherConversationsMode.EdgeLight)

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

    /** Hidden while the keyboard is up (the kit's one value; its own settle hold applies, no extra delay here). */
    fun visible(keyboardUp: Boolean): Boolean = !keyboardUp

    /**
     * The face for [model]: a mark per other conversation that needs the person, failed or is working, and the number
     * needs + failed. The conversation on screen and [page] (the conversation a Files, Changes or Git page belongs to)
     * are left out: it is "other conversations". A finished conversation shows no mark in this version.
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
                    DockListState.Done, DockListState.Idle -> null
                }
            }
        return KitLampFace(count = counts(model, page).number, marks = marks)
    }

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
        val touchMax = (KitLampDims.MAX_H + 2 * KitLampDims.TOUCH_PAD).toFloat()
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
        val touchMax = (KitLampDims.MAX_H + 2 * KitLampDims.TOUCH_PAD).toFloat()
        val top = maxOf(lowDp, areaDp - touchMax)
        return when (height) {
            EdgeLightHeight.Low -> minOf(lowDp, top)
            EdgeLightHeight.Middle -> (areaDp * MIDDLE_SHARE).coerceIn(minOf(lowDp, top), top)
            EdgeLightHeight.High -> (areaDp * HIGH_SHARE).coerceIn(minOf(lowDp, top), top)
        }
    }
}
