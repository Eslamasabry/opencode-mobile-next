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
   nothing is actually sent until you pick the service (D16-1).
5. F-Droid comes after the switch: the build is made reproducible now so F-Droid can ship our own signed APK later; the
   remaining blockers (prebuilt Linux tools, the voice engine binary, the "Tracking" label) are listed with their fixes.

Decision needed: yes, one — D16-1 (which analytics service, if any). Everything else in §16 has a default and work
proceeds on it.

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
| N16-D10 | Release builds strip `Log.v/d/i` from all code including libraries (`-assumenosideeffects`), and the `qa` build type's hooks live only in `src/qa/` with a DEX check that the release has none. | Belt and braces for K5; QA hooks (feed override, step interrupter) must never reach a person. |
| N16-D11 | The Wi-Fi share streams from the file descriptor opened when the share started, and refuses every connection after `endsAt` (even if Android froze the process past it). | Closes a check-then-use gap and the frozen-timer gap (media-voice map risk 12). |
| N16-D12 | The build is made **reproducible now** (deterministic inputs, a two-build check on the VPS, report-only until the F-Droid slice). | F-Droid can then ship our own signed APK (same signer → users can move between GitHub and F-Droid builds). Retrofitting later is harder. |

### 0.3 Conflicts found and change requests (the coordinator routes them; work proceeds on the default)

| # | Where | Conflict | Proposed change | Default until settled |
|---|---|---|---|---|
| CR16-1 | ADR §9.6 "Nothing phones home"; 02 G7, §1.1 row 17, NG15, S-22 | Owner memory `freeze-owner-decisions-2026-11-01` item 6: "Let's build tracking SDK day one … Opt in for detailed but basics we need regardless." 10 §19.5 already emits DETAILED events for "the analytics core (16)"; 12 R12-1 raises the same conflict. | Amend ADR §9.6 to: "Nothing phones home except: the update check (switchable), downloads the person starts, and the disclosed analytics tiers of section 16 §5 (BASIC always on, DETAILED opt-in), with no third-party SDK." Amend 02 row 17 / NG15 accordingly; S-22's allowlist gains the analytics host once D16-1 names it. S-23 is unchanged (still no SDK). | The core is built; `sender=none`; **nothing is sent**, so ADR §9.6 stays literally true until D16-1. |
| CR16-2 | `oc_app/AGENTS.md` ("Known signers: stable CI `2D010C21…`"); `research/android-surfaces.md` §14 ("register `1DE5BF08…` and `2D010C21…`") | Every published GitHub release is signed `842284B2…1C` (`android-release.yml:42 SIDELOAD_CERT_SHA256`, `scripts/release.sh:11`, release notes v1.0.43+49 and v1.0.44+50) [V repo]. `2D010C21…` is the CI quality-gate test signer. | Fix the AGENTS.md line in the native `AGENTS.md` and correct the research note; D18 registers `842284B2…` and `1DE5BF08…` (§6.5). | §6 table is authoritative. |
| CR16-3 | 03 §10.2 `HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }` vs 04 §4.1 `AddressClass { Loopback, Private, Tailnet, Public }` | Two names for one classifier. | Keep 04's `AddressClass` (04 owns the vectors and B-SEC-119..125); 03 §10.2 is edited to match. Link-local is `Private`; an unparseable host is a `UrlRejection`, not a class. | 04's names. |
| CR16-4 | The brief's phrase "cleartext only to loopback/emulator/Tailscale" | The binding rule (04 §4.2, B-SEC-120/121/153) is: loopback without asking; every other `http://` origin (Tailscale, private LAN, public) only after per-origin consent, with softer words for Tailscale/LAN. Paseo: `ws://` only to Loopback or Tailnet. The emulator's host alias `10.0.2.2` is `Private`. | No emulator special case in release code; QA seeds consent through the normal store (05 fixture recipe). | 04 §4.2. |
| CR16-5 | 04 §3.10 `ResumableDownloader` sits on the hardened client (no redirects) | GitHub assets answer 302 → `release-assets.githubusercontent.com`; Hugging Face model files answer 302 → `us.aws.cdn.hf.co` [V curl today]. | The downloader runs its own redirect loop: HTTPS → HTTPS only, ≤ 5 hops, each hop's host must be in the feature's `KnownHosts` redirect set, no credentials ever, resume always restarts from the **original** URL (signed redirect URLs expire). §8.4. | As stated. |
| CR16-6 | 06 `KitField(kind = Secret)` | Must not use `rememberTextFieldState()` (saveable). | N16-D6. | As stated. |
| CR16-7 | 11 §10.4 notification buttons | Question option buttons and the legacy `RemoteInput` reply answer without unlock (threat-model gap 2). | `setAuthenticationRequired(true)` on question options and legacy reply; Reject stays without (harmless). D16-3. | Require unlock. |
| CR16-8 | builtin-linux map BUILTIN-LINUX-05 (unconfined `--bind=/storage` by default) | Any agent in the guest can read all shared storage once All files access is granted. | Confined binds by default (only folders opened as projects), with a visible row "Let Linux see all phone files" (off). D16-4. | Confined. |
| CR16-9 | AI Team (post-switch, ADR D6): supervisor on `127.0.0.1:8472` with "no password yet" (builtin-linux map) | Any app on the phone can reach loopback and drive AI Team. | AI Team may not be ported without authentication on every loopback listener it starts (a switch+N gate, §3.2 L3). | Recorded as a requirement of the AI Team phase. |
| CR16-10 | 07 (screen inventory) | The freeze-tip Updates page (`feat/app-updates-2026-10-31`, merged `6b686bb7c`) and the Privacy page analytics rows are not in 07's inventory (07 was written against `oc_app-batch1`). | Add `SettingsRoute.Updates` (This app › Updates) and the three rows of §5.8 to I08 `PrivacySettingsScreen`. | Rows land in slice 4 with the updater. |
| CR16-11 | 03 §21.1 build types (release, benchmark) vs 05 (uses a `qa` build type) | `qa` is used but not defined. | Define `qa` = `initWith(release)`, `versionNameSuffix = "-qa"`, `matchingFallbacks += "release"`, debuggable false, hooks only in `src/qa/` (N16-D10). The release pipeline refuses any APK whose versionName ends `-qa` (A16-14). | As stated. |

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
| SI-4 | Secrets in memory and on screen never print, never persist in saved state, never land in the clipboard unmarked | `Secret` value class; `KitField(Secret)` on `BasicSecureTextField` with `remember { TextFieldState() }`; `KitCopy(sensitive = true)` sets `EXTRA_IS_SENSITIVE` | `:core:common`, `:ui:kit` | K-SEC-4, K-SEC-5, K-SEC-6, `SecretFieldStateTest` | B-SEC-040, 107, B-SEC-186, 187, 188 | T1 |
| SI-5 | Per-profile data is keyed `oc.<what>.<profileId>` (or registered in `ProfileScope` as a secret, blob area, table, Keystore alias or guest folder) and deleted with the profile | 05 §3 `ProfileScope` + sweep; deletion is one transaction run backwards in `:engine:profiles` | `:core:data`, `:engine:profiles` | K9 (03), `ProfileDeletionSweepTest` | B-SEC-009, 011–013, 016, 018, 026, 039, 063, 085, 093, 125, 143, 172, 178, 182 | T1/T2 |
| SI-6 | Provider credentials (`/config/providers` and friends) are never stored, shown, logged or echoed | parsed to `JsonElement`, credential values registered with the `Redactor` before DTO decoding (04 §3.7); provider DTOs have **no** key fields; UI says "Key saved on the server" | `:protocol:oc2`, `:engine:integrations` | `ProviderKeyCanaryTest` (§2.5), K-SEC-4 | B-SEC-040, 094, 149, 165, 169, 177 | T1/T3 |
| SI-7 | Plain HTTP only to loopback without asking; every other `http://` origin only after per-origin consent; no silent downgrade; credentials never cross origins; no redirects on credentialed clients | 04 §4.1–§4.6 (`AddressClass`, `TransportPolicy`, `TrustGateInterceptor`, `oc.cleartextOk.<id>`) | `:core:security`, `:core:net`, `:core:net-okhttp` | 04 B-PROTO-128 (Ktor retry), B-PROTO-139 (NSC elements), `NetworkSecurityConfigTest` | B-SEC-007, 113, 119–125, 146, 153, 175 | T1 |
| SI-8 | Basic auth is built per request from `SecretStore`, sent only after the trust gate passes, never in a URL, never retried automatically after a 401 | §1.6 | `:core:net`, `:protocol:*` | `BasicAuthTransportTest` (MockEngine), 04 §4.8 | B-SEC-007, 035, 113, 118, 138 | T1 |
| SI-9 | Certificate pins are leaf-DER SHA-256, checked during the handshake before any HTTP byte; pinned clients never fall back to PKI | 04 §4.4 `PinnedLeafTrustManager` | `:core:net-okhttp` | 04 P-N2, `PinnedLeafTrustManagerTest` | B-SEC-007, 152, 154 | T1/T3 |
| SI-10 | Unpinned TLS uses the platform trust store, which Android updates without us; our own blocklist floor is OkHttp ≥ 5.5.1 | §1.7 | `:core:net-okhttp`, `:androidApp` res | K-SEC-2, `TlsFloorTest`, `NetworkSecurityConfigTest` | B-SEC-193 | T2 |
| SI-11 | The OS-visible surface (exported components, intent filters, PendingIntents, FileProvider paths, WebView) is frozen and hardened | §1.8 | `:androidApp`, `:android:*`, `:ui:kit-content` | `ExportedSurfaceTest`, K-SEC-7…K-SEC-9, `FileProviderPathsTest`, lint table §2.2 | B-SEC-058, 180, 183, B-SEC-194–196 | T2 |
| SI-12 | Raw exception or server text is never shown as copy; it appears only redacted under Details | ADR §9.6; 03 K14, K15, K20 | `:ui:words`, translators | 03 K14/K15/K20 | B-SEC-001, 014, 098, 100, 103, 104, 110, 130–133 | T1 |
| SI-13 | Approvals bind to the exact pending request; nothing approves "always" from outside the app; lock-screen answers need unlock | 11 §10.4 + CR16-7 | `:engine:requests`, `:android:background` | `NotificationActionAuthTest` | B-SEC-105, 109, 114, 115, 147, 183, B-SEC-184 | T1/T3 |
| SI-14 | The app contacts only the person's servers and the hosts in `KnownHosts`, each for its listed reason | N16-D7 | `:core:security` | K-SEC-10, egress harness (§2.5) | B-SEC-168, 171, B-SEC-197 | T2/T3 |
| SI-15 | An app update installs only an APK with our package, a higher versionCode, the installed app's own signer and a matching `SHA256SUMS` line | §8 | `:engine:diagnostics`, `:android:platform` | `UpdateVerifierTest`, P16-4 | B-BUILD-010–012, B-MIGR-012, B-SEC-189, 190 | T1/T3 |
| SI-16 | Analytics events carry only enums, booleans and buckets from one schema file; DETAILED only with consent | §5 | `:engine:diagnostics` | K-SEC-11, `AnalyticsPrivacyTest` | B-SEC-191 | T1 |
| SI-17 | Nothing in the release APK is debug-only or QA-only | N16-D10 | `:androidApp` | `QaHooksAbsentTest` (dexdump), 12 X-31 | B-SEC-150 | T2 |
| SI-18 | No keystore, signing secret, `key.properties` or third-party skill pack is ever committed | `.gitignore` + scan | repo | `RepositoryHygieneTest` (ported) | B-SEC-150, B-BUILD-030 | T2 |

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
- **WebView navigation** goes through `shouldOverrideUrlLoading` in `KitWebPreview` (§1.8.5): same origin stays,
  anything else → `ExternalLinkPolicy` with origin `ServerContent`.
