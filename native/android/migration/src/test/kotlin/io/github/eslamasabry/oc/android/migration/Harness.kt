package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.SimulatedCrash
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFixtureWriter
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.FssKeyUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.secure.SoftwareRsaOaepUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * The adopted prefs file as Android treats it, for plain JVM tests: loaded once per "process" (a `.bak` twin replaces
 * the `.xml` on load, an unparsable file loads as an EMPTY map), every acknowledged commit rewrites the file
 * atomically. A new instance = a new process after a crash.
 */
internal class FakeFlutterPrefs(private val dir: File) : FlutterPrefsFile {
    var refuse: (Map<String, RawPref?>) -> Boolean = { false }
    var commits = 0
    private var map: LinkedHashMap<String, RawPref>? = null
    private val xml get() = File(dir, "FlutterSharedPreferences.xml")
    private val bak get() = File(dir, "FlutterSharedPreferences.xml.bak")

    override fun live(): Map<String, RawPref> = LinkedHashMap(loaded())

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        val m = loaded()
        if (refuse(changes)) return false
        for ((k, v) in changes) if (v == null) m.remove(k) else m[k] = v
        dir.mkdirs()
        val tmp = File(dir, "FlutterSharedPreferences.xml.tmp")
        tmp.writeBytes(AndroidPrefsXmlWriter.write(m))
        Files.move(tmp.toPath(), xml.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        commits++
        return true
    }

    private fun loaded(): LinkedHashMap<String, RawPref> {
        map?.let { return it }
        if (bak.exists()) {
            xml.delete()
            bak.renameTo(xml)
        }
        val m = if (xml.exists()) LinkedHashMap(AndroidPrefsXml.parse(xml).androidMap) else LinkedHashMap()
        map = m
        return m
    }
}

/** The native secret store: survives "process deaths" (it is on disk). */
internal class MemorySecrets : MigrationSecretSink {
    val values = sortedMapOf<String, String>()
    var unavailable: (String) -> Boolean = { false }
    var writes = 0

    override suspend fun read(name: String): String? {
        if (unavailable(name)) throw IllegalStateException("keystore said: $name") // a failing Keystore may echo input
        return values[name]
    }

    override suspend fun write(name: String, value: String): Boolean {
        if (unavailable(name)) throw IllegalStateException("keystore said: $value")
        writes++
        values[name] = value
        return true
    }

    override suspend fun delete(name: String): Boolean {
        values.remove(name)
        return true
    }
}

internal class RecordingSignals : MigrationSignals {
    val updated = mutableListOf<Long?>()
    val done = mutableListOf<MigrationDoneEvent>()
    val lines = mutableListOf<String>()
    override fun appUpdated(fromCode: Long?) { updated += fromCode }
    override fun migrationDone(event: MigrationDoneEvent) { done += event }
    override fun diagnosticsLine(text: String) { lines += text }
}

/** Throws [SimulatedCrash] the [occurrence]-th time [crashAt] is reached; records every point. */
internal class CrashAt(private val crashAt: String? = null, private val occurrence: Int = 1, private val delay: Long = 0) : StepInterrupter {
    val seen = mutableListOf<String>()
    override fun reached(point: String) {
        seen += point
        if (point == crashAt && seen.count { it == point } == occurrence) throw SimulatedCrash(point)
    }
    override fun delayMs(): Long = delay
}

/** One phone: a data dir, a secret store and the inputs a runner needs. Each [runner] call is a new process. */
internal class World(root: File) {
    val paths = MigrationPaths(File(root, "data"), externalFilesDir = File(root, "external/files"), externalCacheDir = File(root, "external/cache"))
    val secrets = MemorySecrets()
    val signals = RecordingSignals()
    val redactor = Redactor()
    var unwrapper: FssKeyUnwrapper = SoftwareRsaOaepUnwrapper(null)
    var freeBytes: Long = 1L shl 40
    var kotlinPrefs: Map<String, Map<String, Any?>> = emptyMap()
    var nowMs: Long = 1_730_457_600_000L
    var versionCode: Long = 10_000L
    var prefs: FakeFlutterPrefs = FakeFlutterPrefs(paths.sharedPrefs)

