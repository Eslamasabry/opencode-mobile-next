package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.ProviderUsageReading
import io.github.eslamasabry.oc.engine.integrations.usage.parseProviderUsageList
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads only an already running, owned phone helper. There is no start, restart or credential
 * creation path.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneUsageReader(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    private val retired = java.util.concurrent.ConcurrentHashMap.newKeySet<ProfileId>()

    val supported: Boolean
        get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    fun signInOwner(): ProfileId? = if (supported) owner() else null

    fun retire(profile: ProfileId): Boolean {
        val owned = owner() == profile
        retired += profile
        return owned
    }

    private fun owner(): ProfileId? {
        val raw =
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ?: return null
        return ProfileId(raw).takeIf { it !in retired && stores.profiles.profile(it) != null }
    }

    private suspend fun running(id: ProfileId): Boolean =
        withContext(dispatchers.io) {
            val linux = BuiltinLinux.get(app)
            linux.installed && linux.agentHost.status(id.raw)["running"] == true
        }

    suspend fun read(): Outcome<List<ProviderUsageReading>?> {
        if (!supported) return null.ok()
        when (val ready = stores.warm()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        val captured = owner() ?: return null.ok()
        val isRunning =
            when (val status = attempt({ Failure.Device() }) { running(captured) }) {
                is Outcome.Failed -> return status
                is Outcome.Ok -> status.value
            }
        if (!isRunning) return null.ok()
        val password =
            when (val stored = stores.secrets.read("oc.agentHostSecret.${captured.raw}")) {
                is Outcome.Failed -> return stored
                is Outcome.Ok -> stored.value
            }
        if (password == null || !Regex("^[a-f0-9]{64}$").matches(password))
            return Failure.SecretUnavailable().failed()
        redactor.registerSecret(password)
        if (owner() != captured) return Failure.Refused(RefusalReason.StaleScope).failed()
        return coroutineScope {
            val transport =
                PaseoTransport(
                    endpoint = "http://127.0.0.1:4099",
                    password = password,
                    socketFactory = OkHttpPaseoSockets(),
                    scope = this,
                    lane = dispatchers.serial("phone-provider-usage"),
                    decodeDispatcher = dispatchers.default,
                    requestTimeoutMs = 10_000,
                )
            try {
                withTimeoutOrNull(15_000) {
                    val result =
                        attempt({ Failure.Network() }) {
                            if (owner() != captured || !running(captured)) return@attempt null
                            transport.connect()
                            val payload =
                                transport.request(
                                    "provider.usage.list.request",
                                    jsonOf(),
                                    timeoutMs = 10_000,
                                )
                            val readings =
                                parseProviderUsageList(
                                    DartJson.decodeOrNull(payload.toString()),
                                    clock.now(),
                                )
                            if (owner() != captured || !running(captured)) return@attempt null
                            val currentSecret =
                                stores.secrets
                                    .read("oc.agentHostSecret.${captured.raw}")
                                    .getOrNull()
                            if (currentSecret != password) return@attempt null
                            readings
                        }
                    result
                } ?: Failure.TimedOut().failed()
            } finally {
                withContext(NonCancellable) { transport.close() }
            }
        }
    }
}