- **The opener** (`AndroidLinkOpener`, `:android:platform`): `Intent(ACTION_VIEW, Uri.parse(link.uri))`,
  `addCategory(CATEGORY_BROWSABLE)`, `FLAG_ACTIVITY_NEW_TASK`; `ActivityNotFoundException` → `Failure.Platform.NoHandler`
  ("No app on this phone can open this link.", host under Details).
- **Notification and widget taps** never carry a URL; they carry ids for `EntryRouter` (B-SEC-180).

### 1.2 Redaction and logs (SI-2)

03 §10.2 and §18 own `Redactor`, `RedactingWriter`, `AppLog` and the never-log list; 04 §3.7 owns credential ingress.
This section adds:

1. **The canary list** (`:core:testing` `Canaries.kt`) used by every leak test, so tests do not invent their own:
   ```kotlin
   public object Canaries {
       public const val SERVER_PASSWORD = "oc-canary-pw-7f3a9c2e"
       public const val PROVIDER_KEY = "sk-ant-api03-OCCANARY0000000000000000000000000000000000000000"
       public const val GITHUB_TOKEN = "ghp_OCCANARY000000000000000000000000000000"
       public const val PASEO_PASSWORD = "0cca9a7e" + "0".repeat(56)            // 64 hex, the agent-host shape
       public const val BEARER = "eyJhbGciOiJIUzI1NiJ9.eyJvYyI6ImNhbmFyeSJ9.OCCANARYsig"
       public const val PAIRING_SECRET = "oc-canary-pair-91b4"
       public val ALL: List<String> = listOf(SERVER_PASSWORD, PROVIDER_KEY, GITHUB_TOKEN, PASEO_PASSWORD, BEARER, PAIRING_SECRET)
   }
   ```
   The fixture servers (02/14 fixture set) serve these values; `/config/providers` returns `PROVIDER_KEY` in every
   key-shaped field the 2.1.1 schema has.
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
| `KitField(kind = Secret)` | `BasicSecureTextField`; state created with `remember { TextFieldState() }` (N16-D6); `KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)`; semantics `password()` and `contentType = ContentType.Password` (autofill for password managers); Reveal shows the text only while held/toggled and re-hides on leave |
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

`ProfileDeletionSweepTest` (`:engine:profiles` commonTest, fakes for every store): seeds **every registered shape** for
profiles A and B, deletes A, asserts zero A entries in every store and B byte-identical. A new registration without a
seed in this test fails it (the test iterates `ProfileScope.registrations`).

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
`Canaries.PROVIDER_KEY` is absent from: every DTO's `toString()`, the `LogRing`, `Diagnostics.errors`, the
`ProblemReport` text, the analytics queue, every `XUi` produced (via `toString()`).

### 1.6 Transport: cleartext, Basic auth, pins, redirects (SI-7, SI-8, SI-9)

04 §3–§4 is the implementation. The invariants a reviewer checks:

1. **Who may speak plain text** (04 §4.2): OpenCode 2 `http://` to Loopback without consent; to Tailnet/Private/Public
   only with `oc.cleartextOk.<profileId>` equal to the exact origin. Paseo `ws://` only to Loopback or Tailnet. Codex
   `ws://` only to Loopback. Downloads, the update check and analytics: `https` only.
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
3. **OkHttp ≥ 5.5.1** in `releaseRuntimeClasspath` (ADR §8 pins 5.5.1). `TlsFloorTest` (`:quality`) reads the
   resolved graph and fails below it, so a Ktor bump can never pull OkHttp backwards. This covers AOSP phones without
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

#### 1.8.1 Exported components (frozen list; `ExportedSurfaceTest`)

`ExportedSurfaceTest` (`:quality`, T2) parses the merged release manifest and compares, per component: FQCN,
`exported`, `permission`, every `<intent-filter>` (actions, categories, data). It extends 03's `ComponentFreezeTest`
(names) with the security attributes. The expected file `config/exported-surface.txt` may change only with a CR16 row.

| Component (FQCN under `io.github.eslamasabry.opencode_mobile`) | Exported | Guard | What any app can make it do |
|---|---|---|---|
| `.MainActivity` | yes | — | open the app; `SEND text/plain` → a **draft only**, never sent (B-NAV share rows); `VIEW opencode-mobile://session` and `://team` → ids only, network-silent, waits for the gate (B-SEC-155–162) |
| `.ManageSpaceActivity` | yes (system needs it) | — | show the page; deleting needs the person's confirm |
| `.WidgetConfigureActivity` | yes (`APPWIDGET_CONFIGURE`) | checks `AppWidgetManager.getAppWidgetInfo(id)?.provider?.packageName == packageName`, else finishes with `RESULT_CANCELED` (B-SEC-194) | nothing for a foreign id |
| widget providers (7) | yes (`APPWIDGET_UPDATE`) | system broadcast | redraw from the cache |
| `.AttentionTileService`, `.PauseTileService` | yes | `android.permission.BIND_QUICK_SETTINGS_TILE` | only the system binds |
| `.ScheduledPromptReceiver`, `.BuiltinRestoreReceiver` | **no** (as today [V manifest]) | the system still delivers its own broadcasts (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, …) to non-exported receivers; `onReceive` ignores every other action | nothing |
| `.CodingActionReceiver`, `.StatusActionReceiver`, `.LivePauseReceiver`, `.WidgetActionReceiver`, `.BuiltinIdleReceiver`, `.AppUpdateStatusReceiver` | **no** | explicit intents from our PendingIntents | — |
| `.WidgetRouteActivity`, `.WidgetOverlayActivity` | **no** | — | — |
| services (`BuiltinServerService`, `SetupService`, `BackgroundConnectionService`, `TermuxResultService`, `LocalPdfService`) | **no** | `LocalPdfService` is `isolatedProcess` | — |
| `androidx.core.content.FileProvider` (`${applicationId}.localfiles`) | no, `grantUriPermissions="true"` | §1.8.3 | read a granted URI only |

#### 1.8.2 PendingIntents and intents

- Every `PendingIntent` is `FLAG_IMMUTABLE` with an explicit component, a unique request code and a unique `data` URI
  (11 T17), **except** two reviewed cases (K-SEC-7 allowlist): the legacy `RemoteInput` reply (must be mutable) and the
  `PackageInstaller` status receiver (the system fills extras).
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
resource name (10 C10-10) and allows exactly two roots:

```xml
<!-- androidApp/src/main/res/xml/local_file_paths.xml -->
<paths>
    <external-path name="shared_storage" path="." />   <!-- parity: open a phone file the Linux saved to shared storage -->
    <cache-path name="share" path="share/" />          <!-- copies of app-private files (viewer, rootfs) for Share / Open with; emptied at start -->
</paths>
```

Never `files-path`, `root-path`, `external-files-path` of the whole tree, or `path="/"` under `cache-path`. A URI is built
only for the one file the person chose, granted per intent (`FLAG_GRANT_READ_URI_PERMISSION`, read-only), never
persisted. A file inside the app's private storage (including `files/linux/ubuntu`) is never served from its real path:
it is first checked by 10 §13.2 `PathSafety` (no `looksSecret` file) and copied into `cache/share/`. Share-in URIs are
read once into the draft vault with the MIME gate and size caps of 10 §6 and never kept as URIs.
`FileProviderPathsTest` parses the XML and fails on any other element (B-SEC-195).

#### 1.8.4 Permissions (delta from Flutter)

| Permission | Change | Note |
|---|---|---|
| `REQUEST_INSTALL_PACKAGES` | **added** (already on the freeze tip) | the updater (§8); Play policy irrelevant (not on Play) |
| `com.termux.permission.RUN_COMMAND` | kept while Termux migrate-out exists (D7); removed with it | narrows the surface later |
| `MANAGE_EXTERNAL_STORAGE` | kept, requested lazily only when a shared-storage project is opened (threat model scenario 6) | with D16-4 the guest sees only opened folders |
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
| `shouldOverrideUrlLoading` | same scheme+host+port as the started preview → load; else `ExternalLinkPolicy` | B-SEC-058 |
| Allowed start URL | `http(s)` only, no user info, host = the preview's built address | B-SEC-058 |
| `WebView.setWebContentsDebuggingEnabled` | false in release | |
| Manifest `<meta-data android:name="android.webkit.WebView.MetricsOptOut" android:value="true"/>` | added | no WebView usage metrics upload |
| Manifest `<meta-data android:name="android.webkit.WebView.EnableSafeBrowsing" android:value="false"/>` | added | the preview loads only the phone's own dev servers; Safe Browsing lookups would add a Google destination to the egress list for no protection |
| cookies / storage | cleared when the preview closes | |

