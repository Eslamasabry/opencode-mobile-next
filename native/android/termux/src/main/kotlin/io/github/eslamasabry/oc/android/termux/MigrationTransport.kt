package io.github.eslamasabry.oc.android.termux

import io.github.eslamasabry.oc.core.common.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What a Termux RUN_COMMAND answered (Flutter TermuxCommandResult). `errorCode == -1` means Termux ran it. */
public data class TermuxCommandResult(
    val stdout: String,
    val stderr: String = "",
    val exitCode: Int,
    val errorCode: Int = -1,
    val errorMessage: String = "",
) {
    val successful: Boolean get() = errorCode == -1 && exitCode == 0

    // Never print what Termux said: its output and errors can hold paths or credentials.
    override fun toString(): String = "TermuxCommandResult(exitCode=$exitCode, errorCode=$errorCode)"
}

/**
 * Runs one bash script in Termux and returns its result (the app binds the `RUN_COMMAND` bridge, Flutter
 * `TermuxBridge.run`). Any exception it throws is discarded unread: bridge text can carry credentials.
 */
public fun interface MigrationTermuxRun {
    public suspend fun run(script: String, timeout: Duration): TermuxCommandResult
}

/** The Termux side of a move. Implementations signal refusals with the module's fixed codes. */
public interface TermuxMigrationTransport {
    public suspend fun inspect(): TermuxMigrationSource

    public suspend fun pack(jobId: String, item: TermuxMigrationItem, maxBytes: Long): TermuxMigrationArchive

    public suspend fun copy(
        jobId: String,
        item: TermuxMigrationItem,
        archive: TermuxMigrationArchive,
        destination: Path,
        cancelled: () -> Boolean,
    )

    public suspend fun cancel(jobId: String)
}

/**
 * Confidential archives cross only an authenticated IPv4 loopback socket (Flutter BridgeTermuxMigrationTransport).
 * Commands have their own deadline: a bridge timeout alone does not kill them. No output, source path, bearer or raw
 * exception is exposed to the caller.
 */
