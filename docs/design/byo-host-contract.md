# Adopt an Ubuntu machine: backend and frontend contract

Date: **2026-10-02**, revised for owner decisions **1A / 2B / 3A / 4A**.
Owner: the maintainer. Frontend/coordinator: Claude.
Status: backend slice implemented **default OFF**; no UI, published bundle,
physical-phone/VPS qualification, deployment or enabled product journey.
Supersedes slice 1 of [BYO VPS contract](byo-vps-contract.md); the provider creation
contracts and dated [research](byo-vps-research-2026-09-27.md) remain later work.

Finish line: backend inspects and pins a machine's SSH identity, installs a verified
Ubuntu 24.04 nonroot user host already on the user’s Tailscale, pairs this phone
with a nonexportable Android Keystore identity, returns an authenticated OpenCode1 connection,
resumes after interruption, and removes local access with optional remote revoke.
Non-goals: UI, billing/provider APIs, installing Tailscale, root agent execution,
macOS/Windows, OC2/Codex/Paseo/AI Team/Gas City installation, hosted token broker.
Those runtimes keep their existing contracts/pins; none is claimed supported by
this OpenCode1 bundle. SSH adoption does not enable their gateway capabilities.

## Public composition

Use `lib/state/byo_host_service.dart`, `lib/state/byo_host_controller.dart`, and
values in `lib/domain/byo_host.dart`; UI never generates shell or reads the vault.

```dart
final hosts = await ByoHostService.builtin(
  prefs: prefs,
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
final candidate = await job.inspect(ByoHostTarget.parse('alice@100.80.1.2'));
// Owner independently verifies candidate.fingerprint, then enters login once.
await job.adopt(verifiedFingerprint: candidate.fingerprint,
               login: ByoHostLogin(password: password));
final profile = job.connectedProfile(name: 'My machine');
await connection.connect(profile);
// Restart/select: hosts.machine(savedId).resume(), then connectedProfile().
```

`ByoHostService(store:, runner:, bundle:, clearLocalData:, enabled:)` is the
injected composition. The async builtin production factory requires `clearLocalData` and selects compiled pins using PackageInfo version + build number. No caller-supplied manifest can override production pins.
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
| `generateIdentity(profileId)` | Distinct Android Keystore P-256 key; returns alias + public key only; no private key export |
| `install(profileId, target, hostKey, login, identity, deviceToken, bundle)` | Strict pinned admin SSH; digest verified before executing bundle; idempotent pairing; safe descriptor |
| `forward(profileId, target, hostKey, identity, remotePort)` | Owned SSH process, `-N -L 127.0.0.1:local:127.0.0.1:remote`; forwarding-only device key |
| `describe(tunnel, deviceId, deviceToken)` | Authenticated bounded loopback descriptor; no redirects |
| `revoke(tunnel, deviceId, deviceToken)` | Own-device receipt only; false/uncertain keeps local connection |
| `cleanup(profileId)` | Stops exact abandoned profile tunnel services, closes native signer sockets, cleans private temporary input/result directories; removal also deletes the profile’s Keystore alias |
| `dispose()` | Owner closes local transports only |

Target is explicit user/host/port (22 default), no SSH aliases/config, command
options, proxy commands or URI credential fields. Public SSH is refused **before
keyscan or login**. `ByoHostTailnetResolver.resolve(target)` resolves a hostname
once, requires every DNS answer to be a machine address in `100.64.0.0/10` or
`fd7a:115c:a1e0::/48`, and returns a numeric target. Reserved internal/service
addresses are rejected. That numeric target is used for every SSH operation;
OpenSSH is never given the original hostname to resolve again. A `.ts.net`
suffix, Tailscale package presence, or an ordinary private LAN IP is insufficient.
No public-address fallback, Tailscale auth key, provider API or service of ours.

A range check is not a Tailscale membership API: CGNAT overlaps with some ISP/VPN
networks. The existing `TailscaleBridge` checks installation only. Owner
qualification must prove the Android Tailscale route plus the independently
verified SSH host pin. The app’s fixed `tailnetRequired` reason is “Use a machine that is already on your Tailscale network.” Names with mixed
public/private DNS answers fail closed rather than choosing a permissive answer.

Initial bootstrap accepts either the owner-enrolled phone public key or a one-operation **password** (`ByoHostLogin`),
consumed on success/failure. Imported private keys/passphrases are refused rather
than written to temporary files. The owner must already allow this bootstrap
login through the tailnet; setup never enables password authentication or changes
sshd/firewall policy. Prompt helper answers only the expected password prompt;
keyboard-interactive/2FA is unsupported. The password may serve both bootstrap
SSH calls in this one operation; it is never retained for a later operation.
Do not promise Dart string zeroization. Out-of-band fingerprint verification
precedes any login. After pairing, reconnect uses the native signer exclusively.

