# Provider reload and pinned install audit

Baseline: `bcf45eb75ed579c1ec553366941da38450925be9` on `codex/audit3`.

## Confirmed findings

- **Medium — malformed session status fails open.** A successful `/session/status` response containing null, a string, or a map without a valid type was treated as idle. Provider refresh then disposed instances and could abort an active reply. `lib/api/product_repository.dart`, `_runningSessionCount`: require a nonempty string type; all types except explicit `idle` count as running. Four failing-first fixtures reproduce the prior reset.
- **High — mutable location bypasses the reply guard.** Switching projects while status requests await the server made disposal read the new location, although only the previous location had been checked. An active reply in the new project could be aborted. `refreshProviderRuntime` now snapshots directory/workspace and uses the snapshot for both check and disposal. A real loopback HTTP regression changes location inside the status handler and proves only the checked location is disposed.
- **Medium — unauthorized startup probe falsely succeeds.** The native installer accepted HTTP 401 despite providing a fresh per-probe password. A rejecting listener could therefore pass the probe and replace the prior working install. `lib/builtin/setup/components.dart`, `openCodeNativeInstall`: require HTTP 200 and a still-live candidate process. The failing-first shell test uses a checksum-pinned synthetic program that always responds 401 and asserts failure with the previous install intact.

## Host pin hand-off (Medium, unresolved UI-owned publication dependency)

`lib/ui/setup_commands.dart:112` still pins `c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18`; its Ubuntu script has `curl -fsSL https://opencode.ai/install | bash` at historical line 75 and an unpinned `upgrade` at line 184. Hashing the wrapper is insufficient to pin what that wrapper subsequently executes. The checkout's replacement script has exact version/CPU hashes and checks before extraction, but users do not download those bytes yet. This was already documented by the prior slice; the audit confirms it remains unresolved in the actual command path. Read-only public checks returned HTTP 200 with the exact old configured hash (`host-current-pin-check.json`) and HTTP 404 for the known hardened script commit (`host-publication-check.json`).

UI-owner contract: after explicit authorization to publish a commit containing `scripts/host/ubuntu-opencode.sh`, update `HostScripts.commit`, Ubuntu SHA-256 and release description to that published revision; regenerate the two guide commands; remove `_pendingPinUpdate` in `test/host_script_pin_test.dart`. Assert the actual pinned Git blob contains neither unchecked installer execution nor self-upgrade. Until publication is available, describe this as pending rather than a delivered pin. No UI edits or publication were made by this audit.

## Validation

Failing-first run: `flutter test --concurrency=1 test/provider_reload_running_reply_test.dart test/opencode_native_install_test.dart --reporter expanded` under `tool/qa/machine_lock.sh test`: six intentional failures (four malformed statuses, location drift, unauthorized startup); 18 pass, one opt-in real-download smoke skipped. See `provider-install-red.log`.

Green run: **47 passed, one opt-in real-download smoke skipped**, recorded in `provider-install-green.log`. The complete suite additionally found a pre-existing unbaselined suppression in the installer test; removing it and rerunning the suppression/installer files yielded **10 passed, one opt-in smoke skipped** (`suppression-green.log`). All Dart edits formatted with the pinned Dart and `--language-version=3.10`.

## Limits and inspected invariants

- Status read and server disposal are separate requests. Another client can start work after the check; this change fixes local location drift and malformed status, not an upstream atomic idle-dispose contract.
- Only `lib/ui/widgets/external_link.dart` directly calls `launchUrl` in `lib/`; no new bypass found.
- `OpenCodeApi.providers` and `configuredProviders` register credential fields before decoding; credential ingress regression suite covers subsequent redaction. No provider response bodies or real credentials were logged.
- No real server downloads, owner-phone access, signing, or release actions were performed.
