package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome

/** Reads the monitor's opted-in sources without opening, switching or starting a host. */
public class PaseoUsageSource(
    private val monitor: PaseoUsageMonitor,
    private val paseoProfileIds: () -> List<String>,
) : UsageSource {
    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        monitor.refresh()
        val readings = ArrayList<UsageReading>()
        val unreached = HashSet<String>()
        val known = context.connections.mapTo(HashSet()) { it.id }
        for (id in (listOf(UsageConnection.PHONE_ID) + paseoProfileIds()).distinct()) {
            if (id !in known || !monitor.enabled(id)) continue
            val status = monitor.statusFor(id)
            val list = monitor.readingsFor(id)
            if (list == null || status == PaseoUsageStatus.HostStopped || status == PaseoUsageStatus.Unavailable) {
                unreached += id
                continue
            }
            for (reading in list) {
                readings += UsageReading(
                    providerId = reading.providerId,
                    displayName = reading.displayName,
                    connectionId = id,
                    status = when {
                        reading.needsSignIn && reading.windows.isEmpty() -> UsageReadingStatus.NotSignedIn
                        reading.windows.isEmpty() -> UsageReadingStatus.NoLimits
                        else -> UsageReadingStatus.Ok
                    },
                    fetchedAt = reading.fetchedAt,
                    method = SignInMethod.Plan,
                    windows = reading.windows.map { window ->
                        UsageWindow(
                            id = when (window.durationSeconds) {
                                18_000L -> "5h"
                                604_800L -> "weekly"
                                else -> window.label.ifEmpty { "window" }
                            },
                            usedPercent = 100.0 - window.remainingPercent,
                            durationSeconds = window.durationSeconds,
                            resetsAt = window.resetsAt,
                            label = window.label,
                        )
                    },
                )
            }
        }
        return Outcome.Ok(UsageSourceResult(readings = readings, unreached = unreached))
    }
}
