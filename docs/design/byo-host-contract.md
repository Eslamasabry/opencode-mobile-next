# Adopt an Ubuntu machine: backend and frontend contract

Date: **2026-10-02**. Owner: Eslam. Frontend/coordinator: Claude.
Status: backend slice implemented **default OFF**; no UI, published bundle,
physical-phone/VPS qualification, deployment or enabled product journey.
Supersedes slice 1 of [BYO VPS contract](byo-vps-contract.md); the provider creation
contracts and dated [research](byo-vps-research-2026-09-27.md) remain later work.

Finish line: backend inspects and pins a machine's SSH identity, installs a verified
Ubuntu 24.04 nonroot user host, pairs this phone, returns an authenticated OpenCode1 connection,
resumes after interruption, and removes local access with optional remote revoke.
Non-goals: UI, billing/provider APIs, installing Tailscale, root agent execution,
macOS/Windows, OC2/Codex/Paseo/AI Team/Gas City installation, hosted token broker.
Those runtimes keep their existing contracts/pins; none is claimed supported by
this OpenCode1 bundle. SSH adoption does not enable their gateway capabilities.

## Public composition

Use `lib/state/byo_host_service.dart`, `lib/state/byo_host_controller.dart`, and
values in `lib/domain/byo_host.dart`; UI never generates shell or reads the vault.

```dart
final hosts = ByoHostService.builtin(
  prefs: prefs, bundle: reviewedBundle,
  clearLocalData: (profileId) async {
    // Supply the existing queued-prompt removal confirmation/plan here as needed.
    final result = await connection.deleteProfileAndLocalData(profileId);
    if (!result.complete) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
  },
);
// Production build remains false without --dart-define=OC_BYO_HOST=true.
if (!await hosts.available()) { /* entry unavailable */ }
final saved = await hosts.machines();
final job = hosts.newMachine(); // reserves random stable profileId in memory
final candidate = await job.inspect(ByoHostTarget.parse('alice@host'));
// Owner independently verifies candidate.fingerprint, then enters login once.
await job.adopt(verifiedFingerprint: candidate.fingerprint,
               login: ByoHostLogin(password: password));
final profile = job.connectedProfile(name: 'My machine');
await connection.connect(profile);
// Restart/select: hosts.machine(savedId).resume(), then connectedProfile().
```

`ByoHostService(store:, runner:, bundle:, clearLocalData:, enabled:)` is the
injected composition. The builtin production factory requires `clearLocalData`.
One service owns one shared runner/store and caches one controller per profile.
`machine(profileId)` returns that actor; different actors own independent tunnels.
`release(profileId)` disposes only that actor; service `dispose()` stops its exact
local tunnels. Neither stops any host service or deletes host sessions.
`available()` requires the build gate, injected reviewed bundle and installed
built-in Ubuntu. Individual operation failures still apply (e.g. missing ssh).
`machines()` lists immutable sorted metadata without reading secrets. Gate false
makes entry unavailable; no automatic rollout switch, remote flag or telemetry.

`connectedProfile()` returns a **runtime-only** `ServerProfile` marked
`transientTransport: true`: localhost origin, per-device Basic username/token,
OpenCode1 version. `ProfileStore.upsert()` refuses it before writing anything.
Never persist its port, copy it into the ordinary Edit server flow, or call
`upsert()` to rename it. Saved machines are the BYO records, not placeholder
server URLs. A fresh `resume()` is mandatory before selecting a saved machine.
Existing generic auto-connect/startup selection must route BYO IDs through this
service first; UI integration is outstanding, not automatically wired by this
backend. Do not send the device credential to a public or stale localhost URL.

The existing server QR/paste format is not phone enrollment for this supervisor.
Do not export a transient localhost profile or this phone's token/key through the
generic share-connection action. A second phone adopts the same host separately
and receives a new device tuple. Later QR enrollment would carry only a private
reachable host identity and one-time enrollment proof, never another phone's
long-lived credential; it is not implemented in this slice.

Existing `ConnectionController.connect(profile)` uses the normal `ServerGateway`
and `ServerOperationsGateway`. Screens continue to gate agent features on
`ServerCapabilities`; infrastructure eligibility uses `hosts.available()`.
The host proxies OC1 REST, SSE and PTY WebSocket traffic, not a new chat protocol.
A failed/lost phone connection is not permission to replay queued mutations:
refetch/reconcile sessions using the existing gateway behavior.

