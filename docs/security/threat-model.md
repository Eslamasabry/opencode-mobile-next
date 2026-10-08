# Threat model: a phone that can approve shell commands

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

Status: written 2026-10-02 against the `feat/phone-setup-v2` line. Every claim
names the file that implements it, so a reviewer can check it. Anything marked
**Gap** is not mitigated today. This is a design review of the repository, not
a penetration test; nothing here was verified on a device.

Report vulnerabilities privately, as described in [SECURITY.md](../../SECURITY.md).

## What is being protected

An OpenCode server runs commands on its host as the user who started it. The
phone can authorize those commands (tool permissions), answer agent questions,
and do so from a notification without opening the app. So the assets are:

| Asset | Where it lives |
|---|---|
| Server password (Basic auth) per profile | Keystore through `flutter_secure_storage`: `lib/state/profiles.dart` (`ProfileStore`, keys `_passwordKey`, `_codexTokenKey`, `teamEngineAuthKey` plus profile id) |
| Profile list, queued prompts, drafts, preferences | `SharedPreferences`, per-profile keys named `oc.<what>.<profileId>` |
| The ability to say "yes" to a command | Permission cards, sheets and notification actions: `lib/state/connection.dart` (`answerPermission`, `_handleCodingAlertAction`) |
| Provider API keys | Returned by the server's `/config/providers`; never stored by the app, but they pass through it |
| The phone's own files (All files access) | `android/app/src/main/AndroidManifest.xml` (`MANAGE_EXTERNAL_STORAGE`) |
| Code execution on the phone (AI Team engine) | `lib/domain/phone_project_engine.dart`, `android/.../PhoneEngineNative.kt` |

Out of scope: the OpenCode server's own vulnerabilities, model providers,
Termux/proot upstream, Shorebird (see SECURITY.md "Out of scope here").

## Attacker scenarios

### 1. Stolen phone, unlocked (or shoulder-surfed, or handed over)

The attacker holds an unlocked phone with the app installed.

- **Can do:** open the app, read transcripts, approve or deny pending
  requests, send prompts, and so run commands on every saved server.
- **Mitigations:** none inside the app. There is no app lock or biometric
  gate (no `local_auth` dependency in `pubspec.yaml`). Passwords are in the
  Keystore, which does not help once the device is unlocked and the app can
  read them. Backup cannot be used to copy data off the device:
  `android:allowBackup="false"` with `backup_rules.xml` and
  `data_extraction_rules.xml` excluding every domain.
- **Gap:** no app lock; no "lock the app after N minutes" option. Removing a
  stolen device's access needs the server password changed on the server.
- **Gap:** per-session auto-approval (`lib/state/session_auto_approval.dart`)
  makes an unlocked phone more valuable: it replies "once" to every request
  in that session. It never replies "always", and it is off by default
  (`AutoApprovalMode.ask`), and `automationPolicy.allowsAutoApproval` gates
  it (`connection.dart`, `_autoApprove`).

### 2. Stolen phone, locked

- **Can do:** see whatever lock-screen notifications show, and try the
  notification actions.
- **Mitigations:**
  - Alert channels and notifications use `VISIBILITY_PRIVATE`
    (`BackgroundConnectionService.kt`, channel creation near lines 531-547
    and `setVisibility` near lines 210 and 408), so content is hidden on a
    secure lock screen unless the owner changed the system setting.
  - The **Allow once** action sets `setAuthenticationRequired(true)` on
    Android 12+ (`BackgroundConnectionService.kt`, `codingAlertActions`), so a
    lock-screen tap must authenticate first.
  - Notification actions go through a non-exported receiver
    (`CodingActionReceiver`, `android:exported="false"`) and immutable
    `PendingIntent`s, except the reply field, which must be mutable for
    `RemoteInput`.
- **Gap:** **Deny** and the quick **Reply** do not require authentication.
  Deny is harmless. On OpenCode 2 a permission Reply is a reject with a message
  the model reads (steering by rejection); an attacker with a locked phone in
  hand could inject instructions. The question quick-reply likewise answers
  without unlock. Treat the text as untrusted input to the model.
- **Gap:** the user can raise lock-screen visibility per channel in system
  settings; the app cannot prevent it.

### 3. Malicious or compromised OpenCode server

The server is chosen by the user, but a compromised host, a typo'd address, or
a hostile server on a shared network can answer with anything.

- **Can do:** send misleading permission text, huge payloads, hostile links in
  tool output, `/config/providers` data containing API keys, and replay or
  forge event frames.
