package io.github.eslamasabry.oc.feature.factsbook

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.factsbook.FactsBookEngine
import io.github.eslamasabry.oc.engine.factsbook.FactsBookHttp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookOutboxStore
import io.github.eslamasabry.oc.engine.factsbook.FactsBookSigning
import io.github.eslamasabry.oc.engine.factsbook.FactsBookStore
import io.github.eslamasabry.oc.engine.factsbook.FbHttpResult
import io.github.eslamasabry.oc.engine.factsbook.HttpFactsBookGateway
import io.github.eslamasabry.oc.feature.factsbook.presentation.FactsBookAccess
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal class MemoryKv(initial: Map<String, PrefValue> = emptyMap()) : KeyValueStore {
    val values = LinkedHashMap(initial)
    override val changes: SharedFlow<Set<String>> = MutableSharedFlow()

    override fun get(key: String): PrefValue? = values[key]

    override fun keys(prefix: String): Set<String> = values.keys.filter { it.startsWith(prefix) }.toSet()

    override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> {
        object : PrefEdit {
            override fun putString(key: String, value: String) {
                values[key] = PrefValue.Str(value)
            }

            override fun putBool(key: String, value: Boolean) {
                values[key] = PrefValue.Bool(value)
            }

            override fun putLong(key: String, value: Long) {
                values[key] = PrefValue.Int64(value)
            }

            override fun putStringList(key: String, value: List<String>) {
                values[key] = PrefValue.StrList(value)
            }

            override fun putDouble(key: String, value: Double) {
                values[key] = PrefValue.Dbl(value)
            }

            override fun remove(key: String) {
                values.remove(key)
            }
        }.edit()
        return Outcome.Ok(Unit)
    }

    override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> =
        Outcome.Ok(false)
}

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

/** Answers by "METHOD path"; records what was asked. */
internal class FakeNode : FactsBookHttp {
    val answers = mutableMapOf<String, FbHttpResult>()
    val asked = mutableListOf<String>()
    var unreachable = false

    fun on(route: String, body: String, status: Int = 200) {
        answers[route] = FbHttpResult.Answer(status, body)
    }

    override suspend fun send(method: String, path: String, query: Map<String, String>, headers: Map<String, String>, body: String?): FbHttpResult {
        asked += "$method $path" + if (query.containsKey("kind")) "?kind=${query["kind"]}" else ""
        if (unreachable) return FbHttpResult.Unreachable
        return answers["$method $path"] ?: FbHttpResult.Answer(404, """{"error":{"code":"not_found"}}""")
    }

    /** Rules, one event, its firing, a proposed reminder and an open offer (F: test/fixtures/factsbook). */
    fun triggers() {
        on("GET /v1/rules", """{"items":[$RULE]}""")
        on(
            "GET /v1/events",
            """{"items":[{"id":"evt_1","source":{"kind":"share"},"trust":"third_party_untrusted","sens":"normal","captured_at":"2026-11-08T09:00:00Z",
              "processing":{"state":"evaluated","reason":"matched"},"firings":["trg_1"],"claims":{"sender":"Clinic"}}]}""",
        )
        on(
            "GET /v1/trigger-firings",
            """{"items":[{"id":"trg_1","event_id":"evt_1","rule_id":"rul_1","template":{"id":"appointment"},"at":"2026-11-08T09:00:01Z","state":"proposed","outcomes":["rpr_1","off_1"]}]}""",
        )
        on(
            "GET /v1/reminder-proposals",
            """{"items":[{"id":"rpr_1","rev":2,"event_id":"evt_1","firing_id":"trg_1","area":"health","digest":"dg","state":"proposed",
              "preview":{"what":"Clinic visit","zone":"vault","locale":"en","preview_digest":"pd"}}]}""",
        )
        on(
            "GET /v1/offers",
            """{"items":[{"id":"off_1","rev":1,"event_id":"evt_1","firing_id":"trg_1","summary":"A clinic visit next week","digest":"od","state":"offered","expires_at":"2026-11-09T10:00:00Z"}]}""",
        )
    }

