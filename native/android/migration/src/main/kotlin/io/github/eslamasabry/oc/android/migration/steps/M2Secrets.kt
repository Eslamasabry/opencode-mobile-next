package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.FlutterPrefsFile
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.MigrationSecretSink
import io.github.eslamasabry.oc.android.migration.ReentryKind
import io.github.eslamasabry.oc.android.migration.ReentryNotice
import io.github.eslamasabry.oc.android.migration.StepInterrupter
import io.github.eslamasabry.oc.android.migration.ledger.CredentialLedger
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.secure.FlutterSecret
import io.github.eslamasabry.oc.android.migration.readers.secure.FlutterSecureStorageReader
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.FssKeyUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.secure.FssStores
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.core.security.Redactor

/** Plan 05 §7.4 M2 and Appendix B: the kind decides what an unreadable entry does. */
internal enum class SecretKind(val reentry: ReentryKind?, val flagName: String?) {
    TypedPassword(ReentryKind.Password, "pw"),
    TypedToken(ReentryKind.Token, "codexToken"),
    SignIn(ReentryKind.GitHubSignIn, "githubToken"),
    Setup(ReentryKind.ByoSetup, "byoHostSecrets"),
    /** Made by the app; the owner renews it silently; never listed, never flagged. */
    AppMade(null, null),
    /** FactsBook (D6): copied, handled when ported; never listed. */
    Deferred(null, null),
    /** A2A (D6, S02-7, B-SEC-063): counted, NOT copied. */
    Dropped(null, null),
    /** A name no row of Appendix B covers: copied like a deferred one, never listed. */
    Other(null, null),
    ;

    companion object {
        /** [builtinIds]: profile ids of the built-in phone server (`oc.builtinServerOwner` and `oc.builtinRecovery.<id>`). */
        fun of(name: String, builtinIds: Set<String>): Pair<SecretKind, String?> {
            fun idAfter(prefix: String) = name.removePrefix(prefix)
            return when {
                name.startsWith("pw.") -> (if (idAfter("pw.") in builtinIds) AppMade else TypedPassword) to idAfter("pw.")
                name.startsWith("oc.codexToken.") -> TypedToken to idAfter("oc.codexToken.")
                name.startsWith("oc.githubToken.") -> SignIn to idAfter("oc.githubToken.")
                name.startsWith("oc.byoHostSecrets.") -> Setup to idAfter("oc.byoHostSecrets.")
                name.startsWith("oc.agentHostSecret.") -> AppMade to idAfter("oc.agentHostSecret.")
                name.startsWith("oc.quotaToken.") -> AppMade to idAfter("oc.quotaToken.")
                name.startsWith("oc.teamEngineAuth.") -> AppMade to idAfter("oc.teamEngineAuth.")
                name.startsWith("oc.factsbook") -> Deferred to null
                name.startsWith("oc.a2aBearer.") -> Dropped to null
                else -> Other to null
            }
        }

        /** The secret name behind a re-entry flag (`oc.secretReentry.<id>` lists the prefix only). */
        fun nameFor(flag: String, profileId: String): String? = when (flag) {
            "pw" -> "pw.$profileId"
            "codexToken" -> "oc.codexToken.$profileId"
            "githubToken" -> "oc.githubToken.$profileId"
            "byoHostSecrets" -> "oc.byoHostSecrets.$profileId"
            else -> null
        }

        fun kindOfFlag(flag: String): ReentryKind? = entries.firstOrNull { it.flagName == flag }?.reentry
    }
}

/** Why a name was not copied. Only these names (never exception text) reach the journal and the audit. */
internal enum class M2Failure { ReadBackMismatch, SecretStoreUnavailable }

/**
 * M2, secrets (plan 05 §7.4 M2, §7.5): decrypt the `flutter_secure_storage` 11.2.0 entries and re-store them in the
 * native secret store. Never writes, creates or deletes anything FSS; never applies `resetOnError`; never treats a
 * flagged secret as an empty password.
 */
