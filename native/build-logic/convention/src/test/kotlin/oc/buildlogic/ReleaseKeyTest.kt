package oc.buildlogic

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The three places the release key file is looked for, with fake files and obviously fake values only. */
class ReleaseKeyTest {
    private val repo: File = Files.createTempDirectory("oc-release-key-").toFile()

    @AfterTest
    fun cleanUp() {
        repo.deleteRecursively()
    }

    private fun keyFile(path: String, store: String): File =
        File(repo, path).apply {
            parentFile.mkdirs()
            writeText("storeFile=$store\nstorePassword=fake-store-pass\nkeyAlias=fake-alias\nkeyPassword=fake-key-pass\n")
        }

    @Test
    fun `OC_KEY_PROPERTIES wins over both repository places and resolves a relative keystore next to itself`() {
        keyFile("native/key.properties", "native.jks")
        keyFile("android/key.properties", "old.jks")
        val named = keyFile("elsewhere/ci-key.properties", "ci.jks")
        val key = ReleaseKey.locate(named.path, repo)
        assertEquals(named.canonicalFile, key.source.canonicalFile)
        assertEquals(File(repo, "elsewhere/ci.jks").canonicalFile, key.storeFile!!.canonicalFile)
        assertEquals("fake-alias", key.keyAlias)
        assertNull(key.notice)
    }

    @Test
    fun `an absolute keystore path is kept as it is`() {
        val named = keyFile("elsewhere/ci-key.properties", "/fake/abs/ci.jks")
        assertEquals(File("/fake/abs/ci.jks"), ReleaseKey.locate(named.path, repo).storeFile)
    }

    @Test
    fun `native key properties comes next and resolves a relative keystore against native`() {
        keyFile("native/key.properties", "keys/native.jks")
        keyFile("android/key.properties", "old.jks")
        val key = ReleaseKey.locate(null, repo)
        assertEquals(File(repo, "native/key.properties").canonicalFile, key.source.canonicalFile)
        assertEquals(File(repo, "native/keys/native.jks").canonicalFile, key.storeFile!!.canonicalFile)
        assertEquals("fake-store-pass", key.storePassword)
        assertNull(key.notice)
        assertEquals(key.source.canonicalPath, ReleaseKey.locate("  ", repo).source.canonicalPath, "blank = unset")
    }

    @Test
    fun `the old android key properties is the fallback, resolves against android app as before, and says so`() {
        keyFile("android/key.properties", "../upload.jks")
        val key = ReleaseKey.locate(null, repo)
        assertEquals(File(repo, "android/key.properties").canonicalFile, key.source.canonicalFile)
        // Exactly the old rule: <repo>/android/app/<storeFile>.
        assertEquals(File(repo, "android/app/../upload.jks").canonicalFile, key.storeFile!!.canonicalFile)
        assertEquals("fake-key-pass", key.keyPassword)
        assertNotNull(key.notice)
        // Named explicitly through OC_KEY_PROPERTIES, the old file keeps the old resolution and needs no notice.
        val named = ReleaseKey.locate(File(repo, "android/key.properties").path, repo)
        assertEquals(key.storeFile!!.canonicalFile, named.storeFile!!.canonicalFile)
        assertNull(named.notice)
    }

    @Test
    fun `with no key file anywhere the result names the new place and has no keystore`() {
        val key = ReleaseKey.locate(null, repo)
        assertEquals(File(repo, "native/key.properties").canonicalFile, key.source.canonicalFile)
        assertNull(key.storeFile)
        assertNull(key.keyAlias)
        assertNull(key.notice)
    }
}
