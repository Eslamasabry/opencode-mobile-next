package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope

/**
 * The per-profile DATA of features deferred at the switch (ADR D6, plan 02 §3.3/§3.7, plan 15 Appendix B FORCE
 * rows): AI Team (orchestration plugin, team project editor, phone project engine) and FactsBook memory. The features
 * are not ported; their data stays on the device and is still deleted with its profile. Registering the shapes here
 * makes the sweep and the deletion receipt name them, and `ProfileDeletionSweepTest` seed them.
 *
 * Every shape below is `oc.<what>.<id>` or `oc.orchestration.<id>.<field>`, so the Flutter sweep rule already finds
 * them; their secure-storage names are in [ProfileKeys.SECRET_PREFIXES]. Their files (`files/oc.teamEngine.<id>/`,
 * the FactsBook vault) belong to the `:engine:runtime` cleaner (plan 05 §3.4).
 */
public object DeferredFeatureData {
    public const val TEAM: String = "deferred:team"
    public const val FACTSBOOK: String = "deferred:factsbook"

    public val SHAPES: List<KeyShape> = listOf(
        // AI Team plugin cache: snapshot, cursor, refreshedAt, last-known record, work view (B-STATE-145, B-AGENT-311).
        KeyShape("oc.orchestration.", KeyScope.Profile, PrefType.Str, TEAM, "AI Team plugin cache, oc.orchestration.<id>.<field>"),
        // Team project editor drafts and settings (B-SEC-176, B-AGENT-301).
        KeyShape("oc.teamEditorDrafts.", KeyScope.Profile, PrefType.Str, TEAM, "team project editor drafts (no secrets)"),
        KeyShape("oc.teamModel.", KeyScope.Profile, PrefType.Str, TEAM, "team model"),
        KeyShape("oc.teamRoles.", KeyScope.Profile, PrefType.Str, TEAM, "team roles"),
        KeyShape("oc.teamTaskRoles.", KeyScope.Profile, PrefType.Str, TEAM, "team task roles"),
        KeyShape("oc.teamWorkerStart.", KeyScope.Profile, PrefType.Int64, TEAM, "team worker start"),
        KeyShape("oc.teamWorkspace.", KeyScope.Profile, PrefType.Str, TEAM, "team workspace"),
        KeyShape("oc.teamMigrationKept.", KeyScope.Profile, PrefType.Bool, TEAM, "team migration kept"),
        // Phone project engine tombstone: retained through the row commit (B-STATE-058, B-AGENT-193).
        KeyShape("oc.teamEngineDeleted.", KeyScope.Profile, PrefType.Bool, TEAM, "phone engine deletion tombstone"),
        // FactsBook pairing link (B-STATE-020); its token, owner seed and outbox are secrets.
        KeyShape("oc.factsbookNode.", KeyScope.Profile, PrefType.Str, FACTSBOOK, "FactsBook pairing link"),
    )

    public fun register(scope: ProfileScope) {
        SHAPES.forEach(scope::register)
    }
}
