package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Storage on this phone (TEAM-304; port of lib/termux/storage.dart): what the managed Termux environment uses, by
// category, and per-category cleaning through ~/.oc/tools.sh. The scan runs in the background inside Termux and
// writes a log the UI tails plus a JSON report. Cleaning is limited to explicit regenerable caches from a current scan;
// a clean makes the report stale and another scan is required before further cleanup.

public enum class TermuxStorageScanState { Idle, Running, Done, Cancelled, Failed, Stale }

private fun scanState(value: String?): TermuxStorageScanState =
    when (value) {
        "running" -> TermuxStorageScanState.Running
        "done" -> TermuxStorageScanState.Done
        "cancelled" -> TermuxStorageScanState.Cancelled
        "failed" -> TermuxStorageScanState.Failed
        "stale" -> TermuxStorageScanState.Stale
        else -> TermuxStorageScanState.Idle
    }

/** Category keys in the order the screen shows them; an unknown one stays visible under its raw key. */
public enum class TermuxStorageCategoryKey(public val wireName: String) {
    BuildCaches("build_caches"),
    AgentScratch("agent_scratch"),
    ProjectBuildOutputs("project_build_outputs"),
    Toolchains("toolchains"),
    AiTeam("ai_team"),
    Opencode("opencode"),
    Projects("projects"),
    SharedCaches("shared_caches"),
    ;

    public companion object {
        public fun parse(value: String): TermuxStorageCategoryKey? = entries.firstOrNull { it.wireName == value }
    }
}

public data class TermuxStoragePath(val path: String, val bytes: Long)

public data class TermuxStorageCategory(
    val key: String,
    val bytes: Long,
    val deletable: Boolean,
    val paths: List<TermuxStoragePath>,
    val labelKey: String = "",
    val noteKey: String = "",
) {
    /** Old or untrusted reports cannot expose cleanup for protected categories. */
    val canClean: Boolean get() = deletable && known == TermuxStorageCategoryKey.BuildCaches

    val known: TermuxStorageCategoryKey? get() = TermuxStorageCategoryKey.parse(key)

    public fun without(removed: Set<String>): TermuxStorageCategory {
        val kept = paths.filter { it.path !in removed }
        return copy(bytes = kept.sumOf { it.bytes }, paths = kept)
    }
}

public data class TermuxStorageProject(val name: String, val path: String, val bytes: Long, val buildBytes: Long)

public data class TermuxStorageReport(
    val scannedAtEpochSeconds: Long?,
    val totalBytes: Long,
    val categories: List<TermuxStorageCategory>,
    val projects: List<TermuxStorageProject>,
    val isStale: Boolean = false,
) {
    public fun asStale(): TermuxStorageReport = copy(isStale = true)

    /** Everything a clean may remove, in bytes; nothing for a stale report. */
    val deletableBytes: Long
        get() = if (isStale) 0 else categories.filter { it.canClean }.sumOf { it.bytes }

    public fun category(key: TermuxStorageCategoryKey): TermuxStorageCategory? =
        categories.firstOrNull { it.key == key.wireName }

    /** The report after [removed] paths of [key] went away: entries and total shrink by exactly what was freed. */
    public fun afterClean(key: String, removed: Iterable<String>, freedBytes: Long): TermuxStorageReport {
        val gone = removed.toSet()
        return copy(
            totalBytes = (totalBytes - freedBytes).coerceIn(0, totalBytes.coerceAtLeast(0)),
            categories = categories.map { if (it.key == key) it.without(gone) else it },
            isStale = true,
        )
    }

    public companion object {
        /** The scan's JSON report; null when it is not a JSON object. A report without cleanup policy 2 is stale. */
        public fun parse(json: String): TermuxStorageReport? {
            val decoded = decodeJson(json).asMap() ?: return null
            val categories =
                decoded["categories"].asList().mapNotNull { element ->
                    val raw = element.asMap() ?: return@mapNotNull null
                    val key = raw["key"].asText() ?: ""
                    if (key.isEmpty()) return@mapNotNull null
                    val paths =
                        raw["paths"].asList().mapNotNull { entry ->
                            val map = entry.asMap() ?: return@mapNotNull null
                            val path = map["path"].asText() ?: ""
                            if (path.isEmpty()) null else TermuxStoragePath(path, map["bytes"].asLong())
                        }
                    TermuxStorageCategory(
                        key = key,
                        labelKey = raw["label_key"].asText() ?: "",
                        noteKey = raw["note_key"].asText() ?: "",
                        bytes = raw["bytes"].asLong(),
                        deletable = raw["deletable"].isTrue(),
                        paths = paths,
                    )
                }
            val projects =
                decoded["projects"].asList().mapNotNull { element ->
                    val raw = element.asMap() ?: return@mapNotNull null
                    val name = raw["name"].asText() ?: ""
                    if (name.isEmpty()) {
                        null
                    } else {
                        TermuxStorageProject(
                            name = name,
                            path = raw["path"].asText() ?: "",
                            bytes = raw["bytes"].asLong(),
                            buildBytes = raw["build_bytes"].asLong(),
                        )
                    }
                }
            val scannedAt = decoded["scanned_at"].asLong()
            val policy = decoded["cleanup_policy"]
            return TermuxStorageReport(
                scannedAtEpochSeconds = if (scannedAt > 0) scannedAt else null,
                totalBytes = decoded["total_bytes"].asLong(),
                categories = categories,
                projects = projects,
                isStale = decoded["stale"].isTrue() || !(policy is JsonPrimitive && !policy.isString && policy.doubleOrNull == 2.0),
            )
        }
    }
}

