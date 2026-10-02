# Bring your own VPS: backend and UI contract

Date: 2026-09-27. Status: **proposed, not implemented or enabled**.
Source baseline: `1625ac02` on `codex/vps`. Research and official citations:
[BYO VPS research](byo-vps-research-2026-09-27.md).


## 2026-10-02 scope update: SSH adoption is slice 1

The provider-create plan below is preserved as a later design, not implemented
interfaces. The owner changed first delivery to a MonoCode-style **Add machine**
flow: explicit `user@host`, host-key verification, one-shot admin SSH login,
checksummed OpenCode1 + per-device supervisor user service, then private SSH local
forward. No service of ours, no public agent listener, no credential broker.
Hetzner, DigitalOcean and Linode creation remain subsequent slices. Existing
Tailscale is required for the SSH destination; there is no public SSH fallback
or automatic tailnet join. Task B2 uses a nonexportable Android Keystore signer,
manual owner admin preparation, and version-pinned draft-release bundle tooling.

The implemented backend contract is [BYO host contract](byo-host-contract.md):
`ByoHostService`, `ByoHostController`, `ByoHostSshRunner`, profile-scoped vault and
journal, restricted per-phone forwarding key, remote revoke before local remove,
and a default-OFF `OC_BYO_HOST` gate. UI and real phone/VPS qualification remain
outstanding. This new slice installs OC1 1.18.32 only; it does not expose stub
OC2/Codex/Paseo/AI Team support. The detailed older provider contracts below must
not be treated as current callable Dart APIs.

Finish line: a user can create one Hetzner Ubuntu server, or adopt a clean Ubuntu
server over SSH, connect OpenCode 1 exclusively through their own tailnet, resume
after killing the app, and explicitly remove the resources and local secrets.
Non-goals: hosting/billing/proxying by us, public agent endpoints, AI-controlled
infrastructure, all-provider/all-runtime launch, arbitrary OS conversion, UI code
in the backend slice, and transparent migration of existing workloads.

## 1. Boundaries and ownership

| Proposed owner/module | Responsibility / public seam | Existing integration |
|---|---|---|
| `lib/domain/vps/` | Values, interfaces, policy, typed failures; no provider HTTP or widgets | Adjacent to `ServerGateway`, not an extension of its chat methods |
| `lib/vps/providers/` | Dedicated direct-to-provider clients, pagination, auth, rates, reconciliation | No `api/`/`api2/` imports; no shared agent authorization interceptors |
| `lib/vps/bootstrap/` | SSH host verification, artifact manifest, remote job runner, Tailscale, systemd | Reuse portable component/check/progress semantics from `lib/builtin/setup/` |
| `lib/state/vps_controller.dart` | One job actor per infrastructure profile; safe snapshots and user actions | Composition owner supplies store, gateways, clock and connection finisher |
| `lib/state/vps_store.dart` | Versioned journal and profile-scoped secure vault, deletion hooks | `ProfileStore`, `ConnectionController.deleteProfileAndLocalData` |
| UI owner, later | Pages composed entirely from `lib/ui/kit/`; localization and navigation | Imports domain/state only; missing kit parts are added to kit |

These names are a proposed frozen interface for the first implementation review,
not imports available today. Backend completion must be reported as groundwork
until the UI owner connects and verifies the complete journey.

Infrastructure capabilities belong to `VpsProviderSupport`; installability belongs
to `RuntimeInstallSupport`. `ServerCapabilities` continues to describe the connected
agent's operations. Owning a VPS does not enable unsupported config writes, model
login, terminal operations or assistant sessions. At final connection use the
existing gateway factory and `ServerOperationsGateway`; UI never switches on
`ServerFlavor`. AI Team remains an orchestration attachment, not a chat flavor.

