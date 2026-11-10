# Settings scope state handoff — 2026-10-12

Implemented in `feat/settings-scope-state`, in this worktree only. The [frozen UI contract](../../design/settings-scope-contract-2026-10-12.md) was committed alone first as `3d75d23e6`. This implements the approved study's A/B/D state boundary and the OC1 chat-agent correction. It does not claim completion of the Settings redesign or the wider scope roadmap.

## What now works

| Behavior | Evidence |
|---|---|
| One acknowledged device-preference owner serves main, secondary and isolated controllers, including separate ProfileStore instances sharing SharedPreferences. Appearance, theme, language, effects and transcript flags update live. | `settings_scope_controller_test.dart`: **live controllers share acknowledged display preferences across stores**; **disposing a secondary controller leaves shared preferences usable**. |
| Device writes serialize; false/throwing storage cannot produce a successful save or expose the plugin's temporary cache as confirmed truth. Partial effects writes restore attempted keys or report reloaded partial truth. | `app_preferences_store_test.dart`: **writes serialize and listeners see only acknowledged values**; **effects rollback restores earlier keys after a later refusal**; **failed rollback reports failure and publishes reloaded partial truth**; **failed reload cannot leak optimistic values into a later save**. |
| Local reads expose target, authority, origin, availability, revision and mutability. Watches stay bound to their original target. Edits use issued, one-use tokens. No facade operation connects to a server. | `settings_access_test.dart`: **active connection changes do not retarget a captured consent save**; **cross-facade competing writes conflict and fabricated tokens fail**; **transport replacement invalidates tokens and existing watch context**; **selection reads and streams publish only acknowledged shared writes**. |
| Every controller observes the same privacy epoch. Revocation blocks a waiting send and prevents an already transmitted request's late response from acknowledging local state. | `settings_scope_controller_test.dart`: **revoking sharing in another controller prevents a waiting receipt**; **a transmitted receipt cannot acknowledge state after shared revocation**; **delayed opt-in followed by opt-out never publishes sharing on**. |
| A failed opt-out stays private in memory and returns failure. A save cannot grant consent to a replacement transport. | `settings_scope_controller_test.dart`: **failed opt-out stays private in every controller and advances shared epoch**; `settings_access_test.dart`: **late transport change cannot grant consent to the replacement**. |
| Consent migration preserves the old population's choice, defaults new profiles off, and resumes from a durable cohort without network requests. | The migration tests listed below; the consent store has no gateway/network dependency. |
| The existing Privacy switch names its connection, captures its owner and writes that profile only. Its capability check and existing kit components remain. | `session_read_state_test.dart`: **privacy control requires a named connection with read-state support**; controller tests reject a mismatching expected profile. |
| OC1 chat-agent choices persist without moving the profile default or another chat. Session deletion clears the override; profile deletion drains pending writes and sweeps the key. OC2/Paseo retain their existing server-owned selection path. | Six tests in `settings_session_agent_test.dart`, including **choosing an OC1 chat agent leaves other chats and profile default alone**, **a chat choice is not published before storage acknowledges it**, and **profile deletion drains a pending chat-agent write without resurrection**. |

## Changes by slice

**A — metadata and provenance.** `lib/domain/settings_scope.dart` defines six owner kinds, distinct authorities, effective values, fixed error reasons, edit tokens and the 64-row Settings scope catalog. `settings_scope_fixture.dart` supplies the deterministic in-memory UI fixture. `lib/state/settings_access.dart` implements local reads/watches and the narrowly supported A/B/D writes. Model/agent/variant snapshots are local new-chat preferences, read-only through this facade; they are not remote server defaults. Unsupported targets stay unsupported.

**B — shared state and target pinning.** `AppPreferencesStore` owns the shared snapshot and persistence lane. Controllers retain their existing ValueNotifier interfaces as observed mirrors and remove listeners on disposal. ProfileStore delegates display writes, shares acknowledged profile observations, and exposes confirmed selection metadata. The facade checks captured transport identity and revision instead of reading whichever server is active when Save finishes. Deletion closes consent admission, drains accepted consent/selection writers and uses the existing scoped-key sweep. The legacy locale controller method retains its established exception compatibility; the new facade uses typed failures.

**D — consent and minimal Privacy wiring.** `ReadStateConsentStore` owns per-profile consent, pending-state admission, the shared epoch and the migration journal. `ProfileStore.load` and pre-insertion `upsert` invoke local migration. Every receipt path rechecks privacy after asynchronous work. The existing Privacy row captures a profile ID, displays that profile's safe name and disappears if the controller has moved to another target. This is the only product UI change; it uses existing kit parts and strings.

**State bug correction.** OC1 `selectAgentForSession` now uses an acknowledged per-chat map, capped at 200 entries per profile. Chat choices survive another controller/reconnect without changing new-chat defaults. Existing prompt dispatch already calls `agentForSession`; these tests prove state selection and persistence, not a live-provider prompt run.

## Storage and migrations

Existing device preference keys/defaults remain unchanged. No active server's model is promoted into a new global preference. No credentials are copied, read into the scope facade, or included in tokens/errors.