## SSH runner and typed values

`ByoHostSshRunner` methods:

| Method | Contract |
|---|---|
| `available()` | Runtime eligibility only; no mutation |
| `inspectKey(target)` | Ed25519 public key + computed SHA256 fingerprint, untrusted candidate |
| `generateIdentity(profileId)` | Distinct phone SSH key; private bytes never enter snapshots |
| `install(profileId, target, hostKey, login, identity, deviceToken, bundle)` | Strict pinned admin SSH; digest verified before executing bundle; idempotent pairing; safe descriptor |
| `forward(profileId, target, hostKey, identity, remotePort)` | Owned SSH process, `-N -L 127.0.0.1:local:127.0.0.1:remote`; forwarding-only device key |
| `describe(tunnel, deviceId, deviceToken)` | Authenticated bounded loopback descriptor; no redirects |
| `revoke(tunnel, deviceId, deviceToken)` | Own-device receipt only; false/uncertain keeps local connection |
| `cleanup(profileId)` | Stops only exact abandoned profile tunnel services and deletes private temporary key/input/result directories before vault erasure |
| `dispose()` | Owner closes local transports only |

Target is explicit user/host/port (22 default), no ssh aliases/config, command
options, proxy commands or URI credential fields. Imported private key/password/
passphrase is one-operation input (`ByoHostLogin`), consumed on success/failure.
Password and passphrase together are refused. Prompt helper answers only the
expected password/passphrase prompt; keyboard-interactive/2FA is unsupported.
An entered password may serve the two bootstrap SSH connections in that single
operation; it is never retained for a later operation. Do not promise Dart string
zeroization. Out-of-band host fingerprint verification precedes any login.

Bundle manifest: `ByoHostBundle(version:, openCodeVersion:, artifacts:)`, keys
`x64`/`arm64`; each `ByoHostArtifact(url:, sha256:)` has an HTTPS URL and reviewed
64-character SHA256 of the **whole archive**. No runtime mutable checksum-sidecar
trust, installer `curl | sh`, provider token or auth in URL. Caller-reviewed
OpenCode1 **1.18.32**, host bundle **1.0.0**. No production URL/digest is supplied
in this branch: absent manifest is `bundleUnavailable`. Packaging instructions:
[scripts/byo-host/README.md](../../scripts/byo-host/README.md).

## Journal and transitions

This remote job borrows phone setup v2's check-before-mutate, durable recovery and
safe snapshot semantics ([phone setup v2 design](phone-setup-v2-2026-09-24.md)),
but is not submitted as shell text to `setup_engine`/`setup_contract` components:
those bridges can persist raw output. Remote secrets never enter setup logs.
The new SSH runner uses private file input/output and its own schema-1 pairing
journal. Phone runtime availability is checked through BuiltinLinux; installing
or repairing that runtime remains the existing phone setup flow. Remote setup is
bounded to 15 minutes; app/process loss leaves uncertainty requiring journal
reconciliation, not a promise of uninterrupted Android background execution.

`job.snapshot` and `job.changes` expose only `ByoHostSnapshot(phase, record,
hostKey, failure)`; subscribe once and read initial snapshot. Do not display raw
exceptions, remote stdout/stderr, key files, bearer token or provider data.

| Phase | User intent / next action |
|---|---|
| `idle` | Enter user and machine, optionally port under Details |
| `checking` | Checking this machine; bounded activity indicator |
| `needsTrust` | Verify fingerprint independently; confirm/cancel |
| `installing` | Setting up this machine; no fabricated percent/time estimate |
| `connecting` | Opening private connection and verifying installed host |
| `ready` | Open sessions; connection may also carry a safe revocation failure |
| `disconnected` | Saved machine, fresh tunnel required |
| `failed` | Safe recovery action by failure code; saved journal remains |
| `removing` | Confirmed revoke or local cleanup underway; retry if storage refused |
| `removed` | Local connection and vault cleared; host keeps sessions |

