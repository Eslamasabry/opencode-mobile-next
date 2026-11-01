# 16. Security, privacy and release

Written 2026-11-01. Binding parent: `01-architecture-decision.md` (the ADR): §3.3 (rejected stack pieces), §5.2
(`:core:security`, `:core:net-okhttp`, `:engine:diagnostics`, `:android:platform`), §6.3 (enforcement), §9.6 (errors,
crashes, privacy), §9.7 (startup and migration), §9.9 (identity and release), §10.2 (upgrade journey), §12 D2, D4, D12,
D18. Siblings this section leans on and does not repeat: `02-principles-scope.md` (G7, G8, S-22, S-23, freeze rules
§5), `03-architecture-modules.md` (§10.2 `:core:security` types, §18 logging, §19.4 Konsist K1–K20, §19.6 gates),
`04-protocols-networking.md` (§3 hardened transport, §4 trust and address policy, §4.8 credentials), `05-data-migration.md`
(§3 `ProfileScope` and the sweep, §4 `SecretStore`, §7 migration, §9 rollback), `06-ui-kit-design-system.md` (K11
secret field, `KitCopy`), `10-media-voice.md` (§13 Wi-Fi share, §17 media invariants, §19.5 analytics hooks),
`11-android-platform-native.md` (§3 OS components, §10 notifications), `12-package-registry.md` (§7 banned list, §8
licences and notices, §9 supply chain, §10 update policy, R12-1). When this file and the ADR disagree, the ADR wins and
this file is corrected; §0.3 lists the places where the owner's newer words force an ADR amendment.

Tags: **[V]** checked today (2026-11-01) against a primary source (vendor docs, a changelog, a real APK, the repo).
**[R]** read in the repo, a map, a research file, a sibling section or owner memory. **[S]** secondary source.
**[U]** unverified; the named proof in §13 settles it. Ids used here: `SI-n` (security invariants), `K-SEC-n` (Konsist
rules added to 03 §19.4), `N16-Dn` (decisions made in this section), `CR16-n` (change requests to other sections),
`D16-n` (owner decisions), `P16-n` (proofs), `WP-16.n` (work packages), `A16-n` (acceptance checks). Catalog rows
`B-SEC-NNN` are rows of `../catalog/BEHAVIORS.md`; the new rows this section adds are in §14.

## For the owner, in five lines

1. Every safety rule of today's app comes across unchanged, and most of them become build failures instead of review
   items: links from servers open only through one checked door, passwords and provider keys never reach logs,
   notifications or reports, each server's data is deleted with it, and plain HTTP still needs your yes per address.
2. The October certificate scare (Flutter carried its own certificate list and needed a new release to block the stolen
   eMudhra certificates) does not repeat: the native app uses Android's own certificate store, which Google updates
   without us, and OkHttp 5.5.1 adds the block for phones without Google updates.
3. Releases stay GitHub releases signed with exactly the same keys as today; Shorebird ends, so every fix is a normal
   new version that the app itself offers ("Version 3.0.1 is ready → Update"), checked for the right signer and
   checksum before Android installs it.
4. The usage-tracking core you asked for is built on day one with our own code (no tracking SDK): basic app-health
   reports always on, detailed usage only if the person turns it on, never chats, files, addresses or passwords — and
   nothing is actually sent until you pick the service (D16-1); until then the basic reports wait on the phone.
5. F-Droid comes after the switch: the build is made reproducible now so F-Droid can ship our own signed APK later; the
   remaining blockers (prebuilt Linux tools, the voice engine binary, the "Tracking" label) are listed with their fixes.

Decision needed: yes, one — D16-1 (which analytics service, if any), best answered before the release candidate
(A16-11). D16-11 (a signing fallback) becomes a decision only if proof P16-7 or F-Droid refuses our two
`CN=Android Debug` certificates. Everything else in §16 has a default and work proceeds on it.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read | Then |
|---|---|---|
| Coordinator / `:quality` owner | §0, §2, §12, §15 | add the K-SEC rules, lint table and gates in slice 0–1; route the CR16 items |
| Core owner (`:core:security`, `:core:data`) | §1.1–§1.5, §2.4 | port the vectors; own `Secret`, `KnownHosts`, `ExternalLinkPolicy` |
| Backend (Codex: `:core:net`, `:core:net-okhttp`, protocols) | §1.6, §1.7, §3.4, §8 | TLS floor, redirect-capable downloader, updater check |
| Kit owner | §1.3 (UI half), §1.8 (WebView), §2.1 | `KitField(Secret)`, `KitCopy`, `ExternalLinkUriHandler`, `KitWebPreview` |
| Android platform builder | §1.8, §3, §8 | exported surface, PendingIntents, FileProvider, `PackageInstallerUpdater` |
| Runtime owner (`:android:linux`) | §3.2 | the Linux trust-zone rules |
| Media owner | §3.3 | the two Wi-Fi share tightenings |
| Diagnostics builder | §5 | the analytics core |
| Release lane (coordinator + owner approval) | §6, §7, §9, §10 | signing, workflow, reproducibility, notices |
| Test-writer lane | §2.5, §14 | canary and egress harness; the new rows |
| Reviewer | §1 (the SI table), §15 | reviewers never launch test processes (ADR §10.3) |

### 0.2 Decisions made in this section

| # | Decision | Why |
|---|---|---|
| N16-D1 | The security spec is the **SI table (§1)**: every invariant names its mechanism, its module, the check that fails the build, its catalog rows and its tier. A security review is "does every SI row still hold", not a free read. | The Flutter rules lived in AGENTS.md prose and a hand-run ratchet; agents broke them by accident (e.g. #87's private download URL, B-SEC-171). |
| N16-D2 | Konsist rules for security are numbered **K-SEC-1…K-SEC-12** and live in `:quality` beside 03's K1–K20. | 06 already uses K21/K22 as kit decision ids; separate prefix avoids collisions. |
| N16-D3 | All app-process TLS uses the **platform trust manager** (Conscrypt + Network Security Config). The only custom `X509TrustManager`s are 04's `PinnedLeafTrustManager` and `CapturingTrustManager`, both in `:core:net-okhttp`. OkHttp's resolved version is **≥ 5.5.1**. | The Flutter TLS advisory lesson (§1.7). |
| N16-D4 | Opt in to **Certificate Transparency** in `network_security_config.xml` (`<certificateTransparency enabled="true"/>` in `base-config`) at the switch, **only if proof P16-2 passes**; otherwise leave the API 36 default (off). | CT is what protected CT-enforcing clients from the eMudhra certificates [V Google blog]; targetSdk 37 turns it on anyway [V]. Pinned profiles use our own trust manager, so CT does not touch them. |
| N16-D5 | A `Secret` value class carries every in-memory credential; its `toString()` is `"Secret(•••)"`. Data classes may not hold a secret-named property of type `String` (K-SEC-4). | Kotlin data classes print every field; Flutter had no such trap. |
| N16-D6 | Secret text fields keep their `TextFieldState` in `remember {}`, never `rememberSaveable`/`rememberTextFieldState()` (which saves into the instance-state Bundle). | Saved instance state leaves the process (system_server, task snapshots). |
| N16-D7 | One file of **known hosts** (`KnownHosts.kt`, `:core:security`) lists every host the app or its guest Linux contacts on its own, with purpose and trigger. It drives the egress allowlist (§2.5), the downloader's redirect allowlist (§8.4) and the PRIVACY.md destinations table (§4.3). | One source for "where does data go". |
| N16-D8 | The app updater is **lineage-aware and install-source-aware**: it checks GitHub only when the installed signer is the public lineage and the app was not first installed by an F-Droid client (§8.2). | The owner's local-key phone can never install a public-lineage APK; F-Droid users update through F-Droid. |
| N16-D9 | The analytics core lives in `:engine:diagnostics` (package `…engine.diagnostics.analytics`), not a new module; senders are our own HTTP code over `:core:net`; the build ships with `sender=none` until D16-1. | No ADR module-list change; 12 X-04 bans every vendor SDK; nothing leaves the phone until the owner picks a service. |
| N16-D10 | Release builds strip `Log.v/d/i` from all code including libraries (`-assumenosideeffects`). QA hooks use 14 §4.4's mechanism only: the `QaHooks` data class in `:core:domain` exists in every build and is bound to `QaHooks.None`; the reader of `files/native/qa-hooks.json` and every QA-only class (`QaHooksFileReader`, `EgressRecorder`, `QaFixtureAnalyticsSender`) live in `src/qa/` and are compiled only into `stableQa`; `verifyQaHooksAbsent` (§2.3) proves the release DEX has none of them. | Belt and braces for K5; QA hooks (feed override, step interrupter, fixture analytics) must never reach a person. |
| N16-D11 | The Wi-Fi share **opens first, then checks the open descriptor** (§3.3), streams every request from that descriptor, and refuses every connection after `endsAt` (even if Android froze the process past it). | Closes the check-then-use gap (a symlink swapped between the check and the open, including a swapped parent folder) and the frozen-timer gap (media-voice map risk 12). |
| N16-D12 | The build is made **reproducible now** (deterministic inputs, a two-build check on the VPS, report-only until the F-Droid slice). | F-Droid can then ship our own signed APK (same signer → users can move between GitHub and F-Droid builds). Retrofitting later is harder. |
| N16-D13 | This section adds two fields to 14's `QaHooks`, read only in `stableQa`: `updateFeed` (§8.2) and `analyticsEndpoint` (§5.5). They and 14's existing URL overrides (`downloadBaseUrl`, `githubBaseUrl`) follow one rule: plain `http://` is allowed **only** to the emulator's host alias `10.0.2.2`, and none of them is ever a `KnownHosts` entry. | Without them no buildable APK can exercise the updater (every non-release APK is signed with the local key) or the analytics upload, so P16-4, EG-1, EG-6 and A16-11 would pass on nothing. |
| N16-D14 | Egress is attributed **by Linux UID** on the rooted AVD (iptables `owner` match), not by a whole-device capture minus a baseline (§2.5). | A device-wide pcap cannot tell the app's traffic from Play services, the TTS engine or WebView updates; the app's UID covers OkHttp, WebView's in-process network stack and the guest Linux, and nothing else. |
| N16-D15 | No debug build can update the stable or `.native` package: the `debug` build type gets `applicationIdSuffix = ".debug"` (CR16-15), and every delivery path refuses an APK that `aapt2 dump badging` marks `application-debuggable` (A16-14). | The owner's signing key is the PC's `~/.android/debug.keystore` (§6.1), which AGP uses for every debug build; native debug builds work (unlike Shorebird Flutter), so without this a debuggable stable-package APK installs over the owner's app. |
| N16-D16 | Secret fields keep the Flutter behaviour: **no autofill** (neither save nor offer), no IME personalised learning, never prefilled, "Saved · Replace" for a stored value (DESKTOP-WEB-CROSS-64). An earlier draft of SI-4 turned on `ContentType.Password` for password managers; that would let the keyboard and autofill service see server passwords, which the Flutter app deliberately refused, so it is not done without the owner. Turning autofill on later is an owner choice (one line in `KitField`), recorded here so nobody changes it silently. |

### 0.3 Conflicts found and change requests (the coordinator routes them; work proceeds on the default)

| # | Where | Conflict | Proposed change | Default until settled |
|---|---|---|---|---|
| CR16-1 | ADR §9.6 "Nothing phones home"; 02 G7, §1.1 row 17, NG15, S-22 | Owner memory `freeze-owner-decisions-2026-11-01` item 6: "Let's build tracking SDK day one … Opt in for detailed but basics we need regardless." 10 §19.5 already emits DETAILED events for "the analytics core (16)"; 12 R12-1 raises the same conflict. | Amend ADR §9.6 to: "Nothing phones home except: the update check (switchable), downloads the person starts, and the disclosed analytics tiers of section 16 §5 (BASIC always on, DETAILED opt-in), with no third-party SDK." Amend 02 row 17 / NG15 accordingly; S-22's allowlist gains the analytics host once D16-1 names it. S-23 is unchanged (still no SDK). | The core is built; `sender=none`; **nothing is sent**, so ADR §9.6 stays literally true until D16-1. |
| CR16-2 | `oc_app/AGENTS.md` ("Known signers: stable CI `2D010C21…`"); `research/android-surfaces.md` §14 ("register `1DE5BF08…` and `2D010C21…`") | Every published GitHub release is signed `842284B2…1C` (`android-release.yml:42 SIDELOAD_CERT_SHA256`, `scripts/release.sh:11`, release notes v1.0.43+49 and v1.0.44+50) [V repo]. `2D010C21…` is the CI quality-gate test signer. | Fix the AGENTS.md line in the native `AGENTS.md` and correct the research note; D18 registers `842284B2…` and `1DE5BF08…` (§6.5). | §6 table is authoritative. |
| CR16-3 | 03 §10.2 `HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }` vs 04 §4.1 `AddressClass { Loopback, Private, Tailnet, Public }` | Two names for one classifier. | Keep 04's `AddressClass` (04 owns the vectors and B-SEC-119..125); 03 §10.2 is edited to match. Link-local is `Private`; an unparseable host is a `UrlRejection`, not a class. | 04's names. |
| CR16-4 | The brief's phrase "cleartext only to loopback/emulator/Tailscale" | The binding rule (04 §4.2, B-SEC-120/121/153) is: loopback without asking; every other `http://` origin (Tailscale, private LAN, public) only after per-origin consent, with softer words for Tailscale/LAN. Paseo: `ws://` or `wss://`, and the host must be Loopback or Tailnet **regardless of scheme** (04 §4 row "Paseo profile"; owner rule: nothing on the open internet, the relay is never used). The emulator's host alias `10.0.2.2` is `Private`. | No emulator special case in release code; QA seeds consent through the normal store (05 fixture recipe). The only `10.0.2.2` exceptions are the `stableQa` URL overrides of N16-D13. | 04 §4.2. |
| CR16-5 | 04 §3.10 `ResumableDownloader` sits on the hardened client (no redirects) | GitHub assets answer 302 → `release-assets.githubusercontent.com`; Hugging Face model files answer 302 → `us.aws.cdn.hf.co` [V curl today]. GitHub has moved its asset host before (`objects.githubusercontent.com` until 2025), and native has no code push to follow a new one. | The downloader runs its own redirect loop: HTTPS → HTTPS only, ≤ 5 hops, each hop's host must be in the feature's `KnownHosts` redirect set, no credentials ever, resume always restarts from the **original** URL (signed redirect URLs expire). The `AppUpdate` set is `*.githubusercontent.com` (the checksum and signer checks are the real gate, §8.3). §8.4. | As stated. |
| CR16-6 | 06 `KitField(kind = Secret)` | Must not use `rememberTextFieldState()` (saveable). | N16-D6. | As stated. |
| CR16-7 | 11 §10.4 notification buttons | Question option buttons and the legacy `RemoteInput` reply answer without unlock (threat-model gap 2). `Notification.Action.Builder.setAuthenticationRequired` exists only on API 31+; minSdk is 26. | `setAuthenticationRequired(true)` on Allow once, question options and legacy reply (API 31+). On API 26–30 the action receiver checks `KeyguardManager.isDeviceLocked` first: when locked it answers nothing and opens the app at that request (`EntryRouter`, ids only), so unlocking is still required. Reject stays without (harmless). D16-3. | Require unlock. |
| CR16-8 | builtin-linux map BUILTIN-LINUX-05 (unconfined `--bind=/storage` by default) | Any agent in the guest can read all shared storage once All files access is granted. But today agents save files and APKs to `/sdcard/Download`, and the phone-file sheet opens or installs them (10 §12; the manifest says `REQUEST_INSTALL_PACKAGES` exists to "open an .apk the agent saved to Downloads"). | Confined binds by default (only folders opened as projects) **plus one always-bound outbox**, `/storage/emulated/0/Download/OpenCode` (created on demand, read-write), which the phone-context instruction names as the place to save files for the person; a visible row "Let Linux see all phone files" (off) restores `--bind=/storage`. D16-4 states the trade-off. | Confined + outbox. |
| CR16-9 | AI Team (post-switch, ADR D6): supervisor on `127.0.0.1:8472` with "no password yet" (builtin-linux map) | Any app on the phone can reach loopback and drive AI Team. | AI Team may not be ported without authentication on every loopback listener it starts (a switch+N gate, §3.2 L3). | Recorded as a requirement of the AI Team phase. |
| CR16-10 | 07 (screen inventory) | The freeze-tip Updates page (`feat/app-updates-2026-10-31`, merged `6b686bb7c`) and the Privacy page analytics rows are not in 07's inventory (07 was written against `oc_app-batch1`). | Add `SettingsRoute.Updates` (This app › Updates) and the three rows of §5.8 to I08 `PrivacySettingsScreen`. | Rows land in slice 4 with the updater. |
| CR16-11 | 03 §21.1 build types (release, benchmark) vs 05 (uses a `qa` build type) | `qa` is used but not defined. | Define `qa` = `initWith(release)`, `versionNameSuffix = "-qa"`, `matchingFallbacks += "release"`, debuggable false, hooks only in `src/qa/` (N16-D10). The variant that matters is 14 §4.4's `stableQa` (stable package, local key). The release pipeline refuses any APK whose versionName ends `-qa` (A16-14). | As stated. |
| CR16-12 | 14 §R11, §3 (`CanarySecrets`, `canaries.txt`, `canary_scan.sh`, fx-proxy log, `egress-allowlist.txt`) vs this section's first draft (`Canaries`, `canary_scan.py`, pcap, `.toml`) | Two canary sets, two scanners and two egress sources in one tool tree: a fixture serving one set while a scan greps the other passes without testing anything. | **One canary object**: 14's `CanarySecrets` in `:core:testing`, carrying this section's richer list (§1.2); `canaries.txt` is generated from it by a Gradle task. **One allowlist file**: `native/config/egress-allowlist.toml` (02 S-22's name), generated from `KnownHosts`. **One canary scanner**: 14's `canary_scan.sh` (R11, W14-6), extended with this section's targets and exceptions (§2.5); this section supplies scenarios and allowlists only. **Authoritative egress source**: the UID capture of §2.5 (N16-D14); fx-proxy's connection log is a cross-check, never a pass. | As stated. |
| CR16-13 | 05 Appendix A (no `oc.appUpdate.*` keys) | The freeze-tip updater stores `oc.appUpdate.enabled` (the "Check GitHub for new versions" switch), `oc.appUpdate.lastCheckMs`, `oc.appUpdate.release` [V `lib/update/app_update_controller.dart:71-73`]. Unlisted, the native app could turn a person's switched-off check back on. | 05 M3 carries `oc.appUpdate.enabled` unchanged (app-wide, default true when absent) and drops `lastCheckMs` and `release`; catalog row B-SEC-200 (§14). | Carry the switch. |
| CR16-14 | 05 §3 sweep, §9.5 retention | 05 keeps the Flutter-era copies until the v3 cleanup: `files/migration/flutter-v57/` (prefs and FSS XMLs, prefs in plain text) and the live `FlutterSecureStorage*.xml` with its still-working Keystore alias. Deleting a server in native leaves its password ciphertext, drafts and queued prompt text there for at least two releases. | The sweep reaches them (§1.4). R2 is offered only before the gate first opens (05 §9.4), so no profile deletion can precede an R2 restore; after it, the S0 copies need not stay byte-exact. | As §1.4. |
| CR16-15 | 03 §21.1 `buildTypes` | The `debug` type has no `applicationIdSuffix`; AGP signs it with `~/.android/debug.keystore`, which **is** the owner lineage key (§6.1). `assembleStableDebug` therefore makes a debuggable APK that installs over the owner's app. | `getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }` in the convention plugin (N16-D15). | As stated. |
| CR16-16 | 13 PB-56, 11 §6 start specs | The in-app OpenCode chat server does not set `OPENCODE_DISABLE_MODELS_FETCH` (only the probe and AI Team do [V freeze tip]); the egress environment the Flutter app gives Paseo and agents is not written down in any plan section. | §3.2 L12 lists the fixed egress environment; `GuestEgressEnvTest` checks every start spec. | As §3.2. |
| CR16-17 | 10 C10-14 / §15.2 (camera FileProvider root) | 16's first draft allowed two roots and said "never `files-path`". | Accepted: three roots (§1.8.3); `FileProviderPathsTest` and B-SEC-195 name the camera root. | Resolved here. |
| CR16-18 | ADR §6.1 (`:ui:kit-content` may not depend on `:core:security`) | `KitWebPreview` cannot call `ExternalLinkPolicy` directly. | The kit link API in `:ui:kit` gains an origin: `KitLinks.open(uri, origin: KitLinkOrigin)`; `:ui:shell`'s root implementation maps `KitLinkOrigin` to the policy's origins (§1.1). No ADR change. | As stated. |
| CR16-19 | 14 §4.4 `QaHooks` | Needs the two overrides of N16-D13. | Add `val updateFeed: QaUpdateFeed? = null` (`feedUrl`, `assetPrefix`, `expectedPublicSigner`) and `val analyticsEndpoint: String? = null`; both read only in `stableQa`. `updateFeed` supersedes 14's `updateFeedUrl`. | As stated. |

### 0.4 What this section does not decide

Transport details and timeouts (04), the `SecretStore` file format and the migration steps (05), kit visuals (06),
screen placement beyond the rows named here (07), the media pipeline (10), notification channels and OS components
(11), which library does each job and the update cadence (12), dates and slice contents (15), test tooling beyond the
canary and egress harness (14).

---

## 1. The security invariants (the spec)

Each row: what must always hold, how the native app makes it hold, where the code lives, what fails the build, which
catalog rows prove it, and at which tier. "Breaking a row is a security regression" (AGENTS.md); a PR that weakens a row
needs a CR16 entry and the coordinator's sign-off.

