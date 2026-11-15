package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** B-332: the copied report's build facts come from the build and the platform's package answers, nothing else. */
class ReportBuildTest {
    private val build = AppBuildInfo("2.2.0", 10042, Channel.Preview, debuggable = false)

    @Test
    fun `B-332 the version, install and update times and the Android version reach the report`() {
        val facts = reportBuild(build, 1_759_305_600_000, 1_763_105_400_000, "16", 36)
        assertEquals("2.2.0", facts.versionName)
        assertEquals(10042L, facts.versionCode)
        assertEquals(Instant.parse("2025-10-01T08:00:00Z"), facts.installedAt)
        assertEquals(Instant.parse("2025-11-14T07:30:00Z"), facts.updatedAt)
        assertEquals("16", facts.androidVersion)
        assertEquals(36, facts.apiLevel)
    }

    @Test
    fun `B-332 an unanswered package or platform fact is unknown, never a 1970 date`() {
        val facts = reportBuild(build, null, 0, " ", 0)
        assertNull(facts.installedAt)
        assertNull(facts.updatedAt)
        assertNull(facts.androidVersion)
        assertNull(facts.apiLevel)
        assertEquals("2.2.0", facts.versionName)
    }
}
