package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.platform.VisibilityTracker
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.runtime.budget.PhoneBudgetFiles
import io.github.eslamasabry.oc.engine.runtime.heat.AgentThrottle
import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.PhoneBudgetTimers
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneBudgetSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Actual Keep running switch and best-effort Claude instruction files. The preference is device scoped. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = dev.zacsweers.metro.binding<PhoneBudgetSource>())
@Inject
class NativePhoneBudget(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    clock: Clock,
) : PhoneBudgetSource {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.serial("phone-budget-files"))
    private val lane = Mutex()
    private var started = false
    private val visibility = VisibilityTracker()
    private val pacingVisibility = visibility.visible.map {
        if (it) Visibility.Foreground else Visibility.HiddenIdle
    }.stateIn(scope, SharingStarted.Eagerly, Visibility.HiddenIdle)
    private val pacer = DefaultPacer(pacingVisibility)
    @Volatile private var forecast: Double? = null
    private var thermalListener: Any? = null
    private val linux by lazy { BuiltinLinux.get(app) }
    private val power by lazy { app.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    @Volatile private var fullSpeedValue = false
    private val throttle = AgentThrottle(
        fullSpeed = { fullSpeedValue },
        setPriority = { level ->
            withContext(dispatchers.io) {
                when (val changed = attempt({ Failure.Runtime(RuntimeProblem.Generic) }) { linux.agentPriority(level.name.lowercase()) }) {
                    is Outcome.Failed -> changed
                    is Outcome.Ok -> {
                        val threads = changed.value?.get("threads") as? Number
                        if (threads != null) Outcome.Ok(threads.toInt())
                        else Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                    }
                }
            }
        },
        battery = { battery() },
        powerSave = { power?.isPowerSaveMode == true },
        hidden = { !visibility.visible.value },
        clock = clock,
    )
    private fun battery(): BatteryReading =
        try {
            val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            BatteryReading.of(
                if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).toInt() else null,
                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                    BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                    else -> null
                },
            )
        } catch (_: Exception) {
            BatteryReading.Unknown
        }

    private fun thermalReading(): ThermalReading {
        return try {
            val manager = power ?: return ThermalReading.Unknown
            val status = if (Build.VERSION.SDK_INT >= 29) {
                ThermalStatus.entries.getOrNull(manager.currentThermalStatus + 1) ?: ThermalStatus.Unknown
            } else ThermalStatus.Unknown
            ThermalReading.of(status, if (Build.VERSION.SDK_INT >= 30) manager.getThermalHeadroom(30).toDouble() else null).also { forecast = it.headroom }
        } catch (_: Exception) {
            ThermalReading.Unknown
        }
    }

    fun start() {
        if (started) return
        started = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = visibility.onStarted()
            override fun onActivityStopped(activity: Activity) = visibility.onStopped(activity.isChangingConfigurations)
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val listener = PowerManager.OnThermalStatusChangedListener { value ->
                    scope.launch {
                        if (stores.warm() is Outcome.Failed) return@launch
                        if (withContext(dispatchers.io) { linux.installed }) {
                            val status = ThermalStatus.entries.getOrNull(value + 1) ?: ThermalStatus.Unknown
                            throttle.observe(ThermalReading.of(status, forecast))
                        }
                    }
                }
                power?.addThermalStatusListener(app.mainExecutor, listener)
                thermalListener = listener
            } catch (_: Exception) {
                // Older/OEM builds still receive the bounded periodic readings below.
            }
        }
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            fullSpeedValue = fullSpeed()
            applyEverywhere()
            launch {
                visibility.visible.collect {
                    if (withContext(dispatchers.io) { linux.installed }) throttle.observe(thermalReading())
                }
            }
            launch {
                pacer.ticks(PhoneBudgetTimers.thermal).collect {
                    if (withContext(dispatchers.io) { linux.installed }) throttle.observe(thermalReading())
                }
            }
            launch {
                stores.prefs.changes.collect { keys ->
                    if (PhoneBudgetFiles.PREFERENCE_KEY in keys) applyEverywhere()
                }
            }
            // A newly installed profile is picked up without waiting for an app restart.
            pacer.ticks(PhoneBudgetTimers.files).collect { applyEverywhere() }
        }
    }

    override suspend fun fullSpeed(): Boolean {
        if (stores.warm() is Outcome.Failed) return false
        return (stores.prefs.get(PhoneBudgetFiles.PREFERENCE_KEY) as? PrefValue.Bool)?.value ?: false
    }

    override suspend fun setFullSpeed(enabled: Boolean): Outcome<Unit> = lane.withLock {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return@withLock warm
            is Outcome.Ok -> Unit
        }
        val saved = stores.prefs.commit(stores.lanes.app) { putBool(PhoneBudgetFiles.PREFERENCE_KEY, enabled) }
        if (saved is Outcome.Failed) return@withLock saved
        fullSpeedValue = enabled
        applyFiles(enabled)
        if (withContext(dispatchers.io) { linux.installed }) throttle.observe(thermalReading())
        Outcome.Ok(Unit)
    }

    private suspend fun applyEverywhere() = lane.withLock {
        fullSpeedValue = fullSpeed()
        applyFiles(fullSpeedValue)
    }

    private suspend fun applyFiles(fullSpeed: Boolean) = withContext(dispatchers.io) {
        // Instruction edits are advisory. Unreadable or linked files stay exactly as they are.
        attempt({ Failure.StorageRefused() }) {
            val root = app.filesDir.toPath().resolve("linux/ubuntu/home/oc/.oc-profiles")
            var at = app.filesDir.toPath()
            for (part in app.filesDir.toPath().relativize(root)) {
                at = at.resolve(part)
                if (!Files.isDirectory(at, NOFOLLOW_LINKS)) return@attempt
            }
            Files.newDirectoryStream(root).use { entries ->
                for (entry in entries) {
                    if (!OWNER.matches(entry.fileName.toString()) || !Files.isDirectory(entry, NOFOLLOW_LINKS)) continue
                    val claude = entry.resolve("claude")
                    if (!Files.exists(claude, NOFOLLOW_LINKS)) {
                        if (fullSpeed) continue
                        Files.createDirectory(claude)
                    }
                    if (!Files.isDirectory(claude, NOFOLLOW_LINKS)) continue
                    attempt({ Failure.StorageRefused() }) {
                        edit(claude.resolve("CLAUDE.md").toFile(), fullSpeed) { PhoneBudgetFiles.withBlock(it, fullSpeed) }
                    }
                    attempt({ Failure.StorageRefused() }) {
                        edit(claude.resolve("settings.json").toFile(), fullSpeed) { PhoneBudgetFiles.settings(it, fullSpeed) }
                    }
                }
            }
        }
        Unit
    }

    private fun edit(file: File, fullSpeed: Boolean, transform: (String) -> String?) {
        val path = file.toPath()
        if (Files.exists(path, NOFOLLOW_LINKS) && !Files.isRegularFile(path, NOFOLLOW_LINKS)) return
        if (!Files.exists(path, NOFOLLOW_LINKS) && fullSpeed) return
        val before =
            if (!Files.exists(path, NOFOLLOW_LINKS)) ""
            else {
                if (Files.size(path) > 2_000_000L) return
                Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                    Channels.newInputStream(channel).bufferedReader(Charsets.UTF_8).readText()
                }
            }
        val after = transform(before) ?: return
        if (after == before) return
        if (after.isEmpty()) {
            Files.deleteIfExists(path)
            return
        }
        val temporary = path.resolveSibling("${path.fileName}.oc-new-${UUID.randomUUID()}")
        try {
            Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE).use { out ->
                out.write(after.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        val OWNER = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}

@ContributesTo(AppScope::class)
interface PhoneBudgetAccessors {
    val nativePhoneBudget: NativePhoneBudget
}