The [AI setup assistant contract](ai-setup-assistant-contract.md) is unchanged:
its current guided planner cannot create machines, mint keys, run shell, or make
configuration Apply/Undo available. Future assistant suggestions may select a
non-secret `VpsDraft`; only the ordinary reviewed controller can execute it.
Never put provider credentials or SSH commands into assistant prompts.

## 2. Identity and persistence

Allocate a stable UUID `profileId` **before accepting a token or sending any
mutation**. It is a reserved infrastructure profile, not a selectable connected
`ServerProfile` with a fake URL. Persist reservations under
`oc.vpsReservations`; this shared index stores IDs only and needs an explicit
controller deletion hook. Pending jobs remain visible on startup. No credentials
are shared across profiles in v1; a second machine imports its own scoped token.

| Key (exact suffix convention) | Storage | Contents |
|---|---|---|
| `oc.vpsJob.<profileId>` | Atomic app-private journal file / store | Schema, IDs, phase, safe step receipts, resource ownership, approved quote hash; no secrets |
| `oc.vpsMetadata.<profileId>` | Preferences | Safe provider/account reference, reservation/endpoint relationships |
| `oc.vpsSecrets.<profileId>` | `flutter_secure_storage` | Versioned secret envelope: provider token/refresh token, SSH private key, temporary enrollment key, pairing request nonce, active server connection secrets as needed |
| `oc.vpsTrust.<profileId>` | App-private durable store | Pinned SSH public key/fingerprint, provenance, provider resource ID, generation |

Use one serialized vault writer; secret references are typed slots in that
envelope, not user-entered map keys. UI snapshots never contain the envelope.
The journal uses temp-write/fsync/rename plus a monotonic revision; keep a last
valid checkpoint and fail closed on corrupt/unknown future schemas. Preferences
are not a multi-record transaction. Secrets must be durable before the journal
references them; write intent before each provider mutation. On startup reconcile
unreferenced secret slots and interrupted transactions without making new writes
to a provider.

**Existing deletion limitation:** `profileScopedPreferenceKeys` sweeps
`SharedPreferences`, not all Keystore entries or files. `ProfileStore.remove`
currently deletes the existing password or Codex token slot. The implementation
must register and verify deletion of the new secure envelope, files and reservation
index; naming alone is insufficient. Drain/dispose the job actor before the
existing profile deletion transaction so late callbacks cannot recreate data.
A Keystore deletion failure returns `storageUnavailable`, never successful removal.
Do not call a global `secure.deleteAll()`.

One physical machine has one infrastructure owner and, later, several protocol
endpoint profiles. Each child records `infrastructureProfileId`; each child's
connection credentials remain under its own profile ID. Removing a child does
not destroy the machine. Removing the infrastructure owner requires reviewing all
children and draining all affected controllers. v1 supports one endpoint only;
multiple machines work through independent owners, with serialized costly actions.

## 3. Provider and host interfaces

Dart-shaped signatures below specify the contract; supporting values are immutable
and bounded. All results contain safe codes/IDs, never raw HTTP responses. Every
method accepts a cancellation/deadline context through the constructed adapter.
Cancellation stops waiting; it does not prove a remote operation was cancelled.