The device identity is `ByoHostIdentity(keyAlias:, publicKey:)`. Android Keystore
holds the P-256 private key; `ecdsa-sha2-nistp256` works on supported API 26+
Keystore/OpenSSH versions. Ed25519 availability is not assumed across older
Android releases. `oc/byo_host_signer` owns identity generation and a private
Unix-socket SSH-agent bridge: OpenSSH receives the public key and signatures,
never private bytes. Agent requests are restricted to the expected profile,
identity, SSH user-authentication shape and signing purpose; agent forwarding is
never enabled. Alias/public key can be journaled; signatures are transient.
The signer is not an arbitrary signing oracle offered to UI/setup assistants.
No PEM/OpenSSH private key file, `ssh-keygen` device-key generation, private-key
vault field or private-key export endpoint remains in the phone SSH flow.

The UI does not call the signer directly. The backend owns
`ByoHostSigner.ensureIdentity(profileId)`, `openAgent(profileId, socketPath, user)`,
`closeAgent(profileId)`, and `deleteIdentity(profileId)` via
`lib/host/byo_host_signer.dart`; `AndroidByoHostSigner` maps the corresponding
native MethodChannel operations. Alias format is `oc.byoHostSsh.<profileId>`.
There is no raw-sign/export method. Closing a tunnel closes only its agent;
removing a profile deletes its alias after any requested remote revocation.

Bundle manifest: `ByoHostBundle(version:, openCodeVersion:, artifacts:)`, keys
`x64`/`arm64`; each `ByoHostArtifact(url:, sha256:)` has an HTTPS URL and reviewed
64-character SHA256 of the **whole archive**. No runtime mutable checksum-sidecar
trust, installer `curl | sh`, provider token or auth in URL. Caller-reviewed
OpenCode1 **1.18.32**, host bundle **1.1.0**. Production URL/digest must be compiled into the matching app release after
reviewing the pipeline outputs; no placeholder checksum can enable setup. A
missing manifest is `bundleUnavailable`. Build the deterministic bundles twice
and compare the SHA-256 values. The tag-gated pipeline may attach them only to
a **draft** GitHub `v*` release; it does not publish a release. Building this
branch creates no tag, push or release. Packaging instructions:
[scripts/byo-host/README.md](../../scripts/byo-host/README.md).

The pipeline is [.github/workflows/byo-host-bundle.yml](../../.github/workflows/byo-host-bundle.yml),
using `scripts/byo-host/build_release.py` and reviewed `release-pins.json`.
It freezes upstream OpenCode digests, builds each archive twice, records final
SHA-256 values, and verifies the final digests against the exact app-version
entry before draft attachment. `appVersions` contains the locally built 1.1.0+51 candidate digests; attachment fails closed for every unreviewed app version. Local byte verification is recorded separately from owner approval/publication.
The app receives a version-matching compiled `ByoHostBundle`; it never trusts
a remote mutable manifest or checksum sidecar at runtime. Draft assets are not
publicly usable by the app; release publication remains a separate owner action.

## Journal and transitions

This remote job borrows phone setup v2's check-before-mutate, durable recovery and
safe snapshot semantics ([phone setup v2 design](phone-setup-v2-2026-09-24.md)),
but is not submitted as shell text to `setup_engine`/`setup_contract` components:
those bridges can persist raw output. Remote secrets never enter setup logs.
The new SSH runner uses private input/output files for one-shot password and
pairing data, a native Keystore signing agent, and its own pairing journal.
Those files never contain the generated SSH private key. Phone runtime availability is checked through BuiltinLinux; installing
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
| `needsTrust` | Verify fingerprint independently; optional public-key enrollment; confirm/cancel |
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
Vault `oc.byoHostSecrets.<profileId>` schema 2 contains the profile-scoped
Keystore alias/public SSH key and random 32-byte device token. The private key
exists only inside Android Keystore; no private-key string is serialized.
Legacy exportable private-key identities are refused and need deliberate
owner cleanup/re-pairing, never an automatic import into Keystore. No provider or initial login credentials.
Removal closes the owned tunnel and cleans own-profile abandoned private files
before vault/metadata/Keystore alias erasure; cleanup refusal is unfinished removal.
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
| Add machine | `user@Tailscale-address`, Details port; optional existing machine from saved records; Check; no public SSH fallback |
| Prepare machine | Display `byoHostOwnerSetup(target)` steps, exact commands under Details; owner runs them independently; Check again |
| Verify machine | Public fingerprint, explanation how to compare on the machine; explicit confirmation, cancel |
| Sign in once | Owner-enrolled phone public key or one-time password; no imported private key/passphrase/save-login toggle |
| Setup | Snapshot activity, safe recovery; leaving page does not destroy a saved journal |
| Connected machine | Open sessions, reconnect, machine Details (pin/versions), Remove actions |
| Remove | Two explicit choices; pending revoke, kept-on-failure copy, local cleanup retry |

