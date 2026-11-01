package io.github.eslamasabry.oc.android.platform

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

// Field recorder, part 1 (plan 13 §3.6, WP-13.4; unit S2-FIELD): real cold, warm and hot starts with their first-frame
// and fully-drawn times, read from the system's own start history (ApplicationStartInfo, API 35+). Nothing is stored
// by the app and nothing leaves the phone; the summary feeds the Performance rows ("Opens in 0.3 s, typical this
// week"). No copy lives here: the rows are built by their screen owner from these numbers.

/** How the system started the app (ApplicationStartInfo.START_TYPE_*). */
public enum class StartKind { Cold, Warm, Hot, Unknown }

/** One start as the system saw it, without any platform type. Times are in milliseconds. */
public data class StartRecord(
    val atEpochMs: Long,
    val kind: StartKind,
    val firstFrameMs: Long,
    val fullyDrawnMs: Long?,
)

/**
 * What the Performance rows show. [typicalFirstFrameMs] is the median first-frame time of the starts in the last
 * [StartupHistory.WINDOW_MS] (null when there were none); [supported] is false on Android below 15, where the system
 * keeps no start history.
 */
public data class StartupSummary(
    val supported: Boolean,
    val typicalFirstFrameMs: Long?,
    val starts: Int,
    val coldStarts: Int,
    val latest: StartRecord?,
) {
    public companion object {
        public val Unsupported: StartupSummary = StartupSummary(false, null, 0, 0, null)
        public val Empty: StartupSummary = StartupSummary(true, null, 0, 0, null)
    }
}

/**
 * A start as read from the platform, before validation. Timestamps are `SystemClock.elapsedRealtimeNanos` values
 * (the ApplicationStartInfo clock); null when the system did not record that point.
 */
public data class RawStart(
    val kind: StartKind,
    val activity: Boolean,
    val launchNs: Long?,
    val forkNs: Long?,
    val firstFrameNs: Long?,
    val fullyDrawnNs: Long?,
)

/** The pure rules: which starts count, how a raw start becomes a record, and the weekly summary. */
public object StartupRules {
    /** A first frame later than this is not a start the person waited for (a broken clock or a debugger). */
    public const val MAX_PLAUSIBLE_MS: Long = 60_000

    /**
     * A record for an activity start that reached its first frame, or null. The start is measured from the launch
     * request (or the process fork when the launch time is missing); the wall time is placed with the elapsed clock
     * pair ([nowEpochMs], [elapsedNowNs]) read once by the caller.
     */
    public fun recordOf(raw: RawStart, nowEpochMs: Long, elapsedNowNs: Long): StartRecord? {
        if (!raw.activity) return null
        val origin = raw.launchNs ?: raw.forkNs ?: return null
        val firstFrame = raw.firstFrameNs ?: return null
        val firstFrameMs = (firstFrame - origin) / NANOS_PER_MS
        if (firstFrameMs < 0 || firstFrameMs > MAX_PLAUSIBLE_MS) return null
        val fullyDrawnMs = raw.fullyDrawnNs
            ?.let { (it - origin) / NANOS_PER_MS }
            ?.takeIf { it in firstFrameMs..MAX_PLAUSIBLE_MS }
        if (origin > elapsedNowNs) return null
        val atEpochMs = nowEpochMs - (elapsedNowNs - origin) / NANOS_PER_MS
        return StartRecord(atEpochMs, raw.kind, firstFrameMs, fullyDrawnMs)
    }

    /** Median of the first-frame times inside the window ending at [nowEpochMs]; the newest record is [latest]. */
    public fun summarize(records: List<StartRecord>, nowEpochMs: Long, windowMs: Long = StartupHistory.WINDOW_MS): StartupSummary {
        val recent = records.filter { it.atEpochMs in (nowEpochMs - windowMs)..nowEpochMs }
        if (recent.isEmpty()) return StartupSummary.Empty
        val sorted = recent.map { it.firstFrameMs }.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        return StartupSummary(
            supported = true,
            typicalFirstFrameMs = median,
            starts = recent.size,
            coldStarts = recent.count { it.kind == StartKind.Cold },
            latest = recent.maxBy { it.atEpochMs },
        )
    }

    private const val NANOS_PER_MS = 1_000_000L
}

/** Reads the system's start history. A seam so the rules run on the JVM; [AndroidStartInfoSource] is the real one. */
public interface StartInfoSource {
    public val supported: Boolean

    /** Newest first, at most [max]. Never throws; empty when the system refuses. */
    public fun history(max: Int): List<RawStart>

    /** The current process's start, once its first frame is drawn. Returns a handle that stops listening. */
    public fun onCurrentStart(executor: Executor, listener: (RawStart) -> Unit): AutoCloseable

    /** `SystemClock.elapsedRealtimeNanos()`, the ApplicationStartInfo clock. */
    public fun elapsedNowNs(): Long
}

/**
 * The start-up history. [refresh] reads the system history on [io] (a binder call, never on the main thread) when the
 * Performance rows are shown; [start] listens once for this process's own start. Nothing runs on a timer and nothing
 * runs while the app is hidden. Every call repeats safely.
 */
