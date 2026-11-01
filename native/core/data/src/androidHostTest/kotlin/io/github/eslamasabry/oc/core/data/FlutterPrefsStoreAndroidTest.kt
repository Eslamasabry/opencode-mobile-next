package io.github.eslamasabry.oc.core.data

import android.app.Application
import android.content.Context
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.double
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.AeadSecretStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The adopted file through the REAL `SharedPreferencesImpl` (Robolectric): Android parses the committed real-format
 * fixture, the store decodes it, and what native writes lands in the XML in Flutter's encodings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlutterPrefsStoreAndroidTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "native/settings.gradle.kts").isFile }
    private val fixture = File(repoRoot, "native/tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1/shared_prefs/FlutterSharedPreferences.xml")

    private fun prefsFile(name: String) = File(app.dataDir, "shared_prefs/$name.xml")

    private fun install() {
        val target = prefsFile("FlutterSharedPreferences")
        target.parentFile!!.mkdirs()
        fixture.copyTo(target, overwrite = true)
    }

    @Test
    fun `B-MIGR-005 the real Android loader plus the store read every Flutter encoding of the fixture`() = runTest {
        install()
        val store = AndroidDataStores.flutterPrefs(app, StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler))
        assertEquals(1320L, store.long("oc.notify.quietStart"))
        assertEquals(7L, store.long("oc.fixture.int32"))
        assertEquals(1.0E-7, store.double("oc.fixture.double"))
        assertEquals(1295L, store.long("oc.fixture.bigint"))
        assertEquals(listOf("legacy-set-entry"), store.stringList("oc.agentTurnFailures.1735000000000002"))
        assertIs<PrefValue.StrList>(store.get("oc.chatNoticesSeen.1735000000000002"), "legacy Java list via ObjectInputStream")
        assertIs<PrefValue.StrList>(store.get("oc.withdrawnPrompts.1735000000000002"), "Arrays\$ArrayList form")
        assertEquals("<b>&\"quoted\"</b>\tTab\nLine\u0001\u001b[0m مرحبا 🚀", store.string("oc.draft.note.ses_edge.1735000000000001"))
        assertTrue(store.keys().none { it.startsWith("unprefixed") })
    }

    @Test
    fun `B-MIGR-007 native writes and the sweep reach the XML in Flutter's encodings`() = runTest {
        install()
        val store = AndroidDataStores.flutterPrefs(app, StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        assertEquals(
            Outcome.Ok(Unit),
            store.commit(lanes.app) {
                putLong("oc.notify.quietStart", 60)
                putStringList("oc.native.listProbe", listOf("a", "b"))
                putDouble("oc.native.doubleProbe", 0.5)
            },
        )
        val scope = DefaultProfileScope(store, MapSecretStore(), lanes.app)
        lanes.closeProfile(ProfileId("1735000000000001"))
        assertIs<Outcome.Ok<*>>(scope.sweep(ProfileId("1735000000000001")))

        val xml = prefsFile("FlutterSharedPreferences").readText()
        assertTrue("<long name=\"flutter.oc.notify.quietStart\" value=\"60\" />" in xml, "Dart ints stay <long>")
        assertTrue("name=\"flutter.oc.native.listProbe\">VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu![&quot;a&quot;,&quot;b&quot;]<" in xml)
        assertTrue("name=\"flutter.oc.native.doubleProbe\">VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu0.5<" in xml)
        assertTrue("1735000000000001\"" !in xml.replace("flutter.oc.profiles", ""), "no key of the swept profile left")
        assertTrue("flutter.oc.model.1735000000000002" in xml && "unprefixed.kotlin.key" in xml)

        // A new process (fresh SharedPreferences instance is not possible in one process): re-read the file as Android would.
        val reread = app.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all
        assertEquals(60L, reread["flutter.oc.notify.quietStart"])
    }

    @Test
    fun `B-MIGR-006 secrets persist in oc_secrets_v1 as v1 entries, software AEAD, Keystore on the emulator only`() =
        runTest {
            val prefs = app.getSharedPreferences(AeadSecretStore.FILE_NAME, Context.MODE_PRIVATE)
            val entries = io.github.eslamasabry.oc.core.data.android.SharedPreferencesSecretEntriesForTest(prefs)
            val store = AeadSecretStore(entries, FakeAead(), io.github.eslamasabry.oc.core.security.Redactor(), StandardTestDispatcher(testScheduler))
            assertEquals(Outcome.Ok(Unit), store.write("pw.1735000000000001", "fixture-pass-1"))
            assertTrue(prefsFile(AeadSecretStore.FILE_NAME).readText().contains("name=\"pw.1735000000000001\""))
            assertTrue(!prefsFile(AeadSecretStore.FILE_NAME).readText().contains("fixture-pass-1"))
            assertEquals(Outcome.Ok("fixture-pass-1"), store.read("pw.1735000000000001"))
        }
}
