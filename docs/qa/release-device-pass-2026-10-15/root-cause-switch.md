# Runtime switch and permission blockers

Worktree: `oc_app-codex-relswitch`, branch `codex/release-switch-fixes`.
Investigation started at `c465947c8` (APK 2323 device-pass record).

## P0-2: the conversation is retained in OC1, but was never imported into OC2

Read [the device pass](README.md), `rel-13-chat-reply.png`,
`rel-24-conversations-oc2.png`, and `rel-62-permission-in-app.png` first.
All device commands targeted **`adb -s emulator-5554`**. The release package
was `io.github.eslamasabry.opencode_mobile`. No replacement APK was built or
installed, and the physical phone was not accessed.

The emulator permits `su 0`; `run-as` was not used. Read-only copies of each
database and its WAL/SHM were inspected in a private temporary directory.
These are diagnostic snapshots of a running server, not archival backups.

| Observation | Result |
|---|---|
| OC1 path inside Ubuntu | `/root/.local/share/opencode/opencode.db` exists, with WAL/SHM |
| OC1 session | `ses_ec18d939cffet9YurXoZZBwPOC`, title `Reply PONG only` |
| OC1 content | 1 session, 2 messages; 2 stored parts contain `PONG` |
| OC2 path inside Ubuntu | `/root/.oc-opencode2/data/opencode/opencode.db` exists, with WAL/SHM |
| OC2 sessions | 4 rows in `session_v2`; none has the missing OC1 session ID |
| SQLite integrity | `PRAGMA quick_check` returned `ok` for both snapshots |
| Live OC2 version | `/api/info` returned `2.0.29` |
| Live migration status | `/api/experimental/migration/v1` returned `{"status":"completed"}` despite the absent chat |

The original `openCode1HistoryCopyScript` copies OC1's database only if OC2's
database does not exist. Setup now starts OC2 before the user can choose Older
OpenCode, so the normal round trip fails that guard. Both launcher wrappers also
swallowed copy errors. The migration status describes OC2's own input; it does
not establish that the separate OC1 history was imported.

No supported safe merge of two populated databases was established. The current
OpenAPI exposes the experimental migration route as a GET status operation,
not an import command. Overwriting OC2 would destroy its conversations, and
copying SQLite tables across these different schemas is not a qualified import.
OC2 also has experimental session export/import surfaces. Earlier repository
evidence describes the import as a V2 `{info,messages}` format, not OC1 JSON;
conversion plus conflict-safe import was not qualified here. This is a refusal
because safe merging is unproven, not a claim that migration is impossible.

The fix therefore uses the authorized refusal path:

- Before an explicit OC1-to-OC2 move, check both locations without writing.
  If both databases exist, or the checked locations are unsafe, refuse before
  setup can stop OC1 or change the selected runtime. Termux performs the same
  check before its switch journal and stop action.
- The in-app action uses the existing translated blocking alert:
  “Your earlier conversations could not be brought across. They are still safe
  in OpenCode 1.” It does not show arbitrary shell output. Termux's reason says
  OC2 already has a separate history and to stay on Older OpenCode; neither
  history was changed.
- Preserve the fresh-target copy path, but propagate copy and placement failures
  instead of starting with absent history. Preserve OC1's files in all cases.
- Leave ordinary starts of an already selected OC2 runtime available. The
  explicit move guard is deliberately conservative: it also refuses a repeated
  move where an earlier import might already have succeeded.

The new read-only admission script was run against the actual emulator paths
(translated to their Android rootfs paths): **exit 74**, with
`[oc-history-switch-blocked]` and the expected two-history explanation. The
application itself remains APK 2323, so this is script proof, not an installed
UI-fix qualification.

## P1-2: the app's approval mode did not install an OC2 policy

The approval chip controls the controller's handling of incoming requests:
Asks first retains the request for the user; Auto-approve answers once. Before
this change, the OC2 gateway did not install session permission rules before
dispatch. OC2's normal build-agent defaults could therefore execute shell and
file edits without ever producing a request for the controller to handle.

OC2's shell action is **`shell`**, not `bash`; its `write` tool checks **`edit`**.
The stable API changes session policy with
`PATCH /api/session/{id} {permissions:[{action,resource,effect}]}`.
The existing stable dialect already translates permission replies to
`{decision:"once"}`; this was checked and is **not** an additional app defect.