| Key / rule | Verification |
|---|---|
| `oc.readStateConsentMigration.v1`: durable original IDs plus captured legacy bool; completion written last; completed journal retains no IDs. | **interrupted durable cohort does not absorb later profiles after restart**; **refused migration completion stays private and retry can finish**. |
| `oc.shareSessionViews.<profileId>` and `oc.shareSessionViewsOrigin.<profileId>`: preserve existing destinations; old true/false copied only for saved profiles; absent legacy means the old shipped true default; malformed legacy fails closed. | Controller migration cases for true/false/null; **migration preserves explicit destination choices**; three **malformed legacy … cannot grant sharing** cases. |
| New profile consent is false. The legacy global key remains for rollback compatibility and is never a runtime fallback. Migration itself cannot authorize before acknowledgment. | **missing legacy default migrates only the initial saved population**; **migration cannot expose an optimistic true before disk acknowledgment**; **fresh profiles are private and consent changes only the named profile**. |
| Deletion closes admission before awaiting, prunes an unfinished journal, drains writes, sweeps both consent keys and invalidates observers. Failed deletion permits retry without restoring sharing. | **deletion drains a pending grant, prunes its journal and sweeps both keys**; **failed journal pruning is retryable and never restores consent**; existing `profile_deletion_test.dart` cases now include the migrated keys in truthful deletion counts. |
| `oc.sessionAgents.<profileId>`: session-ID map, bounded, covered by suffix sweep. | Chat-agent persistence, chat deletion and pending profile-deletion tests. |

Migration runs entirely locally. Unknown server capability cannot by itself transmit a receipt: dispatch still requires the actual `SessionReadStateGateway` and a visible loaded chat. No new connector, poll or server request was added to migration. Storage that refuses an opt-out cannot be forced to save it: memory stays private, the operation fails, and the UI reports failure. That is not a promise that an old persisted true is erased across restart. Already transmitted requests cannot be recalled.

## Validation

Pinned Flutter:

```text
/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
```

Dependency resolution ran once under the machine lock. Changed Dart files were formatted with the adjacent pinned `dart format`. Every focused test invocation used:

```text
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 <files below>
```

All of these files have passed their focused checks:

- `test/settings_scope_domain_test.dart` — eight pure domain/fixture tests.
- `test/app_preferences_store_test.dart` — ten shared-store tests.
- `test/read_state_consent_test.dart` — eleven migration/deletion tests.
- `test/settings_scope_controller_test.dart` — ten controller/consent tests.
- `test/settings_access_test.dart` — ten production-facade tests.
- `test/settings_session_agent_test.dart` — six chat-agent tests.
- `test/session_read_state_test.dart` — eighteen existing read-state/UI cases, with named profiles and explicit opt-in fixtures.
- `test/profile_store_test.dart` and `test/profile_deletion_test.dart`.
- `test/session_model_scope_test.dart`.
- `test/app_locale_test.dart`.
- `test/appearance_effects_test.dart` and `test/appearance_picker_test.dart`.
- `test/settings_hub_test.dart` — includes narrow/large-text English and Arabic layout checks.
- `test/side_connections_test.dart` — includes Termux beside in-app Ubuntu and routing on each connection.

Initial focused checks found and fixed early chat-agent publication, old deletion-count/error expectations, missing test imports and fixture assumptions about anonymous/global consent. Review added delayed migration and delayed transport-change regressions. The analyzer's first pass found style warnings and a duplicate-set test literal; those were corrected without ignores. Final production revision: `c5a870dff`. The final reconciliation batch below passed **67 tests**, after the shared-store/identity/deletion fixes:

```text
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/profile_store_test.dart test/profile_deletion_test.dart test/settings_scope_domain_test.dart test/settings_access_test.dart test/settings_scope_controller_test.dart test/read_state_consent_test.dart
```

The final chat-agent file separately passed **6 tests**. The final command `tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze --no-pub` completed with **No issues found**. `git diff --check` and the QA document's local-link check passed. These are focused results for this change, not a full-suite result.

No full suite, build, app launch, device run, live-server migration, push, release or deployment was performed. The coordinator owns the integration/full-suite gate. Tests use mocked secure storage where profiles load/upsert; this is local/offline evidence, not owner-device qualification.

## Contract and UI builder handoff

The original freeze is unchanged except for the appended dated **Changes — 2026-10-12: implementation integration** section. It records guarded consent integration and acknowledged provenance helpers, plus the storage-refusal limitation. No frozen UI signature, value schema, row label or supported-write set was removed or renamed.

For slice C, use `ProfileSettingsAccess(store)` on a page with a captured `SettingsTarget`. Subscribe to `watchSetting`, retain a token from `beginEdit`, and pass that token to `writeSetting`. Reread on conflict; stop editing on stale/deleted/disposed targets. Use `MemorySettingsAccess(initial: ...)` for UI tests. Dispose the facade/subscription, not `store.appPreferences`. Read-only connection `model`, `agent` and `variant` describe this app's new-chat preference. Do not present them as remotely confirmed defaults or offer remote writes through this facade.

Remaining work:

- **C:** the five-group Settings redesign, canonical named connection pages, search routes, localized consequence/provenance labels, regular/advanced progressive disclosure and complete phone/tablet/RTL accessibility validation.
- **E:** personal preferred-model inheritance, per-project defaults, richer adapter selection semantics and unavailable-preference UX. The OC1 agent fix here does not implement that cascade.
- **F:** proven host/account identity, provider-account deduplication and Usage-source ownership. No Usage/widget behavior changed here.
- **H:** capability-aware remote copying, templates, receipts, partial failure and secret-free import/export. No generic server-config mutation or queued fan-out was added.
- **G remains outside this assignment:** the Usage widget and its native lifecycle qualification.

## Local commit ledger

- `3d75d23e6` — contract-only freeze, first.
- `54ce8c917` — domain types, catalog and fixtures.
- `a905090a7` — per-profile consent owner and resumable migration.
- `5b115eed4` — acknowledged shared app preferences and dated contract additions.
- `005071bde` — controller/ProfileStore integration, deletion and minimal Privacy wiring.
- `862d22140` — OC1 chat-agent isolation and deletion coverage.
- `c5a870dff` — target-bound production facade and provenance tests.

All commits are local and contain `[skip ci]`. This handoff is committed separately after the final checks.
