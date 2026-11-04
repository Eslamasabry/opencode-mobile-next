package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import android.os.PowerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.engine.runtime.vitals.LinuxVitals
import io.github.eslamasabry.oc.engine.runtime.vitals.linuxHeatOf
import io.github.eslamasabry.oc.engine.runtime.vitals.linuxShareOf
import io.github.eslamasabry.oc.engine.runtime.vitals.parseMemAvailable
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxVitalsSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Android-side observations only: Ubuntu's synthetic /proc never supplies these readings. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LinuxVitalsSource>())
@Inject
class NativeLinuxVitals(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val platform: PlatformCapabilities,
) : LinuxVitalsSource {
    private val lane = Mutex()

    override suspend fun read(): Outcome<LinuxVitals> = lane.withLock {
        if (!platform.builtinLinux) return@withLock Outcome.Ok(LinuxVitals())
        withContext(dispatchers.io) {
            val status =
                if (Build.VERSION.SDK_INT < 29) ThermalStatus.Unknown
                else {
                    val read = attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                        app.getSystemService(PowerManager::class.java)?.currentThermalStatus
                    }
                    val value = (read as? Outcome.Ok)?.value
                    if (value == null) ThermalStatus.Unknown
                    else ThermalStatus.entries.getOrNull(value + 1) ?: ThermalStatus.Unknown
                }
            val memory = attempt({ Failure.StorageRefused() }) {
                parseMemAvailable(File("/proc/meminfo").readText())
            }
            val processes = attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                val table = BuiltinLinux.get(app).processTable()
                check(table["processes"] is List<*>)
                linuxShareOf(ProcessRows.snapshot(table), Runtime.getRuntime().availableProcessors())
            }
            val share = (processes as? Outcome.Ok)?.value ?: LinuxVitals()
            Outcome.Ok(share.copy(heat = linuxHeatOf(status), phoneMemoryFreeBytes = (memory as? Outcome.Ok)?.value))
        }
    }
}
