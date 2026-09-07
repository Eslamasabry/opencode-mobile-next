# Codex account source checkpoint — unverified WIP

Preserved before returning to the higher-priority voice dispatch-correlation fix.
This branch is **not ready to merge**. No completed account feature is claimed.

## Finish line

Connected supported Codex profile → Account panel → explicit official device-code
sign-in/cancel → authoritative account status and independently available reported
rate windows/token totals. No logout, account-switch UI, provider-token handling,
billing actions, external-token auth, or real-account development probes.

## Feasibility evidence

- Installed binary `C:/Users/Eslam/AppData/Local/Programs/OpenAI/Codex/bin/codex.exe`
  reported exactly `codex-cli 0.153.4`.
- `codex app-server generate-json-schema --out build/traycer/codex-schema`
  completed successfully in this worktree. The ignored, generated schema includes
  `account/read`, `account/login/start` with `chatgptDeviceCode`, its matching
  result, `account/login/cancel` (`canceled`/`notFound`), account/login completion
  and account update notifications, `account/rateLimits/read`, and
  `account/usage/read`. Experimental API opt-in was not used.
- The [official app-server documentation](https://learn.chatgpt.com/docs/app-server)
  describes the same supported managed device-code flow. Provider tokens remain
  at the host. Internal external-token authentication is outside this feature.
- No listener, real account login/logout, provider request, credential file read,
  or account mutation was performed. Schema evidence is not callable runtime proof.

## Implemented source boundary

Optional domain account session/capability; pinned-version transport gate and
epoch-bound requests; Codex mappings and transient owned-login cancellation;
controller with independent metric availability; account route and responsive
panel; connected-profile menu entry; English ARB source. The committed voice
portfolio document was copied as requested; Claude and phone-tool scopes remain.

No persistent account state was added. Codes are excluded from model `toString`
and fixed error copy; verification links use the existing external-link gate.
The host login URL is restricted to the documented official device-code route.

## Required continuation

1. Complete lifecycle/concurrency review and meaningful regression fixtures:
   completion before start reply, close/cancel while start waits, account update
   during sign-in or metric reads, foreign completion, scope loss/reconnect,
   stale refresh results, unsupported version/method, null/zero/malformed data.
   Check cleanup of a late start response without allowing a cancellation on a
   replacement connection. Check cancellation uncertainty recovery and duplicate
   completion semantics. No regression tests have been written yet.
2. Confirm pinned-runtime callability in the separately granted scratch runtime
   slot, using an empty isolated home and no provider/account mutations. Device
   start/completion remain schema plus synthetic-fixture evidence; do not turn
   the prohibition on live login into an implied verification claim.
3. Restore this worktree's dependencies, generate l10n, then focused serial tests,
   analyzer, dark/light/large-text widget captures and visual inspection.
   Generated localization currently lacks the new ARB accessors, so the source
   checkpoint must not be treated as buildable before generation.
4. Record limits, finish the source review, and commit the usable slice locally
   with `[skip ci]`. No push, release, signing, or CI action is authorized.

Changed Dart files were formatted successfully. Formatter warned that package
resolution is unavailable because dependency restore has not run. ARB JSON was
parsed successfully. No Flutter test, analyzer or native runtime check ran.
