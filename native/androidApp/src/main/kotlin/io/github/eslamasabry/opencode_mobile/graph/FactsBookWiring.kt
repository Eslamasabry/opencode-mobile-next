package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.engine.factsbook.FactsBookDeviceCheck
import io.github.eslamasabry.oc.engine.factsbook.FactsBookEngine
import io.github.eslamasabry.oc.engine.factsbook.FactsBookSigning
import io.github.eslamasabry.oc.engine.factsbook.FactsBookHttp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookOutboxStore
import io.github.eslamasabry.oc.engine.factsbook.FactsBookStore
import io.github.eslamasabry.oc.engine.factsbook.HttpFactsBookGateway
import io.github.eslamasabry.oc.engine.factsbook.NetFactsBookHttp
import io.github.eslamasabry.oc.feature.factsbook.presentation.FactsBookAccess
import java.security.SecureRandom
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * s23-factsbook: FactsBook over the adopted Flutter stores. The pairing is read in place (`oc.factsbookNode.<id>` in
 * the Flutter preferences file; the token, owner seed and outbox in the secret store, where the day-1 migration copied
 * them under the same names). Nothing shows until the stores are warm and the developer switch (`oc.dev.factsbook`)
 * is on, as in Flutter. One trust-gated client per pairing, for the node's private origin only.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object FactsBookWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun factsBookAccess(
        stores: PreviewStores,
        http: HttpClientFactory,
        appScope: AppCoroutineScope,
        clock: Clock,
        app: Application,
    ): FactsBookAccess = AppFactsBookAccess(stores, http, appScope, clock, AndroidFactsBookDeviceCheck(app, clock))
}

private class AppFactsBookAccess(
    private val stores: PreviewStores,
    private val http: HttpClientFactory,
    appScope: AppCoroutineScope,
    clock: Clock,
    deviceCheck: FactsBookDeviceCheck,
) : FactsBookAccess {
    private val secure = SecureRandom()

    private val store = FactsBookStore(stores.prefs, stores.secrets, stores.lanes.app) { stores.lanes.forProfile(ProfileId(it)) }

    /** One client per base URL, reused while the pairing stays the same. */
    private var client: Pair<String, FactsBookHttp>? = null

    private fun httpFor(baseUrl: String): FactsBookHttp =
        synchronized(this) {
            client?.takeIf { it.first == baseUrl }?.second
                ?: NetFactsBookHttp.create(http, baseUrl).also { client = baseUrl to it }
        }

    override val engine: FactsBookEngine =
        FactsBookEngine(
            store,
            gatewayFor = { link, token, signer -> HttpFactsBookGateway(httpFor(link.baseUrl), token, owner = signer) },
            outboxFor = { id ->
                object : FactsBookOutboxStore {
                    override suspend fun read(): String? = store.readOutbox(id)

                    override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                }
            },
            clock = clock,
            signing = FactsBookSigning({ n -> ByteArray(n).also(secure::nextBytes) }, deviceCheck),
        )

    private val profile = MutableStateFlow<String?>(null)
    private val on = MutableStateFlow(false)
    override val activeProfileId: StateFlow<String?> = profile
    override val enabled: StateFlow<Boolean> = on

    init {
        appScope.launch {
            // Nothing reads the adopted file before the migration has finished and the profiles are loaded.
            if (stores.warm() !is Outcome.Ok) return@launch
            launch { stores.profiles.activeId.collect { profile.value = it?.raw } }
            on.value = store.flagOn()
            stores.prefs.changes.collect { keys -> if (FactsBookStore.FLAG_KEY in keys) on.value = store.flagOn() }
        }
    }

    override suspend fun setEnabled(on: Boolean) {
        if (store.setFlag(on)) this.on.value = on
    }

    override fun pairingGateway(baseUrl: String): HttpFactsBookGateway = HttpFactsBookGateway(httpFor(baseUrl), null)
}