    init {
        paths.sharedPrefs.mkdirs()
        paths.files.mkdirs()
    }

    /** A new process: a fresh prefs instance over the same files. */
    fun runner(io: CoroutineDispatcher, interrupter: StepInterrupter = StepInterrupter.None): MigrationRunner {
        prefs = FakeFlutterPrefs(paths.sharedPrefs)
        return MigrationRunner(
            paths = paths, prefs = prefs, kotlinPrefs = { kotlinPrefs[it] }, unwrapper = unwrapper, sink = secrets,
            redactor = redactor, signals = signals, interrupter = interrupter, freeBytes = { freeBytes },
            packageName = PACKAGE, nativeVersionCode = versionCode, now = { nowMs }, io = io,
        )
    }

    fun seedPrefs(logical: Map<String, RawPref>) {
        File(paths.sharedPrefs, "FlutterSharedPreferences.xml")
            .writeBytes(AndroidPrefsXmlWriter.write(logical.mapKeys { FlutterPrefsCodec.physicalKey(it.key) }))
    }

    /** Writes FSS 11.2.0 files for [secrets]; [corrupt] names get a ciphertext whose tag fails (AuthFailed). */
    fun seedSecrets(secrets: Map<String, String>, corrupt: Set<String> = emptySet(), markers: FssFixtureWriter.Markers = FssFixtureWriter.Markers.Default) {
        val writer = FssFixtureWriter(KEYS.public, SecureRandom())
        val files = writer.write(secrets, markers)
        for (name in corrupt) {
            val entry = FssFormat.entryName(name)
            val good = (files.data[entry] as RawPref.Str).value
            val bytes = FlutterPrefsCodec.decodeAndroidBase64(good).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            files.data[entry] = RawPref.Str(FlutterPrefsCodec.encodeAndroidBase64Default(bytes))
        }
        for ((name, bytes) in files.xmlFiles()) File(paths.sharedPrefs, "$name.xml").writeBytes(bytes)
        unwrapper = SoftwareRsaOaepUnwrapper(KEYS.private)
    }

    /** The P5 synthetic fixture (`tools/flutter-migration-readers/.../synthetic-v1`), copied into this phone. */
    fun seedReadersFixture() {
        val fixture = readersFixture()
        File(fixture, "shared_prefs").listFiles()!!.forEach { it.copyTo(File(paths.sharedPrefs, it.name), overwrite = true) }
        val pk8 = Base64.getMimeDecoder().decode(File(fixture, "fixture-rsa-private.pk8.b64").readText())
        unwrapper = SoftwareRsaOaepUnwrapper(fixturePrivateKey(pk8))
    }

    fun prefsBytes(): ByteArray = File(paths.sharedPrefs, "FlutterSharedPreferences.xml").readBytes()

    fun logical(): Map<String, RawPref> = FakeFlutterPrefs(paths.sharedPrefs).live().mapNotNull { (k, v) ->
        FlutterPrefsCodec.logicalKey(k)?.let { it to v }
    }.toMap()

    fun journalText(): String = paths.journal.readText()

    companion object {
        const val PACKAGE = "io.github.eslamasabry.opencode_mobile"
        val KEYS: KeyPair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

        fun readersFixture(): File = File(requireNotNull(System.getProperty("oc.readersFixture")) { "oc.readersFixture not set" })

        fun fixturePrivateKey(pk8: ByteArray): PrivateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pk8))

        @Suppress("UNCHECKED_CAST")
        fun fixtureSecrets(): Map<String, String> {
            val m = io.github.eslamasabry.oc.android.migration.readers.json.MiniJson.parse(File(readersFixture(), "seed-manifest.json").readText()) as Map<String, Any?>
            return m["secrets"] as Map<String, String>
        }
    }
}

internal fun s(v: String) = RawPref.Str(v)
internal fun b(v: Boolean) = RawPref.Bool(v)
internal fun l(v: Long) = RawPref.Int64(v)

/** Every file under [dir] → bytes (for "a further run changes no byte"). */
internal fun snapshotFiles(dir: File): Map<String, String> =
    dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to sha(it.readBytes()) }.toSortedMap()

private fun sha(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