#### 1.8.6 Notifications, widgets, tiles (summary; 11 §10–§11 own the detail)

- Channels `VISIBILITY_PRIVATE` with a count-only public version; content never shows a secret (B-SEC-105/106).
- **Allow once** has `setAuthenticationRequired(true)`; **Always allow** never appears outside the app; question
  options and the legacy reply also require unlock (CR16-7, D16-3, B-SEC-184); **Reject** does not.
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

---

## 2. Enforcement

### 2.1 Konsist rules added to 03 §19.4 (`:quality`, K-SEC prefix)

| # | Rule | Scope |
|---|---|---|
| K-SEC-1 | No `Linkify`, `autoLinkMask`, `setAutoLinkMask`, `android:autoLink`; `LocalUriHandler provides` only in `:ui:shell` `App.kt` | everywhere |
| K-SEC-2 | `X509TrustManager`, `X509ExtendedTrustManager`, `TrustManagerFactory.init(`, `SSLContext.init(`, `HostnameVerifier`, `.sslSocketFactory(`, `.hostnameVerifier(` only in `:core:net-okhttp` | everywhere |
| K-SEC-3 | No `okhttp3.CertificatePinner`, no `HttpLoggingInterceptor`, no Ktor `Logging`, `HttpRequestRetry`, `HttpRedirect` installs (12 X-30, mirrored here so a reviewer sees it in one table) | everywhere |
| K-SEC-4 | A property named `(?i).*(password\|passwd\|secret\|token\|apikey\|api_key\|privatekey\|bearer\|credential).*` declared in a `data class` or `@Serializable` class must have type `Secret` or `Secret?` — except wire DTO packages listed in `config/secret-dto-allowlist.txt` (where the value is decoded only to be handed to `Secret(…)` at once); `Secret.reveal()` calls only in files listed in `config/secret-reveal-allowlist.txt` (transports, `SecretStore`, `Redactor`, migration) | production code |
| K-SEC-5 | No `Secret` type in: a class implementing `NavKey`/`AppRoute`, a `SavedStateHandle` set, a `rememberSaveable`, `rememberTextFieldState`, `workDataOf`/`Data.Builder`, `Intent.putExtra`, `Bundle.put*` | production code |
| K-SEC-6 | `ClipData.newPlainText` / `ClipEntry(` only in `:ui:kit` `KitCopy`/`KitPlatform` | everywhere |
| K-SEC-7 | Every `PendingIntent.getActivity(`, `getBroadcast(`, `getService(` or `getForegroundService(` call passes an expression containing `FLAG_IMMUTABLE`, except the files in `config/mutable-pendingintent-allowlist.txt` (2 entries, §1.8.2) | `:android:*`, `:androidApp` |
| K-SEC-8 | No `Intent.parseUri`, `Intent.getIntent(`, and no `startActivity(`/`startActivities(` whose argument comes from `getParcelableExtra`/`getParcelable` — except `PackageInstallerUpdater.kt` | `:android:*`, `:androidApp` |
| K-SEC-9 | `WebView(` construction only in `KitWebPreview.kt`; `addJavascriptInterface`, `setAllowFileAccess(true)`, `setAllowUniversalAccessFromFileURLs`, `MIXED_CONTENT_ALWAYS_ALLOW`, `handler.proceed(` banned everywhere | everywhere |
| K-SEC-10 | Every `"https://` / `"http://` string literal in production code has a host listed in `KnownHosts` (fetch, redirect or link set), or is in a file named `*Fixtures.kt`/a test source set | production code |
| K-SEC-11 | Classes implementing `AnalyticsEvent` declare properties only of enum types, `Boolean`, `CountBucket`, `DurationBucket`, `VersionCode` (no `String`, `Int`, `Long`, collections, `JsonElement`) | `:engine:diagnostics`, every emitter |
| K-SEC-12 | `kotlin.random.Random` and `java.util.Random` not imported in `:core:security`, `:engine:profiles`, `:engine:media` (`share/`), `:engine:diagnostics` (`analytics/`), `:android:linux`, `:android:platform`; tokens and passwords come from `secureRandomBytes` | listed modules |

[U P9 in ADR §11 already proves Konsist sees `commonMain` in this layout; K-SEC rules ride the same harness.]

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

### 2.3 `:quality` gates added by this section (T2)

| Gate | Checks | Rows |
|---|---|---|
| `ExportedSurfaceTest` | §1.8.1 | B-SEC-194 |
| `NetworkSecurityConfigTest` | exactly the elements of §1.7 rule 4 (and 04 §4.7) | B-PROTO-139, B-SEC-193 |
| `FileProviderPathsTest` | §1.8.3 | B-SEC-195 |
| `TlsFloorTest` | resolved `com.squareup.okhttp3:okhttp` ≥ 5.5.1 in `stableReleaseRuntimeClasspath` | B-SEC-193 |
| `QaHooksAbsentTest` | `dexdump`/apkanalyzer on the stable release APK: no class in `io.github.eslamasabry.oc.qa`; no `OC_APP_UPDATE_FEED` string; versionName has no `-qa` | SI-17 |
| `KnownHostsDocTest` | every `KnownHosts` fetch host appears in `PRIVACY.md`'s destinations table and in `config/egress-allowlist.toml` | SI-14 |
| `RepositoryHygieneTest` (ported from `test/repository_hygiene_test.dart`) | no `*.jks`, `*.keystore`, `key.properties`, `*.p12` in git; no `.claude/skills/`; governance files present; non-affiliation statement at every public entry point; `SECURITY.md` present | B-SEC-150, B-BUILD-030 |
| `CanaryLeakTest` | scans `**/build/test-results/**/*.xml` and `**/build/reports/**` of the T2 run for every `Canaries.ALL` value | SI-2 |

### 2.4 Unit-level tests this section owns (T1)

`ExternalLinkPolicyTest` (vectors §1.1), `UriHandlerRootTest`, `SecretTest` (`toString`, equality, no reveal in
`toString` of containing classes), `SecretFieldStateTest` (Robolectric: rotate the activity with a secret typed → the
restored field is empty and the Bundle has no canary), `KitCopySensitiveTest` (API 33 Robolectric: `EXTRA_IS_SENSITIVE`
set), `ProfileDeletionSweepTest` (§1.4), `ProviderKeyCanaryTest` (§1.5), `BasicAuthTransportTest` (header only after
the gate; 401 latches; no header on a redirect target because none is followed), `NotificationActionAuthTest`
(Robolectric: `isAuthenticationRequired` on Allow once, question options, legacy reply; not on Reject),
`UpdateVerifierTest` (§8.3), `AnalyticsPrivacyTest` (§5.10), `WifiShareFdTest` and `WifiShareExpiryTest` (§3.3).

### 2.5 The canary and egress harness (S-22's "egress test (section 16)")

Owner: test-writer lane (Sonnet) builds it in slice 2; it runs at T3 at every slice end and at the switch.

**Files**:

```
native/tool/qa/egress/
├── run.py              python3 -I; holds oc-emulator.lock; starts the AVD with -tcpdump; drives scenarios; reports
├── scenarios.toml      the scenario list and their allowlists (below)
├── pcap_hosts.py       tshark wrapper: DNS query names, TLS ClientHello SNI, TCP SYN destinations
└── canary_scan.py      logcat, dumpsys notification --noredact, files under /data/data/<pkg>/ (adb root), report text
native/config/egress-allowlist.toml   generated from KnownHosts by a Gradle task; checked by KnownHostsDocTest
```

**AVD**: `OC_EGRESS` = the API 36 x86_64 **`default`** (AOSP, no Google Play services) system image if the SDK offers
it for 36, else `google_apis` [U P16-3]. Before each scenario the harness records a 5-minute baseline with the app
force-stopped; hosts seen in the baseline (connectivity checks, NTP, Play services) are subtracted.

**Method**: start the emulator with `-tcpdump build/egress/<scenario>.pcap`; run the scenario (Maestro flow + adb);
extract `(name, port)` from DNS answers, SNI and SYN destinations; every destination must match the scenario's
allowlist (glob on names; `10.0.2.2:<port>` for fixtures). App attribution comes from the baseline subtraction plus a
`qa`-build `EgressRecorder` (an OkHttp `EventListener` that records `dnsStart` names and `connectStart` addresses into
`files/native/qa/egress.jsonl`); a name the recorder saw but the allowlist lacks fails, and so does a pcap name that
neither the baseline nor the recorder explains (traffic from WebView, the guest Linux or a library outside OkHttp).

**Scenarios** (`scenarios.toml`):

| Id | Steps | Allowed beyond the baseline | Rows |
|---|---|---|---|
| EG-1 | fresh install, first run, no servers, 5 min idle, open Settings › This app › Updates, Check now | `api.github.com` (+ the analytics host only after D16-1) | B-SEC-197 |
| EG-2 | daily path: OC2 fixture at `10.0.2.2:4096` (consented HTTP), open chat, stream a reply, approve, attach a photo, read aloud with the system voice | EG-1 + `10.0.2.2:4096` | B-SEC-197 |
| EG-3 | Paseo + Codex fixtures, steer, stop | EG-1 + fixture ports | |
| EG-4 | voice pack download (Balanced) | EG-1 + `huggingface.co`, `*.hf.co` | B-MEDIA-031 |
| EG-5 | phone setup (built-in Linux, OpenCode only) | EG-1 + `cdimage.ubuntu.com`, `archive.ubuntu.com`/`ports.ubuntu.com`, `security.ubuntu.com`, `nodejs.org`, `registry.npmjs.org` | B-SEC-092, 171 |
| EG-6 | update: qa feed override pointing at a fixture release | fixture host only (the override replaces `api.github.com`) | B-SEC-189 |
| EG-7 | Wi-Fi share started and stopped, no client | nothing outbound | B-MEDIA-034 |

**Canary scan** (same run): the fixtures serve `Canaries.*`; after each scenario `canary_scan.py` greps
`adb logcat -d`, `adb shell dumpsys notification --noredact`, the exported problem report text (captured through the
`qa` share hook), the analytics queue and sent log, and every file under `/data/data/<pkg>/` with these exceptions:

