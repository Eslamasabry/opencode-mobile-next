package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.engine.settings.pins.SessionPinStore
import io.github.eslamasabry.oc.engine.settings.reader.ReaderPreferencesStore

/** STATE migration keys use the same registry for server removal and the OS manage-space entry. */
internal fun stateProfileScope(stores: PreviewStores): DefaultProfileScope =
    DefaultProfileScope(stores.prefs, stores.secrets, stores.lanes.app).apply { registerStateKeys(this) }

/** The same keys on any registry; the shared ProfileScope (SecurityFeatureBindings) registers them too. */
internal fun registerStateKeys(scope: ProfileScope) {
    scope.apply {
        register(ReaderPreferencesStore.SHAPE)
        register(SessionPinStore.SHAPE)
        for ((prefix, owner) in
            listOf(
                "oc.consumptionBudgets." to ":engine:integrations-usage",
                "oc.setupAudit." to ":engine:runtime",
                "oc.setupRegistry." to ":engine:runtime",
                "oc.automaticActivity." to ":engine:attention",
                "oc.sessionDisplayModels." to ":engine:agents",
                "oc.sessionModels." to ":engine:agents",
                "oc.contextUsage." to ":engine:settings",
                "oc.sessionInventory." to ":feature:chats",
                "oc.promptStash." to ":engine:composer",
                "oc.promptHistory." to ":engine:composer",
                "oc.olderDraftsBackupV1." to ":engine:composer",
                "oc.pendingCommands." to ":engine:delivery",
                "oc.developmentServices." to ":engine:runtime",
            )) register(
            KeyShape(prefix, KeyScope.Profile, PrefType.Str, owner, "saved native state")
        )
        for (prefix in listOf("oc.stashAttachmentVault.", "oc.savedPromptMigrationV1.")) {
            register(
                KeyShape(
                    prefix,
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":engine:composer",
                    "acknowledged migration ownership",
                )
            )
        }
    }
}
