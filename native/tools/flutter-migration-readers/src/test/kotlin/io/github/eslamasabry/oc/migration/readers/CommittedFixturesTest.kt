package io.github.eslamasabry.oc.migration.readers

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.migration.BackupPublisher
import io.github.eslamasabry.oc.migration.readers.migration.BarrierPref
import io.github.eslamasabry.oc.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.migration.readers.migration.Fs
import io.github.eslamasabry.oc.migration.readers.migration.JournalStore
import io.github.eslamasabry.oc.migration.readers.migration.MigrationStep
import io.github.eslamasabry.oc.migration.readers.migration.PrefsBaseline
import io.github.eslamasabry.oc.migration.readers.migration.RecordingFaults
import io.github.eslamasabry.oc.migration.readers.migration.SimulatedCrash
import io.github.eslamasabry.oc.migration.readers.migration.StepOutcome
import io.github.eslamasabry.oc.migration.readers.migration.StepRunner
import io.github.eslamasabry.oc.migration.readers.model.FlutterPrefsModelBuilder
import io.github.eslamasabry.oc.migration.readers.model.ProfilesRead
import io.github.eslamasabry.oc.migration.readers.model.TypeCheck
import io.github.eslamasabry.oc.migration.readers.prefs.AsyncBackends
import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.prefs.ImagePickerCache
import io.github.eslamasabry.oc.migration.readers.secure.FlutterSecureStorageReader
import io.github.eslamasabry.oc.migration.readers.secure.FssStores
import io.github.eslamasabry.oc.migration.readers.secure.SoftwareRsaOaepUnwrapper
import io.github.eslamasabry.oc.migration.readers.xml.AndroidLoad
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.migration.readers.xml.SharedPrefsFile
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reads the committed fixture files (src/test/resources/fixtures/synthetic-v1) and runs a crash-tested pipeline on them. */
class CommittedFixturesTest {

    private val fixture: File = File(javaClass.getResource("/fixtures/synthetic-v1/seed-manifest.json")!!.toURI()).parentFile
    private val tmp: File = Files.createTempDirectory("e2e").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private val keyPair by lazy {
        FixtureGenerator.keyPairFromPkcs8(Base64.getMimeDecoder().decode(File(fixture, "fixture-rsa-private.pk8.b64").readText()))
    }

    @Suppress("UNCHECKED_CAST")
    private val manifestSecrets: Map<String, String>
        get() = (MiniJson.parse(File(fixture, "seed-manifest.json").readText()) as Map<String, Any?>)["secrets"] as Map<String, String>

    @Test
    fun `committed prefs XML is exactly what the generator writes today (drift guard)`() {
        val committed = File(fixture, "shared_prefs/FlutterSharedPreferences.xml").readBytes()
        assertTrue(committed.contentEquals(AndroidPrefsXmlWriter.write(FixtureGenerator.prefsFixture())), "regenerate with ./gradlew generateFixtures")
        assertEquals(FixtureGenerator.SECRETS, manifestSecrets)
    }

    @Test
    fun `every committed store reads back`() {
        val shared = File(fixture, "shared_prefs")
        val prefs = SharedPrefsFile(shared, FlutterPrefsCodec.FILE_NAME).read()!!
        assertEquals(AndroidLoad.Loaded, prefs.load)
        val model = FlutterPrefsModelBuilder.build(prefs.androidMap)
        assertEquals(ProfilesRead.Read(listOf(FixtureGenerator.PROFILE_A, FixtureGenerator.PROFILE_B), 0, 0), model.profiles)
        val all = model.app.values + model.byId.values.flatMap { it.values }
        assertTrue(all.all { it.typeCheck == TypeCheck.Ok }, all.filter { it.typeCheck != TypeCheck.Ok }.toString())

        val secrets = FlutterSecureStorageReader(SoftwareRsaOaepUnwrapper(keyPair.private)).read(FssStores.fromDirectory(shared))
        assertEquals(manifestSecrets, secrets.plain.associate { it.name to it.value.reveal() })

        val ds = AsyncBackends.readDataStore(File(fixture, AsyncBackends.DATASTORE_RELATIVE_PATH))
        assertIs<AsyncBackends.DataStoreRead.Parsed>(ds)
        assertEquals(FixtureGenerator.dataStoreFixture(), ds.entries)

        val picker = SharedPrefsFile(shared, ImagePickerCache.FILE_NAME).read()!!.androidMap
        assertTrue(ImagePickerCache.read(picker)!!.hasResult)
    }

    // ---- an end-to-end run of S0 → M1 baseline → barrier → M2 → M5 on file copies, crashed everywhere ----

    /** The Flutter-readable flag, written into the live XML the way a commit() would (whole-file atomic rewrite). */
    private class XmlBarrier(private val shared: File) : BarrierPref {
        private val file get() = SharedPrefsFile(shared, FlutterPrefsCodec.FILE_NAME)
        override fun read(): Boolean? = (file.read()?.androidMap?.get("flutter.oc.native.flutterReadable") as? RawPref.Bool)?.value
        override fun write(value: Boolean): Boolean {
            val map = LinkedHashMap(file.read()!!.androidMap)
            map["flutter.oc.native.flutterReadable"] = RawPref.Bool(value)
            Fs.writeAtomically(file.xml, AndroidPrefsXmlWriter.write(map))
            return true
        }
    }

