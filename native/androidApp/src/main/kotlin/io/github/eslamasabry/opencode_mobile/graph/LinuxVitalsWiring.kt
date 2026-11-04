package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.domain.platform.ThermalLevel
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHeat
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxVitalsSource
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxVitalsUi
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Inject
@ContributesBinding(AppScope::class)
class NativeLinuxVitals(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val facts: DeviceFacts,
    private val redactor: Redactor,
) : LinuxVitalsSource {
    override suspend fun read(): Outcome<LinuxVitalsUi> =
        withContext(dispatchers.io) {
            try {
                val memory = ActivityManager.MemoryInfo()
                val activity = app.getSystemService(ActivityManager::class.java)
                activity?.getMemoryInfo(memory)
                val heat =
                    if (Build.VERSION.SDK_INT < 29) null
                    else
                        when (facts.thermal.value) {
                            ThermalLevel.None,
                            ThermalLevel.Light -> LinuxHeat.Cool
                            ThermalLevel.Moderate -> LinuxHeat.Warm
                            ThermalLevel.Severe -> LinuxHeat.Hot
                            ThermalLevel.Critical,
                            ThermalLevel.Emergency,
                            ThermalLevel.Shutdown -> LinuxHeat.TooHot
                        }
                val table = BuiltinLinux.get(app).processTable()
                val rows =
                    (table["processes"] as? List<*>)
                        ?.mapNotNull { it as? Map<*, *> }
                        ?.filter { it["owner"] != "app" }
                val samples = rows?.mapNotNull {
                    (it["cpuPercent"] as? Number)?.toDouble()?.takeIf { v ->
                        v.isFinite() && v >= 0
                    }
                }
                val bytes = rows?.mapNotNull {
                    (it["rssBytes"] as? Number)?.toLong()?.takeIf { v -> v >= 0 }
                }
                val heavy =
                    rows
                        ?.maxByOrNull { (it["cpuPercent"] as? Number)?.toDouble() ?: 0.0 }
                        ?.takeIf { ((it["cpuPercent"] as? Number)?.toDouble() ?: 0.0) >= 50 }
                Outcome.Ok(
                    LinuxVitalsUi(
                        heat = heat,
                        phoneFreeBytes = memory.availMem.takeIf { activity != null && it >= 0 },
                        linuxMemoryBytes = bytes?.takeIf { it.size == rows?.size }?.sum(),
                        linuxCpuPercent =
                            samples?.takeIf { it.size == rows?.size }?.sum()?.roundToInt(),
                        heavyProgram =
                            (heavy?.get("name") as? String)?.let(redactor::redact)?.takeIf {
                                it.isNotBlank()
                            },
                        heavyPercent =
                            (heavy?.get("cpuPercent") as? Number)?.toDouble()?.roundToInt(),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        }
}
