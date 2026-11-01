package io.github.eslamasabry.oc.android.migration.android

import android.app.Application
import io.github.eslamasabry.oc.android.migration.BlockReason
import io.github.eslamasabry.oc.android.migration.MemorySecrets
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.MigrationRunner
import io.github.eslamasabry.oc.android.migration.RecordingSignals
import io.github.eslamasabry.oc.android.migration.RunnerState
import io.github.eslamasabry.oc.android.migration.StepInterrupter
import io.github.eslamasabry.oc.android.migration.World
import io.github.eslamasabry.oc.android.migration.readers.secure.SoftwareRsaOaepUnwrapper
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Plan 05 §10.2 `MigrationRunnerTest` (Robolectric twin) and `CorruptPrefsXmlTest`: the fixture XMLs are copied into
 * `shared_prefs/` and read by Android's own `SharedPreferencesImpl` through the production adapters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPrefsMigrationTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val dataDir: File = requireNotNull(app.filesDir.parentFile)
    private val sharedPrefs = File(dataDir, "shared_prefs").apply { mkdirs() }
    private val secrets = MemorySecrets()
    private val signals = RecordingSignals()

    private fun TestScope.runner(): MigrationRunner {
        val fixture = World.readersFixture()
        val pk8 = Base64.getMimeDecoder().decode(File(fixture, "fixture-rsa-private.pk8.b64").readText())
        return MigrationRunner(
            paths = MigrationPaths(dataDir, app.getExternalFilesDir(null), app.externalCacheDir),
            prefs = SharedPreferencesFlutterPrefs(app),
            kotlinPrefs = AndroidKotlinPrefs(app),
            unwrapper = SoftwareRsaOaepUnwrapper(World.fixturePrivateKey(pk8)),
            sink = secrets, redactor = Redactor(), signals = signals, interrupter = StepInterrupter.None,
            freeBytes = { 1L shl 40 }, packageName = app.packageName, nativeVersionCode = 10_000, now = { 1_730_457_600_000L },
            io = StandardTestDispatcher(testScheduler),
        )
    }

    private fun copyFixture() {
        File(World.readersFixture(), "shared_prefs").listFiles()!!.forEach { it.copyTo(File(sharedPrefs, it.name), overwrite = true) }
    }

    @Test
    fun `B-MIGR-005 B-MIGR-006 the P5 fixture migrates through SharedPreferencesImpl and a re-run changes no byte`() = runTest {
        copyFixture()
        val open = assertIs<RunnerState.Open>(runner().run())
        assertTrue(open.firstNativeLaunch)
        assertEquals(World.fixtureSecrets().toSortedMap(), secrets.values.toSortedMap())
        val prefsFile = File(sharedPrefs, "FlutterSharedPreferences.xml")
        val before = prefsFile.readBytes().toList()
        val prefs = app.getSharedPreferences("FlutterSharedPreferences", 0)
        assertEquals(true, prefs.getBoolean("flutter.oc.native.flutterReadable", false))
        assertEquals(1L, prefs.getLong("flutter.oc.notify.version", 0)) // the fixture's version 42 was not "migrated"
        assertTrue(prefs.all.keys.none { it.startsWith("flutter.oc.budgets.") })

        assertIs<RunnerState.Open>(runner().run())
        assertEquals(before, prefsFile.readBytes().toList())
    }

    @Test
    fun `B-MIGR-010 CorruptPrefsXml - Android loads garbage as an empty map, M1 blocks and no write reaches the file`() = runTest {
        copyFixture()
        val prefsFile = File(sharedPrefs, "FlutterSharedPreferences.xml")
        val full = prefsFile.readBytes()
        prefsFile.writeBytes(full.copyOf(full.size / 2)) // truncated mid-element
        val corrupt = prefsFile.readBytes().toList()

        val blocked = assertIs<RunnerState.Blocked>(runner().run())

        assertEquals(BlockReason.PrefsUnreadable, blocked.reason)
        assertEquals("M1", blocked.step)
        assertEquals(corrupt, prefsFile.readBytes().toList())
        assertTrue(secrets.values.isEmpty())
        assertTrue(File(dataDir, "files/migration/flutter-v57/FlutterSharedPreferences.xml").isFile, "S0 kept the bytes")
    }
}
