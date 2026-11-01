package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileScopeTest {
    private val doomed = "1735000000000001"
    private val keeper = "1735000000000002"

    private val keys = listOf(
        "oc.model.$doomed", "oc.model.$keeper",
        "oc.model.$doomed.agents", "oc.sessionModels.$doomed.agents",
        "oc.draft.note.ses_x.$doomed", "oc.draft.note.ses_$doomed.$keeper", "oc.draft.reportProblem.app",
        "oc.genui.covered.$doomed", "oc.genui.enabled.$keeper",
        "oc.promptStash.$doomed.item1", "oc.promptStash.$keeper.item1",
        "oc.orchestration.$doomed.provider",
        "oc.providerRuntimeRefresh.v1.$doomed.%3Cdefault%3E", "oc.providerRuntimeUnloadable.$doomed.x",
        "oc.phraseChips.$doomed", "oc.phraseChips.enabled",
        "oc.notify.quietStart", "oc.transcript.$doomed", "oc.a2aTasks.$doomed",
        "oc.profiles", "oc.activeProfile", "oc.sessionPins.$keeper",
        "voice.language",
    )

    @Test
    fun `B-STATE-060 the sweep takes every owner position of the doomed id and nothing else`() {
        val swept = ProfileKeyRule.scopedKeys(doomed, keys, setOf(keeper))
        assertEquals(
            setOf(
                "oc.model.$doomed", "oc.model.$doomed.agents", "oc.sessionModels.$doomed.agents",
                "oc.draft.note.ses_x.$doomed", "oc.genui.covered.$doomed", "oc.promptStash.$doomed.item1",
                "oc.orchestration.$doomed.provider", "oc.providerRuntimeRefresh.v1.$doomed.%3Cdefault%3E",
                "oc.providerRuntimeUnloadable.$doomed.x", "oc.phraseChips.$doomed",
            ),
            swept,
        )
    }

    @Test
    fun `B-STATE-060 item text that equals the id is never taken, and longer dotted ids keep their keys`() {
        // A draft target named like the id belongs to its owner (last segment), not to the id inside it.
        assertEquals(emptySet(), ProfileKeyRule.scopedKeys(doomed, listOf("oc.draft.note.ses_$doomed.$keeper"), setOf(keeper)))
        // Historical ids could contain dots: "a.b" owns oc.promptStash.a.b.x, so sweeping "a" leaves it.
        val legacy = listOf("oc.promptStash.a.b.x", "oc.promptStash.a.x", "oc.draft.t.a.b", "oc.draft.t.a")
        assertEquals(setOf("oc.promptStash.a.x", "oc.draft.t.a"), ProfileKeyRule.scopedKeys("a", legacy, setOf("a.b")))
        // A saved profile literally named "<id>.agents" is its own owner.
        assertEquals(setOf("oc.model.a"), ProfileKeyRule.scopedKeys("a", listOf("oc.model.a", "oc.model.a.agents"), setOf("a.agents")))
        assertEquals(emptySet(), ProfileKeyRule.scopedKeys("", keys, emptySet()))
    }

    @Test
    fun `B-STATE-060 sweeping one profile leaves the other whole and is idempotent`() = runTest {
        val backend = FakeBackend(
            keys.associate { "flutter.$it" to RawPref.Str("x") } +
                ("flutter.oc.profiles" to RawPref.Str("[{\"id\":\"$doomed\"},{\"id\":\"$keeper\"}]")),
        )
        val store = storeOver(backend, StandardTestDispatcher(testScheduler))
        val scope = DefaultProfileScope(store, MapSecretStore(), WriterLane("app"))
        scope.register(KeyShape("oc.model.", KeyScope.Profile, PrefType.Str, ":engine:connection", "selected model"))

        val receipt = (scope.sweep(ProfileId(doomed), retained = setOf("oc.phraseChips.$doomed")) as Outcome.Ok).value
        assertEquals(9, receipt.removedKeys)
        assertEquals(1, receipt.retainedKeys)
        assertEquals(2, receipt.removedByOwner[":engine:connection"])
        assertEquals(7, receipt.removedByOwner[DefaultProfileScope.UNREGISTERED])

        val left = store.keys()
        assertTrue(left.none { it != "oc.phraseChips.$doomed" && it in ProfileKeyRule.scopedKeys(doomed, keys, setOf(keeper)) })
        assertTrue("oc.model.$keeper" in left && "oc.promptStash.$keeper.item1" in left && "oc.profiles" in left)
        assertEquals(1, backend.commits.size, "one atomic commit")

        val again = (scope.sweep(ProfileId(doomed), retained = setOf("oc.phraseChips.$doomed")) as Outcome.Ok).value
        assertEquals(0, again.removedKeys)
        assertEquals(1, backend.commits.size)
    }

    @Test
    fun `B-SEC-039 owned data lists the profile's secrets by registered prefix and nothing of others`() = runTest {
        val store = storeOver(FakeBackend(), StandardTestDispatcher(testScheduler))
        val secrets = MapSecretStore(
            mapOf("pw.$doomed" to "a", "pw.$keeper" to "b", "oc.codexToken.$doomed" to "c", "pw.${doomed}9" to "d"),
        )
        val scope = DefaultProfileScope(store, secrets, WriterLane("app"))
        scope.registerSecret("pw.", ":engine:profiles")
        scope.registerSecret("oc.codexToken.", ":protocol:codex")
        scope.registerTable("queued_prompt")
        val owned = (scope.ownedData(ProfileId(doomed)) as Outcome.Ok).value
        assertEquals(setOf("pw.$doomed", "oc.codexToken.$doomed"), owned.secretNames)
        assertEquals(listOf("queued_prompt.profile_id"), owned.tables)
    }

    @Test
    fun `reserved segments come from app-wide shapes so generated ids never collide`() {
        val scope = DefaultProfileScope(storeOver(FakeBackend(), StandardTestDispatcher()), MapSecretStore(), WriterLane("app"))
        scope.register(KeyShape("oc.notify.quietStart", KeyScope.App, PrefType.Int64, ":engine:notify", "quiet hours"))
        scope.register(KeyShape("oc.native.flutterReadable", KeyScope.App, PrefType.Bool, ":android:migration", "R1 guard"))
        scope.register(KeyShape("oc.model.", KeyScope.Profile, PrefType.Str, ":engine:connection", "model"))
        val reserved = scope.reservedSegments()
        assertTrue(reserved.containsAll(setOf("notify", "quietStart", "native", "flutterReadable", "app", "agents")))
        assertTrue("model" !in reserved)
    }
}
