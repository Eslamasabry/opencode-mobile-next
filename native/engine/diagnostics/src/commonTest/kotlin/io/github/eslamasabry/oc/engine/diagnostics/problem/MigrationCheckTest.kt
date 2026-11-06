package io.github.eslamasabry.oc.engine.diagnostics.problem

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E00-S15: after the update, Report a problem shows the migration's counts (and the bridge census when the old app
 * left one), so the owner can see that nothing went missing. Counts only.
 */
class MigrationCheckTest {
    /** The shape `:android:migration` M5 writes, with a server name and a secret name planted where no count is. */
    private val audit =
        """
        {"v":1,
         "prefs":{"bytes":4096,"keys":40},
         "profiles":{"count":3,"invalidIds":0},
         "secrets":{"copied":{"Password":2,"Token":1},"flagged":{"Password.AuthFailed":1},"outcomes":{"pw.1735000000000001":"copied"}},
         "data":{"queueEntries":2,"drafts":4,"scheduleItems":5,"schedules":2,"name":"Work laptop"},
         "files":{"projects":2,"voicePacks":1,"voiceModelBytes":123456},
         "deferred":["AiTeam"]}
        """.trimIndent()

    private fun census(vararg counts: Pair<String, Any>): String =
        """{"v":1,"counts":{${counts.joinToString(",") { (k, v) -> "\"$k\":${if (v is String) "\"$v\"" else v}" }}}}"""

    @Test
    fun `B-MIGR-011 the update check lists every store as a count, sign-ins with the ones to enter again`() {
        val check = assertNotNull(MigrationCheck.of(audit, null))

        assertEquals(
            listOf(
                "Update check (counts only)",
                "Servers: 3",
                "Saved sign-ins: 4 (1 to enter again)",
                "Drafts: 4",
                "Queued prompts: 2",
                "Schedules: 5",
                "Projects: 2",
                "Voice packs: 1",
            ),
            check.text.lines(),
        )
        assertTrue(check.mismatches.isEmpty())
    }

    @Test
    fun `B-MIGR-011 compared with the bridge census, a store that differs is flagged by name and the rest are not`() {
        val before = census(
            "servers" to 3, "signIns" to 4, "drafts" to 5, "queue" to 2, "schedules" to 5, "projects" to 3, "voicePacks" to 1,
        )
        val check = assertNotNull(MigrationCheck.of(audit, before))

        assertEquals(listOf(MigrationStore.Drafts, MigrationStore.Projects), check.mismatches)
        val lines = check.text.lines()
        assertEquals(MigrationCheck.HEADER_WITH_CENSUS, lines.first())
        assertTrue("⚠ Mismatch: Drafts: 4 · before the update: 5" in lines, lines.toString())
        assertTrue("⚠ Mismatch: Projects: 2 · before the update: 3" in lines)
        assertTrue("Servers: 3 · before the update: 3" in lines)
        assertEquals(2, lines.count { it.startsWith(MigrationCheck.MISMATCH) })
    }

    @Test
    fun `B-MIGR-011 a store only one side counted is shown as not counted, and only a census count can mismatch`() {
        val oldAudit = """{"profiles":{"count":1},"data":{"drafts":0}}"""
        val check = assertNotNull(MigrationCheck.of(oldAudit, census("servers" to 1, "voicePacks" to 2)))
        val lines = check.text.lines()
        assertTrue("⚠ Mismatch: Voice packs: not counted · before the update: 2" in lines, lines.toString())
        assertTrue("Projects: not counted · before the update: not counted" in lines)
        assertEquals(listOf(MigrationStore.VoicePacks), check.mismatches)
    }

    @Test
    fun `B-MIGR-011 no value, path or name from either file reaches the text, and bad counts are ignored`() {
        val hostile = census("servers" to "Work laptop", "drafts" to -1, "queue" to 2.5, "projects" to "/data/user/0/x")
        val text = assertNotNull(MigrationCheck.of(audit, hostile)).text
        for (leak in listOf("Work laptop", "pw.", "1735000000000001", "/data", "123456", "4096", "AiTeam", "-1", "2.5")) {
            assertFalse(leak in text, "$leak in $text")
        }
        assertTrue("Servers: 3 · before the update: not counted" in text.lines())
    }

    @Test
    fun `B-MIGR-011 no audit and no census, or unreadable files, add no section to the report`() {
        assertNull(MigrationCheck.of(null, null))
        assertNull(MigrationCheck.of("{not json", "[]"))
        val report = ProblemReport.build(Redactor(), "it broke", "3.0.0+10001", "Android")
        assertFalse(MigrationCheck.HEADER in report.text)
    }

    @Test
    fun `B-MIGR-011 Report a problem carries the update check first in its diagnostics`() {
        val check = MigrationCheck.of(audit, census("servers" to 2))
        val report = ProblemReport.build(Redactor(), "after the update", "3.0.0+10001", "Android", update = check)

        assertTrue(report.diagnostics.startsWith(MigrationCheck.HEADER_WITH_CENSUS), report.diagnostics)
        assertTrue("⚠ Mismatch: Servers: 3 · before the update: 2" in report.text)
    }
}