Plain copy: “Add machine”, “Verify this machine”, “Sign in once”, “Setting up your
machine”, “Connect again”, “Access could not be revoked. Your connection is kept.”
Technical text under Details: Linux prerequisites, SSH settings, fingerprint
command, systemd/linger, pinned versions, Keystore identity and one-shot password-file disclosure.
No shell console/raw log pane. Every URL from a form/remote value follows
`openExternalLink`; do not launch arbitrary URLs or invoke assistant-generated
commands. AI setup assistant remains advisory and receives no login/token/key.

| Failure code | Recovery intent |
|---|---|
| `disabled`, `unavailable`, `bundleUnavailable` | Explain unavailable prerequisite; no mutation/retry loop |
| `invalidTarget`, `needsTrust` | Correct input / verify fingerprint independently |
| `tailnetRequired` | Connect both devices to the user’s Tailscale and enter its machine address; no public SSH fallback |
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

1. Actual ARM64 built-in OpenSSH authenticates through the native Unix-socket
   signer on API 26+ and a current Android device. Private key `getEncoded()`
   remains unavailable; inspect the phone/rootfs/vault with dummy sentinels and
   prove no private key file/export. Test cancellation, agent cleanup, reboot,
   Android Keystore key loss, changed-host denial and deliberate alias deletion.
2. Inspect Logcat, diagnostics, service/setup logs, argv/env and leftovers using
   dummy password/device-token sentinels. Those secrets remain separate from the
   Keystore key and still require private input/output files and own-profile
   cleanup. Same-UID access is not a hostile-agent sandbox; gate stays OFF until
   native bridge/socket behavior is qualified on the built-in Linux runtime.
3. Independently verify SSH fingerprint; install the reviewed checksummed release
   archive; prove both host listeners loopback-only and no public agent ports.
   Phone and machine must already be on the owner’s tailnet. Disable Tailscale:
   no public SSH alternative may be attempted. Test public literal rejection,
   DNS returning both tailnet/public answers, and host DNS rebinding.
4. Owner runs linger and sshd preparation manually, checks effective settings,
   reloads sshd and keeps rescue access. Prove live daemon denies reverse
   forwarding, Unix socket forwarding and tunnel devices while permitting the
   exact loopback local forward. Config-on-disk checks alone are insufficient.
5. Two phones/device tuples, separate native aliases; kill phone/app and reopen
   the same host session. Revoke one phone closes its SSE/WS sockets/key and leaves
   the other phone and host work running. Test response loss/local-only removal.
6. Reboot host and verify user service/linger; interrupted bootstrap uses the same
   device tuple; exercise collision, corrupt journal, checksum failure, storage
   refusal and local cleanup of Keystore alias, vault and scoped preferences.
7. Qualify localhost port allocation race: live-service checks reduce risk but no
   native FD handoff proves socket ownership atomically. Keep flag OFF until
   accepted/proven with dummy credentials or replace with pinned TLS/FD handoff.
   Android can stop the phone tunnel; host sessions must still survive.

A phone device credential authorizes OpenCode tools as the host login user,
including shell/file operations. It is not a hostile-user sandbox: an authorized
agent can change that account’s files/keys. Revoking transport cannot undo those
changes or stop already launched work. Use a dedicated nonroot account for each
trust domain and do not pair untrusted phones to the same account.

Updates remain explicit later work: build/review new archive, host backup, pinned
manifest/version migration, owner-approved host restart and rollback. Adoption
never upgrades a running host or restarts existing work to pair another phone.

## Owner preparation data and sources

`lib/domain/byo_host_setup.dart` exposes `byoHostOwnerSetup(target)` as ordered
`ByoHostSetupStep(id, title, copy, commands, verification)` values. The app displays
these; it never submits their commands to its runner. Step IDs: `tailnet`,
`account`, `linger`, `sshPolicy`, `identity`. Commands interpolate only the
validated nonroot username/port, never phone secrets or raw remote output.
Claude maps title/copy/verification to localized kit parts; exact command text
lives under Details. SSH policy data uses a dedicated-user Match block; no global
password enablement or automatic sudo. The displayed `sshd -T -C` command has
explicit PHONE_TAILSCALE_IP/MACHINE_TAILSCALE_IP placeholders for the owner.

