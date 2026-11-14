package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListRow
import io.github.eslamasabry.oc.engine.attention.dock.DockListState
import io.github.eslamasabry.oc.engine.attention.dock.DockProgress
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampMarkKind
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRules
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import io.github.eslamasabry.oc.ui.routes.LocalTerminalRoute
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edge light's wiring, phase A: the choice and its saved keys, which pages carry it, its face, its place. */
class EdgeLightWiringTest {
    private class MapStore(val data: MutableMap<String, String> = mutableMapOf(), var refuseWrites: Boolean = false) :
        AttentionKeyStore {
        override suspend fun read(key: String): String? = data[key]

        override suspend fun write(key: String, value: String): Boolean {
            if (refuseWrites) return false
            data[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            data.remove(key)
            return true
        }
    }

    private fun ref(s: String) = ConversationRef(ProfileId("p"), SessionId(s))

    private fun row(
        s: String,
        state: DockListState,
        progress: DockProgress? = null,
        open: Boolean = false,
        pulse: Int = 0,
    ) =
        DockListRow(
            ref = ref(s), name = s, state = state, progress = progress, thinking = false, request = null,
            causedBy = null, newestLine = null, subAgents = persistentListOf(),
            subAgentsMore = 0, serverName = null, serverMark = null, agent = null, pinned = false, lastActivityMs = null,
            isOpen = open, pulse = pulse,
        )

    private fun model(vararg rows: DockListRow) = DockListModel(rows = rows.toList().toImmutableList())

    // ---- the choice and its migration -----------------------------------------------------------------------------

    @Test
    fun `settings_default_isRings nothing saved shows Rings and writes nothing`() = runTest {
        val store = MapStore()
        assertEquals(OtherConversationsMode.Rings, EdgeLightPrefs(store).loadMode())
        assertTrue(store.data.isEmpty())
    }

    @Test
    fun `migration_dockShownFalse_isHidden and the old key is gone after the move`() = runTest {
        val store = MapStore(mutableMapOf(EdgeLightKeys.LEGACY_SHOWN to "false"))
        assertEquals(OtherConversationsMode.Hidden, EdgeLightPrefs(store).loadMode())
        assertEquals("hidden", store.data[EdgeLightKeys.MODE])
        assertFalse(EdgeLightKeys.LEGACY_SHOWN in store.data)
        // Second start: nothing to move, same answer.
        assertEquals(OtherConversationsMode.Hidden, EdgeLightPrefs(store).loadMode())
    }

    @Test
    fun `migration_dockShownTrue_isRings`() = runTest {
        val store = MapStore(mutableMapOf(EdgeLightKeys.LEGACY_SHOWN to "true"))
        assertEquals(OtherConversationsMode.Rings, EdgeLightPrefs(store).loadMode())
        assertEquals("rings", store.data[EdgeLightKeys.MODE])
        assertFalse(EdgeLightKeys.LEGACY_SHOWN in store.data)
    }

    @Test
    fun `migration_refusedWrite keeps the old key and tries again next start`() = runTest {
        val store = MapStore(mutableMapOf(EdgeLightKeys.LEGACY_SHOWN to "false"), refuseWrites = true)
        assertEquals(OtherConversationsMode.Hidden, EdgeLightPrefs(store).loadMode())
        assertEquals("false", store.data[EdgeLightKeys.LEGACY_SHOWN])
        store.refuseWrites = false
        assertEquals(OtherConversationsMode.Hidden, EdgeLightPrefs(store).loadMode())
        assertEquals("hidden", store.data[EdgeLightKeys.MODE])
    }

    @Test
    fun `a saved choice survives a restart and wins over a stale old switch`() = runTest {
        val store = MapStore()
        assertTrue(EdgeLightPrefs(store).saveMode(OtherConversationsMode.EdgeLight))
        store.data[EdgeLightKeys.LEGACY_SHOWN] = "false"
        assertEquals(OtherConversationsMode.EdgeLight, EdgeLightPrefs(store).loadMode())
        assertFalse(EdgeLightKeys.LEGACY_SHOWN in store.data)
    }

    // ---- device-scoped keys -----------------------------------------------------------------------------------------

    @Test
    fun `deviceKeys_persistAsDeviceWide and carry no profile id`() {
        for (key in listOf(EdgeLightKeys.MODE, EdgeLightKeys.SIDE, EdgeLightKeys.HEIGHT, EdgeLightKeys.BACK_NOTES, EdgeLightKeys.TIPS, EdgeLightKeys.LEGACY_SHOWN)) {
            assertTrue(EdgeLightKeys.isDeviceKey(key), key)
        }
        // A profile key is never taken for a device key (it would be written with no profile lane and never swept).
        assertFalse(EdgeLightKeys.isDeviceKey("oc.dockPins.p1"))
        assertFalse(EdgeLightKeys.isDeviceKey("oc.edgeLight.side.p1"))
    }

    @Test
    fun `place and taught counts survive a restart`() = runTest {
        val store = MapStore()
        val first = EdgeLightPrefs(store)
        assertEquals(EdgeLightPlace(), first.loadPlace())
        assertEquals(EdgeLightTaught(), first.loadTaught())
        assertTrue(first.savePlace(EdgeLightPlace(KitLampSide.Start, EdgeLightHeight.High)))
        assertTrue(first.saveTaught(EdgeLightTaught(backNotes = 2, tips = 1)))
        // A new object over the same stored values is a restart.
        val second = EdgeLightPrefs(store)
        assertEquals(EdgeLightPlace(KitLampSide.Start, EdgeLightHeight.High), second.loadPlace())
        assertEquals(EdgeLightTaught(backNotes = 2, tips = 1), second.loadTaught())
    }

    @Test
    fun `garbage in the saved keys falls back to the defaults`() = runTest {
        val store = MapStore(mutableMapOf(EdgeLightKeys.SIDE to "x", EdgeLightKeys.HEIGHT to "y", EdgeLightKeys.BACK_NOTES to "-4", EdgeLightKeys.TIPS to "z"))
        val prefs = EdgeLightPrefs(store)
        assertEquals(EdgeLightPlace(KitLampSide.End, EdgeLightHeight.Low), prefs.loadPlace())
        assertEquals(EdgeLightTaught(0, 0), prefs.loadTaught())
    }

    // ---- what is mounted --------------------------------------------------------------------------------------------

    @Test
    fun `ringsMode_samePartsMountedAsBefore the rings host stays and no light is added`() {
        assertEquals(EdgeMounts(rings = true, light = false), EdgeLightRules.mounts(OtherConversationsMode.Rings))
        // Hidden keeps the rings' own host (which draws nothing), as the old off switch did.
        assertEquals(EdgeMounts(rings = true, light = false), EdgeLightRules.mounts(OtherConversationsMode.Hidden))
    }

    @Test
    fun `edgeLightMode_ringsNotMounted_lightIs`() {
        assertEquals(EdgeMounts(rings = false, light = true), EdgeLightRules.mounts(OtherConversationsMode.EdgeLight))
    }

    @Test
    fun `which pages get the light - a conversation, its Files, Changes and Git`() {
        val a = ref("a")
        assertEquals(a, EdgeLightRules.pageConversation(ChatRoute(a)))
        assertEquals(a, EdgeLightRules.pageConversation(FilesRoute.Folder(a)))
        assertEquals(a, EdgeLightRules.pageConversation(FilesRoute.File(a, "src/Main.kt")))
        assertEquals(a, EdgeLightRules.pageConversation(ReviewRoute.Changes(a)))
        assertEquals(a, EdgeLightRules.pageConversation(GitRoute(a)))
    }

    @Test
    fun `which pages do not - Home, Terminal, Preview, Settings and a project's own Files`() {
        assertNull(EdgeLightRules.pageConversation(null))
        assertNull(EdgeLightRules.pageConversation(ChatsRoute.Home))
        assertNull(EdgeLightRules.pageConversation(ChatsRoute.All()))
        assertNull(EdgeLightRules.pageConversation(LocalTerminalRoute))
        assertNull(EdgeLightRules.pageConversation(PreviewRoute.Ports(ref("a"))))
        assertNull(EdgeLightRules.pageConversation(SettingsRoute.Home))
        assertNull(EdgeLightRules.pageConversation(FilesRoute.ProjectFolder(ProfileId("p"), "/work")))
        assertNull(EdgeLightRules.pageConversation(ReviewRoute.Project(ProfileId("p"), "/work")))
    }

    @Test
    fun `the shell hosts the light on every page but the conversation's own, which mounts it in its frame`() {
        val a = ref("a")
        assertNull(EdgeLightRules.shellPageConversation(ChatRoute(a)))
        assertEquals(a, EdgeLightRules.shellPageConversation(FilesRoute.Folder(a)))
        assertEquals(a, EdgeLightRules.shellPageConversation(GitRoute(a)))
        assertNull(EdgeLightRules.shellPageConversation(SettingsRoute.Home))
    }

    @Test
    fun `hidden_whileKeyboardUp`() {
        assertFalse(EdgeLightRules.visible(keyboardUp = true))
        assertTrue(EdgeLightRules.visible(keyboardUp = false))
    }

    // ---- the face ---------------------------------------------------------------------------------------------------

    @Test
    fun `face number is needs plus failed and marks list needs then failed then runs`() {
        val list =
            model(
                row("w1", DockListState.Working, DockProgress(1, 4)),
                row("f1", DockListState.Failed),
                row("n1", DockListState.NeedsYou),
                row("n2", DockListState.NeedsYou),
                row("d1", DockListState.Done),
                row("i1", DockListState.Idle),
                row("w2", DockListState.Working, DockProgress(3, 4)),
                row("w3", DockListState.Working),
            )
        val face = EdgeLightRules.face(list, page = null)
        assertEquals(3, face.count)
        // Done and idle conversations show no mark; everything else does.
        assertEquals(6, face.marks.size)
        val shown = KitLampRules.shown(face.marks)
        assertEquals(listOf(KitLampMarkKind.NeedsYou, KitLampMarkKind.NeedsYou, KitLampMarkKind.Failed), shown.map { it.kind })
        assertEquals(EdgeLightCounts(needs = 2, failed = 1, working = 3), EdgeLightRules.counts(list, null))
    }

    @Test
    fun `a run's mark carries done over total, and none when it has no task list`() {
        val face =
            EdgeLightRules.face(
                model(row("w1", DockListState.Working, DockProgress(1, 4)), row("w2", DockListState.Working)),
                page = null,
            )
        assertEquals(0.25f, face.marks.first { it.key == ref("w1") }.progress)
        assertNull(face.marks.first { it.key == ref("w2") }.progress)
    }

    @Test
    fun `the conversation on screen and the page's own conversation are not other conversations`() {
        val list = model(row("open", DockListState.NeedsYou, open = true), row("page", DockListState.Failed), row("x", DockListState.NeedsYou))
        val face = EdgeLightRules.face(list, page = ref("page"))
        assertEquals(1, face.count)
        assertEquals(1, face.marks.size)
    }

    @Test
    fun `nothing going on is the quiet tab`() {
        val face = EdgeLightRules.face(model(row("a", DockListState.Idle), row("b", DockListState.Done)), page = null)
        assertTrue(face.quiet)
        assertEquals(0, face.count)
    }

    @Test
    fun `a request that comes again is a new mark key so it pulses again`() {
        val first = EdgeLightRules.face(model(row("a", DockListState.NeedsYou, pulse = 1)), null).marks.single().key
        val again = EdgeLightRules.face(model(row("a", DockListState.NeedsYou, pulse = 2)), null).marks.single().key
        assertTrue(first != again)
    }

    // ---- the place --------------------------------------------------------------------------------------------------

    @Test
    fun `low is the token and never lower, middle and high stay inside the free height`() {
        val low = EdgeLightRules.LOW_ABOVE_SAFE_DP
        val area = 700f
        assertEquals(low, EdgeLightRules.bottomOffsetDp(EdgeLightHeight.Low, area, low))
        val mid = EdgeLightRules.bottomOffsetDp(EdgeLightHeight.Middle, area, low)
        val high = EdgeLightRules.bottomOffsetDp(EdgeLightHeight.High, area, low)
        assertTrue(low <= mid && mid < high, "low $low < middle $mid < high $high")
        // The tallest touch box (84 dp) still fits under the top of the free area.
        assertTrue(high + 84f <= area)
        // A short window (landscape): nothing goes outside it, and Low stays Low.
        val short = 300f
        for (h in EdgeLightHeight.entries) {
            val at = EdgeLightRules.bottomOffsetDp(h, short, low)
            assertTrue(at >= low, "$h at $at")
        }
    }
}
