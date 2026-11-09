package io.github.eslamasabry.oc.migration.readers.ledger

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import java.io.File

/** How native handles one Flutter store (`docs/native/migration/store-ledger.json`, `statuses`). */
public enum class LedgerStatus { READ, MISSING, KEPT, DEFERRED, DROPPED, NOT_STORED, NEW }

/** One row of the store ledger. Only the fields the checks need are typed; the file carries more. */
public data class LedgerRow(
    val id: String,
    val kind: String,
    val name: String,
    val aliases: List<String>,
    val flutterWriter: String?,
    val nativeReader: String,
    val status: LedgerStatus,
    val decision: String,
    val fallback: String?,
    val audit: String?,
)

public data class StoreLedger(val flutterCommit: String, val rows: List<LedgerRow>) {

    private val byName: Map<String, List<LedgerRow>> by lazy {
        val m = LinkedHashMap<String, MutableList<LedgerRow>>()
        for (r in rows) for (n in listOf(r.name) + r.aliases) m.getOrPut(n) { mutableListOf() } += r
        m
    }

    /**
     * Rows covering [name] by name or alias. A name can have more than one row when the same spelling is two stores
     * (`oc.orchestration.` is a preference family and a reserved secure-storage name).
     */
    public fun rowsFor(name: String): List<LedgerRow> = byName[name].orEmpty()

    /** Names in [scanned] that no row (by name or alias) covers, in order. */
    public fun unmapped(scanned: Iterable<String>): List<String> = scanned.filter { rowsFor(it).isEmpty() }

    public companion object {
        public const val PATH: String = "docs/native/migration/store-ledger.json"
        public const val SNAPSHOT_PATH: String = "docs/native/migration/flutter-2.1.5-names.json"

        public fun load(repoRoot: File): StoreLedger = parse(File(repoRoot, PATH).readText())

        public fun parse(text: String): StoreLedger {
            val doc = MiniJson.parse(text) as Map<*, *>
            val commit = (doc["flutterSource"] as Map<*, *>)["commit"] as String
            val rows = (doc["rows"] as List<*>).map { raw ->
                val r = raw as Map<*, *>
                LedgerRow(
                    id = r["id"] as String,
                    kind = r["kind"] as String,
                    name = r["name"] as String,
                    aliases = (r["aliases"] as List<*>?).orEmpty().map { it as String },
                    flutterWriter = r["flutterWriter"] as String?,
                    nativeReader = r["nativeReader"] as String,
                    status = LedgerStatus.valueOf(r["status"] as String),
                    decision = r["decision"] as String,
                    fallback = r["fallback"] as String?,
                    audit = r["audit"] as String?,
                )
            }
            return StoreLedger(commit, rows)
        }
    }
}

/** The committed scanner output for the released tree (`docs/native/migration/flutter-2.1.5-names.json`). */
public data class NamesSnapshot(val release: String, val commit: String, val names: List<ScannedName>) {
    public companion object {
        public fun load(repoRoot: File): NamesSnapshot = parse(File(repoRoot, StoreLedger.SNAPSHOT_PATH).readText())

        public fun parse(text: String): NamesSnapshot {
            val doc = MiniJson.parse(text) as Map<*, *>
            val src = doc["flutterSource"] as Map<*, *>
            val names = (doc["names"] as List<*>).map {
                val m = it as Map<*, *>
                ScannedName(m["name"] as String, ScanSource.fromWire(m["source"] as String), m["writer"] as String)
            }
            return NamesSnapshot(src["release"] as String, src["commit"] as String, names)
        }

        public fun write(release: String, ref: String, commit: String, names: List<ScannedName>): String = buildString {
            append("{\n \"schema\": 1,\n \"flutterSource\": {\"release\": ").append(q(release)).append(", \"ref\": ").append(q(ref))
            append(", \"commit\": ").append(q(commit)).append("},\n \"names\": [\n")
            names.forEachIndexed { i, n ->
                append("  {\"name\": ").append(q(n.name)).append(", \"source\": ").append(q(n.source.wire)).append(", \"writer\": ").append(q(n.writer)).append('}')
                append(if (i < names.size - 1) ",\n" else "\n")
            }
            append(" ]\n}\n")
        }

        private fun q(s: String): String = buildString {
            append('"')
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
    }
}

/**
 * `./gradlew scanFlutterTree -PflutterTree=<checkout of the released Flutter line> -PflutterCommit=<sha>` rewrites the
 * committed snapshot. Args: tree, output file, release, ref, commit.
 */
public object FlutterStoreScannerMain {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.size == 5) { "usage: <flutter tree> <out.json> <release> <ref> <commit>" }
        val names = FlutterStoreScanner.scan(File(args[0]))
        File(args[1]).writeText(NamesSnapshot.write(args[2], args[3], args[4], names))
        println("${names.size} names -> ${args[1]}")
    }
}
