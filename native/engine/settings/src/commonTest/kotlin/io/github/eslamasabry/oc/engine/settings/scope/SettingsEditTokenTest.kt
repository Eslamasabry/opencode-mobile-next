package io.github.eslamasabry.oc.engine.settings.scope

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.prefs.PreferenceChange
import io.github.eslamasabry.oc.engine.settings.prefs.ThemePack
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingsEditTokenTest {
    private fun effects(motion: String, colours: String = "one") = SettingValue.Record(
        persistentMapOf(
            "motion" to text(motion),
            "activityGlow" to flag(true),
            "glowStyle" to text("classic"),
            "glowColours" to text(colours),
            "glowSpeed" to text("normal"),
        ),
    )

    @Test
    fun `B-STATE-128 captured edits write only their owner and return the committed state`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val phoneEdit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        access.read("shareReadState", LAPTOP_TARGET)
        val confirmed = access.write(phoneEdit, flag(true)).ok()
        assertEquals(PHONE_TARGET, confirmed.target)
        assertEquals(flag(true), confirmed.value)
        assertEquals(SettingOriginKind.ExplicitValue, confirmed.origin)
        assertEquals(flag(false), access.read("shareReadState", LAPTOP_TARGET).value)
    }

    @Test
    fun `B-STATE-128 forged, foreign and replayed tokens cannot write`() = runTest {
        val f = ScopeFixture(this)
        val first = f.access()
        val second = f.access()
        val edit = first.beginEdit("shareReadState", PHONE_TARGET).ok()
        val forged = SettingsEditToken(edit.before, edit.ownerFingerprint, first)
        assertReason(SettingsFailureReason.StaleTarget, first.write(forged, flag(true)))
        assertReason(SettingsFailureReason.StaleTarget, second.write(edit, flag(true)))
        assertIs<Outcome.Ok<*>>(first.write(edit, flag(true)))
        assertReason(SettingsFailureReason.StaleTarget, first.write(edit, flag(false)))
    }

    @Test
    fun `B-STATE-128 new observations and competing saves conflict, and a deleted owner stays stale`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val old = access.beginEdit("appearance", DEVICE).ok()
        f.preferences.set(PreferenceChange.Theme(ThemePack.Nord)) // a newer observation of the device settings
        assertReason(SettingsFailureReason.Conflict, access.write(old, text("light")))
        val one = access.beginEdit("appearance", DEVICE).ok()
        val two = access.beginEdit("appearance", DEVICE).ok()
        assertIs<Outcome.Ok<*>>(access.write(one, text("light")))
        assertReason(SettingsFailureReason.Conflict, access.write(two, text("system")))

        val deleted = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        f.owners.remove(PHONE)
        assertReason(SettingsFailureReason.StaleTarget, access.write(deleted, flag(false)))
        f.owners.replace(PHONE) // saved again: a new identity, the old token stays stale
        assertReason(SettingsFailureReason.StaleTarget, access.write(deleted, flag(true)))
    }

    @Test
    fun `B-STATE-128 watches replay the latest value per subscriber, then changes and stale states`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val stream = access.watch("shareReadState", PHONE_TARGET)
        val edit = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        stream.test {
            assertEquals(flag(false), awaitItem().value)
            access.write(edit, flag(true)).ok()
            var item = awaitItem()
            while (item.value != flag(true)) item = awaitItem() // saving shows false first: opt-in is never early
            cancelAndIgnoreRemainingEvents()
        }
        stream.test {
            assertEquals(flag(true), awaitItem().value)
            f.owners.remove(PHONE)
            assertEquals(SettingValueState.Stale, awaitItem().state)
            access.close()
            awaitComplete()
        }
        assertEquals(SettingsFailureReason.Disposed, access.read("shareReadState", PHONE_TARGET).reason)
        assertReason(SettingsFailureReason.Disposed, access.beginEdit("appearance", DEVICE))
        assertReason(SettingsFailureReason.Disposed, access.write(edit, flag(false)))
    }

    @Test
    fun `B-STATE-128 malformed values never publish a success`() = runTest {
        val f = ScopeFixture(this)
        val access = f.access()
        val edit = access.beginEdit("effects", DEVICE).ok()
        val revision = f.preferences.revision.value
        assertReason(SettingsFailureReason.InvalidValue, access.write(edit, SettingValue.Record(persistentMapOf("motion" to text("off")))))
        assertReason(SettingsFailureReason.InvalidValue, access.write(edit, effects("sideways")))
        // The reply glow's colours and speed are the app's own: another value could never be confirmed.
        assertReason(SettingsFailureReason.InvalidValue, access.write(edit, effects("off", colours = "two")))
        assertEquals(revision, f.preferences.revision.value)
        assertEquals(effects("off"), access.write(edit, effects("off")).ok().value)

        val locale = access.beginEdit("locale", DEVICE).ok()
        assertReason(SettingsFailureReason.InvalidValue, access.write(locale, text("not-a-language")))
        assertReason(SettingsFailureReason.InvalidValue, access.write(locale, flag(true)))
        assertNull(access.write(locale, null).ok().value)
        val appearance = access.beginEdit("appearance", DEVICE).ok()
        assertReason(SettingsFailureReason.InvalidValue, access.write(appearance, null))
        val consent = access.beginEdit("shareReadState", PHONE_TARGET).ok()
        assertReason(SettingsFailureReason.InvalidValue, access.write(consent, text("yes")))
    }

    @Test
    fun `B-STATE-128 every failure reason maps to one Failure and back`() {
        for (reason in SettingsFailureReason.entries) assertEquals(reason, reason.asFailure().settingsReason(), reason.name)
    }
}