```dart
abstract interface class VpsProviderAdapter {
  VpsProviderSupport get support;
  Future<AccountSummary> inspectAccount();
  Future<Page<RegionOffer>> regions(PageRequest page);
  Future<Page<MachineOffer>> offers(OfferQuery query, PageRequest page);
  Future<Page<ImageOffer>> images(ImageQuery query, PageRequest page);
  Future<Page<MachineSummary>> machines(PageRequest page);
  Future<CostQuote> quote(CreateIntent intent);
  Future<ResourceReceipt> ensureSshPublicKey(SshPublicKeyIntent intent);
  Future<ResourceReceipt> ensureFirewall(FirewallIntent intent);
  Future<CreateOutcome> createMachine(ApprovedCreateIntent intent);
  Future<ReconcileOutcome> reconcile(CreateOperationRef operation);
  Future<MachineObservation> inspectMachine(ResourceRef machine);
  Future<FirewallObservation> closeBootstrapAccess(ResourceRef machine);
  Future<DeleteOutcome> deleteOwnedResource(OwnedResource resource);
  Future<RevocationOutcome> revokeImportedCredential();
  Future<void> dispose();
}

abstract interface class VpsHostAdapter {
  Future<HostAssessment> inspect(VerifiedSshTarget target);
  Future<RemoteJobObservation> readJob(String jobId);
  Future<RemoteJobReceipt> ensureJob(ApprovedBootstrapManifest manifest);
  Future<EnrollmentChallenge> beginTailnetLogin(String jobId);
  Future<void> supplyEnrollmentKey(SecretRef oneUseKey);
  Future<TailnetObservation> inspectTailnet();
  Future<ReadinessEvidence> verifyPrivateServices();
  Future<SealedPairingReceipt> readPairing(PairingRequest request);
  Future<void> acknowledgePairing(PairingAcknowledgement receipt);
  Future<HostCleanupOutcome> removeManagedInstallation(HostCleanupPlan plan);
}
```

Adapters receive a `CredentialProvider` at construction; methods never accept
plaintext credentials as ordinary value objects. Generic SSH implements only the
host interface and a provider support value with `create=false`, `delete=false`,
`providerFirewall=false`, `priceQuote=false`; it must not simulate a cloud API.
`revokeImportedCredential` can return `manualRequired` where the provider has no
safe self-revocation API; local erasure is a different operation.

Core values:

- `VpsProviderSupport`: provider ID, API version, enabled flag and gate reason,
  authentication methods, least-privilege notes, create/adopt/delete/firewall/
  public-key/catalog/price capabilities, idempotency mechanism, ARM availability,
  authenticated-host-key discovery support and console URL policy.
- `RuntimeInstallSupport`: component ID, OS/architecture set, manifest digest,
  exact version, verified artifact set, minimum/recommended memory/disk,
  auth/bootstrap probe version, available flag and reason. Unknown checksum,
  required auth or compatibility => unavailable.
- `CreateIntent`: provider/project/region/image/size IDs, architecture,
  selected runtime IDs, resource UUID, normalized label, approved network mode.
  No arbitrary user-data or shell text. Provider-independent validation rejects
  shell metacharacters, URL overrides, custom images and conflicting ports.
- `CostQuote`: quote ID, UTC observed/expiry times, currency, exact decimal strings,
  hourly price, monthly cap or estimate and its assumed hours, compute/disk/IP/
  backup/egress line items, tax status, included allowance, excluded unknowns,
  source URL and provider catalog revision. Unknown mandatory costs block Create.
  An estimate is not a budget cap. No FX conversion without a dated source.
- `ApprovedCreateIntent`: canonical intent digest + current quote ID/digest +
  confirmation timestamp + operation UUID. Changing region, size, price,
  architecture, backup/network option or selected runtime invalidates approval.
- `OwnedResource`: provider/project/region/type/ID, `createdByThisJob`, action ID,
  parent dependencies, disposition (`delete`, `retain`, `adopted`) and generation.
  Labels aid discovery but never independently authorize deletion.
- `CreateOutcome`: accepted(resource/action IDs), rejected(safe failure), or
  uncertain(operation ref). `ReconcileOutcome`: unique match, proven absent,
  still pending, ambiguous matches, or inaccessible. “Not in first list page”
  is never proof of absence.
- `ReadinessEvidence`: observedAt, machine generation, host-key match,
  tailnet node ID/address/name, enabled Serve routes, Funnel-off result,
  host listener/firewall checks, provider closure with provenance (`apiObserved`,
  `ownerAttested`, `unknown`), external reachability probe with time/address-family/
  observer provenance, authenticated protocol probe, manifest match and
  credential-persistence receipt. Host checks never claim provider API evidence.
  Generic SSH requires explicit owner attestation of provider ingress and an
  external scan; unknown closure blocks Ready. This is weaker evidence than a
  managed provider observation and remains labelled in the UI.
