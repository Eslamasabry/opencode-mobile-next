package io.github.eslamasabry.oc.android.tiles

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.robolectric.RuntimeEnvironment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A provider that serves bytes held in memory; an item named "gone" fails to open, "huge" lies about its size. */
class FakeShareProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor {
        val id = uri.lastPathSegment.orEmpty()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cursor.addRow(arrayOf<Any?>(files[id]?.first ?: id, if (id == "huge") 999_999_999L else files[id]?.second?.size?.toLong()))
        return cursor
    }

    override fun getType(uri: Uri): String? = "application/octet-stream"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val bytes = files[uri.lastPathSegment]?.second ?: throw java.io.FileNotFoundException()
        val tmp = File.createTempFile("share", ".bin").also { it.writeBytes(bytes); it.deleteOnExit() }
        return ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

    companion object {
        val files = mutableMapOf<String, Pair<String, ByteArray>>()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedStashTest {
    private lateinit var stash: SharedStash
    private lateinit var dir: File
    private val limits = StashLimits(maxItems = 5, maxItemBytes = 100, maxTotalBytes = 250)

    private fun uri(id: String) = Uri.parse("content://share.test/$id")

    @Before
    fun setUp() {
        FakeShareProvider.files.clear()
        Robolectric.setupContentProvider(FakeShareProvider::class.java, "share.test")
        val context = RuntimeEnvironment.getApplication()
        dir = File(context.cacheDir, "share-in-test").also { it.deleteRecursively() }
        stash = SharedStash(context.contentResolver, dir)
    }

    private fun put(id: String, name: String, size: Int) {
        FakeShareProvider.files[id] = name to ByteArray(size) { 7 }
    }

    @Test
    fun `B-ANDR-076 a shared item is copied into private storage with its hints`() {
        put("a", "photo.jpg", 40)
        val slot = stash.copyIn(listOf(uri("a")), limits).single() as StashSlot.Kept
        assertEquals(40L, slot.stashed.file.length())
        assertEquals("photo.jpg", slot.stashed.name)
        assertTrue(slot.stashed.file.absolutePath.startsWith(dir.absolutePath))
    }

    @Test
    fun `B-ANDR-076 an item over the per item limit is refused and leaves no copy`() {
        put("big", "big.bin", 101)
        FakeShareProvider.files["huge"] = "huge.bin" to ByteArray(10)
        val slots = stash.copyIn(listOf(uri("big"), uri("huge")), limits)
        assertEquals(listOf(StashRefusal.TooLarge, StashRefusal.TooLarge), slots.map { (it as StashSlot.Refused).why })
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `B-ANDR-076 the total limit stops later items and the count limit stops the rest`() {
        listOf("a", "b", "c", "d", "e", "f").forEach { put(it, "$it.bin", 90) }
        val slots = stash.copyIn(listOf("a", "b", "c", "d", "e", "f").map(::uri), limits)
        assertTrue(slots[0] is StashSlot.Kept)
        assertTrue(slots[1] is StashSlot.Kept)
        assertEquals(StashRefusal.TooLarge, (slots[2] as StashSlot.Refused).why) // 270 > 250
        assertEquals(StashRefusal.OverCount, (slots[5] as StashSlot.Refused).why)
    }

    @Test
    fun `B-ANDR-076 an unreadable item is told apart and does not stop the others`() {
        put("ok", "ok.txt", 5)
        val slots = stash.copyIn(listOf(uri("gone"), uri("ok")), limits)
        assertEquals(StashRefusal.Unreadable, (slots[0] as StashSlot.Refused).why)
        assertTrue(slots[1] is StashSlot.Kept)
    }

    @Test
    fun `B-ANDR-076 clear removes every copy and the description keeps names out`() {
        put("a", "secret-name.txt", 5)
        val slot = stash.copyIn(listOf(uri("a")), limits).single() as StashSlot.Kept
        assertFalse(slot.toString().contains("secret"))
        stash.clear()
        assertFalse(slot.stashed.file.exists())
    }
}
