package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.ui.shouldSmoothFollow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-530 follow-scroll policy:
 * - Own pending prompt lift (owner decision #25) and live streaming reply chunks/footer keep
 *   index 0 in view immediately (`smoothFollow == false`).
 * - Incoming turns (rapid arrivals / completed turns) use smooth follow-scroll (`smoothFollow == true`).
 */
class SmoothFollowPolicyTest {
    @Test
    fun `B-530 smooth follow runs for incoming turns, while own prompt lift and live stream stay glued`() {
        assertTrue(shouldSmoothFollow(hasPending = false, streamingTail = false, sameStreamingTurn = false))
        assertFalse(shouldSmoothFollow(hasPending = true, streamingTail = false, sameStreamingTurn = false))
        assertFalse(shouldSmoothFollow(hasPending = false, streamingTail = true, sameStreamingTurn = false))
        assertFalse(shouldSmoothFollow(hasPending = false, streamingTail = false, sameStreamingTurn = true))
    }
}