public class StartupHistory(
    private val source: StartInfoSource,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) : AutoCloseable {
    private val state = MutableStateFlow(if (source.supported) StartupSummary.Empty else StartupSummary.Unsupported)
    public val summary: StateFlow<StartupSummary> = state.asStateFlow()

    private val lock = Any()
    private var records: List<StartRecord> = emptyList()
    private var currentStart: AutoCloseable? = null

    /** Listens for this process's start (first frame) and folds it in; the system calls back once. */
    public fun start() {
        if (!source.supported) return
        synchronized(lock) {
            if (currentStart != null) return
            currentStart = source.onCurrentStart(Executor { it.run() }) { raw -> add(raw) }
        }
    }

    /** Reads the system history again (newest [MAX_HISTORY] starts). */
    public suspend fun refresh(): StartupSummary = withContext(io) {
        if (!source.supported) return@withContext StartupSummary.Unsupported
        val now = clock.now().toEpochMilliseconds()
        val elapsed = source.elapsedNowNs()
        val read = source.history(MAX_HISTORY).mapNotNull { StartupRules.recordOf(it, now, elapsed) }
        synchronized(lock) {
            records = merge(read, records)
            publish(now)
        }
        state.value
    }

    override fun close() {
        synchronized(lock) {
            currentStart?.close()
            currentStart = null
        }
    }

    private fun add(raw: RawStart) {
        val now = clock.now().toEpochMilliseconds()
        val record = StartupRules.recordOf(raw, now, source.elapsedNowNs()) ?: return
        synchronized(lock) {
            records = merge(listOf(record), records)
            publish(now)
        }
    }

    private fun publish(now: Long) {
        state.value = StartupRules.summarize(records, now)
    }

    /** The same start read twice (history and listener) lands within a few ms; keep one, newest first, bounded. */
    private fun merge(fresh: List<StartRecord>, old: List<StartRecord>): List<StartRecord> {
        val out = ArrayList<StartRecord>(fresh.size + old.size)
        for (r in fresh + old) {
            if (out.none { it.kind == r.kind && it.firstFrameMs == r.firstFrameMs && kotlin.math.abs(it.atEpochMs - r.atEpochMs) <= SAME_START_MS }) {
                out += r
            }
        }
        return out.sortedByDescending { it.atEpochMs }.take(MAX_HISTORY)
    }

    public companion object {
        /** "Typical this week". */
        public const val WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000
        public const val MAX_HISTORY: Int = 32
        private const val SAME_START_MS = 50L
    }
}

/** The real [StartInfoSource]: ActivityManager's ApplicationStartInfo (Android 15, API 35+). */
public class AndroidStartInfoSource(context: Context) : StartInfoSource {
    private val activityManager: ActivityManager? = context.getSystemService(ActivityManager::class.java)

    override val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && activityManager != null

    override fun history(max: Int): List<RawStart> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || activityManager == null) return emptyList()
        return try {
            readHistory(max)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onCurrentStart(executor: Executor, listener: (RawStart) -> Unit): AutoCloseable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || activityManager == null) return AutoCloseable {}
        return try {
            listen(executor, listener)
        } catch (_: Exception) {
            AutoCloseable {}
        }
    }

    override fun elapsedNowNs(): Long = SystemClock.elapsedRealtimeNanos()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readHistory(max: Int): List<RawStart> =
        activityManager?.getHistoricalProcessStartReasons(max).orEmpty().map(::rawOf)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun listen(executor: Executor, listener: (RawStart) -> Unit): AutoCloseable {
        val manager = activityManager ?: return AutoCloseable {}
        val consumer = Consumer<ApplicationStartInfo> { info -> listener(rawOf(info)) }
        manager.addApplicationStartInfoCompletionListener(executor, consumer)
        return AutoCloseable {
            try {
                manager.removeApplicationStartInfoCompletionListener(consumer)
            } catch (_: Exception) {
                // Already delivered and dropped by the system.
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun rawOf(info: ApplicationStartInfo): RawStart {
        val ts = info.startupTimestamps
        return RawStart(
            kind = when (info.startType) {
                ApplicationStartInfo.START_TYPE_COLD -> StartKind.Cold
                ApplicationStartInfo.START_TYPE_WARM -> StartKind.Warm
                ApplicationStartInfo.START_TYPE_HOT -> StartKind.Hot
                else -> StartKind.Unknown
            },
            activity = startedAnActivity(info),
            launchNs = ts[ApplicationStartInfo.START_TIMESTAMP_LAUNCH],
            forkNs = ts[ApplicationStartInfo.START_TIMESTAMP_FORK],
            firstFrameNs = ts[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME],
            fullyDrawnNs = ts[ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN],
        )
    }

    /** The start component is API 36; on Android 15 the start reason tells an activity launch apart. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startedAnActivity(info: ApplicationStartInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            info.startComponent == ApplicationStartInfo.START_COMPONENT_ACTIVITY
        } else {
            info.reason == ApplicationStartInfo.START_REASON_LAUNCHER ||
                info.reason == ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS ||
                info.reason == ApplicationStartInfo.START_REASON_START_ACTIVITY
        }
}