- **Mitigations:**
  - Permission identity is bound to the exact request: replies carry the
    request id, are matched against `permissions[...]`, and a notification for
    one request can never resolve another (`connection.dart`
    `_handleCodingAlertAction`, `PendingRequestIdentity`). A reuse of the id
    with different contents is a different request (`_permissionContents`).
  - A server-supplied URL is never opened directly: every URL the app did not
    author goes through `openExternalLink`
    (`lib/ui/widgets/external_link.dart`), which applies a policy, then
    confirmation. No `launchUrl` outside that file (guarded by the kit ratchet
    in `test/kit_ratchet_baseline.json`, key `G2`, `launchUrl(`).
  - Oversized or truncated SSE frames are dropped without corrupting the next
    ones (`lib/api/sse.dart`, `lib/api2/sse2.dart`; tests in
    `test/api2_sse_test.dart`, `test/connection_sse_*_test.dart`).
  - Credentials the server returns are masked before display, copy or logs
    (`lib/ui/kit/kit_redact.dart`, see scenario 8).
  - Redirects are not followed on clients that carry credentials
    (`followRedirects: false` in `lib/paseo/transport.dart`,
    `lib/state/termux_running_server.dart`,
    `lib/orchestration/adapters/inapp/phone_engine_gateway.dart`;
    `isCleartextRemoteBase` in `lib/domain/loopback_host.dart` documents why),
    so the Basic header does not follow a redirect to another host.
- **Gap:** the user approves a *description* of a command written by the
  server's agent. A compromised server can describe something other than what
  it will run; nothing on the phone can verify that.
- **Gap:** there is no server identity pinning. Anyone who can present a valid
  certificate for the host name (or any server at a confirmed private HTTP
  address) is the server.

### 4. LAN or Wi-Fi attacker (MITM), including private-network HTTP

- **Mitigations:**
  - HTTPS is required for everything except two cases
    (`validateServerProfileUrl` in `lib/state/profiles.dart`):
    loopback (`isLoopbackHost`: `localhost`, `127.0.0.1`, `::1`) and, new, a
    **private network address** (`isPrivateNetworkHost` in
    `lib/domain/loopback_host.dart`: 10/8, 172.16/12, 192.168/16, 169.254/16,
    IPv6 fc00::/7 and fe80::/10, and `*.local`).
  - Plain HTTP to a private address is refused until the person confirms it
    for that exact origin: `serverUrlNeedsCleartextConfirmation`,
    `cleartextOriginOf`, stored under `oc.cleartextOk.<profileId>`
    (`ProfileStore.cleartextConfirmedKeyPrefix`). Changing the address asks
    again; an unconfirmed profile will not connect
    (`cleartextUnconfirmedMessage`).
  - Credentials are never sent over HTTP to a public address: with a username
    or password set, `validateServerProfileUrl` returns "HTTPS is required
    outside this device".
  - Credentials in the URL, query strings, fragments and paths are rejected.
  - A VPN is not an exception: LAN and CGNAT (`100.64.0.0/10`) addresses
    still need HTTPS or the confirmation
    ([connectivity-private-networks.md](../connectivity-private-networks.md)).
- **Gap:** after the confirmation, the password (Basic auth) and the whole
  conversation, including permission requests, cross the LAN in clear text. An
  attacker on that network can read them, and can alter responses, which
  includes forging a permission card. The confirmation is a consent, not a
  protection. Prefer Tailscale Serve or a reverse proxy with a trusted
  certificate.
- **Gap:** Android's platform layer is open: `usesCleartextTraffic="false"` is
  set, but `network_security_config.xml` has
  `<base-config cleartextTrafficPermitted="true" />` because Android cannot
  express address ranges. The Dart validator is the only gate. A bug in
  `isPrivateNetworkHost` or a code path that builds a client without the
  validator would permit cleartext to anywhere. The tests in
  `test/private_network_cleartext_test.dart` and
  `test/setup_ui_messages_test.dart` are the safeguard.
- **Gap:** mDNS names (`*.local`) are accepted as "private" but are trivially
  spoofable on the same network.

### 5. Notification and lock-screen approvals (a tap that was not meant)

Covered partly in scenario 2. Additional cases:

- **Stale tap:** a notification for a request that was already answered or
  withdrawn must not resolve a different request. `_handleCodingAlertAction`
  re-reads `permissions[action.requestID]`, checks `sessionID`, and otherwise
  refreshes the alert instead of acting. Tests: `test/background_action_test.dart`
  (stale request id), `test/permission_edge_cases_test.dart` (notification and
  in-app race: one reply; timed-out request dismisses without error).
- **Double decision:** a notification, a sheet and an inline card share one
  pending slot (`_withPendingReply`), so two taps send one reply.
- **Wrong profile:** an action for another profile returns unhandled
  (`action.profileID != profile?.id`).
- **Gap:** `Allow once` on the lock screen is authenticated, but "Always allow"
  is only offered in-app, behind a scope statement (`test/chat_permission_test.dart`,
  "Always allow states its scope"). Keep it that way; do not add an
  always-allow notification action.
- **Gap:** the Quick Settings tile (`AttentionTileService`, exported behind
  `BIND_QUICK_SETTINGS_TILE`) and the home widget (`SessionsWidgetProvider`,
  exported) display counts and session titles from a cache the app writes.
  Session titles on a locked home screen are a privacy leak by design of
  widgets.

