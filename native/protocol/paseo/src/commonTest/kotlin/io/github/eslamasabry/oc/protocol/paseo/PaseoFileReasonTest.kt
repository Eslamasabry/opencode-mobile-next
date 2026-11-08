package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-184: a daemon's file refusal reads as the file's problem, not as "couldn't reach your computer". */
class PaseoFileReasonTest {
    @Test
    fun `the daemon's own words are sorted`() {
        assertEquals(PaseoFailureKind.FileTooBig, paseoFileReason("File is too large to display"))
        assertEquals(PaseoFailureKind.FileTooBig, paseoFileReason("file exceeds max size"))
        assertEquals(PaseoFailureKind.FileMissing, paseoFileReason("ENOENT: no such file or directory"))
        assertEquals(PaseoFailureKind.FileDenied, paseoFileReason("EACCES: permission denied"))
        assertEquals(PaseoFailureKind.FileDenied, paseoFileReason("path is outside the workspace"))
        assertEquals(PaseoFailureKind.FileNotShowable, paseoFileReason("Cannot display binary file"))
    }

    @Test
    fun `other words and no words stay unsorted`() {
        assertNull(paseoFileReason(null))
        assertNull(paseoFileReason("socket hang up"))
        assertNull(paseoFileReason("too large"), "a size word alone is not about a file")
    }

    @Test
    fun `each kind reaches the person as the file's own problem`() {
        fun media(kind: PaseoFailureKind) = (PaseoException(kind).toFailure() as Failure.Media).problem
        assertEquals(MediaProblem.FileTooBig, media(PaseoFailureKind.FileTooBig))
        assertEquals(MediaProblem.FileMissing, media(PaseoFailureKind.FileMissing))
        assertEquals(MediaProblem.FileDenied, media(PaseoFailureKind.FileDenied))
        assertEquals(MediaProblem.FileNotShowable, media(PaseoFailureKind.FileNotShowable))
    }
}
