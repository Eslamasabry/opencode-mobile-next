package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import kotlin.test.Test

class ScrollAnchorHybridTest {
    @Test
    fun `STEPS-SCROLL-001 hybrid following keeps newest line on message box while steps arrive`() {
        ScrollAnchorWalks.walk001Following(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-002 hybrid scrolled up 40 dp no line in view moves while steps arrive and turn ends`() {
        ScrollAnchorWalks.walk002ScrolledUp40(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-003 hybrid scrolled up a whole screen no line in view moves`() {
        ScrollAnchorWalks.walk003ScrolledUpScreen(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-004 hybrid tap opening group at newest line keeps view still and second tap closes`() {
        ScrollAnchorWalks.walk004TapGroup(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-005 hybrid 002 and 004 at 840x800 and 002 right to left`() {
        ScrollAnchorWalks.walk005WideAndRtl(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-006 hybrid only the person brings following back`() {
        ScrollAnchorWalks.walk006OnlyPersonResumesFollow(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-007 hybrid screen reader scroll leaves following and comes back`() {
        ScrollAnchorWalks.walk007ScreenReaderScrollBy(TranscriptAnchor.Hybrid)
    }

    @Test
    fun `STEPS-SCROLL-008 hybrid screen reader scroll to a row leaves following and comes back`() {
        ScrollAnchorWalks.walk008ScreenReaderScrollToIndex(TranscriptAnchor.Hybrid)
    }
}
