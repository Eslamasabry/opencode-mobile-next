package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy
import io.github.eslamasabry.oc.engine.runtime.heat.HeatHold
import io.github.eslamasabry.oc.engine.runtime.heat.HeatHoldCodec
import io.github.eslamasabry.oc.engine.runtime.heat.HeatPausable
import io.github.eslamasabry.oc.engine.runtime.heat.HeatPausePort
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalGuard
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalGuardStore
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalNotice
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.engine.runtime.recovery.AutomationGate
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** One production owner for protection, durable holds and recovery across process death. */
@SingleIn(AppScope::class)
@Inject
class ThermalRuntime(
    private val stores: PreviewStores,
    private val facts: DeviceFacts,
    private val dispatchers: AppDispatchers,
    scope: AppCoroutineScope,
    private val clock: Clock,
    app: Application,
    profileScope: ProfileScope,
) {
    private val main = CoroutineScope(scope.coroutineContext + dispatchers.main)
    private val retired = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val store = NativeThermalStore(stores, profileScope)
    private val port = NativeThermalTeams(app, stores, dispatchers, store) { it.raw !in retired }
    private val guard =
        ThermalGuard(port, store, AutomationGate { id, _ -> allowsRecovery(id) }, clock)
    private val enabledState = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()
    val pauseActive: StateFlow<Boolean> = guard.pauseActive
    val notice: StateFlow<ThermalNotice?> = guard.notice
    private var started = false

    fun start() {
        if (started) return
        started = true
        main.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            enabledState.value = guard.enabled()
            val current = ThermalReading(ThermalStatus.valueOf(facts.thermal.value.name))
            guard.start(current)
            launch {
                facts.thermal.collect { level ->
                    guard.observe(ThermalReading(ThermalStatus.valueOf(level.name)))
                }
            }
            launch {
                guard.nextCheckAt.collectLatest { at ->
                    if (at != null) {
                        delay((at - clock.now()).inWholeMilliseconds.coerceAtLeast(0))
                        guard.evaluate()
                    }
                }
            }
            launch {
                stores.prefs.changes.collect { changed ->
                    if (
                        changed.any {
                            it == ThermalGuardStore.ENABLED_KEY || it.startsWith("oc.automation.")
                        }
                    ) {
                        enabledState.value = guard.enabled()
                        guard.evaluate()
                    }
                }
            }
            launch {
                stores.profiles.profiles.collect { rows ->
                    val currentIds = rows.map { it.profile.id }.toSet()
                    guard.holds.keys.filter { it !in currentIds }.forEach { guard.forget(it) }
                }
            }
        }
    }

    suspend fun setEnabled(value: Boolean): Outcome<Unit> =
        main
            .async {
                // Once requested, protection and its durable receipt outlive the settings screen.
                // Cancelling the caller cancels only its await, never a half-applied city pause.
                if (stores.warm() is Outcome.Failed)
                    return@async Outcome.Failed(Failure.StorageRefused())
                val result = guard.setEnabled(value)
                enabledState.value = guard.enabled()
                result
            }
            .await()

    fun dismiss() = guard.dismiss()

    private fun allowsRecovery(id: ProfileId): Boolean {
        if (id.raw in retired || stores.profiles.profile(id) == null) return false
        val value = stores.prefs.get("oc.automation.${id.raw}") ?: return true
        val raw = (value as? PrefValue.Str)?.value ?: return false
        return AutomationPolicy.fromJson(raw).allows(AutomationBehavior.ThermalRecovery)
    }

    fun stopAdmission(id: ProfileId) {
        retired += id.raw
    }

    suspend fun drain() = guard.drain()

    fun reopen(id: ProfileId) {
        retired -= id.raw
        // Retirement can have cleared the cooldown timer while deletion was draining.
        main.launch { guard.evaluate() }
    }
}

private class NativeThermalStore(private val stores: PreviewStores, scope: ProfileScope) :
    ThermalGuardStore {
    init {
        scope.register(
            KeyShape(
                ThermalGuardStore.HOLD_KEY_PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:runtime",
                "heat holds and the exact paused service recipe",
            )
        )
        scope.register(
            KeyShape(
                ThermalGuardStore.ENABLED_KEY,
                KeyScope.App,
                PrefType.Bool,
                ":engine:runtime",
                "heat protection preference",
            )
        )
    }

    override suspend fun readEnabled(): Boolean? = stores.prefs.bool(ThermalGuardStore.ENABLED_KEY)

    override suspend fun writeEnabled(value: Boolean): Outcome<Unit> =
        stores.prefs.commit(stores.lanes.app) { putBool(ThermalGuardStore.ENABLED_KEY, value) }

    override suspend fun readHolds(): Map<String, String> =
        stores.prefs
            .keys(ThermalGuardStore.HOLD_KEY_PREFIX)
            .mapNotNull { key ->
                val id = key.removePrefix(ThermalGuardStore.HOLD_KEY_PREFIX)
                if (stores.profiles.profile(ProfileId(id)) == null) null
                else stores.prefs.string(key)?.let { id to it }
            }
            .toMap()

    override suspend fun writeHold(id: ProfileId, raw: String): Outcome<Unit> =
        stores.prefs.commit(stores.lanes.forProfile(id)) {
            putString(ThermalGuardStore.holdKey(id), raw)
        }

    override suspend fun removeHold(id: String): Outcome<Unit> =
        stores.prefs.commit(stores.lanes.forProfile(ProfileId(id))) {
            remove(ThermalGuardStore.HOLD_KEY_PREFIX + id)
        }
}

