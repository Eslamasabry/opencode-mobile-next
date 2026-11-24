package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import kotlin.test.Test

class ScrollAnchorNormalOrderTest {
    @Test
    fun `STEPS-SCROLL-001 normal order following keeps newest line on message box while steps arrive`() {
        ScrollAnchorWalks.walk001Following(TranscriptAnchor.NormalOrder)
    }

    @Test
    fun `STEPS-SCROLL-002 normal order scrolled up 40 dp no line in view moves while steps arrive and turn ends`() {
        ScrollAnchorWalks.walk002ScrolledUp40(TranscriptAnchor.NormalOrder)
    }

    @Test
    fun `STEPS-SCROLL-003 normal order scrolled up a whole screen no line in view moves`() {
        ScrollAnchorWalks.walk003ScrolledUpScreen(TranscriptAnchor.NormalOrder)
    }

    @Test
    fun `STEPS-SCROLL-004 normal order tap opening group at newest line keeps view still and second tap closes`() {
        ScrollAnchorWalks.walk004TapGroup(TranscriptAnchor.NormalOrder)
    }

    @Test
    fun `STEPS-SCROLL-005 normal order 002 and 004 at 840x800 and 002 right to left`() {
        ScrollAnchorWalks.walk005WideAndRtl(TranscriptAnchor.NormalOrder)
    }

    @Test
    fun `STEPS-SCROLL-006 normal order only the person brings following back`() {
        ScrollAnchorWalks.walk006OnlyPersonResumesFollow(TranscriptAnchor.NormalOrder)
    }
}
