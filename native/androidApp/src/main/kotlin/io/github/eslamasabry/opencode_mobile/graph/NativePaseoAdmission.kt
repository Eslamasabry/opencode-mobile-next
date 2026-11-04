package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.engine.runtime.budget.BudgetAdmission
import io.github.eslamasabry.oc.engine.runtime.budget.BudgetReadings
import io.github.eslamasabry.oc.engine.runtime.budget.HeavyJob
import io.github.eslamasabry.oc.engine.runtime.budget.ProcessBudgetGuard
import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.engine.runtime.procs.PhoneProcessWatch
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import io.github.eslamasabry.oc.engine.runtime.procs.RunningReading
import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.ChildProcessLimit
import io.github.eslamasabry.opencode_mobile.SetupRunner
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Shared payload exclusion: a send and a setup/removal can never change the same executables
 * together.
 */
@SingleIn(AppScope::class)
@Inject
class NativePaseoAdmission(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    clock: Clock,
) {
    private val lock = Any()
    private var uses = 0
    private var maintenance = false
    private val budgetLane = Mutex()
    private val runner by lazy { SetupRunner.get(app) }
    private val processWatch = PhoneProcessWatch()
    private var latestProcesses: RunningReading? = null
    private val budget =
        ProcessBudgetGuard(
            object : BudgetReadings {
                override suspend fun processCount(): Int? =
                    withContext(dispatchers.io) {
                        try {
                            val snapshot =
                                ProcessRows.snapshot(BuiltinLinux.get(app).processTable())
                            processWatch
                                .record(snapshot, clock.now())
                                .also { latestProcesses = it }
                                .childCount
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }

                override suspend fun limitLifted(): Boolean =
                    withContext(dispatchers.io) {
                        try {
                            ChildProcessLimit.status(app)["restrictionsOff"] == true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false
                        }
                    }

                override suspend fun thermal(): ThermalReading =
                    withContext(dispatchers.io) {
                        try {
                            val power = app.getSystemService(PowerManager::class.java)
                            val status =
                                if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus
                                else null
                            val level =
                                when (status) {
                                    0 -> ThermalStatus.None
                                    1 -> ThermalStatus.Light
                                    2 -> ThermalStatus.Moderate
                                    3 -> ThermalStatus.Severe
                                    4 -> ThermalStatus.Critical
                                    5 -> ThermalStatus.Emergency
                                    6 -> ThermalStatus.Shutdown
                                    else -> ThermalStatus.Unknown
                                }
                            ThermalReading.of(
                                level,
                                if (Build.VERSION.SDK_INT >= 30)
                                    power?.getThermalHeadroom(30)?.toDouble()
                                else null,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            ThermalReading.Unknown
                        }
                    }

                override suspend fun battery(): BatteryReading =
                    withContext(dispatchers.io) {
                        try {
                            val intent =
                                app.registerReceiver(
                                    null,
                                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                                )
                            val raw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            val level =
                                if (raw != null && raw >= 0 && scale != null && scale > 0)
                                    (100L * raw / scale).toInt()
                                else null
                            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            BatteryReading.of(
                                level,
                                when (status) {
                                    BatteryManager.BATTERY_STATUS_CHARGING,
                                    BatteryManager.BATTERY_STATUS_FULL -> true
                                    BatteryManager.BATTERY_STATUS_DISCHARGING,
                                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                                    else -> null
                                },
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            BatteryReading.Unknown
                        }
                    }

                override suspend fun runningAgents(): Int =
                    latestProcesses?.groups?.count { it.kind == RunningGroupKind.ClaudeCode } ?: 0
            },
            fullSpeed = { false },
            clock = clock,
            scope = appScope,
        )

    fun queuesOften(): Boolean = budget.queuesOften()

    fun local(spec: GatewaySpec): Boolean =
        try {
            val endpoint = PaseoEndpoint.parse(spec.profile.origin.toString())
            endpoint.host in setOf("127.0.0.1", "localhost", "::1", "[::1]") &&
                endpoint.port == 4099
        } catch (_: Exception) {
            false
        }

    suspend fun acquire(profile: ProfileId): Outcome<Unit> =
        withContext(dispatchers.io) {
            synchronized(lock) {
                val running =
                    try {
                        runner.running ||
                            runner.status()?.let {
                                JSONObject(it).optString("state") == "running"
                            } == true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        true
                    }
                if (maintenance || running || stores.profiles.profile(profile) == null) busy()
                else {
                    uses++
                    Outcome.Ok(Unit)
                }
            }
        }

    fun release() {
        synchronized(lock) {
            check(uses > 0)
            uses--
        }
    }

    suspend fun admitAgent(): Outcome<Unit> = budgetLane.withLock {
        if (budget.admit(HeavyJob.AgentSession) == BudgetAdmission.Admitted) Outcome.Ok(Unit)
        else busy()
    }

    /**
     * SetupRunner start and send admission share this lock; a successful start blocks later payload
     * users.
     */
    fun payloadsBusy(): Boolean = synchronized(lock) { uses > 0 || maintenance }

    fun startSetup(start: () -> Unit): Boolean =
        synchronized(lock) {
            if (uses > 0 || maintenance) false
            else {
                start()
                true
            }
        }

    fun beginRemoval(): Boolean =
        synchronized(lock) {
            if (
                uses > 0 ||
                    maintenance ||
                    try {
                        runner.running
                    } catch (_: Exception) {
                        true
                    }
            )
                false
            else {
                maintenance = true
                true
            }
        }

    fun endRemoval() {
        synchronized(lock) { maintenance = false }
    }

    private fun busy(): Outcome.Failed = Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))
}
