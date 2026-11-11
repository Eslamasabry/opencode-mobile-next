package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandoffFolderRuleTest {
    @Test
    fun `a conversation on the phone in a phone project may continue`() {
        assertNull(HandoffFolderRule.judge(true, "/root/projects/app"))
        assertNull(HandoffFolderRule.judge(true, "/root/projects"))
    }

    @Test
    fun `a conversation on a computer never starts an agent on the phone`() {
        assertEquals(
            HandoffBlock.SourceOnComputer,
            HandoffFolderRule.judge(false, "/home/me/app"),
        )
        // Even when the computer's path happens to look like a phone path: the machine decides, not the string.
        assertEquals(
            HandoffBlock.SourceOnComputer,
            HandoffFolderRule.judge(false, "/root/projects/app"),
        )
    }

    @Test
    fun `a phone folder outside the projects folder is refused with its own reason`() {
        assertEquals(HandoffBlock.FolderNotOnPhone, HandoffFolderRule.judge(true, "/root/other"))
        assertEquals(HandoffBlock.FolderNotOnPhone, HandoffFolderRule.judge(true, "/root/projects2/x"))
        assertEquals(HandoffBlock.FolderNotOnPhone, HandoffFolderRule.judge(true, "/root/projects/../etc"))
    }

    @Test
    fun `no reported folder is its own reason on either machine`() {
        assertEquals(HandoffBlock.FolderUnknown, HandoffFolderRule.judge(true, null))
        assertEquals(HandoffBlock.FolderUnknown, HandoffFolderRule.judge(false, " "))
    }

    @Test
    fun `only the phone itself counts as a phone endpoint`() {
        assertTrue(HandoffFolderRule.isPhoneEndpoint("http", "127.0.0.1"))
        assertTrue(HandoffFolderRule.isPhoneEndpoint("https", "localhost"))
        assertFalse(HandoffFolderRule.isPhoneEndpoint("http", "192.168.1.5"))
        assertFalse(HandoffFolderRule.isPhoneEndpoint("ws", "127.0.0.1"))
    }

    @Test
    fun `a project path with control characters is not a phone project`() {
        assertFalse(HandoffFolderRule.isPhoneProject("/root/projects/a\nb"))
    }

    @Test
    fun `reasons survive the trip through a failure and unknown failures are not reasons`() {
        for (block in HandoffBlock.entries) assertEquals(block, HandoffBlock.of(block.failure()))
        assertEquals(
            HandoffBlock.NotConfirmed,
            HandoffBlock.of(Failure.Unavailable(FeatureId("sharedProject"))),
        )
        assertNull(HandoffBlock.of(Failure.Unavailable(FeatureId("agentHandoffFiles"))))
        assertNull(HandoffBlock.of(null))
    }
}
