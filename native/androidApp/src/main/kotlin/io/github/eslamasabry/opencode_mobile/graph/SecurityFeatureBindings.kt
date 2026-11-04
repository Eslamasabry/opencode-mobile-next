package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.platform.AndroidDeviceFacts
import io.github.eslamasabry.oc.android.platform.AndroidNetworkStatus
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.engine.integrations.quota.InAppQuotaCollector

/** Process observations and the ownership registry used by profile deletion. */
@BindingContainer
@ContributesTo(AppScope::class)
object SecurityFeatureBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun network(application: Application): NetworkStatus =
        AndroidNetworkStatus(application).also { it.start() }

    @Provides
    @SingleIn(AppScope::class)
    fun device(application: Application, network: NetworkStatus): DeviceFacts =
        AndroidDeviceFacts(application, network).also { it.start() }

    @Provides
    @SingleIn(AppScope::class)
    fun profileScope(stores: PreviewStores): ProfileScope =
        DefaultProfileScope(stores.prefs, stores.secrets, stores.lanes.app).apply {
            registerSecret("pw.", "profiles")
            registerSecret("oc.codexToken.", "profiles")
            register(
                KeyShape(
                    "oc.quotaConsent.",
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:integrations-quota",
                    "Explicit collector trust bound to saved credentials",
                )
            )
            InAppQuotaCollector.register(this)
        }
}
