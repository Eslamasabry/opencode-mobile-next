package io.github.eslamasabry.oc.feature.servers.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LookAlikeNamesTest {
    @Test
    fun `B-SRV-ROW-001 names that read the same are flagged, distinct names are not`() {
        val names = listOf("Computer", "computer ", "Laptop", "Work-PC", "work pc")
        assertEquals(setOf("computer", "workpc"), lookAlikeNames(names))
        assertEquals(emptySet(), lookAlikeNames(listOf("Computer", "Laptop")))
    }

    @Test
    fun `B-SRV-ROW-001 the told-apart address drops scheme, sign-in part, query and trailing slash`() {
        assertEquals("192.168.1.4:4096", plainAddress("http://192.168.1.4:4096/"))
        assertEquals("box.example.com/oc", plainAddress("https://user:pw@box.example.com/oc/?token=x#y"))
    }
}
