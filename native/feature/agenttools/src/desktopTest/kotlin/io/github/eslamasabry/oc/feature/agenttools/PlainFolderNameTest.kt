package io.github.eslamasabry.oc.feature.agenttools

import io.github.eslamasabry.oc.feature.agenttools.ui.plainFolderName
import kotlin.test.Test
import kotlin.test.assertEquals

class PlainFolderNameTest {
    @Test
    fun `B-482 root folder keeps a visible name`() {
        assertEquals("/", plainFolderName("/"))
        assertEquals("/", plainFolderName("  /  "))
        assertEquals("projects", plainFolderName("/root/projects/"))
        assertEquals("C:", plainFolderName("C:\\"))
    }
}
