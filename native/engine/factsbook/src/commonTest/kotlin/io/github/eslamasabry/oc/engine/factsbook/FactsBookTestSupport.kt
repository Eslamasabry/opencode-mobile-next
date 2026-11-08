package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

internal class MemorySecrets(initial: Map<String, String> = emptyMap()) : SecretStore {
    val values = LinkedHashMap(initial)

    override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(values[name])

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        values[name] = value
        return Outcome.Ok(Unit)
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        values.remove(name)
        return Outcome.Ok(Unit)
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(values.keys.filter { it.startsWith(prefix) }.toSet())
}

/** The Flutter preferences file as it is on disk: physical keys carry the plugin's `flutter.` prefix. */
internal fun TestScope.flutterPrefs(physical: Map<String, RawPref>): Pair<PrefsKeyValueStore, InMemoryPrefsBackend> {
    val disk = InMemoryPrefsBackend(physical)
    return PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler)) to disk
}

/** A Flutter 2.1.5 pairing record, byte for byte as `FactsBookLink.encode` writes it. */
internal const val FLUTTER_LINK: String =
    """{"url":"http://127.0.0.1:8787","client":"cli_01","vault":"v_main","fingerprint":"fp","node":"node_a","scopes":["read","write"]}"""

/** Answers by "METHOD path"; records every request. */
internal class FakeHttp(private val answers: MutableMap<String, FbHttpResult> = mutableMapOf()) : FactsBookHttp {
    data class Sent(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String?)

    val sent = mutableListOf<Sent>()
    var unreachable = false

    fun on(route: String, status: Int, body: String) {
        answers[route] = FbHttpResult.Answer(status, body)
    }

    override suspend fun send(
        method: String,
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        body: String?,
    ): FbHttpResult {
        sent += Sent(method, path, query, headers, body)
        if (unreachable) return FbHttpResult.Unreachable
        return answers["$method $path"] ?: FbHttpResult.Answer(404, """{"error":{"code":"not_found"}}""")
    }
}

internal const val STATUS_JSON: String =
    """{"modules":{"capture":{"state":"ok"}},"key":"locked","index":{"normal":"ok","sensitive":"locked"},"generated_at":"2026-10-11T09:00:00Z"}"""

internal const val THINGS_JSON: String =
    """{"items":[{"id":"per_01","title":"Sara","kind":"person","updated_at":"2026-10-10T08:00:00Z","facts":[
        {"id":"f_1","thing_id":"per_01","text":"Likes tea","provenance":"told","observed_at":"2026-10-10T08:00:00Z","source":"note"}]},
      {"id":"doc_01","title":"Passport","kind":"document","updated_at":"2026-10-09T08:00:00Z","facts":[]}],"next_cursor":null}"""

internal val laneApp = WriterLane("app")