`inspect(target)` refuses reuse of an existing profile ID. `adopt()` requires the
exact fingerprint from that preview. Before remote mutation it saves one secure
identity/token envelope then the `installing` record. `resume(login: optional)`
reads the same tuple. If remote descriptor was never persisted, fresh admin login
is required and pairing is retried with **the same** device ID/token/key. An
uncertain remote result never rotates the tuple, creates another host, reinstalls
a different version or claims ready. With a persisted descriptor, reconnect uses
only the restricted device SSH key; host-key and host UUID/version/port changes
fail closed. Pins have no automatic replace/reset operation.

Persistent record schema 1: `profileId`, target, pinned public host key/fingerprint,
phase, hostId, bundleVersion, openCodeVersion, remotePort, revoked receipt flag.
Key `oc.byoHost.<profileId>` in preferences; **no ephemeral localPort**.
Vault `oc.byoHostSecrets.<profileId>` contains the generated private/public SSH
key and random 32-byte device token. No provider or initial login credentials.
Removal closes the owned tunnel and cleans own-profile abandoned private files
before vault/metadata erasure; cleanup refusal is unfinished removal.
Store mutations are ordered; corrupt/cross-profile records fail closed; deletion
confirms vault removal before dropping metadata and is idempotent/retryable.
Global sign-in reset must dispose the BYO service before invoking ProfileStore
reset, so no cached lease retains a credential after the vault is erased. This
composition hook belongs to the outstanding frontend/lifecycle integration.
Generic `ProfileStore.remove()` explicitly deletes/verifies this vault slot,
then its scoped preference sweep removes the record. Reset saved sign-ins also
owns this vault slot. Same-name `oc.*` preference sweeps alone are insufficient.

## Removal and integration ordering

Show exactly two actions:

- **Remove from this phone only**: explain that the machine keeps running and
  its phone authorization remains installed until the owner revokes it there.
- **Revoke access and remove**: explain that this phone's key and host access
  will be revoked; other phones and host sessions remain. Requires reachable host.

Call `job.remove(revokeAccess: choice)`. A failed/uncertain revoke returns
`revocationFailed`, retaining record/secrets and any current tunnel; show failure
inline and keep the saved machine. A confirmed receipt is persisted before local
erasure, so failed local deletion retries without repeating successful revoke.
The required production `clearLocalData(profileId)` callback invokes existing
`ConnectionController.deleteProfileAndLocalData(profileId)` and throws unless
its result is `complete`, with the existing queued-prompt confirmation/plan.
The job invokes it **after confirmed revoke (when requested), closing its tunnel
and erasing abandoned temporary files, but before erasing the BYO vault/journal**.
If that cascade fails after optimistically sweeping the BYO preference, the job
restores a nonsecret cleanup journal (including confirmed-revoked receipt) so
restart can discover/retry cleanup. It never reports removed on callback refusal.
Only after successful `remove()` call `hosts.release(profileId)`; retire the
selected gateway through the existing deletion/disconnect lifecycle. No frontend
invented in-memory-only cleanup item is required. Do not bypass queue-removal
confirmation. Backend exposes this composition hook; global navigation wiring
remains Claude's integration work.

If revocation happened but its response/journal was lost, the existing SSH tunnel
can query the idempotent revoke receipt using that credential. After phone/tunnel
loss the revoked SSH key cannot reconnect: retain the local record, verify revoke
via admin SSH on the host, then choose local-only cleanup. Never silently erase
on an authentication failure. Removing access is **not** host uninstall or VPS
teardown; provider resource deletion remains a separate later reviewed action.

## Kit pages and copy intent (Claude)

All screen parts come from `lib/ui/kit`; add a missing part to kit. Screens arrange
parts only. English/Arabic copy belongs in l10n, not these backend message strings.

| Page | Fields/actions/states |
|---|---|
| Machines | Saved records; Add machine when available; disconnected status; select resumes |
| Add machine | `user@host`, Details port; optional existing machine from saved records; Check |
| Verify machine | Public fingerprint, explanation how to compare on the machine; explicit confirmation, cancel |
| Sign in once | Password OR imported SSH private key with optional passphrase; no save-login toggle |
| Setup | Snapshot activity, safe recovery; leaving page does not destroy a saved journal |
| Connected machine | Open sessions, reconnect, machine Details (pin/versions), Remove actions |
| Remove | Two explicit choices; pending revoke, kept-on-failure copy, local cleanup retry |

