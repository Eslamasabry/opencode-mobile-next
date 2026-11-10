package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareInTest {
    private val files = FakeFiles()
    private val photos = FakePhotoStore("alpha")
    private val codec = FakeCodec(files)

    private fun TestScope.intake() =
        AttachmentIntake(
            FakeActivity(), FakePermissions(), FakeReader(files), codec, FakeClipboard(), FakeCaptures(files), photos,
            TestWords, TestDispatchers(StandardTestDispatcher(testScheduler)), FixedClock(1_000),
        )

    private fun held(n: Int): List<PromptAttachment> =
        List(n) { PromptAttachment("text/plain", "f$it.txt", DataUrl.encode("text/plain", ByteArray(10) { 65 })) }

    private fun picture(i: Int, name: String = "p$i.png") = SharedInput.Ready(name, "image/png", png(i))

    private fun text(i: Int) = SharedInput.Ready("n$i.txt", "text/plain", "note $i".encodeToByteArray())

    @Test
    fun `B-ANDR-076 one shared picture is added with its name and nothing else`() =
        runTest {
            val result = intake().fromShared(emptyList(), listOf(picture(1, "holiday.png")))
            assertEquals(listOf("holiday.png"), result.added.map { it.filename })
            assertEquals("image/png", result.added.single().mime)
            assertTrue(result.complete)
            assertNull(result.problem)
        }

    @Test
    fun `B-ANDR-076 pictures and files mixed arrive together in order`() =
        runTest {
            val result = intake().fromShared(emptyList(), listOf(picture(1), text(2), picture(3)))
            assertEquals(listOf("p1.png", "n2.txt", "p3.png"), result.added.map { it.filename })
            assertEquals(3, result.total)
            assertTrue(result.complete)
        }

    @Test
    fun `B-ANDR-076 over the composer limit the extra items are left out and the run says so`() =
        runTest {
            val result = intake().fromShared(held(3), listOf(picture(1), picture(2), picture(3), picture(4)))
            assertEquals(2, result.added.size)
            assertEquals(4, result.total)
            assertEquals(ShareInProblem.OverLimit, result.problem)
            assertFalse(result.complete)
        }

    @Test
    fun `B-ANDR-076 a full draft adds nothing and says over the limit`() =
        runTest {
            val result = intake().fromShared(held(5), listOf(text(1)))
            assertEquals(0, result.added.size)
            assertEquals(ShareInProblem.OverLimit, result.problem)
        }

    @Test
    fun `B-ANDR-076 a refused item is skipped with its reason while the others are added`() =
        runTest {
            val result =
                intake().fromShared(
                    emptyList(),
                    listOf(SharedInput.Refused(ShareInProblem.TooLarge), picture(1), SharedInput.Refused(ShareInProblem.Unreadable)),
                )
            assertEquals(listOf("p1.png"), result.added.map { it.filename })
            assertEquals(ShareInProblem.TooLarge, result.problem, "the first problem is the one told")
            assertEquals(3, result.total)
        }

    @Test
    fun `B-ANDR-076 an oversize file and an unsupported file are told apart`() =
        runTest {
            val big = SharedInput.Ready("big.txt", "text/plain", ByteArray(AttachmentLimits.MAX_BYTES.toInt() + 1))
            assertEquals(ShareInProblem.TooLarge, intake().fromShared(emptyList(), listOf(big)).problem)
            val odd = SharedInput.Ready("a.exe", "application/x-msdownload", byteArrayOf(0x4D, 0x5A, 0, 1))
            assertEquals(ShareInProblem.Unsupported, intake().fromShared(emptyList(), listOf(odd)).problem)
        }

    @Test
    fun `B-ANDR-076 problems map from the composer failures`() {
        assertEquals(ShareInProblem.OverLimit, ShareInProblem.of(Failure.Media(MediaProblem.DraftFull)))
        assertEquals(ShareInProblem.TooLarge, ShareInProblem.of(Failure.Media(MediaProblem.AttachmentsTotalTooLarge)))
        assertEquals(ShareInProblem.Unsupported, ShareInProblem.of(Failure.Media(MediaProblem.AttachmentUnsupported)))
        assertEquals(ShareInProblem.Unreadable, ShareInProblem.of(Failure.Media(MediaProblem.PhotoUnavailable)))
    }

    @Test
    fun `B-ANDR-076 the input never prints its name or bytes`() {
        assertEquals("Ready(bytes=3)", SharedInput.Ready("secret-name.png", "image/png", ByteArray(3)).toString())
    }

    @Test
    fun `B-ANDR-076 the event facts are a range and a kind only`() {
        assertEquals(listOf("0", "1", "2-5", "2-5", "6-20", ">20"), listOf(0, 1, 2, 5, 6, 21).map(ShareInFacts::countRange))
        assertTrue(ShareInFacts.picturesOnly(listOf(picture(1), picture(2))))
        assertFalse(ShareInFacts.picturesOnly(listOf(picture(1), text(2))))
        assertFalse(ShareInFacts.picturesOnly(listOf(SharedInput.Refused(ShareInProblem.TooLarge))))
    }

    @Test
    fun `B-ANDR-076 shared files join what the draft holds and a retry does not add them twice`() =
        runTest {
            val have = held(1)
            val result = intake().fromShared(have, listOf(picture(1), text(2)))
            val next = result.draftWith(have)!!
            assertEquals(listOf("f0.txt", "p1.png", "n2.txt"), next.map { it.filename })
            assertNull(result.draftWith(next), "already saved: nothing to write")
            assertNull(intake().fromShared(have, emptyList()).draftWith(have), "nothing added: nothing to write")
        }
}
