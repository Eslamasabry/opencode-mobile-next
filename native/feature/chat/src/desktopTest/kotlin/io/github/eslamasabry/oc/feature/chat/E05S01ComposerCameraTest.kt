package io.github.eslamasabry.oc.feature.chat

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.isCameraRefusal
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** E05-S01: Take photo names its conversation, and a camera refusal offers Open settings. */
class E05S01ComposerCameraTest {
    private class Worlds(private val world: ChatWorld) : ChatWorlds {
        override fun open(ref: ConversationRef): ChatWorld = world

        override fun release(world: ChatWorld) {}
    }

    private class CameraSource(var answer: Outcome<List<PromptAttachment>>) : ComposerAttachSource {
        override val kinds = setOf(AttachKind.Camera)
        val asked = mutableListOf<ConversationRef?>()
        var settingsOpened = 0

        override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>) = answer

        override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>, conversation: ConversationRef?) =
            answer.also { asked += conversation }

        override fun openSettings(kind: AttachKind) {
            if (kind == AttachKind.Camera) settingsOpened++
        }
    }

    private val photo = PromptAttachment("image/jpeg", "photo-1.jpg", "data:image/jpeg;base64,/9j/")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    /** B-STATE-088 the camera is told which conversation's draft a recovered photo belongs to. */
    @Test
    fun `B-STATE-088 Take photo names the conversation and attaches the photo`() = runTest {
        val world = FakeChatWorld()
        val source = CameraSource(Outcome.Ok(listOf(photo)))
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet(), setOf(source))
        vm.state.test {
            var item = awaitItem()
            while (AttachKind.Camera !in item.attachKinds) item = awaitItem()
            vm.attach(AttachKind.Camera)
            while (item.draft.attachments.isEmpty()) item = awaitItem()
            assertEquals(listOf("photo-1.jpg"), item.draft.attachments.map { it.filename })
            assertEquals(listOf<ConversationRef?>(ConversationRef(ProfileId("p1"), SessionId("s1"))), source.asked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** B-MEDIA-008 a camera refusal is explained, offers Open settings, and the tap opens them. */
    @Test
    fun `B-MEDIA-008 a refused camera offers Open settings`() = runTest {
        val world = FakeChatWorld()
        val source = CameraSource(Outcome.Failed(Failure.Media(MediaProblem.CameraDeniedForever)))
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet(), setOf(source))
        vm.state.test {
            var item = awaitItem()
            while (AttachKind.Camera !in item.attachKinds) item = awaitItem()
            vm.attach(AttachKind.Camera)
            while (item.attachmentFailure == null) item = awaitItem()
            assertTrue(assertIs<Failure.Media>(item.attachmentFailure).isCameraRefusal())
            vm.openCameraSettings()
            while (item.attachmentFailure != null) item = awaitItem()
            assertNull(item.attachmentFailure, "the notice closes once settings open")
            assertEquals(1, source.settingsOpened)
            assertTrue(world.draft.value.attachments.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-MEDIA-008 only camera refusals swap Retry for Open settings`() {
        assertTrue(Failure.Media(MediaProblem.CameraDenied).isCameraRefusal())
        assertTrue(Failure.Media(MediaProblem.CameraDeniedForever).isCameraRefusal())
        assertFalse(Failure.Media(MediaProblem.PhotoTooLarge).isCameraRefusal())
        assertFalse(Failure.Device().isCameraRefusal())
    }
}
