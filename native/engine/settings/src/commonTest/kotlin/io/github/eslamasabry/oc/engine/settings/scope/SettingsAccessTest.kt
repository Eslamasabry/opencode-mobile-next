package io.github.eslamasabry.oc.engine.settings.scope

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.AppearanceChoice
import io.github.eslamasabry.oc.engine.settings.prefs.GatedStore
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.seed
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.string
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsAccessTest {
    @Test
    fun `B-STATE-123 device preferences update consistently across access points and keep every effects field`() = runTest {
        val f = ScopeFixture(this)
        val one = f.access()
        // A second preferences instance over the same store (another graph) behind a second facade.
        val two = f.access(Preferences(f.store, WriterLane("app"), backgroundScope, f.consent))
        two.watch("appearance", DEVICE).test {
            assertEquals(text("dark"), awaitItem().value)
            val edit = one.beginEdit("appearance", DEVICE).ok()
            val receipt = one.write(edit, text("light")).ok()
            assertEquals(text("light"), receipt.value)
            assertEquals(text("light"), awaitItem().value)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("light", f.store.string("oc.appearance"))
        val effects = one.beginEdit("effects", DEVICE).ok()
        val before = assertIs<SettingValue.Record>(effects.before.value).fields
        val requested = SettingValue.Record(before.toPersistentMap().putting("glowStyle", text("softRing")))
        one.write(effects, requested).ok()
        runCurrent()
        val saved = two.read("effects", DEVICE)
        assertEquals(requested, saved.value)
        assertEquals(SettingOriginKind.ExplicitValue, saved.origin)
        assertEquals(setOf("motion", "activityGlow", "glowStyle", "glowColours", "glowSpeed"), assertIs<SettingValue.Record>(saved.value).fields.keys)
    }

    @Test
    fun `B-STATE-123 local model snapshots are read-only and malformed stored values read as unknown`() = runTest {
        val f = ScopeFixture(this)
        f.store.seed("oc.model.phone" to "provider|model", "oc.model.laptop" to "malformed", "oc.agent.phone" to "build")
        val access = f.access()
        val model = access.read("model", PHONE_TARGET)
        assertEquals(SettingValue.Record(persistentMapOf("providerId" to text("provider"), "modelId" to text("model"))), model.value)
        assertEquals(SettingAuthority.LocalPreference, model.authority)
        assertEquals(SettingOriginKind.ExplicitValue, model.origin)
        assertFalse(model.writable)
        assertEquals(text("build"), access.read("agent", PHONE_TARGET).value)
        assertEquals(text(""), access.read("variant", PHONE_TARGET).value)
        assertEquals(SettingValueState.Unknown, access.read("model", LAPTOP_TARGET).state)
        assertEquals(SettingsFailureReason.InvalidValue, access.read("model", LAPTOP_TARGET).reason)
        assertEquals(SettingValueState.Unsupported, access.read("shell", PHONE_TARGET).state)
        assertEquals(SettingValueState.Unsupported, access.read("model", SettingsTarget.Project(PHONE, "p")).state)
        assertReason(SettingsFailureReason.Unsupported, access.beginEdit("model", PHONE_TARGET))
    }

    @Test
    fun `B-STATE-124 a consent save stays on the captured connection`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        // The token names its target; there is no "active connection" that could retarget it.
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        val other = access.beginEdit("shareReadState", LAPTOP_TARGET).ok()
        val receipt = access.write(edit, flag(true)).ok()
        assertEquals(PHONE_TARGET, receipt.target)
        assertEquals(flag(true), receipt.value)
        assertEquals(true, f.consent.saved[PHONE])
        assertNull(f.consent.saved[LAPTOP])
        assertEquals(flag(false), access.read("shareReadState", LAPTOP_TARGET).value)
        // The phone save is a newer observation of the connection settings: the page re-reads before writing again.
        assertReason(SettingsFailureReason.Conflict, access.write(other, flag(true)))
        assertIs<Outcome.Ok<*>>(access.write(access.beginEdit("shareReadState", LAPTOP_TARGET).ok(), flag(true)))
        assertEquals(true, f.consent.saved[PHONE])
    }

    @Test
    fun `B-STATE-124 a late transport change cannot grant consent to the replacement`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        val entered = CompletableDeferred<Unit>().also { f.consent.entered = it }
        val release = CompletableDeferred<Unit>().also { f.consent.gate = it }
        val pending = async { access.write(edit, flag(true)) }
        entered.await()
        f.owners.replace(PHONE) // the editor changed the address while the older write awaited I/O
        release.complete(Unit)
        assertReason(SettingsFailureReason.StaleTarget, pending.await())
        assertFalse(f.consent.enabled(PHONE))
        assertNotEquals(true, f.consent.saved[PHONE])
    }

    @Test
    fun `B-STATE-124 a refused save is a failure and opting out is private before acknowledgment`() = runTest {
        val f = ScopeFixture(this)
        f.consent.seed(PHONE, true)
        val access = f.access()
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        assertEquals(flag(true), edit.before.value)
        val entered = CompletableDeferred<Unit>().also { f.consent.entered = it }
        val release = CompletableDeferred<Unit>().also { f.consent.gate = it }
        f.consent.refuse = true
        val result = async { access.write(edit, flag(false)) }
        entered.await()
        assertFalse(f.consent.enabled(PHONE))
        assertEquals(flag(false), access.read("shareReadState", PHONE_TARGET).value)
        release.complete(Unit)
        assertReason(SettingsFailureReason.StorageFailed, result.await())
        assertFalse(f.consent.enabled(PHONE))
    }

    @Test
    fun `B-STATE-125 competing writes on two facades conflict and fabricated tokens fail`() = runTest {
        val f = ScopeFixture(this)
        val one = f.access()
        val two = f.access()
        val first = one.beginEdit("appearance", DEVICE).ok()
        val second = two.beginEdit("appearance", DEVICE).ok()
        assertReason(SettingsFailureReason.StaleTarget, two.write(first, text("dark")))
        val winner = async { one.write(first, text("light")) }
        val loser = async { two.write(second, text("system")) }
        assertReason(SettingsFailureReason.Conflict, loser.await())
        assertIs<Outcome.Ok<*>>(winner.await())
        assertEquals(AppearanceChoice.Light, f.preferences.value.value.appearance)
        val edit = one.beginEdit("appearance", DEVICE).ok()
        val forged = SettingsEditToken(edit.before, edit.ownerFingerprint, one)
        assertReason(SettingsFailureReason.StaleTarget, one.write(forged, text("dark")))
    }

    @Test
    fun `B-STATE-125 transport replacement invalidates tokens and the existing watch context`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        access.watch("shareReadState", PHONE_TARGET).test {
            assertEquals(SettingValueState.Known, awaitItem().state)
            f.owners.replace(PHONE)
            assertEquals(SettingValueState.Stale, awaitItem().state)
            cancelAndIgnoreRemainingEvents()
        }
        assertReason(SettingsFailureReason.StaleTarget, access.write(edit, flag(true)))
        assertEquals(SettingValueState.Known, access.read("shareReadState", PHONE_TARGET).state)
    }

    @Test
    fun `B-STATE-125 closed owners reject writes and closing the facade ends its streams but keeps the shared owner`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        access.watch("shareReadState", PHONE_TARGET).test {
            assertEquals(SettingValueState.Known, awaitItem().state)
            f.consent.closeProfile(PHONE)
            assertEquals(SettingValueState.Stale, awaitItem().state)
            cancelAndIgnoreRemainingEvents()
        }
        assertReason(SettingsFailureReason.StaleTarget, access.write(edit, flag(true)))
        access.watch("appearance", DEVICE).test {
            awaitItem()
            access.close()
            awaitComplete()
        }
        assertEquals(SettingsFailureReason.Disposed, access.read("appearance", DEVICE).reason)
        access.watch("appearance", DEVICE).test {
            assertEquals(SettingsFailureReason.Disposed, awaitItem().reason)
            awaitComplete()
        }
        assertIs<Outcome.Ok<*>>(f.preferences.set(PreferenceChange.Appearance(AppearanceChoice.Light)))
        assertEquals(AppearanceChoice.Light, f.preferences.value.value.appearance)
    }

    @Test
    fun `B-STATE-125 selection reads and streams publish only acknowledged writes`() = runTest {
        val gated = GatedStore(storeOver(TestDisk(), StandardTestDispatcher(testScheduler)))
        gated.seed("oc.model.phone" to "provider|old")
        val f = ScopeFixture(this, gated)
        val access = f.access()
        val old = SettingValue.Record(persistentMapOf("providerId" to text("provider"), "modelId" to text("old")))
        val new = SettingValue.Record(persistentMapOf("providerId" to text("provider"), "modelId" to text("new")))
        access.watch("model", PHONE_TARGET).test {
            assertEquals(old, awaitItem().value)
            val release = CompletableDeferred<Unit>().also { gated.gate = it }
            // The connection engine's selection writer, held before its write is acknowledged.
            val pending = async { gated.commit(WriterLane("profile:phone")) { putString("oc.model.phone", "provider|new") } }
            runCurrent()
            assertEquals(old, f.access().read("model", PHONE_TARGET).value)
            release.complete(Unit)
            assertIs<Outcome.Ok<*>>(pending.await())
            assertEquals(new, awaitItem().value)
            cancelAndIgnoreRemainingEvents()
        }
        access.watch("agent", PHONE_TARGET).test {
            awaitItem()
            gated.commit(WriterLane("profile:phone")) { putString("oc.agent.phone", "review") }
            assertEquals(text("review"), awaitItem().value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-125 a replaced or closed connection is seen by every facade`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val old = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        f.owners.replace(PHONE)
        assertReason(SettingsFailureReason.StaleTarget, access.write(old, flag(true)))
        val fresh = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        f.consent.closeProfile(PHONE)
        assertReason(SettingsFailureReason.StaleTarget, access.write(fresh, flag(true)))
        assertEquals(SettingValueState.Stale, access.read("shareReadState", PHONE_TARGET).state)
        assertReason(SettingsFailureReason.StaleTarget, access.beginEdit("shareReadState", PHONE_TARGET))
    }
}
