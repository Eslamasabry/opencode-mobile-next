package io.github.eslamasabry.oc.migration.readers.secure

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlutterSecureStorageReaderTest {

    private companion object {
        val random = SecureRandom()
        val keyPair: KeyPair = FixtureGenerator.newKeyPair(random)
        val otherKeyPair: KeyPair = FixtureGenerator.newKeyPair(random)
    }

    private val reader = FlutterSecureStorageReader(SoftwareRsaOaepUnwrapper(keyPair.private))
    private fun writer() = FssFixtureWriter(keyPair.public, random)

    private fun plainMap(r: FssReadResult) = r.plain.associate { it.name to it.value.reveal() }

    @Test
    fun `names and constants match the plugin`() {
        assertEquals("This is the prefix for a secure storage\n", String(Base64.getDecoder().decode(FssFormat.ENTRY_PREFIX + "==")))
        assertEquals("io.github.eslamasabry.opencode_mobile.FlutterSecureStoragePluginKeyOAEP", FssFormat.keystoreAlias(FixtureGenerator.PACKAGE))
        assertEquals(FssFormat.ENTRY_PREFIX + "_pw.1", FssFormat.entryName("pw.1"))
    }

    @Test
    fun `decrypts every secret written by the ported write path`() {
        val files = writer().write(FixtureGenerator.SECRETS)
        val r = reader.read(files.stores())
        assertEquals(FixtureGenerator.SECRETS, plainMap(r))
        assertTrue(r.failed.isEmpty())
        assertEquals(MarkerState.Default, r.census.markers)
        assertEquals("namespaced", r.census.markerSource)
        assertEquals(FixtureGenerator.SECRETS.size, r.census.secretEntries)
    }

    @Test
    fun `reads through real XML files with Base64 line breaks escaped as character references`() {
        val dir = Files.createTempDirectory("fss").toFile()
        val long = "x".repeat(300) // long enough for several Base64 lines
        val files = writer().write(mapOf("pw.1" to long))
        for ((name, bytes) in files.xmlFiles()) File(dir, "$name.xml").writeBytes(bytes)
        val dataXml = File(dir, "${FssFormat.DATA_FILE}.xml").readText()
        assertTrue(dataXml.contains("&#10;"), "Base64.DEFAULT newlines are stored as &#10;")
        val r = reader.read(FssStores.fromDirectory(dir))
        assertEquals(mapOf("pw.1" to long), plainMap(r))
        dir.deleteRecursively()
    }

    @Test
    fun `markers only in the legacy config file are honoured`() {
        val r = reader.read(writer().write(mapOf("pw.1" to "a"), FssFixtureWriter.Markers.LegacyFileOnly).stores())
        assertEquals(MarkerState.Default, r.census.markers)
        assertEquals("legacy", r.census.markerSource)
        assertEquals(mapOf("pw.1" to "a"), plainMap(r))
    }

    @Test
    fun `missing markers are attempted like the v11 plugin does`() {
        val r = reader.read(writer().write(mapOf("pw.1" to "a"), FssFixtureWriter.Markers.None).stores())
        assertEquals(MarkerState.Missing, r.census.markers)
        assertEquals(mapOf("pw.1" to "a"), plainMap(r))
    }

    @Test
    fun `v9 removed-cipher markers flag every name and never try to decrypt`() {
        var unwrapCalls = 0
        val counting = FlutterSecureStorageReader { unwrapCalls++; SoftwareRsaOaepUnwrapper(keyPair.private).unwrap(it) }
        val r = counting.read(writer().write(mapOf("pw.1" to "a", "pw.2" to "b"), FssFixtureWriter.Markers.Removed).stores())
        assertEquals(MarkerState.Removed, r.census.markers)
        assertEquals(listOf(SecretFailure.RemovedCipher, SecretFailure.RemovedCipher), r.failed.map { it.reason })
        assertEquals(0, unwrapCalls)
    }

    @Test
    fun `keystore AES and unknown markers are flagged`() {
        val files = writer().write(mapOf("pw.1" to "a"))
        files.configNamespaced[FssFormat.MARKER_KEY_ALGORITHM] = RawPref.Str("AES_GCM_NoPadding")
        assertEquals(SecretFailure.KeystoreAesKeyCipher, reader.read(files.stores()).failed.single().reason)
        files.configNamespaced[FssFormat.MARKER_KEY_ALGORITHM] = RawPref.Str("SomethingNew")
        assertEquals(SecretFailure.UnknownMarkers, reader.read(files.stores()).failed.single().reason)
    }

    @Test
    fun `_BACKUP entries, foreign keys and Tink keysets are skipped and counted`() {
        val files = writer().write(mapOf("pw.1" to "a"), backupCopies = true)
        files.data[FssFormat.TINK_KEYSET_KEYS.first()] = RawPref.Str("tink")
        files.data["unrelated"] = RawPref.Str("x")
        val r = reader.read(files.stores())
        assertEquals(mapOf("pw.1" to "a"), plainMap(r))
        assertEquals(1, r.census.backupEntries)
        assertEquals(2, r.census.foreignEntries)
        assertTrue(r.census.tinkKeysetPresent)
    }

    @Test
    fun `whole-store failures name one reason per secret`() {
        val files = writer().write(mapOf("pw.1" to "a", "pw.2" to "b"))
        val noKey = FssStores(files.data, emptyMap(), files.configNamespaced, files.configLegacy)
        assertEquals(setOf(SecretFailure.NoWrappedKey), reader.read(noKey).failed.map { it.reason }.toSet())

        val badKey = FssStores(files.data, mapOf(FssFormat.WRAPPED_KEY_ENTRY to RawPref.Str("A")), files.configNamespaced, files.configLegacy)
        assertEquals(setOf(SecretFailure.WrappedKeyBadBase64), reader.read(badKey).failed.map { it.reason }.toSet())

        val noAlias = FlutterSecureStorageReader(SoftwareRsaOaepUnwrapper(null)).read(files.stores())
        assertEquals(setOf(SecretFailure.KeystoreAliasMissing), noAlias.failed.map { it.reason }.toSet())

        val wrongRsa = FlutterSecureStorageReader(SoftwareRsaOaepUnwrapper(otherKeyPair.private)).read(files.stores())
        assertEquals(setOf(SecretFailure.UnwrapFailed), wrongRsa.failed.map { it.reason }.toSet())

        val wrongLength = FlutterSecureStorageReader { javax.crypto.spec.SecretKeySpec(ByteArray(32), "AES") }.read(files.stores())
        assertEquals(setOf(SecretFailure.WrongKeyLength), wrongLength.failed.map { it.reason }.toSet())
        assertEquals(2, wrongLength.secrets.size)
    }

    @Test
    fun `per-entry failures do not stop the other entries`() {
        val w = writer()
        val files = w.write(linkedMapOf("pw.ok" to "fine", "pw.tampered" to "x", "pw.short" to "y", "pw.b64" to "z", "pw.type" to "t", "pw.utf" to "u"))
        val tampered = FlutterPrefsCodec.decodeAndroidBase64((files.data.getValue(FssFormat.entryName("pw.tampered")) as RawPref.Str).value)
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 1).toByte()
        files.data[FssFormat.entryName("pw.tampered")] = RawPref.Str(FlutterPrefsCodec.encodeAndroidBase64Default(tampered))
        files.data[FssFormat.entryName("pw.short")] = RawPref.Str(FlutterPrefsCodec.encodeAndroidBase64Default(ByteArray(27)))
        files.data[FssFormat.entryName("pw.b64")] = RawPref.Str("not*base64")
        files.data[FssFormat.entryName("pw.type")] = RawPref.Bool(true)
        files.data[FssFormat.entryName("pw.utf")] = RawPref.Str(w.encryptBytes(byteArrayOf(0xC3.toByte(), 0x28)))
        val r = reader.read(files.stores())
        assertEquals(mapOf("pw.ok" to "fine"), plainMap(r))
        assertEquals(
            mapOf(
                "pw.tampered" to SecretFailure.AuthFailed, "pw.short" to SecretFailure.TooShort, "pw.b64" to SecretFailure.BadBase64,
                "pw.type" to SecretFailure.NotAString, "pw.utf" to SecretFailure.NotUtf8,
            ),
            r.failed.associate { it.name to it.reason },
        )
    }

    @Test
    fun `no AAD in the plugin format, so a ciphertext moved to another name still decrypts (why SecretStore adds AAD)`() {
        val files = writer().write(mapOf("pw.1" to "one", "pw.2" to "two"))
        files.data[FssFormat.entryName("pw.2")] = files.data.getValue(FssFormat.entryName("pw.1"))
        assertEquals("one", plainMap(reader.read(files.stores()))["pw.2"])
    }

    @Test
    fun `an empty store reads as nothing, without touching the Keystore`() {
        var calls = 0
        val r = FlutterSecureStorageReader { calls++; throw IllegalStateException() }.read(FssStores(emptyMap(), emptyMap(), emptyMap(), emptyMap()))
        assertTrue(r.secrets.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `secret values never appear in toString of results or census`() {
        val r = reader.read(writer().write(FixtureGenerator.SECRETS).stores())
        val text = r.toString() + r.census.toString() + r.secrets.joinToString()
        for (v in FixtureGenerator.SECRETS.values.filter { it.length >= 4 }) assertFalse(text.contains(v), "leaked a fixture secret")
    }

    @Test
    fun `the XML of a store can be parsed back from what the writer produced`() {
        val files = writer().write(mapOf("pw.1" to "a"))
        val reparsed = AndroidPrefsXml.parse(AndroidPrefsXmlWriter.write(files.data)).androidMap
        assertEquals(files.data, reparsed)
    }
}
