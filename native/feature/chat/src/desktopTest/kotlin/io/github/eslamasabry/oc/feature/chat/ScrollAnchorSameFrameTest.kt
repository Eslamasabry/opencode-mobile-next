package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import kotlin.test.Test

class ScrollAnchorSameFrameTest {
    @Test
    fun `STEPS-SCROLL-001 same frame following keeps newest line on message box while steps arrive`() {
        ScrollAnchorWalks.walk001Following(TranscriptAnchor.SameFrame)
    }

    @Test
    fun `STEPS-SCROLL-002 same frame scrolled up 40 dp no line in view moves while steps arrive and turn ends`() {
        ScrollAnchorWalks.walk002ScrolledUp40(TranscriptAnchor.SameFrame)
    }

    @Test
    fun `STEPS-SCROLL-003 same frame scrolled up a whole screen no line in view moves`() {
        ScrollAnchorWalks.walk003ScrolledUpScreen(TranscriptAnchor.SameFrame)
    }

    @Test
    fun `STEPS-SCROLL-004 same frame tap opening group at newest line keeps view still and second tap closes`() {
        ScrollAnchorWalks.walk004TapGroup(TranscriptAnchor.SameFrame)
    }

    @Test
    fun `STEPS-SCROLL-005 same frame 002 and 004 at 840x800 and 002 right to left`() {
        ScrollAnchorWalks.walk005WideAndRtl(TranscriptAnchor.SameFrame)
    }

    @Test
    fun `STEPS-SCROLL-006 same frame only the person brings following back`() {
        ScrollAnchorWalks.walk006OnlyPersonResumesFollow(TranscriptAnchor.SameFrame)
    }
}
