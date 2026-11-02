package io.github.eslamasabry.oc.feature.chat.header.freemodel

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.CatalogModel

/**
 * The chat's free-model note (Flutter free_model.dart, free_model_notice.dart): replies come from OpenCode's free model
 * because no other provider is signed in on this server. Said once per conversation; a dismissal is kept per server
 * under `oc.freeModelNoteDismissed.<profileId>` (bounded), which the profile sweep removes with the server (B-CHAT-015).
 */
public object FreeModel {
    public const val PROVIDER: String = "opencode"

    /** The newest dismissals kept; an older conversation may show the note again. */
    public const val MAX_DISMISSALS: Int = 200

    public fun key(profile: ProfileId): String = "oc.freeModelNoteDismissed.${profile.raw}"

    /** Flutter `usesOpenCodeFreeModel`: the OpenCode provider, nothing else signed in, and the catalog says it is free. */
    public fun uses(signedIn: Collection<String>, providerId: String?, modelId: String?, models: List<CatalogModel>): Boolean {
        if (providerId != PROVIDER || modelId == null) return false
        if (signedIn.any { it != PROVIDER }) return false
        val model =
            models.firstOrNull { it.providerId == providerId && (it.id == modelId || it.id == "$providerId/$modelId") }
                ?: return false
        val cost = model.cost ?: return false
        return cost.inputPerMillion == 0.0 && cost.outputPerMillion == 0.0
    }

    /** Flutter `FreeModelNoteDismissals.dismiss`: moves [session] to the newest end and keeps the newest [MAX_DISMISSALS]. */
    public fun dismissed(kept: List<String>, session: String): List<String> =
        (kept.filterNot { it == session } + session).takeLast(MAX_DISMISSALS)
}

/** Durable dismissals; absent on hosts without preferences (the note then is hidden only for this visit). */
public interface FreeNoteStore {
    public fun dismissed(profile: ProfileId, session: String): Boolean

    public suspend fun dismiss(profile: ProfileId, session: String): Outcome<Unit>
}

/** What the note needs from the server: who is signed in and which catalog models exist. */
public data class FreeModelFacts(val signedIn: List<String>, val models: List<CatalogModel>)

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface FreeNoteBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true) public val freeNoteStores: Set<FreeNoteStore>
}
