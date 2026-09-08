# Tailscale connection local verification

Candidate: `feature/tailscale-connection`, based on `eced3c8`.

The first-run and saved-server entries reach official-app handoff, private
HTTPS address review, then the existing authentication/probe/save/connect
editor. A failed probe or external handoff preserves the typed draft.
Installed/unverified, missing, unavailable, returned and retry states are
implemented; no app-presence result is treated as VPN connectivity.

## Checks performed

Pinned Flutter 3.47.2 with this worktree's own package configuration.
`flutter pub get` resolved dependencies, then reported the existing Windows
plugin symlink prerequisite. No system setting was changed. Localization was
generated and changed Dart files formatted. All tests used
`flutter test --no-pub --concurrency=1`.

| Files or command | Result |
| --- | --- |
| `test/tailscale_setup_test.dart` | 6 passed: address/port policy, strict native-response mapping, installed/unverified state, handoff/resume draft, missing-app host review, recovery and late-check disposal |
| `test/tailscale_profile_test.dart` | 1 passed: reload and scoped guidance deletion |
| `test/server_profile_editor_test.dart` | 9 passed: existing editor paths plus full Tailscale auth/probe failure/help/return/retry/save journey and saved-profile HTTPS enforcement |
| `test/server_probe_test.dart`, `test/profile_deletion_test.dart`, `test/platform_capabilities_test.dart`, `test/l10n_coverage_test.dart` | 39 passed |
| `test/first_run_welcome_test.dart` | 11 passed: existing first-run routing, large text, URL normalization and success/error/invalid probe behavior |
| `tool/capture/tailscale_test.dart` | 8 passed; 10 PNGs inspected |
| `flutter analyze --no-pub` | No issues found, 24.4 seconds |
| `git diff --check` | Passed |

The two new editor tests initially assumed off-screen children of lazy lists
were already built. They now scroll to the controls and verdict; only those
failures were rerun. Two brace-style findings and a capture-only fixture API
warning were corrected without ignores. There are no unresolved failures in
this focused set. This is not the full repository gate.

## Production widget evidence

Synthetic addresses, real screens, fonts and themes. No external app or real
tailnet account was used. These are widget captures, not Android screenshots.

| State | Light | Dark |
| --- | --- | --- |
| Installed, VPN unverified | [Light](installed-light.png) | [Dark](installed-dark.png) |
| Missing official app | [Light](missing-light.png) | [Dark](missing-dark.png) |
| 320 px / 2× text, handoff | [Light](narrow-light.png) | [Dark](narrow-dark.png) |
| 320 px / 2× text, address review | [Light](narrow-address-light.png) | [Dark](narrow-address-dark.png) |
| Existing authentication editor | [Light](authentication-light.png) | [Dark](authentication-dark.png) |

Large-text content scrolls vertically and action labels wrap. The address
field remains horizontally editable. The native channel was exercised with
synthetic method responses; actual package visibility/launch, Android touch
behavior and private network reachability remain unverified. The coordinator's
[fresh-device checklist](../../tailscale-connection.md#coordinator-native-handoff-checklist)
is the handoff for those cases. No real-tailnet login or account action is
authorized or claimed.
