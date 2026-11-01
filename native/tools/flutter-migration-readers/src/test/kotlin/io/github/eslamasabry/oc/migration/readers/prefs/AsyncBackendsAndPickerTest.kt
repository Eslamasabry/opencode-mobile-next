package io.github.eslamasabry.oc.migration.readers.prefs

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncBackendsAndPickerTest {

    @Test
    fun `DataStore preferences_pb round trip with every value type`() {
        val entries = linkedMapOf<String, RawPref>(
            "b" to RawPref.Bool(true), "i" to RawPref.Int32(-3), "l" to RawPref.Int64(Long.MIN_VALUE),
            "f" to RawPref.F32(0.25f), "d" to RawPref.F64(-1.5), "s" to RawPref.Str("مرحبا"),
            "set" to RawPref.StrSet(linkedSetOf("x", "y")),
            "list" to FlutterPrefsCodec.encodeStringList(listOf("p")),
        )
        val read = AsyncBackends.readDataStore(AsyncBackends.writeDataStore(entries))
        assertIs<AsyncBackends.DataStoreRead.Parsed>(read)
        assertEquals(entries, read.entries)
        // The same codec decodes async values; there is no flutter. prefix in this store.
        assertEquals(PrefValue.StrList(listOf("p")), FlutterPrefsCodec.decode(read.entries.getValue("list")).value)
        assertEquals(PrefValue.Dbl(-1.5), FlutterPrefsCodec.decode(read.entries.getValue("d")).value)
    }

    @Test
    fun `a truncated or garbage DataStore file is corrupt, never a crash`() {
        val good = AsyncBackends.writeDataStore(FixtureGenerator.dataStoreFixture())
        for (n in 1 until good.size) {
            val r = AsyncBackends.readDataStore(good.copyOfRange(0, n))
            assertTrue(r is AsyncBackends.DataStoreRead.Corrupt || r is AsyncBackends.DataStoreRead.Parsed, "cut at $n")
        }
        assertIs<AsyncBackends.DataStoreRead.Corrupt>(AsyncBackends.readDataStore(byteArrayOf(0x0A, 0x7F)))
        assertIs<AsyncBackends.DataStoreRead.Parsed>(AsyncBackends.readDataStore(ByteArray(0)))
    }

    @Test
    fun `census finds neither async store on a normal install and counts them when present`() {
        val app = Files.createTempDirectory("app").toFile()
        val none = AsyncBackends.census(app, FixtureGenerator.PACKAGE)
        assertFalse(none.anyPresent)

        File(app, AsyncBackends.DATASTORE_RELATIVE_PATH).apply { parentFile.mkdirs() }
            .writeBytes(AsyncBackends.writeDataStore(FixtureGenerator.dataStoreFixture()))
        File(app, "shared_prefs").mkdirs()
        File(app, "shared_prefs/${AsyncBackends.defaultPrefsName(FixtureGenerator.PACKAGE)}.xml")
            .writeBytes(AndroidPrefsXmlWriter.write(mapOf("k" to RawPref.Str("v"))))
        val some = AsyncBackends.census(app, FixtureGenerator.PACKAGE)
        assertTrue(some.anyPresent)
        assertEquals(5, some.dataStoreKeys)
        assertEquals(1, some.defaultPrefsKeys)
        app.deleteRecursively()
    }

    @Test
    fun `image picker lost result is read so the pending photo can be recovered (audit finding 11)`() {
        val map = AndroidPrefsXml.parse(AndroidPrefsXmlWriter.write(FixtureGenerator.imagePickerFixture())).androidMap
        val lost = assertNotNull(ImagePickerCache.read(map))
        assertTrue(lost.hasResult)
        assertEquals("image", lost.type)
        assertEquals(1, lost.paths.size)
        assertTrue(lost.paths[0].endsWith("/cache/image_picker_fixture.jpg"))
        assertEquals(2048.0, lost.maxWidth)
        assertEquals(85, lost.imageQuality)
        assertFalse(lost.toString().contains("image_picker_fixture"))
        assertNull(ImagePickerCache.read(emptyMap()))
        val errorOnly = ImagePickerCache.read(mapOf(ImagePickerCache.KEY_ERROR_CODE to RawPref.Str("no_available_camera")))
        assertTrue(errorOnly!!.hasError && errorOnly.paths.isEmpty())
    }
}
