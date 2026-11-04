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
            HandoffKeys.register(this)
            // PROTO: the public setup registry cache (`oc.setupRegistry.<profileId>`); SEC-2's setup assistant
            // registers the same prefix when it opens.
            register(
                KeyShape(
                    "oc.setupRegistry.",
                    KeyScope.Profile,
                    PrefType.Str,
                    "engine:runtime",
                    "Public setup registry consent and metadata",
                )
            )
            // REST-1: per-profile monitoring keys.
            AndroidProfileMonitoring.registerKeys(this)
            // PORT-SCREENS-2: reader choices, new-chat project, shared project folders, list sources, opening titles.
            io.github.eslamasabry.oc.engine.settings.reader.ReaderPreferencesStore.register(this)
            register(
                KeyShape(
                    "oc.newChatProject.",
                    KeyScope.Profile,
                    PrefType.Str,
                    ":feature:chats",
                    "last project for new conversation",
                )
            )
            register(
                KeyShape(
                    "oc.sharedProjects.",
                    KeyScope.Profile,
                    PrefType.StrList,
                    ":engine:runtime",
                    "selected shared-storage project folders",
                )
            )
            register(
                KeyShape(
                    "oc.chatListShown.",
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":feature:chats",
                    "conversation source visibility",
                )
            )
            register(
                KeyShape(
                    "oc.sessionInventory.",
                    KeyScope.Profile,
                    PrefType.Str,
                    ":feature:chats",
                    "read-only opening conversation titles",
                )
            )
        }
}