- `ReadinessAttestation`: exact job/resource/generation, checked public IPv4/IPv6,
  checks performed, time and owner confirmation; backend validates freshness and
  binding and never promotes it to API-verified. No arbitrary log upload.
- `ManualCleanupEvidence`: exact resource/node/key ID, expected deletion/revocation
  action, console source and owner confirmation time. Manual completion is
  labelled owner-confirmed, never remotely verified.
- `HostAssessment`: Ubuntu release, CPU architecture, disk/RAM, sudo/systemd,
  existing services/users/firewall/Tailscale state, ports, reserved-path conflicts,
  host-trust provenance and required owner actions. It carries no raw command log.

Dedicated HTTPS clients validate certificates, do not follow credential-bearing
redirects, allowlist provider origins, paginate with bounds, honor `Retry-After`
and quota headers, and use jittered backoff. Retry safe reads; reconcile ambiguous
writes. Never attach provider tokens to a download URL, SSH session, agent gateway,
analytics, crash reporter or our services.

## 4. Controller API and UI-safe state

```dart
abstract interface class VpsController {
  VpsSnapshot get snapshot;
  Stream<VpsSnapshot> get changes;
  Future<void> load();
  Future<void> refreshCatalog();
  Future<void> setDraft(VpsDraft draft);
  Future<CostQuote> requestQuote();
  Future<ReviewPlan> review();
  Future<void> confirmCreate(String reviewId);
  Future<void> confirmAdoption(String reviewId);
  Future<void> verifyHostKey(HostKeyProof proof);
  Future<void> submitReadinessAttestation(ReadinessAttestation evidence);
  Future<void> submitManualCleanupEvidence(ManualCleanupEvidence evidence);
  Future<void> beginTailnetLogin();
  Future<void> resume();
  Future<void> reconcile();
  Future<void> pause();
  Future<void> cancel();
  Future<void> connect();
  Future<TeardownPlan> reviewTeardown(TeardownMode mode);
  Future<void> confirmTeardown(String planId);
  Future<UpgradePlan> reviewUpgrade(ManifestRef target);
  Future<void> confirmUpgrade(String planId);
  Future<void> dispose();
}
```

Secret entry is a separate state-layer `VpsCredentialEntry` seam:
`storeProviderCredential(profileId, SecretInput)`,
`storeSshCredential(profileId, SecretInput)`,
`storeEnrollmentKey(profileId, SecretInput)` return only an opaque `SecretRef`.
`SecretInput` has redacted `toString`, cannot serialize, and drops references after
use; Dart strings cannot promise secure heap zeroization. System-browser OAuth
uses a dedicated `VpsAuthCoordinator`; verifier/state/nonce are vault-only,
one-shot and expire. No client secret belonging to us is compiled into the app.

Subscribe then read `snapshot`; broadcasts do not replay. `load` performs local
recovery only, no create/charge or secret display. Explicit resume reconciles
remote truth before continuing the same previously approved operation. `dispose`
stops local polling and drains writes; it does not destroy the server or stop a
remote install. Calls are serialized; concurrent calls return `busy`.

`VpsSnapshot` fields: profile/job IDs, revision, safe provider/account label,
`phase`, optional `currentStep`, per-step measured progress, safe machine summary,
quote/review, `requiredAction`, fixed `failure`, local/remote last-observed times,
resource ledger, `billingMayContinue`, allowed actions and resulting endpoint
profile IDs. No secret, raw exception, request/response body, arbitrary stdout,
model key, login URL, user-data or terminal transcript is in this value.

Login URLs are delivered as short-lived `ExternalActionRef`, resolved only by the
navigation boundary after explicit user action. Validate exact official HTTPS
origins and use `openExternalLink`; do not persist URLs containing enrollment
capabilities or put them in notification copy. Verified Android App Links require
owned HTTPS domain association and package-signing validation; static association
hosting is not a token broker. If registration requires a confidential exchange,
keep OAuth unavailable and offer the documented phone-local token path.