| # | Invariant | Mechanism | Module | Enforced by | Rows | Tier |
|---|---|---|---|---|---|---|
| SI-1 | Every URL the app did not author opens only through the one policy and opener | `ExternalLinkPolicy.check` → `ApprovedLink` (only the policy constructs it) → `ExternalLinkOpener`; Compose `LocalUriHandler` overridden at the root with `ExternalLinkUriHandler` | `:core:security`, `:ui:kit`, `:android:platform` | K6 (03), K-SEC-1, `ExternalLinkPolicyTest` vectors, `UriHandlerRootTest` | B-SEC-003, 058, 064, 065, 095, 129, B-SEC-185 | T1 |
| SI-2 | No secret reaches a log, diagnostics, report, notification, widget, analytics event or test output | `Redactor` installed in `onCreate` step 2 before any secret is read; `AppLog` → `RedactingWriter`; secrets registered on every read and write; credential ingress registered before decoding | `:core:security`, `:core:common` | K5, K13–K15 (03), K-SEC-4, `CanaryLeakTest`, `MigrationLogScrubTest`, `RedactorTest` | B-SEC-004, 008, 041, 043, 055, 056, 079, 094, 102, 106, 117, 128, 144, 149, B-MIGR-011 | T1/T2/T3 |
| SI-3 | Secrets at rest live only in `SecretStore` (Keystore AES-256-GCM, non-exportable, AAD = entry name) | 05 §4 | `:core:data` | K7 (03), `KeystoreSecretStoreTest`, `KeystoreSecretStoreDeviceTest` | B-SEC-036, 075, 076, 135, 141, B-MIGR-006 | T1/T3 |
| SI-4 | Secrets in memory and on screen never print, never persist in saved state, never land in the clipboard unmarked, are never prefilled, and are never learned by the keyboard or offered to autofill | `Secret` value class; `KitField(Secret)` on `BasicSecureTextField` with `remember { TextFieldState() }`, empty on mount, "Saved · Replace" for a stored value, no autofill, `IME_FLAG_NO_PERSONALIZED_LEARNING` (§1.3, N16-D16); `KitCopy(sensitive = true)` sets `EXTRA_IS_SENSITIVE` | `:core:common`, `:ui:kit` | K-SEC-4, K-SEC-5, K-SEC-6, `SecretFieldStateTest`, `SecretFieldImeTest` | B-SEC-040, 107, B-SEC-186, 187, 188, 204 | T1 |
| SI-5 | Per-profile data is keyed `oc.<what>.<profileId>` (or registered in `ProfileScope` as a secret, blob area, table, Keystore alias or guest folder) and deleted with the profile | 05 §3 `ProfileScope` + sweep; deletion is one transaction run backwards in `:engine:profiles` | `:core:data`, `:engine:profiles` | K9 (03), `ProfileDeletionSweepTest` | B-SEC-009, 011–013, 016, 018, 026, 039, 063, 085, 093, 125, 143, 172, 178, 182 | T1/T2 |
| SI-6 | Provider credentials (`/config/providers` and friends) are never stored, shown, logged or echoed | parsed to `JsonElement`, credential values registered with the `Redactor` before DTO decoding (04 §3.7); provider DTOs have **no** key fields; UI says "Key saved on the server" | `:protocol:oc2`, `:engine:integrations` | `ProviderKeyCanaryTest` (§2.5), K-SEC-4 | B-SEC-040, 094, 149, 165, 169, 177 | T1/T3 |
| SI-7 | Plain HTTP only to loopback without asking; every other `http://` origin only after per-origin consent; no silent downgrade; credentials never cross origins; no redirects on credentialed clients | 04 §4.1–§4.6 (`AddressClass`, `TransportPolicy`, `TrustGateInterceptor`, `oc.cleartextOk.<id>`) | `:core:security`, `:core:net`, `:core:net-okhttp` | 04 B-PROTO-128 (Ktor retry), B-PROTO-139 (NSC elements), `NetworkSecurityConfigTest` | B-SEC-007, 113, 119–125, 146, 153, 175 | T1 |
| SI-8 | Basic auth is built per request from `SecretStore`, sent only after the trust gate passes, never in a URL, never retried automatically after a 401 | §1.6 | `:core:net`, `:protocol:*` | `BasicAuthTransportTest` (MockEngine), 04 §4.8 | B-SEC-007, 035, 113, 118, 138 | T1 |
| SI-9 | Certificate pins are leaf-DER SHA-256, checked during the handshake before any HTTP byte; pinned clients never fall back to PKI | 04 §4.4 `PinnedLeafTrustManager` | `:core:net-okhttp` | 04 P-N2, `PinnedLeafTrustManagerTest` | B-SEC-007, 152, 154 | T1/T3 |
| SI-10 | Unpinned TLS uses the platform trust store, which Android updates without us; our own blocklist floor is OkHttp ≥ 5.5.1 | §1.7 | `:core:net-okhttp`, `:androidApp` res | K-SEC-2, `verifyOkHttpFloor`, `NetworkSecurityConfigTest` | B-SEC-193 | T2 |
| SI-11 | The OS-visible surface (exported components, launch extras, intent filters, PendingIntents, FileProvider paths, WebView) is frozen and hardened | §1.8 | `:androidApp`, `:android:*`, `:ui:kit-content` | `verifyExportedSurface`, K-SEC-7…K-SEC-9, `FileProviderPathsTest`, lint table §2.2 | B-SEC-058, 155–162, 180, 183, B-SEC-194–196 | T2 |
| SI-12 | Raw exception or server text is never shown as copy; it appears only redacted under Details | ADR §9.6; 03 K14, K15, K20 | `:ui:words`, translators | 03 K14/K15/K20 | B-SEC-001, 014, 098, 100, 103, 104, 110, 130–133, 166 | T1 |
| SI-13 | Approvals bind to the exact pending request; nothing approves "always" from outside the app; lock-screen answers need unlock (API 31+ by the action flag, API 26–30 by a locked-device check in the receiver) | 11 §10.4 + CR16-7 | `:engine:requests`, `:android:background` | `NotificationActionAuthTest` | B-SEC-105, 109, 114, 115, 147, 183, B-SEC-184 | T1/T3 |
| SI-14 | The app (and the guest Linux, which shares its UID) contacts only the person's servers and the hosts in `KnownHosts`, each for its listed reason | N16-D7, N16-D14, §3.2 L12 | `:core:security` | K-SEC-10, egress harness (§2.5) | B-SEC-097, 168, 171, B-SEC-197, 202 | T2/T3 |
| SI-15 | An app update installs only an APK with our package, a higher versionCode, the installed app's own signer and a matching `SHA256SUMS` line | §8 | `:engine:diagnostics`, `:android:platform` | `UpdateVerifierTest`, P16-4 | B-BUILD-010–012, B-MIGR-012, B-SEC-189, 190, 200 | T1/T3 |
| SI-16 | Analytics events carry only enums, booleans and buckets from one schema file; DETAILED only with consent | §5 | `:engine:diagnostics` | K-SEC-11, `AnalyticsPrivacyTest` | B-SEC-191 | T1 |
| SI-17 | Nothing in the release APK is debug-only or QA-only, and no debuggable APK is ever delivered | N16-D10, N16-D15 | `:androidApp` | `verifyQaHooksAbsent` (§2.3), 12 X-31, A16-14 | B-SEC-150 | T2 |
| SI-18 | No keystore, signing secret, `key.properties` or third-party skill pack is ever committed; nothing pushed or released carries the owner's email, private addresses or device names | `.gitignore` + scan + leak gate (§2.3) | repo | `RepositoryHygieneTest` (ported and extended) | B-SEC-150, B-BUILD-030 | T2 |
| SI-19 | BYO-host SSH reaches only tailnet targets, pins the host key and refuses a changed identity before any forwarding, verifies the installer digest before running it, passes the private payload by stdin only, never writes or returns a private key, and refuses option or shell injection in targets | port of `lib/domain/byo_host/*` and `KeystoreByoSigner` (private key non-exportable in Keystore, alias `oc.byoHostSsh.<id>`) | `:engine:integrations`, `:android:platform` | `ByoHostSshTest` (ported vectors), K-SEC-12 | B-SEC-024, 028–034, 037 | T1/T2 |
| SI-20 | The GitHub token goes only to `github.com` and `api.github.com`, from a fixed `FixedOrigin` client (04 §4, no redirects); it lives only in `SecretStore`; it reaches the guest only as a one-use 0600 file in a 0700 random folder, deleted after the command (L4); a rejected token signs the account out | port of `lib/domain/git/github_device_flow.dart` | `:engine:project`, `:core:net` | `GitHubDeviceFlowTest` (ported), `GitHubTokenCustodyTest` | B-SEC-081–083, 086–091 | T1 |
| SI-21 | Every OAuth flow (agent sign-in, MCP connector) opens only the pinned vendor route, checks `state`, fails closed when OAuth is off or the launch is unsafe, never keeps a code or token outside `SecretStore`; external agent requests never forward credentials on redirect or to another origin | port of the agent sign-in and connector OAuth code | `:engine:agents`, `:engine:integrations` | `AgentAuthorizationUrlTest`, `ConnectorOAuthTest`, `ExternalAgentClientTest` (ported) | B-SEC-006, 062, 101 | T1 |
| SI-22 | Every consent store fails closed: a missing or corrupt record means "not granted"; revoking reaches every observer at once; read-state sharing is per profile and private by default; persisted reports never hold raw locations or credentials | one `ConsentStore` (DESKTOP-WEB-CROSS-48) with the three-state read | `:core:data`, `:engine:settings` | `ConsentStoreTest`, `ReadStateConsentTest` (ported) | B-SEC-010, 015, 017, 163, 164 | T1 |
| SI-23 | Scripts the app runs in the guest quote every word, refuse NUL and unsafe input without echoing it, never generate a script from unsafe params, reject unpinned or path-escaping install inputs, and refuse symlinked roots before any removal | `ShellQuote` + the setup and removal guards, ported with their vectors | `:engine:runtime`, `:android:linux` | `ShellQuoteTest`, `InstallInputGuardTest`, `RemovalGuardTest` (ported) | B-SEC-002, 005, 083, 096, 166, 167, 170 | T1 |
| SI-24 | The built-in Linux rules L1–L13 hold (listeners authenticated, secrets as 0600 files, confined binds, fixed egress environment, safe backups and restores) | §3.2 | `:android:linux`, `:engine:runtime` | `ListenerAuthTest`, `GuestEgressEnvTest`, `SharedStorageBindsTest`, `LinuxBackupPlanTest`, `LinuxRestoreSafetyTest` | B-SEC-081, 092, 097, B-SEC-198, 199, 202, 203 | T1/T3 |
| SI-25 | Git page commands are built only from validated, quoted input: branch and remote names match `^[A-Za-z0-9_][A-Za-z0-9._-]{0,99}$` and addresses refuse whitespace, control characters and user info **before** any shell runs; commit stages only the chosen paths, each quoted; pull is fast-forward only; a conflicted file is never discarded; every script runs with `GIT_TERMINAL_PROMPT=0` and no pager; the GitHub token reaches Git only through the inline helper reading `OC_TOKEN_FILE` for `host=github.com` (SI-20) | `GitScript` + `ShellQuote` (SI-23), `GitRepository` validators; 17 §2.3 | `:engine:project` | K-SEC-13, `GitInjectionTest`, `GitScriptGoldenTest`, `GitRealGitTest` | B-BUILD-014…021 | T1 |

Rows not named above that still bind the port: quota response cap (B-SEC-140, 04 per-feature caps), logo downloads
(B-SEC-137, `ProviderLogoFetcher` contract test), problem-report content (B-SEC-126, 127, 148, §4.1 and 03 §18).

**`SecRowCoverageTest`** (`:quality`, T2): reads every `B-SEC-*` id from `../catalog/behaviors.jsonl` and
`native/config/sec-row-map.toml`. Each id must map to an SI row, to one of the named rows in the paragraph above, or to
an out-of-scope decision id: ADR D6 (FactsBook signing B-SEC-066–078; AI Team B-SEC-023, 176), NG22 (browser grants
B-SEC-019–021), Termux-as-host NG7 (B-SEC-091 Termux half). An unmapped id fails. The catalog has no scope field today,
so the map file is the scope record; the catalog owner keeps it in step with 02's scope table.

### 1.1 External links: one door (SI-1)

The Dart rules (`lib/ui/widgets/external_link.dart`, 276 lines [R]) port exactly. 03 §10.2 owns the types; this table is
the vector set `ExternalLinkPolicyTest` must cover.

| Input | Origin | Decision |
|---|---|---|
| empty, whitespace, > 2,048 chars | any | `Refuse(TooLong / NotAUrl)` |
| scheme not `https`/`http` (`intent:`, `file:`, `content:`, `javascript:`, `data:`, `market:`, `opencode-mobile:`, …) | any | `Refuse(BadScheme)` (`intent:` → `IntentScheme`) |
| no host (opaque URL) | any | `Refuse(NotAUrl)` |
| user info present (`https://u:p@host`) | any | `Refuse(CredentialsInUrl)` |
| `HTTPS://Host` | any | scheme normalised to lower case before anything else |
| `https://…` | `AppAuthored` (help pages, release notes link, licence sites from `KnownHosts.links`) | `Open` |
| `https://claude.com/cai/oauth/authorize?…` that passes `AgentAuthorizationUrl.validate` | `AgentCard`/sign-in flow | `Open` (the sign-in sheet already named the destination) |
| `https://…` | `ServerContent`, `Markdown`, `AgentCard`, `Terminal`, `Notification` | `AskFirst(shownHost)`; the kit confirm shows the host (port only when the URL has one, B-SEC-065), neutral kind |
| `http://…` | any non-AppAuthored | `AskFirst`, destructive kind, "Open anyway" never on Enter (B-SEC-064) |

Native-only traps, each with a rule:

- **Compose opens links by itself.** `Text` with `LinkAnnotation.Url` calls `LocalUriHandler.openUri`. `:ui:shell`
  provides `LocalUriHandler provides ExternalLinkUriHandler(policy, opener, confirmHost)` at the root of `App()`.
  `Dialog`, `Popup` and the M3 in-window sheet inherit composition locals, so they are covered; `UriHandlerRootTest`
  renders a `LinkAnnotation.Url` inside a kit sheet and asserts the policy saw it (B-SEC-185).
- **`AndroidView` text with `autoLink`** bypasses Compose: `TextView.autoLinkMask` and `Linkify` are banned (K-SEC-1).
- **WebView navigation** goes through `shouldOverrideUrlLoading` in `KitWebPreview` (§1.8.5): same origin stays; a
  main-frame navigation from a person's gesture to another origin goes to the kit link API with origin
  `ServerContent`; sub-frame and script navigations to another origin are refused silently. `:ui:kit-content` may not
  depend on `:core:security` (ADR §6.1), so it calls `LocalKitLinks.current.open(uri, KitLinkOrigin.ServerContent)`
  from `:ui:kit`; `:ui:shell` provides the one implementation, which maps `KitLinkOrigin` to the policy's origins and
  runs `ExternalLinkPolicy` → confirm → `ExternalLinkOpener` (CR16-18). `LocalUriHandler` is backed by the same object.
- **The opener** (`AndroidLinkOpener`, `:android:platform`): `Intent(ACTION_VIEW, Uri.parse(link.uri))`,
  `addCategory(CATEGORY_BROWSABLE)`, `FLAG_ACTIVITY_NEW_TASK`; `ActivityNotFoundException` → `Failure.Platform.NoHandler`
  ("No app on this phone can open this link.", host under Details).
- **Notification and widget taps** never carry a URL; they carry ids for `EntryRouter` (B-SEC-180).

### 1.2 Redaction and logs (SI-2)

03 §10.2 and §18 own `Redactor`, `RedactingWriter`, `AppLog` and the never-log list; 04 §3.7 owns credential ingress.
This section adds:

