package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.feature.chat.forms.FormDraftStorage

/**
 * Non-sensitive multiline form drafts only. ProfileKeyRule's oc.draft.<target>.<profile> rule
 * discovers these for the deletion sweep; the profile lane closes and drains before that sweep,
 * fencing late saves.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<FormDraftStorage>())
@Inject
class SavedStateFormDrafts(private val stores: PreviewStores) : FormDraftStorage {
    private fun key(profile: ProfileId, identity: String): String =
        "oc.draft.form-$identity.${profile.raw}"

    private fun valid(identity: String): Boolean =
        identity.length == 64 && identity.all { it in '0'..'9' || it in 'a'..'f' }

    override suspend fun read(profile: ProfileId, identity: String): Outcome<Map<String, String>> {
        if (stores.warm() is Outcome.Failed || !valid(identity))
            return Failure.StorageRefused().failed()
        if (stores.profiles.profile(profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val raw =
            stores.prefs.string(key(profile, identity)) ?: return emptyMap<String, String>().ok()
        val decoded =
            DartJson.decodeOrNull(raw) as? Map<*, *> ?: return Failure.StorageRefused().failed()
        if (decoded.size > 128 || decoded.any { it.key !is String || it.value !is String })
            return Failure.StorageRefused().failed()
        return decoded.entries.associate { it.key as String to it.value as String }.ok()
    }

    override suspend fun write(
        profile: ProfileId,
        identity: String,
        values: Map<String, String>,
    ): Outcome<Unit> {
        if (
            stores.warm() is Outcome.Failed ||
                !valid(identity) ||
                values.size > 128 ||
                values.values.sumOf { it.length.toLong() } > 1_048_576L
        ) {
            return Failure.StorageRefused().failed()
        }
        if (stores.profiles.profile(profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val key = key(profile, identity)
        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
            if (values.isEmpty()) remove(key) else putString(key, DartJson.encode(values))
        }
    }

    companion object {
        fun registerKeys(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    "oc.draft.form-",
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:requests",
                    "non-sensitive multiline form drafts; scope and schema hashed, profile owner last",
                )
            )
        }
    }
}
