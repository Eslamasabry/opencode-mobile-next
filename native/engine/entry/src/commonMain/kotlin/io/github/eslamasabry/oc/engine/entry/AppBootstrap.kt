package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The ADR §9.7 start-up steps, as ports the app binds (`OcApplication` on Android, `main` on the desktop). Steps 1–4 run
 * on the caller's (main) thread and must stay tiny (< 50 ms in total, no file but the ≤ 4 KiB crash-loop record);
 * 5–6 run on the IO dispatcher. Step 7 (WorkManager on demand) needs no call.
 */
public interface BootstrapSteps {
    /** 1. The uncaught-exception handler (category-only crash record). */
    public fun installCrashHandler()

    /** 2. The Redactor. */
    public fun initRedactor()

    /** 3. The crash-loop guard: true = three quick crashes in a row → safe mode. */
    public fun crashLoopGuard(): Boolean

    /** 4. The app graph (stores lazy; backed-up files wait for the fence). */
    public fun buildGraph()

    /** 5. The StartupFence: finish a data restore, step 0 copy, the locale mirror, start MigrationRunner. */
    public suspend fun startupFence()

    /** 6. Warm the prefs file, after the fence. */
    public suspend fun warmPrefs()

    /** 03 §18.6 / 17 §4.2: problem-report capture at start (built by S1-ENG, wired here); never blocks opening. */
    public suspend fun startReportCapture()
}

/** The migration gate as the entry engine needs it (bound over `:core:data` `MigrationGate.state is Open`). */
public interface GateSignal {
    public val open: StateFlow<Boolean>
}

/** 16 §5.3: `app_open`, each cold start, after the migration gate opens. Bound to the analytics core by the app. */
public fun interface AppOpenSink {
    public fun appOpened()
}

/**
 * Runs ADR §9.7 in order and publishes [phase] for [EntryWorld.bootstrap]. [onCreate] is called once per process from
 * the Application; a second call is ignored. `app_open` goes out exactly once, after bootstrap is Ready AND the gate is
 * open; never in safe mode (the person sees the safe-mode page, not the app).
 */
public class AppBootstrap(
    private val steps: BootstrapSteps,
    private val gate: GateSignal,
    private val appOpen: AppOpenSink,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val mutablePhase = MutableStateFlow(BootstrapPhase.Starting)
    public val phase: StateFlow<BootstrapPhase> = mutablePhase.asStateFlow()

    private var started = false

    /** Each ADR §9.7 step, in the order it ran (diagnostics and the order test). */
    private val mutableTrail = MutableStateFlow<List<String>>(emptyList())
    public val trail: StateFlow<List<String>> = mutableTrail.asStateFlow()

    public fun onCreate() {
        if (started) return
        started = true
        step("crashHandler") { steps.installCrashHandler() }
        step("redactor") { steps.initRedactor() }
        val safeMode = step("crashLoopGuard") { steps.crashLoopGuard() }
        step("graph") { steps.buildGraph() }
        if (safeMode) {
            mutablePhase.value = BootstrapPhase.SafeMode
            return
        }
        scope.launch(io) {
            step("startupFence") { steps.startupFence() }
            step("warmPrefs") { steps.warmPrefs() }
            mutablePhase.value = BootstrapPhase.Ready
            launch { step("reportCapture") { steps.startReportCapture() } }
        }
        scope.launch {
            combine(mutablePhase, gate.open) { phase, open -> phase == BootstrapPhase.Ready && open }.first { it }
            mutableTrail.value += "appOpen"
            log.i { "app open" }
            appOpen.appOpened()
        }
    }

    private inline fun <T> step(name: String, block: () -> T): T {
        val result = block()
        mutableTrail.value += name
        return result
    }

    private companion object {
        val log = AppLog.tagged("bootstrap")
    }
}
