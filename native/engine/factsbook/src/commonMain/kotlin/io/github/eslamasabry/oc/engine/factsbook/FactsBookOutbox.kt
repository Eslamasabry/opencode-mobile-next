package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where the outbox keeps its text between runs: the secret store's `oc.factsbookOutbox.<id>` (a note is private). */
public interface FactsBookOutboxStore {
    public suspend fun read(): String?

    /** Null removes it. False when the store refused. */
    public suspend fun write(value: String?): Boolean
}

public class MemoryOutboxStore(public var value: String? = null) : FactsBookOutboxStore {
    override suspend fun read(): String? = value

    override suspend fun write(value: String?): Boolean {
        this.value = value
        return true
    }
}

/** One capture waiting on this phone. [key] is its idempotency key: a resend is never saved twice. */
public data class OutboxEntry(
    val id: String,
    val key: String,
    val request: CaptureRequest,
    val createdAt: Instant,
    val retries: Long = 0,
    /** The node refused it; it stays until the person decides. */
    val refused: Boolean = false,
) {
    val bytes: Int get() = request.text.encodeToByteArray().size

    override fun toString(): String = "OutboxEntry($id)"

    /** F: factsbook_outbox.dart `toJson`, so the Flutter app and this one read each other's outbox. */
    public fun toJson(): JsonObject =
        JsonObject(
            buildMap {
                put("id", JsonPrimitive(id))
                put("key", JsonPrimitive(key))
                put("request", request.toJson())
                put("created_at", JsonPrimitive(createdAt.toString()))
                put("retries", JsonPrimitive(retries))
                if (refused) put("refused", JsonPrimitive(true))
            }
        )

    /** The Not saved yet row for it. */
    public fun asEntry(): CouldNotSaveEntry =
        CouldNotSaveEntry(
            id = id,
            text = request.text,
            reason = if (refused) CouldNotSaveReason.ValidationRejected else CouldNotSaveReason.NodeUnreachable,
            createdAt = createdAt,
            retries = retries,
            state = CouldNotSaveState.Kept,
            onPhone = true,
        )

    internal companion object {
        fun read(json: JsonObject): OutboxEntry =
            OutboxEntry(
                id = json.need("id"),
                key = json.need("key"),
                request = CaptureRequest.read(json.obj("request") ?: throw FactsBookShapeError("request")),
                createdAt = json.time("created_at"),
                retries = json.long("retries") ?: 0,
                refused = json.isTrue("refused"),
            )
    }
}

/** What keeping a capture on the phone came to. */
public sealed interface OutboxAdd {
    public data class Kept(val entry: OutboxEntry) : OutboxAdd

    /** The phone is full: the newest capture is refused and the text stays where it was typed. */
    public data object Full : OutboxAdd
}

/**
 * Captures made while the node could not be reached, held on this phone (F: factsbook_outbox.dart): at most
 * [MAX_ENTRIES] notes and [MAX_BYTES]; when full the newest is refused and the oldest is never dropped; entries stay
 * until sent or discarded by the person. One lane: every change reads, edits and writes under one lock.
 */
public class FactsBookOutbox(private val store: FactsBookOutboxStore, private val clock: Clock) {
    private val lock = Mutex()
    private val list = mutableListOf<OutboxEntry>()
    private var loaded = false
    private var serial = 0

    public val entries: List<OutboxEntry> get() = list.toList()

    public suspend fun load(): Unit = lock.withLock { loadLocked() }

    private suspend fun loadLocked() {
        if (loaded) return
        store.read()?.let { raw ->
            // A damaged blob is not a reason to lose what could be read.
            val items = (try { Json.parseToJsonElement(raw) } catch (e: IllegalArgumentException) { null }) as? JsonArray
            items?.forEach { item ->
                val entry =
                    try {
                        (item as? JsonObject)?.let(OutboxEntry::read)
                    } catch (e: IllegalStateException) {
                        null
                    }
                entry?.let(list::add)
            }
        }
        loaded = true
    }

    private suspend fun save(): Boolean =
        store.write(if (list.isEmpty()) null else JsonArray(list.map(OutboxEntry::toJson)).toString())

    /** Keeps [request], or [OutboxAdd.Full] when it would not fit. */
    public suspend fun add(request: CaptureRequest, key: String): OutboxAdd =
        lock.withLock {
            loadLocked()
            val bytes = request.text.encodeToByteArray().size
            if (list.size >= MAX_ENTRIES || list.sumOf { it.bytes } + bytes > MAX_BYTES) return@withLock OutboxAdd.Full
            val now = clock.now()
            val entry = OutboxEntry("out_${now.toEpochMilliseconds() * 1000}_${serial++}", key, request, now)
            list += entry
            save()
            OutboxAdd.Kept(entry)
        }

    public suspend fun remove(id: String): Unit =
        lock.withLock {
            loadLocked()
            list.removeAll { it.id == id }
            save()
        }

    /**
     * Sends what is waiting (one batch, oldest first). A call that fails to reach the node leaves everything in place
     * and returns its error. Answers how many were accepted (or already there).
     */
    public suspend fun flush(gateway: FactsBookGateway, only: String? = null): FbResult<Int> =
        lock.withLock {
            loadLocked()
            val batch = list.filter { !it.refused && (only == null || it.id == only) }.take(MAX_ENTRIES)
            if (batch.isEmpty()) return@withLock FbResult.Ok(0)
            val receipts =
                when (val sent = gateway.sendOutbox(batch.map { OutboxEvent(it.key, it.request.toJson()) })) {
                    is FbResult.Err -> return@withLock sent
                    is FbResult.Ok -> sent.value
                }
            var accepted = 0
            batch.forEachIndexed { position, entry ->
                // Matched by key; a refusal whose key was not usable comes back empty and matches by position.
                val receipt =
                    receipts.lastOrNull { it.key == entry.key }
                        ?: receipts.getOrNull(position)?.takeIf { receipts.size == batch.size && it.key.isEmpty() }
                val index = list.indexOfFirst { it.id == entry.id }
                if (index < 0) return@forEachIndexed
                when {
                    receipt == null -> list[index] = entry.copy(retries = entry.retries + 1)
                    receipt.status == OutboxReceiptStatus.Refused ->
                        list[index] = entry.copy(retries = entry.retries + 1, refused = true)
                    else -> {
                        list.removeAt(index)
                        accepted += 1
                    }
                }
            }
            save()
            FbResult.Ok(accepted)
        }

    public companion object {
        public const val MAX_ENTRIES: Int = 100
        public const val MAX_BYTES: Int = 5 * 1024 * 1024
    }
}
