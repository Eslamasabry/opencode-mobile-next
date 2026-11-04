package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.termux.PreferencesTermuxMigrationJournal
import io.github.eslamasabry.oc.android.termux.TermuxMigrationArchiveStore
import io.github.eslamasabry.oc.android.termux.readSavedMigrationProviderNames
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.feature.servers.presentation.ImportedProviderReview
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Offline inspection only. The archive store takes its root from the app, never from a form, intent
 * or server.
 */
@ContributesBinding(AppScope::class, binding = binding<ImportedProviderReview>())
@Inject
class AndroidImportedProviderReview(
    app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
) : ImportedProviderReview {
    private val archives = TermuxMigrationArchiveStore(app.filesDir.toPath(), dispatchers)

    override suspend fun read(profile: ProfileId): Outcome<List<String>> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val saved =
            stores.profiles.profile(profile)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val raw =
            (stores.prefs.get(PreferencesTermuxMigrationJournal.key(profile.raw)) as? PrefValue.Str)
                ?.value
        val answer =
            withContext(dispatchers.io) {
                attempt({ Failure.StorageRefused() }) {
                    val record =
                        if (raw == null || raw.length > MAX_JOURNAL_CHARS) null
                        else Json.parseToJsonElement(raw) as? JsonObject
                    readSavedMigrationProviderNames(record, archives)
                }
            }
        return if (stores.profiles.profile(profile) === saved) answer
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    private companion object {
        const val MAX_JOURNAL_CHARS = 64 * 1024
    }
}