## 5. Durable state machine

```text
 draft -> validating -> awaitingCostApproval -> creating -> discovering
 adopt -> verifyingHost -> inspectingExisting -> awaitingAdoptionApproval
       -> bootstrapping
 discovering -> verifyingHost -> bootstrapping -> awaitingTailnetLogin
 -> awaitingTailnetApproval -> securing -> installing -> pairing -> connecting
 -> ready

 any active step -> waitingNetwork | waitingRateLimit | needsCredential
                 | needsUserAction | interrupted | failed | uncertain
 ready -> reviewingUpgrade -> upgrading -> verifying -> ready
 reviewTeardown -> deleting -> verifyingDeletion -> deleted | cleanupRequired
```

Local syntax validation can precede host verification; remote adoption assessment
requires `VerifiedSshTarget`, so no remote command or secret transfer happens
before independent host trust.

`phase` records orchestration; each step also has `pending`, `intentRecorded`,
`running`, `succeeded`, `failed`, `unknown`, or `skipped`. `paused`/`cancelled`
are local scheduling states with the resource ledger preserved. Neither means
billing stopped. `awaitingTailnetApproval` also covers device approval, Tailnet
Lock signing, missing grants, or HTTPS consent; present the specific action.

Journal schema v1 contains `schemaVersion`, monotonic revision, job/profile IDs,
operation ID, manifest digest, canonical plan/quote hashes, credential references,
resource ledger, attempted action IDs, remote runner generation/cursor, enrollment
expiry (not key), step observations, pause/cancel intent and timestamps. Unknown
version blocks writes and offers recovery/export of safe resource IDs.

Rules that make this resumable:

1. Reserve UUID and secure storage, then persist reviewed intent **before** POST.
   Use provider idempotency tokens where supported. Else label/name with UUID,
   save request time and reconcile all pages, actions and exact account scope.
   Timeout after POST never triggers a blind second create. If absence cannot
   be proved, remain uncertain and offer console reconciliation, not Retry create.
2. Persist each returned resource/action ID before the next mutation. Every
   mutation has a receipt state, including firewall and SSH-key creation.
   Reconcile these auxiliary resources too; duplicate names are not safe proof.
3. Remote work runs under a root-owned systemd oneshot runner with a durable
   journal and `flock`; it outlives the phone/SSH connection. A second `ensureJob`
   checks job ID + manifest hash, reads its status and never starts a parallel
   installer. Remote `running` is not assumed dead because the app died.
4. Resume executes component version/hash/config checks. Do not mark done from
   a stale flag, HTTP 200 alone, or percentage. Keep user input out of scripts;
   a new manifest is an explicit upgrade/review, not an implicit resume.
5. Phone termination during pairing is recoverable using the persisted request
   nonce/receipt. The server retains the same sealed result until ACK or expiry;
   ACK happens only after the profile and secure credential are saved and probed.
   An ACK retry is idempotent. Expiry permits authenticated re-pair/rotation.
6. Android foreground work is bounded; app startup discovers pending reservations.
   No claim of indefinite background lifetime or precise progress while offline.
   UI marks stale observations. Completed server work is discovered on reconnect.

Measured bytes/percent from the existing `::oc` protocol can feed kit progress.
Provider allocation, login, DNS and unknown-length stages are indeterminate.
Do not reuse the phone engine's fallback half-progress as factual VPS progress.
`ChannelSetupEngine` is tied to `BuiltinLinux`/Kotlin and a phone finisher: reuse
portable planning/types/helpers through a later reviewed extraction, not by
pretending a VPS is an in-app Linux rootfs. `SetupJobRecord` remains the phone
format; infrastructure requires the additional ledger above.

## 6. Pairing and transport