internal class M2Secrets(
    private val paths: MigrationPaths,
    private val prefs: FlutterPrefsFile,
    private val unwrapper: FssKeyUnwrapper,
    private val sink: MigrationSecretSink,
    private val redactor: Redactor,
    private val ledger: CredentialLedger,
    private val interrupter: StepInterrupter,
    private val now: () -> Long,
) {
    sealed interface Result {
        data class Done(val counts: Map<String, Any?>, val flagged: Int) : Result
        data object WriteRefused : Result
    }

    suspend fun run(): Result {
        val stores = FssStores.fromDirectory(paths.sharedPrefs)
        val read = FlutterSecureStorageReader(unwrapper).read(stores)
        val logical = LogicalPrefs(prefs.live())
        val profileIds = logical.profileIds().toSet()
        val builtin = builtinIds(logical)

        val outcomes = sortedMapOf<String, Long>()
        val flaggedBy = sortedMapOf<String, Long>()
        fun count(key: String) { outcomes.merge(key, 1L, Long::plus) }
        val flags = LinkedHashMap<String, MutableList<Pair<String, String>>>() // id → (flag, reason)
        var written = 0

        for (secret in read.secrets) {
            val (kind, id) = SecretKind.of(secret.name, builtin)
            if (id != null && id !in profileIds) count("orphans")
            val failure: String? = when (secret) {
                is FlutterSecret.Failed -> secret.reason.name
                is FlutterSecret.Plain -> {
                    val value = secret.value.reveal()
                    register(value) // before any log line or store call (ADR §9.7)
                    if (kind == SecretKind.Dropped) {
                        count("countedOnly")
                        null
                    } else {
                        copy(secret.name, value, stores.data[FssFormat.entryName(secret.name)])?.name.also { f ->
                            if (f == null) {
                                written++
                                if (written == 1) interrupter.reached("M2:1")
                            }
                        }
                    }
                }
            }
            if (failure == null) continue
            when {
                kind == SecretKind.Dropped -> count("countedOnly")
                kind == SecretKind.AppMade -> count("renewedByOwner")
                kind.flagName != null && id != null && id in profileIds -> {
                    flags.getOrPut(id) { mutableListOf() } += kind.flagName to failure
                    flaggedBy.merge("${kind.name}.$failure", 1L, Long::plus)
                }
                else -> count("failedUnlisted")
            }
        }
        // `migrated` and `alreadyMigrated` are merged into `stored` by the runner's comparisons; both are kept here.
        if (flags.isNotEmpty() && !writeFlags(logical, flags)) return Result.WriteRefused
        interrupter.reached("M2:all")

        val counts = LinkedHashMap<String, Any?>()
        counts["found"] = read.census.secretEntries.toLong()
        counts["backupEntriesSkipped"] = read.census.backupEntries.toLong()
        counts["markers"] = read.census.markers.name
        counts["wrappedKeyPresent"] = read.census.wrappedKeyPresent
        counts["outcomes"] = outcomes
        counts["copied"] = tallies.toSortedMap()
        counts["flagged"] = flaggedBy
        return Result.Done(counts, flags.values.sumOf { it.size })
    }

    /**
     * The retry at the next three launches (plan 05 §7.4 M2): only names still flagged, only while the secret store has
     * no entry of that name AND the credential ledger has no record of it (a typed value, a renewal, a sign-out or a
     * reset always wins: "no value" after a reset is a tombstone, not a gap to refill; Astra B3). A name that now
     * decrypts and stores is unflagged. Returns false when the flag commit was refused.
     */
    suspend fun retryFlagged(): Boolean {
        val logical = LogicalPrefs(prefs.live())
        val records = logical.keys(LogicalPrefs.SECRET_REENTRY).associateWith { ReentryRecord.parse(logical.string(it)) }
        if (records.values.all { it == null }) return true
        val stores = FssStores.fromDirectory(paths.sharedPrefs)
        val plain = FlutterSecureStorageReader(unwrapper).read(stores).plain.associateBy { it.name }
        val edit = PrefsEdit()
        for ((key, record) in records) {
            record ?: continue
            val id = key.removePrefix(LogicalPrefs.SECRET_REENTRY)
            val keep = record.names.filter { flag ->
                val name = SecretKind.nameFor(flag, id) ?: return@filter true
                if (ledger.has(name)) return@filter true
                val stored = try { sink.read(name) } catch (@Suppress("TooGenericExceptionCaught") e: Exception) { return@filter true }
                if (stored != null) return@filter true
                val secret = plain[name] ?: return@filter true
                val value = secret.value.reveal()
                register(value)
                copy(name, value, stores.data[FssFormat.entryName(name)]) != null
            }
            if (keep.size == record.names.size) continue
            if (keep.isEmpty()) edit.remove(key) else edit.putString(key, record.copy(names = keep).encode())
        }
        return edit.isEmpty || prefs.commit(edit.changes)
    }

    /**
     * Drops flag names whose secret the native app has since changed (the credential ledger holds a record: typed
     * again, signed in through Git, signed out, reset). The re-enter sheet then stops asking for them (Codex review
     * p12/migration #2). An unreadable ledger resolves nothing. Returns false when the commit was refused.
     */
    fun resolveChanged(): Boolean {
        val known = ledger.knownOrNull() ?: return true
        val logical = LogicalPrefs(prefs.live())
        val edit = PrefsEdit()
        for (key in logical.keys(LogicalPrefs.SECRET_REENTRY)) {
            val record = ReentryRecord.parse(logical.string(key)) ?: continue
            val id = key.removePrefix(LogicalPrefs.SECRET_REENTRY)
            val keep = record.names.filter { flag -> SecretKind.nameFor(flag, id)?.let { it in known } != true }
            if (keep.size == record.names.size) continue
            if (keep.isEmpty()) edit.remove(key) else edit.putString(key, record.copy(names = keep).encode())
        }
        return edit.isEmpty || prefs.commit(edit.changes)
    }

    /** Stores one plaintext unless native already has a value; returns null on success. */
    private suspend fun copy(name: String, value: String, cipher: RawPref?): M2Failure? {
        val digest = (cipher as? RawPref.Str)?.value?.let(CredentialLedger::ciphertextDigest)
        val existing = try {
            sink.read(name)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            return M2Failure.SecretStoreUnavailable // never the exception text: a failing Keystore may echo its input
        }
        when {
            existing == value -> {
                tally("alreadyMigrated")
                if (digest != null) ledger.recordMigrated(name, digest)
                return null
            }
            existing != null -> {
                tally("nativeNewer") // kept, never overwritten
                return null
            }
        }
        val ok = try {
            sink.write(name, value)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            return M2Failure.SecretStoreUnavailable
        }
        if (!ok) return M2Failure.ReadBackMismatch
        if (digest != null) ledger.recordMigrated(name, digest)
        tally("migrated")
        return null
    }

    private val tallies = sortedMapOf<String, Long>()
    private fun tally(key: String) { tallies.merge(key, 1L, Long::plus) }

    /** Counts of copy outcomes (`migrated`, `alreadyMigrated`, `nativeNewer`) of the last [run]. */
    fun copyTallies(): Map<String, Long> = tallies.toSortedMap()

    /** Registers the value and, for a JSON object (GitHub credential, BYO envelope), every string field ≥ 8 chars. */
    private fun register(value: String) {
        redactor.registerSecret(value)
        val obj = MiniJson.parseOrNull(value) as? Map<*, *> ?: return
        fun walk(v: Any?) {
            when (v) {
                is String -> if (v.length >= 8) redactor.registerSecret(v)
                is Map<*, *> -> v.values.forEach(::walk)
                is List<*> -> v.forEach(::walk)
                else -> Unit
            }
        }
        walk(obj)
    }

    /**
     * `oc.secretReentry.<profileId>` = `{"v":1,"names":["pw"],"reason":"UnwrapFailed","at":<ms>}` (names are the
     * prefix only, never values), merged with a record an earlier run left. One commit.
     */
    private fun writeFlags(logical: LogicalPrefs, flags: Map<String, List<Pair<String, String>>>): Boolean {
        val edit = PrefsEdit()
        for ((id, list) in flags) {
            val key = LogicalPrefs.SECRET_REENTRY + id
            val old = ReentryRecord.parse(logical.string(key))
            val names = (old?.names.orEmpty() + list.map { it.first }).distinct()
            val record = ReentryRecord(names, old?.reason ?: list.first().second, old?.at ?: now())
            edit.putString(key, record.encode())
        }
        return prefs.commit(edit.changes)
    }

    companion object {
        /** The built-in phone server's profile ids, read the way the Dart owner pointer is kept. */
        fun builtinIds(prefs: LogicalPrefs): Set<String> {
            val out = LinkedHashSet<String>()
            prefs.string(LogicalPrefs.BUILTIN_OWNER)?.takeIf { M1Census.ID_SHAPE.matches(it) }?.let(out::add)
            for (k in prefs.keys("oc.builtinRecovery.")) out += k.removePrefix("oc.builtinRecovery.")
            return out
        }

        /** The one-time sheet rows, from every `oc.secretReentry.*` record still present (plan 05 §7.4 M2). */
        fun notices(prefs: LogicalPrefs): List<ReentryNotice> = prefs.keys(LogicalPrefs.SECRET_REENTRY).sorted().flatMap { key ->
            val id = key.removePrefix(LogicalPrefs.SECRET_REENTRY)
            ReentryRecord.parse(prefs.string(key))?.names.orEmpty().mapNotNull { f -> SecretKind.kindOfFlag(f)?.let { ReentryNotice(id, it) } }
        }.distinct()
    }
}

internal data class ReentryRecord(val names: List<String>, val reason: String, val at: Long) {
    fun encode(): String = MiniJson.encode(linkedMapOf("v" to 1L, "names" to names, "reason" to reason, "at" to at))

    companion object {
        fun parse(text: String?): ReentryRecord? {
            val m = text?.let { MiniJson.parseOrNull(it) } as? Map<*, *> ?: return null
            if (m["v"] != 1L) return null
            val names = (m["names"] as? List<*>)?.filterIsInstance<String>() ?: return null
            return ReentryRecord(names, m["reason"] as? String ?: "", m["at"] as? Long ?: 0L)
        }
    }
}
