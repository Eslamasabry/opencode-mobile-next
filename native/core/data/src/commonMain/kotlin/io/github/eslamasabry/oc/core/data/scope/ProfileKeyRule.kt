package io.github.eslamasabry.oc.core.data.scope

/**
 * The profile sweep rule, ported from Flutter `ProfileStore.profileScopedPreferenceKeys` (`lib/state/profiles.dart`,
 * 2.1.0+57) line by line.
 *
 * Plan 05 §3.4 summarises it as "ends with `.<id>` or contains `.<id>.`". That summary is looser than the code it calls
 * verbatim: it would also take keys whose ITEM or LOCATION text happens to equal another profile's id (a session or a
 * draft target named like the id), and app-wide namespaces such as `oc.notify.*`. The Dart rule matches the id only at
 * its owner position, so this port keeps it (deviation recorded in the S1-DATA report).
 *
 * - Ordinary keys: `oc.<what>.<id>` exactly, or `oc.<what>.<id>.agents` (the phone's agent backend pseudo-profile).
 * - `oc.draft.<target...>.<id>`: owner last.
 * - `oc.genui.<covered|enabled|journal>.<id>`: one item segment before the owner.
 * - `oc.providerRuntimeRefresh.<version>.<id>.<more>`, `oc.providerRuntimeUnloadable.<id>.<more>`,
 *   `oc.promptStash.<id>.<more>`, `oc.orchestration.<id>.<more>`: nested owner.
 * - `oc.phraseChips.<id>` but not the app-wide `oc.phraseChips.enabled`.
 * - App-wide namespaces with several segments, and external agents (own lifecycle), are never taken.
 * - Historical ids may contain dots: a longer saved id that starts with `<id>.` owns its own keys.
 */
public object ProfileKeyRule {
    public const val AGENT_BACKEND_SUFFIX: String = ".agents"

    /** Namespaces that are app-wide despite having several segments, or that have their own deletion lifecycle. */
    public val APP_WIDE_NAMESPACES: Set<String> = setOf(
        "transcript", "notify", "firstRun", "nudges", "secure", "dev", "linuxReset", "mobile", "replySpeed",
        "readStateConsentMigration", "usageAlertSeen", "usageAlerts", "usageWidget", "a2aTasks", "a2aBearer",
    )

    /**
     * The keys of [keys] owned by [profileId]. [knownIds] are the other saved profile ids (from `oc.profiles` and the
     * loaded profiles); they protect longer dotted ids and an exact `<id>.agents` profile.
     */
    public fun scopedKeys(profileId: String, keys: Collection<String>, knownIds: Set<String>): Set<String> {
        if (profileId.isEmpty()) return emptySet()
        val ids = knownIds + profileId
        val longerIds = ids.filter { it.length > profileId.length }
        val backendId = profileId + AGENT_BACKEND_SUFFIX
        // A saved profile with this exact id is its own owner, not a backend.
        val ownedIds = if (backendId in ids) setOf(profileId) else setOf(profileId, backendId)

        fun nestedOwner(value: String): Boolean =
            value.startsWith("$profileId.") && longerIds.none { value.startsWith("$it.") }

        fun owns(key: String): Boolean {
            if (!key.startsWith("oc.")) return false
            val namespaceEnd = key.indexOf('.', 3)
            if (namespaceEnd < 0) return false // app-wide keys such as oc.profiles have no owner
            val namespace = key.substring(3, namespaceEnd)
            val ownerAndItem = key.substring(namespaceEnd + 1)
            if (namespace in APP_WIDE_NAMESPACES) return false
            return when (namespace) {
                "phraseChips" -> ownerAndItem != "enabled" && ownerAndItem in ownedIds
                "draft" ->
                    ownedIds.any { ownerAndItem.endsWith(".$it") } && longerIds.none { ownerAndItem.endsWith(".$it") }
                "genui" -> {
                    val itemEnd = ownerAndItem.indexOf('.')
                    itemEnd >= 0 && ownerAndItem.substring(itemEnd + 1) in ownedIds
                }
                "providerRuntimeRefresh" -> {
                    val versionEnd = ownerAndItem.indexOf('.')
                    versionEnd >= 0 && nestedOwner(ownerAndItem.substring(versionEnd + 1))
                }
                "providerRuntimeUnloadable", "promptStash", "orchestration" -> nestedOwner(ownerAndItem)
                else -> ownerAndItem in ownedIds
            }
        }

        return keys.filterTo(LinkedHashSet(), ::owns)
    }
}