/** The same loopback-only Gas City v0 contract as Flutter GasCityThermalTeams. */
private class NativeThermalTeams(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val store: ThermalGuardStore,
    private val admitted: (ProfileId) -> Boolean,
) : HeatPausePort {
    private data class Answer(val status: Int, val body: JSONObject?) {
        val ok
            get() = status in 200..299
    }

    private val linux
        get() = BuiltinLinux.get(app)

    private val client =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    private fun teams(): List<HeatPausable> =
        stores.profiles.profiles.value
            .mapNotNull { loaded ->
                if (!admitted(loaded.profile.id)) return@mapNotNull null
                val raw = loaded.profile.orchestration?.encoded ?: return@mapNotNull null
                val config =
                    try {
                        JSONObject(raw)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                if (config.optString("provider") != "gascity" || config.optBoolean("front"))
                    return@mapNotNull null
                val url = config.optString("url").trimEnd('/')
                val uri = loopback(url) ?: return@mapNotNull null
                HeatPausable(
                    loaded.profile.id,
                    url,
                    config.optString("city"),
                    uri.host == "127.0.0.1" && uri.port == 8472,
                )
            }
            .distinctBy { it.url to it.city }

    private fun current(target: HeatPausable): Boolean =
        teams().any {
            it.id == target.id &&
                it.url == target.url &&
                (it.city.isEmpty() || it.city == target.city)
        }

    private fun resource(target: HeatPausable, city: String, tail: String = ""): String =
        "${target.url}/v0/city/${segment(city)}$tail"

    private fun segment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun loopback(value: String): URI? =
        try {
            URI(value).takeIf {
                it.scheme == "http" &&
                    it.host in setOf("127.0.0.1", "localhost") &&
                    it.userInfo == null &&
                    it.rawQuery == null &&
                    it.fragment == null &&
                    it.path.orEmpty() in setOf("", "/")
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun ask(
        target: HeatPausable,
        method: String,
        address: String,
        body: JSONObject? = null,
    ): Answer? {
        if (!current(target)) return null
        return withContext(dispatchers.io) {
            attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                    val uri = URI(address)
                    check(
                        loopback(target.url) != null &&
                            uri.host == URI(target.url).host &&
                            uri.port == URI(target.url).port
                    )
                    val payload =
                        body?.toString()?.toRequestBody("application/json".toMediaType())
                            ?: if (method == "POST" || method == "PATCH")
                                ByteArray(0).toRequestBody(null)
                            else null
                    val request =
                        Request.Builder()
                            .url(address)
                            .header("Accept", "application/json")
                            .apply { if (method != "GET") header("X-GC-Request", "thermal") }
                            .method(method, payload)
                            .build()
                    val call = client.newCall(request)
                    try {
                        val response =
                            suspendCancellableCoroutine<Response> { continuation ->
                                continuation.invokeOnCancellation { call.cancel() }
                                call.enqueue(
                                    object : Callback {
                                        override fun onFailure(call: Call, e: IOException) {
                                            if (continuation.isActive)
                                                continuation.resumeWith(Result.failure(e))
                                        }

                                        override fun onResponse(call: Call, response: Response) {
                                            continuation.resume(response) { _, value, _ ->
                                                value.close()
                                            }
                                        }
                                    }
                                )
                            }
                        response.use {
                            val text =
                                it.body.byteStream().use { stream ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = stream.read(buffer)
                                        if (count < 0) break
                                        check(output.size() + count <= 262144)
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                            coroutineContext.ensureActive()
                            val value =
                                if (text.isEmpty()) null
                                else
                                    try {
                                        JSONObject(String(text, Charsets.UTF_8))
                                    } catch (_: Exception) {
                                        null
                                    }
                            Answer(it.code, value)
                        }
                    } finally {
                        call.cancel()
                    }
                }
                .getOrNull()
        }
    }

    private suspend fun city(target: HeatPausable): String? {
        if (target.city.isNotEmpty()) return target.city
        val answer = ask(target, "GET", "${target.url}/v0/cities") ?: return null
        if (!answer.ok) return null
        val items = answer.body?.optJSONArray("items") ?: return null
        return (0 until items.length()).firstNotNullOfOrNull {
            items.optJSONObject(it)?.optString("name")?.takeIf(String::isNotEmpty)
        }
    }

    override suspend fun runningHere(): List<HeatPausable> {
        val found = mutableListOf<HeatPausable>()
        for (target in teams()) {
            val city = city(target) ?: continue
            val state = ask(target, "GET", resource(target, city)) ?: continue
            if (state.ok && state.body?.optBoolean("suspended") == false) found += target
        }
        return found
    }

    override suspend fun pause(target: HeatPausable, now: Instant): HeatHold? {
        val city = city(target) ?: return null
        val state = ask(target, "GET", resource(target, city)) ?: return null
        if (!state.ok || state.body?.optBoolean("suspended") != false) return null
        val captured =
            if (target.builtin) withContext(dispatchers.io) { linux.thermalTeamRecipe() } else null
        val paused =
            ask(target, "PATCH", resource(target, city), JSONObject().put("suspended", true))
        if (paused?.ok != true) return null
        val sessions = mutableListOf<String>()
        val items =
            ask(target, "GET", resource(target, city, "/sessions"))
                ?.takeIf { it.ok }
                ?.body
                ?.optJSONArray("items")
        if (items != null)
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = (item.opt("id") as? String)?.takeIf(String::isNotEmpty) ?: continue
                if (!item.optBoolean("running") && item.optString("state") != "active") continue
                if (
                    ask(target, "POST", resource(target, city, "/session/${segment(id)}/suspend"))
                        ?.ok == true
                )
                    sessions += id
            }
        return HeatHold(target.copy(city = city), now, sessions, restart = captured)
    }

    override suspend fun stop(hold: HeatHold): HeatHold {
        if (
            !hold.target.builtin ||
                hold.serviceStopped ||
                hold.restart == null ||
                !current(hold.target)
        )
            return hold
        // Commit the recovery recipe before killing its exact tracked service tree.
        val stopped = hold.copy(serviceStopped = true)
        if (store.writeHold(hold.target.id, HeatHoldCodec.encode(stopped)) is Outcome.Failed)
            return hold
        val confirmed =
            withContext(dispatchers.io) {
                attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                        linux.stopService("aiteam")
                        !linux.serviceRunning("aiteam")
                    }
                    .getOrNull() == true
            }
        return if (confirmed) stopped else hold
    }

    override suspend fun resume(hold: HeatHold, stillAllowed: () -> Boolean): Boolean {
        val target = hold.target
        if (!current(target)) return true
        if (!stillAllowed()) return false
        if (hold.serviceStopped) {
            val recipe = hold.restart
            val restarted =
                withContext(dispatchers.io) {
                    if (!stillAllowed() || !current(target)) return@withContext false
                    attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                            linux.resumeThermalTeam(recipe, null)
                        }
                        .getOrNull() == true
                }
            if (!restarted) return false
        }
        val city = city(target) ?: return false
        val state = ask(target, "GET", resource(target, city)) ?: return false
        if (!state.ok || state.body == null || !stillAllowed()) return false
        if (state.body?.optBoolean("suspended") == true) {
            if (
                ask(target, "PATCH", resource(target, city), JSONObject().put("suspended", false))
                    ?.ok != true
            )
                return false
        }
        var allConfirmed = true
        for (id in hold.sessions) {
            if (!stillAllowed()) return false
            val address = resource(target, city, "/session/${segment(id)}")
            val session = ask(target, "GET", address)
            if (session?.status == 404) continue
            if (session?.ok != true) {
                allConfirmed = false
                continue
            }
            if (session.body?.optString("id") == id && session.body?.optBoolean("running") == true)
                continue
            if (!stillAllowed()) return false
            val wake = ask(target, "POST", "$address/wake")
            if (wake?.ok == true) continue
            if (wake?.status == 404 && ask(target, "GET", address)?.status == 404) continue
            allConfirmed = false
        }
        return allConfirmed
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class ThermalProfileCloser(private val thermal: ThermalRuntime) : ProfileCloser {
    override val owner = ":engine:runtime:thermal"

    override fun stopAdmission(profile: ProfileId) = thermal.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        thermal.drain()
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) = thermal.reopen(profile)
}

@dev.zacsweers.metro.ContributesBinding(
    AppScope::class,
    replaces = [io.github.eslamasabry.oc.feature.phone.presentation.NoThermalSettings::class],
)
@Inject
class NativeThermalSettings(private val thermal: ThermalRuntime) :
    io.github.eslamasabry.oc.feature.phone.presentation.ThermalSettings {
    init {
        thermal.start()
    }

    override val supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
    override val enabled: StateFlow<Boolean> = thermal.enabled

    override suspend fun setEnabled(value: Boolean): Outcome<Unit> = thermal.setEnabled(value)
}