    private inner class Pipeline(private val app: File, private val faults: FaultPoints) {
        val shared = File(app, "shared_prefs")
        val native = File(app, "files/native")
        val secretStore = File(native, "secret-store-standin.json")
        val journal = JournalStore(File(native, "migration.json"), faults)

        fun run(): List<Any> {
            val s0 = BackupPublisher(shared, File(app, "files/migration"), journal, faults, freeBytes = { Long.MAX_VALUE }).runIfNeeded()
            // M1: read-only integrity check, then the baseline (written once, before the first write).
            val live = SharedPrefsFile(shared, FlutterPrefsCodec.FILE_NAME).read()!!
            check(live.load == AndroidLoad.Loaded)
            val baselineFile = File(native, "m1-baseline.json")
            if (!baselineFile.exists()) PrefsBaseline.of(live.androidMap).save(baselineFile, faults)
            val runner = StepRunner(journal, XmlBarrier(shared), faults)
            val init = runner.initBarrier()
            val m2 = runner.run(object : MigrationStep {
                override val id = "M2"
                override fun apply() {
                    val backupShared = File(app, "files/migration/flutter-v57")
                    val read = FlutterSecureStorageReader(SoftwareRsaOaepUnwrapper(keyPair.private)).read(FssStores.fromDirectory(backupShared))
                    val existing = if (secretStore.exists()) MiniJson.parse(secretStore.readText()) as Map<*, *> else emptyMap<Any, Any>()
                    val merged = LinkedHashMap<String, Any?>()
                    existing.forEach { (k, v) -> merged[k as String] = v }
                    for (p in read.plain) if (p.name !in merged) {
                        merged[p.name] = p.value.reveal()
                        Fs.writeAtomically(secretStore, MiniJson.encode(merged).toByteArray(), faults, "secret:${p.name}")
                    }
                }
                override fun verify(): Boolean =
                    secretStore.exists() && (MiniJson.parse(secretStore.readText()) as Map<*, *>).keys == manifestSecrets.keys
            })
            val m5 = runner.run(object : MigrationStep {
                override val id = "M5"
                override fun apply() = Unit
                override fun verify(): Boolean {
                    val nowMap = SharedPrefsFile(shared, FlutterPrefsCodec.FILE_NAME).read()!!.androidMap
                    return PrefsBaseline.load(baselineFile)!!.undeclaredChanges(nowMap, PrefsBaseline.V1_DECLARED_WRITES) == 0
                }
            })
            return listOf(s0, init, m2, m5)
        }
    }

    private fun freshApp(name: String): File {
        val app = File(tmp, name)
        File(fixture, "shared_prefs").copyRecursively(File(app, "shared_prefs"))
        return app
    }

    @Test
    fun `the pipeline survives a crash at every point and a rerun changes nothing`() {
        val ref = freshApp("ref")
        val points = RecordingFaults().also { Pipeline(ref, it).run() }.seen
        val expectedSecrets = File(ref, "files/native/secret-store-standin.json").readText()
        val expectedPrefs = SharedPrefsFile(File(ref, "shared_prefs"), FlutterPrefsCodec.FILE_NAME).read()!!.androidMap
        assertEquals(RawPref.Bool(true), expectedPrefs["flutter.oc.native.flutterReadable"])
        assertTrue(points.size > 20, "$points")

        for ((i, point) in points.withIndex()) {
            val app = freshApp("case$i")
            val occurrence = points.subList(0, i + 1).count { it == point }
            assertFailsWith<SimulatedCrash> { Pipeline(app, RecordingFaults(point, occurrence)).run() }
            val out = Pipeline(app, FaultPoints.None).run()
            assertTrue(out[2] == StepOutcome.Verified || out[2] == StepOutcome.AlreadyVerified, "M2 after $point: $out")
            assertTrue(out[3] == StepOutcome.Verified || out[3] == StepOutcome.AlreadyVerified, "M5 after $point: $out")
            assertEquals(MiniJson.parse(expectedSecrets), MiniJson.parse(File(app, "files/native/secret-store-standin.json").readText()), "after $point")
            assertEquals(expectedPrefs, SharedPrefsFile(File(app, "shared_prefs"), FlutterPrefsCodec.FILE_NAME).read()!!.androidMap, "after $point")

            // Rerun: no byte changes anywhere.
            val before = app.walkTopDown().filter { it.isFile }.associate { it.relativeTo(app).path to Fs.sha256Hex(it) }
            Pipeline(app, FaultPoints.None).run()
            val after = app.walkTopDown().filter { it.isFile }.associate { it.relativeTo(app).path to Fs.sha256Hex(it) }
            assertEquals(before, after, "rerun after $point changed files")
        }
    }

    @Test
    fun `a corrupted live prefs file is caught by M1 before any write`() {
        val app = freshApp("corrupt")
        val live = File(app, "shared_prefs/FlutterSharedPreferences.xml")
        live.writeBytes(live.readBytes().copyOfRange(0, live.length().toInt() / 2))
        val read = AndroidPrefsXml.parse(live)
        assertEquals(AndroidLoad.LoadsEmpty, read.load)
        assertTrue(read.entries.isNotEmpty(), "our salvage finds keys while Android would see none: Blocked(PrefsUnreadable)")
        val sha = Fs.sha256Hex(live)
        assertFailsWith<IllegalStateException> { Pipeline(app, FaultPoints.None).run() }
        assertEquals(sha, Fs.sha256Hex(live), "no write reached the file")
        // S0 still made the byte backup first.
        assertTrue(File(app, "files/migration/flutter-v57/FlutterSharedPreferences.xml").exists())
    }
}
