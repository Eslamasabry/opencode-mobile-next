package io.github.eslamasabry.oc.engine.composer.phrases

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** The moment of a chat a phrase was sent in (Flutter `PhraseMoment`; [wire] is the stored Dart enum name). */
public enum class PhraseMoment(public val wire: String) {
    /** The agent is busy or streaming. */
    Working("working"),

    /** The last turn was cut off, stopped, interrupted or ended in an error. */
    StoppedPartway("stoppedPartway"),

    /** The last finished turn edited files. */
    FinishedWithChanges("finishedWithChanges"),

    /** Finished without edits. */
    Idle("idle"),
}

/** One chip: the words to send, and whether they are a built-in starter rather than learned. */
public data class PhraseChip(val text: String, val starter: Boolean = false)

/** The pure rules of `lib/state/phrase_chips.dart` (plan 09 §10.4). */
public object PhraseRules {
    public const val MAX_LENGTH: Int = 40
    public const val MAX_ENTRIES: Int = 50
    public const val MAX_HIDDEN: Int = 100
    public const val MIN_SENDS: Int = 2
    public const val MAX_CHIPS: Int = 4
    public val HALF_LIFE_MS: Long = 14.days.inWholeMilliseconds

    // Dart's RegExp \s and \S follow JavaScript: Unicode spaces count. Java's \s is ASCII only.
    private const val SPACE = "\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    private val SPACES = Regex("[$SPACE]+")
    private val TRAILING = Regex("[$SPACE.!?,;:…؟،؛]+$")
    private val LONG_RUN = Regex("[^$SPACE]{20,}")

    private val REDO =
        setOf(
            "try again", "try that again", "try it again", "retry", "run again", "run it again", "run that again",
            "do it again", "do that again",
        )

    /** Case-folded, whitespace collapsed, trailing punctuation trimmed: "Run the tests." = "run the tests". */
    public fun normalize(text: String): String = text.trim().lowercase().replace(SPACES, " ").replace(TRAILING, "")

    /** Short, one line, plain words; never commands, shell, mentions, files or anything secret-like (B-CHAT-078). */
    public fun isRecordable(text: String, redactor: Redactor, hasAttachments: Boolean = false): Boolean {
        if (hasAttachments) return false
        val value = text.trim()
        if (value.length < 2 || value.length > MAX_LENGTH) return false
        if ('\n' in value || '\r' in value) return false
        if (value.startsWith('/') || value.startsWith('!')) return false
        if ('@' in value) return false
        if (normalize(value).isEmpty()) return false
        return !looksSecret(value, redactor)
    }

    /** The app's secret detector, or any 20+ character run without spaces (a pasted key on its own). */
    public fun looksSecret(text: String, redactor: Redactor): Boolean = redactor.containsSecret(text) || LONG_RUN.containsMatchIn(text)

    /** "Try again" and its kin: never offered while the agent works (B-CHAT-084). */
    public fun isRedoPhrase(text: String): Boolean = normalize(text) in REDO
}

/**
 * The person's own short phrases learned on this phone, per server profile (plan 09 §10.4, B-CHAT-077…084,
 * B-STATE-059). One JSON blob under `oc.phraseChips.<profileId>`:
 * `{"v":1,"hidden":[…],"entries":{key:{"t","n","l","m":{moment:{"n","l"}}}}}` — the Flutter format, read unchanged.
 * A corrupt blob reads as nothing learned. After [close] (profile deletion) no write lands.
 */
