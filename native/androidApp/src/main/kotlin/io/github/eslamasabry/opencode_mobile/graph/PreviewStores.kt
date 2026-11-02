package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fresh, separate preview package only. This is deliberately not a production migration gate. */
@SingleIn(AppScope::class)
@Inject
class PreviewStores(application: Application, dispatchers: AppDispatchers, redactor: Redactor) {
    init {
        check(application.packageName == "io.github.eslamasabry.opencode_mobile.native")
    }

    private val gate = StateMigrationGate.alwaysOpen()
    val prefs = AndroidDataStores.flutterPrefs(application, gate, dispatchers.io)
    val secrets = AndroidDataStores.secrets(application, gate, redactor, dispatchers.io)
    val lanes = DefaultWriterLanes()
    val profiles = ProfileStore(prefs, secrets, lanes, redactor)
    private val warming = Mutex()
    @Volatile private var prepared = false

    /** Every synchronous composer read follows both preference warm-up and profile restoration. */
    suspend fun warm(): Outcome<Unit> = warming.withLock {
        if (prepared) return@withLock Unit.ok()
        when (
            val result =
                attempt({ Failure.StorageRefused() }) {
                    prefs.warm()
                    profiles.load().map { Unit }
                }
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok -> result.value.also { if (it is Outcome.Ok) prepared = true }
        }
    }

    fun requirePrepared() {
        check(prepared) { "preview stores are not ready" }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PreviewBindings {
    @Provides fun profiles(stores: PreviewStores): ProfileStore = stores.profiles

    @Provides
    @IntoSet
    fun delivery(stores: PreviewStores): DeliveryStorage =
        DeliveryStorage(
            stores.prefs,
            stores.lanes,
            ProfileDirectory { stores.profiles.profile(it) != null },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun http(): HttpClientFactory = HttpClientFactory(OkHttpEngineFactory())

    @Provides @SingleIn(AppScope::class) fun random(): Random = SecureRandom().asKotlinRandom()
}

@ContributesIntoSet(AppScope::class, binding = binding<ConnectionCredentialSource>())
@Inject
class PreviewCredentials(private val profiles: ProfileStore) : ConnectionCredentialSource {
    override suspend fun resolve(profile: ServerProfile): Outcome<ConnectionCredentials> {
        val loaded =
            profiles.profile(profile.id)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (loaded.toServerProfile() != profile)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        if (loaded.reentry.password || loaded.reentry.agentToken)
            return Failure.SecretUnavailable().failed()
        return when (val secret = profiles.secret(profile.id)) {
            is Outcome.Failed -> secret
            is Outcome.Ok -> ConnectionCredentials(profile.username, secret.value).ok()
        }
    }
}
