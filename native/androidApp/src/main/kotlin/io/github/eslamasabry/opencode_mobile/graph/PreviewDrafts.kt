package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.PrefWrite
import io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.FilePayloadStore
import kotlin.time.Clock
import kotlinx.io.files.Path

/** No optimistic cache: all reads see only the adopted store's acknowledged values. */
internal class PreviewComposerPrefs(private val stores: PreviewStores) : ComposerPrefs {
    private fun value(key: String): PrefValue? {
        stores.requirePrepared()
        return stores.prefs.get(key)
    }

    override fun string(key: String): String? =
        when (val value = value(key)) {
            null -> null
            is PrefValue.Str -> value.value
            else -> error("unreadable composer preference")
        }

    override fun bool(key: String): Boolean? =
        when (val value = value(key)) {
            null -> null
            is PrefValue.Bool -> value.value
            else -> error("unreadable composer preference")
        }

    override fun contains(key: String): Boolean = value(key) != null

    override fun keys(): Set<String> {
        stores.requirePrepared()
        return stores.prefs.keys()
    }

    override suspend fun commit(edit: PrefsEdit): Boolean {
        stores.requirePrepared()
        return stores.prefs.commit(stores.lanes.app) {
            edit.entries.forEach { (key, write) ->
                when (write) {
                    is PrefWrite.Str -> putString(key, write.value)
                    is PrefWrite.Bool -> putBool(key, write.value)
                    PrefWrite.Remove -> remove(key)
                }
            }
        } is Outcome.Ok
    }
}

internal class PreviewComposerProfiles(private val stores: PreviewStores) : ComposerProfiles {
    override fun exists(profileId: String): Boolean {
        stores.requirePrepared()
        return stores.profiles.profile(ProfileId(profileId)) != null
    }

    override fun count(): Int {
        stores.requirePrepared()
        return stores.profiles.profiles.value.size
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PreviewDraftBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun drafts(
        stores: PreviewStores,
        application: Application,
        appScope: AppCoroutineScope,
        dispatchers: AppDispatchers,
        clock: Clock,
    ): ConversationDrafts =
        ConversationDrafts(
            Drafts(
                PreviewComposerPrefs(stores),
                AttachmentVault(
                    FilePayloadStore(
                        Path(application.filesDir.absolutePath, AttachmentVault.DRAFT_ROOT),
                        dispatchers.io,
                    )
                ),
                PreviewComposerProfiles(stores),
                clock,
            ),
            appScope,
            dispatchers,
        )
}