Plain copy: “Add machine”, “Verify this machine”, “Sign in once”, “Setting up your
machine”, “Connect again”, “Access could not be revoked. Your connection is kept.”
Technical text under Details: Linux prerequisites, SSH settings, fingerprint
command, systemd/linger, pinned versions, private temporary-file disclosure.
No shell console/raw log pane. Every URL from a form/remote value follows
`openExternalLink`; do not launch arbitrary URLs or invoke assistant-generated
commands. AI setup assistant remains advisory and receives no login/token/key.

| Failure code | Recovery intent |
|---|---|
| `disabled`, `unavailable`, `bundleUnavailable` | Explain unavailable prerequisite; no mutation/retry loop |
| `invalidTarget`, `needsTrust` | Correct input / verify fingerprint independently |
| `hostKeyChanged`, `identityChanged` | Block; owner verifies host/rebuild; no one-tap accept changed identity |
| `unsupportedHost` | Ubuntu 24.04 and nonroot account required; no sudo conversion |
| `authentication` | Fresh one-shot login for bootstrap, or admin repair revoked device |
| `checksum` | Stop; reviewed artifact/manifest correction required |
| `lingerRequired`, `sshPolicyRequired` | Owner performs documented host preparation; no automatic sudo |
| `uncertain` | Keep journal; re-enter admin login and retry same pairing |
| `transport`, `protocol` | Reconnect/check reachable machine; show no remote response text |
| `storage` | Keep retry state; vault/storage repair before claiming removal |
| `busy` | Existing operation finishes; do not run another actor for same ID |
| `revocationFailed` | Keep saved connection; retry reachable host or deliberate local-only choice |

## Required owner qualification before enablement

No provider account/live VPS/phone was changed by this task. Use a throwaway
Ubuntu 24.04 nonroot account and dummy secrets; published manifest plus kit UI
integration are prerequisites. Owner-run proof must include:

1. Actual ARM64 built-in SSH/ssh-keygen/askpass and private rootfs file mapping;
   password, encrypted-key passphrase, cancellation, timeout, changed-host denial.
2. Inspect Logcat, diagnostics, service/setup logs, argv/env and leftovers using
   dummy sentinels. Only private files may contain them; reboot/kill then reconnect
   triggers own-profile orphan cleanup. Same-UID phone agents are not isolated
   from these temporary plaintext files; decide acceptance or build native signer.
3. Independently verify SSH fingerprint; install checksummed owner-built archive;
   prove both server listeners loopback-only, no public agent port. Prefer SSH
   over the user's existing tailnet; SSH forwarding is the owner-authorized
   encrypted fallback. This slice does not join a tailnet or alter SSH/firewalls.
4. Confirm live sshd denies reverse forwarding, Unix socket forwarding and tunnel
   devices, while exact allowed local forward works. Config-on-disk preflight is
   insufficient if the running daemon was not reloaded. Keep rescue SSH access.
5. Two phones/device tuples, separate host credentials; kill phone/app, reopen same
   session; revoke one phone closes its SSE/WS sockets/key and leaves other phone
   and host work running. Test response loss and deliberate local-only removal.
6. Reboot host, verify user service/linger persists; collision, corrupt journal,
   checksum failure, retry interrupted install, storage refusal, full local sweep.
7. Qualify localhost port allocation race: repeated live-service checks reduce
   risk but no native fd handoff proves socket ownership atomically. Keep flag OFF
   unless accepted/proven with dummy credentials or replace with pinned TLS/FD
   handoff. Android can stop the phone tunnel; host sessions must still survive.

A phone device credential authorizes OpenCode tools as the host login user,
including shell/file operations. It is not a hostile-user sandbox: an authorized
agent can change that account’s files/keys. Revoking transport cannot undo those
changes or stop already launched work. Use a dedicated nonroot account for each
trust domain and do not pair untrusted phones to the same account.

Updates remain explicit later work: build/review new archive, host backup, pinned
manifest/version migration, owner-approved host restart and rollback. Adoption
never upgrades a running host or restarts existing work to pair another phone.
