package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile

/*
 * The phone agents' helper connection is saved as a profile (`<owner>-agents`, AppLocalAgentHostStarter) only because
 * native connects every list source through a saved profile. To a person it is part of "This phone": Flutter keeps it
 * a side connection of the phone's profile and never lists it as a server (lib/state/connection/side_connections.dart
 * :61-100, phone_agents_backend.dart:117-128). Every list a person sees servers in leaves it out with these.
 */

/** Whether [loaded] is the phone agents' helper profile, which no server list shows. */
internal fun PreviewStores.isPhoneHelper(loaded: LoadedProfile): Boolean =
    loaded.profile.backend == BackendKind.Paseo &&
        PhoneAgentRules.isHelperProfile(loaded.id.raw, helperOwner(loaded.id))

/** Whether saved profile [id] is the phone agents' helper. */
internal fun PreviewStores.isPhoneHelper(id: ProfileId): Boolean =
    profiles.profile(id)?.let { isPhoneHelper(it) } == true

/** The saved "This phone" server helper [id] belongs to; null when [id] is not the helper or no phone is saved. */
internal fun PreviewStores.phoneOfHelper(id: ProfileId): ProfileId? {
    if (!isPhoneHelper(id)) return null
    return PhoneAgentRules.phoneOf(
            id.raw,
            helperOwner(id),
            (prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value,
            profiles.profiles.value.map { it.id.raw }.toSet(),
        )
        ?.let(::ProfileId)
}

/**
 * Gives each saved helper its phone's name ([PhoneAgentRules.helperNames]): earlier builds saved it as "Phone agents",
 * and every place that names a connection (a chat's header and Details, an alert, All conversations, a widget, the
 * Inbox) read that name. Run before the helper connects (a renamed profile reopens its connection). A failed write
 * leaves the old name until the next start; nothing else depends on it.
 */
internal suspend fun PreviewStores.nameHelpersAfterPhones() {
    val saved = profiles.profiles.value
    val renames =
        PhoneAgentRules.helperNames(saved.map { it.id.raw to it.profile.name }) { id ->
            phoneOfHelper(ProfileId(id))?.raw
        }
    for ((id, name) in renames) {
        val helper = profiles.profile(ProfileId(id)) ?: continue
        profiles.upsert(helper.profile.copy(name = name), expected = helper)
    }
}

/** The name a helper is saved under: its phone's ([phone]), as everywhere else; [fallback] only with no phone name. */
internal fun PreviewStores.helperName(phone: ProfileId, fallback: String): String =
    profiles.profile(phone)?.profile?.name?.takeIf { it.isNotBlank() } ?: fallback

private fun PreviewStores.helperOwner(id: ProfileId): String? =
    (prefs.get("oc.phoneAgentOwner.${id.raw}") as? PrefValue.Str)?.value
