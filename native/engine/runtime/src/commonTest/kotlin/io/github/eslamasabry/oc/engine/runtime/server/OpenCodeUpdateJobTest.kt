package io.github.eslamasabry.oc.engine.runtime.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCodeUpdateJobTest {
    @Test
    fun `the job the update starts is the update, while it starts and after`() {
        val update = OpenCodeUpdateJob()
        update.starting(shownJob = "setup-1")
        assertTrue(update.owns("setup-2"), "its first progress arrives before the ask returns")
        update.started(shownJob = "setup-2")
        assertTrue(update.owns("setup-2"))
    }

    @Test
    fun `the job that was there before, Repair and Add tools are not the update`() {
        val update = OpenCodeUpdateJob()
        assertFalse(update.owns("setup-1"))
        update.starting(shownJob = "setup-1")
        assertFalse(update.owns("setup-1"))
        update.started(shownJob = "setup-2")
        assertFalse(update.owns("setup-1"))
        assertFalse(update.owns("setup-3"), "a later job of another kind")
        assertFalse(update.owns(null))
    }

    @Test
    fun `a refused update owns nothing and keeps the last update it ran`() {
        val update = OpenCodeUpdateJob()
        update.starting(shownJob = "setup-1")
        update.started(shownJob = "setup-1")
        assertFalse(update.owns("setup-1"))
        update.starting(shownJob = "setup-1")
        update.started(shownJob = "setup-2")
        update.starting(shownJob = "setup-2")
        update.started(shownJob = "setup-2")
        assertTrue(update.owns("setup-2"), "the failed update still shows on the row after a refused retry")
    }
}
