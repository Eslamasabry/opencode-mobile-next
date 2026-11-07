package io.github.eslamasabry.oc.feature.diagnostics.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Report this failure" for a failure saved by an older app version (E19-S03): the one plain line
 * "This happened on [happenedOn]. Version [fixedIn] may already fix it." with Update first (only
 * when [canUpdate]) and Report anyway. Versions are `X.Y.Z`, never a build number or a tag.
 */
public data class StaleFailureUi(
    val happenedOn: String,
    val fixedIn: String,
    /** The in-app updater knows a release newer than the running app: Update first is offered. */
    val canUpdate: Boolean,
)

/**
 * The comparison behind [StaleFailureUi]. A version reads as `X.Y.Z`, optionally with a leading
 * `v`, a `-suffix` and a `+build`; anything else is unknown. Unknown or same versions return
 * null, so the report opens as it does today.
 */
public object FailureVersionCheck {
    private val SHAPE = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?(?:\+(\d+))?$""")

    /** A comparable version: X, Y, Z and the build when one was given. */
    internal data class Version(val parts: List<Int>, val build: Int?) : Comparable<Version> {
        val name: String get() = parts.joinToString(".")

        override fun compareTo(other: Version): Int {
            for (i in parts.indices) {
                val c = parts[i].compareTo(other.parts[i])
                if (c != 0) return c
            }
            // Same X.Y.Z: the build decides only when both sides carry one.
            return if (build != null && other.build != null) build.compareTo(other.build) else 0
        }
    }

    internal fun parse(value: String?): Version? {
        val m = SHAPE.matchEntire(value?.trim().orEmpty()) ?: return null
        val parts = (1..3).map { m.groupValues[it].toIntOrNull() ?: return null }
        return Version(parts, m.groupValues[4].toIntOrNull())
    }

    /**
     * Compares the failure's [saved] app version with the [running] app and the [latest] release
     * the in-app updater knows. Null (report as today) when [saved] is unknown, or when neither
     * known version is newer than it.
     */
    public fun stale(saved: String?, running: String?, latest: String?): StaleFailureUi? {
        val then = parse(saved) ?: return null
        val now = parse(running)
        val release = parse(latest)
        val canUpdate = release != null && (now == null || release > now)
        val newest = listOfNotNull(now, release).maxOrNull() ?: return null
        if (newest <= then) return null
        // When no update is waiting the running app is the newer one: it may already fix it.
        val fixedIn = if (canUpdate && release != null) release else newest
        return StaleFailureUi(happenedOn = then.name, fixedIn = fixedIn.name, canUpdate = canUpdate)
    }
}

/**
 * What the gate reads and opens (the updater's read side, bound by :androidApp, or [from] over
 * [AppUpdateState]). Nothing here downloads or installs: Update first only opens the Updates page.
 */
public interface ReportVersions {
    /** The running app's version name, or null when it is not known yet. */
    public fun running(): String?

    /** The newest release the in-app updater knows (`X.Y.Z`), or null when it knows none. */
    public fun latestRelease(): String?

    /** Opens the Updates page, where the person starts the update themselves. */
    public fun openUpdate()

    public companion object {
        /** Reads the app-wide updater state ([AppUpdates.state]) without changing it. */
        public fun from(state: StateFlow<AppUpdateState>, open: () -> Unit): ReportVersions =
            object : ReportVersions {
                override fun running(): String? = state.value.installed?.version

                override fun latestRelease(): String? =
                    state.value.takeIf { it.supported }?.release?.version

                override fun openUpdate() = open()
            }
    }
}

/** The answers the stale-failure line offers. Each names its one target. */
public interface StaleFailureActions {
    public fun onUpdateFirst()

    public fun onReportAnyway()

    public fun onDismissStale()
}

/**
 * Sits in front of "Report this failure" (E19-S03). [request] reports straight away when the
 * failure's saved version is the same as now or unknown; otherwise it holds the report and shows
 * [StaleFailureUi] until the person picks Update first, Report anyway or closes it. One question at
 * a time: a second request while one is open replaces it.
 *
 * Closing hides the line but keeps the held report until the next request or Update first:
 * KitConfirm closes itself (onCancel) just before it runs an alternative answer, so Report anyway
 * arrives after the close and must still find the report.
 */
public class StaleFailureGate(private val versions: ReportVersions) : StaleFailureActions {
    private val mutable = MutableStateFlow<StaleFailureUi?>(null)
    public val state: StateFlow<StaleFailureUi?> = mutable.asStateFlow()
    private var pending: (() -> Unit)? = null

    /** [savedVersion] is the app version the failure was saved by, or null when it was not kept. */
    public fun request(savedVersion: String?, report: () -> Unit) {
        val stale =
            FailureVersionCheck.stale(savedVersion, versions.running(), versions.latestRelease())
        if (stale == null) {
            close()
            report()
            return
        }
        pending = report
        mutable.value = stale
    }

    override fun onUpdateFirst() {
        if (mutable.value == null) return
        close()
        versions.openUpdate()
    }

    override fun onReportAnyway() {
        val report = pending ?: return
        close()
        report()
    }

    override fun onDismissStale() {
        mutable.value = null
    }

    private fun close() {
        pending = null
        mutable.value = null
    }
}
