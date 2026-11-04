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
    // NetworkStatus and DeviceFacts are provided once, by PreviewBindings (PreviewStores.kt).

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
            // AGENT-1: explicit computer usage reader consent (`oc.usageComputerReader.<profileId>`).
            register(
                KeyShape(
                    "oc.usageComputerReader.",
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":engine:integrations-usage",
                    "Explicit computer usage reader consent",
                )
            )
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
            // AGENT-2: the phone helper's secret, collector rules, widget routing and monitor consent
            // (moved here from PreviewRuntime's local scope; the rest of its list is registered above).
            registerSecret("oc.agentHostSecret.", ":engine:agents")
            io.github.eslamasabry.oc.engine.integrations.quota.CollectorRules.register(this)
            register(
                KeyShape(
                    "oc.widgetTarget.",
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:attention",
                    "monitor and private widget routing metadata",
                )
            )
            register(
                KeyShape(
                    "oc.monitorConsent.",
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":engine:attention",
                    "explicit per-server foreground monitor consent",
                )
            )
            // STATE: saved native state (pins, budgets, models, prompt history, pending commands and more).
            registerStateKeys(this)
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
            // ANDR-1: consent records (moved here from PreviewRuntime's local scope; its monitor keys
            // and session pins are already registered above by AndroidProfileMonitoring.registerKeys
            // and registerStateKeys).
            io.github.eslamasabry.oc.engine.settings.consent.ConsentKeys.shapes.forEach { register(it) }
            // 45-PORT-STATE: native form drafts and device-only viewed watermarks / return briefs
            // (moved here from PreviewRuntime's local scope).
            SavedStateFormDrafts.registerKeys(this)
            NativeSessionReads.shapes.forEach { register(it) }
            // 40-PORT-SCREENS-3: the unit's per-profile keys, swept by the same deletion
            // (duplicate prefixes only add a second identical shape).
            NativeProfileMonitoring.register(this)
            AndroidProjectStorageAccess.register(this)
            NativePhraseSuggestions.register(this)
            NativeReadStateConsent.register(this)
            NativePermissionInvitations.register(this)
            NativeConnectionForms.register(this)
            NativeServerDefaults.register(this)
            AndroidPlansReaders.register(this)
            PlansLegacyCollectors.register(this)
            // 44-PORT-UI: model favorites and recent models (`oc.modelLibrary.<profileId>`).
            register(
                KeyShape(
                    io.github.eslamasabry.oc.engine.agents.models.ModelKeys.MODEL_LIBRARY_PREFIX,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:agents",
                    "model favorites and recent models",
                )
            )
        }
}
