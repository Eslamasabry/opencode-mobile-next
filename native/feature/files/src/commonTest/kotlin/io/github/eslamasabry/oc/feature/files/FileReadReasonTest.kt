package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.feature.files.presentation.FileReadReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-184: a file that will not open says why, in plain words, from the closed failure only. */
class FileReadReasonTest {
    @Test
    fun `the file's own problems are named`() {
        assertEquals(FileReadReason.TooBig, FileReadReason.of(Failure.Media(MediaProblem.FileTooBig)))
        assertEquals(FileReadReason.Missing, FileReadReason.of(Failure.Media(MediaProblem.FileMissing)))
        assertEquals(FileReadReason.Denied, FileReadReason.of(Failure.Media(MediaProblem.FileDenied)))
        assertEquals(FileReadReason.NotShowable, FileReadReason.of(Failure.Media(MediaProblem.FileNotShowable)))
    }

    @Test
    fun `an OpenCode server answer is read the same way`() {
        assertEquals(FileReadReason.Missing, FileReadReason.of(Failure.NotFound()))
        assertEquals(FileReadReason.TooBig, FileReadReason.of(Failure.Rejected(413)))
        assertEquals(FileReadReason.Denied, FileReadReason.of(Failure.Rejected(403)))
    }

    @Test
    fun `a failure that is not about the file stays a connection problem`() {
        assertNull(FileReadReason.of(null))
        assertNull(FileReadReason.of(Failure.Network(NetworkWhy.Offline)))
        assertNull(FileReadReason.of(Failure.ComputerDidNotAnswer()))
        assertNull(FileReadReason.of(Failure.Media(MediaProblem.PdfFailed)))
        assertNull(FileReadReason.of(Failure.Rejected(400)))
    }

    @Test
    fun `trying again only helps where access can change`() {
        assertTrue(FileReadReason.Denied.retryHelps)
        assertFalse(FileReadReason.TooBig.retryHelps)
        assertFalse(FileReadReason.Missing.retryHelps)
        assertFalse(FileReadReason.NotShowable.retryHelps)
    }
}
