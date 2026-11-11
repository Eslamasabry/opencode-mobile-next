package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.projectPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** W4: the project sheet names the folders a project sits in, never a clipped path or the phone's own /root. */
class ProjectPlaceTest {
    @Test
    fun `W4 a project straight in the phone's home has no place line`() {
        assertNull(projectPlace("/root/projects"))
        assertNull(projectPlace("/root"))
    }

    @Test
    fun `W4 a project inside projects says projects, without the phone's root`() {
        assertEquals("projects", projectPlace("/root/projects/second"))
    }

    @Test
    fun `W4 a deep path keeps its last two folders`() {
        assertEquals("…/b/c", projectPlace("/srv/a/b/c/app"))
        assertEquals("work/clients", projectPlace("/work/clients/app"))
    }
}