Official sources fetched **2026-10-02**:

- [Tailscale reserved IP addresses](https://tailscale.com/docs/reference/reserved-ip-addresses),
  last validated Jan 12, 2026: numeric machine-range and reserved-service policy.
- [Tailscale CGNAT conflicts](https://tailscale.com/docs/reference/troubleshooting/network-configuration/cgnat-conflicts),
  last validated Mar 16, 2026: a range match cannot establish VPN membership.
- [OpenSSH sshd_config](https://man.openbsd.org/sshd_config): Match/user-effective
  policy, local-only TCP forwarding and separate Unix socket/tunnel restrictions.
- [systemd loginctl](https://www.freedesktop.org/software/systemd/man/252/loginctl.html),
  systemd 252 documentation: lingering retains the user service manager after logout.

## Key-only account enrollment and release pins (B2)

After independently verifying the offered fingerprint, call
`job.prepareSshIdentity(verifiedFingerprint:)`. It reserves a `needsTrust` journal
before generating the native identity and returns **public** alias/key data only.
Render `byoHostKeyEnrollment(job.profileId, identity)` as a setup step. The owner
runs its commands as the dedicated host user through an independent session.
The exact `oc-byo-PROFILE_ID` comment is required: install replaces that bootstrap
line with a forwarding-only key, and revoke removes the same marker. A duplicate
unmarked phone key is rejected; do not instruct users to use a different comment.
Then call `adopt(..., login: ByoHostLogin())` to authenticate through the signer.
If the app closes before adoption, the saved `needsTrust` machine resumes to the
same host pin and profile; requesting the public identity again reuses its alias.
No password or imported SSH private key is needed in this route. Setup errors
leave a retryable journal; local removal also deletes the generated alias.

`lib/host/byo_host_bundle_pins.dart` matches **exact** app versions; unknown versions
return null and disable setup. Update it together with reviewed release-pins.json
whenever bundled bytes change. Assets are still unpublished: no real install can
use this candidate until the owner separately authorizes tag/release publication.
Never publish a draft while asset attachment runs (GitHub has no atomic
check-draft-and-upload operation). The pipeline refuses an already published
release, never clobbers assets, and stops on uncertain API state.

Native security: P-256 generation uses AndroidKeyStore explicitly, not JCA's
exportable software generator. Only its public certificate and Keystore handle
are read. DER signatures are translated to RFC5656 SSH mpints. There is no
private-key serialization or file writer in the native signer. Android Keystore
is not necessarily hardware backed on every device. Same-UID app/runtime
compromise can use an active signer as an authentication oracle for the configured
user: session/destination binding is not implemented. Socket mode/UID checks
exclude other Android apps but do not sandbox the app's own Linux programs.
Closing transports shuts the sockets; profile deletion/reset removes aliases.
Vault schema 2 stores alias/public key/device token and refuses legacy private-key
envelopes rather than silently restoring an exportable identity.

Primary references, fetched **2026-10-02**: [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
(last updated 2026-03-06), [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)
(API 23+), [EC KeyProperties](https://developer.android.com/reference/android/security/keystore/KeyProperties#KEY_ALGORITHM_EC),
[RFC5656](https://www.rfc-editor.org/rfc/rfc5656) (ECDSA P-256 wire format),
[RFC9987](https://www.rfc-editor.org/info/rfc9987/) (SSH agent protocol),
[OpenSSH agent extensions](https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.agent),
[Tailscale IP addresses](https://tailscale.com/kb/1015/100.x-addresses), and
[GitHub release assets API](https://docs.github.com/en/rest/releases/assets).
Ed25519 in Android Keystore is not assumed on API26+; portable P-256 is the single
slice-1 algorithm. No confidential client secret or backend of ours is involved.

B2 listener preparation also exposes the `sshNetwork` owner step. It shows the
exact `ListenAddress` drop-in, configuration check, Ubuntu ssh.socket disable and
ssh.service restart commands. The owner must keep rescue access, replace the
address placeholder and remove existing wildcard ListenAddress directives.
Setup reads **actual** `ss -H -ltn` output on the chosen SSH port and rejects
wildcard, public or ordinary LAN bindings before pairing. It never applies these
administrator changes. Check all alternate SSH ports independently; the app's
read-only gate covers the selected port. Existing public services on an adopted
multi-purpose host are outside this host supervisor; all supervisor/OpenCode
listeners remain loopback-only. Do not promise that unrelated services are closed.

The signer/channel is native code: it requires a new signed application release.
A Dart-only Shorebird patch cannot supply this native implementation. No APK or
release was produced by this backend task.
