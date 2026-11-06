package io.github.eslamasabry.oc.feature.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E00-S14: whatever native cannot read yet (MISSING) or keeps for a later port (DEFERRED) is left untouched, and the
 * store ledger (`docs/native/migration/store-ledger.json`) says how the person hears about it: nothing (silent, the
 * fallback words say what happens instead), the one deferred-features notice, or the one-time re-enter sheet. This
 * keeps every such row on one of those three, and the notice rows on a feature the gate can name.
 */
class GateFallbackLedgerTest {
    private data class Row(val id: String, val kind: String, val name: String, val status: String, val fields: Map<String, String>)

    /** The ledger keeps one row per line; only flat string fields are needed here. */
    private val rows: List<Row> by lazy {
        val file = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, LEDGER) }
            .firstOrNull { it.isFile } ?: fail("$LEDGER not found above ${File(".").absolutePath}")
        file.readLines()
            .map { it.trim() }
            .filter { it.startsWith("{\"id\"") }
            .map { line ->
                val fields = FIELD.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                Row(fields.getValue("id"), fields.getValue("kind"), fields.getValue("name"), fields.getValue("status"), fields)
            }
    }

    @Test
    fun `B-MIGR-005 every unread or deferred store maps to silent keep, the one notice or the re-enter sheet`() {
        val open = rows.filter { it.status == "MISSING" || it.status == "DEFERRED" }
        assertTrue(open.size > 20, "ledger rows not found (${rows.size} rows read)")
        val problems = open.mapNotNull { r ->
            when (r.fields["surface"]) {
                "silent" -> if (r.fields["fallback"].isNullOrBlank()) "${r.id}: silent with no fallback words" else null
                "notice" -> {
                    val feature = r.fields["deferredFeature"]
                    if (GateDeferredFeature.entries.none { it.name == feature }) "${r.id}: notice names no gate feature" else null
                }
                "reenter" -> if (r.kind != "secret") "${r.id}: only a secret is typed again" else null
                else -> "${r.id}: no surface (silent, notice or reenter)"
            }
        }
        assertEquals(emptyList(), problems)
    }

    /**
     * The census (M1, `deferredFeatures`) raises the notice from these names, so the ledger's notice rows and the
     * census agree: AI Team for `oc.team…`, `oc.orchestration.` and the team engine files, Memory for `oc.factsbook…`
     * and the external factsbook folder. Gemma's models have no Flutter 2.1.4 store row; the census finds them by
     * folder.
     */
    @Test
    fun `B-MIGR-009 the notice rows are the stores the census names AI Team and Memory from`() {
        val expected = rows
            .filter { it.status == "DEFERRED" || it.status == "MISSING" }
            .mapNotNull { r -> censusFeature(r.name)?.let { r.id to it.name } }
            .toMap()
        val notice = rows.filter { it.fields["surface"] == "notice" }.associate { it.id to it.fields["deferredFeature"] }
        assertEquals(expected, notice)
        assertTrue(GateDeferredFeature.AiTeam.name in notice.values && GateDeferredFeature.Memory.name in notice.values)
    }

    private fun censusFeature(name: String): GateDeferredFeature? =
        when {
            name.startsWith("oc.team") || name.startsWith("oc.orchestration.") -> GateDeferredFeature.AiTeam
            name.startsWith("oc.factsbook") || name == "external-factsbook" -> GateDeferredFeature.Memory
            else -> null
        }

    private companion object {
        const val LEDGER = "docs/native/migration/store-ledger.json"
        val FIELD = Regex(""""([A-Za-z]+)": "((?:[^"\\]|\\.)*)"""")
    }
}
