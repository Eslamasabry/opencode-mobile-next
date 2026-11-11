package io.github.eslamasabry.oc.engine.runtime.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A failed install step names its real cause: the network when names did not resolve, never a guess. */
class SetupFailureCauseTest {
    @Test
    fun `an install step whose names did not resolve is a network problem`() {
        val log =
            "Err:1 http://archive.ubuntu.com/ubuntu noble InRelease\n" +
                "  Temporary failure resolving 'archive.ubuntu.com'\n" +
                "E: Unable to locate package git\n" +
                "[oc] The packages could not be installed. Check your connection and retry setup.\n"
        val (row, job) =
            SetupPlanning.describeFailure(
                SetupJobComponent(id = "essentials", state = "failed", error = "app_install_failed"),
                log,
                "Git and SSH",
            )
        assertIs<SetupError.Offline>(row)
        assertEquals(SetupError.NoInternet, job)
    }

    @Test
    fun `an install step that failed without a lookup failure stays an install problem`() {
        val (_, job) =
            SetupPlanning.describeFailure(
                SetupJobComponent(id = "essentials", state = "failed", error = "app_install_failed"),
                "dpkg: error processing package git\n",
                "Git and SSH",
            )
        assertIs<SetupError.Install>(job)
    }
}
