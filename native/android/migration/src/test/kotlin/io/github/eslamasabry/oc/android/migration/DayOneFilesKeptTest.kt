package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.SimulatedCrash
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-MIGR-008 / B-MIGR-009 (owner decision 2026-11-01, day-1 update): the migration's file plan keeps the app files
 * directory (the built-in Linux rootfs, its projects and the in-app server's data, voice and model files) and the
 * Flutter documents directory `app_flutter` (attachments, drafts, saved prompts, voice / read-aloud models, reports)
 * exactly where they are. Nothing there is moved, rewritten or deleted, so nothing has to be installed or downloaded
 * again. The only things the runner writes are `files/native/`, `files/migration/` and the adopted prefs file.
 */
class DayOneFilesKeptTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.io() = StandardTestDispatcher(testScheduler)

    /** A large-ish model so a "re-download" (delete + new bytes) cannot go unnoticed. */
    private val model = ByteArray(256 * 1024) { (it * 31).toByte() }

    private val linuxFiles = mapOf(
        "linux/ubuntu/root/projects/my-app/main.py" to "print('hi')\n",
        "linux/ubuntu/root/projects/my-app/.git/HEAD" to "ref: refs/heads/main\n",
        "linux/ubuntu/root/.local/share/opencode/storage/session/ses_1.json" to "{\"id\":\"ses_1\"}",
        "linux/ubuntu/root/.local/share/opencode/auth.json" to "{\"fixture\":true}",
        "linux/ubuntu/usr/bin/opencode" to "#!/bin/sh\n",
        "linux/ubuntu.ready" to "",
        "linux/setup.json" to "{\"state\":\"done\"}",
    )

    private val appFlutterFiles = mapOf(
        "draft-attachments/ses_1/photo.png" to "PNG fixture",
        "prompt-stash/stash_1.md" to "Saved prompt text fixture",
        "saved-prompts/p1.json" to "{\"title\":\"Fixture\"}",
        "reports/usage-2026-10.csv" to "day,tokens\n1,2\n",
        "exports/session-ses_1.md" to "# Exported\n",
    )

    private fun World.seedFiles() {
        for ((path, text) in linuxFiles) File(paths.files, path).apply { parentFile.mkdirs() }.writeText(text)
        File(paths.files, "voice_models/whisper-base/model.onnx").apply { parentFile.mkdirs() }.writeBytes(model)
        File(paths.files, "tts_models/piper-en/voice.onnx").apply { parentFile.mkdirs() }.writeBytes(model)
        File(paths.files, "local_llm_models/gemma.task").apply { parentFile.mkdirs() }.writeBytes(model)
        val docs = File(paths.dataDir, "app_flutter")
        for ((path, text) in appFlutterFiles) File(docs, path).apply { parentFile.mkdirs() }.writeText(text)
        File(docs, "voice_models/whisper-tiny/model.onnx").apply { parentFile.mkdirs() }.writeBytes(model)
        File(docs, "read_aloud/kokoro/model.onnx").apply { parentFile.mkdirs() }.writeBytes(model)
    }

    /** Everything outside what the runner may write: `files/native`, `files/migration`, `shared_prefs`. */
    private fun World.kept(): Map<String, String> =
        snapshotFiles(paths.dataDir).filterKeys { path ->
            !path.startsWith("files/native/") && !path.startsWith("files/migration/") && !path.startsWith("shared_prefs/")
        }

    @Test
    fun `B-MIGR-008 B-MIGR-009 the full first run keeps the files dir and app_flutter byte for byte`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this); seedFiles() }
        val before = w.kept()
        assertTrue(before.keys.any { it.startsWith("files/linux/ubuntu/root/projects/") })
        assertTrue(before.keys.any { it.startsWith("app_flutter/") })

        assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(before, w.kept(), "nothing outside files/native, files/migration and the prefs was touched")
        // Linux is ready without a reinstall: the rootfs and its ready marker are where the native host looks.
        assertTrue(File(w.paths.files, "linux/ubuntu.ready").isFile)
        assertTrue(File(w.paths.files, "linux/ubuntu/root/projects/my-app/main.py").isFile)
        assertTrue(File(w.paths.files, "linux/ubuntu/root/.local/share/opencode/storage/session/ses_1.json").isFile)
        // Large models are the same files, never removed for a download.
        for (path in listOf("files/voice_models/whisper-base/model.onnx", "app_flutter/voice_models/whisper-tiny/model.onnx", "app_flutter/read_aloud/kokoro/model.onnx")) {
            assertTrue(File(w.paths.dataDir, path).readBytes().contentEquals(model), path)
        }
        // app_flutter stays readable: every file can be opened and read in place.
        for ((path, text) in appFlutterFiles) assertEquals(text, File(w.paths.dataDir, "app_flutter/$path").readText(), path)
    }

    @Test
    fun `B-MIGR-009 the census reads app_flutter and the files dir without walking the Linux rootfs`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this); seedFiles() }
        assertIs<RunnerState.Open>(w.runner(io()).run())
        val inventory = w.paths.inventory.readText()
        assertTrue(inventory.contains("\"app_flutter\""), "app_flutter is inventoried")
        assertTrue(inventory.contains("\"voice_models\""), "the voice models are inventoried")
        assertTrue(inventory.contains("\"linux\""), "the Linux folder is counted")
        // files/linux is counted at the top only: no rootfs path is listed.
        assertTrue(!inventory.contains("main.py") && !inventory.contains("opencode/storage"))
        assertTrue(w.paths.audit.readText().contains("\"ubuntuReady\":true"))
    }

    @Test
    fun `B-MIGR-008 B-MIGR-009 a re-run and a crash-resumed run keep the files too`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this); seedFiles() }
        val before = w.kept()
        assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:all")).run() }
        assertEquals(before, w.kept())
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertIs<RunnerState.Open>(w.runner(io()).run())
        assertEquals(before, w.kept())
    }

    @Test
    fun `B-MIGR-008 B-MIGR-009 Start fresh after a blocked migration moves only the prefs, never Linux or app_flutter`() = runTest {
        val w = World(tmp.newFolder()).apply { Seed.phone(this); seedFiles() }
        val before = w.kept()
        repeat(3) { assertFailsWith<SimulatedCrash> { w.runner(io(), CrashAt("M2:all")).run() } }
        assertIs<RunnerState.Blocked>(w.runner(io()).run())
        assertTrue(w.runner(io()).requestStartFresh())
        assertTrue(StartFreshTransaction(w.paths, { w.nowMs }).recoverBeforeOpen())
        assertIs<RunnerState.Open>(w.runner(io()).run())

        assertEquals(before, w.kept())
        assertTrue(File(w.paths.files, "linux/ubuntu.ready").isFile)
    }
}
