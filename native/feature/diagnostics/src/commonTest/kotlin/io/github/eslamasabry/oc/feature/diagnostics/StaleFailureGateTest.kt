package io.github.eslamasabry.oc.feature.diagnostics

import io.github.eslamasabry.oc.engine.updates.AppRelease
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.feature.diagnostics.presentation.FailureVersionCheck
import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportVersions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureGate
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** E19-S03: Report this failure on a failure an older version saved offers the update first. */
class StaleFailureGateTest {
    private class Versions(var running: String?, var latest: String?) : ReportVersions {
        var opened = 0

        override fun running(): String? = running

        override fun latestRelease(): String? = latest

        override fun openUpdate() {
            opened++
        }
    }

    @Test
    fun `E19-S03 an older failure with a newer release names both and offers the update`() {
        assertEquals(
            StaleFailureUi(happenedOn = "2.1.1", fixedIn = "3.0.0", canUpdate = true),
            FailureVersionCheck.stale(saved = "2.1.1+59", running = "2.1.4", latest = "3.0.0"),
        )
    }

    @Test
    fun `E19-S03 an older failure on an already newer app names the running version without Update first`() {
        assertEquals(
            StaleFailureUi(happenedOn = "2.1.1", fixedIn = "2.1.4", canUpdate = false),
            FailureVersionCheck.stale(saved = "2.1.1", running = "2.1.4", latest = "2.1.4"),
        )
    }

    @Test
    fun `E19-S03 the same version reports as today`() {
        assertNull(FailureVersionCheck.stale(saved = "2.1.4", running = "2.1.4", latest = null))
        assertNull(FailureVersionCheck.stale(saved = "v2.1.4+62", running = "2.1.4", latest = "2.1.4"))
    }

    @Test
    fun `E19-S03 an unknown version reports as today`() {
        assertNull(FailureVersionCheck.stale(saved = null, running = "2.1.4", latest = "3.0.0"))
        assertNull(FailureVersionCheck.stale(saved = "dev build", running = "2.1.4", latest = "3.0.0"))
        assertNull(FailureVersionCheck.stale(saved = "2.1.1", running = null, latest = null))
    }

    @Test
    fun `E19-S03 a newer build of the same version counts as newer only when both builds are known`() {
        assertEquals(
            StaleFailureUi("2.1.4", "2.1.4", canUpdate = false),
            FailureVersionCheck.stale(saved = "2.1.4+61", running = "2.1.4+62", latest = null),
        )
        assertNull(FailureVersionCheck.stale(saved = "2.1.4+61", running = "2.1.4", latest = null))
    }

    @Test
    fun `E19-S03 versions compare by number not by text`() {
        assertEquals(
            "2.10.0",
            FailureVersionCheck.stale(saved = "2.9.0", running = "2.9.0", latest = "2.10.0")?.fixedIn,
        )
        assertNull(FailureVersionCheck.stale(saved = "2.10.0", running = "2.9.0", latest = "2.9.0"))
    }

    @Test
    fun `E19-S03 same or unknown version opens the report straight away`() {
        val gate = StaleFailureGate(Versions(running = "2.1.4", latest = "3.0.0"))
        var reports = 0
        gate.request("3.0.0") { reports++ }
        gate.request(null) { reports++ }
        assertEquals(2, reports)
        assertNull(gate.state.value)
    }

    @Test
    fun `E19-S03 an older failure holds the report until Report anyway`() {
        val versions = Versions(running = "2.1.4", latest = "3.0.0")
        val gate = StaleFailureGate(versions)
        var reports = 0
        gate.request("2.1.1") { reports++ }
        assertEquals(0, reports)
        assertEquals(StaleFailureUi("2.1.1", "3.0.0", canUpdate = true), gate.state.value)
        // KitConfirm closes itself before it runs the alternative answer.
        gate.onDismissStale()
        gate.onReportAnyway()
        assertEquals(1, reports)
        assertNull(gate.state.value)
        gate.onReportAnyway()
        assertEquals(1, reports)
        assertEquals(0, versions.opened)
    }

    @Test
    fun `E19-S03 Update first opens the update and drops the held report`() {
        val versions = Versions(running = "2.1.4", latest = "3.0.0")
        val gate = StaleFailureGate(versions)
        var reports = 0
        gate.request("2.1.1") { reports++ }
        gate.onUpdateFirst()
        assertEquals(1, versions.opened)
        assertNull(gate.state.value)
        gate.onReportAnyway()
        assertEquals(0, reports)
    }

    @Test
    fun `E19-S03 the versions come from the updater state read-only`() {
        val state =
            MutableStateFlow(
                AppUpdateState(
                    installed = InstalledApp("io.example", "2.1.4", 62),
                    release = AppRelease("v3.0.0+70", "3.0.0", 70, "", "", emptyList(), "{}"),
                )
            )
        var opened = 0
        val versions = ReportVersions.from(state) { opened++ }
        assertEquals("2.1.4", versions.running())
        assertEquals("3.0.0", versions.latestRelease())
        versions.openUpdate()
        assertEquals(1, opened)
        // Where the app cannot update itself, no release is claimed.
        state.value = state.value.copy(supported = false)
        assertNull(versions.latestRelease())
    }
}
