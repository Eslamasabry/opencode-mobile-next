package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeClock(var nowMs: Long = START_MS) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)

    fun advance(ms: Long) {
        nowMs += ms
    }

    companion object {
        /** 2026-11-01T10:15:00Z: mid-hour, mid-day. */
        const val START_MS: Long = 1_793_528_100_000L
    }
}

/** Deterministic ids in the exact shape the app generates (32 lowercase hex). */
internal class CountingIds : IdSource {
    val issued = mutableListOf<String>()

    override fun newId(): String = (issued.size + 1).toString(16).padStart(32, 'a').also { issued += it }
}

internal val TestSystem =
    SystemProps(
        appVersion = "3.0.0",
        appBuild = 60,
        channel = ReleaseChannel.Preview,
        os = OsFamily.Android,
        osMajor = 36,
        device = DeviceClass.Phone,
        lang = "en",
        isDebug = false,
    )

/** A remote sink that records each batch and can be told to fail. */
internal class RecordingRemoteSink(override val host: String? = "collector.example") : AnalyticsSink {
    val batches = mutableListOf<WireBatch>()
    var fail: Boolean = false
    var calls = 0

    override val id: SinkId get() = SinkId.Custom
    override val kind: SinkKind get() = SinkKind.Remote

    override suspend fun send(batch: WireBatch): Outcome<Delivery> {
        calls++
        if (fail) return Outcome.Failed(Failure.Network())
        batches += batch
        return Outcome.Ok(Delivery(WireCodec.encode(batch)))
    }
}

/** A sink whose send throws, to prove a broken sink never surfaces. */
internal class ThrowingSink : AnalyticsSink {
    override val id: SinkId get() = SinkId.Custom
    override val kind: SinkKind get() = SinkKind.Remote
    override val host: String? get() = "collector.example"

    override suspend fun send(batch: WireBatch): Outcome<Delivery> = error("boom with secret text")
}

internal val Online = DeviceConditions(NetworkKind.Unmetered, validated = true, foreground = true, dataSaver = false)

internal fun wire(
    name: String = "app_start",
    props: Map<String, String> = mapOf("kind" to "Cold", "duration" to "Under1S"),
    hourUtc: Long = 1_793_527_200_000L,
    install: String = "0123456789abcdef0123456789abcdef",
    session: String = "fedcba9876543210fedcba9876543210",
    system: SystemProps = TestSystem,
): WireEvent = WireEvent(name, hourUtc, install, session, system, props)
