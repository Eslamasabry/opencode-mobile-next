# Issue 116: Antigravity CLI integration

Finish line: install Google's Antigravity CLI through the existing phone-agent
flow, let the person complete its own browser/terminal sign-in, and drive it
through the existing Paseo ACP route while retaining qualification gates.
Non-goals: unofficial adapters, credential handling, Gemini history migration,
owner-phone access, releases, pushes, or GitHub comments.

Requested reference: `8302de537` (2.1.1 product). Actual clean checkout parent:
`9c50bd5ed` (`mobile-next/master`, includes the already-merged #114 picker).
Branch: `fix/issue-116-antigravity`; only the issue-116 commit is new here.
Research and local checks: 2026-11-05. Discovery concluded within the requested
20-minute feasibility window.

## Finding and source evidence

Google's [migration announcement](https://developers.googleblog.com/an-important-update-transitioning-gemini-cli-to-antigravity-cli/)
says consumer Gemini CLI access (free, Google AI Pro and Ultra) stopped on
2026-06-18. Enterprise and paid API access remain supported. This explains the
reporter's migration message; it is not evidence of an unsupported phone CPU.
The exact reporter session was not inspected.

The app already installs Gemini 0.63.0 and launches `gemini --acp`
(`lib/domain/agent_catalog.dart`). Its separate auth-status implementation
explicitly returns `probeUnsupported` for Gemini (`agent_scripts.dart`). Its
bundled certification record is for 0.62.0 and lacks authenticated smoke proof.
These are additional app limitations, distinct from Google's account policy.
Gemini is retained for supported uses and existing conversations; its setup and
sign-in screens now explain the Antigravity path without displaying raw errors.

Google's [official installer](https://antigravity.google/cli/install.sh) installs
`agy` into `~/.local/bin`. The inspected installer distinguishes native Android
Bionic from GNU/Linux inside proot and selects ARM64 for `aarch64`. The
[official changelog](https://github.com/google-antigravity/antigravity-cli/blob/main/CHANGELOG.md)
records native Android/Termux support and ACP. This establishes upstream support;
no ARM64 phone or proot execution is claimed by this branch.

The [official ACP guide](https://antigravity.google/docs/ide/extensions/acp/)
documents `agy acp` as ACP v1 JSON-RPC over stdio for any ACP client. It also
documents `agy acp --login`: the person opens Google's page and pastes the
browser's code into the CLI terminal. The app never handles passwords, tokens,
or keys. Gemini models are available to generic clients; third-party models
are restricted to specific IDEs, so none are promised here. Resume/history
capabilities remain unqualified.

## Pinned artifacts

Both public manifests returned 1.5.0 from the official installer's updater:
[ARM64 manifest](https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64.json),
[x64 manifest](https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_amd64.json).
Archives were downloaded, checked against manifest SHA-512, then independently
hashed with SHA-256 for the app's catalog. Archive member: `antigravity`.
The existing installer wraps that executable as `agy` and preserves profile HOME.

| Architecture | Download bytes | Executable bytes | SHA-256 |
| --- | ---: | ---: | --- |
| ARM64 | 66011999 | 227627384 | `87e73a5ab4ecb3a9668660d259dafd13f5443149da0113664d59129a21a62ba4` |
| x64 | 69781910 | 238604688 | `f65e365a67be49abdaf92f5669c3157bd4b9c569f6b9f448340b00ca261b0f01` |

Catalog launch: `agy acp --disable-telemetry`. Sign-in: `agy acp --login`.
No permissions are automatically approved. Existing download, extraction,
architecture, readiness, and certification checks remain in force.

## Account-free runtime evidence

The verified x64 executable reported `1.5.0`. Its `--help` and `acp --help`
confirmed the commands. An isolated, empty HOME with a minimal environment
(no inherited credentials) negotiated ACP v1. `session/list` and `session/new`
both returned code -32000 with the canonical authentication-required message.
No authentication or model prompt was sent. Only the exact child process group
was terminated. Sanitized observations are in [local-probe.json](local-probe.json).

The production auth script was then run against that real binary in a temporary
fixture root, substituting the guest path and UID check for this host harness.
It returned exactly `{"state":"signedOut"}`. This is x64 host evidence,
not an Android, ARM64, proot, authenticated-chat, or native-bridge execution proof.

The production status probe uses only `initialize` and read-only `session/list`.
It accepts the documented auth-required reply as signed out, or a structured
session-list result as signed in. It never creates a conversation, submits a
prompt, services tools, reads credential stores, or exports session content.
An eight-second deadline and 64 KiB aggregate limit bound the probe. Unknown,
malformed, oversized and unexpected request responses remain errors. Native
admission still validates the exact authored script digest.

## Integration and safety

- Added catalog recipe, persona/Google mark, English/Arabic guidance, and
  existing AgentSheet Widgetbook cases. Gemini gets a conditional migration
  notice; it is not declared universally unsupported.
- Added native sign-in/inventory admission, install guard, removal admission,
  and process classification. No new storage format or preference keys.
- Agent files use the existing per-profile HOME. Generic profile deletion
  removes that HOME; removing an executable preserves accounts and chats.
- Focused removal coverage proves a live payload refuses removal and unrelated
  profile files/shared tools survive successful removal.
- No certification row was fabricated. Antigravity is installable in setup;
  the chat picker remains disabled until exact-version install and authenticated
  smoke evidence is recorded through the existing certification process.

## Verification

Use the pinned Flutter 3.47.1 via `tool/qa/machine_lock.sh`. Focused results and
review screenshots are recorded below after the final local check boundary.

Unrun by request/scope: full suite, full preflight (which invokes repository-wide
analysis/tests), APK build/signing, device/emulator/proot runs, authenticated
model/tool/permission/resume tests, pushes, PRs, GitHub comments, releases.

Completed checks (all test invocations used `--concurrency=1`):

| Check | Result |
| --- | --- |
| `flutter pub get` | Passed, app lockfile unchanged |
| `flutter test test/agent_auth_probe_test.dart test/phone_agent_auth_native_test.dart test/agent_catalog_test.dart` | 77 passed |
| `flutter test --no-pub test/agent_catalog_test.dart test/agent_removal_scripts_test.dart test/phone_agent_host_native_test.dart test/phone_agent_sign_in_native_test.dart test/agent_persona_test.dart test/host_agent_providers_test.dart test/agents_ui_test.dart test/coverage/phone_agent_catalog_coverage_test.dart` | 140 passed |
| New guidance golden generation, followed by normal golden comparison and `test/linux_manager_parse_test.dart` | 44 passed; four screenshots visually inspected |
| `flutter test --no-pub test/phone_agents_controller_test.dart --plain-name 'Antigravity payload'` | 1 passed |
| Widgetbook `flutter test --no-pub test/standard_check_test.dart --name 'standard: agents/check-this-phone/(antigravity-install\|antigravity-google-sign-in\|gemini-switch-guidance)$'` | 3 use-cases passed through EN, AR, large-text and dark passes; full catalog ratchet not run |
| `flutter analyze --no-pub <31 changed Dart files>` | No issues found |
| `git diff --check` | Passed |

The batches overlap on catalog tests; their counts are not a count of unique
tests. Native auth/host scenarios compile and run JVM harnesses, not Android.
The Widgetbook lockfile still describes the prior app version: enforcing it
failed, local dependency resolution succeeded, and its original tracked bytes
were restored. The initial analyzer's only errors were unresolved Widgetbook
imports before that resolution; the final changed-file analysis was clean.

Screenshots (fake data, no live terminal/account):
[Antigravity EN](../../../test/goldens/antigravity_guidance_en.png),
[Antigravity AR](../../../test/goldens/antigravity_guidance_ar.png),
[Gemini EN](../../../test/goldens/gemini_guidance_en.png),
[Gemini AR](../../../test/goldens/gemini_guidance_ar.png).

Status: integration implemented locally; chat enablement BLOCKED on real
signed-in/phone certification. Next evidence must cover the pinned 1.5.0 install,
user-completed sign-in and a real app chat through Paseo on an authorized scratch
Android ARM64 runtime, followed by the existing certification ledger update.
No such result is inferred from docs, artifact availability, synthetic auth
responses, or this host's account-free initialization. Large session-list
responses over 64 KiB conservatively fail the sign-in check rather than bypassing
its output limit. No APK was built or delivered.

Commit-gate boundary: `tool/qa/check_commits.sh 8302de537 HEAD` reports an
inherited violation in #114 commit `074dcda37` (missing `[skip ci]`, body and
Co-Authored-By trailer). It also includes #114's Dart files. That history was
not edited. The issue-116-only range is `HEAD^ HEAD`; its message/staged
format check passed before commit. Its range check passed: one commit and
31 formatted Dart files.