v1 uses pinned ordinary OpenSSH over the tailnet to read a root-protected pairing
receipt through a fixed restricted helper; it needs no extra HTTP pairing server.
Use public SSH only for initial bootstrap, restricted to an owner-supplied source
CIDR and deadline. Pin the same host key when transitioning to the tailnet.
Never infer identity from a `100.x` address or a device name alone.

After verified tailnet enrollment, the phone reads `tailscale status --json`
through the pinned channel, records Self node ID + DNS name + addresses, then
probes the returned HTTPS endpoint through its own Tailscale connection. It does
not need a tailnet-wide device-list permission. Cross-check an API device record
when supplied; hostname lookup alone must never select an unrelated machine.

OpenCode payload compatibility:

```json
{"urls":["https://oc-RESOURCE.tailnet.ts.net"],"username":"opencode","password":"<secret, never logged>"}
```

The inner value fits `PairingPayload`/`parsePairingPayload` and its bounded parser;
call `consume()` after secure handoff. The current format is **not** a one-time
protocol and carries no resource binding, expiry or cryptographic proof. Wrap it
in an SSH-authenticated `PairingReceiptV1` binding job ID, resource ID, host-key
fingerprint, manifest digest, protocol, endpoint, nonce and expiry. The job/UI
stores only receipt metadata. No QR, clipboard or log is required on the phone.
Manual QR/paste remains an explicit recovery action with the same validation.

Codex/Paseo need typed `wss` + secret variants through their existing profile
contracts, not the OpenCode JSON parser. AI Team needs its orchestration contract,
not an invented Basic password. Its current direct-tailnet HTTP host front uses
peer-IP identity; it cannot simply sit behind Serve. Keep VPS AI Team unavailable
until the direct transport or a reviewed HTTPS identity-aware front is qualified,
as detailed in the research. Later multi-runtime deployments use distinct
Serve HTTPS ports/origins so current root-origin URL validators need no path
prefix changes; all ports remain tailnet-only. Port assignments are in the
manifest and included in grants and probes.

`ready` requires correct version and authenticated protocol handshake, persisted
profile, verified tailnet route, no public SSH/agent ingress, no Funnel, and
reboot-persistent configuration. Model account sign-in is separate
`needsAgentSignIn`; it is never implied by server readiness. In UI distinguish
“Server connected” from “Ready to run an agent”.

## 7. Cleanup, deletion and upgrades

Three explicit teardown modes:

- **Remove from this phone**: review resources and continuing charges, dispose
  controllers, erase all scoped secrets/files/preferences. No remote deletion.
  Offer safe provider IDs/console links before forgetting; no token export.
- **Delete managed server and resources**: fresh review names exact machine,
  disks, public IPs, firewalls, uploaded SSH keys, snapshots and child profiles.
  Retain backups only when explicitly selected and list their continued cost.
  Verify ownership/generation, delete dependent resources in provider order,
  poll absence, remove the Tailscale device and revoke enrollment credentials,
  then revoke the provider token if requested and supported, and erase locally.
  Interactive Tailscale enrollment provides no API credential: in that mode device
  deletion is `manualRequired`, with recorded node ID and a guarded console link.
  Preserve a safe outstanding-cleanup record until owner submits bound console
  evidence; display owner-confirmed rather than API-verified. `tailscale logout`
  before VM deletion deauthorizes but does not prove device-record deletion.
  The same manual-result rule applies to enrollment-key revocation without API
  rights. VM billing stopped and tailnet cleanup pending are separate outcomes.
- **Uninstall from adopted server**: remove only manifest-owned units/files/users
  and explicitly managed tailnet enrollment. Do not delete the VM, its unrelated
  data, pre-existing SSH keys, Tailscale node, firewall or provider resources.

Keep credential and resource ledger until cleanup finishes; `cleanupRequired`
means charges may continue. Revoked credentials / 403 / wrong region / network
failure never count as absence. A provider-authenticated 404 for a known exact
ID may confirm absence only after account/project access is established. Cleanup
can resume after app death. Never automatic rollback-delete a billed VM with
potential user data; ask through the exact teardown review.