public class PhraseChipStore(
    private val prefs: ComposerPrefs,
    public val profileId: String,
    private val clock: Clock,
    private val redactor: Redactor,
    private val changed: () -> Unit = {},
) {
    public companion object {
        /** The device-wide switch; not profile data, so the profile sweep leaves it. */
        public const val ENABLED_KEY: String = "oc.phraseChips.enabled"

        public fun keyFor(profileId: String): String = "oc.phraseChips.$profileId"

        public fun enabled(prefs: ComposerPrefs): Boolean = prefs.bool(ENABLED_KEY) ?: true
    }

    private class Use(var count: Long = 0, var last: Long = 0)

    private class Entry(var text: String) {
        var total = 0L
        var last = 0L
        val moments = LinkedHashMap<String, Use>()
    }

    private class Blob {
        val hidden = LinkedHashSet<String>()
        val entries = LinkedHashMap<String, Entry>()
    }

    private val lane = Mutex()
    private var closed = false
    private val key = keyFor(profileId)

    private fun read(): Blob {
        val raw = prefs.string(key) ?: return Blob()
        return try {
            decode(parseJson(raw) as? JsonObject ?: return Blob())
        } catch (e: StoredFormatException) {
            Blob()
        } catch (e: IllegalArgumentException) {
            Blob()
        }
    }

    private fun decode(json: JsonObject): Blob {
        val blob = Blob()
        (json["hidden"] as? JsonArray)?.forEach { item ->
            val p = item as? JsonPrimitive
            if (p != null && p.isString) blob.hidden += p.content
        }
        val entries = json["entries"] as? JsonObject ?: return blob
        for ((k, value) in entries) {
            if (value !is JsonObject) continue
            val t = value["t"] as? JsonPrimitive
            if (t == null || !t.isString || t.content.isEmpty()) continue
            val entry = Entry(t.content)
            entry.total = num(value["n"])
            entry.last = num(value["l"])
            (value["m"] as? JsonObject)?.forEach { (moment, use) ->
                if (use is JsonObject) entry.moments[moment] = Use(num(use["n"]), num(use["l"]))
            }
            blob.entries[k] = entry
        }
        return blob
    }

    private fun num(e: Any?): Long {
        val p = e as? JsonPrimitive ?: return 0
        if (p.isString) return 0
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: 0
    }

    private fun encode(blob: Blob): String =
        buildJsonObject {
            put("v", JsonPrimitive(1))
            put("hidden", JsonArray(blob.hidden.map(::JsonPrimitive)))
            put(
                "entries",
                buildJsonObject {
                    for ((k, e) in blob.entries) {
                        put(
                            k,
                            buildJsonObject {
                                put("t", JsonPrimitive(e.text))
                                put("n", JsonPrimitive(e.total))
                                put("l", JsonPrimitive(e.last))
                                put(
                                    "m",
                                    buildJsonObject {
                                        for ((m, u) in e.moments) {
                                            put(m, buildJsonObject {
                                                put("n", JsonPrimitive(u.count))
                                                put("l", JsonPrimitive(u.last))
                                            })
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }.toString()

    private suspend fun write(blob: Blob): Boolean {
        if (closed) return false
        val ok = prefs.commit { putString(key, encode(blob)) }
        if (ok) changed()
        return ok
    }

    /** Counts one send of [text] in [moment]. True when it was kept. */
    public suspend fun record(text: String, moment: PhraseMoment, hasAttachments: Boolean = false): Boolean =
        lane.withLock {
            if (closed || !PhraseRules.isRecordable(text, redactor, hasAttachments)) return@withLock false
            val k = PhraseRules.normalize(text)
            val blob = read()
            if (k in blob.hidden) return@withLock false
            val now = clock.now().toEpochMilliseconds()
            val entry = blob.entries.getOrPut(k) { Entry(text.trim()) }
            entry.text = text.trim()
            entry.total += 1
            entry.last = now
            val use = entry.moments.getOrPut(moment.wire) { Use() }
            use.count += 1
            use.last = now
            if (blob.entries.size > PhraseRules.MAX_ENTRIES) {
                blob.entries.entries.sortedBy { it.value.last }.take(blob.entries.size - PhraseRules.MAX_ENTRIES)
                    .map { it.key }.forEach { blob.entries.remove(it) }
            }
            write(blob)
        }

    /**
     * The chips for [moment], best first, at most [limit]: the moment's [starters] lead (a learned starter keeps the
     * person's spelling), then learned phrases sent [PhraseRules.MIN_SENDS]+ times and at least once in this moment,
     * ranked by count with a 14-day half-life. Hidden phrases and [exclude] are left out (B-CHAT-079…082).
     */
    public fun chipsFor(
        moment: PhraseMoment,
        starters: List<String> = emptyList(),
        exclude: Set<String> = emptySet(),
        limit: Int = PhraseRules.MAX_CHIPS,
    ): List<PhraseChip> {
        val blob = read()
        val skip = exclude.map(PhraseRules::normalize).toSet()
        val now = clock.now().toEpochMilliseconds()
        class Scored(val score: Double, val last: Long, val key: String, val entry: Entry)
        val scored = mutableListOf<Scored>()
        for ((k, entry) in blob.entries) {
            if (entry.total < PhraseRules.MIN_SENDS) continue
            if (k in blob.hidden || k in skip) continue
            if (moment == PhraseMoment.Working && PhraseRules.isRedoPhrase(k)) continue
            val use = entry.moments[moment.wire] ?: continue
            if (use.count < 1) continue
            val age = max(0L, now - use.last)
            scored += Scored(use.count * 0.5.pow(age.toDouble() / PhraseRules.HALF_LIFE_MS), use.last, k, entry)
        }
        scored.sortWith(compareByDescending<Scored> { it.score }.thenByDescending { it.last })
        val chips = mutableListOf<PhraseChip>()
        val taken = HashSet<String>()
        for (starter in starters) {
            if (chips.size >= limit) break
            val k = PhraseRules.normalize(starter)
            if (k in taken || k in blob.hidden || k in skip) continue
            val learned = blob.entries[k]
            chips += if (learned != null && learned.total >= PhraseRules.MIN_SENDS) PhraseChip(learned.text) else PhraseChip(starter, starter = true)
            taken += k
        }
        for (s in scored) {
            if (chips.size >= limit) break
            if (!taken.add(s.key)) continue
            chips += PhraseChip(s.entry.text)
        }
        return chips
    }

    /** Never suggest [text] again (until [showHidden]); the oldest hidden phrase goes first when full. */
    public suspend fun hide(text: String): Boolean =
        lane.withLock {
            val k = PhraseRules.normalize(text)
            if (k.isEmpty() || closed) return@withLock false
            val blob = read()
            blob.hidden.remove(k)
            blob.hidden.add(k)
            while (blob.hidden.size > PhraseRules.MAX_HIDDEN) blob.hidden.remove(blob.hidden.first())
            write(blob)
        }

    public val hiddenCount: Int get() = read().hidden.size

    public val learnedCount: Int get() = read().entries.size

    /** "Show hidden phrases". */
    public suspend fun showHidden(): Boolean =
        lane.withLock {
            val blob = read()
            if (blob.hidden.isEmpty()) return@withLock true
            blob.hidden.clear()
            write(blob)
        }

    /** Before the profile sweep: refuses later writes and waits for the one running. */
    public suspend fun close() {
        lane.withLock { closed = true }
    }
}

/** One [PhraseChipStore] per profile, so the chat and Settings share it; [changes] bumps after every change. */
public class PhraseChipStores(private val prefs: ComposerPrefs, private val clock: Clock, private val redactor: Redactor) {
    private val stores = HashMap<String, PhraseChipStore>()
    private val _changes = MutableStateFlow(0)

    public val changes: StateFlow<Int> = _changes.asStateFlow()

    public fun forProfile(profileId: String): PhraseChipStore =
        stores.getOrPut(profileId) { PhraseChipStore(prefs, profileId, clock, redactor) { _changes.value += 1 } }

    public fun enabled(): Boolean = PhraseChipStore.enabled(prefs)

    public suspend fun setEnabled(value: Boolean): Boolean {
        val ok = prefs.commit { putBool(PhraseChipStore.ENABLED_KEY, value) }
        if (ok) _changes.value += 1
        return ok
    }

    /** Profile deletion: drains and closes that profile's store before the sweep removes its key. */
    public suspend fun closeProfile(profileId: String) {
        forProfile(profileId).close()
    }

    /** A server added again under a removed id gets a fresh, open store (the closed one stays closed). */
    public fun reopenProfile(profileId: String) {
        stores.remove(profileId)
    }
}