### 6. All files access (`MANAGE_EXTERNAL_STORAGE`)

- **What it is for:** opening a project that lives in the phone's shared
  storage (`/sdcard`, SD card) so the in-app server can read and write it.
  Detection: `lib/domain/shared_storage_path.dart` (`isSharedStoragePath`, with
  `..` resolved). It is requested only when such a project is opened
  (`lib/platform/storage_access.dart`, `StorageAccess.kt`).
- **Can do (if abused or if the in-app server is compromised):** read and
  write every file in shared storage, including other apps' non-media files.
- **Mitigations:** requested lazily, not at install; with AI Team on, only the
  shared-storage folders opened as projects are bound and allowed (commits
  `1ddddbad`, `dd6b197e`; `BuiltinProjectStorage.kt`); app-private data is
  excluded from backup.
- **Gap:** once granted, the permission is app-wide and stays until revoked in
  system settings. The app does not revoke it when the last shared-storage
  project is removed.

### 7. AI Team sandbox tiers

The phone-hosted AI Team engine runs agents that execute code on the phone.

- **Tiers:** `PhoneEngineHealth.boundaryTier` is `none`, `landlock` or `proot`
  (`lib/domain/phone_project_engine.dart`). The engine reports it signed;
  the app rejects an unknown tier, or a top-level tier that disagrees with the
  capability copy (`fromJson`).
- **Gate:** execution requires
  `canExecute => execution && boundary && oc1Verified && !oc2`. If the engine
  does not attest a boundary, the app does not offer to run
  (`team_execution_gate.dart`; the user sees the tier through
  `phoneTeamProtectionText`).
- **Mitigations:** the tier is shown to the person; attestation is checked
  against the profile id (`profileMismatch`); unverified schema versions are
  refused (`schemaUnsupported`).
- **Gap:** `none` means "unreported" for older engines, and their existing
  execution flags remain valid (comment on `boundaryTier`). An older engine
  therefore runs with no attested confinement.
- **Gap:** the tiers are not equal. `proot` is a path-translation sandbox, not
  a security boundary against a determined local attacker; `landlock` is a
  kernel boundary but depends on the phone's kernel. The app shows a label but
  does not rank the tiers.

### 8. Credentials, Keystore and diagnostics redaction

- **Storage:** server passwords, Codex tokens and engine auth are in
  `flutter_secure_storage` (Android Keystore-backed) keyed by profile id. Other
  data is in `SharedPreferences` under `oc.<what>.<profileId>` so the deletion
  sweep (`ProfileStore.profileScopedPreferenceKeys`,
  `ConnectionController.deleteProfileAndLocalData`) removes it with the
  profile.
- **Redaction:** `KitRedact.text` masks provider keys, bearer tokens,
  credential headers, `password=`/`token=` pairs, URL user-info, PEM blocks
  and JWTs (`lib/ui/kit/kit_redact.dart`). Loaded passwords and provider keys
  are registered (`registerKnownSecret`) so exact values are masked first.
  It is applied to App diagnostics (`lib/diagnostics/app_diagnostics.dart`,
  marked `redacted-process-memory-only`), the report-a-problem capture
  (`report_problem.dart`: every write redacts), failed-job reports
  (`failed_job_report.dart`: redacted before bounding) and the external-link
  confirmation sheet.
- **Gap:** redaction is pattern-based and best effort. A secret with an
  unusual shape that was never registered can leak into a transcript the user
  pastes. The bug-report templates say so and tell reporters to review before
  pasting.
- **Gap:** the keyring on desktop depends on a running secret service
  (libsecret); without one, passwords silently fail to persist rather than
  falling back to plain storage (`docs/desktop.md`, "Runtime requirements").
  That fails safe, but surprises users.
- **Gap:** the Keystore protects data at rest on a locked device, not from the
  app's own process; a rooted device or a debuggable build exposes it.

## Summary of open gaps, in priority order

1. No app lock or per-action authentication for approving on an unlocked
   phone (scenario 1).
2. Lock-screen quick **Reply** on a permission carries text to the model
   without unlock (scenario 2).
3. Cleartext private-network HTTP exposes the password and approvals to the
   LAN after the confirmation; the platform layer does not backstop the Dart
   gate (scenario 4).
4. Older AI Team engines run with `boundaryTier: none` (scenario 7).
5. All files access is never revoked automatically (scenario 6).
6. No server identity pinning (scenario 3).

## Keeping this file honest

Re-read it when changing any of: `lib/state/profiles.dart` URL rules,
`lib/domain/loopback_host.dart`, notification actions in
`BackgroundConnectionService.kt`, the manifest's permissions or exported
components, `KitRedact`, or the phone engine's attestation. The file
references move; the table of mitigations is only useful while the references
resolve.