- ciphertext stores: `shared_prefs/oc_secrets_v1.xml`, `shared_prefs/FlutterSecureStorage*.xml`,
  `FlutterSecureKeyStorage.xml`, and their step-0 copies in `files/migration/flutter-v57/`;
- the rootfs `files/linux/ubuntu/**` is out of scope (the guest is one trust zone, §3.2), **except** the files the app
  itself writes there (`RuntimeHost.appWrittenFiles()`), where only the documented secret files may contain their own
  secret: `/root/.oc-builtin/server.password` (phone-server password), `/home/oc/.oc-profiles/<id>/paseo/config.json`
  (agent-host password), the quota token file, and a GitHub token file only while its command runs (B-SEC-081).

Any other hit fails with the file and byte offset. Results go to `docs/qa/native/egress-<YYYY-MM-DD>/README.md`
(audit-trail rule).

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
| T5 | **Another app on the same phone** | connect to `127.0.0.1` listeners; send intents; draw overlays | phone server Basic auth (32 random bytes); Paseo agent host password (64 hex); quota collector bearer; exported surface frozen (§1.8.1); immutable PendingIntents; deep links ids only; overlay touches blocked by Android 12+ | AI Team supervisor has no auth (CR16-9, blocks its port); dev servers agents start are the person's own and unauthenticated; tapjacking on API 26–30 |
| T6 | **Hostile content** read by an agent (prompt injection from a repo or web page) | make an agent run commands or read files | approvals; auto-approval off by default and never "always" from a notification (B-SEC-080, 147); D16-4 confines the guest's view of shared storage | an agent the person allowed to run tools acts with that permission |
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
| L3 | Every listener the app starts in the guest binds `127.0.0.1` **and** authenticates: OpenCode 4097 (Basic), Paseo 4099 (password), quota collector (bearer), phone engine (credentials). A listener without auth may not ship; AI Team's supervisor (8472) gets auth before AI Team is ported (CR16-9) | `:engine:runtime` start specs; `ListenerAuthTest` asserts every `ServiceSpec` with a port has an auth kind |
| L4 | Secrets reach the guest only as 0600 files written from the app side (`umask 077`, temp + rename), never on a command line or in a script body; GitHub tokens as one-use 0600 files in a 0700 random folder, deleted after the command (B-SEC-081) | `:android:linux`, `:engine:project` (11 §6.2 step 3 already says "never on a command line") |
| L5 | Agents run with `--change-id=1000:1000`, their own `HOME`, the `/root` view replaced; no secrets in agent profiles (option A's cheap defences) | `ProotLauncher` (11 §5.3) |
| L6 | **Shared storage**: with D16-4 the guest sees only folders opened as projects (today's "confined mode" becomes the default); a visible row "Let Linux see all phone files" (off by default, plain warning) restores `--bind=/storage` | `SharedStorageBinds`, `:feature:phone` |
| L7 | Everything the app downloads into the guest is HTTPS, pinned by version and SHA-256, from a `KnownHosts` host (B-SEC-092, 171); manifests never point at private addresses | `:engine:runtime` setup engine, `:engine:agents` catalog |
| L8 | npm installs pin exact versions; the registry's `integrity` (sha512) is checked by npm itself; lifecycle scripts run (agents need them) — accepted | runtime scripts |
| L9 | Executables run only from `nativeLibraryDir` (proot set) or inside proot; the app never `System.load`s a writable file (Android 17 Safer Native DCL: loaded files must be read-only [V]) | `:android:linux`, `:android:media` |
| L10 | The guest trust store is refreshed by the layer-2 updater (TI-3) | runtime |
| L11 | Dev servers started by agents may bind `0.0.0.0` and become reachable from the Wi-Fi. Post-switch improvement (not parity): the Linux manager probes each detected port on the phone's Wi-Fi address and labels "Others on this Wi-Fi can open this". The phone-context instruction injected into agents (roadmap 2026-10-31 bug 3) asks them to bind `127.0.0.1` | `:engine:runtime` (later), `:engine:agents` |

### 3.3 Wi-Fi share (T8)

10 §13 is the implementation (192-bit token, Wi-Fi/hotspot interface only, GET/HEAD, constant-time compare, `nosniff`,
`no-store`, 15 minutes, never app-private files, B-MEDIA-034, B-SEC-022). Threat notes and the two tightenings
(N16-D11):

| Threat | Effect | Mitigation | Status |
|---|---|---|---|
| Sniffing an open or shared Wi-Fi | the token travels in the URL in clear; a sniffer can download within the 15 minutes or alter the file in transit | the sheet's warning "Anyone on this Wi-Fi with the link can download this file."; one file; read-only; "Downloaded N times" lets the person notice extra downloads | accepted (it is plain HTTP by design, so a phone browser can open it without certificates) |
| Swap after check | a symlink inside an allowed root is swapped to a secret after `PathSafety` approved it | **open the file once at `start` (`FileInputStream`/`FileChannel` on the resolved real path, `O_NOFOLLOW` semantics via `Os.open(path, O_RDONLY or O_NOFOLLOW)`), keep the descriptor, serve every request from it with `pread`/`position`**; close at stop | new, B-SEC-192 |
| Frozen process outlives 15 min | Android freezes the app; the timer never fires; on thaw the server still accepts | every accepted connection checks `clock.now() < endsAt` first; otherwise answer `410 Gone`, close, and stop the share | new, B-SEC-192 |
| Token leaks through the app | logs, diagnostics, analytics, clipboard | the token is registered with the `Redactor` at start; `wifi_share_started` has no fields (10 §19.5); Copy link uses `sensitive = true` | B-SEC-188 |
| Resource exhaustion | many connections | ≤ 4 at once, 10 s socket timeout, 8 KiB request head (10 §13.3) | parity |
| Hotspot clients | the phone's own hotspot clients can reach it | same token rule | accepted |
| targetSdk 37 | inbound LAN needs `ACCESS_LOCAL_NETWORK` [V] | the targetSdk-37 slice (§11) | later |

### 3.4 Updates and supply chain (T9)

| Path | Attack | Mitigation |
|---|---|---|
| In-app update | network attacker swaps the APK | TLS to GitHub with platform trust; `SHA256SUMS` match; signer equality; package and versionCode checks (§8.3); Android itself refuses a different signer |
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
- Diagnostics stay in memory (20 errors, the 256 KiB ring); a problem report leaves only when the person shares it,
  after an exact preview (B-SEC-047–060).
- Crash capture stays **off by default** and category-only (B-SEC-042, 043).
- No backup, no device-to-device transfer of app data (11 §3).
- Widgets and tiles show titles or counts only.
- New in native: the analytics tiers (§5), disclosed on the Privacy page and in PRIVACY.md.

### 4.2 `KnownHosts` (N16-D7)

```kotlin
// :core:security commonMain
public enum class HostUse { Fetch, Redirect, Link }         // app fetches / a fetch may be redirected to / opened in a browser only
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
| `api.github.com` | Fetch | App | update check: start and resume, at most every 6 h, switch on (§8) | IP, `User-Agent: OpenCodeMobile` |
| `github.com` | Fetch | App | Update now (asset), Git page device sign-in (`/login/device/code`, `/login/oauth/access_token`) | IP; the device code |
| `release-assets.githubusercontent.com` | Redirect | App | update download | IP |
| `huggingface.co`, `*.hf.co` | Fetch, Redirect | App | the person picks a voice pack, the natural voice, or (if ported) the freeze tip's on-device assistant model `litert-community/gemma-4-E4B-it-litert-lm` [R `lib/local_llm/local_llm_pack.dart`] | IP, the pack's file names |
| `models.dev` | Fetch | App | a provider with no bundled mark is shown (weekly cache) | IP, the provider id |
| `registry.modelcontextprotocol.io` | Fetch | App | explicit MCP catalog refresh only (B-SEC-168) | IP, the request |
| `cdimage.ubuntu.com` | Fetch | App | phone setup (Ubuntu Base) | IP |
| `archive.ubuntu.com`, `ports.ubuntu.com`, `security.ubuntu.com` | Fetch | GuestLinux | setup, package installs, Linux manager | IP, package names |
| `nodejs.org` | Fetch | GuestLinux | setup | IP |
| `registry.npmjs.org` | Fetch | GuestLinux | setup, agent installs, layer-2 updates | IP, package names |
| `downloads.claude.ai` | Fetch | GuestLinux | Claude Code install (if chosen) | IP |
| `github.com` + `release-assets…` | Fetch, Redirect | GuestLinux | pinned agent binaries (Codex, Goose, Oh My Pi) | IP |
| the analytics endpoint (D16-1) | Fetch | App | §5.7 | §5.4 |
| `claude.com`, `auth.openai.com`, `console.anthropic.com`, `aistudio.google.com`, `tailscale.com`, `f-droid.org`, `vercel.com`, `agentclientprotocol.com`, `docs` pages | Link | App | only when the person taps | (browser) |

Model providers that agents and servers call (Anthropic, OpenAI, …) are the person's own agents' traffic, not the app's;
PRIVACY.md says so in one sentence.

### 4.3 PRIVACY.md rewrite (switch gate item 5; WP-16.10)

Written in plain words, en + ar, bundled into `:ui:words` `composeResources/files/privacy/` (07 L07 sheet). Required
sections, in order:

1. Who we are (independent project; non-affiliation statement).
2. What the app sends and to whom: the generated `KnownHosts` table (§4.2), grouped "always", "when you ask", "inside
   your phone's Linux".
3. Analytics (§5): what BASIC sends (the exact event table), that it is always on, what DETAILED adds, that it is off
   unless turned on, the endpoint operator's name and location, that the endpoint sees the IP, how to see what was sent.
4. Updates: the GitHub check, the switch to turn it off, what GitHub sees.
5. What stays on the phone; secure storage; no backup; deleting a server deletes its data; Manage space deletes all.
6. Plain HTTP: per-address consent and what it means (from 04 §4.2 wording; replaces today's stale paragraph, CR16-4).
7. Notifications, widgets, tiles: titles or counts only.
8. Problem reports: only when you share them.
9. Removed: the Shorebird paragraph.
10. Effective date and how to report a problem (SECURITY.md).

`KnownHostsDocTest` keeps section 2 in step with code.

---

## 5. The analytics core (owner decision 2026-11-01)

Owner's words (memory `freeze-owner-decisions-2026-11-01`, item 6): "Let's build tracking SDK day one that can later talk
to other analytics apps." "Opt in for detailed but basics we need regardless." The memory adds: one event API, pluggable
senders (Aptabase, PostHog, Firebase, Plausible, self-hosted), privacy rules (never content, paths, addresses or
credentials), BASIC always on (anonymous, minimal, disclosed), DETAILED opt-in, and the F-Droid "Tracking" caveat.

### 5.1 Shape (N16-D9)

```
emitters (engines, ViewModels via engine API) ──track(AnalyticsEvent)──► Analytics
   └► PrivacyGate (tier vs consent, migration gate, qa/debug routing)
        └► EventQueue (files/native/analytics/queue.json; 500 events / 256 KiB; 7 days)
             └► Uploader (on background and every 30 min in foreground; batches ≤ 25; backoff 1 min → 6 h)
                  └► AnalyticsSender (exactly one, chosen at build time: None | Aptabase | PostHog | Plausible | Custom)
                       └► SentLog (last 50 payloads, for "What was sent")
```

Module: `:engine:diagnostics`, package `io.github.eslamasabry.oc.engine.diagnostics.analytics` (engine order #2, so every
engine above it may emit; `:engine:settings` changes are emitted by `:engine:diagnostics` observing settings flows).
Senders use `HttpClientFactory` from `:core:net` (allowed for engines, ADR §6.1). No vendor SDK (12 X-04).

### 5.2 Public API

```kotlin
package io.github.eslamasabry.oc.engine.diagnostics.analytics

public enum class AnalyticsTier { Basic, Detailed }

/** Every event is a class in AnalyticsSchema.kt. Properties: enums, Boolean, buckets only (K-SEC-11). */
public sealed interface AnalyticsEvent {
    public val name: String          // snake_case, unique, ≤ 40 chars; checked by AnalyticsSchemaTest
    public val tier: AnalyticsTier
}

public interface Analytics {
    /** Never throws, never blocks, never suspends. Drops the event when its tier is not permitted. */
    public fun track(event: AnalyticsEvent)
    public val detailedEnabled: StateFlow<Boolean>
    public suspend fun setDetailedEnabled(enabled: Boolean): Outcome<Unit>   // false also drops queued DETAILED events
    public val sent: StateFlow<ImmutableList<SentPayload>>                    // for "What was sent"
}

public data class SentPayload(val atMs: Long, val senderId: SenderId, val json: String)   // json is exactly the body sent

@JvmInline public value class VersionCode(public val value: Int)
public enum class CountBucket { Zero, One, TwoToFive, SixToTwenty, MoreThanTwenty }
public enum class DurationBucket { Under100Ms, Under300Ms, Under1S, Under3S, Under10S, Under60S, Longer }

public enum class SenderId { None, Aptabase, PostHog, Plausible, Custom }
public interface AnalyticsSender {
    public val id: SenderId
    public val host: String?                                   // null for None; must be a KnownHosts Fetch host
    public suspend fun send(batch: List<WireEvent>): Outcome<Unit>
}
public data class WireEvent(
    val name: String,
    val hourUtc: Long,                                          // epoch ms truncated to the hour
    val session: String,                                        // random UUID, per process, rotated after 60 min idle, never stored
    val system: SystemProps,
    val props: Map<String, String>,                             // enum names / "true"/"false" / bucket names only
)
public data class SystemProps(
    val appVersion: String, val appBuild: Int, val channel: String,   // "stable" | "preview" | "desktop"
    val osApi: Int, val lang: String, val isDebug: Boolean,           // lang = one of the app's 7 language codes
)
```

### 5.3 Events at the switch (`AnalyticsSchema.kt`, the single source)

BASIC (always on; the "basics we need" — chosen to answer "is the native switch working?"):

| Event | When | Props |
|---|---|---|
| `app_open` | each cold start, after the migration gate opens | — |
| `app_updated` | first launch after the versionCode changed | `from_code: VersionCode` |
| `migration_done` | once, when `native.migration.v1` is set or the runner gives up | `outcome` (Done / ReentryNeeded / Failed), `failed_step` (None / S0 / M1…M6), `secrets_reentry: CountBucket`, `duration: DurationBucket` |
| `backend_connected` | first successful connect per backend kind per UTC day | `kind` (OpenCode2 / Paseo / Codex), `where` (ThisPhone / Tailnet / LocalNetwork / Internet) — an `AddressClass`, never an address |
| `update_result` | after an install attempt ends without the app being replaced | `result` (Cancelled / SignerMismatch / ChecksumMismatch / NoSpace / Failed) |

DETAILED (opt-in), at the switch: the media set of 10 §19.5; `screen_open` (`route`: the `AppRoute` class simple name,
never arguments — 07 N8); `failure_seen` (`category`: the `Failure` subclass name); `cold_start` (`duration`);
`crash_seen` (`category`: `CrashCategory`, and only while local crash capture is on — B-SEC-042 stays the gate);
`setting_changed` (`setting`: an enum of settings ids; `value`: only for enumerated settings). A feature that wants a new
event adds a class to `AnalyticsSchema.kt` (owned by the diagnostics builder; reviewed by the coordinator) and a row to
PRIVACY.md's table in the same PR.

### 5.4 Never sent (enforced by types, K-SEC-11)

Prompt or reply text, file names or paths, server addresses or names, profile ids, session ids of conversations, model
or provider names, error or exception text, device model or manufacturer, screen size, time zone, install id,
advertising id, Android id, contact or account data. The app adds no country; the endpoint may derive one from the IP
(disclosed).

### 5.5 Senders and their wire formats

| Sender | Endpoint | Auth | Body | Notes |
|---|---|---|---|---|
| `None` | — | — | — | the default build (`sender=none`); `track` still runs the gate and the queue in memory so tests are real; nothing uploads |
| `Aptabase` | `https://<region>.aptabase.com/api/v0/event` (or self-hosted base URL) | header `App-Key: A-EU-…` / `A-SH-…` | `{timestamp, sessionId, eventName, systemProps{isDebug, osName:"Android", osVersion, locale, appVersion, appBuildNumber, sdkVersion:"oc-native"}, props}` per the Kotlin SDK source [V `aptabase-kotlin` `Aptabase.kt`] | one POST per event; a batch endpoint `/api/v0/events` [U] used if it exists |
| `PostHog` | `https://eu.i.posthog.com/batch/` (or self-hosted) | `api_key` in the body (project token, public by design) | `{api_key, batch:[{event, distinct_id:<session>, timestamp, properties:{…, "$process_person_profile": false}}]}` [V posthog.com/docs/api/capture] | configure the project to discard client IPs |
| `Plausible` | `https://plausible.io/api/event` (or self-hosted) | none; `User-Agent: OpenCodeMobile` | `{name, url:"app://opencode-mobile/<event>", domain, props}` [U exact fields, check at build] | designed for web; a fallback |
| `Custom` | a self-hosted HTTPS URL | optional bearer from build config | `{"events":[WireEvent…]}` (our own JSON) | for a future own collector |
| Firebase / GA4 | **not offered** | — | — | needs the Google SDK or an SDK-issued instance id; violates S-23 and 12 X-01 |

Build configuration (committed, so F-Droid reproducible builds produce the same APK): `native/androidApp/analytics.properties`

```properties
# D16-1 picks these. Ingestion keys are public by design (they can only write events).
sender=none
endpoint=
key=
```

`:androidApp` maps it to `AppBuildInfo.analytics` and binds the matching `AnalyticsSender` with Metro. The `qa` build
type overrides `sender=custom`, `endpoint=http://10.0.2.2:<fixture>/analytics` so EG scenarios see every payload.

### 5.6 Rules of the pipeline

1. Nothing is tracked before the migration gate opens; `Analytics` is created lazily after the first frame (S-7 keeps
   `onCreate` ≤ 50 ms).
2. `isDebug = true` for debuggable builds and the `qa` build type; the release check `QaHooksAbsentTest` guarantees a
   stable release reports `false`.
3. Turning DETAILED off removes queued DETAILED events at once and sends nothing more of that tier.
4. Upload only with a validated default network; never while Data Saver restricts background data
   (`ConnectivityManager.restrictBackgroundStatus == ENABLED`) and the app is in the background.
5. HTTPS only, the endpoint host must be a `KnownHosts` Fetch host (K-SEC-10), no cookies, `User-Agent: OpenCodeMobile`,
   no redirects (hardened client).
6. Failures never surface to the person; they back off (1 min, doubling to 6 h) and are recorded as a diagnostics entry
   by category.
7. Queue file `files/native/analytics/queue.json`: app-wide (no profile data, so not in `ProfileScope`); written through
   one `WriterLane`; deleted by Manage space › Delete everything.

### 5.7 When anything is sent

Only when all are true: `sender != none` (D16-1 answered), the build is not `qa`, BASIC (always) or DETAILED (switch on)
permits the event. With `sender=none` the switch release sends nothing (CR16-1).

### 5.8 What the person sees (kit only; rows on the existing Privacy page I08, no new level)

| Row (Settings › This app › Privacy) | Kind | Words (en; ar from the l10n lane) |
|---|---|---|
| App health reports | row → kit sheet with the BASIC table in plain words | "Always on. Sends the app version, Android version and whether updates and connections worked. Never your chats, files, server addresses or passwords." |
| Share detailed usage | switch, **off** by default | "Also send which screens and features you use, and how fast the app starts. Helps decide what to improve." |
| What was sent | row → page with the last 50 payloads in a kit code block (LTR), Copy | "Exactly what left this phone, newest first." |

When `sender=none` the first row reads "Not sent in this version" and the switch is hidden (nothing to opt into).
First-run disclosure: the 3.0 "What's new" line in the status-line slot (the update notice's slot, used once) says "This
version can send basic app health reports. See Privacy." — shown only once a sender exists (D16-2).

### 5.9 F-Droid caveat

An always-on BASIC tier earns F-Droid's **Tracking** anti-feature. With reproducible builds the F-Droid APK is byte-
identical to ours, so a "BASIC off on F-Droid" build cannot exist without giving up the shared signature. D16-2 records
the owner's choice (default: accept the label, re-ask at submission).

### 5.10 Tests

`AnalyticsSchemaTest` (names unique, snake_case, ≤ 40 chars, every event has a PRIVACY.md row), K-SEC-11,
`AnalyticsPrivacyTest` (B-SEC-191: feed every emitter canary-bearing inputs — paths, titles, error text, provider keys —
and assert no queued `WireEvent` contains any canary; DETAILED never queued while off; switching off drops queued
DETAILED; the session id is not in any file), `AnalyticsSenderContractTest` per sender with `ktor-client-mock` (exact
body shape, headers, no cookies, HTTPS only), `AnalyticsUploadPolicyTest` (backoff, Data Saver, batch size), and EG-1/EG-2
in the egress harness.

---

## 6. Signing

### 6.1 The three lineages (verified, do not trust docs over `apksigner`)

| Lineage | Certificate SHA-256 | Signs | Key lives | Used by |
|---|---|---|---|---|
| **Public** | `842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C` | every GitHub release (v2.0.0+56, v2.1.0+57, and the native switch release) [V `android-release.yml:42`, `scripts/release.sh:11`, release notes] | GitHub secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; owner's offline backup | the release job only, on an explicit owner request |
| **Owner / local** | `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C` (DN `CN=Android Debug`, RSA 2048; build 4511 is v2+v3, no v1 [V `apksigner` today]) | the owner's phone, emulator builds, the `.native` preview flavour, hosted test APKs | `android/key.properties` → keystore outside the repo, mode 0600, on the PC | the PC re-sign step (§6.2) |
| **CI test** | `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC` | quality-gate APK artifacts, device smoke | GitHub secrets `CI_KEYSTORE_BASE64`, `CI_KEYSTORE_PASSWORD` | CI only; never delivered to a person |

ADR D2: both installed populations stay on their lineage; never rotate, substitute or add a signer. No APK Signature
Scheme v3 rotation lineage and no v3.2 hybrid PQC signing (research android-surfaces §14).

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
| `qa` | PC emulator | local (it must install over Flutter for the upgrade journey) | never delivered: versionName `-qa`, refused by A16-14 and by `deliver.sh` |

Before any APK reaches the owner's phone: ask for (or read with read-only adb `dumpsys package` signatures) the
installed signer and compare with `apksigner verify --print-certs` (AGENTS.md workflow rule, kept).

### 6.3 Key custody (owner actions; D16-9)

1. Keep **two offline copies** of each keystore (public and local) and their passwords in the owner's password manager.
   Losing the public key means users can never update in place again, and developer verification needs it [V
   android-surfaces §14: "If you lose your signing key you won't be able to register"].
2. The release job uses a GitHub **environment** `release` with the owner as required reviewer, so a leaked workflow
   change cannot sign without approval.
3. Never print a keystore path, password or alias in logs (scripts pass `env:` references; CI masks secrets).
4. `RepositoryHygieneTest` fails on any `*.jks`, `*.keystore`, `*.p12`, `key.properties` in git (B-SEC-150).

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

Account type: full distribution ($25, ID verification) — the limited account caps at 20 devices (D16-7).

---

## 7. Release pipeline without Shorebird

### 7.1 What changes and what stays

| Stays (packages-build map §0 item 10) | Changes |
|---|---|
| tag `v<x.y.z>+<N>`; notes `docs/releases/v<version>.md` starting `# OpenCode Mobile <version>`; `CHANGELOG.md` top entry; fastlane changelog `<versionCode>.txt`; assets `opencode-mobile-<version>.apk` (universal) and `opencode-mobile-<version>-arm64-v8a.apk`; `SHA256SUMS`; draft → verify → publish; tag commit == `origin/master` HEAD; owner's explicit request for tag, CI run, signing and publish | Shorebird setup/release steps → Gradle `assembleStableRelease`; `--flutter-version` checks → JDK/AGP/Kotlin provenance; the `-armeabi-v7a.apk` asset is gone (ADR §9.9 ABI set) and `-x86_64.apk` is added; size gate numbers (S-14); output path `native/androidApp/build/outputs/apk/stable/release/`; "patch" and "patch-plan" modes are removed from the native release script |

### 7.2 Hotfixes

There is no code push for Kotlin (02 NG17). A fix is a full release: bump `+N` (and the patch version), same pipeline.
Target turnaround after the owner's yes: tag → draft with verified assets in ≤ 30 min of CI, publish on the owner's
second yes, every installed copy sees "Version x is ready" within 6 hours (the check interval) or at next app start.
The Flutter bridge release keeps Shorebird for **security-only** patches with the owner's OK until its users have moved
(02 §5.7).

### 7.3 `native-release.yml` (GitHub Actions; runs only on an explicit owner request)

Triggers: `workflow_dispatch` with input `tag` (a tag push on a `[skip ci]` commit starts nothing, memory `release-2.1`).
`concurrency: native-release-${{ inputs.tag }}`, no cancel; `permissions: contents: write`; `environment: release`.

Step names (the verifier reads them; rename both together):

1. **Require release secrets** — the four `RELEASE_*` secrets exist (fail closed).
2. **Checkout tag** — `fetch-depth: 0`.
3. **Resolve version** — from `native/version.properties`; `versionName` matches `^\d+\.\d+\.\d+$`, `versionCode` ≥ 10000.
4. **Verify tag and notes** — tag == `v<name>+<code>`; notes file exists with the header; `CHANGELOG.md` agrees
   (port of B-BUILD-028).
5. **Require tag commit equals master** — and any existing GitHub release for the tag is a draft.
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
    checks ported, PACKAGES-BUILD-06), `QaHooksAbsentTest` script form.
14. **Check 16 KB alignment** — `zipalign -c -P 16 -v 4` on every APK and an ELF `p_align` check on every `.so`.
15. **Check APK size** — `check_apk_size.py` against the previous published release and S-14 caps.
16. **Check component freeze and exported surface** — the `:quality` freeze list against the built APK's manifest.
17. **Write SHA256SUMS** — `dist/opencode-mobile-<version>.apk`, `-arm64-v8a.apk`, `-x86_64.apk`.
18. **Upload mapping** — R8 `mapping.txt` as a 400-day workflow artifact (never a release asset).
19. **Create draft GitHub release** — notes + a "Verify this build" block: commit, "Gradle build: AGP 9.3.3, Kotlin
    2.4.21, JDK 17 (Temurin <exact>)", package id, signer `842284B2…`, `SHA256SUMS`, and "Rebuild it yourself:
    `native/tool/release/repro_check.sh v<version>`".

Then, locally (`scripts/release.sh github`, ported): download the CI artifacts and the draft assets, re-verify identity
and checksums, and on `--publish` (owner's explicit yes) make the release non-draft, non-prerelease and `--latest`, and
read back `releases/latest`.

### 7.4 `scripts/release.sh` (native mode)

Keeps: fail-closed checks, `master` with a same-named upstream, clean tree, synced, nothing uploaded without
`--publish`, publish from a throwaway clone (memory `release-1.4.0`). Replaces: the Flutter version assertion with
`native/.java-version` + `libs.versions.toml` AGP/Kotlin pins; `sideload` mode builds with `-Poc.expectedSigner`.
Removes: `patch`, `patch-plan`. Runs before tagging: the release-notes check, T2 on the VPS (record the run), the
reproducibility check in report mode (§9.3).

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
| `UpdateNotice` (state), `ReleaseSource`, `UpdateVerifier`, `UpdatePolicy` | `:engine:diagnostics` commonMain | 03 §11 already lists `UpdateNotice` and `UpdateInstaller` here |
| `GitHubReleaseSource` | `:engine:diagnostics` commonMain over `:core:net` | `GET https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest`, `Accept: application/vnd.github+json`, 10 s timeout, no auth |
| `ResumableDownloader` | `:core:net` | with the redirect loop of CR16-5 |
| `PackageInstallerUpdater` (implements `UpdateInstaller`), `AndroidApkInspector`, `AndroidInstallSource` | `:android:platform` | port of the freeze-tip `AppUpdateChannel.kt` minus the channel |
| `.AppUpdateStatusReceiver` | `:androidApp` (frozen FQCN, not exported) | 11 §3 |

### 8.2 When the updater may check (N16-D8)

```kotlin
public enum class UpdateFeedState { Active, OffBySwitch, DeveloperBuild, InstalledByStore, NotThisPackage }

public object UpdatePolicy {
    public const val PUBLIC_SIGNER: String = "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
    public val STORE_INSTALLERS: Set<String> = setOf(
        "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid",
    )
    public fun feedState(
        packageName: String, ownSigner: String, firstInstaller: String?, checkSwitchOn: Boolean,
    ): UpdateFeedState = when {
        packageName != "io.github.eslamasabry.opencode_mobile" -> UpdateFeedState.NotThisPackage   // .native, .bench
        ownSigner != PUBLIC_SIGNER -> UpdateFeedState.DeveloperBuild                               // owner phone, emulator
        firstInstaller in STORE_INSTALLERS -> UpdateFeedState.InstalledByStore
        !checkSwitchOn -> UpdateFeedState.OffBySwitch
        else -> UpdateFeedState.Active
    }
}
```

- `firstInstaller` is read once (API 30+ `getInstallSourceInfo(pkg).initiatingPackageName`; API 26–29
  `getInstallerPackageName`) at the first native launch and stored app-wide as `oc.appUpdate.firstInstaller` (after a
  self-update the installer becomes our own package, so it must be the *first* value). Obtainium
  (`dev.imranr.obtainium`) is not a store installer: its users keep the in-app check.
- Words on the Updates page: DeveloperBuild "This copy was built by its developer. Updates come from them, not from
  GitHub."; InstalledByStore "Updates come from F-Droid."; both keep "Check now" hidden (B-SEC-190).
- Check frequency: app start and resume, at most every **6 hours**, never while hidden (owner design).

### 8.3 The verification pipeline (all must pass; any failure → "This download isn't signed like your app, so it wasn't installed." or the matching plain sentence, details under Details)

| Step | Check | Failure (`UpdateProblem`) |
|---|---|---|
| 1 | release JSON from `api.github.com`: tag parses `^v(\d+)\.(\d+)\.(\d+)\+([1-9]\d*)$`, not draft, not prerelease; build number > installed versionCode (B-BUILD-010/011) | `NotNewer` (silent) |
| 2 | pick asset: `-arm64-v8a.apk` if `arm64-v8a` ∈ `SUPPORTED_ABIS`, else `-x86_64.apk` if `x86_64` ∈; if that split is missing from the release, the universal `opencode-mobile-<v>.apk` (owner design); if the phone has neither ABI, nothing (`NeedsSupportedPhone`, "This version needs a 64-bit phone."). The `SHA256SUMS` asset must exist | `NoAsset`, `NeedsSupportedPhone` |
| 3 | every asset URL is `https://github.com/Eslamasabry/opencode-mobile-next/releases/download/…`; redirects only to `release-assets.githubusercontent.com` (KnownHosts redirect set) | `UnexpectedSource` |
| 4 | free space ≥ 2 × asset size in `cacheDir`; download to `cacheDir/updates/<tag>/<file>.part` with Range resume; rename on completion | `NoSpace`, `DownloadFailed` |
| 5 | SHA-256 of the file equals its `SHA256SUMS` line (exact file name, 64 lowercase hex) | `ChecksumMismatch` |
| 6 | `PackageManager.getPackageArchiveInfo(file, GET_SIGNING_CERTIFICATES)`: package == ours; `longVersionCode` > ours; `minSdkVersion` ≤ device SDK | `WrongApp`, `NotNewer`, `NeedsNewerAndroid` |
| 7 | the archive's current signer set (`signingInfo.signingCertificateHistory.last()`, or `apkContentsSigners` if several) equals our installed signer set, compared as sorted upper-case SHA-256 strings (the freeze-tip `AppUpdatePolicy.signerString` format) | `SignerMismatch` |
| 8 | `PackageInstaller` session: `MODE_FULL_INSTALL`, `setAppPackageName(ours)`, API 31+ `setRequireUserAction(USER_ACTION_REQUIRED)`, API 33+ `setPackageSource(PACKAGE_SOURCE_DOWNLOADED_FILE)`; stream **the same verified file** into the session; commit with the immutable-except-allowlisted status PendingIntent | `InstallFailed` (Android's text under Details) |
| 9 | `STATUS_PENDING_USER_ACTION`: start the confirm intent from the visible `MainActivity` (BAL rules) | — |
| 10 | on `aborted`/`failure`: return to "ready", keep the verified file for 24 h, then delete; emit BASIC `update_result` | — |

`UpdateVerifierTest` (commonTest with fakes for the archive inspector) covers every row; P16-4 proves it on the emulator
(local feed, a correctly signed APK installs; a CI-test-signed APK is refused at step 7; a tampered byte fails step 5).

### 8.4 The downloader's redirect loop (CR16-5; shared with voice packs and the rootfs)

```kotlin
// :core:net commonMain
public class ResumableDownloader(private val http: HttpClientFactory, private val clock: Clock) {
    /** HTTPS only. Follows at most 5 redirects, each to an https URL whose host is in [allowedRedirectHosts]
     *  (exact or "*.suffix"). Sends no credentials, cookies or Referer. Resume always re-requests [url]. */
    public suspend fun download(
        url: String, expectedSha256: String, target: kotlinx.io.files.Path,
        allowedRedirectHosts: Set<String>, progress: (Long, Long?) -> Unit,
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
| F10 | Always-on BASIC analytics | §5 | anti-feature `Tracking` (D16-2) | owner |
| F11 | In-app updater | §8 | install-source rule turns it off for F-Droid installs; [U] F-Droid inclusion policy on self-updaters, check at submission | release lane |
| F12 | Reproducible build with our signature | §9.2 | `Binaries:` + `AllowedAPKSigningKeys: 842284b27aa297fb74cf831779fd16498517e1bc2104451459fec2ea7ac11d1c` in the fdroiddata metadata [V f-droid.org Reproducible_Builds] | release lane |
| F13 | IzzyOnDroid (separate repo) | blocked by its AI policy [R] | none | — |

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
| WP-16.1 | `Secret`, `Canaries`, `KnownHosts`, `ExternalLinkPolicy` vectors port | `:core:common`, `:core:testing`, `:core:security` | 0–1 | Codex (core owner) | 03 §10 freeze | `ExternalLinkPolicyTest`, `SecretTest`, K-SEC-4 green |
| WP-16.2 | K-SEC-1…12, lint table, `ExportedSurfaceTest`, `NetworkSecurityConfigTest`, `FileProviderPathsTest`, `TlsFloorTest`, `QaHooksAbsentTest`, `RepositoryHygieneTest`, `CanaryLeakTest` | `:quality`, build-logic, `androidApp/lint.xml` | 0–1 | coordinator (Sonnet) | ADR P9 | each rule fails on a planted violation (P9 style) |
| WP-16.3 | Root `ExternalLinkUriHandler`, `AndroidLinkOpener`, `KitCopy(sensitive)`, `KitField(Secret)` state rule | `:ui:kit`, `:ui:shell`, `:android:platform` | 1–2 | Claude (kit owner) | WP-16.1 | `UriHandlerRootTest`, `SecretFieldStateTest`, `KitCopySensitiveTest` |
| WP-16.4 | Canary + egress harness (§2.5), first run on slice-2 build | `native/tool/qa/egress/`, `config/egress-allowlist.toml` | 2 | Sonnet (test-writer lane) | fixture servers (14) | P16-3; EG-1/EG-2 green |
| WP-16.5 | `ProfileDeletionSweepTest`, `ProviderKeyCanaryTest`, `BasicAuthTransportTest` | `:engine:profiles`, `:protocol:oc2`, `:core:net` tests | 1–3 | test-writer lane | 05 §3, 04 §4 | green |
| WP-16.6 | `AppUpdater` (§8) + Updates page rows | `:engine:diagnostics`, `:core:net` downloader, `:android:platform`, `:feature:about` | 4 | Codex (engine + platform), Claude (UI) | WP-16.1, 07 CR16-10 | `UpdateVerifierTest`; P16-4; EG-6 |
| WP-16.7 | Analytics core (§5): API + `None` sender + queue in slice 1; senders, Privacy rows, sent log in slice 4; enabled only after D16-1 | `:engine:diagnostics`, `:feature:settings` | 1, 4 | Codex (core), Claude (rows) | CR16-1 | §5.10 tests |
| WP-16.8 | Notification auth (CR16-7) | `:android:background` | 4 | Sonnet | 11 §10.4, D16-3 | `NotificationActionAuthTest` |
| WP-16.9 | Linux rules L3, L6 (confined binds + row), `ListenerAuthTest` | `:android:linux`, `:engine:runtime`, `:feature:phone` | 3 | Opus (runtime owner) | D16-4 | tests + emulator check that an agent cannot list `/sdcard/DCIM` unopened |
| WP-16.10 | Wi-Fi share FD + expiry (N16-D11) | `:android:media`, `:engine:media` | 5 | Sonnet (media owner) | 10 §13 | `WifiShareFdTest`, `WifiShareExpiryTest` |
| WP-16.11 | WebView hardening (§1.8.5) | `:ui:kit-content` androidMain, `:androidApp` manifest | 5 | Claude (kit-content) | — | K-SEC-9; lint |
| WP-16.12 | Release pipeline: signing convention, `sign_local.sh`, `native-release.yml`, `scripts/release.sh` native mode, verifier step names | build-logic, `native/tool/release/`, `.github/workflows/native-release.yml`, `scripts/` | 4–6 | Codex + coordinator | owner OK for any CI run | dry run on a throwaway tag in a fork-free way: `workflow_dispatch` with `dry_run=true` (no upload) |
| WP-16.13 | Reproducibility (§9.2, §9.3) | build-logic, `native/tool/release/repro_check.sh` | 4–6 | Codex | WP-16.12 | P16-5 report |
| WP-16.14 | Notices, About, GPL sources release, SECURITY.md, PRIVACY.md, threat model, trust inventory | docs, `:feature:about`, `:ui:words` files | 5–6 | Sonnet (docs), owner approval to publish the sources release | 12 W-18 | `NoticesCurrentTest`, `KnownHostsDocTest`, `RepositoryHygieneTest` |
| WP-16.15 | Bridge-release ABI pre-check | frozen Flutter repo (`lib/update/`) | before the switch | Sonnet | D4 | Flutter test; P16-6 bridge leg |
| WP-16.16 | targetSdk-37 security slice (§11) | several | switch+1 | Sonnet + Opus | — | P16-2 on API 37; LAN permission flow |

Every WP brief carries the read/write sets above, "format only changed files", "reviewers never launch tests", and the
row ids it turns green.

---

## 13. Proofs (each at most about a day; each has a fallback)

| # | Proof | Acceptance | Fallback |
|---|---|---|---|
| P16-1 | TLS floor and platform trust: on the API 37 emulator, an unpinned `HardenedOkHttp` client's trust manager is the platform's (Conscrypt/NSC root), and the resolved OkHttp is 5.5.1 | assertion passes; `TlsFloorTest` green | — |
| P16-2 | CT opt-in on API 36 and 37 emulators: connect to `api.github.com`, `huggingface.co`, a Let's Encrypt host, a `*.ts.net` HTTPS host, a pinned self-signed fixture (must still work through our trust manager), loopback HTTP | every connection succeeds; a deliberately non-logged test certificate (fixture CA added for the test build only) is refused when CT is on | ship without the opt-in (API 36 default) |
| P16-3 | Egress harness: AVD choice (`default` image for API 36 exists?), baseline subtraction works, a planted `qa` request to `example.org` fails EG-1 | planted host caught; EG-1 green otherwise | `google_apis` image + longer baseline |
| P16-4 | Updater end to end on the emulator with the `qa` feed override: correct local-key APK installs over itself; CI-test-signed APK refused (step 7); one flipped byte refused (step 5); 32-bit-only emulator image shows "needs a 64-bit phone" | all four | — |
| P16-5 | Reproducibility: two builds of one commit, different paths and worker counts, are byte-identical unsigned; `apksigcopier compare` passes against the signed one; record which of `baseline.prof`, Licensee report, CMP resources, R8 output differ if any | identical, or a named list of differing entries with fixes | report-only until fixed; F-Droid waits |
| P16-6 | Trust inventory facts: Node 24's `--use-system-ca` availability and effect in the guest; which root list the OpenCode 2 binary uses; bridge-release ABI pre-check on a 32-bit image | facts recorded in the trust inventory | mark TI rows "unknown, check per incident" |

---

## 14. New catalog rows (B-SEC-184 … B-SEC-199 reserved for this section)

The catalog owner assigns final numbers if another section claimed one first (02 §6.1). All `scope = switch` unless
marked.

| Id | Behaviour (Given/When/Then in short) | Level | Test |
|---|---|---|---|
| B-SEC-184 | Given a question or legacy reply action on the lock screen, when tapped, then Android asks to unlock first; Reject does not ask | unit | `NotificationActionAuthTest` |
| B-SEC-185 | Given a `LinkAnnotation.Url` in any composable (including sheets and dialogs), when tapped, then the link policy decides and nothing opens directly | screenshot/unit | `UriHandlerRootTest` |
| B-SEC-186 | Given any object holding a credential, when printed or logged, then the value appears as `•••` | unit | `SecretTest` |
| B-SEC-187 | Given a secret typed into a field, when the activity is recreated, then the field is empty and no saved state holds the value | integration | `SecretFieldStateTest` |
| B-SEC-188 | Given a secret, pairing link, share link or one-time code, when copied, then the clip is marked sensitive (Android 13+) | unit | `KitCopySensitiveTest` |
| B-SEC-189 | Given an update download, when its package, versionCode, signer or checksum is wrong, or its source host is unexpected, then nothing installs and the person sees the plain reason | unit | `UpdateVerifierTest` |
| B-SEC-190 | Given a developer-signed copy, an F-Droid install or another package, when the app starts, then it does not check GitHub and the Updates page says where updates come from | unit | `UpdatePolicyTest` |
| B-SEC-191 | Given analytics, when any event is queued, then it carries only schema enums and buckets; DETAILED is never queued while off and is dropped when turned off; no session id is stored | unit | `AnalyticsPrivacyTest` |
| B-SEC-192 | Given a Wi-Fi share, when the file's path is swapped after start or the clock passes `endsAt` while the app was frozen, then the original file is served or the request is refused with 410 and sharing stops | integration | `WifiShareFdTest`, `WifiShareExpiryTest` |
| B-SEC-193 | Given the release build, then unpinned TLS uses the platform trust store, OkHttp ≥ 5.5.1, no user CAs, and the NSC has exactly the reviewed elements | contract | `TlsFloorTest`, `NetworkSecurityConfigTest` |
| B-SEC-194 | Given the merged manifest, then the exported surface equals the reviewed list; `WidgetConfigureActivity` refuses a widget id that is not ours | contract | `ExportedSurfaceTest`, `WidgetConfigureGuardTest` |
| B-SEC-195 | Given the FileProvider, then only shared storage (parity) and `cache/share/` are path roots, and an app-private file is shared only as a checked copy | contract | `FileProviderPathsTest`, `ShareCopyTest` |
| B-SEC-196 | Given the dev preview, then file/content access is off, no JS bridge exists, SSL errors cancel, other origins go to the link policy, WebView metrics and Safe Browsing are off | integration | `KitWebPreviewTest` (Robolectric) + K-SEC-9 |
| B-SEC-197 | Given each egress scenario, then every destination is in that scenario's allowlist | e2e | egress harness EG-1…EG-7 |
| B-SEC-198 | Given built-in Linux with All files access, when an agent lists shared storage, then it sees only folders opened as projects unless "Let Linux see all phone files" is on (D16-4) | integration | `SharedStorageBindsTest` + emulator check |
| B-SEC-199 | Given any service the app starts in its Linux with a port, then it listens on `127.0.0.1` and requires authentication | unit | `ListenerAuthTest` |

---

## 15. Acceptance checklist owned by this section (switch gate)

| # | Check | Tier | Blocks the switch? |
|---|---|---|---|
| A16-1 | All SI rows hold: their named tests green, K-SEC-1…12 clean | T1/T2 | yes |
| A16-2 | S-22 canary scan: 0 hits across EG-1…EG-7 and the upgrade journey (ADR §10.2) | T3 | yes |
| A16-3 | S-22 egress: EG-1…EG-7 green on the release candidate | T3 | yes |
| A16-4 | S-23 / 12 X-01…X-31: `BannedDependenciesTest`, `PrereleaseGuardTest` (fail mode) green | T2 | yes |
| A16-5 | `TlsFloorTest`, `NetworkSecurityConfigTest`, P16-1 recorded; P16-2 result decides N16-D4 | T2/T3 | yes |
| A16-6 | Updater P16-4 green; the switch candidate, signed with the public key, verifies against a published-shape draft (`dry_run`) | T3 | yes |
| A16-7 | Signing: `apksigner` shows the right single signer for the owner candidate (local) and the release candidate (public); v2+v3, no v1 | T2 | yes |
| A16-8 | Component freeze + exported surface green against the release APK | T2 | yes |
| A16-9 | `NoticesCurrentTest` green; the GPL sources release exists (D16-8) and the notices link it | T2 | yes |
| A16-10 | PRIVACY.md rewritten (en + ar), `KnownHostsDocTest` green; SECURITY.md and threat model updated | review | yes (02 switch-ready 5) |
| A16-11 | Analytics: `sender=none` unless D16-1 answered; if answered, EG-1 shows only that host and §5.8 rows render | T3 | yes |
| A16-12 | Bridge release carries the ABI pre-check (WP-16.15) | Flutter tests | yes, if D4 ships |
| A16-13 | Reproducibility report P16-5 exists (report-only; not blocking) | VPS | no |
| A16-14 | No `-qa` APK and no QA hook in any delivered or released APK (`QaHooksAbsentTest`, `deliver.sh`) | T2 | yes |
| A16-15 | Developer verification registration done or scheduled before enforcement reaches the owner's region (D16-7) | owner | no (date-driven) |

---

## 16. Owner decisions

Each has a default; work proceeds on it.

| # | Decision | Options | Recommended default |
|---|---|---|---|
| **D16-1** | **Which analytics service receives BASIC (and opted-in DETAILED) events?** | (a) Aptabase EU cloud (`A-EU-` key; privacy-first, made for apps, no identifiers); (b) self-hosted Aptabase on a small separate server (AGPL server, data stays with you; another machine to run); (c) PostHog EU cloud (more analysis, more to configure: discard IPs, no person profiles); (d) none for now | **(a) Aptabase EU cloud.** Until you answer, the build ships `sender=none` and sends nothing. |
| D16-2 | BASIC stays always on everywhere, accepting F-Droid's "Tracking" label; one-time "basic app health reports" line in 3.0's What's new | accept / make BASIC opt-in too | Accept (your words: "basics we need regardless"); re-ask at F-Droid submission |
| D16-3 | Lock-screen question answers and the old quick Reply need the phone unlocked (Reject still does not) | yes / keep today's | Yes |
| D16-4 | The phone's Linux sees only folders you opened as projects; a visible row "Let Linux see all phone files" turns the old behaviour back on | yes / keep today's (whole shared storage) | Yes |
| D16-5 | F-Droid route after the switch | (a) main F-Droid repo with reproducible builds and our signature; (b) our own F-Droid repo first (a new repo key to guard); (c) not now | (a), after the F4–F7 source builds |
| D16-6 | Supported versions in SECURITY.md | latest native only / + bridge security patches for 3 months | + bridge security patches for 3 months |
| D16-7 | Developer verification account | full ($25, ID) / limited (≤ 20 devices, free) | Full, before global enforcement; register the packages and signers in §6.5 |
| D16-8 | GPL source offer for proot/talloc as an immutable `third-party-sources-1` GitHub release | yes / host elsewhere | Yes |
| D16-9 | Key custody: confirm two offline backups of each keystore and passwords exist; release job behind a GitHub `release` environment with you as reviewer | yes | Yes (action for you) |
| D16-10 | 32-bit-only phones stay on the Flutter 2.x line (the native ABI set is 64-bit, ADR §9.9) | accept / add `armeabi-v7a` back (no on-phone Linux there anyway) | Accept; the bridge updater and notes say so |

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
`1de5bf08…d60c`, DN `CN=Android Debug`, RSA 2048. `curl -I` on
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
- [U] GHSA-frr6-h895-5pxp page itself (GitHub advisories returned 404 to an unauthenticated fetch; the id comes from our
  commit message `6e0fc0482`); Plausible event API field list; Aptabase batch endpoint; Node `--use-system-ca` version;
  OpenCode 2 binary's root store; F-Droid policy on self-updaters and on Maven-Central native AARs.

Maps and research: `../map/packages-build.md` (§0 items 3–7, 10, 12; PACKAGES-BUILD-03, 05, 08, 10, 23, 29–36, 40),
`../map/builtin-linux.md` (§0, BUILTIN-LINUX-05, 15, 16, ports), `../map/media-voice.md` (Wi-Fi share risks 12, 21),
`../map/desktop-web-cross.md` (DESKTOP-WEB-CROSS-46), `../research/android-surfaces.md` (§2.2, §13, §14, §15),
`../research/network-data.md` (Keystore survival), `../research/media.md` (zxing-cpp, F-Droid), `../catalog/BEHAVIORS.md`
(B-SEC-001…183, B-BUILD-007…034, B-MIGR-004…013).

Owner memory: `freeze-owner-decisions-2026-11-01` (QR, F-Droid after the rewrite, analytics reversal),
`dev-freeze-native-rewrite-2026-11-01` (TLS fix allowed during the freeze), `app-updates-two-layers`,
`roadmap-issues-2026-10-31` (#37 app lock closed, #42 Play closed), `shorebird-is-mandatory`, `release-1.4.0-2026-10-15`,
`release-2.1-prep`, `apk-build-numbers`, `no-raw-errors-rule`, `actions-name-their-target`, `obvious-options-first`,
`no-new-ui-levels`, `audit-trail-rule`, `public-commit-email`.
