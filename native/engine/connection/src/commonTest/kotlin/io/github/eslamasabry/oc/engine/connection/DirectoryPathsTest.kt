package io.github.eslamasabry.oc.engine.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of `test/connection_location_restore_test.dart:248` (row B-STATE-011). */
class DirectoryPathsTest {
    @Test
    fun `B-STATE-011 directory paths compare equal regardless of trailing separators`() {
        assertEquals("/work/acme", DirectoryPaths.normalize("/work/acme/"))
        assertEquals("/work/acme", DirectoryPaths.normalize("/work/acme//"))
        assertEquals("/", DirectoryPaths.normalize("/"))
        assertEquals("C:\\", DirectoryPaths.normalize("C:\\"))
        assertEquals("/Work/Acme", DirectoryPaths.normalize("/Work/Acme"))
        assertTrue(DirectoryPaths.same("/work/acme/", "/work/acme"))
        assertFalse(DirectoryPaths.same("/work/acme", "/work/Acme"))

        val worktrees = listOf("/work/acme-wt")
        assertTrue(DirectoryPaths.covers("/work/acme/", worktrees, "/work/acme"))
        assertTrue(DirectoryPaths.covers("/work/acme/", worktrees, "/work/acme/lib/"))
        assertTrue(DirectoryPaths.covers("/work/acme/", worktrees, "/work/acme-wt/"))
        assertFalse(DirectoryPaths.covers("/work/acme/", worktrees, "/work/other"))
        assertFalse(DirectoryPaths.covers("/", emptyList(), "/work/acme"), "the catch-all root covers nothing")
    }
}
