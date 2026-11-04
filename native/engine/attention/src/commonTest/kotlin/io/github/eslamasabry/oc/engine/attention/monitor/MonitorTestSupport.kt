package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun at(iso: String): Instant = Instant.parse(iso)

/** UTC wall clock, so quiet hours in tests read the instant's own hour. */
internal val utcMinuteOfDay: (Instant) -> Int = { ((it.toEpochMilliseconds() / 60_000) % 1440).toInt() }

internal class FakePrefs : MonitorPrefs {
    val values = LinkedHashMap<String, Any>()

    /** Return true to refuse this write. */
    var refuse: (String, Any) -> Boolean = { _, _ -> false }

    /** Runs before a write lands; a test may suspend here. */
    var beforeWrite: suspend (String, Any) -> Unit = { _, _ -> }

    override fun getString(key: String): String? = values[key] as? String

    @Suppress("UNCHECKED_CAST")
    override fun getStringList(key: String): List<String>? = values[key] as? List<String>

    override fun contains(key: String): Boolean = key in values

    private suspend fun write(key: String, value: Any): Boolean {
        beforeWrite(key, value)
        if (refuse(key, value)) return false
        values[key] = value
        return true
    }

    override suspend fun putString(key: String, value: String): Boolean = write(key, value)

    override suspend fun putStringList(key: String, value: List<String>): Boolean = write(key, value.toList())

    override suspend fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }
}

internal class FakeConsent(private val prefs: FakePrefs) : MonitorConsent {
    private val listeners = mutableMapOf<String, MutableList<() -> Unit>>()

    fun key(id: String) = "oc.automation.$id"

    override fun allows(profileId: String): Boolean = prefs.values[key(profileId)] == "monitor:true"

    override fun hasRecord(profileId: String): Boolean = key(profileId) in prefs.values

    override suspend fun set(profileId: String, allowed: Boolean): Boolean {
        prefs.values[key(profileId)] = "monitor:$allowed"
        listeners[profileId]?.toList()?.forEach { it() }
        return true
    }

    /** Another automation behaviour changed: listeners run, the monitor decision does not change. */
    fun touchOther(profileId: String) {
        listeners[profileId]?.toList()?.forEach { it() }
    }

    override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
        listeners.getOrPut(profileId) { mutableListOf() } += listener
        return { listeners[profileId]?.remove(listener) }
    }
}

internal class FakeProfiles(count: Int = 2) : MonitorProfiles {
    val list = MutableList(count) { i -> MonitorProfile(id = "profile-${i + 1}", baseUrl = "https://server${i + 1}.example") }
    val locations = mutableMapOf<String, MonitorLocation>()

    override fun profiles(): List<MonitorProfile> = list.toList()

    override fun location(profileId: String): MonitorLocation? = locations[profileId]

    fun edit(id: String, change: (MonitorProfile) -> MonitorProfile) {
        val i = list.indexOfFirst { it.id == id }
        list[i] = change(list[i])
    }
}

internal fun pending(id: Int, session: String = "same-session") = MonitorPending("request-$id", session)

internal open class TestGateway(
    val requests: List<MonitorPending> = emptyList(),
    val failure: Boolean = false,
    val pendingRead: CompletableDeferred<List<MonitorPending>>? = null,
    val statuses: Map<String, String>? = null,
    val statusFailure: Boolean = false,
) : MonitorGateway {
    var isClosed = false
    override var directory: String? = null
    override var workspace: String? = null
    override val formsSupported: Boolean = false
    val messageCalls = mutableListOf<String>()
    val messageLimits = mutableListOf<Int>()
    var page: MonitorMessagePage = MonitorMessagePage(listOf(MonitorMessage("user-1", "user"), MonitorMessage("assistant-2", "assistant", finish = "length")))
    var pendingPage: CompletableDeferred<MonitorMessagePage>? = null

    override fun setLocation(directory: String?, workspace: String?) {
        this.directory = directory
        this.workspace = workspace
    }

    override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> {
        val ids = statuses?.keys ?: setOf("same-session")
        return MonitorSessionPage(ids.map { MonitorSession(it, if (statuses == null) "Private title" else "Title of $it", directory, workspace) }).ok()
    }

    override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> {
        if (failure) return Failure.Unexpected().failed()
        pendingRead?.let { return it.await().ok() }
        return requests.ok()
    }

    override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> = Failure.Unexpected().failed()

    override suspend fun listQuestions(): Outcome<List<MonitorPending>> = emptyList<MonitorPending>().ok()

    override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> = Failure.Unexpected().failed()

    override suspend fun pendingForms(): Outcome<List<MonitorPending>> = emptyList<MonitorPending>().ok()

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> {
        if (statusFailure) return Failure.Unexpected().failed()
        return (statuses?.toMap() ?: mapOf("same-session" to "busy")).ok()
    }

    override suspend fun session(id: String): Outcome<MonitorSession> = MonitorSession(id, null, directory, workspace).ok()

    override suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage> {
        messageCalls += sessionId
        messageLimits += limit
        pendingPage?.let { return it.await().ok() }
        return page.ok()
    }

    override fun close() {
        isClosed = true
    }
}

internal class RecordingAlerts : MonitorAlerts {
    val posted = mutableListOf<Triple<String, String, String>>()
    val requests = mutableListOf<MonitoredRequest>()
    val dismissed = mutableListOf<String>()
    var result: suspend (MonitoredRequest) -> Boolean = { true }
    var dismissResult: (String) -> Boolean = { true }

    override suspend fun alert(profileId: String, request: MonitoredRequest, key: String, token: String): Boolean {
        val shown = result(request)
        posted += Triple(profileId, key, token)
        requests += request
        return shown
    }

    override suspend fun dismiss(key: String): Boolean {
        dismissed += key
        return dismissResult(key)
    }
}

internal class Harness(count: Int = 2) {
    val prefs = FakePrefs()
    val consent = FakeConsent(prefs)
    val profiles = FakeProfiles(count)
    val clock = TestClock(at("2026-09-07T10:00:00Z"))
    val alerts = RecordingAlerts()
    var factories = 0
    var gateway: (MonitorProfile) -> MonitorGateway = { TestGateway() }
    var lastGateway: MonitorGateway? = null
    var wifi: suspend () -> Boolean? = { true }
    var alertsAllowed: ((String) -> Boolean)? = null
    var readAttention: MonitorAttentionReading? = null

    fun create(
        scope: TestScope,
        timeout: Duration = 8.seconds,
        backgroundInterval: Duration = 5.minutes,
    ): ProfileMonitor =
        ProfileMonitor(
            prefs = prefs,
            profiles = profiles,
            consent = consent,
            createGateway = {
                factories++
                gateway(it).also { g -> lastGateway = g }
            },
            isReadable = { true },
            networkWifi = { wifi() },
            alerts = alerts,
            clock = clock,
            scope = scope.backgroundScope,
            minuteOfDay = utcMinuteOfDay,
            alertsAllowed = alertsAllowed,
            readAttention = readAttention,
            timeout = timeout,
            backgroundInterval = backgroundInterval,
        )

    /** A stored legacy rule (prior consent before the automation policy existed). */
    suspend fun legacyConsent(id: String = "profile-1", rules: MonitorNotifyRules = MonitorNotifyRules(enabled = true)) {
        prefs.putString(ProfileMonitor.rulesKey(id), rules.toJson())
    }
}