public data class TermuxStorageScanStatus(
    val state: TermuxStorageScanState,
    val log: String,
    val report: TermuxStorageReport?,
) {
    public companion object {
        private const val LOG_MARKER = "__OC_TOOLS_LOG__"
        private const val JSON_MARKER = "__OC_TOOLS_JSON__"
        private val stateLine = Regex("^state=(\\w+)", RegexOption.MULTILINE)

        public fun parse(raw: String): TermuxStorageScanStatus {
            val logIndex = raw.indexOf(LOG_MARKER)
            val jsonIndex = raw.indexOf(JSON_MARKER)
            val head = if (logIndex < 0) raw else raw.substring(0, logIndex)
            val state = scanState(stateLine.find(head)?.groupValues?.get(1))
            var log = ""
            var json = ""
            if (logIndex >= 0) {
                val logStart = logIndex + LOG_MARKER.length
                if (jsonIndex > logIndex) {
                    log = raw.substring(logStart, jsonIndex)
                    json = raw.substring(jsonIndex + JSON_MARKER.length).trim()
                } else {
                    log = raw.substring(logStart)
                }
            }
            var report = if (json.isEmpty()) null else TermuxStorageReport.parse(json)
            if (report != null && state != TermuxStorageScanState.Done) report = report.asStale()
            return TermuxStorageScanStatus(state, log.trim(), report)
        }
    }
}

/**
 * The scan's stages, in the order the bundled script measures them. Each starts with one `[oc] <heading>` log line,
 * so the scan view can fill its rows in as it goes; the sizes arrive only with the finished report.
 */
public enum class TermuxStorageScanStage(public val heading: String) {
    BuildCaches("Build caches"),
    SharedCaches("Other caches and package data (read only)"),
    AgentScratch("Agent scratch"),
    Toolchains("Toolchains"),
    AiTeam("AI Team"),
    Opencode("OpenCode"),
    Projects("Projects"),
    Total("Measuring the whole Termux install"),
    ;

    public companion object {
        /** The stage the scan has reached in [log]: the last heading printed, or null before the first one. */
        public fun reached(log: String): TermuxStorageScanStage? {
            var last: TermuxStorageScanStage? = null
            for (raw in log.split('\n')) {
                val line = raw.trim()
                if (!line.startsWith("[oc] ")) continue
                val heading = line.substring(5)
                entries.firstOrNull { it.heading == heading }?.let { last = it }
            }
            return last
        }
    }
}

/** The cheap answer for the settings row: last total and when it was taken. */
public data class TermuxStorageSummary(
    val state: TermuxStorageScanState,
    val totalBytes: Long?,
    val scannedAtEpochSeconds: Long?,
) {
    public companion object {
        public fun parse(raw: String): TermuxStorageSummary {
            val values = mutableMapOf<String, String>()
            for (line in raw.split('\n')) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                values[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
            }
            val total = values["total_bytes"]?.toLongOrNull()
            val scanned = values["scanned_at"]?.toLongOrNull()
            return TermuxStorageSummary(
                state = scanState(values["state"]),
                totalBytes = if (total == null || total < 0) null else total,
                scannedAtEpochSeconds = if (scanned == null || scanned <= 0) null else scanned,
            )
        }
    }
}

public data class TermuxStorageRefusal(val path: String, val reason: String, val processes: List<String> = emptyList()) {
    val inUse: Boolean get() = reason == "in_use"
}