    fun healthy() {
        on("GET /v1/status", """{"modules":{},"index":{"normal":"ok","sensitive":"locked"}}""")
        on("GET /v1/review", """{"items":[{"rvw":"rvw_1","class":"fact_add","state":"proposed","digest":"d","proposal":{"text":"x"}}]}""")
        on("GET /v1/inbox/pending", """{"items":[]}""")
        on("GET /v1/things", THINGS)
    }
}

internal const val RULE: String =
    """{"id":"rul_1","rev":4,"updated_at":"2026-11-07T10:00:00Z","definition":{"v":1,"name":"Clinic","enabled":true,
      "source":{"client_id":"cli_01","kind":"share","instance":"manual-share"},"template":{"id":"appointment","version":2,"locale":"en"},
      "area":"health","contains_all":["clinic"],"outcomes":[{"kind":"reminder"}],"limit_per_day":5}}"""

internal const val THINGS: String =
    """{"items":[{"id":"per_01","title":"Sara","kind":"person","updated_at":"2026-10-10T08:00:00Z","facts":[
        {"id":"f_1","thing_id":"per_01","text":"Likes tea","provenance":"told","observed_at":"2026-10-10T08:00:00Z","source":"note"}]}]}"""

internal const val LINK: String =
    """{"url":"http://127.0.0.1:8787","client":"cli_01","vault":"v","fingerprint":"fp","node":"n","scopes":["read","write"]}"""

internal val OWNER_LINK: String = LINK.dropLast(1) + ""","owner_device":"dev_1"}"""

internal class FixedClock(private val at: Instant = Instant.parse("2026-11-08T10:00:00Z")) : Clock {
    override fun now(): Instant = at
}

internal class Nav : AppNavigator {
    val opened = mutableListOf<AppRoute>()
    var backs = 0
    override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

    override fun open(route: AppRoute) {
        opened += route
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

    var lands = 0

    override fun land() {
        lands++
    }

    override fun replaceRoot(root: AppRoute) {}

    override fun back() {
        backs++
    }
}

/** The access the app wiring gives, over a real engine and a fake node. */
internal class FakeAccess(
    paired: Boolean = true,
    on: Boolean = true,
    /** The pairing names an owner device: this phone signs approvals. */
    owner: Boolean = false,
    val node: FakeNode = FakeNode(),
    val secrets: MemorySecrets = MemorySecrets(if (paired) mapOf("oc.factsbook.p1" to "t") else emptyMap()),
) : FactsBookAccess {
    val kv = MemoryKv(if (paired) mapOf("oc.factsbookNode.p1" to PrefValue.Str(if (owner) OWNER_LINK else LINK)) else emptyMap())
    private val store = FactsBookStore(kv, secrets, WriterLane("app"))
    val pairNode = FakeNode()

    override val engine: FactsBookEngine =
        FactsBookEngine(
            store,
            gatewayFor = { _, token, signer -> HttpFactsBookGateway(node, token, Random(1), retryDelayMs = 1, owner = signer) },
            outboxFor = { id ->
                object : FactsBookOutboxStore {
                    override suspend fun read(): String? = store.readOutbox(id)

                    override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                }
            },
            clock = FixedClock(),
            random = Random(2),
            signing = FactsBookSigning({ n -> ByteArray(n) { (it + 7).toByte() } }),
        )
    override val activeProfileId: MutableStateFlow<String?> = MutableStateFlow("p1")
    override val enabled: MutableStateFlow<Boolean> = MutableStateFlow(on)

    override suspend fun setEnabled(on: Boolean) {
        enabled.value = on
    }

    override fun pairingGateway(baseUrl: String): HttpFactsBookGateway = HttpFactsBookGateway(pairNode, null)
}