Provider key revocation, removal of Tailscale auth key, removal of enrolled node,
SSH-key removal and server password rotation are separate effects. Deleting a
Tailscale enrollment key does not evict an already enrolled node. Phone-loss
recovery is documented through the user's provider/Tailscale consoles; no recovery
account or retained token exists on our side.

Upgrade review shows old/new manifest, architecture, downtime, backups and added
cost. Preflight disk, snapshot if explicitly approved, stage versioned binaries,
verify hashes, drain sessions, atomically switch executable links, restart and
probe. Keep previous binaries/config for rollback; irreversible database/schema
migration needs a tested restore plan, never “just downgrade”. Ubuntu security
updates are automatic with explicit reboot policy; agent versions change only
through reviewed manifests. Adopted hosts get a scoped change plan first.

## 8. UI handoff to Claude

All pages consume controller snapshots/actions; none owns tokens, raw provider
HTTP or shell execution. English/Arabic copy is added by the UI owner; URLs,
resource IDs and fingerprints remain LTR. Announce stage changes accessibly,
not every byte update; support large text, keyboard/focus and reduced motion.

| Page | Required content / copy intent | Actions and exceptional states |
|---|---|---|
| Your VPS | “Use a server you own. You pay your provider directly.” Provider rank, available/gated reasons, existing Ubuntu route, pending jobs | Choose provider/adopt; resume pending job; never imply unavailable OAuth works |
| Connect account | Required scope/project, secure token entry or supported browser sign-in; where data goes | Save secret through entry seam; expired/insufficient scope/locked secure storage; no secret in summary |
| Choose machine | Region, architecture, available sizes, recommended memory, image, runtime availability | Refresh; empty capacity/quota; retain selection without substituting a bigger paid size |
| Review costs | Itemized currency/rate, estimate/cap distinction, extras/tax/egress, quote time; “Charges start when created and may continue while stopped.” | Explicit Create using exact review ID; expired/changed quote must be reviewed again |
| Adopt and trust | Host/SSH user, OS and conflict report, changes planned, fingerprint with console verification instructions | Confirm host proof and adoption plan; mismatched key blocks; no silent TOFU |
| Connect Tailscale | Install/open Tailscale on phone, server-specific sign-in, correct tailnet, device/HTTPS/grant approval | Open validated external action; retry expired login; tailnet offline/restricted/Lock pending; no fallback public URL |
| Setup progress | Named steps, measured progress, stale timestamp, resources already billed | Continue/reconcile, pause/cancel, review cleanup; copy “Closing this app does not stop the server or its charges.” |
| Server connected | Private HTTPS/WSS address, protocol/version, connection result; model sign-in separately | Open server; existing agent sign-in; failed model auth does not recreate VM |
| Manage server | Health, manifest, provider resource links, expiry/reboot/update needs | Review upgrade, reconnect/repair, add another machine, review teardown |
| Remove / recovery | Exact owned/adopted resources and retained charges, local forget vs remote deletion | Confirm current plan; partial deletion retains ledger; manual revocation links; never generic “Done” on uncertainty |

Typed errors map to fixed localizable copy and allowed recovery:

