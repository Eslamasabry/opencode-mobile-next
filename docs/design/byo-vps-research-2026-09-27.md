# Bring your own VPS: research and buildable plan

Current follow-up: **2026-10-02 SSH adoption backend, default OFF**. Read
[BYO host contract](byo-host-contract.md) for implemented APIs and qualification
gates. The original findings and price observations below are dated research.

Research date: **2026-09-27**. Repository inspected at **`1625ac02`**, branch
`codex/vps`. Status: **research complete; implementation, live feasibility and
release not performed**. Companion: [backend/UI contract](byo-vps-contract.md).

Finish line for this task: dated official evidence, explicit feasibility gates,
and a backend-first plan that Claude can use to build the kit-based UI later.
Non-goals: product code, paid hosting, accounts, resource creation, credential
collection, publishing, signing or pushing. All research requests were public
read-only documentation/catalog/schema requests. Prices are dated observations,
not offers; no provider account or real phone-to-VPS bootstrap was tested.


## Follow-up evidence and revised first slice — 2026-10-02

The 2026-09-27 provider findings/prices below retain their observation date;
they were not all refreshed for this implementation follow-up. The owner's new
first slice is **adopt Ubuntu over SSH**, then Hetzner/DO/Linode creation later.
Implementation and current UI contract: [BYO host contract](byo-host-contract.md).
Default OFF, no real phone or VPS proof, no UI/bundle publication performed.

### MonoCode: inspected actual MIT source