1. **The canary list**: one object for every leak test, unit and device, so tests do not invent their own. It is
   14's `CanarySecrets` (`:core:testing`), carrying this list (CR16-12); 14's two values stay as the server password
   and the generic provider key, the rest are added. `canaries.txt` (read by 14's `canary_scan.sh`) is generated from
   `ALL` by the `:core:testing:writeCanaries` task, never edited by hand:
   ```kotlin
   public object CanarySecrets {
       public const val SERVER_PASSWORD = "canary-pass-7c1e"                     // 14's value
       public const val PROVIDER_KEY = "sk-e2e-FAKE-0123456789abcdef"            // 14's value
       public const val ANTHROPIC_KEY = "sk-ant-api03-OCCANARY0000000000000000000000000000000000000000"
       public const val GITHUB_TOKEN = "ghp_OCCANARY000000000000000000000000000000"
       public const val PASEO_PASSWORD = "0cca9a7e00000000000000000000000000000000000000000000000000000000" // 64 hex, the agent-host shape
       public const val BEARER = "eyJhbGciOiJIUzI1NiJ9.eyJvYyI6ImNhbmFyeSJ9.OCCANARYsig"
       public const val PAIRING_SECRET = "oc-canary-pair-91b4"
       public val ALL: List<String> = listOf(
           SERVER_PASSWORD, PROVIDER_KEY, ANTHROPIC_KEY, GITHUB_TOKEN, PASEO_PASSWORD, BEARER, PAIRING_SECRET,
       )
       public fun assertNoCanary(text: String) { /* 14 §3 */ }
   }
   ```
   The fixture servers (02/14 fixture set) serve these values; `/config/providers` returns `PROVIDER_KEY` and
   `ANTHROPIC_KEY` in every key-shaped field the 2.1.1 schema has.
2. **Release log stripping** (N16-D10), in `androidApp/src/main/keepRules/logging.keep`:
   ```
   -assumenosideeffects class android.util.Log {
       public static int v(...); public static int d(...); public static int i(...);
   }
   ```
   `w`/`e` stay (libraries' real failures), and our own code never calls `Log` at all (K5).
3. **Throwable text** never reaches a sink unredacted: 03 K15 limits `.message` reads to `*Failures.kt`; those
   translators pass it through `Redactor.details(error)`. Known carriers of secrets in Kotlin-land: Ktor
   `ResponseException` messages (full URL + body text), `UnknownHostException` (host), kotlinx.serialization errors when
   `exceptionsWithDebugInfo = true` (input JSON; 04 N4-D17 sets it false), SQLite/Keystore exception messages (file
   paths, aliases).

### 1.3 Secrets at rest, in memory and on screen (SI-3, SI-4)

At rest (05 §4 is the format owner; these are the invariants that format must keep):

| Rule | Value | Why |
|---|---|---|
| Key | AES-256-GCM, alias `oc.secrets.aesgcm.v1`, `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, non-exportable | 05 §4.1 |
| User authentication | **none** (`setUserAuthenticationRequired(false)`) | scheduled runs and widget actions read secrets while the screen is off |
| `setUnlockedDeviceRequired` | **false** | same; true would break night-time schedules |
| StrongBox | no | latency; not on every phone |
| AAD | the entry name | swapping two ciphertexts fails (`KeystoreSecretStoreTest` "AAD swap rejected") |
| Failure | `Failure.Storage.SecretUnreadable` → that profile's re-entry flag; never `""`, never silent delete | B-SEC-135, S-4 |
| Backup | excluded (`allowBackup="false"`, exclude-everything rules kept, 11 §3) | keys never leave the device |
| Direct Boot | the app is not `directBootAware`; nothing runs before first unlock | credential-encrypted storage only |

In memory:

```kotlin
// :core:common (commonMain). Equality by value; never printed.
@JvmInline
public value class Secret(private val value: String) {
    /** Only transports, SecretStore and the Redactor read the raw value; Konsist K-SEC-4 allowlists the call sites. */
    public fun reveal(): String = value
    override fun toString(): String = "Secret(•••)"
    public val isEmpty: Boolean get() = value.isEmpty()
}
```

- Pairing payloads, Basic credentials, Codex/Paseo tokens, GitHub tokens, quota tokens and the phone-server password are
  `Secret` from the moment they are read until the transport writes the header.
- A route key, `SavedStateHandle`, `rememberSaveable`, WorkManager `Data`, an `Intent` extra or a `PendingIntent` never
  holds a `Secret` (K-SEC-5). Notification, widget, schedule and shortcut intents carry ids only (B-SEC-180).

On screen and clipboard (06 owns the parts; these are the rules):

| Part | Rule |
|---|---|
| `KitField(kind = Secret)` | `BasicSecureTextField`; state created with `remember { TextFieldState() }` (N16-D6); `KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)`; semantics `password()`; **no `contentType`** and the field is marked not important for autofill (`View.IMPORTANT_FOR_AUTOFILL_NO` on the host view while focused), so autofill neither saves nor offers values (parity with Flutter's `autofillHints: null`, N16-D16); the IME gets `IME_FLAG_NO_PERSONALIZED_LEARNING` through `InterceptPlatformTextInput` (Compose UI ≥ 1.7; the interceptor ORs the flag into `EditorInfo.imeOptions` after the original request fills it) [U: P16-8, the flag is advisory and some keyboards ignore it]; no suggestions; **never prefilled**: the state starts empty on mount, and a stored value shows the line "Saved · Replace" with **no input** until the person chooses Replace (`SecretFieldPresence.Saved` → `Editing`); value left-to-right and bidi-isolated; the masked value is never the semantics value and never handed to a copy part; every keystroke registers the value with the `Redactor` before the host is notified (DESKTOP-WEB-CROSS-64); Reveal shows the text only while held/toggled and re-hides on leave |
| `KitCopy(text, sensitive)` | `sensitive = true` for any secret, pairing link or one-time code; Android 13+ adds `ClipDescription.EXTRA_IS_SENSITIVE` so the clipboard preview hides it (06 K11) |
| Clipboard reads | only on a person's action (paste, "paste image"); never polled |
| Screens that show a secret | none show it unmasked by default; no `FLAG_SECURE` needed except the widget overlay panels (already set, 11) |

### 1.4 Per-profile keys and the deletion sweep (SI-5)

05 §3 owns the naming rules, `ProfileScope` and the sweep text. This section adds the end-to-end test and the
non-preference stores that a sweep must reach:

| Store | Per-profile shape | Swept by |
|---|---|---|
| prefs | `oc.<what>.<profileId>` | `ProfileScope.keyShapes` |
| `SecretStore` | `pw.<id>`, `oc.codexToken.<id>`, `oc.agentHostSecret.<id>`, `oc.quotaToken.<id>`, GitHub sign-in, … (05 Appendix B) | `secretShapes` |
| Keystore aliases | `oc.byoHostSsh.<id>` | `:engine:integrations` registration (B-SEC-026) |
| blob areas | draft attachments, photo recovery, stash files of that profile | `blobAreas` |
| guest folders | `/home/oc/.oc-profiles/<id>` (agent homes, sign-ins) | `RuntimeHost.deleteAgentHome(profile)` via a registration hook (B-SEC-026, 063) |
| Room tables (switch+1) | `profile_id` column | `tables` |
| widget / tile / shortcut caches | widget snapshot rows, pinned shortcuts | B-SEC-178, 182 |
| analytics | none: events never carry a profile id (§5.4) | — |
| Flutter-era live secure storage (until the v3 cleanup, 05 §9.5) | `FlutterSecureStorage*.xml` entries `<fss prefix>pw.<id>`, `…codexToken.<id>`, `…agentHostSecret.<id>`, `…quotaToken.<id>` and the other per-profile names of 05 Appendix B; the FSS Keystore alias still decrypts them | `FlutterEraSweep` (`:android:migration`, registered in `ProfileScope` as a `ProfileResourceCleaner`): removes that profile's entries from the XML (`.tmp`, fsync, rename) — CR16-14 |
| Flutter-era byte backup (until the v3 cleanup) | `files/migration/flutter-v57/` copies of `FlutterSharedPreferences.xml` (drafts and queued prompt text in **plain text**) and of the FSS XMLs; `replaced-*` and `start-fresh-*` copies (05 §9.4) | same cleaner: rewrites **each copy, its authoritative `.bak` twin included**, with the **same structural edits as the live data** (Astra M1), not only key shapes: the profile's `oc.<what>.<id>` keys and FSS entries, **and** its embedded records inside app-wide JSON values: entries of `oc.offlineQueue` and `oc.keptQueuedPrompts` (`profileID`), `oc.sessionDrafts` rows of the profile **and the unattributed `profileID == ''` rows** (05 §3.5 step 4), the profile's element of `oc.profiles`, `oc.activeProfile` when it names it, `oc.pendingPromptPhoto` when its `profileID` matches, widget snapshot and target entries, pinned-conversation and schedule-index entries naming it. One function, `ProfileStructuralEdit.apply(map, id, plan)`, is used for the live file **and** every copy, so they cannot drift. Queued prompts survive in a copy only as the drafts the person explicitly chose to keep (`DeletionOptions.keepQueuedPromptsAsDrafts`). Each rewritten copy gets a new manifest hash; the copy's recovery value for the deleted server is gone on purpose. Safe because R2 restore is offered only before the gate first opens (05 §9.4), so a deletion can never precede it; R1 reads the live files, which the sweep already cleaned |

`ProfileDeletionSweepTest` (`:engine:profiles` commonTest, fakes for every store): seeds **every registered shape** for
profiles A and B, including the Flutter-era live FSS XML and the S0, `replaced-*` and `start-fresh-*` copies **with
`.bak` twins**, and A's **embedded** records (a queued prompt, a draft, an unattributed `''` draft, A's `oc.profiles`
element with its address, the pending photo, a widget snapshot row), deletes A, then greps the **actual bytes** of
every file for A's canary prompt text, address and id: zero hits; B's records are unchanged. A new registration without a seed in this test fails it
(the test iterates `ProfileScope.registrations`). Catalog row B-SEC-201 (§14).

### 1.5 Provider credentials never echoed (SI-6)

| Step | Rule |
|---|---|
| Ingress | `/provider`, `/config`, `/config/providers`, MCP config reads, connector reads: body → `JsonElement` → `Redactor.registerCredentialValues(tree)` (keys matching `(?i)(api[-_]?key\|key\|token\|secret\|password\|authorization\|access[-_]?token\|refresh[-_]?token\|bearer\|cookie)` after separator normalisation) → only then DTO decode, tracing or diagnostics (04 §3.7) |
| DTOs | provider and connector DTOs have **no** property for a key value; they carry `hasKey: Boolean` and the key's source label only |
| UI | "Key saved on the server" / "No key yet"; editing sends a new key and never shows the old one (B-SEC-040) |
| Exports | plain project export leaves out credentials; a private export says "private" in its name (B-SEC-099) |
| Notifications, widgets, analytics | no provider names in analytics (§5.4); notification bodies masked (B-SEC-106) |

`ProviderKeyCanaryTest` (`:protocol:oc2` + `:engine:integrations`, MockEngine serving the canary fixture): after loading
providers, opening the providers screen model, building a problem report and emitting all analytics events, assert
`CanarySecrets.PROVIDER_KEY` and `CanarySecrets.ANTHROPIC_KEY` are absent from: every DTO's `toString()`, the `LogRing`, `Diagnostics.errors`, the
`ProblemReport` text, the analytics queue, every `XUi` produced (via `toString()`).

### 1.6 Transport: cleartext, Basic auth, pins, redirects (SI-7, SI-8, SI-9)

04 §3–§4 is the implementation. The invariants a reviewer checks:

1. **Who may speak plain text** (04 §4.2): OpenCode 2 `http://` to Loopback without consent; to Tailnet/Private/Public
   only with `oc.cleartextOk.<profileId>` equal to the exact origin. Paseo: `ws://` or `wss://`, and the host must be
   Loopback or Tailnet **regardless of scheme** (04 §4; nothing on the open internet, the relay is never used). Codex
   `ws://` only to Loopback. Downloads, the update check and analytics: `https` only (the `stableQa` URL overrides of
   N16-D13 are the only exceptions, and they are absent from release builds).
2. **Nothing is sent before consent**: pairing and the editor hold an unconfirmed `http://` origin unprobed ("nothing
   was sent to it", B-SEC-124).
3. **Basic auth**:
   - header `Authorization: Basic base64(utf8("$user:${secret.reveal()}"))`, built inside the transport per request;
     the default user is `opencode`;
   - the `TrustGateInterceptor` runs first: wrong origin, unconsented `http`, `http` with a pin, or a latched pin
     mismatch → the request never gets a socket;
   - never in a URL (validators reject user info, query and fragment, B-SEC-153);
   - a 401 sets `passwordRejected` and stops every retry loop until the person updates the password (no lockout by
     retry, no guessing); the banner names the server;
   - Paseo: password in the subprotocol, the header and `hello.auth` (04 §5); empty password allowed (B-SEC-113).
   - The phone server's password is 32 random bytes from `SecureRandom`, base64url (11 §6.2); it is the only thing that
     keeps other apps on the phone out of `127.0.0.1:4097` (§3.1 T5).
4. **Redirects**: never followed by a credentialed client (`followRedirects(false)` in OkHttp **and** Ktor, 04 N4-D2);
   the downloader's own loop (CR16-5) carries no credentials.
5. **Pins**: 04 §4.4. A pin cannot be set on `http`; a host/port/scheme change deletes it; another profile never
   inherits it (B-SEC-154).

### 1.7 TLS trust: the Flutter advisory lesson (SI-10)

**What happened** [V]: on 2026-10-19 Google learned that private keys of eight eMudhra intermediate CAs (six issuing
TLS certificates) were compromised. Android got a blocking update through Google Play services that evening; Chrome
and "Android applications that target the latest SDK version or have otherwise opted in to CT enforcement" were safe
because no rogue certificate was CT-logged (Google security blog, 2026-10-20). Flutter apps were **not** safe: `dart:io`
on Android validates chains with its own compiled-in BoringSSL root bundle and "does not read OS intermediate
certificate blocklists", so the fix had to be a rebuilt app on Dart 3.13.7 / Flutter 3.47.8 (GHSA-frr6-h895-5pxp;
Firebase guidance page, updated 2026-10-29). Our Flutter app took it as toolchain commit `6e0fc0482` and merge
`fc5c1e78f` on the freeze branch. A Shorebird patch cannot carry it: patches must be built with the release's exact
Flutter (3.47.1 for 2.1.0, per that commit message), and the root bundle lives in the engine binary, not in patchable
Dart code [R].
OkHttp 5.5.1 (2026-10-22) also hard-codes a block of the 8 intermediates; its changelog says this "is not necessary for
Android devices with Google Play Services … This change protects JVMs and AOSP devices" [V CHANGELOG].

**The lesson**: *every TLS stack we ship with its own root list is a release we owe at every CA incident.* The native
app therefore owns as few root lists as possible and keeps an inventory of the ones it cannot avoid.

Rules for the app process:

1. Unpinned HTTPS uses OkHttp's default, which on Android is the platform `X509TrustManager` from Conscrypt + Network
   Security Config. On Android 14+ the root store itself is updatable through the Conscrypt Mainline module (Google
   Play system updates) [S XDA, source.android.com]. Never bundle a CA file; never `<certificates src="@raw/…">`; never
   `src="user"` (NSC defaults for API 24+ trust system CAs only) [V developer.android.com].
2. Custom trust managers, `SSLContext.init`, `HostnameVerifier`s and `sslSocketFactory(…)` exist only in
   `:core:net-okhttp` (K-SEC-2): `PinnedLeafTrustManager` (pinned profiles) and `CapturingTrustManager` (certificate
   inspection; sends no application bytes, 04 §4.5). Lint `TrustAllX509TrustManager`, `BadHostnameVerifier`,
   `AllowAllHostnameVerifier` are errors (§2.2).
3. **OkHttp ≥ 5.5.1** in `stableReleaseRuntimeClasspath` (ADR §8 pins 5.5.1). The build-logic task
   `verifyOkHttpFloor` (registered on `:androidApp`, §2.3) reads the resolved graph and fails below it, so a Ktor bump
   can never pull OkHttp backwards. This covers AOSP phones without
   Google Play services (some F-Droid users) and the desktop JVM.
4. **Certificate Transparency** (N16-D4): Android 16 (API 36) supports CT only by opt-in; apps targeting Android 17
   (API 37) get it by default [V developer.android.com security-config and behavior-changes-17]. "Certificate
   transparency verification isn't performed on connections that use custom trust anchors" [V], and our pinned clients
   use their own `SSLContext`, so self-signed pinned servers are unaffected. The switch release opts in after P16-2:
   ```xml
   <!-- androidApp/src/main/res/xml/network_security_config.xml (04 §4.7 keeps the base-config element) -->
   <network-security-config>
       <base-config cleartextTrafficPermitted="true">
           <certificateTransparency enabled="true" />   <!-- N16-D4, only after P16-2 passes; API < 36 ignores it -->
       </base-config>
   </network-security-config>
   ```
   No `<trust-anchors>` element (system only); `NetworkSecurityConfigTest` pins exactly these elements and attributes
   (extends 04's B-PROTO-139 test).
5. **WebView** (dev preview) uses Chromium's own verifier, CRLSet and CT, updated through the WebView package; nothing
   to own.
6. **Rust phone engine**: `reqwest` is built with `default-features = false` (no TLS at all, loopback HTTP only)
   [V `engine/phone/Cargo.toml`]. If it ever needs TLS, it uses `rustls-platform-verifier`, never `webpki-roots`.

**Trust inventory** (kept in `native/docs/security/trust-inventory.md`; checked within 72 hours of any CA or TLS
advisory, the same window as 12 §10.2 security fixes):

| # | TLS stack | Root list it uses | Who updates it | Owner | Action at an incident |
|---|---|---|---|---|---|
| TI-1 | App process (OkHttp → Conscrypt) | Android system store (APEX on 14+) + OkHttp's hard-coded blocklist | Google (Play system updates); us for the OkHttp floor | backend | bump OkHttp if it ships a block; release |
| TI-2 | WebView (dev preview) | Chromium | WebView updates | kit-content | none |
| TI-3 | Guest Ubuntu `apt`, `curl` | Ubuntu `ca-certificates` package in the rootfs | **us**: the layer-2 updater (memory `app-updates-two-layers`) must refresh `ca-certificates` when idle | runtime | ship a pin/refresh; tell the person on This phone |
| TI-4 | Guest Node 24 (OpenCode 2 helpers, npm, Claude Code) | Node's bundled Mozilla store unless `--use-system-ca` [U version of the flag] | Node releases → our pinned Node (v24.21.0) | runtime | bump the Node pin; consider `NODE_OPTIONS=--use-system-ca` so TI-3 covers it [U, P16-6] |
| TI-5 | OpenCode 2 binary (`@opencode/cli`, single binary) | unverified which root list it embeds [U] | OpenCode releases → our pin (2.1.1) | backend | check at P16-6; bump the pin |
| TI-6 | Desktop JVM (desktop phase) | the bundled runtime's `cacerts` + OkHttp blocklist | us (jpackage runtime) | desktop phase | rebuild with a patched JDK |
| TI-7 | Rust engine | none (no TLS feature) | — | runtime | keep it that way |

### 1.8 The Android surface (SI-11)

#### 1.8.1 Exported components (frozen list; `verifyExportedSurface`)

`verifyExportedSurface` (a build-logic verification task registered on `:androidApp`, §2.3, T2) reads the merged
release manifest and compares, per component: FQCN, `exported`, `permission`, `taskAffinity`, `launchMode`, every
`<intent-filter>` (actions, categories, data). It extends 03's `ComponentFreezeTest` (names) with the security
attributes. The expected file `config/exported-surface.txt` may change only with a CR16 row; it includes the library
components the manifest merger adds (last rows of the table), so the first run is reviewed, not filled in blindly.

| Component (FQCN under `io.github.eslamasabry.opencode_mobile`) | Exported | Guard | What any app can make it do |
|---|---|---|---|
| `.MainActivity` | yes; `launchMode="singleTop"`, `taskAffinity=""` (both frozen: they keep another app's task from hosting or hijacking ours) | — | open the app; `SEND text/plain` → a **draft only**, never sent (B-NAV share rows); `VIEW opencode-mobile://session` and `://team` → ids only, network-silent, waits for the gate (B-SEC-155–162). **Explicit launch extras** any app can also set — `oc.shortcut` (`connect`, `new_task`, `activity`, `usage*`, `phone_setup`, `linux`, `usage_target:…`), `oc.shortcut.profile`, `oc.shortcut.session`, and the coding-alert extras (`kind`, `sessionID`, `profileID`, `reply`, `monitorToken`) — are read only by 07's `EntryIntentReader`: allowlisted values, bounded lengths, navigation only, network-silent, then stripped; they never connect, send, approve, create a task or sign in without a tap; `monitorToken` must match the stored token (`^[a-f0-9]{64}$`) or the alert extras are ignored. Actions that may run without a tap (`new_task` → `CreateSession`, `usage_target` → connect) run **only** from app-owned `PendingIntent`s aimed at the non-exported `.InternalEntryActivity` (07 §7.4, Astra M2); the same values arriving here are untrusted |
| `.InternalEntryActivity` (new) | **no** | explicit app-owned `PendingIntent`s only (`FLAG_IMMUTABLE`) | — |
| `.ManageSpaceActivity` | yes (system needs it) | — | show the page; deleting needs the person's confirm |
| `.WidgetConfigureActivity` | yes (`APPWIDGET_CONFIGURE`) | checks `AppWidgetManager.getAppWidgetInfo(id)?.provider?.packageName == packageName`, else finishes with `RESULT_CANCELED` (B-SEC-194) | nothing for a foreign id |
| widget providers (7) | yes (`APPWIDGET_UPDATE`) | system broadcast | redraw from the cache |
| `.AttentionTileService`, `.PauseTileService` | yes | `android.permission.BIND_QUICK_SETTINGS_TILE` | only the system binds |
| `.ScheduledPromptReceiver`, `.BuiltinRestoreReceiver` | **no** (as today [V manifest]) | the system still delivers its own broadcasts (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, …) to non-exported receivers; `onReceive` ignores every other action | nothing |
| `.CodingActionReceiver`, `.StatusActionReceiver`, `.LivePauseReceiver`, `.WidgetActionReceiver`, `.BuiltinIdleReceiver`, `.AppUpdateStatusReceiver` | **no** | explicit intents from our PendingIntents | — |
| `.WidgetRouteActivity`, `.WidgetOverlayActivity` | **no** | — | — |
| services (`BuiltinServerService`, `SetupService`, `BackgroundConnectionService`, `TermuxResultService`, `LocalPdfService`) | **no** | `LocalPdfService` is `isolatedProcess` | — |
| `androidx.core.content.FileProvider` (`${applicationId}.localfiles`) | no, `grantUriPermissions="true"` | §1.8.3 | read a granted URI only |
| merged: `androidx.work.impl.background.systemjob.SystemJobService` | yes (library default) | `android.permission.BIND_JOB_SERVICE` | only the system binds |
| merged: `androidx.work.impl.diagnostics.DiagnosticsReceiver` | yes (library default) | `android.permission.DUMP` | only shell/system; prints WorkManager state (ids and tags only, K-SEC-5) |
| merged: `androidx.profileinstaller.ProfileInstallReceiver` | yes (library default) | `android.permission.DUMP` | only shell/system; installs the baseline profile |
| merged: anything else a library adds | must be `exported="false"` or removed with `tools:node="remove"` in `androidApp/src/main/AndroidManifest.xml` | — | — |

#### 1.8.2 PendingIntents and intents

- Every `PendingIntent` is `FLAG_IMMUTABLE` with an explicit component, a unique request code and a unique `data` URI
  (11 T17), **except** three reviewed cases (K-SEC-7 allowlist): the legacy `RemoteInput` reply (must be mutable); the
  `PackageInstaller` status receiver (the system fills extras); and the Termux `RUN_COMMAND` result in `:android:termux`
  `TermuxActivityCommands` (Termux fills the result extras; explicit component `TermuxResultService`,
  `FLAG_ONE_SHOT or FLAG_MUTABLE`, request code = execution id, unique `data` URI, as on the freeze tip
  `TermuxActivityCommands.kt:98` [V]). The third entry is removed together with Termux migrate-out (ADR D7);
  `TermuxResultPendingIntentTest` asserts it is mutable, one-shot and explicit, so nobody "fixes" it to immutable and
  silently loses migrate-out results.
- `Intent.parseUri`, `Intent.getIntent` and re-launching an `Intent` taken from another intent's extras are banned,
  except the `PackageInstaller` `STATUS_PENDING_USER_ACTION` confirm intent, which is started **from the visible
  activity** (Android 17 BAL rules on `IntentSender`, research android-surfaces §2.2) (K-SEC-8).
- No implicit internal broadcasts; `registerReceiver` uses `RECEIVER_NOT_EXPORTED` on API 33+.
- `<queries>` stays exactly: `TTS_SERVICE`, `com.termux` (until Termux migrate-out ends, ADR D7), `com.tailscale.ipn`,
  `PROCESS_TEXT text/plain`. `QUERY_ALL_PACKAGES` is never requested.

#### 1.8.3 FileProvider

The freeze tip already declares `androidx.core.content.FileProvider` (`${applicationId}.localfiles`, not exported,
`grantUriPermissions="true"`) with `res/xml/local_file_paths.xml` = `<external-path name="shared_storage" path="."/>`
("files the built-in Linux or Termux saved for the person") [V `int/freeze-2026-11-01`]. The native file keeps the
resource name (10 C10-10) and allows exactly three roots (the camera root is 10 C10-14, accepted here, CR16-17):

```xml
<!-- androidApp/src/main/res/xml/local_file_paths.xml -->
<paths>
    <external-path name="shared_storage" path="." />          <!-- parity: open a phone file the Linux saved to shared storage -->
    <files-path name="camera" path="camera-capture-v1/" />    <!-- TakePicture targets only (10 §10.4); write-only for the camera app -->
    <cache-path name="share" path="share/" />                 <!-- copies of app-private files (viewer, rootfs) for Share / Open with; emptied at start -->
</paths>
```

Never `files-path` for any other folder, never `path="."` or `path="/"` under `files-path` or `cache-path`, never
`root-path` or `external-files-path` of the whole tree. A URI is built only for the one file the person chose, granted
per intent (`FLAG_GRANT_READ_URI_PERMISSION`, read-only), never persisted. A file inside the app's private storage
(including `files/linux/ubuntu`) is never served from its real path: it is first checked by 10 §13.2 `PathSafety` (no
`looksSecret` file) and copied into `cache/share/`. Share-in URIs are read once into the draft vault with the MIME gate
and size caps of 10 §6 and never kept as URIs.

The camera root's rules (10 C10-14, `docs/security/fileprovider-camera-capture.md`): the folder holds only capture
files; a URI is built only for `content://${applicationId}.localfiles/camera/<captureId>.jpg` and handed only to the
`TakePicture` intent with `FLAG_GRANT_WRITE_URI_PERMISSION` (never a read grant, never to any other intent); the file is
deleted after `preparePhoto` or cancel; stale files are swept at start.

`FileProviderPathsTest` parses the XML and fails on any element other than these three, exactly as written
(B-SEC-195); `CameraCaptureGrantTest` asserts the write-only, capture-intent-only grant.

#### 1.8.4 Permissions (delta from Flutter)

| Permission | Change | Note |
|---|---|---|
| `REQUEST_INSTALL_PACKAGES` | **added** (already on the freeze tip) | two uses: the updater (§8), and **Install** for an `.apk` an agent saved (10 §12; the system installer screen confirms every time, the sheet names the file and says an agent saved it, and the action is never offered from a notification or widget — §3.1 T6). Play policy irrelevant (not on Play) |
| `com.termux.permission.RUN_COMMAND` | kept while Termux migrate-out exists (D7); removed with it | narrows the surface later |
| `MANAGE_EXTERNAL_STORAGE` | kept, requested lazily only when a shared-storage project is opened (threat model scenario 6) | with D16-4 the guest sees only opened folders and the outbox |
| `ACCESS_LOCAL_NETWORK` | **not** at targetSdk 36; added in the targetSdk-37 slice (§11) | |
| everything else | unchanged (packages-build map PACKAGES-BUILD-08) | |

#### 1.8.5 WebView (dev preview only; `KitWebPreview` in `:ui:kit-content` androidMain)

| Setting | Value | Why |
|---|---|---|
| `javaScriptEnabled` | true | dev servers need it (lint `SetJavaScriptEnabled` suppressed on this one file with the reason) |
| `allowFileAccess`, `allowContentAccess` | false (explicit) | no local files |
| `addJavascriptInterface` | never (K-SEC-9) | no bridge into the app |
| `mixedContentMode` | `MIXED_CONTENT_NEVER_ALLOW` | |
| `onReceivedSslError` | `handler.cancel()` only | lint `WebViewClientOnReceivedSslError` = error |
| `shouldOverrideUrlLoading` | same scheme+host+port as the started preview → load. Another origin: if `!request.isForMainFrame` or `!request.hasGesture()` → refuse **silently** (return true, nothing opens, no dialog: a page's iframes or script redirects cannot raise prompts by themselves); only a main-frame navigation from a person's gesture goes to the kit link API with `KitLinkOrigin.ServerContent` (§1.1, CR16-18) | B-SEC-058, DESKTOP-WEB-CROSS-41 |
| Allowed start URL | `http(s)` only, no user info, host = the preview's built address | B-SEC-058 |
| `WebView.setWebContentsDebuggingEnabled` | false in release | |
| `setGeolocationEnabled` | false | parity |
| `CookieManager.setAcceptThirdPartyCookies(webView, …)` | false | parity |
| Manifest `<meta-data android:name="android.webkit.WebView.MetricsOptOut" android:value="true"/>` | added | no WebView usage metrics upload |
| Safe Browsing | per WebView (`WebSettings.setSafeBrowsingEnabled`, API 26+): **off** when the preview host is loopback, LAN or tailnet (`HostClass`, 04 §4.1); **on** for a Public host. (Corrected, Astra M4: previews also open remote dev servers, 17 §2.4, so "only the phone's own servers" was false.) The Google destination this adds is listed in `KnownHosts` and PRIVACY.md | |
| `shouldInterceptRequest` | **every** request the page makes that WebView exposes (subresources, frames, fetch and XHR, form POSTs, redirects): another origin than the started preview → a `403` `WebResourceResponse` with no body, never fetched | Astra M4 |
| main-frame check | `onPageStarted` / `doUpdateVisitedHistory`: a main frame that ended on another origin by a path `shouldOverrideUrlLoading` never saw (a POST form submit, a server redirect) is stopped (`stopLoading()`, back to the preview start URL) and the link goes to the kit link API only after a tap | Astra M4 |
| cache, `localStorage`, cookies | cleared **before every load** (`clearCache(true)`, `WebStorage.deleteAllData()`, `CookieManager.removeAllCookies`) and again when the preview closes | parity (today clears before every load) |

**What the boundary covers, honestly (Astra M4).** The requirement is: **no cross-origin traffic leaves the preview
without the person's tap, for navigations (GET and POST), redirects, frames, subresources and fetch/XHR.**
`shouldOverrideUrlLoading` alone does not do that (Android does not call it for POST navigations, subresources or
script traffic), which is why the two rows above exist. **Not covered:** WebSocket and WebRTC connections, which no
WebView API intercepts. They are limited, not blocked: WebRTC needs a permission the preview never grants
(`onPermissionRequest` → deny), and the preview is described in the UI as "the server's own page", with the same trust
as opening it in a browser. `WebPreviewEgressTest` (instrumented, `emulator-5584`, a local fixture with two origins)
asserts zero requests at the second origin for a form POST, a 302, an iframe, an `<img>`, a `fetch` and an XHR, and
records (does not assert) a WebSocket to the second origin as the documented gap.

#### 1.8.6 Notifications, widgets, tiles (summary; 11 §10–§11 own the detail)

- Channels `VISIBILITY_PRIVATE` with a count-only public version; content never shows a secret (B-SEC-105/106).
- **Allow once** has `setAuthenticationRequired(true)`; **Always allow** never appears outside the app; question
  options and the legacy reply also require unlock (CR16-7, D16-3, B-SEC-184); **Reject** does not. On API 26–30, where
  the flag does not exist, the receiver checks `KeyguardManager.isDeviceLocked` and, when locked, answers nothing and
  opens the app at that request instead (CR16-7).
- Widget snapshots hold redacted, bounded titles and no account, plan, address or error text (B-SEC-179–182).
- Tiles show counts only.

### 1.9 Native traps checklist (things the Flutter stack did for us, or never had)

Every builder reads this list once; each item has its rule above.

| # | Trap | Rule |
|---|---|---|
| 1 | Compose `LinkAnnotation.Url` opens through `LocalUriHandler` by itself | root override (§1.1) |
| 2 | `TextView` `autoLink` / `Linkify` inside `AndroidView` | banned (K-SEC-1) |
| 3 | Data class `toString()` prints passwords | `Secret` (§1.3, K-SEC-4) |
| 4 | `rememberSaveable` / `rememberTextFieldState()` persist secrets into the instance-state Bundle | `remember { TextFieldState() }` for secrets (K-SEC-5) |
| 5 | Ktor `HttpRedirect` is on by default; Ktor re-enables OkHttp `retryOnConnectionFailure(true)` | 04 N4-D2 |
| 6 | kotlinx.serialization 1.12 puts input JSON into exception text by default | `exceptionsWithDebugInfo = false` (04 N4-D17) |
| 7 | Exception messages carry URLs, hosts, file paths, aliases | 03 K15 + `Redactor.details` |
| 8 | `PendingIntent` mutability; implicit intents | K-SEC-7 |
| 9 | WebView defaults (file access, JS bridges, SSL errors, metrics, Safe Browsing) | §1.8.5 |
| 10 | `FileProvider` paths exposing `filesDir` | §1.8.3 |
| 11 | WorkManager `Data`, `Intent` extras and route keys are persisted by the system | ids only (K-SEC-5) |
| 12 | Libraries log at `d`/`i` to logcat | R8 strip (§1.2) |
| 13 | Clipboard previews on Android 13+ show copied text | `EXTRA_IS_SENSITIVE` (§1.3) |
| 14 | `kotlin.random.Random` is not cryptographic | `secureRandomBytes(n)` (`:core:common`, 10 C10-2) for tokens, passwords, nonces (K-SEC-12) |
| 15 | Keystore keys that need an unlocked device break night-time schedules | `setUnlockedDeviceRequired(false)` (§1.3) |
| 16 | Overlay tapjacking on API 26–30 (API 31+ blocks untrusted touches by default) | accepted residual (§3.1 T5) |
| 17 | AGP signs every debug build with `~/.android/debug.keystore`, which on the PC **is** the owner's release lineage, and native debug builds install (Shorebird Flutter's did not) | `.debug` package suffix; delivery refuses debuggable APKs (N16-D15) |
| 18 | `Notification.Action` `setAuthenticationRequired` exists only on API 31+ | locked-device check in the receiver on API 26–30 (CR16-7) |
| 19 | `Enum.name` and class simple names can be renamed by R8 in release | explicit `wire` strings for analytics (§5.2) |
| 20 | A library's merged manifest can add exported components | listed in §1.8.1 or removed with `tools:node="remove"` |

### 1.10 Native crashes and ANRs in diagnostics (DESKTOP-WEB-CROSS-54/55/57; 11 §1.1 rows 44, 45)

The native app keeps JNI code (sherpa-onnx, zxing-cpp, the Termux terminal-emulator PTY, proot launchers), and an ANR is
never seen by `NativeCrashStore`'s JVM handler. The rule is the Flutter one, made explicit for the new sources:

| Source | How it is seen | What is stored (only after the crash-report consent, opt-in, off by default) |
|---|---|---|
| JVM uncaught exception | `CrashHandler` → `NativeCrashStore` (03 §16.3) | `{source: platform, category, time}`; the previous handler gets the sanitised `RuntimeException("Native application error")` with an empty stack, always |
| Native crash (`REASON_CRASH_NATIVE`) | `AndroidExitHistory` reads `ApplicationExitInfo` at the next start (API 30+) | `{source: native, category: "Native application error", time}` |
| ANR (`REASON_ANR`) | same | `{source: anr, category: "Android reported that the app stopped responding", time}` |
| Exit classification | `ExitClassifier` (11 §1.1 row 44) | the typed exit in the problem report (`androidExit` entry), never `description` |

Never read, kept or sent: the tombstone and the ANR trace behind `ApplicationExitInfo.getTraceInputStream()` (API 30+ for
ANR traces, API 31+ for native tombstones). They hold memory contents, file paths and thread names that can carry chat
text, codes or tokens, and no redaction can prove them clean. **Release builds never call `getTraceInputStream`**
(Konsist rule in `:quality`, allowlisting only the `qa` source set). In the `qa` build type only, `QaTraceDump` may copy
the trace into `files/native/qa-traces/` for emulator debugging (memory `phone-logcat-silenced`: the owner's phone logs
nothing); it never ships (SI-17). Records follow the consent rules of DESKTOP-WEB-CROSS-54 unchanged: only exits newer
than the consent time are imported; turning the switch off erases them; `clear()` bumps the consent epoch so an old ANR
cannot come back. Tests: `ExitImportTest` (native and ANR exits become category-only records, only since consent, once),
`NoTraceReadTest` (`:quality`).

---

## 2. Enforcement

### 2.1 Konsist rules added to 03 §19.4 (`:quality`, K-SEC prefix)

| # | Rule | Scope |
|---|---|---|
| K-SEC-1 | No `Linkify`, `autoLinkMask`, `setAutoLinkMask`, `android:autoLink`; `LocalUriHandler provides` only in `:ui:shell` `App.kt` | everywhere |
| K-SEC-2 | `X509TrustManager`, `X509ExtendedTrustManager`, `TrustManagerFactory.init(`, `SSLContext.init(`, `HostnameVerifier`, `.sslSocketFactory(`, `.hostnameVerifier(` only in `:core:net-okhttp` | everywhere |
| K-SEC-3 | No `okhttp3.CertificatePinner`, no `HttpLoggingInterceptor`, no Ktor `Logging`, `HttpRequestRetry`, `HttpRedirect` installs (12 X-30, mirrored here so a reviewer sees it in one table) | everywhere |
| K-SEC-4 | A property named `(?i).*(password\|passwd\|secret\|token\|apikey\|api_key\|privatekey\|bearer\|credential).*` declared in a `data class` or `@Serializable` class, **whose declared type is `String`, `String?`, `CharSequence`, `CharArray` or `ByteArray`** (nullable or not), must have type `Secret` or `Secret?` instead. Exempt: any other type (Booleans, numbers, enums, `StateFlow`s, nested types such as 09's `val tokens: Tokens`); names ending in `Name`, `Id`, `Count`, `Rejected`, `Reentry` (03's `TransportAuth.Basic(val secretName: String)`, 04's `CredentialSwitched(credentialId: String?)`, `passwordRejected`, `secretReentry`); and the wire DTO packages listed in `config/secret-dto-allowlist.txt` (where the value is decoded only to be handed to `Secret(…)` at once). `monitorToken` (07/03) stays a `String`: it must travel in our own immutable PendingIntent extras, where K-SEC-5 bars `Secret`; it is the one entry of `config/secret-name-allowlist.txt` (with that reason) and is registered with the `Redactor`. `Secret.reveal()` calls only in files listed in `config/secret-reveal-allowlist.txt` (transports, `SecretStore`, `Redactor`, migration) | production code |
| K-SEC-5 | No `Secret` type in: a class implementing `NavKey`/`AppRoute`, a `SavedStateHandle` set, a `rememberSaveable`, `rememberTextFieldState`, `workDataOf`/`Data.Builder`, `Intent.putExtra`, `Bundle.put*` | production code |
| K-SEC-6 | `ClipData.newPlainText` / `ClipEntry(` only in `:ui:kit` `KitCopy`/`KitPlatform` | everywhere |
| K-SEC-7 | Every `PendingIntent.getActivity(`, `getBroadcast(`, `getService(` or `getForegroundService(` call passes an expression containing `FLAG_IMMUTABLE`, except the files in `config/mutable-pendingintent-allowlist.txt` (3 entries, §1.8.2: legacy reply, `PackageInstallerUpdater`, `:android:termux` `TermuxActivityCommands`; the last leaves with migrate-out) | `:android:*`, `:androidApp` |
| K-SEC-8 | No `Intent.parseUri`, `Intent.getIntent(`, and no `startActivity(`/`startActivities(` whose argument comes from `getParcelableExtra`/`getParcelable` — except `PackageInstallerUpdater.kt` | `:android:*`, `:androidApp` |
| K-SEC-9 | `WebView(` construction only in `KitWebPreview.kt`; `addJavascriptInterface`, `setAllowFileAccess(true)`, `setAllowUniversalAccessFromFileURLs`, `MIXED_CONTENT_ALWAYS_ALLOW`, `handler.proceed(` banned everywhere | everywhere |
| K-SEC-10 | Every `"https://` / `"http://` / `"ws://` / `"wss://` string literal in production code has a host listed in `KnownHosts` (fetch, redirect, link or shown set). Exempt: loopback hosts (`127.0.0.1`, `localhost`, `[::1]`: the phone server, Paseo and Codex on the phone are the person's own servers, not `KnownHosts`), the `src/qa/` source set, files named `*Fixtures.kt` and test source sets. Non-literal endpoints are covered elsewhere: the analytics endpoint by `verifyAnalyticsEndpoint` (§2.3) | production code |
| K-SEC-11 | Properties a class implementing `AnalyticsEvent` declares in its **primary constructor** are only of enum types, `Boolean`, `CountBucket`, `DurationBucket`, `VersionCode` (no `String`, `Int`, `Long`, collections, `JsonElement`). The inherited `name`/`tier` overrides are exempt: each event's companion is an `EventSpec(name, tier)` constant and the overrides are getters that return it (§5.2) | `:engine:diagnostics`, every emitter |
| K-SEC-12 | `kotlin.random.Random` and `java.util.Random` not imported in `:core:security`, `:engine:profiles`, `:engine:media` (`share/`), `:engine:diagnostics` (`analytics/`), `:android:linux`, `:android:platform`; tokens and passwords come from `secureRandomBytes` | listed modules |

| K-SEC-13 | A `String` template or concatenation whose result flows into `GitScript`, `GuestScript` or `GuestCommand` construction outside `ShellQuote` (`:engine:runtime`) fails; script text is built only from `ShellQuote.word`/`command` and fixed literals (SI-23, SI-25) | `:engine:project`, `:engine:runtime`, `:engine:agents`, `:android:linux`, `:android:agenthost` |

[U P9 in ADR §11 already proves Konsist sees `commonMain` in this layout; K-SEC rules ride the same harness.]

WP-16.2's planted-violation suite includes, for K-SEC-4: `data class Usage(val tokens: Tokens)` (must pass),
`data class Auth(val secretName: String)` (must pass), `data class Flags(val passwordRejected: Boolean)` (must pass),
`data class Provider(val apiKey: String)` (must fail), `data class Login(val password: CharArray)` (must fail).

### 2.2 Android lint severities (`androidApp/lint.xml` and the `oc.android.*` convention plugins)

`abortOnError = true`, no baseline (03 §19.5). These checks are raised to **error**:

`TrustAllX509TrustManager`, `BadHostnameVerifier`, `AllowAllHostnameVerifier`, `SSLCertificateSocketFactoryCreateSocket`,
`SSLCertificateSocketFactoryGetInsecure`, `WebViewClientOnReceivedSslError`, `AddJavascriptInterface`,
`SetJavaScriptEnabled` (one file-level suppression in `KitWebPreview.kt` with a reason comment),
`UnspecifiedImmutableFlag`, `MutableImplicitPendingIntent`, `UnsafeImplicitIntentLaunch`, `UnsafeIntentLaunch`,
`ExportedContentProvider`, `ExportedReceiver`, `ExportedService`, `ExportedPreferenceActivity`, `GrantAllUris`,
`WorldReadableFiles`, `WorldWriteableFiles`, `HardcodedDebugMode`, `SecureRandom`, `TrulyRandom`, `GetInstance`
(ECB/no-padding ciphers), `UnsafeDynamicallyLoadedCode`, `UnsafeNativeCodeLocation`, `PackageManagerGetSignatures`
(our API 26–27 fallback path in `PackageInstallerUpdater` carries a reasoned suppression), `AcceptsUserCertificates`,
`DataExtractionRules`, `AllowBackup`, `UnprotectedSMSBroadcastReceiver`.

`InsecureBaseConfiguration` fires on our `cleartextTrafficPermitted="true"` base config by design (04 §4.7): it is
suppressed in `lint.xml` for `network_security_config.xml` only, with the text "Kotlin's TrustGateInterceptor is the
cleartext boundary (04 §4.6, B-PROTO-139)".

### 2.3 Gates added by this section (T2)

Two kinds. Checks that read source files or plain resources are `:quality` JVM tests. Checks that need
`:androidApp`'s resolved classpath, merged manifest or built APK cannot be `:quality` tests (a JVM test in another
project cannot resolve `:androidApp`'s configurations or artifacts without cross-project wiring); they are
**build-logic verification tasks registered on `:androidApp`** and wired into T2's task list:

- `verifyOkHttpFloor` reads `configurations["stableReleaseRuntimeClasspath"].incoming.resolutionResult`;
- `verifyExportedSurface` consumes the merged manifest through the AGP Artifacts API
  (`androidComponents.onVariants { it.artifacts.get(SingleArtifact.MERGED_MANIFEST) }`) for `stableRelease`;
- `verifyQaHooksAbsent` and `verifyAnalyticsEndpoint` consume the `stableRelease` APK (`SingleArtifact.APK`) and the
  `analytics.properties` mapping task's output.

The configuration is `stableReleaseRuntimeClasspath` everywhere in this section.

| Gate | Kind | Checks | Rows |
|---|---|---|---|
| `verifyExportedSurface` | `:androidApp` task | §1.8.1, including `taskAffinity`, `launchMode` and the merged library components | B-SEC-194 |
| `NetworkSecurityConfigTest` | `:quality` | exactly the elements of §1.7 rule 4 (and 04 §4.7) | B-PROTO-139, B-SEC-193 |
| `FileProviderPathsTest` | `:quality` | §1.8.3: exactly the three roots | B-SEC-195 |
| `verifyOkHttpFloor` | `:androidApp` task | resolved `com.squareup.okhttp3:okhttp` ≥ 5.5.1 in `stableReleaseRuntimeClasspath` | B-SEC-193 |
| `verifyQaHooksAbsent` | `:androidApp` task (+ a script form for the release workflow, §7.3 step 13) | apkanalyzer/`dexdump` on the stable release APK, against 14 §4.4's mechanism: no class from `src/qa/` (the list is generated from the `src/qa/` source tree at build time, at least `QaHooksFileReader`, `EgressRecorder`, `QaFixtureAnalyticsSender`); no `qa-hooks.json` string in DEX or resources; no `10.0.2.2` string; versionName has no `-qa`; `aapt2 dump badging` shows no `application-debuggable`. A Metro graph smoke test on `stableRelease` (instrumented, T3) asserts `AppBuildInfo.qaHooks === QaHooks.None` and that the bound `AnalyticsSender` is not the qa fixture sender. Planted-violation run: a `stableRelease` built with `src/qa/` wired into `main` must fail | SI-17 |
| `verifyAnalyticsEndpoint` | `:androidApp` task | for every non-qa variant: `sender=none`, or `endpoint` is `https` and its host is a `KnownHosts` Fetch host with `Contacter.App` (§5.6 rule 5) | SI-14, SI-16 |
| `KnownHostsDocTest` | `:quality` | every `KnownHosts` Fetch host appears in `PRIVACY.md`'s destinations table and in `config/egress-allowlist.toml` | SI-14 |
| `PrivacyCoverageTest` | `:quality` | every consent of DESKTOP-WEB-CROSS-48 (kept in `config/consents.toml`) and every runtime permission in the merged-manifest snapshot (`config/exported-surface.txt`'s permission block) maps to a heading of `PRIVACY.md` (en and ar) | §4.3 |
| `SecRowCoverageTest` | `:quality` | §1 (after the SI table) | all B-SEC |
| `RepositoryHygieneTest` (ported from `test/repository_hygiene_test.dart`, extended) | `:quality` + `tool/release/leak_gate.sh` | no `*.jks`, `*.keystore`, `key.properties`, `*.p12` in git; no `.claude/skills/`; governance files present; non-affiliation statement at every public entry point; `SECURITY.md` present. **Leak gate** (owner memories `public-commit-email`, `push-freely-snapshot-recipe`, `release-1.2.0`): for the commits being pushed or tagged, author and committer email equal the noreply address `104366884+Eslamasabry@users.noreply.github.com`; added lines contain no owner gmail address, no `100.64.0.0/10` address and no build-VPS public address outside `native/e2e/fixtures/` and documentation ranges (`203.0.113.0/24`, `100.64.0.10`), and no device or host name from the git-ignored deny-list `native/config/private-denylist.txt` (absent file → the gate fails closed on the release path, skips with a warning on a work-branch push). `scripts/release.sh` runs it on the tag commit and records the result in the release verification record | B-SEC-150, B-BUILD-030 |
| `CanaryLeakTest` | `:quality` | scans `**/build/test-results/**/*.xml` and `**/build/reports/**` of the T2 run for every `CanarySecrets.ALL` value | SI-2 |

### 2.4 Unit-level tests this section owns (T1)

`ExternalLinkPolicyTest` (vectors §1.1), `UriHandlerRootTest`, `SecretTest` (`toString`, equality, no reveal in
`toString` of containing classes), `SecretFieldStateTest` (Robolectric: rotate the activity with a secret typed → the
restored field is empty and the Bundle has no canary), `KitCopySensitiveTest` (API 33 Robolectric: `EXTRA_IS_SENSITIVE`
set), `ProfileDeletionSweepTest` (§1.4), `ProviderKeyCanaryTest` (§1.5), `BasicAuthTransportTest` (header only after
the gate; 401 latches; no header on a redirect target because none is followed), `NotificationActionAuthTest`
(Robolectric: API 31+ `isAuthenticationRequired` on Allow once, question options, legacy reply, not on Reject; an
API 30 case where a locked `KeyguardManager` makes the receiver answer nothing and open the app at that request),
`TermuxResultPendingIntentTest` (§1.8.2), `CameraCaptureGrantTest` (§1.8.3), `UpdateVerifierTest` and
`UpdatePolicyTest` (§8), `AnalyticsPrivacyTest` (§5.10), `WifiShareFdTest` and `WifiShareExpiryTest` (§3.3),
`GuestEgressEnvTest`, `LinuxBackupPlanTest`, `LinuxRestoreSafetyTest` (§3.2), `KitWebPreviewTest` (§1.8.5).

### 2.5 The canary and egress harness (S-22's "egress test (section 16)")

Owner: test-writer lane (Sonnet) builds it in slice 2 **inside 14's runner** (CR16-12): the egress capture is one more
T3 step of `native/tool/e2e/run.py` (W14-6), and the canary half is 14's `canary_scan.sh` (R11), extended with this
section's targets and exceptions. This section supplies the scenarios, the allowlists and the attribution method; it
builds no runner or scanner of its own. It runs at T3 at every slice end and at the switch.

**Files** (in 14's tree):

```
native/tool/e2e/egress/
├── uid_capture.sh      adb root; installs the UID rules below, starts the capture, removes the rules at the end
└── egress_check.py     python3 -I; joins the UID log with the host-side pcap's DNS answers and TLS SNI; checks the allowlist
native/e2e/flows/egress/EG-n-<slug>.yaml (+ -qa.yaml companions, 14 §4.4)
native/config/egress-allowlist.toml      generated from KnownHosts (+ one section per scenario); checked by KnownHostsDocTest
```

**AVD**: any rooted image of 14 §4.6 (`OCN_A`, API 37 `google_apis`, is installed today). No AOSP `default` image and no
idle baseline are needed, because attribution is by UID (N16-D14): Play services, the TTS engine, the Photo Picker and
WebView component updates run under other UIDs and are simply not counted.

**Method**:

1. After install: `UID=$(adb shell stat -c %u /data/data/$PKG)`.
2. `adb root`, then for both families:
   `iptables -I OUTPUT -m owner --uid-owner $UID -j NFLOG --nflog-group 7` and the same with `ip6tables`; also
   `-j LOG --log-prefix "OCEG " --log-uid` as the fallback sink. `xt_owner` and `NFLOG` are required kernel options in
   `android-base.config` [S AOSP kernel config; U on the emulator kernel until P16-3].
3. Capture with `tcpdump -i nflog:7 -w build/egress/<scenario>-uid.pcap` inside the AVD if the image ships `tcpdump`
   [U P16-3]; otherwise read the `OCEG` lines from `dmesg`. Either way the result is the list of `(dst IP, port, proto)`
   the app's UID opened.
4. Names: the emulator also runs with `-tcpdump build/egress/<scenario>-host.pcap`; `egress_check.py` maps each
   UID-attributed IP to a name from that pcap's DNS answers and TLS SNI. The host pcap is used **only for names**, never
   for attribution.
5. Every UID-attributed destination must match the scenario's allowlist (glob on names; `10.0.2.2:<port>` for
   fixtures). An unattributable IP (no DNS answer, no SNI) fails with the address.

This covers OkHttp, WebView (its network stack runs in the app's process; renderers are sandboxed and have no network),
the guest Linux (every guest process runs as the app's UID), and nothing else. `LocalPdfService` runs as an isolated
UID with no network at all. The `stableQa` `EgressRecorder` (an OkHttp `EventListener` writing `dnsStart`/`connectStart`
to `files/native/qa/egress.jsonl`) stays only to **label** each host with the feature that contacted it in the report;
14's fx-proxy connection log is a cross-check. Neither can make a run pass.

**Scenarios** (`egress-allowlist.toml`; each EG-n is a Maestro flow in 14's flow tree):

| Id | Variant | Steps | Allowed | Rows |
|---|---|---|---|---|
| EG-1 | `stableQa` with `updateFeed = {expectedPublicSigner: <local key>}` only (feed URL and asset prefix stay the real GitHub ones, N16-D13) | fresh install, first run, no servers, 5 min idle, open Settings › This app › Updates, Check now (the real `releases/latest` answers v2.x, so `NotNewer`, silent) | `api.github.com` (+ the analytics host only after D16-1) | B-SEC-197 |
| EG-2 | `stableQa` with `analyticsEndpoint = http://10.0.2.2:<fx-analytics>/analytics`, DETAILED on | daily path: OC2 fixture at `10.0.2.2:4096` (consented HTTP), open chat, stream a reply, approve, attach a photo, read aloud with the system voice | EG-1 + `10.0.2.2:4096` + the analytics fixture port | B-SEC-197, 191 |
| EG-3 | `stableRelease` | Paseo + Codex fixtures, steer, stop | EG-1 + fixture ports | |
| EG-4 | `stableRelease` | voice pack download (Balanced) | EG-1 + `huggingface.co`, `*.hf.co` | B-MEDIA-031 |
| EG-5 | `stableRelease` | phone setup (built-in Linux, OpenCode only), start the phone's OpenCode 2 server, idle 10 min, open the model picker, then make a Linux backup | EG-1 + `cdimage.ubuntu.com`, `archive.ubuntu.com`/`ports.ubuntu.com`, `security.ubuntu.com`, `nodejs.org`, `registry.npmjs.org`, and the `GuestLinux` server hosts of §4.2 (`models.dev` for the model catalogue refresh, plus whatever P16-6 records) | B-SEC-092, 097, 171, 202 |
| EG-6 | `stableQa` with the full `updateFeed` override (`feedUrl`, `assetPrefix` = the FX-GH fixture origin on `http://10.0.2.2:<port>`, `expectedPublicSigner` = local key) | Check now → Update now → install | fixture host only | B-SEC-189 |
| EG-7 | `stableRelease` | Wi-Fi share started and stopped, no client | nothing outbound | B-MEDIA-034 |

**Canary scan** (same run, 14's `canary_scan.sh` with this section's targets): the fixtures serve `CanarySecrets.ALL`;
after each scenario it greps `adb logcat -d`, `adb shell dumpsys notification --noredact`, the exported problem report
text (captured through the `qa` share hook), the analytics queue (`files/native/analytics/queue.json`) and the "What
was sent" text (copied through the `qa` share hook), the EG-5 backup zip (unpacked), and every file under
`/data/data/<pkg>/` with these exceptions:

- ciphertext stores: `shared_prefs/oc_secrets_v1.xml`, `shared_prefs/FlutterSecureStorage*.xml`,
  `FlutterSecureKeyStorage.xml`, and their step-0 copies in `files/migration/flutter-v57/`;
- the rootfs `files/linux/ubuntu/**` is out of scope (the guest is one trust zone, §3.2), **except** the files the app
  itself writes there (`RuntimeHost.appWrittenFiles()`), where only the documented secret files may contain their own
  secret: `/root/.oc-builtin/server.password` (phone-server password), `/home/oc/.oc-profiles/<id>/paseo/config.json`
  (agent-host password), the quota token file, and a GitHub token file only while its command runs (B-SEC-081).

The EG-2 and EG-5 runs fail if the analytics queue, the sent log or the backup zip is empty when the scenario should
have produced it, so the scan can never pass on missing data. Any hit fails with the file and byte offset. Results go
to `docs/qa/native/egress-<YYYY-MM-DD>/README.md` (audit-trail rule).

---

## 3. Threat model (native)

The Flutter threat model (`docs/security/threat-model.md`, 2026-10-02 [R]) carries over scenario by scenario. The native
copy lives at `native/docs/security/threat-model.md` (WP-16.10), with the same "every claim names its file" rule.

### 3.1 Scenarios

| # | Attacker | Can do | Native mitigation | Residual (accepted unless a D16 says otherwise) |
|---|---|---|---|---|
| T1 | Someone holding the **unlocked** phone | approve commands, read chats, send prompts | none in-app; Keystore; backup off | No app lock: the owner closed #37 "not planned" on 2026-10-31 ("don't complicate things") [R memory]. Removing a stolen phone needs the server password changed. |
| T2 | Someone holding the **locked** phone | see notifications, tap actions | `VISIBILITY_PRIVATE` + count-only public; Allow once needs unlock; Always allow never outside the app; CR16-7 makes question options and legacy reply need unlock | the person can raise lock-screen visibility per channel in system settings |
| T3 | A malicious or compromised **server** | misleading permission text, huge payloads, hostile links, forged events, provider keys in replies | exact request binding (B-SEC-183); SI-1 link policy; SSE 16 MiB frame cap and per-call body caps (04); no redirects; credential ingress registration; plain words only; gen-UI card validator (09); SVG sanitizer, XML without DTD, PDF in an isolated process (10 §17) | the person approves a *description* written by the server's agent |
| T4 | **LAN / Wi-Fi** attacker | read or alter consented plain HTTP | consent per exact origin; pins checked in the handshake; no downgrade; Tailscale recommended in the words | after consent, password and approvals travel unencrypted on that network |
| T5 | **Another app on the same phone** | connect to `127.0.0.1` listeners; send intents; draw overlays | phone server Basic auth (32 random bytes); Paseo agent host password (64 hex); quota collector bearer; exported surface frozen (§1.8.1); immutable PendingIntents; deep links ids only; overlay touches blocked by Android 12+ | AI Team supervisor has no auth (CR16-9, blocks its port); dev servers agents start are the person's own and unauthenticated; vendor sign-in callback listeners (e.g. `127.0.0.1:1455`) live during a sign-in, protected by OAuth `state` (L3); tapjacking on API 26–30 |
| T6 | **Hostile content** read by an agent (prompt injection from a repo or web page) | make an agent run commands or read files; build or download an APK and get it installed | approvals; auto-approval off by default and never "always" from a notification (B-SEC-080, 147); D16-4 confines the guest's view of shared storage; **agent-made APK install**: only from the phone-file sheet, never offered from a notification or widget; the sheet names the file and says an agent saved it; Android's installer screen is the only confirmation and asks every time | an agent the person allowed to run tools acts with that permission; once "Install unknown apps" is granted for updates, an agent-saved APK is one system confirmation away |
| T7 | Code inside the **built-in Linux** | anything the app UID can do inside the rootfs | §3.2 | one trust zone |
| T8 | A device on the same **Wi-Fi during a share** | download the shared file | §3.3 | the link travels in clear on that Wi-Fi |
| T9 | **Update channel / supply chain** | ship a malicious APK, library or binary | §3.4 | an attacker holding our signing key |
| T10 | Anyone during the **migration window** | read decrypted secrets | secrets decrypted in memory only, registered with the `Redactor` before any log line; step-0 backup copies only ciphertext files and prefs; no secret in the migration report (05 §7, B-MIGR-011) | the backup folder keeps the Flutter files until the v3 cleanup (05 §9.5) |
| T11 | The **analytics endpoint** operator | learn usage patterns | §5: enums only, no ids, no content, BASIC tiny, DETAILED opt-in | the endpoint sees the phone's IP (disclosed) |
| T12 | A person who receives a **problem report** | read secrets in it | redacted, previewed, shared only by the person (B-SEC-041–060) | pattern-based redaction misses unregistered odd-shaped secrets |
| T13 | A **rooted** or debuggable device | read prefs, use Keystore through our process | Keystore non-exportable; release non-debuggable | prefs (drafts, queue) are plaintext in the sandbox, as today |
| T14 | **CI / build** compromise | inject code before signing | actions pinned by SHA; Gradle dependency verification (12 §9.2); signer checks; secrets only in the release job; reproducible builds let anyone check (§9) | a compromised GitHub account with release-secret access |

### 3.2 The built-in Linux (T7)

Facts the design accepts [R builtin-linux map; `docs/verification/in-app-ubuntu-trust-2026-10-07.md`]: every process in
the guest runs as the app's single Android UID; proot changes what a process **sees**, not what it **can do**; Landlock is
not in Android GKI kernels (measured 2026-10-07); an agent can read `/proc/<pid>/environ` of the OpenCode server (which
holds its password) and can rewrite `/usr`, `/opt` and the server binary. The 2026-10-07 record's option A ("accept one
trust zone and say so") is the shipped stance.

Rules for the native port:

| # | Rule | Where |
|---|---|---|
| L1 | Say it plainly: This phone › Linux › About shows "Programs in this Linux can see each other's files and settings. Only install agents and tools you trust." | `:feature:phone` copy (D16 none; parity text exists today) |
| L2 | The app never trusts output from inside the guest for a security decision: attestation receipts are signed **outside** (Keystore, `PhoneEngineAttestation`), process identity is `(pid, start-ticks, uid)` read from `/proc` by Kotlin, never from guest output | `:android:linux` (kept verbatim; "never simplify `NativeRuntimeOwnership.plan`") |
| L3 | Every **long-running service** (`ServiceSpec`) the app starts in the guest binds `127.0.0.1` **and** authenticates: OpenCode 4097 (Basic), Paseo 4099 (password), quota collector (bearer), phone engine (credentials). Such a service without auth may not ship; AI Team's supervisor (8472) gets auth before AI Team is ported (CR16-9). One exception, by design of the vendors: the OAuth callback listeners that agent sign-in programs (`SIGN_IN_PROGRAMS`, e.g. `codex login` on `127.0.0.1:1455`) open themselves. They live only during that sign-in, are loopback-bound, rely on the OAuth `state` parameter, and the sign-in session kills them on cancel, finish or timeout | `:engine:runtime` start specs; `ListenerAuthTest` asserts every `ServiceSpec` with a port has an auth kind and that sign-in sessions have a timeout and kill their process tree |
| L4 | Secrets reach the guest only as 0600 files written from the app side (`umask 077`, temp + rename), never on a command line or in a script body; GitHub tokens as one-use 0600 files in a 0700 random folder, deleted after the command (B-SEC-081) | `:android:linux`, `:engine:project` (11 §6.2 step 3 already says "never on a command line") |
| L5 | Agents run with `--change-id=1000:1000`, their own `HOME`, the `/root` view replaced; no secrets in agent profiles (option A's cheap defences) | `ProotLauncher` (11 §5.3) |
| L6 | **Shared storage**: with D16-4 the guest sees only folders opened as projects (today's "confined mode" becomes the default) **plus the outbox** `/storage/emulated/0/Download/OpenCode` (read-write, created on demand), which the phone-context instruction names as the place to save files for the person; the phone-file sheet opens, shares and installs from it (10 §12). A visible row "Let Linux see all phone files" (off by default) restores `--bind=/storage`; its warning reads "Programs in this Linux can then read every file on your phone's storage." The confined row's help line says "Agents save files for you in Download › OpenCode." | `SharedStorageBinds`, `:feature:phone`, `:engine:agents` (phone-context text) |
| L7 | Everything the app downloads into the guest is HTTPS, pinned by version and SHA-256, from a `KnownHosts` host (B-SEC-092, 171); manifests never point at private addresses | `:engine:runtime` setup engine, `:engine:agents` catalog |
| L8 | npm installs pin exact versions; the registry's `integrity` (sha512) is checked by npm itself; lifecycle scripts run (agents need them) — accepted | runtime scripts |
| L9 | Executables run only from `nativeLibraryDir` (proot set) or inside proot; the app never `System.load`s a writable file (Android 17 Safer Native DCL: loaded files must be read-only [V]) | `:android:linux`, `:android:media` |
| L10 | The guest trust store is refreshed by the layer-2 updater (TI-3) | runtime |
| L11 | Dev servers started by agents may bind `0.0.0.0` and become reachable from the Wi-Fi. Post-switch improvement (not parity): the Linux manager probes each detected port on the phone's Wi-Fi address and labels "Others on this Wi-Fi can open this". The phone-context instruction injected into agents (roadmap 2026-10-31 bug 3) asks them to bind `127.0.0.1` | `:engine:runtime` (later), `:engine:agents` |
| L12 | **Every program the app starts in the guest gets the fixed egress environment** (parity with the freeze tip [V `BuiltinLinux.kt:775`, `:864`, `PhoneAgentHost.kt:230`, `phone_agents_host.dart:590`, `:943-956`]; B-SEC-097). Paseo: env `PASEO_RELAY_ENABLED=false`, `PASEO_WEB_UI_ENABLED=false`, `PASEO_DICTATION_ENABLED=false`, `PASEO_VOICE_MODE_ENABLED=false`, `PASEO_SERVICE_PROXY_ENABLED=false`, `PASEO_LISTEN=127.0.0.1:<port>`, and `config.json` `daemon.relay.enabled=false`, `daemon.serviceProxy.enabled=false`, `daemon.mcp.injectIntoAgents=false`, `features.{webUi,dictation,voiceMode}.enabled=false`. Every agent process and agent sign-in session: `DISABLE_AUTOUPDATER=1`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`. The phone's OpenCode 2 chat server: `OPENCODE_DISABLE_MODELS_FETCH=1` once P13-5 proves 2.1.1 honours it (13 PB-56; the probe and AI Team already set it), and its own update check off if P16-6 finds one (`autoupdate` off in the start spec, recorded in the trust inventory). Turning the Paseo relay on would expose the phone's agents through Paseo's cloud relay; no setting, row or agent may change these values | `:engine:runtime` start specs, `:engine:agents`, `:android:agenthost`; `GuestEgressEnvTest` asserts every `ServiceSpec`, agent launch and sign-in session carries them (CR16-16) |
| L13 | **Linux backup and restore** (switch scope, 02; BUILTIN-LINUX-48; owner decision Q4: no private keys in backups). The backup zip goes to shared storage through "Save to", so it holds only the allowlist: `manifest.json`, `apt-manual.txt`, `npm-global.json`, `pipx.txt`, `home.tar.gz` (`.bashrc .bash_profile .bash_aliases .profile .inputrc .gitconfig .vimrc .tmux.conf .config/{git,nvim,htop} .ssh/{authorized_keys,config,known_hosts}`), `services.tar.gz`, optional `projects.tar.gz` (without `node_modules .venv venv __pycache__ build dist .dart_tool .gradle target .next`). **Never**: sign-ins, tokens, `.env*`, keys, `.ssh/id_*`, `.gnupg`, `.aws`, `.docker`, `.claude`, `.config/gh`, `.config/opencode`, `.local/share/opencode`, `.oc-opencode2`, the agent homes `/home/oc/.oc-profiles/**`, or a project whose `.git/config` remote carries a password. Restore extracts only the 7 allowlisted entry names; refuses absolute paths, `..` and symlink entries; installs missing packages by name; keeps a replaced file as `<name>.before-restore`; never overwrites a project (a name collision becomes `name (restored)`) | `:engine:runtime`, `:feature:phone`; `LinuxBackupPlanTest` (allowlist and denylist verbatim, a planted `.ssh/id_ed25519`, `.env`, `.claude/.credentials.json` and password remote are absent), `LinuxRestoreSafetyTest` (traversal, absolute, symlink and unknown entries refused; collisions renamed); EG-5 canary-scans the zip |

### 3.3 Wi-Fi share (T8)

10 §13 is the implementation (192-bit token, Wi-Fi/hotspot interface only, GET/HEAD, constant-time compare, `nosniff`,
`no-store`, 15 minutes, never app-private files, B-MEDIA-034, B-SEC-022). Threat notes and the two tightenings
(N16-D11):

| Threat | Effect | Mitigation | Status |
|---|---|---|---|
| Sniffing an open or shared Wi-Fi | the token travels in the URL in clear; a sniffer can download within the 15 minutes or alter the file in transit | the sheet's warning "Anyone on this Wi-Fi with the link can download this file."; one file; read-only; "Downloaded N times" lets the person notice extra downloads | accepted (it is plain HTTP by design, so a phone browser can open it without certificates) |
| Swap after check | a symlink inside an allowed root (or a parent folder of the file) is swapped to point at a secret between `PathSafety`'s check and the open | **open first, then check the open descriptor**: `val fd = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)`; wrap it (`ParcelFileDescriptor.dup(fd)`, then close `fd`) to get the int `n`; read the real path with `Os.readlink("/proc/self/fd/$n")`; run `PathSafety` (inside an allowed root, not `looksSecret`, not app-private) on **that** path; require `OsConstants.S_ISREG(Os.fstat(pfd.fileDescriptor).st_mode)`; refuse otherwise. `O_NOFOLLOW` guards only the last component, but the `/proc/self/fd` path is what was actually opened, so a swapped parent is caught too. Keep the descriptor and serve every request from it with `pread`/`position`; close at stop | new, B-SEC-192 |
| Frozen process outlives 15 min | Android freezes the app; the timer never fires; on thaw the server still accepts | every accepted connection checks `clock.now() < endsAt` first; otherwise answer `410 Gone`, close, and stop the share | new, B-SEC-192 |
| Token leaks through the app | logs, diagnostics, analytics, clipboard | the token is registered with the `Redactor` at start; `wifi_share_started` has no fields (10 §19.5); Copy link uses `sensitive = true` | B-SEC-188 |
| Resource exhaustion | many connections | ≤ 4 at once, 10 s socket timeout, 8 KiB request head (10 §13.3) | parity |
| Hotspot clients | the phone's own hotspot clients can reach it | same token rule | accepted |
| targetSdk 37 | inbound LAN needs `ACCESS_LOCAL_NETWORK` [V] | the targetSdk-37 slice (§11) | later |

### 3.4 Updates and supply chain (T9)

| Path | Attack | Mitigation |
|---|---|---|
| In-app update | network attacker swaps the APK | TLS to GitHub with platform trust; `SHA256SUMS` match; signer equality; package and versionCode checks (§8.3); Android itself refuses a different signer. The redirect host set (`*.githubusercontent.com`) is deliberately wide: the host check adds little, the checksum and signer checks decide |
| In-app update | compromised GitHub account publishes a malicious release | the attacker lacks the signing key → signer check fails and Android refuses; the release job needs repository secrets (keep 2FA, protected tag rules, environment approval for the release job) |
| In-app update | downgrade to an old vulnerable build | versionCode must be strictly greater (B-BUILD-010); Android refuses downgrades on release builds |
| Libraries | tampered artifact on a mirror or JitPack rebuild | `verification-metadata.xml` SHA-256 for every artifact, strict mode (12 §9.2); `exclusiveContent` repositories (12 §9.1) |
| Vendored binaries | swapped sherpa-onnx AAR, proot `.so` | `third_party/SHA256SUMS` + `ThirdPartyHashTest`; proot debs fetched by SHA-256 (12 §9.3) |
| Guest downloads | swapped rootfs, Node, OpenCode, agent tarballs | pinned SHA-256 per file, checked before naming/running (B-SEC-092) |
| CI | a compromised third-party Action | every Action pinned by commit SHA with its version in a comment (today's rule, kept); release job permissions `contents: write` only; `gradle/actions/wrapper-validation` pinned |
| Signing key theft | anything | §6.3 key custody |

---

## 4. Privacy

### 4.1 Data stays on the phone unless the person sends it

Unchanged from today (PRIVACY.md 2026-09-06 [R]), restated as native rules:

- Prompts, files, approvals and terminal input go only to the server the person chose.
- Diagnostics: the error list (20 errors) and the 256 KiB log ring stay in memory; the problem report is **persisted**
  redacted to `diagnostics/report_problem.json` (128 events / 256 KiB, B-SEC-126, 127, 148) so it survives a crash, and
  is erased by Clear and by "Delete saved crash reports". It leaves the phone only when the person shares it (system
  share or the GitHub issue link), after an exact preview (B-SEC-047–060); there is no send-to-server action.
- Crash capture stays **off by default** and category-only (B-SEC-042, 043).
- No backup, no device-to-device transfer of app data (11 §3).
- Widgets and tiles show titles or counts only.
- New in native: the analytics tiers (§5), disclosed on the Privacy page and in PRIVACY.md.

### 4.2 `KnownHosts` (N16-D7)

```kotlin
// :core:security commonMain
public enum class HostUse { Fetch, Redirect, Link, Shown }   // app fetches / a fetch may be redirected to / opened in a browser only / only displayed or copied, never fetched
public enum class Contacter { App, GuestLinux }
public data class KnownHost(
    val host: String,                  // exact name, or "*.suffix" for Redirect sets
    val use: HostUse,
    val by: Contacter,
    val purposeKey: String,            // :ui:words key for PRIVACY/Settings text, e.g. "privacy_host_update_check"
    val trigger: String,               // plain English for the generated docs table, e.g. "app start and resume, at most every 6 h"
)
public object KnownHosts {
    public val all: List<KnownHost>
    public fun redirectSet(feature: DownloadFeature): Set<String>   // used by ResumableDownloader (CR16-5)
}
public enum class DownloadFeature { AppUpdate, VoiceModels, RuntimeRootfs, ProviderLogos, McpRegistry }
```

Initial content (from the Flutter source today [V grep of `lib/`] and the redirect checks of CR16-5):

| Host | Use | By | When | What it learns |
|---|---|---|---|---|
| the person's servers | Fetch | App | always while connected | everything the person sends |
| `api.github.com` | Fetch | App | update check: app start and resume, at most every 6 h, switch on (§8); Git page sign-in: who you are signed in as (`GET /user`, the GitHub token is sent as Bearer [V `lib/domain/git/github_device_flow.dart:24`]) | IP, `User-Agent: OpenCodeMobile`; for `/user` the person's GitHub token and so their account |
| `github.com` | Fetch | App | Update now (asset), Git page device sign-in (`/login/device/code`, `/login/oauth/access_token`) | IP; the device code |
| `*.githubusercontent.com` (today `release-assets.githubusercontent.com`) | Redirect | App | update download (`AppUpdate` redirect set, CR16-5) | IP |
| `huggingface.co`, `*.hf.co` | Fetch, Redirect | App | the person picks a voice pack, the natural voice, or (if ported) the freeze tip's on-device assistant model `litert-community/gemma-4-E4B-it-litert-lm` [R `lib/local_llm/local_llm_pack.dart`] | IP, the pack's file names |
| `models.dev` | Fetch | App | a provider with no bundled mark is shown (weekly cache) | IP, the provider id |
| `registry.modelcontextprotocol.io` | Fetch | App | explicit MCP catalog refresh only (B-SEC-168) | IP, the request |
| `cdimage.ubuntu.com` | Fetch | App | phone setup (Ubuntu Base) | IP |
| `archive.ubuntu.com`, `ports.ubuntu.com`, `security.ubuntu.com` | Fetch | GuestLinux | setup, package installs, Linux manager | IP, package names |
| `nodejs.org` | Fetch | GuestLinux | setup | IP |
| `registry.npmjs.org` | Fetch | GuestLinux | setup, agent installs, layer-2 updates | IP, package names |
| `downloads.claude.ai` | Fetch | GuestLinux | Claude Code install (if chosen) | IP |
| `github.com` + `release-assets…` | Fetch, Redirect | GuestLinux | pinned agent binaries (Codex, Goose, Oh My Pi) | IP |
| `models.dev` | Fetch | GuestLinux | the phone's OpenCode 2 server: model catalogue refresh at start and periodically until PB-56 lands (the client handles `models-dev.refreshed`, [V `lib/api2/events.dart:401`]), then only when the model picker opens | IP |
| hosts P16-6 records for the pinned OpenCode 2.1.1 phone server (its own update check, plugin installs, share host) | Fetch | GuestLinux | as recorded; each is either allowed here with its purpose or switched off in the start spec (L12) | as recorded |
| the analytics endpoint (D16-1) | Fetch | App | §5.7 | §5.4 |
| `claude.com`, `auth.openai.com`, `console.anthropic.com`, `aistudio.google.com`, `tailscale.com`, `f-droid.org`, `vercel.com`, `agentclientprotocol.com`, `docs` pages | Link | App | only when the person taps | (browser) |
| `play.google.com` | Link | App | Tailscale setup: "Get Tailscale" | (browser / Play) |
| `eslamasabry.github.io` | Link | App | About › website | (browser) |
| `github.com/…/issues/new` | Link | App | Report a problem › open a GitHub issue (prefilled with the previewed, redacted text) | what the person submits |
| `raw.githubusercontent.com` | Shown | App | the copyable host-script command on the setup page (`lib/ui/setup_commands.dart:70`); the app never fetches it | — |

Model providers that agents and servers call (Anthropic, OpenAI, …) are the person's own agents' traffic, not the app's;
PRIVACY.md says so in one sentence.

### 4.3 PRIVACY.md rewrite (switch gate item 5; WP-16.10)

Written in plain words, en + ar, bundled into `:ui:words` `composeResources/files/privacy/` (07 L07 sheet). Rule:
**keep every current section that describes a switch-scope feature, rewritten for native** (the list of
DESKTOP-WEB-CROSS-49), and add the new ones. Required sections, in order:

1. Who we are (independent project; non-affiliation statement).
2. What the app sends and to whom: the generated `KnownHosts` table (§4.2), grouped "always", "when you ask", "inside
   your phone's Linux" (including the phone's OpenCode server's own traffic).
3. Analytics (§5): what BASIC sends (the exact event table), that it is always on, what DETAILED adds, that it is off
   unless turned on, the endpoint operator's name and location, that the endpoint sees the IP, that events made before
   a service is chosen wait on the phone (§5.7), how to see what was sent.
4. Updates: the GitHub check, the switch to turn it off, what GitHub sees.
5. What stays on the phone; secure storage (passwords in Keystore; other app data not separately encrypted from the
   sandbox); no backup; deleting a server deletes its data, including the copies kept from the 2.x app (§1.4);
   Manage space deletes all.
6. Plain HTTP: per-address consent and what it means (from 04 §4.2 wording; replaces today's stale paragraph, CR16-4).
7. Notifications, widgets, shortcuts, tiles: titles or counts only.
8. App diagnostics and problem reports: the persisted redacted report, optional crash categories (off by default),
   no send-to-server, the GitHub issue link (§4.1).
9. Microphone and on-device voice: audio stays on the phone; voice packs come from Hugging Face on request.
10. Read aloud: prose is sent to the separately installed system speech engine, only after `oc.readAloudConsent`.
11. Camera and photos: the system camera and photo picker; pending-photo recovery keeps the picture only until it is
    sent or discarded.
12. Remaining-usage collector: consent per server, exact origin, no redirect, no Claude collection.
13. Pending provider sign-in recovery: no URL, code, answers or tokens kept.
14. GitHub sign-in on the Git page: the token is kept in secure storage and sent only to GitHub.
15. Files, terminal, the built-in Linux (one trust zone, L1), Linux backups (what they never contain, L13), the agent
    outbox folder (L6), and Termux `RUN_COMMAND` while migrate-out exists.
16. Wi-Fi share: the link travels in clear on that Wi-Fi; 15 minutes; one file.
17. Provider and agent logos (`models.dev`).
18. Permissions: the native app's real list, generated from the merged manifest (including All files access and what
    D16-4 changes, and Install unknown apps with both uses).
19. Removed: the Shorebird paragraph; Termux-only claims once migrate-out ends; AI Team plugin paragraphs until AI Team
    returns (ADR D6).
20. Effective date and how to report a problem (SECURITY.md).

`KnownHostsDocTest` keeps section 2 in step with code; `PrivacyCoverageTest` (§2.3) maps every consent in the
DESKTOP-WEB-CROSS-48 inventory and every runtime permission to a heading.

---

## 5. The analytics core (owner decision 2026-11-01)

**Since 2026-11-01 the analytics core has its own section, 18-analytics.md, and its own module `:engine:analytics`
(03 S27).** Section 18 owns the event schema, the tiers and their lists, the identifiers, the queue and upload policy
and the sinks; where §5.1–§5.7 below differ from it, 18 wins. This section keeps §5.4 (never collected), §5.5's wire
formats as the reference for each sink, §5.8 (what the person sees, now with the "What we collect" page, 18 §6), §5.9
and §5.10. Changed by the owner's decision: BASIC now includes crashes and freezes (category only), start-up time,
daily use counts, server and agent kinds as counts, error categories and the device class (18 §2.1); a random install
id rotating every 30 days is sent (18 §3); background uploads go only over unmetered networks, never mobile data
(18 §4.3); the F-Droid choice waits for the submission (18 §7).

Owner's words (memory `freeze-owner-decisions-2026-11-01`, item 6): "Let's build tracking SDK day one that can later talk
to other analytics apps." "Opt in for detailed but basics we need regardless." The memory adds: one event API, pluggable
senders (Aptabase, PostHog, Firebase, Plausible, self-hosted), privacy rules (never content, paths, addresses or
credentials), BASIC always on (anonymous, minimal, disclosed), DETAILED opt-in, and the F-Droid "Tracking" caveat.

### 5.1 Shape (N16-D9)

```
emitters (engines, ViewModels via engine API) ──track(AnalyticsEvent)──► Analytics
   └► PrivacyGate (tier vs consent, migration gate, qa/debug routing)
        └► EventQueue (files/native/analytics/queue.json; 500 events / 256 KiB; 7 days, one-time switch events 90 days)
             └► Uploader (on background and every 30 min in foreground; batches ≤ 25; backoff 1 min → 6 h; idle while sender = None)
                  └► AnalyticsSender (exactly one, bound at build time: None | Aptabase | PostHog | Plausible | Custom;
                     stableQa only: QaFixtureAnalyticsSender)
                       └► SentLog (last 50 payloads, memory only, for "What was sent")
```

Module: `:engine:diagnostics`, package `io.github.eslamasabry.oc.engine.diagnostics.analytics` (engine order #2, so every
engine above it may emit; `:engine:settings` changes are emitted by `:engine:diagnostics` observing settings flows).
Senders use `HttpClientFactory` from `:core:net` (allowed for engines, ADR §6.1). No vendor SDK (12 X-04).

### 5.2 Public API

```kotlin
package io.github.eslamasabry.oc.engine.diagnostics.analytics

public enum class AnalyticsTier { Basic, Detailed }
public class EventSpec(public val name: String, public val tier: AnalyticsTier)   // name: snake_case, unique, ≤ 40 chars

/** Every event is a class in AnalyticsSchema.kt. Constructor properties: enums, Boolean, buckets only (K-SEC-11). */
public sealed interface AnalyticsEvent {
    public val spec: EventSpec                       // a getter returning the companion constant; never a constructor property
    public val name: String get() = spec.name
    public val tier: AnalyticsTier get() = spec.tier
}
// Example:
// public data class MigrationDone(val outcome: MigrationOutcome, val failedStep: MigrationStep,
//     val secretsReentry: CountBucket, val duration: DurationBucket) : AnalyticsEvent {
//     override val spec: EventSpec get() = SPEC
//     public companion object { public val SPEC: EventSpec = EventSpec("migration_done", AnalyticsTier.Basic) }
// }

/** Every enum used as an event property implements this; the wire value never comes from Enum.name (R8 may rename it). */
public interface WireEnum { public val wire: String }

public interface Analytics {
    /** Never throws, never blocks, never suspends. Drops the event when its tier is not permitted. */
    public fun track(event: AnalyticsEvent)
    public val detailedEnabled: StateFlow<Boolean>
    public suspend fun setDetailedEnabled(enabled: Boolean): Outcome<Unit>   // false also drops queued DETAILED events
    public val sent: StateFlow<ImmutableList<SentPayload>>                    // for "What was sent"; memory only
}

public data class SentPayload(val atMs: Long, val senderId: SenderId, val json: String)   // json is exactly the body sent

@JvmInline public value class VersionCode(public val value: Int)
public enum class CountBucket(override val wire: String) : WireEnum { Zero("0"), One("1"), TwoToFive("2-5"), SixToTwenty("6-20"), MoreThanTwenty("21+") }
public enum class DurationBucket(override val wire: String) : WireEnum { Under100Ms("lt100ms"), Under300Ms("lt300ms"), Under1S("lt1s"), Under3S("lt3s"), Under10S("lt10s"), Under60S("lt60s"), Longer("ge60s") }

/** One constant per AppRoute, with a stable wire name. RouteIdCompletenessTest (:quality) checks it against the routes SerializersModule. */
public enum class RouteId(override val wire: String) : WireEnum { /* ChatList("chat_list"), Chat("chat"), … generated or hand-kept */ }
/** Exhaustive over Failure: `Failure.toCategory()` is a `when` with no else branch, so a new Failure subclass breaks the build. */
public enum class FailureCategory(override val wire: String) : WireEnum { /* Network("network"), Auth("auth"), … */ }
/** One constant per setting that may be reported; values only for enumerated settings, each with its own WireEnum. */
public enum class SettingId(override val wire: String) : WireEnum { /* Theme("theme"), TextSize("text_size"), … */ }

public enum class SenderId { None, Aptabase, PostHog, Plausible, Custom, QaFixture }
public interface AnalyticsSender {
    public val id: SenderId
    public val host: String?                                   // null for None; a KnownHosts Fetch host for every non-qa sender
    public suspend fun send(batch: List<WireEvent>): Outcome<Unit>
}
public data class WireEvent(
    val name: String,
    val hourUtc: Long,                                          // epoch ms truncated to the hour
    val session: String,                                        // random UUID per process, rotated after 60 min idle (see §5.6 rule 7)
    val system: SystemProps,
    val props: Map<String, String>,                             // WireEnum.wire values / "true"/"false" only
)
public data class SystemProps(
    val appVersion: String, val appBuild: Int, val channel: String,   // "stable" | "preview" | "desktop"
    val osApi: Int, val lang: String, val isDebug: Boolean,           // lang = one of the app's 7 language codes
)
```

`WireEvent` is built at `track` time (so the session is the one of the process that saw the event) and queued as is.

### 5.3 Events at the switch (`AnalyticsSchema.kt`, the single source)

BASIC (always on; the "basics we need" — chosen to answer "is the native switch working?"):

| Event | When | Props |
|---|---|---|
| `app_open` | each cold start, after the migration gate opens | — |
| `app_updated` | first launch after the versionCode changed | `from_code: VersionCode` |
| `migration_done` | once, when `native.migration.v1` is set or the runner gives up | `outcome` (Done / ReentryNeeded / Failed), `failed_step` (None / S0 / M1…M6), `secrets_reentry: CountBucket`, `duration: DurationBucket` |
| `backend_connected` | first successful connect per backend kind per UTC day | `kind` (OpenCode2 / Paseo / Codex), `where` (ThisPhone / Tailnet / LocalNetwork / Internet) — an `AddressClass`, never an address |
| `update_result` | after an install attempt ends without the app being replaced | `result` (Cancelled / SignerMismatch / ChecksumMismatch / NoSpace / Failed) |

DETAILED (opt-in), at the switch: the media set of 10 §19.5; `screen_open` (`route: RouteId`, never arguments — 07 N8);
`failure_seen` (`category: FailureCategory`); `cold_start` (`duration`); `crash_seen` (`category`: `CrashCategory`, and
only while local crash capture is on — B-SEC-042 stays the gate); `setting_changed` (`setting: SettingId`; `value`:
only for enumerated settings, as that setting's own `WireEnum`). No event carries a class name: R8 renames classes in
release, and a `String` property would break K-SEC-11. A feature that wants a new event adds a class to
`AnalyticsSchema.kt` (owned by the diagnostics builder; reviewed by the coordinator) and a row to PRIVACY.md's table in
the same PR.

### 5.4 Never sent (enforced by types, K-SEC-11)

Prompt or reply text, file names or paths, server addresses or names, profile ids, session ids of conversations, model
or provider names, error or exception text, credentials, keys and tokens, device model or manufacturer, screen size,
time zone, advertising id, Android id, contact or account data. (The rotating random install id of 18 §3 **is** sent:
owner decision 2026-11-01. It is unrelated to any device or account id and is replaced every 30 days and by Delete
everything.) The app adds no country; the endpoint may derive one from the IP
(disclosed).

### 5.5 Senders and their wire formats

| Sender | Endpoint | Auth | Body | Notes |
|---|---|---|---|---|
| `None` | — | — | — | the default build (`sender=none`); `track` runs the gate and the **on-disk** queue (§5.6 rule 7) so tests are real and the switch's one-time events survive until a sender exists; nothing uploads |
| `Aptabase` | `https://<region>.aptabase.com/api/v0/event` (or self-hosted base URL) | header `App-Key: A-EU-…` / `A-SH-…` | `{timestamp, sessionId, eventName, systemProps{isDebug, osName:"Android", osVersion, locale, appVersion, appBuildNumber, sdkVersion:"oc-native"}, props}` per the Kotlin SDK source [V `aptabase-kotlin` `Aptabase.kt`] | one POST per event; a batch endpoint `/api/v0/events` [U] used if it exists |
| `PostHog` | `https://eu.i.posthog.com/batch/` (or self-hosted) | `api_key` in the body (project token, public by design) | `{api_key, batch:[{event, distinct_id:<session>, timestamp, properties:{…, "$process_person_profile": false}}]}` [V posthog.com/docs/api/capture] | configure the project to discard client IPs |
| `Plausible` | `https://plausible.io/api/event` (or self-hosted) | none; `User-Agent: OpenCodeMobile` | `{name, url:"app://opencode-mobile/<event>", domain, props}` [U exact fields, check at build] | designed for web; a fallback |
| `Custom` | a self-hosted HTTPS URL | optional bearer from build config | `{"events":[WireEvent…]}` (our own JSON) | for a future own collector |
| Firebase SDK | **not offered** | — | — | needs the Google SDK and an SDK-issued app instance id; violates S-23 and 12 X-01. A Firebase destination, if the owner picks one, is the GA4 row below (18 A18-6) |
| GA4 Measurement Protocol (web stream, no SDK) | `https://www.google-analytics.com/mp/collect?measurement_id=…&api_secret=…` [U exact shape, check if chosen] | `api_secret` in `analytics.properties` | `{client_id:<session>, events:[{name, params}]}` | possible as plain HTTPS (option (e) of D16-1); not built unless the owner picks it. Google documents it as a supplement to tag/SDK collection, so some GA4 reports stay empty [U]; it also adds a Google destination |
| `QaFixture` (`stableQa` only) | `QaHooks.analyticsEndpoint`, which may be `http://10.0.2.2:<port>/…` and nothing else | none | the `Custom` body | class lives in `src/qa/`; absent from release DEX (`verifyQaHooksAbsent`); never a `KnownHosts` entry (N16-D13) |

Build configuration (committed, so F-Droid reproducible builds produce the same APK): `native/androidApp/analytics.properties`

```properties
# D16-1 picks these. Ingestion keys are public by design (they can only write events).
sender=none
endpoint=
key=
```

`:androidApp` maps it to `AppBuildInfo.analytics` and binds the matching `AnalyticsSender` with Metro;
`verifyAnalyticsEndpoint` (§2.3) fails any non-qa variant whose `endpoint` is not `https` to a `KnownHosts` Fetch host.
In `stableQa` the Metro graph binds `QaFixtureAnalyticsSender` when `QaHooks.analyticsEndpoint` is set, and `None`
otherwise; `analytics.properties` is not overridden.

### 5.6 Rules of the pipeline

1. Nothing is tracked before the migration gate opens; `Analytics` is created lazily after the first frame (S-7 keeps
   `onCreate` ≤ 50 ms).
2. `isDebug = true` for debuggable builds and `stableQa`; `verifyQaHooksAbsent` guarantees a stable release reports
   `false`.
3. Turning DETAILED off removes queued DETAILED events at once and sends nothing more of that tier.
4. Upload only with a validated default network. In the background only over an **unmetered** network (never mobile
   data, never under Data Saver); in the foreground any validated network unless Data Saver restricts it. Never a
   scheduled wake-up (18 §4.3, 13 §12.2).
5. Every **non-qa** sender: HTTPS only, the endpoint host must be a `KnownHosts` Fetch host (checked at build by
   `verifyAnalyticsEndpoint` and again when the sender is created), no cookies, `User-Agent: OpenCodeMobile`, no
   redirects (hardened client). The `QaFixture` sender is the only exception (N16-D13).
6. Failures never surface to the person; they back off (1 min, doubling to 6 h) and are recorded as a diagnostics entry
   by category.
7. Queue file `files/native/analytics/queue.json`: app-wide (no profile data, so not in `ProfileScope`); written through
   one `WriterLane`; at most 500 events / 256 KiB; events expire after 7 days, except the one-time switch events
   (`migration_done`, and the `app_updated` whose `from_code` is below 10000), which are kept up to 90 days so a sender
   added in 3.0.x can still deliver the switch outcome. It holds `WireEvent`s **including their random session id**
   (option (a) of the review: the id is a per-process random value, not a person or install id); the id is never in
   prefs, logs, problem reports or diagnostics, and it leaves the phone only inside a sent payload. Sent events are
   removed from the file; Manage space › Delete everything deletes it. The `SentLog` is memory only.

### 5.7 When anything is sent

Only when one of these holds and the tier permits the event (BASIC always, DETAILED only with the switch on):

- a non-qa build whose `sender != none` (D16-1 answered); or
- `stableQa` with `QaHooks.analyticsEndpoint` set: only to that fixture (EG-2).

With `sender=none` the switch release sends nothing (CR16-1); its BASIC events wait in the queue (rule 7). `stableQa`
without the override sends nothing.

### 5.8 What the person sees (kit only; rows on the existing Privacy page I08, no new level)

| Row (Settings › This app › Privacy) | Kind | Words (en; ar from the l10n lane) |
|---|---|---|
| What we collect | row → page generated from `WhatWeCollectSource` (18 §6): every BASIC and DETAILED event with every value it can take, the never-collected list, the ids and their rotation, the sending state |
| App health reports | row → kit sheet with the BASIC table in plain words | "Always on. Sends the app version, Android version and whether updates and connections worked. Never your chats, files, server addresses or passwords." |
| Share detailed usage | switch, **off** by default | "Also send which screens and features you use, and how fast the app starts. Helps decide what to improve." |
| What was sent | row → page with the last 50 payloads in a kit code block (LTR), Copy | "Exactly what left this phone since the app started, newest first." |

When `sender=none` the first row reads "Not sent yet. Basic reports wait on this phone, at most 90 days, until a
service is chosen." and the switch is hidden (nothing to opt into).
First-run disclosure: the 3.0 "What's new" line in the status-line slot (the update notice's slot, used once) says "This
version can send basic app health reports. See Privacy." — shown only once a sender exists (D16-2, recorded).

### 5.9 F-Droid caveat

An always-on BASIC tier earns F-Droid's **Tracking** anti-feature. With reproducible builds the F-Droid APK is byte-
identical to ours, so a "BASIC off on F-Droid" build cannot exist without giving up the shared signature. The owner's
view is "basics we need regardless" (D16-2), but **the decision waits until the F-Droid submission** after the switch
(18 §7, D18-2): keep BASIC on and declare the label, or an F-Droid build with `sender=none`.

### 5.10 Tests

`AnalyticsSchemaTest` (names unique, snake_case, ≤ 40 chars, every event has a PRIVACY.md row, every `WireEnum` wire
value unique within its enum and snake_case-safe), `RouteIdCompletenessTest` (`:quality`: one `RouteId` per `AppRoute`
in the routes `SerializersModule`), K-SEC-11, `AnalyticsPrivacyTest` (B-SEC-191: feed every emitter canary-bearing
inputs — paths, titles, error text, provider keys — and assert no queued `WireEvent` contains any canary; DETAILED never
queued while off; switching off drops queued DETAILED; the session id is found only in `queue.json` and the in-memory
sent log, never in prefs, logs, the problem report or diagnostics, and is gone after the queue is sent or deleted;
with `sender=none` BASIC events persist across a process restart and expire on schedule),
`AnalyticsSenderContractTest` per sender with `ktor-client-mock` (exact body shape, headers, no cookies, HTTPS only),
`AnalyticsUploadPolicyTest` (backoff, Data Saver, batch size), a release-DEX assertion in `verifyQaHooksAbsent` that
`QaFixtureAnalyticsSender` is absent, and EG-1/EG-2 in the egress harness.

---

## 6. Signing

### 6.1 The three lineages (verified, do not trust docs over `apksigner`)

| Lineage | Certificate SHA-256 | Signs | Key lives | Used by |
|---|---|---|---|---|
| **Public** | `842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C` (also a `CN=Android Debug` certificate: the freeze-tip `scripts/release.sh:257-260` calls it "the legacy Android debug certificate" and refuses it for store releases [R; map packages-build §0.3]) | every GitHub release (v2.0.0+56, v2.1.0+57, and the native switch release) [V `android-release.yml:42`, `scripts/release.sh:11`, release notes] | GitHub secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; owner's offline backups. **Never on the PC or the VPS**: native drops the `sideload` mode that signed with it locally (§7.4) | the release job only, on an explicit owner request |
| **Owner / local** | `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C` (DN `CN=Android Debug`, RSA 2048; build 4511 is v2+v3, no v1 [V `apksigner` today]) | the owner's phone, emulator builds, the `.native` preview flavour, hosted test APKs | **the PC's default AGP debug keystore** `~/.android/debug.keystore` (alias `androiddebugkey`, the well-known default password): the freeze-tip `android/key.properties` points at it and `keytool` shows this SHA-256 [V today]. §6.3 moves it to a dedicated path | the PC re-sign step (§6.2) |
| **CI test** | `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC` | quality-gate APK artifacts, device smoke | GitHub secrets `CI_KEYSTORE_BASE64`, `CI_KEYSTORE_PASSWORD` | CI only; never delivered to a person |

ADR D2: both installed populations stay on their lineage; never rotate, substitute or add a signer. No APK Signature
Scheme v3 rotation lineage and no v3.2 hybrid PQC signing (research android-surfaces §14).

**Both shipping lineages are `CN=Android Debug` certificates.** Nobody has yet checked that developer verification
accepts two debug-named certificates for one package (§6.5), and F-Droid has an open proposal to refuse APKs signed with
`CN=Android Debug` keys (fdroidserver issue #1171 [V page; status not shown]); its MR !1478 ("lint: blocklist known
AOSP debug keys in AASK", 2024) blocks the **published AOSP** debug keys by fingerprint, which ours are not [S search
result; U merged state]. D2 forbids rotation, and rotation is the only way out if either route refuses. So: proof P16-7
registers both packages now (the console has been open to all developers since March 2026 [R android-surfaces §14]);
F12 and D16-5 stay [U] until fdroidserver's debug-key policy is known; and D16-11 holds the fallback (an APK Signature
Scheme v3 rotation to a non-debug key, which keeps in-place updates but needs D2 amended), decided only if P16-7 or
F-Droid refuses.

### 6.2 How native builds are signed

`oc.android.application` convention plugin (build-logic):

```kotlin
android {
    signingConfigs {
        create("release") {
            val props = rootProject.file("androidApp/key.properties").takeIf { it.isFile }?.let { loadProperties(it) }
                ?: providers.environmentVariable("OC_SIGNING_STORE_FILE").orNull?.let { envSigningProps() }
            if (props != null) {
                storeFile = file(props.storeFile); storePassword = props.storePassword
                keyAlias = props.keyAlias; keyPassword = props.keyPassword
                enableV1Signing = false; enableV2Signing = true; enableV3Signing = true; enableV4Signing = false
            }
        }
    }
    buildTypes.getByName("release") {
        signingConfig = if (providers.gradleProperty("oc.unsigned").isPresent) null else signingConfigs.getByName("release")
    }
}
// Fails the build when -Poc.expectedSigner=<64 hex> is given and any produced APK's signer differs.
tasks.register<VerifyApkSignerTask>("verifyStableReleaseSigner")   // uses com.android.tools.build:apksig [U version = AGP's]
```

| Build | Where | Signed by | Command |
|---|---|---|---|
| Public release | GitHub Actions `native-release.yml` (§7.3) | public | `-Poc.expectedSigner=842284B2…` |
| Owner / emulator APK | VPS builds **unsigned** (`-Poc.unsigned=true`) in its own folder; the PC signs | local | `native/tool/release/sign_local.sh <unsigned.apk> <out.apk>`: `zipalign -P 16 -f 4`, `apksigner sign --ks <path> --ks-pass env:OC_KS_PASS --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true`, then `apksigner verify --print-certs` must print exactly one signer equal to `1DE5BF08…`, else the output is deleted (port of `tool/qa/vps_build_apk.sh`) |
| Preview `.native` | same as owner | local | same |
| Benchmark `.bench` | VPS/PC | debug key | never delivered |
| `debug` (any flavour) | PC/VPS | debug key (on the PC that is the owner key, §6.1) | package suffix `.debug` (CR16-15), so it can never update the stable or `.native` app; never delivered |
| `stableQa` | PC emulator | local (it must install over Flutter for the upgrade journey) | never delivered: versionName `-qa`, refused by A16-14 and by `deliver.sh` |

Before any APK reaches the owner's phone: ask for (or read with read-only adb `dumpsys package` signatures) the
installed signer and compare with `apksigner verify --print-certs` (AGENTS.md workflow rule, kept). `deliver.sh`, the
auto-install rule (memory `auto-install-latest-build`) and A16-14 also refuse any APK whose `aapt2 dump badging` shows
`application-debuggable` or whose versionName ends `-qa` or `-debug` (N16-D15).

### 6.3 Key custody (owner actions; D16-9)

1. Keep **two offline copies** of each keystore (public and local) and their passwords in the owner's password manager.
   Losing the public key means users can never update in place again, and developer verification needs it [V
   android-surfaces §14: "If you lose your signing key you won't be able to register"].
2. **Move the local key out of `~/.android`.** Android tooling silently creates a new `~/.android/debug.keystore` when
   the file is missing (for example after an `~/.android` clean-up), and every APK signed with that new key would be
   refused by the owner's installed app for good. Copy the current file (byte-identical, checked by its SHA-256
   certificate fingerprint) to a dedicated path outside `~/.android` and outside the repo, e.g. `~/Storage/keys/oc-local.keystore`
   (mode 0600), point `androidApp/key.properties` and `sign_local.sh` at that copy, and keep the two offline backups of
   that copy. The release signing config never reads `~/.android`.
3. The public keystore lives only in GitHub secrets and the offline backups (§6.1); no `key.public.properties` exists
   anywhere, because native has no local sideload mode (§7.4).
4. The release job uses a GitHub **environment** `release` with the owner as required reviewer, so a leaked workflow
   change cannot sign without approval.
5. Never print a keystore path, password or alias in logs (scripts pass `env:` references; CI masks secrets).
6. `RepositoryHygieneTest` fails on any `*.jks`, `*.keystore`, `*.p12`, `key.properties` in git (B-SEC-150).

### 6.4 versionCode and names (ADR D3)

Base **10000** for the switch (`3.0.0+10000`), never lower than any installed build (owner's local builds 4300–4511).
ABI splits (`arm64-v8a`, `x86_64`) and the universal APK share one versionCode. Native dev builds use numbers ≥ 10000 in
ranges reserved per agent brief (memory `apk-build-numbers`).

### 6.5 Developer verification (ADR D18)

Register in the Android Developer Console before the global enforcement ("2027 and beyond" [V research]):

| Package | Signers |
|---|---|
| `io.github.eslamasabry.opencode_mobile` | `842284B2…1C` (public), `1DE5BF08…D60C` (owner) |
| `io.github.eslamasabry.opencode_mobile.native` | `1DE5BF08…D60C` |
| (later) F-Droid | none needed when F-Droid ships our signature (§9) |

Account type: full distribution ($25, ID verification) — the limited account caps at 20 devices (D16-7). Do it
**before slice 4** as proof P16-7, not just before enforcement: both certificates are `CN=Android Debug` (§6.1), and if
the console refuses them the fallback (D16-11) needs time.

---

## 7. Release pipeline without Shorebird

### 7.1 What changes and what stays

| Stays (packages-build map §0 item 10) | Changes |
|---|---|
| tag `v<x.y.z>+<N>`; notes `docs/releases/v<version>.md` starting `# OpenCode Mobile <version>`; `CHANGELOG.md` top entry; fastlane changelog `<versionCode>.txt`; assets `opencode-mobile-<version>.apk` (universal) and `opencode-mobile-<version>-arm64-v8a.apk`; `SHA256SUMS`; draft → verify → publish; tag commit == `origin/master` HEAD; owner's explicit request for tag, CI run, signing and publish | Shorebird setup/release steps → Gradle `assembleStableRelease`; `--flutter-version` checks → JDK/AGP/Kotlin provenance; the `-armeabi-v7a.apk` asset is gone (ADR §9.9 ABI set) and `-x86_64.apk` is added; size gate numbers (S-14); output path `native/androidApp/build/outputs/apk/stable/release/`; "patch" and "patch-plan" modes are removed from the native release script |

### 7.2 Hotfixes

There is no code push for Kotlin (02 NG17). A fix is a full release: bump `+N` (and the patch version), same pipeline.
Target turnaround after the owner's yes: tag → draft with verified assets in ≤ 30 min of CI, publish on the owner's
second yes, every installed copy sees "Version x is ready" the next time the app is opened (start or resume), at most
one check every 6 hours; the app never checks while hidden (§8.2), so a phone that stays closed sees it when opened.
The Flutter bridge release keeps Shorebird for **security-only** patches with the owner's OK until its users have moved
(02 §5.7).

### 7.3 `native-release.yml` (GitHub Actions; runs only on an explicit owner request)

Triggers: `workflow_dispatch` with inputs `tag` and `dry_run` (boolean, default `false`) (a tag push on a `[skip ci]`
commit starts nothing, memory `release-2.1`). `concurrency: native-release-${{ inputs.tag }}`, no cancel;
`permissions: contents: write`; `environment: release`.

`dry_run=true` exists so the pipeline can be proven before native code is on `master` (WP-16.12, A16-6): step 5 records
the commit instead of requiring `origin/master` HEAD, step 19 (the draft release) is skipped, and the run builds,
signs, verifies and writes `SHA256SUMS` only; its APKs stay workflow artifacts (90 days, the public-repo limit).

Step names (the verifier reads them; rename both together):

1. **Require release secrets** — the four `RELEASE_*` secrets exist (fail closed).
2. **Checkout tag** — `fetch-depth: 0`.
3. **Resolve version** — from `native/version.properties`; `versionName` matches `^\d+\.\d+\.\d+$`, `versionCode` ≥ 10000.
4. **Verify tag and notes** — tag == `v<name>+<code>`; notes file exists with the header; `CHANGELOG.md` agrees
   (port of B-BUILD-028).
5. **Require tag commit equals master** — and any existing GitHub release for the tag is a draft. With `dry_run=true`:
   record the commit, skip the master check.
6. **Set up Java 17** — Temurin, exact version from `native/.java-version`.
7. **Validate Gradle wrapper** — `gradle/actions/wrapper-validation@<sha>`; `distributionSha256Sum` present.
8. **Fetch vendored artifacts** — `native/tools/vendor/fetch.sh` (checks `third_party/SHA256SUMS`).
9. **Install release signing identity** — decode the keystore to `$RUNNER_TEMP`; compute its certificate SHA-256;
   **refuse unless it equals `842284B2…`**.
10. **Build release APKs** — `./gradlew --no-daemon --no-configuration-cache -Poc.expectedSigner=842284B2…
    :androidApp:assembleStableRelease :androidApp:verifyStableReleaseSigner :androidApp:analyzeStableReleaseR8Config`.
11. **Remove signing identity** — `if: always()`.
12. **Verify APK signer and version** — for universal and each split: `apksigner verify --print-certs` (exactly one
    signer, the public lineage, v2+v3, no v1), `aapt2 dump badging` (package, versionCode, versionName, `native-code`
    per split).
13. **Verify release rules in DEX** — `verify_scheduled_prompt_apk.py`, `verify_widget_release_rules.py` (R8 keep
    checks ported, PACKAGES-BUILD-06), `verifyQaHooksAbsent` script form (no `src/qa/` class, no `qa-hooks.json`, no
    `10.0.2.2`, not debuggable).
14. **Check 16 KB alignment** — `zipalign -c -P 16 -v 4` on every APK and an ELF `p_align` check on every `.so`.
15. **Check APK size** — `check_apk_size.py` against the previous published release and S-14 caps.
16. **Check component freeze and exported surface** — the `:quality` freeze list against the built APK's manifest.
17. **Write SHA256SUMS** — `dist/opencode-mobile-<version>.apk`, `-arm64-v8a.apk`, `-x86_64.apk`.
18. **Upload mapping** — R8 `mapping.txt`, `build/outputs/native-debug-symbols/` and their SHA-256 as a workflow
    artifact (`retention-days: 90`: `opencode-mobile-next` is a **public** repository [V `gh repo view` today], and
    GitHub caps public-repo artifacts at 90 days; 400 is the private-repo limit [V GitHub docs], so the Flutter
    workflow's `retention-days: 400` was already silently capped). Never a release asset. The permanent copy is made
    by `scripts/release.sh github` (§7.4) before publishing.
19. **Create draft GitHub release** — notes + a "Verify this build" block: commit, "Gradle build: AGP 9.3.3, Kotlin
    2.4.21, JDK 17 (Temurin <exact>)", package id, signer `842284B2…`, `SHA256SUMS`, and "Rebuild it yourself:
    `native/tool/release/repro_check.sh v<version>`".

Then, locally (`scripts/release.sh github`, ported): download the CI artifacts and the draft assets, re-verify identity
and checksums, and on `--publish` (owner's explicit yes) make the release non-draft, non-prerelease and `--latest`, and
read back `releases/latest`. The verifier requires the draft to carry **exactly** the three APKs plus `SHA256SUMS`,
nothing else (memory `stable-release-1.0.44-2026-09-21`, draft-asset trap).

**Mapping archive** (memory `phone-logcat-silenced`: crashes on installed versions are decoded with R8 retrace and the
exact build's mapping): before `--publish`, `release.sh github` copies the mapping artifact to
`$OC_SYMBOLS_DIR/<tag>/` on the PC (default on the Storage2 disk), mirrors it to the build VPS, and reminds the owner to
add it to the offline backup; publishing is refused unless the archived `mapping.txt` hashes to the value the
workflow recorded. A release check (`release.sh symbols-check <tag>`) proves the mapping for any published version is
retrievable. Alternative if the owner prefers it in git: a private `opencode-mobile-symbols` repository pushed by the
release job with a deploy key (one more secret).

### 7.4 `scripts/release.sh` (native mode)

Keeps: fail-closed checks, `master` with a same-named upstream, clean tree, synced, nothing uploaded without
`--publish`, publish from a throwaway clone (memory `release-1.4.0`). Replaces: the Flutter version assertion with
`native/.java-version` + `libs.versions.toml` AGP/Kotlin pins. **Removes: `patch`, `patch-plan`, `sideload`.** The
Flutter `sideload` mode signed on the PC with the public key from `android/key.properties`; in native the public key
lives only in GitHub secrets and the offline backups (§6.1, §6.3), so every public APK comes from `native-release.yml`.
Runs before tagging: the release-notes check, T2 on the VPS (record the run), the reproducibility check in report mode
(§9.3), the leak gate on the tag commit (§2.3: noreply author and committer, no owner email, private addresses or
device names; result recorded in the release verification record). Adds `symbols-check` (§7.3).

**Flutter workflows at the switch** (WP-16.12): `android-release.yml` (Shorebird), `desktop-linux.yml` and
`byo-host-bundle.yml` start on `v*` tags [V freeze tip]. They would attach extra assets to the native draft (which the
verifier refuses) or start a Shorebird release for a v3 tag. Their tag filters become `v2.*` (the bridge line) in the
switch commit, and the native verifier requires exactly the three APKs plus `SHA256SUMS`.

### 7.5 The bridge release (ADR D4; the last Flutter release)

Security items that must be in it (02 §5.7 owns the rest):

1. The freeze-tip updater (`AppUpdateChannel`, merged `6b686bb7c`) already checks package, versionCode and signer.
2. **Add an ABI pre-check**: the native app ships only `arm64-v8a` and `x86_64`; on a phone whose `Build.SUPPORTED_ABIS`
   contains neither, the bridge updater says "Version 3 needs a 64-bit phone. This version keeps working." and offers
   nothing (else Android fails with `INSTALL_FAILED_NO_MATCHING_ABIS` after a 30 MB download). Test in the Flutter
   suite (allowed under the freeze as part of the bridge release).
3. Release notes of 3.0.0 say the same for 32-bit phones; the site's download section drops the armv7 link.

---

## 8. The in-app updater (`AppUpdater`)

Design and UI come from the owner's two-layer design (memory `app-updates-two-layers`; `docs/design/app-updates-2026-10-31.md`
on the freeze tip [R]): status-line notice → gear badge → "Update now / Open Settings" sheet → Settings › This app ›
Updates. This section specifies the security half.

### 8.1 Where the code lives

| Piece | Module | Notes |
|---|---|---|
| `UpdateNotice` (state), `ReleaseSource`, `UpdateVerifier`, `UpdatePolicy`, `UpdateFeed`, `UpdatePrefs` | `:engine:diagnostics` commonMain | 03 §11 already lists `UpdateNotice` and `UpdateInstaller` here; the `stableQa` feed binding lives in `androidApp/src/qa/` |
| `GitHubReleaseSource` | `:engine:diagnostics` commonMain over `:core:net` | `GET <UpdateFeed.feedUrl>` (release: `https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest`, §8.2), `Accept: application/vnd.github+json`, 10 s timeout, no auth |
| `ResumableDownloader` | `:core:net` | with the redirect loop of CR16-5 |
| `PackageInstallerUpdater` (implements `UpdateInstaller`), `AndroidApkInspector`, `AndroidInstallSource` | `:android:platform` | port of the freeze-tip `AppUpdateChannel.kt` minus the channel |
| `.AppUpdateStatusReceiver` | `:androidApp` (frozen FQCN, not exported) | 11 §3 |

### 8.2 When the updater may check (N16-D8)

```kotlin
public enum class UpdateFeedState { Active, OffBySwitch, DeveloperBuild, InstalledByStore, NotThisPackage }

/** The feed the updater trusts. Release builds always use [UpdateFeed.Public]; only stableQa may build another one,
 *  from QaHooks.updateFeed (14 §4.4, CR16-19), in code that lives in src/qa/. */
public data class UpdateFeed(
    val feedUrl: String,                 // releases/latest JSON
    val assetPrefix: String,             // every asset URL must start with this (§8.3 step 3)
    val expectedPublicSigner: String,    // the signer this feed's releases carry
    val allowCleartextFixture: Boolean,  // true only for a stableQa feed on http://10.0.2.2:<port>
) {
    public companion object {
        public val Public: UpdateFeed = UpdateFeed(
            feedUrl = "https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest",
            assetPrefix = "https://github.com/Eslamasabry/opencode-mobile-next/releases/download/",
            expectedPublicSigner = "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C",
            allowCleartextFixture = false,
        )
    }
}

public object UpdatePolicy {
    public val STORE_INSTALLERS: Set<String> = setOf(
        "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid",
    )
    public fun feedState(
        feed: UpdateFeed, packageName: String, ownSigner: String, firstInstaller: String?, checkSwitchOn: Boolean,
    ): UpdateFeedState = when {
        packageName != "io.github.eslamasabry.opencode_mobile" -> UpdateFeedState.NotThisPackage   // .native, .bench, .debug
        ownSigner != feed.expectedPublicSigner -> UpdateFeedState.DeveloperBuild                     // owner phone, emulator
        firstInstaller in STORE_INSTALLERS -> UpdateFeedState.InstalledByStore
        !checkSwitchOn -> UpdateFeedState.OffBySwitch
        else -> UpdateFeedState.Active
    }
}
```

- **The qa feed (N16-D13).** `AppBuildInfo.updateFeed` is `UpdateFeed.Public` in every build except `stableQa` with
  `QaHooks.updateFeed` set, where the `src/qa/` binding builds the feed from it: `expectedPublicSigner` = the local-key
  fingerprint (so a local-key `stableQa` APK is `Active`, not `DeveloperBuild`); `feedUrl` and `assetPrefix` default to
  the public ones and may be replaced by a fixture origin. `http://` is accepted only when the host is exactly
  `10.0.2.2` (`allowCleartextFixture = true`); any other `http` override is refused at load. The fixture origin is
  never a `KnownHosts` entry. `verifyQaHooksAbsent` (§2.3) shows the release DEX has neither the binding nor the
  `10.0.2.2` string.
- `firstInstaller` is read once (API 30+ `getInstallSourceInfo(pkg).initiatingPackageName`; API 26–29
  `getInstallerPackageName`) at the first native launch and stored app-wide as `oc.appUpdate.firstInstaller` (after a
  self-update the installer becomes our own package, so it must be the *first* value). Obtainium
  (`dev.imranr.obtainium`) is not a store installer: its users keep the in-app check.
- **Stored values** (app-wide, `UpdatePrefs` in `:engine:diagnostics`): `oc.appUpdate.enabled` — the person's "Check
  GitHub for new versions" switch, **the same key the freeze-tip updater writes** [V `lib/update/app_update_controller.dart:71`],
  default true when absent, carried unchanged by 05 M3 (CR16-13), so a person who turned the check off in 2.x has it off
  after the update (B-SEC-200); `oc.appUpdate.firstInstaller`; `oc.appUpdate.lastCheckMs` (native's own). The Flutter
  values `oc.appUpdate.lastCheckMs` and `oc.appUpdate.release` are dropped at migration (a fresh check follows the first
  start).
- Words on the Updates page: DeveloperBuild "This copy was built by its developer. Updates come from them, not from
  GitHub."; InstalledByStore "Updates come from F-Droid."; both keep "Check now" hidden (B-SEC-190).
- Check frequency: app start and resume, at most every **6 hours**, never while hidden (owner design).
- **Never claims an update when the service is unavailable** (B-MIGR-012): a failed or unreadable check leaves the
  state "Couldn't check · checked <time>" with Check now; it never shows "is ready".

### 8.3 The verification pipeline (all must pass; any failure → "This download isn't signed like your app, so it wasn't installed." or the matching plain sentence, details under Details)

The UI source is the owner's freeze design (`docs/design/app-updates-2026-10-31.md` [R]): the Updates page row shows
the state and the size before anything is downloaded ("3.0.1 is ready · 40 MB" with **Update**, which reads "Download
40 MB" on metered networks), a progress bar with **Cancel** during the download (Cancel keeps the `.part` file for
Range resume), "Installing…", or a failure with a way forward.

| Step | Check | Failure (`UpdateProblem`) |
|---|---|---|
| 1 | release JSON from the feed (`UpdateFeed.feedUrl`, §8.2): tag parses `^v(\d+)\.(\d+)\.(\d+)\+([1-9]\d*)$`, not draft, not prerelease; build number > installed versionCode (B-BUILD-010/011) | `NotNewer` (silent) |
| 2 | pick asset: walk `Build.SUPPORTED_ABIS` **in the OS's order** and take the first ABI that has a `-<abi>.apk` split in the release (so an x86_64 device that also lists `arm64-v8a` through binary translation, such as ChromeOS or some emulators, gets the x86_64 split and runs natively); if none matches but the phone has a 64-bit ABI we ship, the universal `opencode-mobile-<v>.apk` (owner design; the freeze-tip `app_release.dart` `apkFor` does the same); if the phone has neither `arm64-v8a` nor `x86_64`, nothing (`NeedsSupportedPhone`, "This version needs a 64-bit phone."). The `SHA256SUMS` asset must exist | `NoAsset`, `NeedsSupportedPhone` |
| 3 | every asset URL starts with `UpdateFeed.assetPrefix` (release: `https://github.com/Eslamasabry/opencode-mobile-next/releases/download/`); redirects only to the `AppUpdate` set `*.githubusercontent.com` (HTTPS only, ≤ 5 hops, no credentials; CR16-5). GitHub has changed this host before, and native has no code push, so the set is a suffix, not one host; the checksum (step 5) and signer (step 7) checks are the real gate. `http://` only for a `stableQa` fixture feed on `10.0.2.2` (§8.2) | `UnexpectedSource` |
| 4 | free space ≥ 2 × asset size in `cacheDir`; download to `cacheDir/updates/<tag>/<file>.part` with Range resume; rename on completion | `NoSpace`, `DownloadFailed` |
| 5 | SHA-256 of the file equals its `SHA256SUMS` line (exact file name, 64 lowercase hex) | `ChecksumMismatch` |
| 6 | `PackageManager.getPackageArchiveInfo(file, flags)`: package == ours; versionCode > ours through `PackageInfoCompat.getLongVersionCode` (never `longVersionCode` directly: API 28+); `minSdkVersion` ≤ device SDK. **Flags by API (Astra M3):** API 28+ `GET_SIGNING_CERTIFICATES`; **API 26–27** `GET_SIGNATURES` (the only signing flag there) | `WrongApp`, `NotNewer`, `NeedsNewerAndroid` |
| 7 | the archive's current signer set equals our installed signer set: **API 28+** `signingInfo.signingCertificateHistory.last()`, or `apkContentsSigners` if several; **API 26–27** `PackageInfo.signatures` of the archive against `PackageInfo.signatures` of the installed app (`GET_SIGNATURES`; no rotation history exists there, and both lineages are single-signer, D2). If the archive returns no signatures on 26–27 [U whether the platform fills them for archives; settled by P16-4b], the step fails closed with `SignerMismatch` and the row offers the release page; an in-app v2 signature check (`apksig` `ApkVerifier`, version pinned at that slice [U], 12 registry row first) is the fallback. In all cases, compared as sorted upper-case SHA-256 strings (the freeze-tip `AppUpdatePolicy.signerString` format) | `SignerMismatch` |
| 7b | `PackageManager.canRequestPackageInstalls()`: if false, explain in one line ("Android needs your OK to install updates from this app."), open `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for our package, and continue **by itself** when the person returns (a lifecycle observer on `ON_RESUME` re-checks and goes on to step 8; if still not allowed, the row says so with the same button) | `InstallNotAllowed` (a state with a way forward, not a failure) |
| 8 | `PackageInstaller` session: `MODE_FULL_INSTALL`, `setAppPackageName(ours)`, API 31+ `setRequireUserAction(USER_ACTION_REQUIRED)`, API 33+ `setPackageSource(PACKAGE_SOURCE_DOWNLOADED_FILE)`; stream **the same verified file** into the session; commit with the immutable-except-allowlisted status PendingIntent | `InstallFailed` (Android's text under Details) |
| 9 | `STATUS_PENDING_USER_ACTION`: start the confirm intent from the visible `MainActivity` (BAL rules) | — |
| 10 | on `aborted`/`failure`: return to "ready", keep the verified file for 24 h, then delete; emit BASIC `update_result` | — |

On `UnexpectedSource` or `NoAsset` the row also offers **Open the release page**, which goes through SI-1 (origin
`AppAuthored`, the release's `html_url` only if it starts with `https://github.com/Eslamasabry/opencode-mobile-next/`),
so a changed CDN host never leaves people without a way to update.

`UpdateVerifierTest` (commonTest with fakes for the archive inspector and the clock) covers every row, plus: a
redirect to a new `*.githubusercontent.com` host passes and one to `example.org` fails; ABI order (`[x86_64,
arm64-v8a]` picks the x86_64 split; `[arm64-v8a]` with only a universal asset picks the universal); a failed or
malformed feed never yields "ready" (B-MIGR-012); `InstallNotAllowed` resumes after the permission page;
`UpdatePolicyTest` covers `feedState` for the public feed and a qa feed. P16-4 proves it on the emulator with
`stableQa` and the `updateFeed` override (§13). **P16-4b** runs the real verifier and `PackageInstaller` (no fake
archive inspector) on an **API 26** AVD and on API 37, both through `emulator-5584`'s booking: a correct update installs
in place; a wrong-signer APK, a wrong-package APK and a lower-versionCode APK are each refused before the install
session opens (Astra M3).

### 8.4 The downloader's redirect loop (CR16-5; shared with voice packs and the rootfs)

```kotlin
// :core:net commonMain
public class ResumableDownloader(private val http: HttpClientFactory, private val clock: Clock) {
    /** HTTPS only. Follows at most 5 redirects, each to an https URL whose host is in [allowedRedirectHosts]
     *  (exact or "*.suffix"). Sends no credentials, cookies or Referer. Resume always re-requests [url].
     *  [cleartextFixtureHost] is null in every release path; only a stableQa UpdateFeed or QaHooks.downloadBaseUrl
     *  may pass "10.0.2.2", which then allows http:// to exactly that host and no redirects. */
    public suspend fun download(
        url: String, expectedSha256: String, target: kotlinx.io.files.Path,
        allowedRedirectHosts: Set<String>, progress: (Long, Long?) -> Unit,
        cleartextFixtureHost: String? = null,
    ): Outcome<Unit>
}
```

---

## 9. F-Droid readiness and reproducible builds

### 9.1 Status (freeze decision 1: Google-free QR now, F-Droid submission after the rewrite)

| # | F-Droid blocker (packages-build PACKAGES-BUILD-34) | Native state at the switch | Work to clear (after the switch) | Owner |
|---|---|---|---|---|
| F1 | ML Kit (proprietary) | **cleared**: zxing-cpp (12 R-19, X-02) | — | — |
| F2 | Shorebird | **cleared** (X-03) | — | — |
| F3 | Google Play services / Firebase | **cleared** (X-01, `BannedDependenciesTest`) | — | — |
| F4 | Prebuilt `jniLibs` in the source tree: proot, loader, talloc, shmem | still prebuilt from Termux `.deb`s | an fdroiddata recipe that builds them from source with the pinned NDK (proot 5.1.107.94, talloc 2.4.3, shmem 0.7, Termux patches) and `--build-id=none`, `-ffile-prefix-map` | runtime owner |
| F5 | sherpa-onnx AAR (vendored binary, includes ONNX Runtime) | vendored, hash-pinned (12 §9.3) | build sherpa-onnx + ONNX Runtime from source in the recipe, or a voice-less F-Droid variant [U: whether F-Droid accepts the Maven/GitHub AAR] | media owner |
| F6 | zxing-cpp AAR (Maven Central, native) | from Maven Central | [U] F-Droid scanner verdict on Maven-Central native AARs; else build from source (Apache-2.0, CMake) | media owner |
| F7 | Termux `terminal-emulator`/`terminal-view` from JitPack | JitPack + our fork (12 K-04) | vendor the source (Apache-2.0) into `native/third_party/termux/` and build it as a module | kit-content owner |
| F8 | Rust `libaiteam_*` | not shipped at the switch (ADR D6) | Rust build in the recipe when AI Team returns | runtime owner |
| F9 | Runtime downloads of executables (Ubuntu rootfs, Node, npm packages, agent binaries) | only after the person starts phone setup | explicit disclosure in the setup screen and F-Droid metadata; anti-features likely `NonFreeAdd` (Claude Code is proprietary) and `NonFreeNet` (model providers) [U exact labels] | owner (D16-5) |
| F10 | Always-on BASIC analytics | §5 | anti-feature `Tracking` (D16-2, recorded) | owner |
| F11 | In-app updater | §8 | install-source rule turns it off for F-Droid installs; [U] F-Droid inclusion policy on self-updaters, check at submission | release lane |
| F12 | Reproducible build with our signature | §9.2 | `Binaries:` + `AllowedAPKSigningKeys: 842284b27aa297fb74cf831779fd16498517e1bc2104451459fec2ea7ac11d1c` in the fdroiddata metadata [V f-droid.org Reproducible_Builds]. **[U]** our public key is a `CN=Android Debug` certificate (§6.1); fdroidserver issue #1171 proposes refusing such APKs. Re-check fdroidserver's debug-key policy before submission; if it refuses, D16-11 | release lane |
| F13 | IzzyOnDroid (separate repo) | blocked by its AI policy [R] | none | — |
| F14 | Photo Picker backport entry (`com.google.android.gms.metadata.ModuleDependencies` service declaration in the manifest, no dependency; 10 §2.7, §15.2) | present in the one APK | [U] whether F-Droid's scanner flags a manifest-only gms entry; if it does, the entry is removed for **every** build (API < 30 without the system picker falls back to `OpenDocument`, 10 §2.7) — never a flavour | media owner |
| F15 | LiteRT-LM AAR (`com.google.ai.edge.litertlm:litertlm-android`, freeze tip 0.19.0 [V `android/app/build.gradle.kts:181`]; prebuilt native libs) for the on-device assistant | not shipped at the switch unless the assistant is ported | if ported: [U] scanner verdict on a Google-Maven native AAR (Apache-2.0); else build from source in the recipe, or keep the assistant out of the F-Droid submission by not porting it | media / assistant owner |

**One APK, no `fdroid` flavour** (10 §2.7, D10-6 default "Full"): F-Droid builds the same `stableRelease` from the tag
and, with F12, ships our signed APK. Every F-row is therefore cleared for **all** builds (source builds in the recipe or
removal from `main`), never by a flavour; that is what keeps §5.9 and F12 true. If D10-6 ever falls back to a voice-less
"Lite" build, it gives up the shared signature (F-Droid signs it) and needs its own versionCode policy and updater
behaviour (F11 already turns the in-app check off for F-Droid installs); that would be a new owner decision, not a
default.

### 9.2 Reproducibility rules (do now; N16-D12)

| # | Rule | Where |
|---|---|---|
| RB-1 | Gradle wrapper committed with `distributionSha256Sum`; JDK pinned exactly in `native/.java-version` (Temurin 17.0.x); Android build-tools pinned in `libs.versions.toml` | root |
| RB-2 | No timestamps, hostnames, user names or absolute paths in any generated file: `AppBuildInfo` has no build time; BuildConfig fields are version, channel, debuggable only | `:androidApp` |
| RB-3 | Every `AbstractArchiveTask` gets `isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true` (convention plugin) | build-logic |
| RB-4 | Baseline profile committed (`androidApp/src/main/baseline-prof.txt`, regenerated by T4); `baseline.prof`/`.profm` determinism checked by P16-5 (F-Droid lists them as a known source of drift [V]) | `:androidApp` |
| RB-5 | PNGs pre-optimised; `cruncherEnabled = false`; no `vectorDrawables.generatedDensities` | `:androidApp` |
| RB-6 | `vcsInfo` left on (AGP writes the commit into `META-INF/version-control-info.textproto`; same tag → same bytes) | default |
| RB-7 | Licensee's `artifacts.json` and CMP resource generation sorted and timestamp-free [U, P16-5] | build-logic |
| RB-8 | Any native code we compile (later: Rust, proot) uses `--remap-path-prefix` / `-ffile-prefix-map` and `--build-id=none` | runtime |
| RB-9 | Release builds run on Linux only (resource shrinker output may differ across OSes [V F-Droid]) | CI, VPS |
| RB-10 | Signing with `apksigner` (v2+v3, no v1) so `apksigcopier` can copy the signature; F-Droid's page warns that `apksigner` from build-tools ≥ 35.0.0-rc1 produced APKs `apksigcopier` could not verify and recommends 34 [V page; U whether still true — P16-5 checks with the current apksigcopier] | §6.2 |

### 9.3 The reproducibility check

`native/tool/release/repro_check.sh <tag>` (VPS; report-only until the F-Droid slice, then a release gate):

1. Two clean clones of the tag at two different paths (`/tmp/repro-a`, `/srv/build/repro-b`).
2. Build A with `--max-workers=2`, B with `--max-workers=8`, both `-Poc.unsigned=true :androidApp:assembleStableRelease`,
   `--no-build-cache --no-configuration-cache`.
3. `cmp` each unsigned APK pair; on difference, `diffoscope --html build/repro/<tag>.html` and fail with the first
   differing entry.
4. If the tag is published: `apksigcopier compare <published.apk> --unsigned <A.apk>` must pass.
5. Write `docs/qa/native/repro-<tag>/README.md` with the commands, tool versions and result.

---

## 10. Third-party notices, licences, GPL sources, About

12 §8 owns licence policy, Licensee and the generated `THIRD_PARTY_NOTICES.md`. This section adds what the release must
carry:

1. **About › Open-source licences** lists Licensee's `artifacts.json` plus `third_party/BUNDLED.toml` entries (12 §8.3),
   through kit parts only; each opens its full text (`composeResources/files/notices/<id>.txt`) with one action
   "Open <project> website" through SI-1.
2. **Runtime-downloaded components** get their own section in the notices and in the About list ("Downloaded when you
   choose"): Ubuntu Base 24.04.5 (its own licences inside the rootfs), Node.js (MIT + bundled), OpenCode 2 (MIT), Paseo
   [U licence, check at pin time], Claude Code (Anthropic's terms; the person accepts them in its sign-in), Codex (Apache-2.0),
   Whisper converted models (MIT), Kokoro (Apache-2.0) — today's `THIRD_PARTY_NOTICES.md` "Runtime and model data flow"
   section, kept current by the runtime and media owners.
3. **GPL/LGPL source offer for the proot set** (GPL-2.0 proot, LGPL-3.0 talloc): the APK distributes these binaries, so
   each release must offer the corresponding source. Default (D16-8): one immutable GitHub release
   `third-party-sources-1` (never "latest", tag not version-shaped so the updater skips it) carrying
   `proot-5.1.107.94-src.tar.gz`, `talloc-2.4.3-src.tar.gz`, `libandroid-shmem-0.7-src.tar.gz` and the Termux patch
   files; the notices name that URL; a new tag `third-party-sources-2` when a pin changes (same immutability rule as
   `aiteam-assets-1`). `LICENSES/` gains `GPL-2.0-proot.txt`, `LGPL-3.0-talloc.txt` + `GPL-3.0.txt`,
   `BSD-3-Clause-libandroid-shmem.txt` (12 §8.3).
4. **Non-affiliation statement** ("OpenCode Mobile is an independent community project. It is not built, maintained,
   endorsed by, or affiliated with the official OpenCode team.") at every public entry point: README, About, PRIVACY,
   SECURITY, release notes header, site (B-BUILD-030, `RepositoryHygieneTest`).
5. **SECURITY.md** (ported): private reporting; supported versions = the latest native release, plus security-only
   Shorebird patches for the bridge release until 3 months after the switch [default, owner may change]; Shorebird
   moves out of the out-of-scope list.

---

## 11. The targetSdk-37 security slice (after the switch; ADR §8 "targetSdk 37 in its own slice")

| Change at targetSdk 37 [V developer.android.com behavior-changes-17] | Impact | Work |
|---|---|---|
| Certificate Transparency on by default | already on if N16-D4 shipped; else surfaces any CT gap | rerun P16-2 on API 37 |
| `ACCESS_LOCAL_NETWORK` runtime permission (group `NEARBY_DEVICES`); TCP to LAN times out without it; inbound LAN needs it; local = RFC1918, link-local, `100.64.0.0/10`, multicast on broadcast-capable interfaces, **excluding VPN** [V local-network-definition] | LAN servers, LAN Paseo, LAN pairing, Wi-Fi share listener. Tailscale runs over a VPN interface, so expected unaffected; loopback not listed [U, prove] | ask in context the first time a Private-class origin is used or a share starts; one visible row "Let OpenCode reach computers on your Wi-Fi"; map "timeout + not granted" to that ask, never a raw error |
| ECH used when the TLS library and server support it; `<domainEncryption>` in NSC | OkHttp 5.5 supports ECH opt-in only via its DNS APIs [V CHANGELOG] | none at first |
| BAL hardening on `IntentSender` | the updater's confirm intent | already started from the visible activity (§8.3 step 9) |
| Safer native DCL: `System.load` files must be read-only | packaged libs only | `L9` already holds; verify sherpa-onnx loads from `nativeLibraryDir` |
| `static final` reflection writes fail | old libraries | dependency audit (12 owner) |

---

## 12. Work packages

Slices as in ADR §5.2 (0 skeleton … 6 switch). Models per memory `subagent-model-choice` and `codex-backend-claude-frontend`.

| WP | What | Modules / files (write set) | Slice | Builder | Depends on | Acceptance |
|---|---|---|---|---|---|---|
| WP-16.1 | `Secret`, the merged `CanarySecrets` + generated `canaries.txt` (CR16-12, coordinated with W14-6), `KnownHosts`, `ExternalLinkPolicy` vectors port | `:core:common`, `:core:testing`, `:core:security` | 0–1 | Codex (core owner) | 03 §10 freeze | `ExternalLinkPolicyTest`, `SecretTest`, K-SEC-4 green |
| WP-16.2 | K-SEC-1…12, lint table; `:quality` tests `NetworkSecurityConfigTest`, `FileProviderPathsTest`, `KnownHostsDocTest`, `PrivacyCoverageTest`, `SecRowCoverageTest`, `RepositoryHygieneTest` (+ `leak_gate.sh`), `CanaryLeakTest`; build-logic tasks on `:androidApp` `verifyExportedSurface`, `verifyOkHttpFloor`, `verifyQaHooksAbsent`, `verifyAnalyticsEndpoint`, wired into T2 | `:quality`, build-logic, `androidApp/lint.xml`, `native/config/` | 0–1 | coordinator (Sonnet) | ADR P9 | each rule fails on a planted violation (P9 style), including the K-SEC-4 set of §2.1 and a `stableRelease` with `src/qa/` wired in |
| WP-16.3 | Root `ExternalLinkUriHandler`, `AndroidLinkOpener`, `KitCopy(sensitive)`, `KitField(Secret)` state rule | `:ui:kit`, `:ui:shell`, `:android:platform` | 1–2 | Claude (kit owner) | WP-16.1 | `UriHandlerRootTest`, `SecretFieldStateTest`, `KitCopySensitiveTest` |
| WP-16.4 | Egress capture by UID and the EG scenarios inside 14's runner; canary targets added to 14's `canary_scan.sh` (§2.5, CR16-12) | `native/tool/e2e/egress/`, `native/e2e/flows/egress/`, `config/egress-allowlist.toml` | 2 | Sonnet (test-writer lane) | W14-6 runner, W14-7 fixtures | P16-3; EG-1/EG-2 green |
| WP-16.5 | `ProfileDeletionSweepTest` (including the Flutter-era stores, CR16-14), `ProviderKeyCanaryTest`, `BasicAuthTransportTest`, the ported SI-19…SI-23 vector tests | `:engine:profiles`, `:android:migration` (`FlutterEraSweep`), `:protocol:oc2`, `:core:net`, `:engine:integrations`, `:engine:project`, `:engine:runtime` tests | 1–3 | test-writer lane | 05 §3, §9, 04 §4 | green |
| WP-16.6 | `AppUpdater` (§8) + Updates page rows (size, Cancel, install-permission resume, Open the release page) + the `stableQa` feed binding (`src/qa/`) | `:engine:diagnostics`, `:core:net` downloader, `:android:platform`, `:feature:about`, `androidApp/src/qa/` | 4 | Codex (engine + platform), Claude (UI) | WP-16.1, 07 CR16-10, 14 CR16-19 | `UpdateVerifierTest`, `UpdatePolicyTest`; P16-4; EG-1, EG-6 |
| WP-16.7 | Analytics core (§5): API, `RouteId`/`FailureCategory`/`SettingId`, `None` sender and the on-disk queue in slice 1; senders, `QaFixtureAnalyticsSender` (`src/qa/`), Privacy rows, sent log in slice 4; a real sender only after D16-1 | `:engine:diagnostics`, `:feature:settings`, `androidApp/src/qa/` | 1, 4 | Codex (core), Claude (rows) | CR16-1, CR16-19 | §5.10 tests; EG-2 payloads non-empty |
| WP-16.8 | Notification auth (CR16-7) | `:android:background` | 4 | Sonnet | 11 §10.4, D16-3 | `NotificationActionAuthTest` |
| WP-16.9 | Linux rules L3 (with the sign-in listener exception), L6 (confined binds + outbox + row), L12 (egress environment), L13 (backup and restore); `ListenerAuthTest`, `GuestEgressEnvTest`, `SharedStorageBindsTest`, `LinuxBackupPlanTest`, `LinuxRestoreSafetyTest` | `:android:linux`, `:engine:runtime`, `:engine:agents`, `:feature:phone` | 3 | Opus (runtime owner) | D16-4, 13 PB-56 | tests + emulator checks: an agent cannot list `/sdcard/DCIM` unopened; an APK an agent saves to `Download/OpenCode` reaches the Install sheet |
| WP-16.10 | Wi-Fi share FD + expiry (N16-D11) | `:android:media`, `:engine:media` | 5 | Sonnet (media owner) | 10 §13 | `WifiShareFdTest`, `WifiShareExpiryTest` |
| WP-16.11 | WebView hardening (§1.8.5) and the kit link origin API (CR16-18) | `:ui:kit-content` androidMain, `:ui:kit` (`KitLinks`), `:ui:shell`, `:androidApp` manifest | 5 | Claude (kit-content) | WP-16.3 | K-SEC-9; lint; `KitWebPreviewTest` |
| WP-16.12 | Release pipeline: signing convention (+ `.debug` suffix, CR16-15), `sign_local.sh`, `native-release.yml` (with `dry_run`), `scripts/release.sh` native mode (no `sideload`; leak gate; mapping archive and `symbols-check`), verifier step names; at the switch, the Flutter workflows' `v*` tag filters become `v2.*` | build-logic, `native/tool/release/`, `.github/workflows/native-release.yml`, `.github/workflows/{android-release,desktop-linux,byo-host-bundle}.yml`, `scripts/` | 4–6 | Codex + coordinator | owner OK for any CI run | dry run on a throwaway tag: `workflow_dispatch` with `dry_run=true` (builds, signs, verifies; no draft) |
| WP-16.13 | Reproducibility (§9.2, §9.3) | build-logic, `native/tool/release/repro_check.sh` | 4–6 | Codex | WP-16.12 | P16-5 report |
| WP-16.14 | Notices, About, GPL sources release, SECURITY.md, PRIVACY.md, threat model, trust inventory | docs, `:feature:about`, `:ui:words` files | 5–6 | Sonnet (docs), owner approval to publish the sources release | 12 W-18 | `NoticesCurrentTest`, `KnownHostsDocTest`, `RepositoryHygieneTest` |
| WP-16.15 | Bridge-release ABI pre-check | frozen Flutter repo (`lib/update/`) | before the switch | Sonnet | D4 | Flutter test; P16-6 bridge leg |
| WP-16.16 | targetSdk-37 security slice (§11) | several | switch+1 | Sonnet + Opus | — | P16-2 on API 37; LAN permission flow |
| WP-16.17 | Developer verification registration (P16-7) and the local-key move (§6.3 item 2) | owner actions; record in `docs/qa/native/` | before slice 4 | owner, with the coordinator | — | P16-7 recorded; `sign_local.sh` reads the dedicated keystore copy |

Every WP brief carries the read/write sets above, "format only changed files", "reviewers never launch tests", and the
row ids it turns green.

---

## 13. Proofs (each at most about a day; each has a fallback)

| # | Proof | Acceptance | Fallback |
|---|---|---|---|
| P16-1 | TLS floor and platform trust: on the API 37 emulator, an unpinned `HardenedOkHttp` client's trust manager is the platform's (Conscrypt/NSC root), and the resolved OkHttp is 5.5.1 | assertion passes; `verifyOkHttpFloor` green | — |
| P16-2 | CT opt-in on API 36 and 37 emulators: connect to `api.github.com`, `huggingface.co`, a Let's Encrypt host, a `*.ts.net` HTTPS host, a pinned self-signed fixture (must still work through our trust manager), loopback HTTP. Negative case: inline or user trust anchors **disable** CT unless the same `<domain-config>` enables it [V developer.android.com security-config, evaluation order], so the test-only NSC puts the fixture CA in a `<domain-config>` for the fixture host that **also** contains `<certificateTransparency enabled="true"/>`; a certificate from that CA (never CT-logged) must then be refused. Images: API 37 `google_apis` x86_64 is installed; API 36 is not (14 T14-2) — download `system-images;android-36;google_apis;x86_64` (about 1.5–2 GB download, more once unpacked [U, measure; check free space first, memory `storage2-offload`]); if the disk cannot take it, run the API 36 leg on the owner's Android 16 phone with read-only adb only while they are present (memory `owner-phone-debug-allowed`) | every positive connection succeeds; the negative case is refused on both API levels | ship without the opt-in (API 36 default) |
| P16-3 | Egress harness: on the rooted AVD, the `owner` match and `NFLOG` (or `LOG --log-uid`) work on the emulator kernel; the image has `tcpdump` or the `dmesg` fallback works; the app's DNS lookups are attributed to its UID or names come from the host pcap; a planted `stableQa` request to `example.org` and a planted guest `curl example.org` both fail EG-1; system traffic (Play services check-in, TTS) is not counted | both planted hosts caught; EG-1 green otherwise | names from `-tcpdump` joined by IP only; the `LOG` sink if `NFLOG` is missing |
| P16-4 | Updater end to end on the emulator with `stableQa` and the `updateFeed` override (feed and assets on the FX-GH fixture at `http://10.0.2.2:<port>`, `expectedPublicSigner` = local key): a correctly local-key-signed APK with a higher versionCode installs over itself; an APK signed with a **throwaway keystore the test generates** (`keytool -genkeypair` into the test's temp folder; the CI-test key exists only in GitHub secrets) is refused at step 7; one flipped byte is refused at step 5; an asset URL outside `assetPrefix` is refused at step 3; with "Install unknown apps" off, step 7b opens the permission page and resumes; a 32-bit-only emulator image shows "needs a 64-bit phone" | all six | — |
| P16-5 | Reproducibility: two builds of one commit, different paths and worker counts, are byte-identical unsigned; `apksigcopier compare` passes against the signed one; record which of `baseline.prof`, Licensee report, CMP resources, R8 output differ if any | identical, or a named list of differing entries with fixes | report-only until fixed; F-Droid waits |
| P16-6 | Trust inventory and guest-server facts: Node 24's `--use-system-ca` availability and effect in the guest; which root list the OpenCode 2 binary uses; bridge-release ABI pre-check on a 32-bit image. **Plus**: start the pinned OpenCode 2.1.1 phone server with no providers for 10 minutes under the UID capture (§2.5) and record every destination (at least `models.dev` for the model catalogue refresh; any update check, plugin install or share host); for each, either add a `GuestLinux` `KnownHosts` row with its purpose and put it in EG-5's allowlist, or switch it off in the start spec (L12, e.g. `autoupdate` off) and record that; repeat once with `OPENCODE_DISABLE_MODELS_FETCH=1` (13 P13-5) | facts recorded in the trust inventory; KnownHosts and EG-5 updated | mark TI rows "unknown, check per incident"; allow the recorded hosts in EG-5 |
| P16-7 | Developer verification with debug-named certificates: in the Android Developer Console (open to all since March 2026), register `io.github.eslamasabry.opencode_mobile` with `842284B2…` and `1DE5BF08…`, and `.native` with `1DE5BF08…`, before slice 4 (owner action, coordinator records it) | both packages registered with every listed signer | D16-11 (rotation fallback, needs D2 amended) |
| P16-8 | Secret field IME flags: on the API 37 emulator with Gboard and AOSP keyboard, a Robolectric `EditorInfo` check plus a manual look show `IME_FLAG_NO_PERSONALIZED_LEARNING` set and no autofill save prompt after typing into `KitField(Secret)` | fallback: if `InterceptPlatformTextInput` is unavailable in the pinned CMP, `KitField(Secret)` uses the kit-content slot's `AndroidView` `EditText` with `inputType = TYPE_TEXT_VARIATION_PASSWORD` and `imeOptions` set directly |

---

## 14. New catalog rows (B-SEC-184 … B-SEC-199 reserved for this section; B-SEC-200 … 203 added by the review)

The catalog owner assigns final numbers if another section claimed one first (02 §6.1). All `scope = switch` unless
marked.

| Id | Behaviour (Given/When/Then in short) | Level | Test |
|---|---|---|---|
| B-SEC-184 | Given a question or legacy reply action on the lock screen, when tapped, then Android asks to unlock first (API 31+), or the app opens at that request without answering (API 26–30); Reject does not ask | unit | `NotificationActionAuthTest` |
| B-SEC-185 | Given a `LinkAnnotation.Url` in any composable (including sheets and dialogs), when tapped, then the link policy decides and nothing opens directly | screenshot/unit | `UriHandlerRootTest` |
| B-SEC-186 | Given any object holding a credential, when printed or logged, then the value appears as `•••` | unit | `SecretTest` |
| B-SEC-187 | Given a secret typed into a field, when the activity is recreated, then the field is empty and no saved state holds the value | integration | `SecretFieldStateTest` |
| B-SEC-188 | Given a secret, pairing link, share link or one-time code, when copied, then the clip is marked sensitive (Android 13+) | unit | `KitCopySensitiveTest` |
| B-SEC-189 | Given an update download, when its package, versionCode, signer or checksum is wrong, or its source is unexpected, then nothing installs and the person sees the plain reason with Open the release page; a moved GitHub CDN host under `*.githubusercontent.com` still updates | unit | `UpdateVerifierTest` |
| B-SEC-190 | Given a developer-signed copy, an F-Droid install or another package, when the app starts, then it does not check GitHub and the Updates page says where updates come from | unit | `UpdatePolicyTest` |
| B-SEC-191 | Given analytics, when any event is queued, then it carries only schema enums and buckets; DETAILED is never queued while off and is dropped when turned off; the random session id exists only in the queue file and in a sent payload, never in prefs, logs, reports or diagnostics, and is deleted with the queue | unit | `AnalyticsPrivacyTest` |
| B-SEC-192 | Given a Wi-Fi share, when the file or a parent folder is swapped to a symlink between the path check and the open, or after start, or the clock passes `endsAt` while the app was frozen, then the swapped target is never served (the check runs on the opened descriptor's real path), the original file is served, or the request is refused with 410 and sharing stops | integration | `WifiShareFdTest` (swap between resolve and open, swap after start), `WifiShareExpiryTest` |
| B-SEC-193 | Given the release build, then unpinned TLS uses the platform trust store, OkHttp ≥ 5.5.1, no user CAs, and the NSC has exactly the reviewed elements | contract | `verifyOkHttpFloor`, `NetworkSecurityConfigTest` |
| B-SEC-194 | Given the merged manifest, then the exported surface equals the reviewed list; `WidgetConfigureActivity` refuses a widget id that is not ours | contract | `verifyExportedSurface`, `WidgetConfigureGuardTest` |
| B-SEC-195 | Given the FileProvider, then only shared storage (parity), `files/camera-capture-v1/` (write-only, granted only to the TakePicture intent) and `cache/share/` are path roots, and an app-private file is shared only as a checked copy | contract | `FileProviderPathsTest`, `CameraCaptureGrantTest`, `ShareCopyTest` |
| B-SEC-196 | Given the dev preview, then file/content access, geolocation and third-party cookies are off, no JS bridge exists, SSL errors cancel, cache/storage/cookies are cleared before every load, a sub-frame or script navigation to another origin is refused silently, only a main-frame navigation from a gesture goes to the link policy, WebView metrics and Safe Browsing are off | integration | `KitWebPreviewTest` (Robolectric) + K-SEC-9 |
| B-SEC-197 | Given each egress scenario, then every destination is in that scenario's allowlist | e2e | egress harness EG-1…EG-7 |
| B-SEC-198 | Given built-in Linux with All files access, when an agent lists shared storage, then it sees only folders opened as projects and the `Download/OpenCode` outbox unless "Let Linux see all phone files" is on (D16-4); a file or APK an agent saves to the outbox reaches the phone-file sheet | integration | `SharedStorageBindsTest` + emulator check |
| B-SEC-199 | Given any long-running service the app starts in its Linux with a port, then it listens on `127.0.0.1` and requires authentication; a vendor sign-in callback listener lives only during its sign-in | unit | `ListenerAuthTest` |
| B-SEC-200 | Given a person who turned "Check GitHub for new versions" off in 2.x, when the native app first starts, then the check stays off | unit + journey | `UpdatePrefsMigrationTest`, 05 J1 |
| B-SEC-201 | Given a server deleted in the native app, then no entry of it remains in the Flutter-era live secure-storage file or in the S0, `replaced-*` and `start-fresh-*` copies | unit | `ProfileDeletionSweepTest` |
| B-SEC-202 | Given any program the app starts in its Linux, then it runs with the fixed egress environment (Paseo relay, web UI, dictation, voice, service proxy and MCP injection off; agent auto-update and non-essential traffic off) | unit + e2e | `GuestEgressEnvTest`, EG-5 |
| B-SEC-203 | Given a Linux backup, then it never contains sign-ins, tokens, keys, `.env*` or a password-bearing git remote; given a restore, then only allowlisted entries are extracted, traversal and symlinks are refused, and no project is overwritten | unit | `LinuxBackupPlanTest`, `LinuxRestoreSafetyTest` |

| B-SEC-204 | Given a secret field, when it opens with a stored value, then it shows "Saved · Replace" and no input until Replace; when typing, then the IME is told not to learn (`IME_FLAG_NO_PERSONALIZED_LEARNING`), autofill neither saves nor offers the value, and nothing is prefilled (DESKTOP-WEB-CROSS-64; N16-D16) | unit (Robolectric `EditorInfo`) | `SecretFieldImeTest`, `SecretFieldPresenceTest` |

---

## 15. Acceptance checklist owned by this section (switch gate)

| # | Check | Tier | Blocks the switch? |
|---|---|---|---|
| A16-1 | All SI rows hold: their named tests green, K-SEC-1…12 clean, `SecRowCoverageTest` green | T1/T2 | yes |
| A16-2 | S-22 canary scan: 0 hits across EG-1…EG-7 and the upgrade journey (ADR §10.2) | T3 | yes |
| A16-3 | S-22 egress: EG-1…EG-7 green on the release candidate (EG-1, EG-2 and EG-6 on `stableQa` of the same commit, 14 §4.4), attributed by UID (§2.5) | T3 | yes |
| A16-4 | S-23 / 12 X-01…X-31: `BannedDependenciesTest`, `PrereleaseGuardTest` (fail mode) green | T2 | yes |
| A16-5 | `verifyOkHttpFloor`, `NetworkSecurityConfigTest`, P16-1 recorded; P16-2 result decides N16-D4 | T2/T3 | yes |
| A16-6 | Updater P16-4 green (on `stableQa` with the `updateFeed` override); the switch candidate, built and signed with the public key by a `native-release.yml` `dry_run=true` run, passes steps 1–7 of §8.3 against a locally assembled release-shaped JSON (the real asset names, its `SHA256SUMS`, `Public` feed values) | T3 | yes |
| A16-7 | Signing: `apksigner` shows the right single signer for the owner candidate (local) and the release candidate (public); v2+v3, no v1 | T2 | yes |
| A16-8 | Component freeze + `verifyExportedSurface` green against the release APK | T2 | yes |
| A16-9 | `NoticesCurrentTest` green; the GPL sources release exists (D16-8) and the notices link it | T2 | yes |
| A16-10 | PRIVACY.md rewritten (en + ar), `KnownHostsDocTest` green; SECURITY.md and threat model updated | review | yes (02 switch-ready 5) |
| A16-11 | Analytics: EG-2's fixture payloads are non-empty and pass the canary scan; `sender=none` unless D16-1 answered (best answered before the release candidate; if not, the switch ships with the on-disk BASIC queue of §5.6 rule 7); if answered, EG-1 shows only that host and §5.8 rows render | T3 | yes |
| A16-12 | Bridge release carries the ABI pre-check (WP-16.15) | Flutter tests | yes, if D4 ships |
| A16-13 | Reproducibility report P16-5 exists (report-only; not blocking) | VPS | no |
| A16-14 | No `-qa` or `-debug` APK, no debuggable APK (`aapt2 dump badging` `application-debuggable`) and no QA hook in any delivered or released APK (`verifyQaHooksAbsent`, `deliver.sh`, the auto-install rule) | T2 | yes |
| A16-15 | Developer verification registration done or scheduled before enforcement reaches the owner's region (D16-7); P16-7 recorded | owner | no (date-driven) |
| A16-16 | The R8 mapping of the release candidate is archived and `release.sh symbols-check` passes | release | yes |
| A16-17 | The Flutter workflows no longer start on a v3 tag (tag filters `v2.*`); the leak gate passed on the tag commit | release | yes |

---

## 16. Owner decisions

Each has a default; work proceeds on it.

| # | Decision | Options | Recommended default |
|---|---|---|---|
| **D16-1** | **Which analytics service receives BASIC (and opted-in DETAILED) events?** Best answered before the release candidate, so the switch's one-time reports (did the update and migration work?) are sent; until then they wait on the phone for up to 90 days | (a) Aptabase EU cloud (`A-EU-` key; privacy-first, made for apps, no identifiers); (b) self-hosted Aptabase on a small separate server (AGPL server, data stays with you; another machine to run); (c) PostHog EU cloud (more analysis, more to configure: discard IPs, no person profiles); (d) none for now; (e) Google Analytics 4 through its Measurement Protocol (plain HTTPS, no Google SDK; a Google destination; Google treats it as a supplement, so some reports stay empty). Your list included Firebase: the **Firebase SDK** itself is ruled out (it is Google's SDK, which the no-SDK rule S-23 and F-Droid both forbid); (e) is the SDK-free way to reach the same Google product | **(a) Aptabase EU cloud.** Until you answer, the build ships `sender=none` and sends nothing. |
| D16-2 | **Recorded, not asked** (memory `freeze-owner-decisions-2026-11-01`: "basics we need regardless", F-Droid caveat known; "don't re-raise these"): BASIC stays always on everywhere; one-time "basic app health reports" line in 3.0's What's new | — | Only note: F-Droid's "Tracking" label will appear at submission |
| D16-3 | Lock-screen question answers and the old quick Reply need the phone unlocked (Reject still does not) | yes / keep today's | Yes |
| D16-4 | The phone's Linux sees only folders you opened as projects, plus one folder `Download › OpenCode` where agents save files and APKs for you (they can no longer save anywhere else in Downloads); a visible row "Let Linux see all phone files" turns the old behaviour back on | yes / yes without the outbox (agents can no longer hand you files through Downloads at all) / keep today's (whole shared storage) | Yes, with the outbox |
| D16-5 | F-Droid route after the switch | (a) main F-Droid repo with reproducible builds and our signature; (b) our own F-Droid repo first (a new repo key to guard); (c) not now | (a), after the F4–F7 source builds — **[U]** until fdroidserver's policy on `CN=Android Debug` signers is known (F12, D16-11) |
| D16-6 | Supported versions in SECURITY.md | latest native only / + bridge security patches for 3 months | + bridge security patches for 3 months |
| D16-7 | Developer verification account | full ($25, ID) / limited (≤ 20 devices, free) | Full, before global enforcement; register the packages and signers in §6.5 |
| D16-8 | GPL source offer for proot/talloc as an immutable `third-party-sources-1` GitHub release | yes / host elsewhere | Yes |
| D16-9 | Key custody: confirm two offline backups of each keystore and passwords exist; copy the local key out of `~/.android` to its own place (Android tools can silently replace that file, which would lock your phone's app out of updates for good); release job behind a GitHub `release` environment with you as reviewer | yes | Yes (actions for you) |
| D16-10 | 32-bit-only phones stay on the Flutter 2.x line (the native ABI set is 64-bit, ADR §9.9) | accept / add `armeabi-v7a` back (no on-phone Linux there anyway) | Accept; the bridge updater and notes say so |
| D16-11 | **Only if** P16-7 (developer verification) or F-Droid refuses our `CN=Android Debug` certificates: move to a non-debug key with an APK Signature Scheme v3 rotation (installed copies keep updating in place; needs ADR D2 amended) | rotate the refused lineage / stay and skip the refusing route | Decide when the refusal happens; until then, keep both lineages (D2) |

---

## 17. Sources and verification log (2026-11-01)

Repo [R] (read-only `/home/eslam/Storage/Code/wt/oc_app-batch1`, plus the freeze tip in `/home/eslam/Storage/Code/oc_app`):
`docs/security/threat-model.md`; `docs/verification/in-app-ubuntu-trust-2026-10-07.md`; `docs/aptabase-telemetry-plan.md`;
`PRIVACY.md`; `THIRD_PARTY_NOTICES.md`; `LICENSES/`; `lib/ui/widgets/external_link.dart`; `lib/voice/model_manifest.dart`;
`engine/phone/Cargo.toml`; `.github/workflows/android-release.yml`, `android-quality.yml`, `android-device.yml`;
`scripts/release.sh`; `docs/releases/v1.0.43+49.md`, `v1.0.44+50.md`; commits `6e0fc0482` (Flutter 3.47.8 TLS fix),
`fc5c1e78f` (its merge), `6b686bb7c` (app-updates merge: `AppUpdateChannel.kt`, `AppUpdatePolicy.kt`,
`docs/design/app-updates-2026-10-31.md`).

Measured [V]: `apksigner verify -v --print-certs build/vps-apk/app-release-4511.apk` → v2 + v3, no v1, one signer
`1de5bf08…d60c`, DN `CN=Android Debug`, RSA 2048. Review pass (2026-11-01): `keytool -list -v` on
`~/.android/debug.keystore` (alias `androiddebugkey`) shows SHA-256 `1D:E5:BF:08…:D6:0C`, and the freeze-tip
`android/key.properties` points at that file; `gh repo view Eslamasabry/opencode-mobile-next` → `PUBLIC`; the freeze
tip (`int/freeze-2026-11-01` = `fc5c1e78f`) has `FLAG_ONE_SHOT or FLAG_MUTABLE` at `TermuxActivityCommands.kt:98`,
`oc.appUpdate.{enabled,lastCheckMs,release}` at `lib/update/app_update_controller.dart:71-73`, `models-dev.refreshed` at
`lib/api2/events.dart:401`, `https://api.github.com/user` at `lib/domain/git/github_device_flow.dart:24`, the Paseo and
agent egress settings at `PhoneAgentHost.kt:230`, `BuiltinLinux.kt:775/864`, `phone_agents_host.dart:590/943-956`,
`OPENCODE_DISABLE_MODELS_FETCH` only in `aiteam_scripts.dart` and the setup probe, LiteRT-LM 0.19.0 in
`android/app/build.gradle.kts:181`, `v*` tag triggers in `android-release.yml`, `desktop-linux.yml`,
`byo-host-bundle.yml`, and the Store-mode refusal of the public key as "the legacy Android debug certificate" at
`scripts/release.sh:257-260`. Installed system images: API 34, 35, 37 `google_apis` only. `curl -I` on
`github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.0%2B57/SHA256SUMS` → 302 to
`release-assets.githubusercontent.com`; Hugging Face `resolve/<rev>/base-encoder.int8.onnx` → 302 to
`us.aws.cdn.hf.co`. Maven Central `com.squareup.okhttp3:okhttp` metadata: latest 5.5.1.

Web [V unless marked]:
- OkHttp CHANGELOG 5.5.1 (2026-10-22): "Block 8 compromised eMudhra intermediate certificates … not necessary for Android
  devices with Google Play Services … protects JVMs and AOSP devices" — `raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md`.
- Google security blog, "Protecting Chrome and Android users from compromised eMudhra intermediate CA certificates"
  (2026-10-20): Play services update on 2026-10-19; CT-enforcing clients protected.
- Firebase, "Updates required to remediate eMudhra certificate compromise" (updated 2026-10-29): `dart:io` uses its own
  BoringSSL and ignores OS blocklists; fixed in Dart 3.13.7 / Flutter 3.47.8.
- developer.android.com Network security configuration: CT opt-in on API 36, default on 37; no CT with custom trust
  anchors; API 28+ defaults trust system CAs only; implicit localhost config on 37.
- developer.android.com Behavior changes for apps targeting Android 17: CT default, ECH, `ACCESS_LOCAL_NETWORK`, Safer
  native DCL.
- developer.android.com local network permission and local network definition: inbound TCP needs the permission;
  RFC1918, link-local and `100.64.0.0/10` are local; VPN and cellular excluded.
- f-droid.org Reproducible Builds: `Binaries:`, `AllowedAPKSigningKeys`, apksigcopier, the build-tools 35 warning,
  baseline-profile and R8 pitfalls.
- posthog.com/docs/api/capture: `/i/v0/e/`, `/batch/`, `$process_person_profile: false`.
- `aptabase/aptabase-kotlin` `Aptabase.kt`: `POST <base>/api/v0/event`, header `App-Key`, body fields.
- [S] XDA on Android 14 updatable root certificates in the Conscrypt APEX.
- developer.android.com security-config (fetched in the review pass): CT evaluation order per `domain-config` — an
  explicit `<certificateTransparency enabled="true"/>` wins over user or inline trust anchors, which otherwise disable CT.
- docs.github.com, artifact and log retention: 1–90 days for public repositories, 1–400 for private ones.
- gitlab.com/fdroid/fdroidserver issue #1171 (proposal to refuse `CN=Android Debug`-signed APKs; status not shown on the
  fetched page); MR !1478 "lint: blocklist known AOSP debug keys in AASK" [S search result; merge state U].
- [U] GHSA-frr6-h895-5pxp page itself (GitHub advisories returned 404 to an unauthenticated fetch; the id comes from our
  commit message `6e0fc0482`); Plausible event API field list; Aptabase batch endpoint; Node `--use-system-ca` version;
  OpenCode 2 binary's root store; F-Droid policy on self-updaters and on Maven-Central native AARs.

Maps and research: `../map/packages-build.md` (§0 items 3–7, 10, 12; PACKAGES-BUILD-03, 05, 08, 10, 23, 29–36, 40),
`../map/builtin-linux.md` (§0, BUILTIN-LINUX-05, 15, 16, ports), `../map/media-voice.md` (Wi-Fi share risks 12, 21),
`../map/desktop-web-cross.md` (DESKTOP-WEB-CROSS-41, 46, 48, 49), `../map/builtin-linux.md` BUILTIN-LINUX-48, 50, 52, `../research/android-surfaces.md` (§2.2, §13, §14, §15),
`../research/network-data.md` (Keystore survival), `../research/media.md` (zxing-cpp, F-Droid), `../catalog/BEHAVIORS.md`
(B-SEC-001…183, B-BUILD-007…034, B-MIGR-004…013).

Owner memory: `freeze-owner-decisions-2026-11-01` (QR, F-Droid after the rewrite, analytics reversal),
`dev-freeze-native-rewrite-2026-11-01` (TLS fix allowed during the freeze), `app-updates-two-layers`,
`roadmap-issues-2026-10-31` (#37 app lock closed, #42 Play closed), `shorebird-is-mandatory`, `release-1.4.0-2026-10-15`,
`release-2.1-prep`, `apk-build-numbers`, `no-raw-errors-rule`, `actions-name-their-target`, `obvious-options-first`,
`no-new-ui-levels`, `audit-trail-rule`, `public-commit-email`, `push-freely-snapshot-recipe`, `release-1.2.0-2026-10-07`,
`phone-logcat-silenced`, `auto-install-latest-build`, `stable-release-1.0.44-2026-09-21`, `storage2-offload`,
`owner-phone-debug-allowed`.

---

## Review notes (2026-11-01 revision)

Each issue was checked against the freeze tip (`int/freeze-2026-11-01` = `fc5c1e78f`), sibling sections 01, 03, 05, 10,
13 and 14, the maps, owner memory and, where version-specific, the web. Issues are numbered in the order received.

**Fixed as proposed (verified real):**

- #1 Updater unreachable from any buildable APK (blocker): `UpdateFeed` + the `stableQa`-only `QaHooks.updateFeed`
  override (§8.2, N16-D13, CR16-19); http only to `10.0.2.2`; P16-4 uses a test-generated throwaway keystore; EG-1 and
  EG-6 rewritten; `verifyQaHooksAbsent` checks the release DEX.
- #2 Analytics qa contradiction: `QaHooks.analyticsEndpoint` + `QaFixtureAnalyticsSender` (`src/qa/`); §5.5–§5.7
  rewritten so HTTPS/KnownHosts binds every non-qa sender; release-DEX assertion added.
- #3 Duplicate canary/scanner/allowlist with 14: CR16-12; one `CanarySecrets` (16's list merged into 14's name,
  `canaries.txt` generated), one `.toml` allowlist, 14's `canary_scan.sh` as the only scanner, UID capture
  authoritative.
- #4 Whole-device pcap cannot attribute: UID attribution with iptables `owner` + `NFLOG`/`LOG` (N16-D14, §2.5); host pcap
  used for names only; tool availability on the emulator kernel is [U] under P16-3.
- #5 Guest server hosts missing: `models.dev` GuestLinux row, P16-6 extended to a 10-minute capture, EG-5 allowlist.
- #6 Strings in DETAILED events / K-SEC-11 vs `name`: `RouteId`, `FailureCategory`, `SettingId`, `WireEnum` wire
  names; `EventSpec` companion getters exempt (§5.2, K-SEC-11).
- #7 Session id stored in the queue: option (a) chosen and written into §5.6 rule 7, §5.10, B-SEC-191; the sent log is
  memory only.
- #8 K-SEC-4 too broad: limited to String-like types with the named exemptions and a planted-violation set.
- #9 Termux result PendingIntent: allowlist is 3 entries; `TermuxResultPendingIntentTest`.
- #10 Debug-DN certificates: §6.1 records both; P16-7 (register now); F12/D16-5 marked [U]; D16-11 fallback. Nuance:
  fdroidserver MR !1478 blocklists the published AOSP debug keys by fingerprint, which ours are not; issue #1171
  (refuse `CN=Android Debug`) is the real risk, status unknown.
- #11 Wi-Fi share open-then-check with `/proc/self/fd` readlink and `fstat` (§3.3, N16-D11, B-SEC-192).
- #12 Mapping retention: public repo confirmed (`gh`), 90-day cap confirmed (GitHub docs); permanent archive through
  `release.sh github` + `symbols-check` (A16-16); private symbols repo kept as the alternative.
- #13 Public key custody: option (a), `sideload` mode dropped (§6.1, §6.3, §7.4).
- #14 `QaHooksAbsentTest` rewritten against 14's mechanism as `verifyQaHooksAbsent`, with a planted-violation run.
- #15 P16-2 negative CT case: fixture CA plus explicit CT in one `domain-config` (developer.android.com evaluation order
  confirmed); API 36 image download or owner-phone fallback named.
- #16 KitWebPreview dependency: kit link API with an origin (CR16-18), no ADR change.
- #17 K-SEC-10: loopback and `src/qa/` exempt; `verifyAnalyticsEndpoint` covers the non-literal endpoint.
- #18 `:quality` cannot read `:androidApp` artifacts: those gates are now build-logic tasks on `:androidApp`;
  `stableReleaseRuntimeClasspath` everywhere.
- #19 `const val` with `repeat`: 64-hex literal written out.
- #20 "Within 6 hours" reworded; `dry_run` input defined; A16-6 compares against a locally assembled release-shaped JSON.
- #21 `api.github.com/user` added to KnownHosts.
- #22 API 26–30 lock-screen answers: locked-device check (CR16-7, §1.8.6, test case).
- #23 ABI choice follows `SUPPORTED_ABIS` order, as the freeze-tip `apkFor` does.
- #24 D16-2 turned into a recorded fact.
- #25 Camera FileProvider root (blocker): three roots, write-only grant rules, B-SEC-195 and the test updated (CR16-17).
- #27 Owner key is `~/.android/debug.keystore` (checked with `keytool`): `.debug` suffix (CR16-15, N16-D15), key moved
  out of `~/.android` (§6.3), debuggable APKs refused (A16-14).
- #28 Duplicate of 1 and 2; handled there.
- #29 `oc.appUpdate.enabled` carried over (CR16-13, §8.2, B-SEC-200).
- #32 Guest egress environment: L12, `GuestEgressEnvTest`, B-SEC-202, CR16-16.
- #33 SI coverage: SI-19…SI-24 and `SecRowCoverageTest`. The catalog has no `scope.jsonl` or scope field, so the test
  reads a map file (`native/config/sec-row-map.toml`) instead.
- #34 Linux backup and restore rules: L13, two tests, B-SEC-203, EG-5 scans the zip.
- #35 Redirect set widened to `*.githubusercontent.com` + "Open the release page" fallback.
- #36 Update UX steps: size line, Cancel, step 7b permission resume, "unavailable never shows available".
- #37 PRIVACY.md: "keep every switch-scope section" rule, 20 sections, `PrivacyCoverageTest`; §4.1 diagnostics corrected
  (the report is persisted).
- #39 WebView: silent refusal of sub-frame and script navigations, clear before every load, geolocation and third-party
  cookies off (B-SEC-196).
- #40 Duplicate of 6 and 7; handled there.
- #42 Exported surface: launch extras, `taskAffinity`/`launchMode`, merged WorkManager and ProfileInstaller components.
- #43 Leak gate added to `RepositoryHygieneTest` and the release script.
- #44 Paseo wording: Loopback or Tailnet regardless of scheme.
- #45 `REQUEST_INSTALL_PACKAGES` lists both uses; T6 covers agent-made APKs.
- #46 L3 scoped to long-running services, with the vendor sign-in callback exception.
- #47 Flutter `v*` tag triggers narrowed to `v2.*` at the switch (A16-17). Only three workflows trigger on tags
  (`android-release`, `desktop-linux`, `byo-host-bundle`); `desktop-windows` does not.

**Fixed with a different choice than proposed:**

- #30 Flutter-era stores: the gap is real and §1.4 now sweeps them (CR16-14, B-SEC-201). The claim that an R1 or R2
  restore would bring a deleted profile back is not quite right: R2 is offered only before the gate first opens
  (05 §9.4), so it can never follow a deletion, and R1 reads the live files the sweep already cleans. The actual risk
  is the plaintext drafts and queued prompts in the S0 copy, and that is what the fix covers.
- #31 Downloads outbox: a dedicated `Download/OpenCode` folder instead of binding all of `Download`, which would show
  agents every downloaded personal file. D16-4's options state the trade-off.
- #38 BASIC with `sender=none`: both halves. D16-1 is flagged "best before the release candidate", **and** the BASIC
  queue stays on disk, with the one-time switch events kept for 90 days (7 days was too short to bridge a late
  answer).
- #41 Firebase: no GA4 sender is built. D16-1 gains option (e), GA4 Measurement Protocol, and a line explaining why
  the Firebase SDK itself is excluded, so the owner confirms knowingly.

**Rejected (in part):**

- #26 The `fdroid` flavour conflict. `10-media-voice.md` §2.7 as it stands says "There is no `fdroid` flavour in this
  section": one APK, Full by default (D10-6), the gms entry removed for every build if the scanner objects. It has no
  flavour that drops sherpa-onnx, `REQUEST_INSTALL_PACKAGES` or the Install action. §5.9, F11 and F12 already agree
  with 10. The valid part is accepted: F14 (the gms `ModuleDependencies` entry) and F15 (the LiteRT-LM AAR,
  confirmed in the freeze-tip Gradle file) were added, plus a paragraph stating the one-APK rule and what a Lite
  fallback would cost.