The gateway now prepares and verifies permissions before prompts (including
queued delivery), commands, and direct shell dispatch. It reads the effective
agent's rules and the original session rules in their server precedence order,
changes allow effects to ask, retains denials and their ordered exceptions,
then PATCHes and reads the result back. A failed or malformed policy read/write
prevents dispatch. A blanket wildcard ask alone would mask agent denials, so it
is not sufficient.
For a session without an explicit agent, OC2's agent list identifies the
effective default in its first entry; the live probe also covers a configured
restricted default. Read tools now ask too. Older OC2 versions that lack the
required policy contract fail closed instead of sending without established
permissions; only 2.0.29 was exercised live.
An agent change observed during PATCH/readback refuses the send. OC2 exposes no
atomic policy-and-prompt operation here, so another client changing policy after
the final read is outside that verification boundary.

The session metadata key `oc.mobile.permissionRequests` records the original
and last-applied rule arrays. This lets subsequent sends rebuild the policy
when the selected agent changes, without promoting an earlier agent's rules
into permanent session overrides. Unrelated metadata is retained; external
rule changes are treated as new server truth. This introduces no local
per-profile storage, and the metadata follows the server session's lifecycle.

Auto-approve keeps the existing app controller's automatic **once** reply,
rather than persisting a server-wide allow rule. A previously granted “always”
approval remains effective: OC2 can satisfy ask rules from those saved user
grants. This fix does not revoke such consent.

Live proof uses [the reproducible probe](../../../tool/qa/relswitch_permission_probe.py)
and [sanitized results](permission-contract.json). It runs an isolated OC2
2.0.29 server against a deterministic localhost model provider, using synthetic
data and no owner model credential. The synthesized app policy was exercised:

- Default shell execution reproduces the missing request.
- Shell and project-file writes wait for `shell` and `edit` requests; their
  files are absent before a once reply and present after it.
- A write outside the scratch project waits for `external_directory` and
  `edit` requests. This uses a scratch path, not the host's `/etc`.
- Automatic once replies complete the shell operation without user input.
  This exercises the wire behavior of Auto-approve; it is not a run of the
  Flutter chip/controller on a newly installed APK.
- Agent denials survive policy synthesis, and changing agent recomputes from
  the original session rules. Permission/metadata writes are read back.

The probe stops its own server by exact PID and removes its temporary data.
The emulator's adb forward and private database snapshots were also removed.

Accessibility and privacy: the refusal uses the existing localized Kit blocking
alert and its focus/semantics behavior. No new screen or interaction pattern is
introduced. Shell diagnostics are not interpolated into that alert, and neither
provider credentials nor the device databases are included in these artifacts.

## Verification and handoff

No Flutter command was run. No Kotlin or Rust implementation changed, so no
native test/build or engine `.so` restaging was necessary. No package files in
`/home/owner` were changed. Nothing was pushed, signed, deployed, or released.

Migration checks completed without Flutter:

- POSIX shell syntax for both history snippets, and Bash syntax for the full
  Termux manager.
- Scratch histories: successful DB/WAL/SHM copy; conflicting-history refusal
  with byte preservation; existing OC2 restart unchanged; absent source;
  invalid destination; orphan WAL; injected WAL placement failure; symlink
  refusal. Both original databases remain untouched on refused moves.
- Termux manager and dependent script hashes regenerated through standalone
  Dart rendering, without invoking Flutter.
- Dart formatting of changed files and `git diff --check`.

Pending user-run Dart/Flutter checks (use the repository's pinned SDK and
machine lock; these results are **not claimed as passed**):

```sh
flutter test --concurrency=1 test/opencode_history_copy_test.dart
flutter test --concurrency=1 test/phone_server_card_test.dart
flutter test --concurrency=1 test/builtin_linux_test.dart
flutter test --concurrency=1 test/termux_scripts_test.dart
flutter test --concurrency=1 test/termux_script_hashes_test.dart
flutter test --concurrency=1 test/api2_session_permission_policy_test.dart
flutter test --concurrency=1 test/api2_permission_dispatch_test.dart
flutter test --concurrency=1 test/api2_interaction_gateway_test.dart
flutter test --concurrency=1 test/api2_phone_chat_dispatch_test.dart
flutter test --concurrency=1 test/session_auto_approval_controller_test.dart
flutter test --concurrency=1 test/api2_dialect_test.dart
```

Then run the integration analyzer and serial suite on the final candidate.
An installed-build pass must still demonstrate the blocking alert and the
permission chip flows. Native/owner-phone/release qualification is not implied
by the script or scratch-server evidence.
