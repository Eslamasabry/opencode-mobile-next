package io.github.eslamasabry.oc.android.platform

import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingTrigger
import androidx.annotation.RequiresApi
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

// Field recorder, part 4 (plan 13 §3.6, WP-13.4, P13-D3; unit S2-FIELD): an off-by-default system trace taken by
// Android when the app starts slowly (fully drawn) or stops responding (ANR), at most once a day per trigger. The
// system writes the file under the app's own `files/profiling/`; it never leaves the phone unless the person shares it
// through the Android share sheet themselves. The recorder registers triggers with the system and lists kept files on
// demand; it runs no callback, timer or thread of its own, so nothing runs while the app is hidden.

/** A trace file the system kept for the app. [path] is app-private and is never logged or shown. */
public data class KeptTrace(val path: String, val atEpochMs: Long, val sizeBytes: Long)

/** [supported] is false below Android 16 (system triggers are API 36). */
public data class TraceState(val supported: Boolean, val enabled: Boolean, val latest: KeptTrace?) {
    public companion object {
        public val Off: TraceState = TraceState(supported = false, enabled = false, latest = null)
    }
}

/** The system half. A seam so the rules run on the JVM; [AndroidTraceSystem] is the real one. */
public interface TraceSystem {
    public val supported: Boolean

    /** Registers (true) or removes (false) the app's triggers. Returns false when the system refused. */
    public fun setTriggers(enabled: Boolean): Boolean

    /** The folder the system writes traces to; null when unsupported. */
    public val traceDir: File?
}

/**
 * The trace switch and the kept traces. [setEnabled] applies the person's choice (the switch row's value, owned by
 * settings; this class keeps no preference); [refresh] lists kept traces on [io] and prunes all but the newest
 * [MAX_KEPT]. Every call repeats safely.
 */
public class TraceRecorder(
    private val system: TraceSystem,
    private val io: CoroutineDispatcher,
) {
    private val mutable = MutableStateFlow(if (system.supported) TraceState(true, false, null) else TraceState.Off)
    public val state: StateFlow<TraceState> = mutable.asStateFlow()

    /** Turns recording on or off. Off by default; turning it off keeps traces already taken. */
    public suspend fun setEnabled(enabled: Boolean): TraceState = withContext(io) {
        if (!system.supported) return@withContext TraceState.Off
        val applied = system.setTriggers(enabled)
        val now = if (applied) enabled else false
        mutable.value = mutable.value.copy(enabled = now)
        mutable.value
    }

    /** Lists the kept traces (newest first), deletes all but [MAX_KEPT], and publishes the newest. */
    public suspend fun refresh(): TraceState = withContext(io) {
        if (!system.supported) return@withContext TraceState.Off
        val kept = keep(list())
        mutable.value = mutable.value.copy(latest = kept.firstOrNull())
        mutable.value
    }

    /** Deletes every kept trace (the person's "Delete" on the row, or profile-independent data clearing). */
    public suspend fun deleteAll(): TraceState = withContext(io) {
        if (!system.supported) return@withContext TraceState.Off
        list().forEach { File(it.path).delete() }
        mutable.value = mutable.value.copy(latest = null)
        mutable.value
    }

    private fun list(): List<KeptTrace> {
        val dir = system.traceDir ?: return emptyList()
        val files = try {
            dir.listFiles().orEmpty()
        } catch (_: SecurityException) {
            emptyArray()
        }
        return files
            .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.length() > 0 }
            .map { KeptTrace(it.path, it.lastModified(), it.length()) }
            .sortedByDescending { it.atEpochMs }
    }

    private fun keep(traces: List<KeptTrace>): List<KeptTrace> {
        traces.drop(MAX_KEPT).forEach { File(it.path).delete() }
        return traces.take(MAX_KEPT)
    }

    public companion object {
        public const val MAX_KEPT: Int = 2
        public const val RATE_LIMIT_HOURS: Int = 24
    }
}

/** The real [TraceSystem]: ProfilingManager system triggers (Android 16, API 36+). */
public class AndroidTraceSystem(private val context: Context) : TraceSystem {
    override val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && manager() != null

    override val traceDir: File? get() = if (supported) File(context.filesDir, "profiling") else null

    override fun setTriggers(enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA || manager() == null) return false
        return try {
            apply(enabled)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun manager(): ProfilingManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        context.getSystemService(ProfilingManager::class.java)
    } else {
        null
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun apply(enabled: Boolean) {
        val manager = manager() ?: return
        if (enabled) {
            val hours = TraceRecorder.RATE_LIMIT_HOURS
            manager.addProfilingTriggers(
                listOf(
                    ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN).setRateLimitingPeriodHours(hours).build(),
                    ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANR).setRateLimitingPeriodHours(hours).build(),
                ),
            )
        } else {
            manager.removeProfilingTriggersByType(
                intArrayOf(ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN, ProfilingTrigger.TRIGGER_TYPE_ANR),
            )
        }
    }
}
