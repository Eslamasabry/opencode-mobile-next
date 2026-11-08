package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The in-app updater installs only its own download of a newer, same-signed build. */
class AppUpdateInstallerCheckTest {
    private val app = UpdateApkCheck.Facts("io.github.eslamasabry.opencode_mobile", 62, setOf("aa"))

    @Test fun aNewerSameSignedBuildMayBeInstalled() {
        assertNull(UpdateApkCheck.problem(app, app.copy(versionCode = 63)))
    }

    @Test fun anythingElseIsRefused() {
        assertNotNull(UpdateApkCheck.problem(app, null))
        assertNotNull(UpdateApkCheck.problem(null, app.copy(versionCode = 63)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(packageName = "com.evil", versionCode = 63)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(signers = setOf("bb"), versionCode = 63)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(signers = setOf("aa", "bb"), versionCode = 63)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(signers = emptySet(), versionCode = 63)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(versionCode = 62)))
        assertNotNull(UpdateApkCheck.problem(app, app.copy(versionCode = 61)))
    }

    @Test fun onlyAnApkDirectlyInTheUpdatesFolderIsAccepted() {
        val cache = Files.createTempDirectory("cache").toFile()
        try {
            val updates = File(cache, "updates").apply { mkdirs() }
            val apk = File(updates, "update.apk").apply { writeText("x") }
            File(updates, "notes.txt").writeText("x")
            File(cache, "other.apk").writeText("x")
            val files = File(cache, "../files").canonicalFile
            assertEquals(apk.canonicalFile, AppUpdateInstaller.updateApk(cache, apk.path))
            assertNull(AppUpdateInstaller.updateApk(cache, File(updates, "notes.txt").path))
            assertNull(AppUpdateInstaller.updateApk(cache, File(cache, "other.apk").path))
            assertNull(AppUpdateInstaller.updateApk(cache, "${updates.path}/../other.apk"))
            assertNull(AppUpdateInstaller.updateApk(cache, File(updates, "missing.apk").path))
            assertNull(AppUpdateInstaller.updateApk(cache, File(files, "linux/ubuntu/x.apk").path))
            val link = File(updates, "link.apk")
            Files.createSymbolicLink(link.toPath(), File(cache, "other.apk").toPath())
            assertNull(AppUpdateInstaller.updateApk(cache, link.path))
        } finally {
            cache.deleteRecursively()
        }
    }
}
