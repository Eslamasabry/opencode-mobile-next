package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LookAlikeNamesTest {
    @Test
    fun `B-SRV-ROW-001 names that read the same are flagged, distinct names are not`() {
        val names = listOf("Computer", "computer ", "Laptop", "Work-PC", "work pc")
        assertEquals(setOf("computer", "workpc"), lookAlikeNames(names))
        assertEquals(emptySet(), lookAlikeNames(listOf("Computer", "Laptop")))
    }

    @Test
    fun `P-019 look-alike names are told apart by their number in saved order, never by address`() {
        val shown =
            distinctServerNames(
                listOf("a" to "My computer", "b" to "Laptop", "c" to "my computer ", "d" to "My computer")
            )
        assertEquals(
            mapOf("a" to "My computer (1)", "b" to "Laptop", "c" to "my computer (2)", "d" to "My computer (3)"),
            shown,
        )
        assertEquals(mapOf("a" to "Laptop"), distinctServerNames(listOf("a" to "Laptop")))
    }

    @Test
    fun `P-019 the kind is said only when the list holds more than one kind`() {
        assertFalse(kindsDiffer(List(4) { BackendKind.OpenCode2 }))
        assertFalse(kindsDiffer(emptyList()))
        assertTrue(kindsDiffer(listOf(BackendKind.OpenCode2, BackendKind.Codex)))
    }

    @Test
    fun `P-019 a line without its kind ends on its last word`() {
        assertEquals("Not connected", withoutTrailingSeparator(AnnotatedString("Not connected · ")).text)
        assertEquals("Not connected · Codex", withoutTrailingSeparator(AnnotatedString("Not connected · Codex")).text)
        assertEquals("", withoutTrailingSeparator(AnnotatedString("")).text)
    }
}
