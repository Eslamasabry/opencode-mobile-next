package io.github.eslamasabry.oc.engine.linuxtools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Android half of shared_storage_path_test.dart's "storage permission … bound only outside the confined tier":
 * the native BuiltinLinux.kt (:android:linux) binds all shared storage only before AI Team, and only the opened folders
 * (never the whole of /storage) in the confined command. Nothing about the permission is stored by these rules.
 */
class SharedStorageBindSourceTest {
    @Test
    fun `B-LINUX-221 shared storage is bound whole only outside the confined tier`() {
        val linux = File(ANDROID_LINUX_SOURCES, "BuiltinLinux.kt").readText()
        assertTrue(linux.contains("--bind=/storage\""))
        assertTrue(linux.contains(":/sdcard\""))
        assertTrue(Regex("""sharedStorageBinds\(\)\s*\+\s*fakeProcBinds""").containsMatchIn(linux))
        // Confined (AI Team on): only the folders the person opened (plus the app's Download/OpenCode outbox), bound and
        // allowed by exact path. Whole storage needs the person's broad-access choice, and never under AI Team.
        assertTrue(linux.contains("object SharedStorageBinds"))
        assertTrue(
            Regex("""SharedStorageBinds\.binds\(\s*sharedStorageRootsForLaunch\(\),\s*confined = !sharedStorageBroadAccess\(\),?\s*\)""")
                .containsMatchIn(linux),
        )
        assertTrue(Regex("""fun sharedStorageBroadAccess\(\): Boolean =\s*!prootIsConfined &&""").containsMatchIn(linux))
        val launchRoots = linux.substring(linux.indexOf("private fun sharedStorageRootsForLaunch"), linux.indexOf("private fun sharedStorageBinds"))
        assertTrue(launchRoots.contains("sharedProjectRoots()"))
        assertFalse(launchRoots.contains("\"/storage\""))
        val protectedBody =
            linux.substring(linux.indexOf("private fun protectedCommand"), linux.indexOf("private fun requirePhoneBoundaryKernel"))
        assertTrue(protectedBody.contains("sharedStorageRootsForLaunch()"))
        assertFalse(protectedBody.contains("\"/storage\""))
        assertFalse(protectedBody.contains("sdcard"))
    }

    @Test
    fun `B-LINUX-221 the portable bind rules keep no per-profile or stored state`() {
        val rules =
            File("src/commonMain/kotlin/io/github/eslamasabry/oc/engine/linuxtools/SharedStoragePath.kt").absoluteFile.readText()
        assertFalse(rules.contains("prefs"))
        assertFalse(rules.contains("SharedPreferences"))
    }
}