public data class TermuxStorageCleanResult(
    val freedBytes: Long,
    val removed: List<TermuxStoragePath>,
    val refused: List<TermuxStorageRefusal>,
    val rescanRequired: Boolean = false,
) {
    val inUse: TermuxStorageRefusal? get() = refused.firstOrNull { it.inUse }

    public companion object {
        /** The clean verb's JSON; null when it is not an object. */
        public fun parse(json: String): TermuxStorageCleanResult? {
            val decoded = decodeJson(json.trim()).asMap() ?: return null
            return TermuxStorageCleanResult(
                freedBytes = decoded["freed_bytes"].asLong(),
                rescanRequired = decoded["rescan_required"].isTrue(),
                removed =
                    decoded["removed"].asList().mapNotNull { raw ->
                        raw.asMap()?.let { TermuxStoragePath(it["path"].asText() ?: "", it["bytes"].asLong()) }
                    },
                refused =
                    decoded["refused"].asList().mapNotNull { raw ->
                        raw.asMap()?.let { map ->
                            TermuxStorageRefusal(
                                path = map["path"].asText() ?: "",
                                reason = map["reason"].asText() ?: "",
                                processes = map["processes"].asList().map { it.asText() ?: "null" },
                            )
                        }
                    },
            )
        }
    }
}

/** Runs the storage verbs of `~/.oc/tools.sh` (port of `TermuxStorage`). */
public class TermuxStorage(private val tools: TermuxToolRunner) {
    private val started = Regex("(^|\n)tools-(started|already-running):[0-9]+")

    /** Starts a background scan; a scan already running is left alone. No acknowledgement is a [Failure.Termux]. */
    public suspend fun startScan(): Outcome<Unit> =
        tools.runTool("storage-scan", "", DefaultToolTimeout).flatMap { out ->
            if (started.containsMatchIn(out.trim())) Unit.ok() else Failure.Termux().failed()
        }

    public suspend fun status(): Outcome<TermuxStorageScanStatus> =
        tools.runTool("storage-status", "", DefaultToolTimeout).map { TermuxStorageScanStatus.parse(it) }

    public suspend fun summary(): Outcome<TermuxStorageSummary> =
        tools.runTool("storage-summary", "", 10.seconds).map { TermuxStorageSummary.parse(it) }

    public suspend fun cancelScan(): Outcome<Unit> =
        tools.runTool("storage-cancel", "", DefaultToolTimeout).map { }

    /** Cleans one category; only [TermuxStorageCategoryKey.BuildCaches] is ever removable (the script re-checks). */
    public suspend fun clean(category: TermuxStorageCategoryKey): Outcome<TermuxStorageCleanResult> =
        tools.runTool("storage-clean", category.wireName, 5.minutes).flatMap { out ->
            TermuxStorageCleanResult.parse(out)?.ok() ?: Failure.UnreadableReply().failed()
        }
}

public enum class TermuxByteUnit { B, KB, MB, GB }

public data class SplitBytes(val value: String, val unit: TermuxByteUnit)

/** "11.6 GB", "812 MB", "3 KB": one decimal above a gigabyte, none below. Locale-neutral digits. */
public fun splitBytes(bytes: Long): SplitBytes {
    val gb = 1024L * 1024 * 1024
    val mb = 1024L * 1024
    if (bytes >= gb) {
        val tenths = (bytes * 10.0 / gb).roundToLong()
        val value = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
        return SplitBytes(value, TermuxByteUnit.GB)
    }
    if (bytes >= mb) return SplitBytes("${bytes / mb}", TermuxByteUnit.MB)
    if (bytes >= 1024) return SplitBytes("${bytes / 1024}", TermuxByteUnit.KB)
    return SplitBytes("$bytes", TermuxByteUnit.B)
}

private val rootfsPath =
    Regex(
        "^/data/data/com\\.termux/files/usr/var/lib/proot-distro/" +
            "(?:containers/opencode-ubuntu/rootfs|installed-rootfs/opencode-ubuntu)",
    )

/** Shortens a phone path for display: the Ubuntu rootfs becomes `ubuntu:`, Termux's prefix `termux:`, its home `~`. */
public fun displayTermuxPath(path: String): String {
    rootfsPath.find(path)?.let { return "ubuntu:" + path.substring(it.range.last + 1) }
    if (path.startsWith("$TERMUX_HOME/")) return "~" + path.substring(TERMUX_HOME.length)
    if (path.startsWith("$TERMUX_PREFIX/")) return "termux:" + path.substring(TERMUX_PREFIX.length)
    return path
}
