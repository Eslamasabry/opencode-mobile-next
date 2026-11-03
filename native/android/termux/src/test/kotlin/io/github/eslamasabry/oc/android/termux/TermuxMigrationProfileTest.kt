package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

// Flutter test/termux_migration_profile_test.dart, case for case, over a memory profile store and connection.
class TermuxMigrationProfileTest {
    private data class Location(val directory: String?, val workspace: String?)

    private var source = MigrationProfile(id = "termux", openCode = true, baseUrl = TERMUX_MANAGED_SERVER_URL, flavor = "v2")
    private val destination = MigrationProfile(id = "builtin", openCode = true, baseUrl = "http://127.0.0.1:4097", flavor = "v2")
    private val saved = mutableListOf<MigrationProfile>()
    private val locations = HashMap<String, Location>()
    private val prefs = HashMap<String, String>()
    private var activeId: String? = null
    private var connected = false
    private var starts = 0
    private var rejectConnect = false
    private var cancelAfterStart = false
    private var wanted = true
    private var secretFailure = false
    private var changeSelectionDuringStart = false
    private val connections = mutableListOf<String>()

    private val profiles = object : MigrationProfiles {
        override fun profile(id: String): MigrationProfile? = saved.firstOrNull { it.id == id }

        override fun locationDirectory(profileId: String): String? = locations[profileId]?.directory

        override suspend fun setLocationDirectory(profileId: String, directory: String) {
            locations[profileId] = Location(directory, workspace = null)
        }

        override suspend fun ensureBuiltinProfile(flavor: String): MigrationProfile {
            assertEquals(source.flavor, flavor)
            if (saved.none { it.id == destination.id }) saved.add(destination)
            return destination
        }

        override suspend fun startBuiltin(profile: MigrationProfile, stillWanted: () -> Boolean): Boolean {
            starts++
            if (changeSelectionDuringStart) activeId = "other"
            if (secretFailure) throw IllegalStateException("provider-token-do-not-expose")
            if (cancelAfterStart) wanted = false
            return stillWanted()
        }

        override suspend fun connect(profile: MigrationProfile) {
            connections.add(profile.id)
            activeId = profile.id
            connected = !rejectConnect || profile.id == source.id
        }

        override fun isConnectedTo(profile: MigrationProfile): Boolean = connected && activeId == profile.id

        override fun selectedProfileId(): String? = activeId
    }

    private lateinit var switcher: TermuxMigrationProfileSwitcher

    @Before
    fun setUp() {
        saved.add(source)
        activeId = source.id
        switcher = TermuxMigrationProfileSwitcher(profiles)
    }

    private suspend fun run(paths: Map<String, String> = emptyMap()): String = switcher.switchProfile(source.id, { wanted }, paths)

    @Test
    fun `B-LINUX-235 switch and repeat keep the source and map only the verified project location`() = runTest {
        locations[source.id] = Location("/root/projects/repo/src", "old-workspace")
        prefs["oc.sessionPins.termux"] = """{"old": ["session"]}"""
        val sourcePins = prefs["oc.sessionPins.termux"]
        repeat(2) {
            assertEquals(destination.id, run(mapOf("/root/projects/repo" to "/root/projects/import/repo")))
        }
        assertEquals(2, saved.size)
        assertSame(source, saved.first())
        assertEquals("old-workspace", locations[source.id]?.workspace)
        assertEquals("/root/projects/import/repo/src", locations[destination.id]?.directory)
        assertNull(locations[destination.id]?.workspace)
        assertEquals(sourcePins, prefs["oc.sessionPins.termux"])
        assertFalse("oc.sessionPins.builtin" in prefs)
        assertEquals(2, starts)
    }

    @Test
    fun `B-LINUX-235 path matching respects boundaries and rejects destination traversal`() = runTest {
        locations[source.id] = Location("/root/projects/repository", null)
        run(mapOf("/root/projects/repo" to "/root/projects/import/repo"))
        assertNull(locations[destination.id])
        expectFailure(TermuxMigrationFailure.invalidSelection) { run(mapOf("/root/projects/repo" to "/root/projects/../secret")) }
    }

    @Test
    fun `B-LINUX-235 a failed connect restores the source selection and keeps the destination for retry`() = runTest {
        rejectConnect = true
        expectFailure(TermuxMigrationFailure.profileSwitch) { run() }
        assertEquals(listOf(destination.id, source.id), connections)
        assertEquals(source.id, activeId)
        assertEquals(2, saved.size)
    }

    @Test
    fun `B-LINUX-235 cancellation before start leaves profiles unchanged`() = runTest {
        wanted = false
        expectFailure(TermuxMigrationFailure.cancelled) { run() }
        assertEquals(0, starts)
        assertEquals(listOf(source), saved)
    }

    @Test
    fun `B-LINUX-235 cancellation during start never selects the destination`() = runTest {
        cancelAfterStart = true
        expectFailure(TermuxMigrationFailure.cancelled) { run() }
        assertTrue(connections.isEmpty())
        assertEquals(source.id, activeId)
    }

    @Test
    fun `B-LINUX-235 a sensitive underlying exception becomes a fixed failure code`() = runTest {
        secretFailure = true
        try {
            run()
            fail("Expected a switch failure")
        } catch (error: TermuxMigrationException) {
            assertEquals(TermuxMigrationFailure.profileSwitch, error.code)
            assertFalse(error.toString().contains("provider-token-do-not-expose"))
            assertNull(error.cause)
        }
        assertEquals(source.id, activeId)
    }

    @Test
    fun `B-LINUX-235 a user selection during start wins over the migration`() = runTest {
        changeSelectionDuringStart = true
        expectFailure(TermuxMigrationFailure.profileSwitch) { run() }
        assertTrue(connections.isEmpty())
        assertEquals("other", activeId)
    }

    @Test
    fun `B-LINUX-235 a remote source is rejected before a destination is created`() = runTest {
        saved.clear()
        source = source.copy(baseUrl = "https://example.com")
        saved.add(source)
        expectFailure(TermuxMigrationFailure.invalidSelection) { run() }
        assertEquals(0, starts)
        assertEquals(listOf(source), saved)
    }

    @Test
    fun `B-LINUX-235 the in-app server URL rule matches only plain http on 127_0_0_1 port 4097`() {
        assertTrue(builtinManagesServerUrl("http://127.0.0.1:4097"))
        assertFalse(builtinManagesServerUrl(TERMUX_MANAGED_SERVER_URL))
        assertFalse(builtinManagesServerUrl("https://127.0.0.1:4097"))
        assertFalse(builtinManagesServerUrl("http://localhost:4097"))
        assertFalse(builtinManagesServerUrl(null))
        assertTrue(termuxMigrationEligible(source))
        assertFalse(termuxMigrationEligible(source.copy(openCode = false)))
    }
}
