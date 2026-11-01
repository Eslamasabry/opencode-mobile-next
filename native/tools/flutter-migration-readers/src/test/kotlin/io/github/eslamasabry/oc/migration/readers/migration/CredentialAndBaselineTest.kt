package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.migration.CredentialReconciler.NativeCredentialState
import io.github.eslamasabry.oc.migration.readers.migration.CredentialReconciler.ReturnAction
import io.github.eslamasabry.oc.migration.readers.migration.CredentialReconciler.TransitionBaseline
import io.github.eslamasabry.oc.migration.readers.model.FlutterKey
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CredentialAndBaselineTest {

    @Test
    fun `stale legacy entries - native edit, native sign-out, untouched`() {
        val native = NativeCredentialState(
            revisions = mapOf("pw.1" to 3, "pw.2" to 1, "pw.4" to 1),
            tombstones = mapOf("pw.3" to 2),
            fssSyncedAt = mapOf("pw.1" to 1, "pw.2" to 1, "pw.3" to 1),
        )
        // pw.1 changed in native after the FSS copy; pw.3 signed out in native; pw.2 unchanged;
        // pw.4 was added in native and never existed in FSS (nothing to delete there).
        assertEquals(setOf("pw.1", "pw.3"), CredentialReconciler.staleLegacyEntries(native, setOf("pw.1", "pw.2", "pw.3")))
    }

    @Test
    fun `return reconciliation sees changes, additions and deletions made in Flutter`() {
        val baseline = TransitionBaseline(1, mapOf("pw.1" to CredentialReconciler.fingerprint("ct-a"), "pw.2" to CredentialReconciler.fingerprint("ct-b")))
        val actions = CredentialReconciler.onReturn(baseline, mapOf("pw.1" to "ct-a", "pw.3" to "ct-new"))
        assertEquals(
            listOf(ReturnAction.KeepNative("pw.1"), ReturnAction.DeleteNative("pw.2"), ReturnAction.OverwriteNative("pw.3")),
            actions,
        )
        // Same name, new ciphertext → changed in Flutter.
        assertEquals(listOf(ReturnAction.OverwriteNative("pw.1"), ReturnAction.DeleteNative("pw.2")),
            CredentialReconciler.onReturn(baseline, mapOf("pw.1" to "ct-a2")))
    }

    @Test
    fun `two rollback cycles compare against their own baseline, not the first one`() {
        val s0 = mapOf("pw.1" to "ct-0")
        val cycle1 = TransitionBaseline(1, s0.mapValues { CredentialReconciler.fingerprint(it.value) })
        val afterCycle1 = mapOf("pw.1" to "ct-1") // changed during the first rollback
        assertEquals(listOf(ReturnAction.OverwriteNative("pw.1")), CredentialReconciler.onReturn(cycle1, afterCycle1))
        val cycle2 = TransitionBaseline(2, afterCycle1.mapValues { CredentialReconciler.fingerprint(it.value) })
        // Nothing changed during the second rollback: comparing with S0 would wrongly overwrite native again.
        assertEquals(listOf(ReturnAction.KeepNative("pw.1")), CredentialReconciler.onReturn(cycle2, afterCycle1))
    }

    @Test
    fun `baseline proves nothing outside the declared write sets changed`() {
        val before = FixtureGenerator.prefsFixture()
        val baseline = PrefsBaseline.of(before)
        val declared = PrefsBaseline.V1_DECLARED_WRITES

        val after = LinkedHashMap(before)
        after.remove("flutter.oc.budgets.${FixtureGenerator.PROFILE_A}") // L1
        after["flutter.oc.notify.version"] = RawPref.Int64(1) // L2
        after["flutter.oc.native.flutterReadable"] = RawPref.Bool(true) // barrier
        after["flutter.oc.secretReentry.${FixtureGenerator.PROFILE_A}"] = RawPref.Str("""{"v":1,"names":["pw"],"reason":"UnwrapFailed","at":1}""")
        assertEquals(0, baseline.undeclaredChanges(after, declared))
        assertEquals(baseline.unchangedKeysDigest(declared), PrefsBaseline.liveDigest(after, declared))

        // A change to the queue (dispatch marker!) is caught.
        val tampered = LinkedHashMap(after)
        tampered["flutter.oc.offlineQueue"] = RawPref.Str("[]")
        assertEquals(1, baseline.undeclaredChanges(tampered, declared))
        assertNotEquals(baseline.unchangedKeysDigest(declared), PrefsBaseline.liveDigest(tampered, declared))
        // So is a type change with the same text, and an unknown key disappearing.
        val retyped = LinkedHashMap(after).apply { put("flutter.oc.fixture.int32", RawPref.Int64(7)) }
        assertEquals(1, baseline.undeclaredChanges(retyped, declared))
        val lost = LinkedHashMap(after).apply { remove("flutter.oc.futureFeature.v9") }
        assertEquals(1, baseline.undeclaredChanges(lost, declared))
    }

    @Test
    fun `baseline file round trip holds hashes and shapes only`() {
        val dir = Files.createTempDirectory("bl").toFile()
        val f = File(dir, "m1-baseline.json")
        val prefs = FixtureGenerator.prefsFixture()
        PrefsBaseline.of(prefs).save(f)
        val text = f.readText()
        assertFalse(text.contains("queued while offline"))
        assertFalse(text.contains("oc.offlineQueue"))
        assertEquals(PrefsBaseline.of(prefs).entries, PrefsBaseline.load(f)!!.entries)
        assertEquals(0, PrefsBaseline.load(f)!!.undeclaredChanges(prefs, setOf(FlutterKey.Budgets)))
        dir.deleteRecursively()
    }
}