| Code | Intent / next action |
|---|---|
| `unsupportedAuth`, `registrationRequired` | This sign-in method is not available; use supported token/manual path |
| `needsCredential`, `insufficientScope`, `credentialRevoked` | Re-enter or grant the listed permission; do not show provider response body |
| `storageUnavailable` | Could not securely save/remove credentials; stop before mutation |
| `quoteExpired`, `quoteChanged`, `costUnknown` | Review updated costs; no creation |
| `quotaExceeded`, `capacityUnavailable`, `architectureUnavailable` | Choose another offer explicitly; no automatic paid substitution |
| `hostKeyUnverified`, `hostKeyChanged` | Verify through trusted console; changed identity blocks all secrets |
| `hostConflict`, `unsupportedHost`, `sudoRequired` | Explain required host property and non-destructive manual repair |
| `tailnetOffline`, `tailnetMismatch`, `tailnetApprovalRequired`, `httpsRequired` | Restore private connectivity/approval; never widen ingress |
| `checksumMismatch`, `manifestUnsupported`, `protocolMismatch` | Stop; use reviewed compatible manifest, not latest |
| `network`, `rateLimited`, `busy` | Retry safe read at reported time; preserve observed state |
| `operationUncertain`, `resourceAmbiguous` | Reconcile exact job/resources before any repeat mutation |
| `pairingExpired`, `pairingRejected` | Re-pair over verified tailnet channel; no credentials in details |
| `publicExposureDetected`, `firewallUnverified`, `bootstrapAccessExpired` | Stop agent services/Serve exposure where safely owned; require repair before Ready |
| `cleanupRequired`, `revocationManualRequired` | List remaining resource IDs/actions and potential ongoing charges |

## 9. Implementation slices and acceptance

First freeze the types above plus a versioned JSON schema and runtime manifest.
Use independent worktrees, exclusive write sets, and one integration owner for
`profiles.dart`/`connection.dart`/`main.dart`; reviewers do not run test processes.

1. **Feasibility packet (before adapter implementation):** resolve the research
   gates for Hetzner auth/catalog/firewall attachment, SSH host proof, Tailscale
   consent/HTTPS, SSH client package and OpenCode artifact integrity. A failing
   provider gate leaves its Create disabled; generic SSH can proceed if its own
   gates pass. Produce sanitized fixtures, no resource creation in ordinary tests.
2. **Backend first, one coordinated usable slice:** three independent owners can
   implement (A) Hetzner adapter + fake HTTP fixtures, (B) generic SSH host runner
   + manifest + VM-free shell fixture harness, (C) job/vault/controller + fake
   adapters. Freeze result/error types first. Integration owner wires reservations,
   profile finalization/deletion and tailnet policy. Deliver scripted fake-controller
   walkthroughs and this UI contract. This is not product completion yet.
3. **Claude UI integration:** kit-only create/adopt/review/progress/recovery/remove
   journey, English/Arabic, runtime screenshots and accessibility evidence. Finish
   create → restart → private connect → cleanup and adopt → restart → uninstall.
4. **Owner throwaway proof:** execute the research proof plan; only after passing
   enable Hetzner and generic SSH. Backend, enabled, verified, committed and released
   are separate states. No release or resource creation is authorized by this doc.
5. **Next complete slices:** DigitalOcean, then Linode; runtime additions OC2,
   Codex, Paseo/Claude and AI Team are separate gated end-to-end slices including
   auth, restart, upgrade and deletion. Remaining providers follow demand/rank.

Required fake tests: every 401/403/429/5xx; malformed/paginated catalogs and money;
expired quote; denial before mutation; POST success followed by lost response;
app death at every journal boundary; ambiguous duplicate labels; secondary-resource
leaks; SSH mismatch and reboot; wrong tailnet/name collision; grant/HTTPS denial;
checksum/truncated/range-download failures; apt lock/disk-full; resume without
reinstall; pairing ACK lost before/after profile save; secure storage failure;
secret sentinel absent from snapshots/logs/diagnostics/user-data/notifications;
foreign/adopted resource protection; partial deletion and key revocation; unknown
journal schema; multiple jobs/profile deletion races. Mock the secure-storage
method channel when touching ProfileStore, as AGENTS.md requires.

No live API calls in unit/widget tests. Use fixtures against a fake transport,
clock, filesystem, SSH process and credential vault. Real VM proof is owner-run,
opt-in and outside CI. Format affected code and run focused checks per slice;
analyzer and full serial Flutter suite belong to the stable integration boundary.
This research-only change requires document/link/diff checks, not Flutter tests.
