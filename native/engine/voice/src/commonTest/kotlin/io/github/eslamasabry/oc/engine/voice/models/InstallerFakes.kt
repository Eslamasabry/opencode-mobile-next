package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

internal fun sha(bytes: ByteArray): String = PureSha256().apply { update(bytes) }.digestHex()

internal fun bytesOf(size: Int, seed: Int): ByteArray = Random(seed).nextBytes(size)

/** A small two-file pack whose pins are the real hashes of [contents]. */
internal fun testPack(id: String = "base", contents: List<ByteArray> = listOf(bytesOf(3_000, 1), bytesOf(5_000, 2))) =
    ModelPackSpec(
        id = PackId(id),
        kind = PackKind.Speech,
        repo = "example/test-$id",
        revision = "rev1",
        files =
            contents.mapIndexed { i, body ->
                ModelFile(listOf("enc.int8.onnx", "dec.int8.onnx", "tokens.txt")[i], body.size.toLong(), sha(body))
            }.toImmutableList(),
        minTotalRamMb = 1024,
    )

internal fun bodiesOf(pack: ModelPackSpec, contents: List<ByteArray>): MutableMap<String, ByteArray> =
    pack.files.zip(contents).associate { (file, body) -> pack.urlFor(file) to body }.toMutableMap()

internal fun tempRoot(): String {
    val dir = "${SystemTemporaryDirectory}/voice-test-${Random.nextLong().toULong()}"
    SystemModelFiles().createDirectories(dir)
    return Path(dir).toString()
}

internal class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_767_225_600_000 + scheduler.currentTime)
}

internal class FakeResponse(
    override val status: Int,
    private val body: ByteArray = ByteArray(0),
    override val contentLength: Long? = body.size.toLong(),
    override val contentRange: String? = null,
    override val location: String? = null,
    private val chunk: Int = 1_000,
    /** Stall (suspend until cancelled) once this many bytes were read. */
    private val stallAfter: Int? = null,
) : FetchResponse {
    private var position = 0
    var closed = false

    override suspend fun read(buffer: ByteArray): Int {
        if (stallAfter != null && position >= stallAfter) awaitCancellation()
        if (position >= body.size) return -1
        val n = minOf(chunk, buffer.size, body.size - position, (stallAfter ?: Int.MAX_VALUE) - position)
        body.copyInto(buffer, 0, position, position + n)
        position += n
        return n
    }

    override fun close() {
        closed = true
    }
}

/** Serves [bodies] by URL, honouring Range like huggingface.co; [script] can answer first. */
internal class FakeFetcher(val bodies: MutableMap<String, ByteArray>) : ModelFetcher {
    val requests = mutableListOf<FetchRequest>()
    val responses = mutableListOf<FakeResponse>()
    var script: (suspend (FetchRequest) -> Outcome<FetchResponse>?)? = null
    var stallAfter: Int? = null

    override suspend fun open(request: FetchRequest): Outcome<FetchResponse> {
        requests += request
        script?.invoke(request)?.let { return it }
        val body = bodies[request.url] ?: return FakeResponse(404).also { responses += it }.ok()
        val start = request.rangeStart
        val response =
            if (start != null && start > 0) {
                FakeResponse(
                    206,
                    body.copyOfRange(start.toInt(), body.size),
                    contentRange = "bytes $start-${body.size - 1}/${body.size}",
                    stallAfter = stallAfter,
                )
            } else {
                FakeResponse(200, body, stallAfter = stallAfter)
            }
        responses += response
        return response.ok()
    }
}

/** Real files, with failures on demand (B-MEDIA-027 publishing and cleanup failures). */
internal class FlakyFiles(private val real: ModelFiles = SystemModelFiles()) : ModelFiles by real {
    var failMove: ((from: String, to: String) -> Boolean)? = null
    var failDelete: ((String) -> Boolean)? = null
    var fullAfterBytes: Long? = null
    private var written = 0L

    override fun move(from: String, to: String) {
        if (failMove?.invoke(from, to) == true) throw IllegalStateException("move refused")
        real.move(from, to)
    }

    override fun delete(path: String) {
        if (failDelete?.invoke(path) == true) throw IllegalStateException("delete refused")
        real.delete(path)
    }

    override fun openAppend(path: String): ByteSink {
        val sink = real.openAppend(path)
        return object : ByteSink by sink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                val limit = fullAfterBytes
                if (limit != null && written + length > limit) throw StorageFullException()
                written += length
                sink.write(bytes, offset, length)
            }
        }
    }
}
