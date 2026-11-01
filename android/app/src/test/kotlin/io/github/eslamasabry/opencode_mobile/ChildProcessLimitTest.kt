package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChildProcessLimitTest {
    @Test fun falseMeansRestrictionsAreOff() = assertEquals(true, ChildProcessLimit.restrictionsOffFrom("false\n"))
    @Test fun trueMeansRestrictionsApply() = assertEquals(false, ChildProcessLimit.restrictionsOffFrom(" TRUE "))
    @Test fun emptyOrUnknownIsUnknown() {
        assertNull(ChildProcessLimit.restrictionsOffFrom(null))
        assertNull(ChildProcessLimit.restrictionsOffFrom("  "))
        assertNull(ChildProcessLimit.restrictionsOffFrom("maybe"))
    }
}
