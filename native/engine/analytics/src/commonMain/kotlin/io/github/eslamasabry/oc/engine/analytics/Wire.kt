package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The envelope facts every event carries (plan 16 §5.2 SystemProps, plus the owner's "device class"). App-supplied,
 * checked by [PrivacyScrubber.checkSystem] before anything is queued.
 */
@Serializable
public data class SystemProps(
    /** `major.minor.patch` with an optional lowercase suffix, for example `3.0.0` or `3.0.0-preview`. */
    val appVersion: String,
    val appBuild: Int,
    val channel: ReleaseChannel,
    val os: OsFamily,
    /** Android API level or the desktop OS major version. */
    val osMajor: Int,
    val device: DeviceClass,
    /** A bare language code, one of the app's own languages (two or three lowercase letters). */
    val lang: String,
    val isDebug: Boolean,
)

/**
 * One event as it leaves the phone. Constructed only inside this module, after the scrubber passed it.
 * [install] is the rotating random install id; [session] is per process, rotated after 60 idle minutes, never stored.
 */
@ConsistentCopyVisibility
@Serializable
public data class WireEvent internal constructor(
    val name: String,
    /** Epoch milliseconds truncated to the hour (UTC). */
    val hourUtc: Long,
    val install: String,
    val session: String,
    val system: SystemProps,
    /** Wire key → enum name, `true`/`false`, a bucket name or a versionCode. In schema order. */
    val props: Map<String, String>,
)

/** What a sink receives: at most [EventQueue.MAX_BATCH] events. */
@Serializable
public data class WireBatch(val events: List<WireEvent>)

/** Our own JSON (the Custom collector format of plan 16 §5.5 and the debug sink). Deterministic key order. */
public object WireCodec {
    @OptIn(ExperimentalSerializationApi::class)
    internal val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            exceptionsWithDebugInfo = false
        }

    public fun encode(batch: WireBatch): String = json.encodeToString(WireBatch.serializer(), batch)

    public fun encode(event: WireEvent): String = json.encodeToString(WireEvent.serializer(), event)
}

internal const val HOUR_MS: Long = 3_600_000L
internal const val DAY_MS: Long = 24 * HOUR_MS

internal fun truncateToHour(epochMs: Long): Long = epochMs - epochMs.mod(HOUR_MS)
