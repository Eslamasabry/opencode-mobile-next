package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.Fixtures.ORPHAN
import io.github.eslamasabry.oc.core.data.Fixtures.PROFILE_A
import io.github.eslamasabry.oc.core.data.Fixtures.PROFILE_B
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.double
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The store adopted over the real-format Flutter file (plan 05 §2.2). */
class AdoptedFixtureTest {
    private val raw = Fixtures.prefsXml()

    @Test
    fun `B-MIGR-005 every Flutter encoding in the adopted file reads as Dart saw it`() = runTest {
        val store = storeOver(FakeBackend(raw), StandardTestDispatcher(testScheduler), codec = jvmCodec)
        assertEquals(42L, store.long("oc.notify.checkInAfterMinutes"))
        assertEquals(1320L, store.long("oc.notify.quietStart"))
        assertEquals(true, store.bool("oc.notify.requests"))
        assertEquals(7L, store.long("oc.fixture.int32"), "a legacy <int> reads as a Dart int")
        assertEquals(1.5, store.double("oc.fixture.float"))
        assertEquals(1.0E-7, store.double("oc.fixture.double"), "the double prefix")
        assertEquals(1295L, store.long("oc.fixture.bigint"), "the legacy BigInteger prefix (base 36)")
        assertNull(store.get("oc.fixture.null"), "<null> is invisible, as to Dart")
        assertEquals(listOf("legacy-set-entry"), store.stringList("oc.agentTurnFailures.$PROFILE_B"), "<set>")
        // Both list encodings: the old Java-serialised form and the JSON form.
        assertIs<PrefValue.StrList>(store.get("oc.chatNoticesSeen.$PROFILE_B"))
        assertIs<PrefValue.StrList>(store.get("oc.withdrawnPrompts.$PROFILE_B"))
        val jsonLists = raw.filterValues { it is RawPref.Str && it.value.startsWith(FlutterPrefsCodec.JSON_LIST_PREFIX) }
        assertTrue(jsonLists.isNotEmpty())
        for (k in jsonLists.keys) assertIs<PrefValue.StrList>(store.get(FlutterPrefsCodec.logicalKey(k)!!), k)
        // Control characters, markup and non-ASCII survive the XML layer byte for byte.
        assertEquals(
            "<b>&\"quoted\"</b>\tTab\nLine\u0001\u001b[0m مرحبا 🚀",
            store.string("oc.draft.note.ses_edge.$PROFILE_A"),
        )
        // The flutter. prefix is the only door: voice.* and other plugin keys are visible; unprefixed keys are not.
        assertTrue("voice.language" in store.keys() && "someOtherPlugin.flag" in store.keys())
        assertTrue(store.keys().none { it.startsWith("unprefixed") || it.startsWith("flutter.") })
        assertTrue(store.keys("oc.").all { store.get(it) !is PrefValue.Undecodable }, "the fixture has no unreadable value")
    }

    @Test
    fun `B-MIGR-005 a native write changes only the keys it names and leaves every other raw value as it was`() = runTest {
        val backend = FakeBackend(raw)
        val store = storeOver(backend, StandardTestDispatcher(testScheduler), codec = jvmCodec)
        val lanes = DefaultWriterLanes()
        assertEquals(Outcome.Ok(Unit), store.commit(lanes.app) { putLong("oc.notify.quietStart", 1380) })
        val after = backend.persisted()
        assertEquals(RawPref.Int64(1380), after["flutter.oc.notify.quietStart"])
        assertEquals(raw - "flutter.oc.notify.quietStart", after - "flutter.oc.notify.quietStart")
    }

    @Test
    fun `B-MIGR-007 per-profile data stays scoped and the sweep removes all of it and only it`() = runTest {
        val backend = FakeBackend(raw)
        val store = storeOver(backend, StandardTestDispatcher(testScheduler), codec = jvmCodec)
        val lanes = DefaultWriterLanes()
        val scope = DefaultProfileScope(store, MapSecretStore(), lanes.app)
        val before = store.keys()
        val ownedByA = scope.scopedKeys(ProfileId(PROFILE_A))
        val ownedByB = scope.scopedKeys(ProfileId(PROFILE_B))
        assertTrue(ownedByA.isNotEmpty() && ownedByB.isNotEmpty())
        assertTrue((ownedByA intersect ownedByB).isEmpty())
        assertTrue("oc.model.$PROFILE_A.agents" in ownedByA, "the phone's agent backend goes with the phone")
        assertTrue("oc.providerRuntimeRefresh.v1.$PROFILE_A.%3Cdefault%3E%0A%3Cdefault%3E" in ownedByA)
        assertTrue("oc.draft.note.ses_edge.$PROFILE_A" in ownedByA && "oc.promptStash.$PROFILE_A.item1" in ownedByA)
        assertTrue(ownedByA.none { it.endsWith(".$ORPHAN") })

        lanes.closeProfile(ProfileId(PROFILE_A))
        lanes.drainProfile(ProfileId(PROFILE_A))
        val receipt = (scope.sweep(ProfileId(PROFILE_A)) as Outcome.Ok).value
        assertEquals(ownedByA.size, receipt.removedKeys)

        val after = store.keys()
        assertEquals(before - ownedByA, after)
        assertTrue(after.none { it.endsWith(".$PROFILE_A") || it.contains(".$PROFILE_A.") && it in ownedByA })
        assertEquals(ownedByB, scope.scopedKeys(ProfileId(PROFILE_B)), "the other server is whole")
        assertTrue("oc.model.$ORPHAN" in after && "oc.draft.reportProblem.app" in after && "oc.profiles" in after)

        // Nothing returns after a restart: a fresh store over what reached the disk.
        val restarted = storeOver(FakeBackend(backend.persisted()), StandardTestDispatcher(testScheduler), codec = jvmCodec)
        assertEquals(after, restarted.keys())
    }
}
