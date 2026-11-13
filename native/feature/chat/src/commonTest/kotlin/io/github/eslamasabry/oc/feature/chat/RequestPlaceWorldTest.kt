package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestPlace
import io.github.eslamasabry.oc.feature.chat.world.requestPlace
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * C-010: the world handed its request cards an empty place, so a card never knew the conversation's folder and a
 * folder read showed no target. The world now gives the cards the folder the connection names for the conversation,
 * with the project's name as the header's sub-line says it ("projects · This phone").
 */
class RequestPlaceWorldTest {
    @Test
    fun `C-010 a pending request carries the conversation's folder and project name`() {
        assertEquals(RequestPlace(project = "projects", directory = "/root/projects"), requestPlace(ServerLocation("/root/projects")))
        assertEquals(RequestPlace(project = "app", directory = "/home/u/app/"), requestPlace(ServerLocation("/home/u/app/", "ws1")))
    }

    @Test
    fun `C-010 a connection that names no folder gives no place`() {
        assertEquals(RequestPlace(), requestPlace(ServerLocation()))
        assertEquals(RequestPlace(), requestPlace(ServerLocation("  ")))
    }
}
