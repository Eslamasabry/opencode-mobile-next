package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.ui.canOpenArtifact
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact
import io.github.eslamasabry.oc.feature.chat.world.isInlineFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** C7: a file with inline content is offered Attach and Save instead of being a dead button. */
class ToolFileTest {
    @Test
    fun `inline content counts as a file the person can act on`() {
        assertTrue(isInlineFile("data:text/plain;base64,aGk="))
        assertFalse(isInlineFile("https://example.com/a.txt"))
        assertFalse(isInlineFile(null))
        assertTrue(canOpenArtifact(ToolArtifact(null, "data:text/plain;base64,aGk=", "text/plain", "a.txt")))
        assertFalse(canOpenArtifact(ToolArtifact(null, "ftp://x/y", null, null)))
    }
}
