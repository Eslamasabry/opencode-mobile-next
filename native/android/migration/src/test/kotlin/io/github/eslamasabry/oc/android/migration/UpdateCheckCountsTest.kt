package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.Seed.REMOTE
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * E00-S15: the M5 audit counts every store Report a problem compares with the bridge census: schedules one by one
 * (each server's key holds a `{schedules, runs, digest}` record), one folder per project and per voice pack.
 */
class UpdateCheckCountsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `B-MIGR-011 the audit counts schedules, projects and voice packs, and still holds no name`() = runTest {
        val w = World(tmp.newFolder())
        val schedules = MiniJson.encode(
            linkedMapOf(
                "schedules" to listOf(linkedMapOf("id" to "s1", "prompt" to "nightly tidy"), linkedMapOf("id" to "s2")),
                "runs" to listOf(linkedMapOf("scheduleId" to "s1")),
                "digest" to linkedMapOf<String, Any?>(),
            ),
        )
        Seed.phone(w, prefs = Seed.prefs + ("oc.scheduledPrompts.$REMOTE" to RawPref.Str(schedules)))
        for (project in listOf("app-canary", "site-canary")) File(w.paths.files, "projects/$project").mkdirs()
        File(w.paths.files, "projects/notes.txt").writeText("not a project")
        File(w.paths.files, "voice_models/whisper-base").mkdirs()

        assertIs<RunnerState.Open>(w.runner(StandardTestDispatcher(testScheduler)).run())

        val audit = MiniJson.parseOrNull(w.paths.audit.readText()) as Map<*, *>
        val data = audit["data"] as Map<*, *>
        val files = audit["files"] as Map<*, *>
        assertEquals(2L, data["scheduleItems"])
        assertEquals(2L, files["projects"])
        assertEquals(1L, files["voicePacks"])
        val text = w.paths.audit.readText()
        for (leak in listOf("nightly tidy", "whisper-base", "site-canary", "app-canary")) assertFalse(leak in text, leak)
    }
}
