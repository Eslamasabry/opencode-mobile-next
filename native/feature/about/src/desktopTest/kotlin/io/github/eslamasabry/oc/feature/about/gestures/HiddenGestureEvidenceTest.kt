package io.github.eslamasabry.oc.feature.about.gestures

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Each ledger row's evidence is a real place in the native code (test/gesture_audit_test.dart:72). */
class HiddenGestureEvidenceTest {
    /** The repository root: the folder that holds `native/settings.gradle.kts`, above the module's working dir. */
    private fun repoRoot(): File? =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "native/settings.gradle.kts").isFile }

    @Test
    fun `B-NAV-019 every evidence place exists and is inside its file`() {
        val root = assertNotNull(repoRoot(), "run from inside the repository")
        val missing =
            HiddenGestures.all.mapNotNull { row ->
                val match = HiddenGestures.evidencePattern.matchEntire(row.evidence) ?: return@mapNotNull "${row.element}: bad evidence"
                val file = File(root, row.evidence.substringBeforeLast(':'))
                when {
                    !file.isFile -> "${row.element}: ${file.path} does not exist"
                    match.groupValues[1].toInt() > file.readLines().size -> "${row.element}: ${row.evidence} is past the end of the file"
                    else -> null
                }
            }
        assertEquals(emptyList(), missing)
    }
}