Read the public repository at commit
[`1e97594ddf6f40aa24671f7fa09f2048deb1d5eb`](https://github.com/hardbeat920/monocode/tree/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb),
including README remote access and docs, not only an abstract architecture.
Original code in our slice was written for this app; no MonoCode code was copied.

| Inspected source | Finding and adopted decision |
|---|---|
| [remote-access.md](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/docs/remote-access.md), [remote_bootstrap.sh](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src-tauri/src/remote_bootstrap.sh) | Architecture-specific host archive, user service/linger and local listener. Our archive digest is reviewed in the app manifest before installer execution, rather than trusting a freshly fetched sidecar. |
| [remote_ssh.rs](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src-tauri/src/remote_ssh.rs), [remote.rs](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src-tauri/src/remote.rs) | Local SSH forward, askpass, pinned host identity, revoke before forget. We use strict preverified Ed25519 pins and explicit safe typed failures. |
| [host/store.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/store.ts), [host/server.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/server.ts), [host/service.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/service.ts) | Host owns durable work, random per-device credentials represented as hashes on host; revoke targets one device. Our small stdlib supervisor wraps existing OC1 instead of reimplementing a session protocol. |
| [connections.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/features/connections/model/connections.ts), [remoteSessionState.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/features/connections/model/remoteSessionState.ts), [session.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/features/sessions/model/session.ts) | Saved connection and session snapshots are separate from a live client transport. Phone disconnect does not destroy host work; reconcile server truth on reconnect. |

### Phone feasibility: source-proven, device proof still required

Built-in setup `lib/builtin/setup/components.dart` installs openssh-client;
`lib/builtin/builtin_linux.dart` prerequisite repair and
`lib/termux/opencode_ubuntu_setup.dart` also install it in managed Ubuntu.
That does not prove native outer-Termux SSH or an available installed runtime.
`BuiltinLinux.run` dispatches shell argv, closes stdin and its native implementation
logs merged output (`android/.../BuiltinLinux.kt`). It cannot carry SSH output or
secret stdin safely without isolation. Its arbitrary `startService`/`stopService`
can own one long-running foreground SSH tunnel; detached children of a one-shot
proot command cannot be assumed to survive. Android may stop that service.

Task B2 supersedes the initial exportable-key approach: Android Keystore creates
P-256 `ecdsa-sha2-nistp256` keys and a private same-UID Unix-socket agent returns
signatures to built-in Ubuntu OpenSSH. No phone SSH private bytes enter Dart,
the vault or a file. Vault schema 2 contains alias/public key/device token only;
legacy private-key envelopes are refused. Imported keys/passphrases are refused.
One-operation password/pairing inputs still use private, bounded temporary files;
these do not contain the device SSH private key. Host keys remain independently
verified and pinned. Termux's different UID/private filesystem needs a separate
transport; this slice uses built-in Linux only. Native compile and fake tests are
not physical Android Keystore/proot socket proof. Only already-tailnet machine
addresses are accepted, with numeric DNS resolution and no public SSH fallback.
Official signer/agent evidence and owner qualification gates are in the updated
[BYO host contract](byo-host-contract.md); the feature remains default OFF.

`ProfileStore` preference suffix sweeping never swept new secure slots by itself.
This slice explicitly adds the BYO vault to profile deletion and sign-in reset;
it rejects persisting runtime localhost profiles. Backend metadata has stable
host/device identity, not a stale forwarded port. UI remains Claude's kit work.

### Current official SSH/systemd evidence and feasibility gates

Fetched official OpenSSH manuals on **2026-10-02**; living manuals are not an
Ubuntu version pin. `ssh -L` can explicitly bind loopback and `-N` avoids a remote
command; `SSH_ASKPASS_REQUIRE=force` supports the helper approach. Our helper
uses private files, prompt-kind checking and one bootstrap operation. Host keys
are verified out of band, then `StrictHostKeyChecking=yes`, explicit known_hosts,
no user config/agent/proxy fallback. See [ssh(1)](https://man.openbsd.org/ssh.1).

Authorized-key `restrict` with `port-forwarding` and `permitopen` permits the
specific local destination but does not alone deny reverse or Unix socket
forwarding. `permitlisten="none"` is **not** a valid authorized_keys value in
Ubuntu's OpenSSH 9.6 parser; do not confuse it with sshd_config `PermitListen none`.
Verified [OpenSSH 9.6 source](https://github.com/openssh/openssh-portable/blob/V_9_6_P1/auth-options.c)
and [sshd(8)](https://man.openbsd.org/sshd.8). Installer requires effective
local-only TCP forwarding, no streamlocal forwarding, no tunnel devices and
unambiguous host matching; it refuses if `sshd -T -C` cannot prove this. The live
owner must reload and exercise denial, because disk config is not daemon truth.
See [sshd_config(5)](https://man.openbsd.org/sshd_config.5).

User systemd with linger is a prerequisite for host work surviving SSH logout.
Task B2 installer only reads and requires Linger=yes; the owner runs the exact
manual enable-linger command before setup. It never submits a password/sudo. Official [loginctl source
manual](https://github.com/systemd/systemd/blob/v256/man/loginctl.xml) defines this
behavior (pinned documentation v256; actual Ubuntu systemd version is checked by
owner). Prefer a dedicated nonroot host account; this is not hostile-workload
isolation. No provider OAuth/tailnet key minting is needed for adoption, and no
backend of ours is required. Bundle publication is a static reviewed artifact,
not an infrastructure/control-plane service; absent URL/digest blocks setup.

Persistent OpenCode is pinned to current repo OC1 **1.18.32**. Deterministic local
packer checks supplied upstream digest and ELF architecture, then emits full
bundle digest; it does not download or publish. Runtime upgrade/pinning for the
other servers remains the original later design; do not borrow phone-specific
proot scripts as systemd installers without a portable manifest/health proof.

Proof requirements, typed UI errors, restart/deletion semantics, threat limits
and owner options are enumerated in [the implemented contract](byo-host-contract.md).
No real SSH connection, provider resource creation, account creation or secret
inspection was performed in this follow-up.

## Recommendation

Start with **Hetzner Cloud + generic Ubuntu over SSH + OpenCode 1**, then
DigitalOcean and Linode. Build the direct phone-to-provider control plane and a
shared SSH/Tailscale bootstrap executor. There is no server, token broker, billing
account or infrastructure custody on our side. The user contracts with and pays
the provider and owns their tailnet. Generic SSH gives customers of every listed
provider a usable path without waiting for a bespoke adapter.

| Priority | Provider/path | Reason / launch posture |
|---|---|---|
| 1a | Generic Ubuntu SSH adoption | Exercises trust, private bootstrap, resume, pairing and removal without billing APIs; prefer already-tailnet machines |
| 1b | Hetzner Cloud | Small API surface, project-scoped token, low-cost 4GB x86/ARM; coarse token permissions contained in a dedicated project |
| 2 | DigitalOcean | Granular PAT permissions and good catalogs; native OAuth needs a separate proof |
| 3 | Linode/Akamai | Scoped tokens, mature VM/firewall APIs; current interface generation and PAT distribution guidance need checking |
| 4 | Scaleway | IAM application/project scoping, ARM; more price/IP/storage composition |
| 5 | Vultr | Good coverage/prices; new OAuth docs contain a mobile client-secret contradiction and API IP restrictions need proof |
| 6 | AWS Lightsail | Predictable bundles; IAM signing/Identity Center onboarding is heavier |
| 7 | Azure | Native public-client auth is viable; many dependent resources, consent and cost surfaces |
| 8 | Google Cloud | Foreground Android authorization viable; IAM, Play Services and network/cost setup add complexity |
| 9 | OVHcloud Public Cloud | User-owned API credentials viable; dual management/OpenStack surfaces and imminent price changes |
| 10 | AWS EC2 | Strong APIs/idempotency/ARM, but network/storage/IAM/billing complexity exceeds first-slice value |
| 11 | Oracle OCI free tier | Attractive conditional $0; manual signing-key setup, scarce capacity and idle reclamation make a poor default |

This is an implementation-priority judgment, not a provider reliability score.
All create paths are gated until their own current API/auth/cost proof passes.
OAuth is not automatically safer if it requires embedding a confidential secret.
Use local token entry when that is the documented no-backend route; never ship a
shared OAuth secret or build a broker merely to make a login button work.

**Sizing assumption:** remote model APIs, one light coding agent, Ubuntu 24.04 LTS,
2GiB RAM and roughly 20–25GB disk as a *trial floor*, 4GiB recommended for the first
slice. 8GiB+ is a measurement candidate for AI Team/concurrent agents/builds.
These are engineering hypotheses, not proven runtime minima. Do not recommend
512MB/1GB “from” offers for the complete stack or local model inference. The
small-machine costs below are comparable light-agent examples; they are not a
claim that all five stacks fit, or that the globally cheapest SKU was exhaustively
found. Monthly estimates use 730 hours unless a provider publishes a monthly cap.
Tax, model subscriptions/API charges, backups and overage are separate.

## Meaning of the network and credential constraints

No agent or pairing listener is publicly reachable, including during installation.
Only time-limited, source-restricted SSH may be public during bootstrap. The final
state is tailnet-only SSH or SSH disabled, provider ingress closed, agent listeners
on loopback, and tailnet HTTPS/WSS through Tailscale Serve. No Funnel, public relay,
reverse tunnel to us, public readiness webhook or DNS bypass. A public IP for
outbound traffic is not a public service endpoint.

Internet egress is still necessary for provider/identity APIs, software downloads,
Tailscale coordination/DERP and model APIs. Tailscale can relay end-to-end encrypted
packets through DERP without opening inbound service ports; verify that route on
the chosen network. “Nothing over the open internet” is implemented as no public
application access, not an offline machine. [Tailscale firewall guidance](https://tailscale.com/docs/reference/faq/firewall-ports).

Infrastructure credentials persist only in the phone's `flutter_secure_storage`,
scoped `oc.<what>.<profileId>`. Their only network use is authentication directly
to their intended provider/Tailscale API over validated TLS. They never reach the
VPS, our infrastructure, logs, diagnostics or assistant prompts. A one-use
Tailscale *enrollment* credential may transiently reach the VPS through pinned
SSH; the preferred interactive path avoids even this. Agent model-account secrets
are a different trust domain: a server running an agent may need them, through
that agent's explicit login flow. Do not transfer phone provider tokens as a
shortcut or imply phone-only storage for server-side model credentials.

## Provider evidence

Fetched official documentation and public catalogs follow. API versions are
listed independently from documentation dates. “Unverified” means evidence was
not established; it does not mean the provider forbids a feature. Third-party
terms findings describe documented integration routes, not provider certification
or a legal clearance. Gate public distribution on applicable current terms and
registration policies. Never evade quota, funding, consent or app review.

### Hetzner, DigitalOcean, Linode and Vultr

| Provider/API | Native authorization and least privilege | Provisioning surface | Catalog / ARM / observed cost | Limits / launch gate |
|---|---|---|---|---|
| Hetzner Cloud `/v1` | Bearer API token created in user's project. Read or Read & Write only; no resource/action-level scopes documented. Use an otherwise empty project to contain blast radius. OAuth, PKCE, app links, device grant not documented in fetched Cloud API docs; PAT/manual token first. | `GET/POST /servers`, `GET/DELETE /servers/{id}`; actions are asynchronous. `POST /ssh_keys`; `/firewalls` and attachment actions; server create supports public keys, firewall IDs, cloud-init user data. Cloud-config limit 32KiB. | `/locations`, `/datacenters`, `/server_types`, `/images`, `/pricing` (net/gross, account currency/tax, regional). CAX ARM exists. EU CX23 x86 2vCPU/4GB/40GB: **€5.49/$6.49 per month excl IPv4/VAT**; CAX11 ARM 2vCPU/4GB/40GB **€5.99/$6.99**. These are new prices effective 15 June 2026, not older €3–4 figures. | 3,600 requests/hour/project with replenishment and `RateLimit-*`; respect async action state and 429. Prove region availability, quote IP addition, create firewall at provisioning and pin image/architecture. [Cloud API](https://docs.hetzner.cloud/reference/cloud), [June price revision](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) |
| DigitalOcean REST `/v2` | Manual PAT with expiry and CRUD scopes per resource. OAuth docs explicitly offer server authorization-code + client secret, or implicit for native apps. **PKCE/device flow not documented**; do not embed app secret or choose implicit as new design. PAT first; native OAuth gated. | `GET/POST /droplets`; `GET/DELETE /droplets/{id}`; create `user_data`, `ssh_keys`, `tags`; `GET/POST /account/keys`, delete key by ID; `/firewalls`. Precreate firewall targeting unique tag, create Droplet with same tag; verify enforcement, never start agent listeners before local firewall. | `/regions`, `/sizes`, `/images`; size has hourly/monthly USD and region availability. Basic 2GB/1vCPU/50GB **$12/month**; 4GB/2vCPU/80GB **$24/month**. Public CPU options fetched are x86 Intel/AMD; ARM Droplets not verified. | 5,000/hour + 250/minute. Scoped PAT minimum plan: droplet CRUD needed for actions, firewall CRUD, ssh_key read/create/delete, region/size/image reads, tag read/create/delete as used; derive exact dependencies from scope pages. [OAuth](https://docs.digitalocean.com/reference/api/oauth/), [API](https://docs.digitalocean.com/reference/api/reference/droplets/), [prices](https://www.digitalocean.com/pricing/droplets) |
| Linode/Akamai REST `/v4` (avoid beta) | Bearer PAT with category `read_only`/`read_write`, further constrained by user grants/IAM. OAuth docs recommend public client for native apps but describe **implicit**, two-hour access token and no refresh. Confidential code flow requires secret; PKCE/device flow not found in fetched Linode docs. Use locally entered scoped PAT for owner-operated tooling, confirm distribution/authorization policy before broad release. | `GET/POST /linode/instances`, `GET/DELETE /linode/instances/{id}`; create accepts `authorized_keys` and `metadata.user_data` (base64), firewall assignment. `/profile/sshkeys`; `/networking/firewalls` + device/rules endpoints. Metadata-capable image/region required. New Linode-vs-legacy interfaces affect firewall attachment: honor current per-interface schema rather than assuming old `firewall_id` works universally. | `/regions`, `/linode/types`, `/images`; type contains base and regional price data. Public catalog fetched without auth: `g6-standard-1` 2GB/1CPU/50GB **$12/month,$.018/h**; `g6-standard-2` 4GB/2CPU/80GB **$24/month,$.036/h**. No ARM general-purpose instance verified. | Paginated GET 200/minute, other operations 1,600/minute, create 20/15sec; per-user or unauthenticated IP. Native OAuth, minimum scopes (e.g. `linodes:read_write`, `firewall:read_write`, required events/images/account/profile scopes only when actually used) and interface generation need focused feasibility proof. [API/auth](https://techdocs.akamai.com/linode-api/reference/get-started), [catalog](https://api.linode.com/v4/linode/types), [limits](https://techdocs.akamai.com/linode-api/reference/rate-limits) |
| Vultr API `/v2` | API key works directly; legacy docs describe per-user key + source-IP ACL; newer IAM service user supports role/group policies. **New OAuth/PKCE docs dated 21 July 2026 exist.** They claim public/mobile support but PKCE token example still uses client-secret HTTP Basic. Treat secretless native exchange as **unproven**, not unsupported/no OAuth. No device flow found. | `GET/POST /instances`, `GET/DELETE /instances/{id}`; cloud-init `user_data` base64; `/ssh-keys`, `/firewalls`, `/firewalls/{id}/rules`, instance `firewall_group_id`. | `/regions`, `/plans`, `/os`; public `GET /v2/plans` fetched without auth: `vc2-1c-2gb` 2GB/1CPU/55GB **$10/month**; `vc2-2c-4gb` 4GB/2CPU/80GB **$20/month**. Plans supply regional availability + price. Current VX1 official material is x86 AMD; ARM availability not verified. | >30 requests/sec/source IP may return 429. OAuth needs developer root org key, funded account, HTTPS callback, attached policy scope and administrator review before public use. Roaming phones + mandatory legacy source-IP ACL need proof (do not silently allow the entire internet); validate current IAM service-user restrictions. [API SDK](https://github.com/vultr/vultr-csharp), [catalog](https://api.vultr.com/v2/plans), [OAuth PKCE](https://docs.vultr.com/platform/iam/oauth/access-tokens/how-to-use-pkce-with-vultr-oauth) |

### Token-provider auth gates

- “No own backend” works for user-supplied provider PAT/API credentials held on the phone. A shared confidential OAuth secret cannot safely be shipped in the binary. Where native PKCE is unproven, a token-entry path is the no-backend alternative; registration of a public OAuth app and static verified app-link ownership could be acceptable separately, but do not invent a successful secretless flow.
- Vultr public OAuth is new and deserves an explicit feasibility spike: the provider's PKCE article says mobile clients cannot keep secrets, yet requires CLIENT-SECRET in step 3. Ask provider to confirm `token_endpoint_auth_method=none`, app-link callback handling, rotating-refresh behavior and reviews before coding OAuth. Their guide says production consent uses Vultr Console, while curl `/oauth/authorize` examples require the end-user API key; do not implement that example as a user login screen. [Vultr OAuth registration](https://docs.vultr.com/platform/iam/oauth/access-tokens/how-to-integrate-a-third-party-app-with-vultr-oauth)
- Hetzner price-adjustment page supersedes historical pricing; lowest x86 and ARM offers may be capacity limited. ARM cannot be enabled merely because a provider supports it: every selected agent artifact needs the supported architecture and verified pin.
- No OAuth device authorization grant was verified for these four. Document as “not verified/documented”, not a universal claim that the provider forbids it.
- No adapter should require a provider credential on its VPS. SSH bootstrap supplies only bootstrap-specific material over the pinned SSH session; agent provider/model credentials are a separate user-approved server runtime concern.

### Token-provider third-party terms

Hetzner API docs explicitly encourage integrations; current general terms allow granting third parties use of services while customer remains responsible. This supports owner-controlled local automation but is not a special endorsement of the app. [Hetzner terms](https://www.hetzner.com/legal/terms-and-conditions/)

DigitalOcean documents OAuth delegation and its TOS (updated 22 August 2026) makes the user responsible for account activity including agents/third parties and binds use to AUP/service terms. Public API access is documented; no special paid-service resale is proposed. A published OAuth app still needs registration; app review/PKCE policy was not established here. [DigitalOcean terms](https://www.digitalocean.com/legal/terms-of-service-agreement)

Linode documents third-party OAuth clients explicitly. PAT docs say personal use; product-wide authentication UX should be checked against provider guidance rather than assuming every PAT-on-device app is approved. MSA was fetched, but a distinct API-app approval/redistribution term was not located. Maintain user ownership and do not claim blanket third-party certification. [Linode client registration](https://techdocs.akamai.com/linode-api/reference/post-client), [MSA](https://www.akamai.com/legal/msa)

Vultr explicitly documents third-party OAuth and mandatory review for public applications, so this is a concrete registration gate. The generic ToS URL returned 403 during web fetch; record legal terms review as unverified instead of inventing approval. PAT/IAM tooling is officially documented. [Vultr OAuth](https://docs.vultr.com/platform/iam/oauth)

### Token-provider sources

All fetched 2026-09-27; dates below are page dates where present. REST major versions as named above; no authenticated call or mutation was made. Two public read-only catalog API calls were made with Python urllib because the browser could not render useful catalog prices: Catalog evidence is the public endpoint and the dated values above; temporary JSON downloads are not committed.

- H1 [H1 source](https://docs.hetzner.cloud/reference/cloud) — live Cloud API overview/reference: API token project scope, actions, endpoints, pricing, limits.
- H2 [H2 source](https://docs.hetzner.com/cloud/api/getting-started/generating-api-token/) — 2021-12-13: Read versus Read & Write.
- H3 [H3 source](https://docs.hetzner.com/cloud/servers/getting-started/creating-a-server/) — cloud-config and 32KiB limit.
- H4 [H4 source](https://docs.hetzner.com/cloud/servers/faq/) — provider images needed for cloud-init.
- H5 [H5 source](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) — effective 2026-06-15 prices excluding VAT and IPv4.
- H6 [H6 source](https://www.hetzner.com/cloud/cost-optimized/) — CX23 and CAX11 specs; capacity availability warning.
- H7 [H7 source](https://www.hetzner.com/legal/terms-and-conditions/) and [H7 source](https://docs.hetzner.com/cloud/api/faq/) — third-party use/customer responsibility, API integration feedback.
- D1 [D1 source](https://docs.digitalocean.com/reference/api/oauth/) — native implicit versus confidential code flow; client_secret required for exchange; no PKCE text.
- D2 [D2 source](https://docs.digitalocean.com/reference/api/create-personal-access-token/) — last verified 2026-07-13; token expiry/custom scopes.
- D3 [D3 source](https://docs.digitalocean.com/reference/api/scopes/) — last verified 2026-08-07; scopes/dependencies.
- D4 [D4 source](https://docs.digitalocean.com/reference/api/reference/droplets/) — generated 2026-09-23; CRUD, user-data, rate limits.
- D5 [D5 source](https://docs.digitalocean.com/reference/api/reference/sizes/) and [D5 source](https://docs.digitalocean.com/reference/api/reference/regions/) — catalog/prices.
- D6 [D6 source](https://docs.digitalocean.com/reference/api/reference/ssh-keys/) — upload/read/delete.
- D7 [D7 source](https://docs.digitalocean.com/reference/api/reference/firewalls/) — rules/attachments.
- D8 [D8 source](https://www.digitalocean.com/pricing/droplets) — basic $12/$24; billing effective 2026-01-01 per second with 60-second/$0.01 minimum.
- D9 [D9 source](https://www.digitalocean.com/legal/terms-of-service-agreement) — updated 2026-08-22.
- L1 [L1 source](https://techdocs.akamai.com/linode-api/reference/get-started) — PAT scopes; native/public implicit two-hour tokens; confidential OAuth secret.
- L2 [L2 source](https://techdocs.akamai.com/linode-api/reference/post-linode-instance) — create options including SSH, metadata, interface/firewall caveats.
- L3 [L3 source](https://techdocs.akamai.com/linode-api/reference/post-firewalls) — firewall API.
- L4 [L4 source](https://techdocs.akamai.com/linode-api/reference/rate-limits) — current operation-specific limits.
- L5 [L5 source](https://techdocs.akamai.com/linode-api/reference/api-summary) — endpoint summary (search fetch succeeded, later open failed).
- L6 [L6 source](https://api.linode.com/v4/linode/types) — direct unauthenticated read-only JSON; current observed $12/$24.
- L7 [L7 source](https://techdocs.akamai.com/linode-api/reference/post-client) — public/private app registration.
- L8 [L8 source](https://www.akamai.com/cloud/pricing) — pricing/caps/regional variation; redirected from linode.com/pricing.
- L9 [L9 source](https://www.akamai.com/legal/msa) — redirected from linode.com/legal-msa.
- V1 [V1 source](https://github.com/vultr/vultr-csharp) — official SDK endpoint table (instances, SSH keys, firewalls, plans, regions).
- V2 [V2 source](https://docs.vultr.com/how-to-deploy-a-vultr-server-with-cloudinit-userdata) — base64 user-data create/retrieve.
- V3 [V3 source](https://docs.vultr.com/support/platform/api/what-rate-limits-apply-to-the-vultr-api) — updated 2026-04-15, 30/sec/source IP.
- V4 [V4 source](https://docs.vultr.com/support/platform/users/how-can-i-manage-api-access-for-users) — updated 2025-12-16, legacy source-IP ACL required.
- V5 [V5 source](https://api.vultr.com/v2/plans) — direct unauthenticated read-only JSON; current $10/$20 monthly catalog.
- V6 [V6 source](https://discover.vultr.com/vx1-cloud-compute-deploy) — x86 AMD hardware, not ARM proof.
- V7 [V7 source](https://docs.vultr.com/platform/iam/oauth) — third-party apps, scoped tokens, lifecycle review.
- V8 [V8 source](https://docs.vultr.com/platform/iam/oauth/access-tokens/how-to-use-pkce-with-vultr-oauth) — 2026-07-21; native claim conflicts with secret in exchange example.
- V9 [V9 source](https://docs.vultr.com/platform/iam/oauth/access-tokens/how-to-integrate-a-third-party-app-with-vultr-oauth) — 2026-07-21; funded account/HTTPS/review and refresh-token rotation.
- V10 [V10 source](https://docs.vultr.com/platform/iam/service-user/how-to-create-a-service-user) — 2026-06-01; API-only service users with roles/groups.

### AWS, OCI, Google and Azure

| Provider | Direct-phone authentication and least privilege | API coverage and catalogs | ARM and illustrative cost | Limits / recommendation |
|---|---|---|---|---|
| AWS Lightsail | AWS SigV4 with user-owned restricted IAM access key + secret, preferably expiring STS credentials. No Lightsail consumer OAuth/PAT. IAM Identity Center supports public-client PKCE/device grant, then `GetRoleCredentials`; requires the user's configured Identity Center and permission set, not arbitrary AWS-console login. IAM action/resource/tag conditions; never root keys. | API **2016-11-28**: `CreateInstances`, `GetInstances`, `DeleteInstance`; `userData`; `PutInstancePublicPorts` replaces complete inbound port set; `ImportKeyPair`; `GetRegions`, `GetBlueprints`, `GetBundles` (includes monthly price). | Ordinary Lightsail ARM option not established by fetched catalog docs: treat unsupported until catalog/blueprint architecture proof. 2GB Linux/60GB bundle **$12/month with IPv4**, **$10 IPv6-only**; IPv6-only bootstrap/download compatibility must be proven. | `GetInstances` token bucket 20, refill 5/s; action-specific limits and dynamic limits for static-IP churn. Later adapter: simpler quote than EC2, heavier auth than token VPS vendors. |
| AWS EC2 | Same AWS signing/STS/Identity Center path. Fine-grained IAM and tag/resource conditions; launch dependencies include image, subnet, security group, key and volume actions. No service IAM role required on VM. | Query API **2016-11-15**: `RunInstances` with `ClientToken` + `UserData`, `DescribeInstances`, `TerminateInstances`; security-group APIs; `ImportKeyPair`; `DescribeRegions`, `DescribeInstanceTypes`/offerings and images; Price List Query/Bulk APIs separate from EC2. | Graviton ARM e.g. `t4g.small` 2GiB. Official x86 `t3a.small` 2GiB example: **$0.0188/h = $13.72/730h** in N. Virginia; +20GB gp3 **assuming** the official example rate $0.08/GB-month +IPv4 $0.005/h ≈ **$18.97/month illustrative total** before traffic/CPU-credit overage; confirm regional EBS SKU before create. ARM t4g.small exact quote remains unresolved. [T3 pricing](https://aws.amazon.com/ec2/instance-types/t3/), [EBS](https://aws.amazon.com/ebs/pricing/), [IPv4](https://aws.amazon.com/vpc/pricing/). | Current documented `RunInstances` request bucket **5**, refill **2/s**, plus resource bucket **1000**, refill **2/s**; account overrides possible. Later than Lightsail because network/volume/cost surface is larger. |
| Oracle OCI | Per-user RSA API signing key (>=2048-bit PEM), tenancy/user OCIDs + fingerprint; generate on phone, user uploads public half in OCI console. Compartment-scoped IAM group policy, narrow resource types. OCI “auth token” is not a general Compute API bearer token; identity-domain OAuth is not proof of core Compute access. No turnkey core-Compute native OAuth/device login established. | Core API **20160918**: `LaunchInstance`, `ListInstances`, `TerminateInstance`; `metadata.user_data` (base64) and `ssh_authorized_keys`; NSG/security-list/VCN APIs; `ListShapes`, image/availability-domain/region catalogs. Public Oracle cost-estimator products API exists. | A1 Flex ARM; **$0 only within eligibility/remaining limits**. Current fetched Always Free page states **1,500 OCPU-hours + 9,000 GB-hours/month ≈ 2 OCPUs/12GB**, not old 4/24 advice; 200GB total free boot/block allowance in home region. One 1OCPU/2GB A1 is within those CPU/RAM ceilings when available. | 429 `TooManyRequests`; no universal published core limit established. Home-region capacity can block creation; idle machines can be reclaimed after 7-day low utilization. Do not promise free always-on reliability or spin to defeat reclamation. Later/experimental. |
| Google Compute Engine | Android `AuthorizationClient` obtains scoped local access token and reacquires on return/resume; request Compute OAuth scope, then project IAM limits actual permissions. Native PKCE exists for supported installed-app platforms, but **custom URI schemes no longer supported on Android and mobile loopback deprecated**. Android offline server-auth-code flow calls for a backend; do not use it. Public app registration/consent verification required as applicable. Service-account JSON is a possible user-supplied signing alternative but avoid broad long-lived export by default. | REST **v1** `instances.insert/list/delete`; `requestId` UUID for dedup; metadata startup scripts, image-validated cloud-init; `firewalls`, network/subnetwork APIs; per-instance SSH metadata or OS Login key APIs; `regions`, `zones`, `machineTypes`, `images`; Cloud Billing Catalog `services.skus.list`. | ARM Tau T2A/C4A catalog. 2GiB `e2-small` Iowa **$0.016752855/h ≈ $12.23/mo compute**. +20GiB standard persistent disk at paid rate ≈ **$0.80**, +IPv4 **$3.65** = **$16.68/mo** before traffic (eligible free disk/IP hours can lower actual cost; don't assume). | Per-project, per-minute method-group quotas; 403 `rateLimitExceeded`, resets aligned to minute. Actual limits queried from project, not one universal requests/min figure. Later; foreground native auth proof first. |
| Azure | Entra app registration, native public-client delegated authorization code+PKCE or device grant; no shipped secret, no our backend. ARM delegated token + Azure RBAC; custom role at dedicated resource group. Multi-tenant registration/consent and tenant policy may block some accounts. No Azure VM PAT. | Compute REST **2026-03-01** VM create/update/list/delete, `customData` cloud-init, `osProfile.linuxConfiguration.ssh.publicKeys`; separate public-key resource optional. Network NSG REST **2025-09-01**; subscription locations, Resource SKUs **2021-07-01**. Public Retail Prices API **2023-01-01-preview** available, unauthenticated; LRO polling. | ARM Bpsv2 and newer catalog offerings. Verified public retail GET: Linux B1ms (2GiB) East US **$0.0207/h = $15.11/mo compute**, effective 2025-10-01. Add selected OS disk, outbound path/public IP and traffic from quote before create; this is not a full or absolute lowest quote. | ARM token buckets: read bucket capacity 250, refill 25 tokens/second; separate write and delete buckets capacity 200 each, refill 10 tokens/second; per-subscription/service-principal plus provider-level limits, smaller trial limits possible. Later; good native auth but many dependent resources. |

### AWS evidence and implementation notes

- [Lightsail API](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/Welcome.html), [create-instances, fetched CLI reference 2.37.4](https://docs.aws.amazon.com/cli/latest/reference/lightsail/create-instances.html), [GetBundles](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/API_GetBundles.html), [PutInstancePublicPorts](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/API_PutInstancePublicPorts.html), [ImportKeyPair](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/API_ImportKeyPair.html). ImportKeyPair currently documents base64 **ssh-rsa** public key; do not assume every provider accepts the app's preferred Ed25519 key type.
- [Lightsail pricing](https://aws.amazon.com/lightsail/pricing/) supports the two 2GB prices above. IPv6-only is a network compatibility option, not equivalent to removing public reachability: firewall still denies all public inbound services. Outbound connectivity for packages, Tailscale control/DERP and model APIs remains necessary.
- [Public client registration](https://docs.aws.amazon.com/singlesignon/latest/OIDCAPIReference/API_RegisterClient.html) supports PKCE/device/refresh grants; [GetRoleCredentials](https://docs.aws.amazon.com/singlesignon/latest/PortalAPIReference/API_GetRoleCredentials.html) returns account/role credentials. A dynamically issued per-install registration credential is not a shared secret embedded in the APK. Gate: demonstrate Android browser/device flow, account/role selection and SigV4 without a broker. [STS temporary credentials](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp_request.html) provide the fallback, with expiry leading to `needsAuthorization`, not restart/create.
- [RunInstances](https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_RunInstances.html) includes idempotency, user-data, network/security and storage configuration; [Price List APIs](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/price-changes.html) are separate. Always set `ClientToken`, persist request identity before sending, and reconstruct by managed tags if transport response is lost. Pin an Ubuntu AMI resolved from trusted publisher metadata; never blindly use a label match.
- [EC2 ARM T4g](https://aws.amazon.com/ec2/instance-types/t4/) is confirmed; [EC2 pricing](https://aws.amazon.com/ec2/pricing/on-demand/) does not expose the sought SKU rate in the fetched text, so its exact ARM rate remains a quote gate. The coordinator additionally fetched official T3/EBS/VPC price pages for the x86 2GiB example in the table; it is not an assertion of the cheapest eligible EC2 SKU.
- [Lightsail throttling](https://docs.aws.amazon.com/lightsail/latest/userguide/lightsail-api-throttling.html) explicitly covers third-party applications. [EC2 throttling](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-throttling.html) gives per-action and resource buckets; avoid old remembered generic values.
- [AWS Customer Agreement, last updated 2026-08-14](https://aws.amazon.com/agreement/) makes customer responsible for account/agent activity and security; restricts key transfer/resale and quota evasion. Our local client uses credentials on the customer's behalf, never transfers them to us and never resells infrastructure. This architecture fits documented API usage; it is not a provider certification or a blanket legal clearance.

### Oracle evidence and implementation notes

- [Required keys/OCIDs](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm); [common IAM policies](https://docs.oracle.com/en-us/iaas/Content/Identity/Concepts/commonpolicies.htm); [network access control, updated 2026-08-19](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/accesscontrol.htm). Dedicated compartment, dedicated user/group, only instance/volume/network operations needed; do not ask for tenancy admin. Network policy can use individual resource types instead of broad `virtual-network-family`.
- [Compute client reference, SDK 2.187.0](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/core/client/oci.core.ComputeClient.html) and [network client](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/core/client/oci.core.VirtualNetworkClient.html) provide readable official operation references when the Core REST single-page UI does not render. Production Dart implements REST signatures, not Python. `opc-retry-token` where supported, durable resource IDs and reconciliation remain required.
- [Instance metadata](https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/gettingmetadata.htm) explicitly lists both user data and authorized public SSH keys; [metadata updates](https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/updatinginstancemetada.htm) says those reserved fields are launch-only. Never insert Tailscale/provider/runtime secrets there.
- [Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm): current allocation differs from historic guides; capacity, quota and idle-reclamation caveats are part of the UI's cost/reliability disclosure. The page itself mixes 47GB/minimum and 50GB boot-volume statements; use current image/launch schema minimum and budget **50GB**, not a hardcoded 47GB assumption.
- [Cost estimator API](https://docs.oracle.com/en-us/iaas/Content/Billing/Tasks/signingup_topic-Estimating_Costs.htm) documents public `https://apexapps.oracle.com/pls/apex/cetools/api/v1/products/`. Do not confuse this with tenancy billing/actual cost or infer free allowance remaining from a list price.
- [REST throttling and errors](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/usingapi.htm), [429 errors](https://docs.oracle.com/en-us/iaas/Content/API/References/apierrors.htm), [cloud contracts](https://www.oracle.com/contracts/cloud-services/). Customer contract varies by country/order. No special approval requirement for ordinary documented local API clients established; no claim that all third-party usage is preapproved.

### Google evidence and implementation notes

- [Android authorization guide, updated 2025-10-27](https://developer.android.com/identity/authorization) explicitly supports repeated on-device `authorize()` to reacquire access tokens after consent. It describes server-side offline token exchange separately. Native Google AuthorizationClient/Play Services is a platform dependency to prove for this Flutter app; return typed auth actions to UI, never raw tokens. Devices without the supported native route use generic SSH, not an undocumented redirect trick.
- [Installed-app OAuth](https://developers.google.com/identity/protocols/oauth2/native-app) documents PKCE and client registration but explicitly excludes Android custom-scheme and mobile loopback assumptions; device flow is aimed at limited-input devices, not the Android fallback. [Compute authentication](https://docs.cloud.google.com/compute/docs/authentication) confirms REST OAuth paths. OAuth scope (`compute` or `cloud-platform`) is not resource least privilege: dedicate a project and reduce IAM permissions.
- [REST v1 catalog](https://docs.cloud.google.com/compute/docs/reference/rest/v1) includes create/list/delete, regions/zones/machine types/firewalls; [instances.insert](https://docs.cloud.google.com/compute/docs/reference/rest/v1/instances/insert) documents `requestId` dedup. Use per-instance SSH public keys with project-wide SSH blocked where suitable. Startup metadata is a secret-free script; cloud-init compatibility must be verified on the chosen Ubuntu image rather than assumed for all Google images.
- [General purpose prices](https://cloud.google.com/products/compute/pricing/general-purpose), [disk prices](https://cloud.google.com/compute/disks-image-pricing), [IPv4/network prices](https://cloud.google.com/vpc/network-pricing) underpin the example; [Billing Catalog](https://docs.cloud.google.com/billing/v1/how-tos/catalog-api) documents programmatic price discovery. Prevent public `default-allow-ssh` rules from inheriting into the instance: dedicated VPC/firewall or a tested deny-priority plan. No public IP is needed for incoming agents; if outbound NAT chosen, disclose its cost too.
- [ARM machine availability](https://docs.cloud.google.com/compute/docs/general-purpose-machines), [rate quotas](https://docs.cloud.google.com/compute/api-quota), [Cloud terms, current page last modified 2026-09-02](https://cloud.google.com/terms), [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy). Public distributed OAuth client needs registered identity/consent configuration and applicable verification; privacy disclosure must match local-only storage. No our backend is required for the foreground authorization design.

### Azure evidence and implementation notes

- [Azure REST auth overview](https://learn.microsoft.com/en-us/rest/api/gettingstarted/) expressly distinguishes native public clients from confidential web clients; [PKCE code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow), [device grant](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code). Gate app registration, supported Android redirect/broker, tenant consent and ARM audience; don't promise that an arbitrary universal app-link is supported without registration proof. [Custom RBAC](https://learn.microsoft.com/en-us/azure/role-based-access-control/custom-roles) supports least privilege in dedicated resource group. Delegated `user_impersonation` is not an action-level permission boundary; RBAC is.
- [VM API](https://learn.microsoft.com/en-us/rest/api/compute/virtual-machines?view=rest-compute-2026-03-01), [create/update](https://learn.microsoft.com/en-us/rest/api/compute/virtual-machines/create-or-update?view=rest-compute-2026-03-01), [cloud-init](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/using-cloud-init), [SSH public-key resources](https://learn.microsoft.com/en-us/rest/api/compute/ssh-public-keys?view=rest-compute-2026-03-01), [NSGs](https://learn.microsoft.com/en-us/rest/api/virtualnetwork/network-security-groups/create-or-update?view=rest-virtualnetwork-2025-09-01), [Resource SKUs](https://learn.microsoft.com/en-us/rest/api/compute/resource-skus/list?view=rest-compute-2021-07-01). Use deterministic resource IDs and LRO reconciliation, never unconditional recreation. Deleting the VM alone may leave disk/NIC/IP resources; ledger and teardown enumerate every owned dependency.
- [Retail API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices) is unauthenticated. Read-only query actually fetched in this research: `https://prices.azure.com/api/retail/prices?%24filter=armRegionName+eq+%27eastus%27+and+armSkuName+eq+%27Standard_B1ms%27+and+priceType+eq+%27Consumption%27`; result Linux `Virtual Machines BS Series`, B1ms, USD 0.0207/1 Hour, effective 2025-10-01; Windows excluded. This price is a baseline, not a full deployable quote; use current SKU and all ancillary price rows. [ARM Bpsv2 sizes](https://learn.microsoft.com/en-us/azure/virtual-machines/sizes/general-purpose/bpsv2-series) proves ARM availability.
- [ARM throttling](https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/request-limits-and-throttling) plus per-provider limits require `Retry-After` handling. [Azure legal terms gateway](https://azure.microsoft.com/en-us/support/legal/) points to applicable customer/product/marketplace agreements. No separate third-party API certification condition established from fetched docs; organization policies and app consent remain real gates.


### Scaleway, OVH and generic SSH

| Provider / relative recommendation | Mobile authentication and limitation | Server / provisioning API | Firewall / SSH / catalog | Price / ARM | Rate limits / third-party use gate |
| --- | --- | --- | --- | --- | --- |
| Scaleway: second wave, after Hetzner/DO/generic | User-owned IAM application API key; `X-Auth-Token` secret. IAM policy permits project/permission-set scoping and key expiry. No public native PKCE/device-flow evidence found; do not label it OAuth login. | Instance v1 zonal `servers` POST/GET; `servers/{id}` DELETE; cloud-init supported under `cloud-init` user-data key (plain text only). | Instance security-group/rule APIs; SSH public keys via IAM `/iam/v1alpha1/ssh-keys`; server types via `/instance/v1/zones/{zone}/products/servers`, images via Marketplace, available zones per API; Product Catalog API exposes locations/properties/prices. | Public PAR1 catalog GET verified DEV1-S 2 vCPU/2 GiB €0.008976/h ≈€6.55248/730h, DEV1-M 3 vCPU/4 GiB €0.020196/h ≈€14.74308; **storage and IPv4 extra**; IPv6/egress included. BASIC2-A2C-4G arm64 2 vCPU/4 GiB €0.023/h ≈€16.79, storage extra. | General Instance request-rate number not located (do not reuse AI-model rate limits). Handle 429 + bounded jitter/backoff. API automation explicitly documented; production third-party terms/app review not established: review current account/service terms before enabling. |
| OVHcloud Public Cloud: later, OpenStack complexity | User-created service account `clientId`/`clientSecret` + IAM policy; client-credentials OAuth works directly phone→OVH when secret belongs to user. Legacy application key + application secret + consumer key with verb/path rights also supported. Auth-code advertised for third-party apps, but public PKCE/device flow/secretless native registration **unproven**; do not embed vendor secret. | OVH v1 `/cloud/project/{project}/instance` GET/POST; item GET/DELETE; or OpenStack Nova API. Cloud-init `userData` / OpenStack user-data supported. | Neutron security groups/port-security; OVH SSH key upload `/cloud/project/{project}/sshkey`; regions/flavors/images catalogs; `/order/catalog/public/cloud` price catalog (account-country mapping matters). | d2-2 1 vCPU/2GB/25GB: listed $0.0123/h (730h ≈$8.98) or **distinct monthly rate** $6.74; d2-4 2 vCPU/4GB/50GB $0.0244/h (~$17.81/730h) or $13.50 monthly. IPv4 unbundles **1 October 2026**, so do not lock a September all-in quote. ARM not verified in current Public Cloud catalog; use x86 eligibility only until confirmed. | OVH OpenStack Keystone 60 requests/min/user; Nova/Neutron/Glance/Cinder 20 requests/sec/project. OVH management API separately limited; numeric limit unproven. OVH explicitly documents customer-account delegation and third-party OAuth applications; verify final auth arrangement and regional terms. |
| Generic Ubuntu over SSH: first slice alongside first provider | User supplies host, SSH public-key login identity/private key or password (prefer key), independently obtained host fingerprint, sudo capability. No provider credential and no OAuth. | Adopt only: SSH runs the same reconciled bootstrap plan; no provider create/list/delete guarantees. Prefer already-tailnet Ubuntu 24.04 LTS amd64; ARM gated per component artifact compatibility. | Host UFW/nft rules; external provider firewall remains an explicit owner check; no catalog, price, region or rate-limit API. Never assume deleting a profile deletes a rented machine. | Existing owner bill; no reliable app estimate—owner enters cost/acknowledges unknown. Runtime architecture comes from preflight. | User/provider terms continue to apply; fail closed on changed SSH host key or no independently verified initial fingerprint. Never `StrictHostKeyChecking=no`, auto-accept unknown host, or equate uploaded **client** key fingerprint with server host identity. |

### Scaleway sources (fetched)

- [API auth and version](https://www.scaleway.com/en/developers/api): published docs build **v1.8872.0**, secret header + inherited IAM rights; explicitly designed for resource automation.
- [Instance API v1](https://www.scaleway.com/en/developers/api/instance/v1): create/list/delete and security groups, images, types.
- [IAM API](https://www.scaleway.com/en/developers/api/iam): IAM applications, policies, expiring API keys, SSH keys.
- [cloud-init](https://www.scaleway.com/en/docs/instances/how-to/use-cloud-init/), reviewed **2025-12-16**: metadata-backed plain-text userdata; `ssh-host-fingerprints` key is documented, making authenticated API retrieval a candidate **separate feasibility proof**, not yet a generic host-trust guarantee.
- [CLI Product Catalog API](https://www.scaleway.com/en/developers/cli/product-catalog): catalog includes description, locations, prices/properties; filters project-specific availability separately.
- [Virtual Instance price list](https://www.scaleway.com/en/pricing/virtual-instances/): exclusions for storage/public IPv4; DEV1 and PLAY2 prices.
- [Public PAR1 server-type catalog](https://api.scaleway.com/instance/v1/zones/fr-par-1/products/servers): **actual unauthenticated GET via Python urllib 2026-09-27** confirmed above prices, RAM, architecture. No credentials transmitted. `web.run` could not render it.
- [ARM instances](https://www.scaleway.com/en/docs/instances/reference-content/general-purpose/): BASIC2-A arm64 Ampere; PAR1/PAR2/AMS1. [Service terms](https://www-uploads.scaleway.com/General_Terms_of_Services_v17072024_45d4879c08.pdf) fetched; older document, **not proof of current app-distribution approval**.

### OVH sources (fetched)

- [Service accounts](https://docs.ovhcloud.com/en/guides/manage-and-operate/api/manage-service-account) and [API OAuth authentication](https://docs.ovhcloud.com/en/guides/account-and-service-management/account-information/authenticate-api-with-service-account): client credentials; IAM actions, third-party authorization-code flow mentioned but not native-mobile PKCE proof.
- [First API steps](https://support.us.ovhcloud.com/hc/en-us/articles/360018130839-First-steps-with-the-OVHcloud-API): declared application and method/path credential rights. [Delegated customer accounts](https://help.ovhcloud.com/csm/de-api-api-rights-delegation?id=kb_article_view&sysparm_article=KB0068603): authorization/redirect flow; not a public-client safety claim.
- [OVH public cloud API schema](https://api.eu.ovhcloud.com/1.0/cloud.json) and [order schema](https://api.eu.ovhcloud.com/1.0/order.json): **unauthenticated GET via urllib 2026-09-27**, confirmed listed instance/region/flavor/SSH/catalog paths; `web.run` rendering failed. API family `/1.0`.
- [OpenStack start](https://docs.ovhcloud.com/en/guides/public-cloud/compute/starting-with-nova), updated **2026-07-08**. [cloud-init](https://docs.ovhcloud.com/de/guides/public-cloud/compute/launching-script-when-creating-instance), updated **2026-07-30**, body English.
- [OpenStack service accounts official repository](https://github.com/ovh/docs/blob/develop/pages/manage_and_operate/iam/authenticate-api-openstack-with-service-account/guide.en-ie.md) includes compute/network security IAM roles; mapping must be tested before adapter.
- [API rate limits](https://docs.ovhcloud.com/en/guides/public-cloud/cross-functional/api-rate-limits), updated **2023-06-23**: limits above, cache Keystone token + exponential jitter recommended.
- [Price list](https://www.ovhcloud.com/en/public-cloud/prices/) and [1 October 2026 change](https://blog.ovhcloud.com/en/posts/public-cloud-pricing-update-october-2026/): do not confuse monthly billing rate with hourly ×730, and re-quote IPv4/storage.
- [Infrastructure automation](https://www.ovhcloud.com/en/public-cloud/infrastructure-automation/) encourages OpenStack/OVH API infrastructure automation. [Public-cloud terms](https://www.ovh.com/world/support/termsofservice/specific_conditions_public_cloud.pdf) fetched; older PDF not a complete current contractual review.


## Tailscale enrollment without our backend

| Option | Phone and server actions | Recommendation / gate |
|---|---|---|
| Interactive login | Over pinned SSH, run `tailscale up`; privately return its login URL to phone; user authenticates directly to Tailscale; poll `tailscale status --json` through SSH | **First slice**. No Tailscale API credential required; explicit device/grant/HTTPS approval may still be necessary |
| Manually supplied auth key | Owner generates a short-lived, non-reusable auth key in console; phone vault transfers it over pinned SSH into root-only tmpfs, enrolls, then removes it | Useful automation fallback. Prefer an existing least-privilege tag; non-ephemeral for a persistent VPS; preauthorization is explicit |
| User-owned OAuth client | Owner/admin creates narrowly scoped client; phone stores their secret, exchanges at `/api/v2/oauth/token`, creates key at `POST /api/v2/tailnet/{tailnet}/keys` | Advanced option, no vendor backend needed. Request `auth_keys` with selected tag; separate device read/delete rights only if used |
| New OAuth app | Current alpha auth-code flow, same-tailnet authorizing users and client-secret exchange | **Not a proven shared native login solution**. Do not embed a secret or build a broker to bypass this gate |

These are separate mechanisms: an auth key enrolls a device; an OAuth client
mints scoped API tokens; interactive user login enrolls through a browser. The
current OAuth-app one-time provisioning scope `auth_keys:create:once` has a
one-hour single-use token without refresh. Public-client PKCE and cross-customer
tailnet registration are not established by that feature. [OAuth clients](https://tailscale.com/docs/features/oauth-clients),
[auth keys](https://tailscale.com/docs/features/access-control/auth-keys),
[OAuth apps](https://tailscale.com/docs/features/oauth-apps),
[one-time provisioning](https://tailscale.com/docs/features/oauth-apps/device-provisioning)
(all these pages validated 2026-06-30), [scope reference](https://tailscale.com/docs/reference/trust-credentials).

Do not request policy-write or `all` just to enroll. Offer a reviewed grant example
for the owner to apply: their phone/user may reach the VPS tag only on selected
HTTPS/WSS and administrative SSH ports. An existing default allow-all tailnet is
not a least-privilege configuration. Require user confirmation of the selected
tailnet and a successful phone-to-node test, plus device approval/Tailnet Lock
signing where enabled. No automatic replacement of an existing tailnet policy.
[Tailscale grants](https://tailscale.com/docs/features/access-control/grants).

Auth-key expiry/revocation does not evict an enrolled node; teardown separately
removes that node. Persistent nodes must not use ephemeral enrollment merely to
avoid a cleanup implementation. Keep enrollment and node expiry visible and offer
re-enrollment over trusted console/SSH; do not silently disable the owner's expiry
policy. [Server setup](https://tailscale.com/docs/how-to/set-up-servers),
[auth keys](https://tailscale.com/docs/features/access-control/auth-keys).

## Bootstrap: proposed exact sequence

This is a design, not a tested install script. Ubuntu 24.04 LTS amd64 is the first
qualification target; arm64 requires a complete matching artifact manifest and
proof. No secrets are embedded in cloud-init, its generated script, metadata,
service unit text, process arguments or readiness output. cloud-init's local
permissions do not make provider metadata or serial logs an appropriate vault.
[cloud-init 26.2 security guidance](https://docs.cloud-init.io/en/latest/explanation/security.html).

1. **Preflight and quote on phone.** Verify secure storage, Tailscale app/network,
   account access, selected offer/image, provider firewall support, available quota,
   and manifest completeness. Allocate durable profile/job/operation IDs and a
   phone-generated SSH client key. Default to a dedicated provider project. Show
   all mandatory cost components before the exact create confirmation. No
   provider secret is copied to the machine.
2. **Create containment first.** Create a managed firewall with only TCP22 from
   an owner-supplied, narrow bootstrap source CIDR (IPv4 and/or IPv6); attach it
   in the create request or use the provider's proven pre-attached mechanism.
   Do not infer a stable mobile egress address from Tailscale addresses or use
   `0.0.0.0/0`/`::/0` as an automatic fallback. A network change requires an explicit
   reviewed rule update. All agent ports are denied from creation. Record every
   auxiliary firewall/key/IP ID and reconcile lost responses.
3. **Secret-free cloud-init.** Supply job/manifest IDs, the public client key,
   a bootstrap account, SSH password/root-login disablement, root-owned runner
   directories and a durable public-SSH expiry timer. Configure UFW/nft to deny
   inbound IPv4/IPv6 except the narrow bootstrap rule; ensure no inherited cloud
   rule opens other ports. Install only signed OS prerequisites and the verified
   Tailscale package. Do not start agents or invoke an auth key from user-data.
   Use the official Tailscale apt signing-key/repository configuration with a
   reviewed signing-key fingerprint and exact package version, or an exact
   artifact + bundled SHA-256. The current stable package index fetched here
   advertises **1.102.4** and checksum sidecars; this is a qualification candidate,
   not a new repository pin. [Tailscale package index](https://pkgs.tailscale.com/stable/).
4. **Prove SSH host identity.** Provider-uploaded public key authenticates the
   phone, not the server. First slice asks the owner to compare/paste the server
   Ed25519 public-key SHA-256 from the authenticated provider console (or an
   independently trusted existing administrator). Persist the complete key,
   fingerprint, resource ID and provenance. Refuse changes. A future documented,
   authenticated API/console host-key retrieval can automate this; cloud-init
   `ssh_publish_hostkeys` is datasource-dependent, not universal attestation.
   Never ship private host keys through metadata, auto-accept unknown keys or
   use `StrictHostKeyChecking=no`. [cloud-init SSH module](https://docs.cloud-init.io/en/latest/reference/modules.html#ssh),
   [OpenSSH host-key checking](https://man.openbsd.org/ssh_config#StrictHostKeyChecking).
5. **Start durable host job.** Over the verified SSH channel, install the
   application-bundled root-owned runner and reviewed manifest, then trigger a
   systemd oneshot job. `flock`, atomic journal and component checks make repeat
   attachment safe. Service work outlives a broken SSH connection. Status contains
   only enumerated stage IDs, counters, versions and fixed safe errors. Remote
   raw stdout is not the UI or diagnostic contract.
6. **Enroll privately.** Run interactive `tailscale up` in a separate sensitive
   channel; deliver the login URL only to `openExternalLink`. Alternatively pass a
   one-use key via SFTP/stdin to root-only tmpfs; use the selected CLI's verified
   file-input support, never interpolation into argv. Suppress shell tracing and
   redact outputs structurally. Delete the enrollment file even after failure;
   do not put it in the remote job JSON.
7. **Move administration to tailnet.** Read Self node ID, DNS name and tailnet
   addresses through the pinned SSH channel, then establish a second phone→tailnet
   SSH session with the **same pinned host key**. Check identity, device approval,
   grants and phone reachability. Restrict sshd/listener/firewall to tailnet or
   disable public-interface access; remove public TCP22 from provider firewall and
   verify. The host timer closes its public SSH rule even if the app disappears;
   the phone removes the provider rule on return. A VM has no provider token to
   revoke that rule itself. After deadline, recovery is console/tailnet/manual
   authorization, never automatic public reopening.
8. **Install selected runtimes.** Verify every downloaded byte against a reviewed
   manifest before execution, create non-login runtime user(s), versioned binary
   directories, private state directories and `/srv/oc/projects`. No agents run
   as root or obtain sudo/cloud instance roles. Separate writable homes for
   OC1/OC2/Codex/Paseo; share only explicitly selected project paths. Run install
   hooks unprivileged where possible; root only performs audited OS/service
   operations. See the pin/gap table below.
9. **Persist services and private TLS.** System-level units use `User=oc-agent`
   (or a dedicated per-runtime user), `WorkingDirectory`, `UMask=0077`, a private
   credential file or systemd credential, explicit loopback bind, restart policy,
   `NoNewPrivileges`, bounded restart/log/storage policy and narrowly writable
   directories. Qualify sandboxing against actual terminal/build features rather
   than blindly blocking every write/exec. Keep agent processes unable to read
   root Tailscale state, bootstrap files or other runtime credentials. Disable
   unnecessary image management agents/instance roles where feasible; block
   workload metadata access without breaking the documented boot/network agent.
   Do not assume IMDSv2 alone makes secrets in metadata safe.
10. **Expose to tailnet and verify.** OC1 default route is a reviewed equivalent
    of `tailscale serve --bg --https=443 http://127.0.0.1:4096`. Require tailnet
    HTTPS enablement; validate certificate hostname normally. Never `Funnel`,
    `--https+insecure`, cleartext remote fallback, or wildcard agent bind. Audit
    effective Serve/Funnel configuration and reboot persistence; test SSE, WSS
    upgrade, auth headers, timeouts and large messages for each added runtime.
    Separate ports/origins avoid unsupported path-prefix URLs. [Serve](https://tailscale.com/docs/features/tailscale-serve)
    (validated 2026-01-20), [Serve CLI](https://tailscale.com/docs/reference/tailscale-cli/serve).
11. **Pair and connect.** Generate strong server credentials on the VPS, save
    with mode 0600 and suppress startup credential output. Create an expiring,
    job-bound pairing receipt; retrieve it through authenticated **tailnet SSH**,
    not a public callback. The phone durably saves the credential/profile,
    authenticates against the expected protocol, then ACKs. A lost ACK must return
    the same receipt to the same authenticated request, not lose the only password.
    Delete the temporary receipt after ACK/expiry. Mark ready only after private
    connectivity, public closure and profile persistence. Existing OpenCode JSON
    pairing carries `urls`, `username`, `password`; it is reusable secret material,
    not intrinsically a one-time authorization mechanism.
12. **OS maintenance.** Enable Ubuntu security unattended upgrades with a visible
    maintenance/reboot policy. Third-party repositories are not covered by its
    default allowlist. Agent and Tailscale pin changes remain reviewed manifest
    upgrades with a tested rollback/recovery route. [Ubuntu automatic updates](https://ubuntu.com/server/docs/how-to/software/automatic-updates/).

Tailscale HTTPS certificate issuance places the machine's certificate DNS name in
public certificate-transparency logs. Explain that before enabling HTTPS; choose
an opaque hostname rather than a project/customer name. This does not expose the
listener, but it is a privacy disclosure. If the user refuses, the first slice
cannot satisfy the existing remote HTTPS client policy; keep setup paused rather
than accept an untrusted certificate. [Tailscale HTTPS](https://tailscale.com/docs/how-to/set-up-https-certificates)
(validated 2025-12-10).

**Adopt path:** same preflight/trust/enrollment/runner/pairing, without VM creation.
Inventory existing units/users/ports/UFW/provider rules and Tailscale ownership
first. On an already joined server, never log out or switch tailnets automatically.
The first slice supports a clean compatible host or an explicitly reviewed empty
namespace; conflicts produce a plan, not destructive repair. Preserve unrelated
SSH access/data and clearly disclose that making SSH tailnet-only changes host
administration. Provider firewall status is owner-attested for generic SSH and
labelled as such; local listener/firewall checks and owner external scan are still
required. The app must not pretend to have provider-side evidence it cannot fetch.

## Repository fit and install manifest gaps

| Current source | Verified local facts / reuse | Required VPS work before enabling |
|---|---|---|
| [TermuxBridge](../../lib/termux/bridge.dart) | OC1 `1.18.29`, OC2 `2.0.10`; Node `v24.21.0` with x64/arm64 SHA-256; Paseo `0.9.1` | Reuse exact versions/hashes; extract a platform-neutral immutable manifest without importing Termux paths/native execution |
| [AI Team scripts](../../lib/builtin/setup/aiteam_scripts.dart) | Gas City `1.4.1`, Beads `1.2.2`, Dolt `2.3.3`; x64/arm64 artifact hashes; Gas Town pack commit `33d3a430a67d1782ad364556cb566bdb01d0afe3` | Preserve artifact/member checks; verify pack bytes/commit; replace `/root` assumptions and phone process limits with measured VPS policy |
| [Ubuntu OpenCode installer](../../lib/termux/opencode_ubuntu_setup.dart) | Versioned npm package + required platform package; OC2 isolated prefix avoids OC1 binary collision | npm version alone is not a bundled integrity lock; add reviewed tarball/platform/dependency integrity and controlled install scripts; no runtime `latest` |
| [Linux host script](../../scripts/host/ubuntu-opencode.sh) | Loopback default, 0600 password file and systemd lifecycle are useful patterns | Its install pipe downloads a mutable script and update is not a verified VPS manifest; do not execute it unchanged or expose its `password` output to logs |
| [Phone setup v2](phone-setup-v2-2026-09-24.md), [engine](../../lib/builtin/setup/setup_engine.dart), [contract](../../lib/builtin/setup/setup_contract.dart), [components](../../lib/builtin/setup/components.dart) | Component dependency/check/install, `::oc` stage/bytes/percent/version, durable native job and final connection step exist | New SSH remote runner and infrastructure journal; existing engine is coupled to BuiltinLinux/Kotlin and does not provision VPSs |
| [Codex transport](../../lib/codex/transport.dart) | Experimental `0.153.4` protocol baseline, Bearer WSS client; no managed installer pin | Choose exact compatible binary + verified digest for each arch; prove pinned binary's CLI authentication and reconnect behavior |
| [Paseo transport](../../lib/paseo/transport.dart), Termux local-agent script | `paseo.bearer.<secret>` subprotocol plus Authorization; local launcher disables relay/web UI/MCP injection and voice downloads | Preserve `--no-relay --no-web-ui --no-inject-mcp`; add dependency integrity; Claude Code presently **not pinned**, so VPS auto-install stays unavailable until pinned/qualified |
| [Built-in team](../../lib/builtin/team/builtin_team.dart), [orchestration client](../../lib/orchestration/client/http.dart), [host front](../../tool/host/cp_front/README.md) | Supervisor loopback + allowed Host; host front uses peer-IP Tailscale `whois`, identity ACLs and durable mutation receipts; client strips credentials | Keep identity/receipt enforcement. Existing front is direct tailnet HTTP; putting it behind Serve changes peer identity to loopback and is **not a drop-in**. See gate below |
| [Profiles](../../lib/state/profiles.dart), [pairing](../../lib/state/pairing.dart), [connection](../../lib/state/connection.dart) | Remote OpenCode/Codex/Paseo require HTTPS/WSS; bounded OC2 pairing parser; secure password/token storage and serialized deletion | Reserve profile ID before provisioning; explicit new secure-slot/file/shared-index cleanup; no fake connected profile before authenticated readiness |
| [External links](../../lib/ui/widgets/external_link.dart), [AI assistant contract](ai-setup-assistant-contract.md) | Untrusted URL guard; setup read/guided proposal boundary, no production mutations | Browser navigation only through guard; assistant cannot become infrastructure executor; kit-only UI later |

Official live [OpenCode server docs](https://opencode.ai/docs/server/) document
`serve` and password protection; the repository's OC2 notes still describe a
captured `beta-18600` while its install pin is `2.0.10`. Probe that exact pin and
record the contract before enabling OC2 VPS installation; a historical schema is
not sufficient current evidence. Do not emit a random password into journald by
relying on the server's default startup behavior.

Current [Codex app-server docs](https://learn.chatgpt.com/docs/app-server) expose
loopback WebSocket transport and file-based capability-token auth, but call
WebSocket experimental/unsupported. Those live flags are not automatically a
promise about pinned `0.153.4`; prove them on the chosen artifact. The app already
sends a Bearer token; keep it. Do not silently substitute unauthenticated sockets
or external-token account flows. The existing model-login flow remains separate.

The upstream [Paseo repository](https://github.com/getpaseo/paseo) includes a relay
transport; our VPS design explicitly disables it and verifies no relay connection.
[Claude Code setup](https://code.claude.com/docs/en/setup) offers version selection;
choose and checksum a supported distribution and qualify its auto-update controls
instead of inheriting the current unpinned phone installer. Respect the user's
own Claude/OpenAI/model login and applicable account permissions; do not copy
subscription tokens through our service.

AI Team is an orchestration stack, not one extra executable. The upstream
[Gas City repository](https://github.com/gastownhall/gascity), three binary pins,
pack, Dolt, project initialization and host front must all be accounted for.
For first VPS support, keep it unavailable until one of these is proven:

- Existing direct-tailnet HTTP front works under the orchestration-specific app
  transport policy and preserves peer-IP identity and grants. This is an existing
  exception to the main server profile HTTPS policy, not permission to relax OC1,
  OC2, Codex or Paseo. Verify Android network-security behavior, not only Dart URL
  normalization.
- A reviewed HTTPS front accepts identity only from trusted loopback Serve,
  handles tagged devices explicitly, rejects spoofed headers/direct bypass, and
  preserves Host rewriting, SSE, idempotency and current mutation authorization.
  Merely putting Serve in front of `front.py` fails the current `whois` identity
  design. Revisit its stale identity cache when defining revocation latency.

The second is the preferred consistent long-term route, but neither is claimed
implemented here. A tailnet network grant is required in both; do not publish raw
Gas City or replace its application identity checks with a shared password the
current client intentionally strips.

## Security and failure model

| Threat / failure | Required mitigation and remaining limitation |
|---|---|
| Stolen unlocked/rooted phone | Keystore-backed scoped credentials, OS screen lock, optional re-auth before destructive actions, shortest practical token life, separate project/subaccount, explicit revocation instructions. Secure storage does not stop an attacker operating an unlocked app/rooted OS |
| Overbroad provider token | Display exact permissions; project/compartment/resource-group boundary; no billing/admin rights unless a documented mandatory operation needs them. Hetzner write token remains project-wide; disclose it |
| Leaked metadata/user-data | No bearer credentials/private keys/runtime passwords in metadata; public-key-only bootstrap. Root may read cloud-init logs and provider controls VM memory/disks; this design cannot protect against malicious provider/root |
| SSH MITM or rebuilt machine | Independently verified host key, binding to instance generation, strict mismatch refusal; no trust inferred from uploaded client key, server name or IP |
| Compromised download/package | Exact per-arch manifest URL/hash, trusted signing roots, signature/provenance where offered, no shell-pipe latest, bounded extraction/member/path checks; checksum is integrity against our manifest, not proof upstream is benign |
| Agent escape / stolen model key | Unprivileged separate service users, no root or cloud instance role, inaccessible bootstrap/Tailscale state, explicit writable projects; coding agents intentionally execute code as their runtime user |
| Public exposure / IPv6 bypass | Provider and host firewall, loopback agent bind, private Serve only, Funnel-off audit, IPv4+IPv6 external probe, inspect Docker/nft/forwarding precedence; UFW output alone is insufficient |
| Wrong tailnet / broad member access | Node identity + SSH binding + phone reachability, explicit tailnet approval, least-privilege grants, runtime credentials; a tailnet address prefix is not identity proof |
| Credential leak through diagnostics | Dedicated transport, no body/header tracing; safe typed events rather than raw exception/log tail; redact login URLs, pairing, WebSocket subprotocols and `/config/providers` output; sentinel tests |
| App killed after paid create | Durable pre-send intent, provider action/resource IDs, reconciliation, no blind create retry; visible pending billable resources and explicit cleanup |
| Public SSH left open during login | Local persistent deadline timer, restricted source, no agent listeners; after expiry require console/tailnet recovery. Provider-rule removal waits for phone, with host-side closure independently enforced |
| Expired/revoked enrollment/provider token | Reauthorize/reconcile, never recreate; revoke auth key and enrolled node separately; provider console recovery remains available |
| Partial deletion / owner forgets profile | Exact resource ledger and residual-charge report; local forget and remote destroy separate. Do not erase recovery metadata/token before remote cleanup unless user explicitly chooses local-only removal |
| Compromised/replayed pairing | Tailnet SSH with pinned host, job/resource/manifest/nonce/expiry binding; durable consume-ACK protocol; reuse existing payload parser only inside authenticated envelope |
| Shared/adopted server damage | Read-only assessment and exact owned-file/resource manifest; never flush foreign firewall, destroy VM or remove pre-existing keys/node; unsupported conflicts require manual resolution |

Revocation proof must cover the *data plane*: invalidating a provider API token
stops cloud control but not agent sessions; deleting a Tailscale key stops future
enrollment but not existing nodes; changing an agent password may not close an
already authenticated socket. Specify session closure/restart and verify new and
existing connection behavior for each runtime before promising immediate revocation.

## Feasibility gates before building/enabling adapters

| Gate | Evidence required | If it fails / no-backend alternative |
|---|---|---|
| Hetzner first provider | Read/write dedicated-project token can list quote catalog and perform documented create with firewall; supported image/arch; action and uncertain-create reconciliation | Disable Create; generic SSH adoption; no inferred idempotency header |
| Provider native OAuth | Registered app/client type, redirect/app-link ownership, PKCE S256, state, public token endpoint, refresh/revoke behavior and distribution rules tested on Android | DO/Linode: documented PAT path pending policy check. Vultr: resolve July PKCE client-secret contradiction/admin review. No embedded vendor secret |
| AWS auth | User's Identity Center public-client PKCE/device registration/account-role retrieval or restricted IAM/temporary STS signing, expiry/resume | User-supplied restricted credentials or generic SSH; no universal “Sign in with AWS” claim |
| Azure auth | Entra public-client registration, tenant consent, correct ARM audience/RBAC and Android redirect/broker | User-owned registration or generic SSH; confidential service principal secret must belong to user, never us |
| Google auth | Android AuthorizationClient integration, allowed Compute scope, consent verification, reacquisition after restart; devices without Play Services assessed | Foreground reacquisition or generic SSH; offline server-auth-code refresh path **would require a backend** and is excluded |
| OCI / OVH / Scaleway | User-owned signing key/API application/service account, scoped policy and expiry lifecycle; current third-party terms | Manual console setup + generic SSH if auth/policy unsupported. These user-owned secrets can stay phone-side; no common vendor secret |
| Tailscale enrollment | Interactive SSH login with sanitized sensitive channel + correct node; optional one-use key and least-privilege OAuth-client API test | Console-generated key or manual tailnet join. Shared cross-tailnet native OAuth app is not proven; no broker |
| Trusted bootstrap | Provider console fingerprint or documented authenticated host-key retrieval before any secret; source-bound public SSH with deadline | Already-tailnet generic SSH / owner console. No TOFU or host private key in metadata |
| Private HTTPS | Tailscale phone routing/DERP, grants, device approval, HTTPS consent/CT disclosure, certificate trust, Funnel absence | Pause for owner action; no public or cleartext fallback for main profiles |
| Artifact and protocol support | Complete checksummed per-arch manifests; exact pinned OC1/OC2/Codex/Paseo/Claude/AI Team probes, startup secrets suppressed | Unsupported runtime unavailable. OC1 first; never install all stacks to discover compatibility afterward |
| AI Team identity | Current front's direct tailnet identity or redesigned trusted Serve identity + transport-policy proof | Leave AI Team VPS unavailable, retain existing supported deployment paths |
| Resume/deletion storage | Atomic journal + secure slot cleanup + interrupted POST/pairing/delete fixtures; SSH client vetted for host-key verification and no tracing | No paid create until recovery is demonstrated; ordinary add-existing-server remains |
| Current price / capacity | Full account-region quote, memory/storage/architecture eligibility and capacity; no unknown mandatory line item | Cost unknown => no Create. Adoption can proceed with explicit existing-bill disclosure |

A provider API being callable is not proof its consumer OAuth registration is
approved. Any provider flow that mandates a **shared confidential client secret**
requires a confidential component (normally a backend) for a generic distributed
app. We will not supply one. User-owned credentials or manual SSH are the explicit
alternatives. Static Android App Links association metadata is public configuration,
not a secret exchange service; it still requires domain and signer control.

## Slice plan and owner-run real VPS proof

The [contract](byo-vps-contract.md) defines interfaces, state machine, persistence,
UI pages/errors/copy, ownership and fake-test matrix. Implement one complete
Hetzner+generic+OC1 slice before adding providers or runtimes. Independent owners
can build adapter, remote runner and controller against frozen types; one owner
integrates shared profile/connection code. Claude then builds kit screens and
English/Arabic copy. Backend-only completion must not be labelled a usable feature.

The following plan is **for the owner to run later with explicit authorization**;
this research executed none of it. Use a disposable provider project/tailnet device,
a capped reviewed spend, no real repositories/model keys, and no CI credentials.
A simple harmless protocol/terminal operation proves connectivity; model generation
needs separate owner account consent and cost disclosure.

1. Review exact manifest/pins, sanitized plan, provider price and maximum planned
   experiment duration. Owner creates a scoped token and tailnet grant in their
   own consoles; enters secrets only through the phone's secure entry flow.
2. Create one smallest recommended compatible VPS (4GB x86 Hetzner candidate).
   Verify provider ID, image, firewall and quote; independently verify SSH host key
   from console. Record public IDs/timestamps only in the evidence packet.
3. Kill the app **after create is sent but before reply is saved**. Reopen and
   reconcile: exactly one VM, no duplicate key/firewall, charges visible. Repeat
   at remote install, enrollment, pairing-save-before-ACK and deletion checkpoints.
4. Let the phone go offline during enrollment; wait for the bootstrap deadline.
   From an independent non-tailnet host verify TCP22 is closed after expiry and
   all agent ports were never public (both address families). Recover through
   provider console/tailnet without an automatic world-open rule.
5. Complete login, device/grant/HTTPS consent and tailnet SSH transition. Verify
   phone can connect to private HTTPS, unauthenticated/wrong-password agent calls
   are denied, and another disallowed tailnet peer cannot connect. Turn off phone
   Tailscale: app connection must fail with no public fallback.
6. Inspect sockets, nft/UFW rules, provider rules and effective Serve/Funnel config;
   scan public IPv4/IPv6 from outside the tailnet. Negative external reachability
   plus configuration evidence is required; a local health probe is insufficient.
7. Reboot VPS and phone; reconnect without recreating resources/reinstalling good
   components. Test a harmless session/read plus streaming protocol. Repeat with
   rejected enrollment, wrong tailnet, denied grants, rate limits, expired provider
   auth and checksum failure using safe failure injection.
8. Owner inspects metadata, cloud-init logs, journald, process arguments, job store,
   crash diagnostics and exported evidence for sentinel secrets. The report records
   only pass/fail and which stores were checked; never paste credential values.
9. Revoke a one-use auth key: confirm the enrolled node remains, then remove node
   and test access loss. Test service-secret rotation, including existing sockets.
   Reauthorize only through owner channels. No claim of automatic revocation where
   the provider lacks an API; record required console actions.
10. Confirm explicit teardown. Verify exact VM, disks/IPs, keys/firewalls and chosen
    backups absent, Tailscale node removed (manual console evidence for interactive enrollment without API rights), credential revocation outcome, local
    secure slots/files/reservation gone and final billable inventory checked. A
    failed delete leaves `cleanupRequired`; never report $0 simply because profile
    disappeared. Owner inspects the provider bill after usage posts.
11. Repeat **adopt** on a separately prepared clean Ubuntu host. Add a harmless
    unrelated file/service/key; uninstall managed components and verify those
    survive. Confirm adoption never offers VM deletion. Later arm64 and each new
    runtime/provider get their own qualification, not inherited OC1 evidence.

Evidence packet: app revision/diff, Ubuntu image ID, provider/API version,
manifest digest, dependency hashes, phone OS/Tailscale version, timing, resource
counts, safe status snapshots, public/tailnet reachability results, reboot/resume
outcomes, cleanup verification and costs. Screenshots redact account identifiers
where appropriate; no tokens, pairing payloads, login URLs or project content.
Tests in the implementation use fake HTTP/SSH/clock/vault exclusively. Docs-only
verification here is scoped diff, repository-link existence and citation review;
Flutter analysis/tests and live infrastructure proof were not run.

## Research verification record

The final documents were reviewed against the gathered provider evidence and
current local source. Review corrected Azure rate-limit units and made host-trust
ordering, generic-server firewall attestation and manual Tailscale device cleanup
explicit in the contract. All relative repository links resolve; Markdown fences
are balanced and the staged diff has no whitespace errors. Only the two requested
design documents and `COMMIT_MSG.txt` belong to this change. Some official API
pages required public read-only JSON fetches because browser rendering failed;
Vultr's generic terms page returned 403, and older Scaleway/OVH terms PDFs are not
claimed as current distribution approval. Those limitations remain launch gates.
No Flutter tests, signed build, account login or live resource proof was performed.
