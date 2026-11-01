# Session links and profile deletion audit

Finish line: session address ingress/discovery remains fail-closed, binding persistence excludes recognized credentials, and deleting one profile cannot sweep another profile's data. Non-goal: UI implementation or enabling the unavailable address-link production capability.

## Findings

| ID | Severity | Scenario and evidence | Fix |
| --- | --- | --- | --- |
| LS-1 | Medium, local data loss | `ProfileStore.profileScopedPreferenceKeys` matched the profile text at **any** dot-delimited position. Deleting `doomed` selected another owner's `oc.promptStash.keeper.doomed`, `oc.orchestration.keeper.mutations.doomed.entry`, draft target, and provider-runtime location. IDs such as `profiles` could select a global blob. A shorter profile ID also matched another saved dotted ID. Original location `lib/state/profiles.dart:970`; fixed implementation `lib/state/profiles.dart:965`. | Match the actual owner position. Ordinary namespace keys require exact owner; legacy draft owner is the suffix; only inventoried nested families match an owner prefix, choosing the longest saved profile ID for the legacy unescaped format. Global namespaces and the independent external-agent registry are excluded. No stored-format migration. |
| LS-2 | Low, latent plaintext credential persistence | Binding validation checked a UUID before lowercasing it. An uppercase UUID matching a registered lowercase credential was accepted then persisted as that credential. A credential registered after `save` admission but before the asynchronous write was also persisted. Address handoff production is disabled, limiting current exposure. Original location `lib/state/session_link_bindings.dart:85` and `:145`; fixed `:85` and immediately before `jsonEncode` in `_save`. | Validate both supplied/canonical identifiers with `containsSecret`; repeat full canonical validation immediately before the preference write. Refuse with the existing content-free failure category. |

Migration offer deletion admission/drain wiring in `lib/state/profiles.dart` and `lib/state/connection.dart` accompanies the migration owner's separate fix and evidence. It closes admission synchronously, awaits admitted writes before sweep, and reopens after an aborted deletion only through the owner's durable-membership check.

## Focused verification

Toolchain: `/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`. Every test invocation uses `tool/qa/machine_lock.sh test --`; formatting uses the paired Dart binary with `format --language-version=3.10`.

1. Before the LS-1 source fix, `flutter test --concurrency=1 test/profile_deletion_test.dart --plain-name 'profile sweep matches owners'` failed: expected 8 owned keys, actual 13, including the five unrelated keys above. The initial owner-position fix passed 114 tests across the seven files below.
2. [links-storage-red.log](links-storage-red.log) preserves the final failing-first run for dotted-ID isolation and the two LS-2 cases. Command: `flutter test --no-pub --concurrency=1 test/session_link_bindings_test.dart test/profile_deletion_test.dart --name 'normalized binding|newly known credentials|dotted profile ids'`. All three failed as expected.
3. [links-storage-intermediate-dependency.log](links-storage-intermediate-dependency.log) records an intermediate compile failure while migration-offer wiring preceded the new dependency file. It is not a product regression or a passing gate; the complete-tree run below supersedes it.
4. [links-storage-green.log](links-storage-green.log) is the final focused run (**117 tests passed**, exit 0): `flutter test --no-pub --concurrency=1 test/profile_deletion_test.dart test/kit/kit_draft_manifest_test.dart test/session_link_bindings_test.dart test/session_address_controller_test.dart test/session_address_link_test.dart test/session_address_transport_test.dart test/session_address_link_intent_test.dart`.

Whole-project analyzer and requested architecture/security/kit gates are coordinated by the root audit owner and recorded in the main README. No phone interaction, native execution, live private-host verification, or release claim is made by this slice.

## Inspected contracts without a new finding

- `lib/platform/session_link.dart`: pending route types replace each other, malformed recognizable v2 routes retain only a failure code, and generation checks prevent late cold-start capture replacing a newer intent.
- `lib/domain/session_address_link.dart`: exact envelope/keys, bounded ASCII, one percent decode, strict HTTPS tailnet origin, UUID/session validation, secret rejection before encoding; no I/O in parsing.
- `lib/platform/session_address_transport.dart`: anonymous descriptor fetch, checked tailnet IP socket pinning, no proxy or redirects, normal hostname TLS checks, bounded time/response, and late-DNS cancellation. Existing behavior tests cover private/public mixtures, TLS, response limit, descriptor spoofing, and cancellation.
- `lib/state/session_address_controller.dart`: production constructor remains disabled; tests verify contact and binding consent, independent deployment proof, authorized scoped lookup, instance recheck, deletion cancellation, and no late session opening. Test-only harness is not production enablement.
- `lib/state/session_link_bindings.dart`: scoped key, serialized durable write verification, stale facade closure, deletion drain, and fail-closed storage failures.
- Storage writer inventory: separately versioned owner layout found only in `oc.providerRuntimeRefresh.<version>.<profile>.<location>`; nested owner-first families are `providerRuntimeUnloadable`, `promptStash`, and `orchestration`; `oc.draft.<target>.<profile>` is owner-last. Remaining per-profile preferences use a full profile ID after one namespace. Legacy unescaped nested keys prefer the longest saved owner because profiles historically accepted dots; no assumption of globally dot-free IDs is made.
- External URLs: repository-wide `launchUrl`/`launchUrlString` inspection found only the central launch in `lib/ui/widgets/external_link.dart`; inspected UI callers use `openExternalLink`. No bypass found in this inventory.

## UI hook-up contract

No UI edits or new UI wiring are required for LS-1/LS-2. Keep `SessionAddressController.available == false` and capability gating until independently verified host authorization/private-ingress evidence exists. Continue rendering `SessionAddressFailureCode` through existing localized mappings; never display or log raw link input, binding contents, descriptor exception text, or provider credential payloads. The backend fixes preserve current public signatures and failure categories.

Commits: assigned by the coordinating audit owner in the main README; this worker creates no commits or pushes.