public class BridgeTermuxMigrationTransport(
    private val run: MigrationTermuxRun,
    private val dispatchers: AppDispatchers,
    private val random: SecureRandom = SecureRandom(),
) : TermuxMigrationTransport {
    public companion object {
        public const val MAXIMUM_BYTES: Long = 512L * 1024 * 1024
        public const val CHUNK_BYTES: Long = 1024L * 1024
        public val COMMAND_TIMEOUT: Duration = 110.seconds
        private val BODY_TIMEOUT = 40.seconds
        private val POLL = 100.milliseconds

        private fun script(name: String): String =
            checkNotNull(BridgeTermuxMigrationTransport::class.java.getResourceAsStream("scripts/$name.sh")) { "script $name" }
                .use { String(it.readBytes(), Charsets.UTF_8) }

        private val COMMAND: String by lazy { script("command") }
        private val ROOTS: String by lazy { script("roots").trimEnd('\n') }
        private val INSPECT: String by lazy { script("inspect") }
        private val PACK: String by lazy { script("pack") }
        private val COPY: String by lazy { script("copy") }
        private val CANCEL: String by lazy { script("cancel") }

        /** One pass over `@@NAME@@` placeholders; every value is validated before it gets here. */
        internal fun fill(template: String, values: Map<String, String>): String =
            Regex("@@([A-Z]+)@@").replace(template) { match -> values[match.groupValues[1]] ?: error("placeholder") }
    }

    private val cancelledJobs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun id(value: String) {
        if (!JOB_ID.matches(value)) fail(TermuxMigrationFailure.invalidSelection)
    }

    private suspend fun command(script: String): String {
        val wrapped = fill(COMMAND, mapOf("SCRIPT" to script.trimEnd('\n')))
        try {
            // Stderr is suppressed inside the timeout too: callbacks never carry file names.
            val result = withContext(dispatchers.io) { withTimeout(COMMAND_TIMEOUT) { run.run(wrapped, COMMAND_TIMEOUT) } }
            if (!result.successful) fail(TermuxMigrationFailure.unavailable)
            val output = result.stdout.trim()
            val failures = output.split('\n').filter { it.startsWith("failure|") }
            if (failures.isNotEmpty()) {
                fail(TermuxMigrationFailure.byName(failures.last().substring(8)) ?: TermuxMigrationFailure.unavailable)
            }
            if (output.length > 2048) fail(TermuxMigrationFailure.unavailable)
            return output
        } catch (e: TermuxMigrationException) {
            throw e
        } catch (_: TimeoutCancellationException) {
            fail(TermuxMigrationFailure.timedOut)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(TermuxMigrationFailure.unavailable)
        }
    }

    override suspend fun inspect(): TermuxMigrationSource {
        val output = command(fill(INSPECT, mapOf("ROOTS" to ROOTS)))
        try {
            val items = ArrayList<TermuxMigrationSize>()
            var free: Long? = null
            var isolated = false
            for (line in output.lines().let { if (output.isEmpty()) emptyList() else it }) {
                val parts = line.split('|')
                when {
                    parts.size == 2 && parts[0] == "free" -> free = parts[1].toLong()
                    parts.size == 2 && line == "isolated|1" -> isolated = true
                    parts.size == 3 && parts[0] == "problem" -> {
                        val item = TermuxMigrationItem.byName(parts[1]) ?: throw IllegalArgumentException()
                        val problem = TermuxMigrationFailure.byName(parts[2]) ?: throw IllegalArgumentException()
                        if (items.any { it.item == item }) throw IllegalArgumentException()
                        items.add(TermuxMigrationSize(item, 0, 0, problem = problem))
                    }
                    parts.size == 4 && parts[0] == "item" -> {
                        val item = TermuxMigrationItem.byName(parts[1]) ?: throw IllegalArgumentException()
                        val bytes = parts[2].toLong()
                        val count = parts[3].toLong()
                        if (bytes < 0 || bytes > MAXIMUM_BYTES || count < 0 || count > 20_000 || items.any { it.item == item }) {
                            throw IllegalArgumentException()
                        }
                        items.add(TermuxMigrationSize(item, bytes, count))
                    }
                    else -> throw IllegalArgumentException()
                }
            }
            val measured = free
            if (measured == null || measured < 0) throw IllegalArgumentException()
            return TermuxMigrationSource(items = items, freeBytes = measured, oc2Isolated = isolated)
        } catch (_: Exception) {
            fail(TermuxMigrationFailure.unavailable)
        }
    }

    override suspend fun pack(jobId: String, item: TermuxMigrationItem, maxBytes: Long): TermuxMigrationArchive {
        id(jobId)
        if (maxBytes <= 0 || maxBytes > MAXIMUM_BYTES) fail(TermuxMigrationFailure.tooLarge)
        cancelledJobs.remove(jobId)
        val output = command(fill(PACK, mapOf("ROOTS" to ROOTS, "JOB" to jobId, "ITEM" to item.name, "MAX" to maxBytes.toString())))
        val parts = output.split('|')
        val bytes = if (parts.size == 3) parts[1].toLongOrNull() else null
        if (parts.size != 3 || parts[0] != "archive" || bytes == null || bytes < 1024 || bytes > maxBytes || !SHA256.matches(parts[2])) {
            fail(TermuxMigrationFailure.invalidArchive)
        }
        return TermuxMigrationArchive(bytes = bytes, sha256 = parts[2])
    }

    override suspend fun copy(
        jobId: String,
        item: TermuxMigrationItem,
        archive: TermuxMigrationArchive,
        destination: Path,
        cancelled: () -> Boolean,
    ) {
        id(jobId)
        if (archive.bytes < 1024 || archive.bytes > MAXIMUM_BYTES || !SHA256.matches(archive.sha256)) {
            fail(TermuxMigrationFailure.invalidArchive)
        }
        val stopped = { cancelled() || jobId in cancelledJobs }
        fun check() {
            if (stopped()) fail(TermuxMigrationFailure.cancelled)
        }
        try {
            check()
            withContext(dispatchers.io) { transfer(jobId, item, archive, destination, stopped, ::check) }
        } catch (e: TermuxMigrationException) {
            if (stopped()) fail(TermuxMigrationFailure.cancelled)
            throw e
        } catch (_: TimeoutCancellationException) {
            fail(if (stopped()) TermuxMigrationFailure.cancelled else TermuxMigrationFailure.timedOut)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(if (stopped()) TermuxMigrationFailure.cancelled else TermuxMigrationFailure.storage)
        }
    }

    private suspend fun transfer(
        jobId: String,
        item: TermuxMigrationItem,
        archive: TermuxMigrationArchive,
        destination: Path,
        stopped: () -> Boolean,
        check: () -> Unit,
    ) {
        prepareDestination(jobId, destination)
        val checkpoint = destination.resolveSibling("${destination.fileName}.offset")
        var offset = 0L
        if (Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            val value = Json.parseToJsonElement(String(Files.readAllBytes(checkpoint), Charsets.UTF_8)) as? JsonObject
            val saved = (value?.get("offset") as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (value != null &&
                (value["sha256"] as? JsonPrimitive)?.content == archive.sha256 &&
                (value["bytes"] as? JsonPrimitive)?.longOrNull == archive.bytes &&
                saved != null && saved >= 0 && saved <= archive.bytes &&
                (saved == archive.bytes || saved % CHUNK_BYTES == 0L)
            ) {
                offset = saved
            }
        }
        Files.createDirectories(destination.parent)
        FileChannel.open(
            destination,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { target ->
            if (target.size() < offset) offset = 0
            target.truncate(offset)
            target.position(offset)
            mode(destination, 384)
            val token = Base64.getUrlEncoder().encodeToString(ByteArray(32).also(random::nextBytes))
            LoopbackReceiver(token, archive, target, checkpoint, offset).use { receiver ->
                coroutineScope {
                    val serving = launch { receiver.serve(this) }
                    val watching = launch {
                        while (true) {
                            delay(POLL)
                            if (stopped()) receiver.stop(TermuxMigrationException(TermuxMigrationFailure.cancelled))
                        }
                    }
                    try {
                        while (receiver.offset < archive.bytes) {
                            check()
                            val body = receiver.expect()
                            val at = receiver.offset
                            val script = fill(
                                COPY,
                                mapOf(
                                    "JOB" to jobId,
                                    "ITEM" to item.name,
                                    "BYTES" to archive.bytes.toString(),
                                    "SHA" to archive.sha256,
                                    "CHUNK" to CHUNK_BYTES.toString(),
                                    "SKIP" to (at / CHUNK_BYTES).toString(),
                                    "TOKEN" to token,
                                    "OFFSET" to at.toString(),
                                    "PORT" to receiver.port.toString(),
                                ),
                            )
                            try {
                                coroutineScope {
                                    listOf(
                                        async { command(script) },
                                        async { withTimeout(BODY_TIMEOUT) { body.await() } },
                                    ).awaitAll()
                                }
                            } catch (e: Throwable) {
                                // Settle the receiver too when Termux vanished before uploading: no live wait remains.
                                body.completeExceptionally(TermuxMigrationException(TermuxMigrationFailure.unavailable))
                                throw e
                            }
                            check()
                        }
                    } finally {
                        watching.cancel()
                        receiver.close()
                        serving.cancel()
                    }
                }
            }
        }
        if (sha256Of(destination) != archive.sha256) {
            Files.deleteIfExists(checkpoint)
            fail(TermuxMigrationFailure.checksumMismatch)
        }
    }

    override suspend fun cancel(jobId: String) {
        id(jobId)
        cancelledJobs.add(jobId)
        command(fill(CANCEL, mapOf("JOB" to jobId)))
    }

    private fun prepareDestination(jobId: String, destination: Path) {
        val job = destination.toAbsolutePath().parent
        val migrations = job?.parent
        // Restrict chmod to the transport's two owned staging directories.
        if (job == null || migrations == null || job.fileName?.toString() != jobId || migrations.fileName?.toString() != "migrations") {
            fail(TermuxMigrationFailure.destinationConflict)
        }
        for (directory in listOf(migrations, job)) {
            if (present(directory) && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                fail(TermuxMigrationFailure.destinationConflict)
            }
        }
        val name = destination.fileName.toString()
        for (path in listOf(destination, job.resolve("$name.offset"), job.resolve("$name.offset.tmp"))) {
            if (present(path) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) fail(TermuxMigrationFailure.destinationConflict)
        }
        Files.createDirectories(job)
        for (directory in listOf(migrations, job)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) fail(TermuxMigrationFailure.destinationConflict)
            mode(directory, 448)
        }
    }

    private fun present(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
}

/** Writes the resume checkpoint atomically and privately. */
internal fun writeCheckpoint(checkpoint: Path, archive: TermuxMigrationArchive, offset: Long) {
    val temporary = checkpoint.resolveSibling("${checkpoint.fileName}.tmp")
    val body = buildJsonObject {
        put("sha256", archive.sha256)
        put("bytes", archive.bytes)
        put("offset", offset)
    }
    writeFlushed(temporary, body.toString().toByteArray(Charsets.UTF_8))
    mode(temporary, 384)
    Files.move(temporary, checkpoint, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
