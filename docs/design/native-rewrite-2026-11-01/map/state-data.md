# Area map: state-data (lib/state, ConnectionController, profiles, secrets, caches, stores)

Native-rewrite map. READ-ONLY survey of `/home/eslam/Storage/Code/wt/oc_app-batch1` (HEAD 65e3f70a3, 2026-11-01, app 2.1.0+57).
Scope: `lib/state/**` (68k lines, 216 files incl. `connection/*.dart` 54 part files, `profiles/*`, `factsbook/*`, `orchestration/*`),
plus every store those classes own outside `lib/state` that the day-1 migration must carry (listed in section 4).
IDs: `STATE-DATA-NN`. Test evidence: the 159 `state-data` rows in `/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl` (section 11).

Reading order for a rewrite agent: section 1 (facts that decide the architecture), section 2 (physical stores and exact encodings),
section 3 (migration recipe), section 4 (every key/file), section 5 (deletion sweep), section 6 (feature catalog, 55 ids), 7 (lifecycles + timers), 8 (hard-won behaviors), 9 (performance), 10 (risks), 11 (tests), 12 (packages), 13 (native bridges), 14-15 (suggested shape, open decisions), 16 (evidence/method).

### Feature index

A profiles/secrets/trust: 01 profiles CRUD, 02 credentials + re-enter, 03 cleartext consent + cert pin + URL rules, 04 backends + OC1->OC2 flags, 05 phone-agent owner aliasing, 06 saved sign-in reset, 07 AI Team config in profile, 08 profile deletion sweep, 09 bootstrap/startup order.
B connection controller: 10 connect/transport pairs, 11 app lifecycle + quiet reconnect, 12 status model, 13 project location + recents, 14 session list/busy/pins/polling, 15 model/agent/variant selection, 16 permissions + auto-approval, 17 questions/forms/inbox, 18 offline queue + flush, 19 online-send marker, 20 command receipts, 21 drafts + attachment vault, 22 prompt photo recovery, 23 saved prompts (stash), 24 read state + return brief, 25 first-paint caches, 26 small conversation memories, 27 staged revert, 28 turn stall/stop semantics.
C notifications/surfaces: 29 notification rules, 30 profile monitor + attention feed, 31 coding alerts + status notification, 32 launcher surfaces (widget/shortcuts/tile), 33 background live + pause notice, 34 automation policy + automatic activity, 35 consent stores, 36 teaching/nudges/first run/defaults/chips, 37 app preferences + settings facade.
D agents/connections: 38 agents on this phone, 39 side connections, 40 handoff + session links.
E gen UI/integrations: 41 generated UI state, 42 MCP/connectors/integration auth, 43 worktrees/isolated tasks.
F team/usage/setup/services: 44 AI Team stores, 45 usage + quota, 46 setup engine state, 47 development services, 48 external A2A agents, 49 GitHub account, 50 BYO host, 51 FactsBook pairing.
G phone runtime: 52 built-in server owner/recovery/stop/thermal/reset markers, 53 scheduled prompts (Kotlin store), 54 diagnostics/reports, 55 voice settings.

---------------------------------------------------------------------------------------------------

## 1. What a rewrite must know first (ten facts)

1. **There is no database.** All app state is `SharedPreferences` (legacy API -> file `shared_prefs/FlutterSharedPreferences.xml`, every key
   prefixed `flutter.`), Keystore-backed `flutter_secure_storage` 11.2.0 (default options), a handful of private files under
   `filesDir`, and Kotlin-owned prefs/files. ~210 distinct `oc.*` key shapes plus 4 `voice.*` keys. Almost every value is a JSON string.
2. **Profile-scoped data is addressed by key shape, not by a list.** The convention `oc.<what>.<profileId>` (or `oc.<what>.<profileId>.<more>`)
   IS the deletion mechanism: `ProfileStore.profileScopedPreferenceKeys(id)` sweeps every `oc.*` key that `endsWith('.<id>')` or contains
   `'.<id>.'`. A native rewrite that moves to Room/DataStore/tables must keep an equivalent "delete everything owned by profile X" guarantee
   and must not break the sweep for keys that stay in prefs. (Security invariant in AGENTS.md.)
3. **Writers are single-owner, serialized, and fail closed.** Nearly every store has: a per-profile writer lane (promise chain), a
   `closeProfile()` that stops admission synchronously then drains in-flight writes, a `reload()` after any refused write (because the
   Android plugin updates its in-memory cache before the platform write is acknowledged), a bounded size, redaction of secrets before
   persistence (`KitRedact`), and "corrupt data => treat as unreadable, never overwrite with an empty list". These are the hard-won
   semantics; the data formats are the easy part.
4. **Deletion is a transaction run backwards**: every dependent blob is rewritten and verified first, and only then the profile row and
   Keystore secrets go; any refusal keeps the server and reports plain-language failures (section 5).
5. **Prompts are never double-sent.** The offline queue persists a *dispatch marker* before the request leaves the device; an entry with a
   marker never auto-sends again (not after restart either) and waits for the person's explicit "resend" (STATE-DATA-18). Command
   receipts (`oc.pendingCommands.<id>`) add idempotent lookup-only retry on servers that support them.
6. **Kotlin already reads Flutter's prefs directly.** Widgets, Quick Settings tile, pinned shortcuts, scheduled-prompt worker, background
   service, notification locale and the built-in Linux recovery read `FlutterSharedPreferences` with the `flutter.` prefix (section 4.6).
   Those keys are a cross-process contract; changing their home breaks the native half unless both change together.
7. **The Linux rootfs and engine files are already Kotlin-owned** (`filesDir/linux/ubuntu`, `filesDir/projects`, `oc.teamEngine.<id>/`,
   `oc.sharedProjects`, recovery prefs). The migration must leave them untouched; only Dart-owned state moves.
8. **flutter_secure_storage encryption is reproducible natively** (section 2.2): AES-128-GCM value encryption with an app key that is
   RSA-OAEP wrapped by an AndroidKeyStore key whose alias is derived from the package name. Same package + same signing key (the day-1
   requirement) keeps that Keystore entry reachable. Phones that jumped from the 9.x build straight to 11.x may hold unreadable legacy
   ciphertext (section 2.2 trap); the Flutter app already degrades to "re-enter password" instead of failing.
9. **OpenCode 1 is gone.** `ServerProfile.fromJson` forces `flavor = v2`; old OC1 profiles carry `requiresOpenCode2Migration` /
   `openCode2HistoryPending` (managed phone servers) and the app refuses to connect until history import is confirmed. The rewrite must
   preserve these two booleans through the first launch.
10. **Many "stores" exist only for deletion-safety or consent-safety** (`oc.teamEngineDeleted.<id>` tombstones, `oc.scheduleDeleted.<id>`,
    `oc.shareSessionViews*`, consent journals). They have no UI but encode owner decisions; dropping them silently changes behaviour
    (e.g. read-state sharing defaults to off after migration; automation behaviors missing from a stored policy are OFF).

---------------------------------------------------------------------------------------------------

## 2. Physical stores and exact encodings

### 2.1 SharedPreferences (legacy plugin, `shared_preferences` 2.5.5 / `shared_preferences_android` 2.4.27)

- File: `/data/data/io.github.eslamasabry.opencode_mobile/shared_prefs/FlutterSharedPreferences.xml`. Package `io.github.eslamasabry.opencode_mobile`
  (the `preview` flavor appends `.preview` -> a completely separate data dir; out of scope for the owner's phone).
- Every Dart key `K` is stored as `flutter.K`. Kotlin code therefore reads `flutter.oc.profiles`, etc.
- Type mapping (verified in `SharedPreferencesPlugin.kt` / `LegacySharedPreferencesPlugin.kt`):
  - `String` -> SharedPreferences string (guard: any string that starts with the list/double prefixes below is treated specially by the plugin).
  - `bool` -> boolean. `int` -> **long** (a Kotlin reader must use `getLong`; `getInt` throws `ClassCastException`). `double` -> string `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + doubleString`.
  - `List<String>` -> string. Current writer: `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!" + jsonEncode(list)`.
    **Older builds wrote** `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu" + base64(Java ObjectOutputStream of ArrayList<String>)`
    (the deprecated path). Both exist on a long-lived install; a migration reader must decode both (the plugin re-encodes legacy lists on read in
    `transformPref`, so only never-read lists remain legacy).
- Whole file is loaded into memory at process start by Android; the app keeps one `offlineQueue` blob of up to 60 MB (3 x 20 MB, section 4.2)
  in this file. That is a latent startup-latency and OOM risk, called out in the code itself (`OfflineQueueStore.maxTotalBytes` doc).
- `prefs.reload()` is called all over (widget runners, deletion, after refused writes) because separate Dart engines (widget action runner,
  scheduled-prompt entrypoint, widget overlay) and Kotlin share the file. A native rewrite with one process removes most of that, but the
  Kotlin workers (schedule worker, widgets) still run outside the UI process lifecycle and need an atomic compare-and-set (the schedule store
  already has one: channel `oc/scheduled_prompts` `readStore`/`casStore`).
- `oc.profiles` (the profile list) is parsed by ~12 other stores as an existence check (`_hasProfile`), so a profile that is not in that list cannot
  receive writes from `PendingCommandJournal`, `SessionLinkBindings`, `SetupAuditStore`, `MobileDownloadConsent`, `InteractionDefaultsStore`,
  `ConsentOwners`, etc. (fail-closed admission). Keep that property.

### 2.2 flutter_secure_storage 11.2.0 on Android (defaults; verified in the package source)

- Value file: `shared_prefs/FlutterSecureStorage.xml`. Entry name = `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg" + "_" + key`.
  Entry value = `Base64.DEFAULT( IV[12] || AES-GCM ciphertext+tag(128-bit) )`, plaintext UTF-8.
- Storage cipher = `StorageCipherImplementationGCM`, AES key 16 bytes. The key is stored RSA-wrapped, Base64, in
  `shared_prefs/FlutterSecureKeyStorage.xml` under entry `AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK`.
- Key cipher = `RSA/ECB/OAEPPadding` in AndroidKeyStore (provider `AndroidKeyStoreBCWorkaround`), params `OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,DEFAULT)`,
  alias `<packageName>.FlutterSecureStoragePluginKeyOAEP` (no suffix; no namespace configured). Unwrap with `Cipher.UNWRAP_MODE`, algorithm `"AES"`.
- Also present: `FlutterSecureStorageConfiguration` prefs (plugin bookkeeping). Not needed.
- **Version trap (verified from git):** `^9.2.2` (2026-08-23) -> `10.0.0` (08-27) -> `11.0.0` (09-02) -> `11.2.0` (09-27). v11 removed
  RSA PKCS1 / AES-CBC / EncryptedSharedPreferences readers ("upgrade to v10 first so data is migrated"). A phone that went straight from a 9.x
  build to a 11.x build has unreadable secrets; the Flutter app already survives this (`requiresPasswordReentry`). A native migration must do the same:
  never fail the whole migration on one undecryptable entry; mark the profile "needs password again".
- Secrets are not extractable by `adb`/backup (`allowBackup=false`, backup rules exclude everything). Do not add them to any export.
- `flutter_secure_storage.readAll()` is used by the reset flow (`resetSavedSignIns`) to enumerate orphans; a native store needs list-by-prefix.

### 2.3 Files (all under `filesDir` = `getApplicationSupportDirectory()` on Android)

`getApplicationDocumentsDirectory()` (`app_flutter/`) is NOT used by first-party code for data (only as an exclusion root in `lib/platform/wifi_file_share.dart`).
`getTemporaryDirectory()` (`cache/`) holds project-export plan files only. See section 4.5 for the full list.

### 2.4 Kotlin-owned persistence (stays; do not migrate, but keep it working)

SharedPreferences files `oc_lifecycle`, `oc_background_native`, `oc_background_pause`, `builtin_server_recovery`, `builtin_component_writer`,
`builtin_service_diagnostics`, `camera_permissions`, `voice_permissions`; AndroidKeyStore aliases `oc.byoHostSsh.<id>`; files
`oc.teamEngine.<id>/`, `oc.teamEngineDeletion.<id>`, `oc.teamEngine.protection-required`, `oc.sharedProjects`, `linux/**`, `projects/**`.

---------------------------------------------------------------------------------------------------

## 3. Day-1 migration recipe (state-data part)

Inputs the native first launch must read, in this order. Everything is idempotent and verified before the next step; record progress in one
new marker (suggest `native.migration.v1` = JSON `{step, doneAt}`), never delete a source in the migration release (keep it one release as the
Flutter app did for older drafts: `oc.olderDraftsBackupV1.<id>`).

1. **Open `FlutterSharedPreferences`** read-only. Decode types per 2.1 (long->Int, double prefix, both list encodings).
2. **Decide the physical home of prefs** (decision for the owner/architect, recommendation first):
   - *Recommended day-1:* keep `FlutterSharedPreferences` + `flutter.` prefix as the Android actual of a KMP `KeyValueStore` ("adopt in place").
     Zero data copy, keeps every Kotlin reader (widgets, tile, shortcuts, scheduler, locale, recovery) working unchanged, keeps the key-shape sweep.
     Parsers in commonMain port the Dart tolerant readers. Later release: move large blobs (queue, stash, drafts index, caches) to files/DB with their own versioned step.
   - *Alternative:* copy into DataStore/Room and rewrite all Kotlin readers in the same release (higher risk; the tile/widgets/schedule/recovery readers are in section 4.6).
3. **Secrets:** read `FlutterSecureStorage.xml` + wrapped key + Keystore RSA alias (2.2), decrypt, re-store each secret in the new `SecretStore`
   (Android Keystore AES-256-GCM key, non-exportable; `androidx.security:security-crypto` is deprecated, so avoid it). Verify each by read-back; on any
   failure set the profile's "re-enter" flag (mirror `requiresPasswordReentry` / `requiresCodexTokenReentry`) instead of aborting. Map of keys: section 4.3.
   Register every decrypted secret with the redactor before any logging (`KitRedact.registerKnownSecret` equivalent).
4. **Profiles first:** parse `flutter.oc.profiles` (JSON list). Per-profile side keys that must be attached on load: `oc.cleartextOk.<id>`, `oc.certificatePin.<id>`
   (origin must still equal the profile's current HTTPS origin, else drop), secrets `pw.<id>` / `oc.codexToken.<id>` / `oc.teamEngineAuth.<id>`.
   Apply `plainServerName()` on read exactly like `ServerProfile.fromJson` ("This phone" for loopback names, "Computer at <ip>" for bare IPs).
5. **Run the idempotent Flutter-side migrations the old app would have run** so the native app starts from the same truth: retire keys (`oc.budgets.*`,
   `oc.glassLiquidActive`, `oc.glassLiquidStrikes`, `oc.glassLiquidOffUntil`, `oc.effectsLiveBackground`), phone-agent owner mapping
   (`_migratePhoneAgentOwners`), notification-rules merge (`oc.notify.version` = 1), read-state consent migration journal. The native app should either
   run the same functions or refuse to touch keys whose migration marker is absent.
6. **Files:** none move. `draft-attachments-v1/`, `prompt-photo-recovery-v1/`, `prompt-stash-attachments-v1/`, `provider_logos/`, `voice_models/`, `diagnostics/`,
   `crash-diagnostics/`, `migrations/` stay at the same paths; the native file stores must keep the same sharding (`<sha256(owner)>/<sha256(content)>.blob`).
7. **Do not read** `app_flutter/` (nothing first-party lives there) or Shorebird's updater dirs; the in-app update notice (`lib/update/shorebird_update_notice.dart`)
   has no persisted state of its own (checked: no prefs use in `lib/update`).
8. **Verify** with a count-and-hash audit: number of profiles, per-profile queue entries, drafts, stash prompts, pins, and a SHA-256 of each blob key before/after.
   Surface a plain-words failure (no raw errors) and keep the Flutter data so the owner can retry; never destroy on partial success.
9. **Rollback story:** Shorebird patching ends at the switch, but the previous Flutter APK, if reinstalled over the native one, cannot run (downgrade is refused by Android
   for lower versionCode). So the only safe rollback is "keep the old data readable and untouched" plus a build that can re-read it.
   versionCode must exceed Flutter's current `2.1.0+57`.
10. **Preview flavor** (`.preview` applicationId suffix) is a separate sandbox: nothing to migrate.

---------------------------------------------------------------------------------------------------

## 4. Complete data inventory (what the day-1 migration and the new stores must cover)

Legend: `<id>` = server profile id (opaque string, regex `^[A-Za-z0-9_-]{1,128}$`; generated as `DateTime.now().microsecondsSinceEpoch.toString()` in most
flows, a UUID or a fixed id elsewhere). `<owner>` = phone-agent owner id (a profile id, see STATE-DATA-05). "Swept" = removed by the generic profile sweep
(section 5). Type column: S string(JSON unless stated), B bool, I int(long), L string list. All keys below are physically `flutter.<key>` on Android.
"Native reads" = a Kotlin class reads it directly (section 4.6).

### 4.1 SharedPreferences: profiles, trust, identity

| Key | Type | Format / caps | Owner file | Notes |
|---|---|---|---|---|
| `oc.profiles` | S | JSON array of profile objects (below). Never contains secrets. | `lib/state/profiles.dart`, `profiles/models.dart` | Native reads (`ScheduledPromptStore`, `project_export`). Parsed by ~12 stores as an existence check. |
| `oc.activeProfile` | S | profile id | `profiles.dart` | Native reads (`WidgetStatusIdentity`). Removed (not set) on deletion/reset. |
| `oc.cleartextOk.<id>` | S | exact origin string `http://host:port` the person confirmed for plain HTTP | `profiles.dart` `_syncCleartextConfirmation` | Swept. Only valid while it equals `cleartextOriginOf(baseUrl)`; dropped when profile moves or becomes HTTPS. |
| `oc.certificatePin.<id>` | S | `{"origin":"https://host:port","sha256":"<64 hex lowercase>"}` | `profiles.dart` | Swept. Valid only when origin equals the profile's current HTTPS origin; agent-socket profiles never keep one. |
| `oc.tailscale.<id>` | B | server chosen as a Tailscale server in the add flow | `ui/screens/servers/servers_actions.dart:273,337` | Swept. |
| `oc.phoneAgentOwner.<id>` | S | owner profile id (regex `^[A-Za-z0-9_-]{1,80}$`) | `profiles/phone_agent_owner.dart` | Swept (kept while another local profile still uses that owner). Native reads (`BuiltinLinux`). |
| `oc.phoneAgentGateMigration.<owner>` | L | profile ids whose legacy gates were already adopted | `phone_agent_owner.dart` | |
| `oc.agentPhoneGate.<owner>` | S | JSON map `agentId -> gate{fingerprint:String,...}` (checks that certify the shared Ubuntu agent install) | `builtin/agents/phone_agents_host.dart`, `domain/phone_agent_host.dart` | |
| `oc.agentInstall.<owner>` | S | id of the agent being installed (resume marker) | `phone_agents_host.dart:694,724` | |
| `oc.phoneAgentsUsed.<id>` | B | the agents had conversations the last time the list read them | `connection/phone_agents_cache.dart` | Swept/retained with owner. |
| `oc.builtinServerOwner` | S | profile id owning the one phone runtime pointer | `state/builtin_server_owner.dart` | App-wide; tombstoned on deletion (`clearProfile`); native reads (acceptance tests). |
| `oc.builtinRecovery.<id>` | S | JSON recovery marker for the in-app server (restart attempts) | `lib/builtin/builtin_server_recovery.dart:96` | Native reads (`BuiltinLinux`). Swept. |
| `oc.phoneServerStopped.<id>` | B | person stopped the phone server on purpose | `lib/builtin/deliberate_stop.dart` | Swept. |
| `oc.managedServerRecovery.<id>` | S | Termux managed-server recovery record | `lib/termux/managed_server_recovery.dart:84` | Swept (prepared for deletion first). |
| `oc.termuxMigrationOffer.<id>` | B | one-time offer dismissed | `domain/termux_migration_service.dart` | Swept. |
| `oc.termuxMigration.<id>` | S | Termux -> in-app Ubuntu migration job state | `builtin/migration/termux_migration_controller.dart:25` | Swept. |
| `oc.linuxReset.pending` | S | `"erasing <ISO>"` or `"installing <ISO>"` | `builtin/linux_manager/linux_reset_flow.dart:71` | App-wide, resumable marker. |
| `oc.sharedProjects.<id>` | L | canonical shared-storage project roots opened by this profile, sorted | `state/shared_project_roots.dart` | Swept; the union is also pushed to native via channel `io.github.eslamasabry.opencode_mobile/builtin_linux` (native keeps its own copy in file `oc.sharedProjects`). |
| `oc.quotaCollector.<id>` | B | usage collector in In-app Ubuntu is set up and not turned off | `builtin/quota_collector_host.dart:131` | Swept; retained until profile commit. |
| `oc.usageComputerReader.<id>` | B | opt-in one-shot usage reader on that computer | `state/computer_usage_source.dart:393` | Swept. |

`ServerProfile` JSON (inside `oc.profiles`): `id`, `name`, `baseUrl`, `backend` (`openCode|codex|paseo`), `username`, `codexDirectory` (only for codex/paseo),
`flavor` (always read back as `v2`), `requiresOpenCode2Migration` (also true when an old non-codex/paseo profile has `flavor != v2`), `openCode2HistoryPending`,
`serverVersion?`, `orchestration?` = `{provider(gascity|fixture|phoneEngine), url, city, hostMode(computer|phone), hostKind(pc|laptop|wsl|phone), front, enabledAt?(UTC ISO)}`.
Transient (never persisted): `password`, `codexToken`, `teamEngineAuth`, `requiresPasswordReentry`, `requiresCodexTokenReentry`, `cleartextConfirmedOrigin`, `certificatePin*`, `transientTransport`.
Codex and Paseo share the "agent socket" shape: `baseUrl` is a `ws(s)://` URL, the secret is in `oc.codexToken.<id>`, the project folder in `codexDirectory`.
Pseudo-profiles `<id>.agents` (suffix const `agentBackendProfileSuffix`) are never saved; they exist only so keys written for an agent backend end in `.<phoneId>.agents`
and fall under the phone profile's sweep.

### 4.2 SharedPreferences: conversation, composer and queue data

| Key | Type | Format / caps | Owner file | Notes |
|---|---|---|---|---|
| `oc.location.<id>` | S | `{"directory":String?,"workspace":String?}` (at least one non-empty, else key removed) | `profiles.dart` `setLocation` | Swept. Not restored when it is a home/root or the AI Team folder. |
| `oc.recentLocations.<id>` | S | JSON list `[{"directory","workspace"}]`, most recent first, **max 8** | `profiles.dart` | Swept. Write failure never fails a project switch. |
| `oc.lastProject.<id>` | S | normalized project directory (trailing separators stripped) | `connection/chat_feed.dart:10` | Swept. |
| `oc.defaultProject.<id>` | S | last opened/created project id (redacted) | `state/interaction_defaults.dart:301` | Swept; refuses secret-like ids. |
| `oc.defaultNotices.<id>` | L | `DefaultKind` names whose "chosen for you" notice was already shown (`project, model, voicePack, voice, delivery, review`) | `interaction_defaults.dart:302` | Swept. |
| `oc.model.<id>` | S | `"providerID|modelID"` | `profiles.dart` | Swept. |
| `oc.modelExplicit.<id>` | B | model was picked by the person (vs default) | `profiles.dart` | Swept. |
| `oc.agent.<id>` | S | selected agent name | `profiles.dart` | Swept. |
| `oc.variant.<id>` | S | thinking variant; empty = key removed | `profiles.dart` | Swept. `clearModel` clears model+explicit+variant atomically. |
| `oc.sessionModels.<id>` | S | JSON map `sessionId -> "provider|model|variant"`, keep newest **200** | `profiles.dart` | Swept. |
| `oc.sessionAgents.<id>` | S | JSON map `sessionId -> agent`, newest **200** | `profiles.dart` | Swept. |
| `oc.sessionDisplayModels.<id>` | S | JSON map `sessionId -> {providerID,modelID}`, **200** | `connection/models.dart:108` | Swept. Display only (never used to send). |
| `oc.modelLibrary.<id>` | S | `{"favorites":[ModelRef],"recent":[ModelRef <= 8]}`; empty library = key removed | `state/model_library.dart` | Swept. `ModelRef` = `{providerID,modelID}`. |
| `oc.modelNames.<id>` | S | JSON map `"provider/model" -> display name`, **40** | `connection/models.dart:40` | Swept. |
| `oc.providerRuntimeRefresh.v1.<id>.<enc>` | B | one-shot provider runtime refresh done at this location; `<enc>` = `Uri.encodeComponent("<dir|'<default>'>\n<ws|'<default>'>")` | `profiles.dart` | Swept via infix rule. |
| `oc.providerRuntimeUnloadable.<id>.<enc>` | S | sorted comma-joined provider ids still unloaded after refresh | `profiles.dart` | Swept via infix rule. |
| `oc.chatAgent.<id>` / `oc.agentModel.<id>` | S | phone-agent chat default agent / default model for that profile | `connection/phone_agents.dart:419,453` | Swept. |
| `oc.sessionPins.<id>` | S | JSON map `scopeJson -> [sessionId]` where `scopeJson = jsonEncode([directory, workspace])` | `state/session_pins.dart` | Swept. |
| `oc.sessionViews.<id>` | S | JSON map `jsonEncode([baseUrl, directory, workspace, sessionId]) -> idleMillis(int>=0)`, **2048** (oldest dropped) | `state/session_read_state.dart` | Swept. Local "viewed up to" watermark. |
| `oc.returnBrief.<id>` | S | JSON map `jsonEncode([baseUrl,dir,ws]) -> ReturnBriefAck{runs,requestIDs}`, **64** locations | `state/return_brief_state.dart` | Swept. |
| `oc.autoApprove.<id>` | S | JSON map `sessionId -> {"mode":"ask|autoOnce","inheritToChildren":bool}`; key `"*"` = server-wide "approve everything" | `state/session_auto_approval.dart` | Swept. Mode `autoOnce` replies "once", never "always". |
| `oc.sessionInventory.<id>` | S | `{"v":1,"scope":sha256(...),"at":ms,"rows":[{id,title<=256 scalars,updated}]}`, **100 rows, 64 KiB**; titles redacted | `state/session_inventory_cache.dart` | Swept. `scope = sha256(jsonEncode([backend,flavor,baseUrl,username,directory,workspace]))`. Display only. |
| `oc.chatTail.<id>` | S | `{"v":1,"scope","session","at","rows":[{id,role(user|assistant),text<=1000 scalars}]}` newest **12**, <=100 000 chars; redacted | `state/session_tail_cache.dart` | Swept. One conversation per profile. |
| `oc.contextUsage.<id>` | S | JSON map `sessionId -> fraction 0..1 (3 decimals)`, newest **200** | `state/context_usage_memory.dart` | Swept. |
| `oc.conversationTitles.<id>` | L | each element JSON `{"k":"provider:sessionId","t":title}`, **300** | `state/conversation_title_memory.dart` | Swept. Keeps titles after helper records are archived. |
| `oc.chatNoticesSeen.<id>` | L | `"<note>:<sessionID>"` entries, **400** (note `auto-approve`) | `state/chat_notice_memory.dart` | Swept. |
| `oc.agentTurnFailures.<id>` | L | JSON entries per conversation, redacted, **100** | `chat_notice_memory.dart` (AgentTurnFailureMemory) | Swept. Needed because Paseo forgets failure text on restart. |
| `oc.shareSessionViews.<id>` | B | share "viewed" watermark with the server (read-state sharing) | `state/read_state_consent.dart` | Swept. Absent => default for that profile (see `...Default`). |
| `oc.shareSessionViewsOrigin.<id>` | S | e.g. `"migrated"` provenance of the value | `read_state_consent.dart` | Swept. |
| `oc.shareSessionViewsDefault.<id>` | B | the default captured when the profile was first seen | `read_state_consent.dart:214` | Swept. |
| `oc.shareSessionViews` | B | legacy app-wide value (pre-migration) | `read_state_consent.dart:138` | Read once by the migration. |
| `oc.readStateConsentMigration.v1` | S | `{"version":1,"complete":bool,"ids":[String],"legacy":bool}` | `read_state_consent.dart:15` | App-wide journal; missing-or-invalid legacy value must not grant sharing. |
| `oc.sessionLinkBinding.<id>` | S | `{"schemaVersion":1,"origin","instanceId","verifiedAt":ms}` (exactly 4 keys) | `state/session_link_bindings.dart` | Swept. Verified install metadata for session links. |
| `oc.agentHandoffs.<id>` | S | JSON list of HandoffLink `{id,sourceProfileId,sourceSessionId,sourceOrigin,sourceTitle,targetProfileId,targetSessionId,targetOrigin,targetAgent,projectDirectory,handoffPath}`; one canonical copy under the *source* profile | `state/agent_handoff_links.dart` | Swept; deleting either profile removes references in the survivor. Fields <=4096 chars, ids <=256 and redaction-stable. |
| `oc.pendingCommands.<id>` | S | `{"schemaVersion":1,"commands":[{commandID,receiptID,sessionID,tabID,scope,createdAt,state(sent|uncertain|confirmed|failed)}]}`, **1000 entries / 1 MiB**, tombstones kept, never evicted | `state/pending_command_journal.dart` | Swept (closed first). Commands ids: `queue:<entryId>`, `create:<commandID>`. No prompt body, ever. |
| `oc.pendingAuth.<id>` | S | JSON list of 9-field attempts `{attemptID,integrationID,kind(oauth|command),mode(auto|code),profileID,origin,directory,workspace,expiresAt}`, bounded count/bytes | `state/pending_auth.dart` | Swept. Recovery metadata only: no URL, code, command text. |
| `oc.genui.journal.<id>` | S | list of references `{profile,source,directory,workspace,session,message,call,revision,endpoint,dispatch?}` | `state/gen_ui_journal.dart` | Swept. Routing metadata only. |
| `oc.genui.covered.<id>` | S | `{"v":1,"entries":...}` coverage of history already recovered | `state/gen_ui_coverage.dart` | Swept. |
| `oc.genui.enabled.<id>` | B | generated-UI on/off for that server (default true when managed) | `connection/gen_ui.dart:52` | Swept. |
| `oc.chatListShown.<id>` | B | the server's conversations appear in the one Conversations list (default true for on-this-phone servers) | `connection/side_connections.dart:33` | Swept. |
| `oc.agentFeed.<owner>` | S | JSON list (<=200) of `{sourceId,sessionID,title,directory,projectName,isGit,at,parentID,agentId,agentLabel,sourceLabel,canReopen}` | `connection/phone_agents_cache.dart` | Swept with owner (kept while shared). |
| `oc.mainFeed.<id>` / `oc.sideFeed.<id>` | S | same row shape: this server's own rows / another server's rows | `phone_agents_cache.dart:123,152` | Swept. |
| `oc.agentSignedOut.<id>` | S | JSON map `agentId -> display name` of agents found signed out | `phone_agents_cache.dart:83` | Swept. |
| `oc.offlineQueue` | S | JSON array of queued prompts (below). **App-wide, not profile-keyed** | `state/offline_queue.dart` | Entries carry `profileID`; removed per-profile by the deletion transaction. Limits: 50 entries, 14 days, 60 MiB total, 20 MiB/entry. |
| `oc.keptQueuedPrompts` | S | queued prompts saved as drafts from removed servers | `state/queued_prompt_removal.dart` | App-owned on purpose; NOT swept. Cap = 50. |
| `oc.sessionDrafts` | S | JSON array (sorted newest first) of `{sessionID,profileID?,text,updatedAt,attachments?[DraftAttachmentRef],directory?,workspace?}`; storage key = `jsonEncode([profileID,sessionID])` or bare sessionID when profileID empty | `state/session_drafts.dart` | App-wide; **max 50, full store refuses new drafts (never evicts)**. `profileID==''` = legacy unattributable draft. |
| `oc.draftAttachmentVault` | B | the draft attachment directory may hold files (GC only runs when set) | `connection/drafts.dart:138`, `draft_photo_recovery.dart:114` | App-wide. |
| `oc.pendingPromptPhoto` | S | `{id,profileID,sessionID,directory?,workspace?,ref?:DraftAttachmentRef,path?,name?,failed}` | `state/prompt_photos.dart:107` | App-wide; origin committed before the camera launches (Android may kill the app). |
| `oc.promptStash.<id>.<stashId>` | S | one JSON per saved prompt `{id,text,createdAt,directory,workspace,attachments[],attachmentFormat:1,attachmentRefs[],references[ReviewReference]}`; **capacity 50 per profile**, attachments <=5, <=32 MiB each prompt | `state/prompt_shelf.dart` | Swept (infix). `key == "oc.promptStash.<id>."+prompt.id` is validated on read (identity change = corrupt). |
| `oc.promptHistory.<id>` | S | JSON list of strings (recently sent prompts) | `prompt_shelf.dart` | Swept. |
| `oc.stashAttachmentVault.<id>` | B | owner marker: this profile may own stash attachment files | `prompt_shelf.dart` | Swept (after file GC). Must survive until files are collected. |
| `oc.savedPromptMigrationV1.<id>` | B | older drafts already moved into Saved prompts | `state/migration_runner.dart` | Swept. |
| `oc.olderDraftsBackupV1.<id>` | S | redacted backup of migrated older drafts (nothing reads it; "one release") | `migration_runner.dart` | Swept. |
| `oc.draft.<target>.<id|app>` | S | raw text draft of a kit text input / sheet (`note.<sessionId>` etc.); empty text removes the key | `ui/kit/kit_sheet.dart:164` | Swept; `oc.draft.<target>.app` (Report a problem) is app-wide and never swept. A manifest test (G10) lists every target. |
| `oc.phraseChips.<id>` | S | `{"v":1,"hidden":[normalizedPhrase<=100],"entries":{normalized:{"t":text,"n":total,"l":lastMs,"m":{moment:{"n","l"}}}}}`; phrase<=40 chars, one line, no `@`,`/`,`!`, no secrets; **50 entries**; chip after 2 sends; half-life 14 days; max 4 chips | `state/phrase_chips.dart` | Swept. |
| `oc.phraseChips.enabled` | B | device-wide switch (default true) | `phrase_chips.dart` | Not swept. |
| `oc.readerPreferences.<id>` | S | `{"sourceFirst":bool,"wrapCode":bool?}` | `state/reader_preferences.dart` | Swept. |
| `oc.developmentServices.<id>` | S | `{"version":1,"services":[DevelopmentService]}` max **20**, <256 KiB; configs + ownership receipts only, never logs/creds | `state/development_service_store.dart` | Swept. |
| `oc.setupAudit.<id>` | S | metadata-only history of setup proposals (`proposed|pending|accepted|verified|uncertain|rejected` x `apply|undo|review`, ids by pattern) | `state/setup_audit_store.dart` | Swept (closed first). |
| `oc.setupRegistry.<id>` | S | `{"version":1,"optedIn":bool,"cachedAt","entries":[RegistryEntry <=100]}` | `state/setup_registry_store.dart` | Swept. Registry browsing off by default. |
| `oc.mobileDownloadConsent.<id>` | S | `{"version":1,"choice":MobileDownloadChoice name}` | `state/mobile_download_consent.dart` | Swept. |
| `oc.inFlowConsent.<id>` | S | `{"version":1,"firstStart":bool,"choices":{InFlowConsentKind:InFlowConsentChoice}}` | `state/in_flow_consent.dart` | Swept. Redaction-stable or write refused. |
| `oc.permissionConsent.<id>` | S | `{"version":1,"entries":{sha256digest:{"requests":[ids],"decision":PermissionOfferDecision}}}` <=256 entries, <=128 KiB | `state/repeated_permission_consent.dart` | Swept. "Third request of one kind" offer. |
| `oc.automation.<id>` | S | AutomationPolicy `{"version":1,"supervision":high|balanced|autonomous,"autoApprove":bool,"autoMergeOnGreen":bool,"behaviors":{<AutomationBehavior>:bool}}` | `state/automation_policy.dart`, `domain/automation_policy.dart` | Retained through a failed deletion, removed at commit. Native reads (`BuiltinLinux` restart policy). Missing key => defaults (non-consent behaviors ON); stored-but-corrupt/unknown version => ALL OFF; stored record missing a behavior => that behavior OFF. |
| `oc.automaticActivity.<id>` | S | `{"version":1,"acts":[AutomaticAct{id,location,...redacted summary...}]}` bounded | `state/automatic_activity.dart` | Same retention as automation. Powers "While you were away". |

Queued prompt entry (`oc.offlineQueue`): `{id,profileID,sessionID,text,attachments:[{mime,filename,url(data:|file:|http(s):)}],mentions:[{name,value,start,end}],modelProviderID?,modelID?,agent?,variant?,createdAt(ms),error?,dispatchedAt?(ms)}`.
Corrupt blob => `readable=false`, no sends, non-empty writes refused, only an explicit empty save (user clears) recovers.
Timestamps below 2000-01-01 are "unknown age" and are never expired.

### 4.3 flutter_secure_storage keys (Keystore-encrypted)

| Key | Content | Owner | Deleted by |
|---|---|---|---|
| `pw.<id>` | OpenCode server password (non-agent-socket profiles) | `ProfileStore` | `remove`, `resetSavedSignIns` |
| `oc.codexToken.<id>` | Codex connection token OR Paseo daemon password (agent-socket profiles) | `ProfileStore` | same |
| `oc.teamEngineAuth.<id>` | bearer token of the native phone project engine; kept across ordinary profile saves (empty on save = preserve) | `ProfileStore` | `remove`, `clearTeamEngineAuth`, reset |
| `oc.quotaToken.<id>` | read token for the usage collector in In-app Ubuntu | `builtin/quota_collector_host.dart` | `remove`, reset |
| `oc.githubToken.<id>` | JSON `GitHubCredential{accessToken,refreshToken?,...}` | `state/github_account.dart` | `remove`, reset |
| `oc.byoHostSecrets.<id>` | JSON `{schema:2,keyAlias:"oc.byoHostSsh.<id>",publicKey,deviceToken}`; **private SSH key is NOT here** (AndroidKeyStore alias `oc.byoHostSsh.<id>`, non-exportable, deleted by `ByoHostSigner.deleteIdentity`) | `state/byo_host_store.dart` | `remove` (deletes identity first), reset (`deleteAllIdentities`) |
| `oc.agentHostSecret.<owner>` | 64-hex random password of the on-phone Paseo/agent host | `builtin/agents/phone_agents_host.dart` | `remove` (unless owner shared), reset |
| `oc.factsbook.<id>` / `oc.factsbookOwner.<id>` / `oc.factsbookOutbox.<id>` | FactsBook token / owner key seed (hex) / unsent notes | `state/factsbook/factsbook_store.dart` | `remove` via `FactsBookStore.secureKeys` |
| `oc.factsbook-node.passphrase` | passphrase of the on-phone FactsBook node | `builtin/factsbook_node_host.dart` | node uninstall |
| `oc.a2aBearer.<agentId>` | bearer for an external A2A agent (agent id, not profile id) | `state/external_agents.dart` | external agent delete (tombstone survives failed delete) |
| `oc.orchestration.<id>.grant` | reserved, never written | `orchestration_store.dart` | `sweep` |
| `oc.secure.probe` | read-only keyring probe (Linux desktop only) | `profiles.dart` | n/a |

`resetSavedSignIns` deletes only keys with prefixes `pw.`, `oc.codexToken.`, `oc.teamEngineAuth.`, `oc.quotaToken.`, `oc.githubToken.`, `oc.agentHostSecret.`, `oc.byoHostSecrets.`
(enumerating orphans via `readAll`), invalidates in-memory profiles and removes `oc.activeProfile`. FactsBook secrets and `oc.a2aBearer.*` are NOT in that list (flag for the rewrite owner).

### 4.4 SharedPreferences: device-wide preferences, consent, teaching (never swept)

| Key | Type | Values / default | Owner |
|---|---|---|---|
| `oc.appearance` | S | `system|light|dark|saver`; default **dark** | `app_preferences.dart` |
| `oc.themePack` | S | `ThemePackId` names (31: opencode, catppuccin, gruvbox, solarized, dracula, nord, tokyoNight, oneDark, monokai, rosePine, everforest, kanagawa, ayu, nightOwl, github, palenight, synthwave, cobalt, midnight, paper, forest, ocean, sunset, sakura, lavender, mint, coffee, slate, amber, highContrast, dynamic); default `opencode` | same |
| `oc.appearanceAccent` | S | accent swatch id; absent = match theme | same |
| `oc.textSize` | S | `smallest(0.85)|small(0.92)|normal(1.0)|large(1.15)` | same (`effects.dart`) |
| `oc.appLocale` | S | language code in `{en,ar,es,ja,pt,ru,zh}`; absent = follow system (see locale resolution below). **Native reads** (`NativeStrings`). | `app_locale.dart` |
| `oc.transcript.reasoningExpanded` / `oc.transcript.timestampsVisible` | B | default false | `app_preferences.dart` |
| `oc.effectsMotion` | S | `full|calm|off` (default full). Legacy `oc.effectsCelebrations==false` + full => calm; the legacy key is removed when motion is set back to full | same |
| `oc.effectsActivityGlow` | B | default true | same |
| `oc.effectsBackgroundOn` | B | live background; default **false** (new key since 2026-10-26; old `oc.effectsLiveBackground` retired) | same |
| `oc.effectsLiveHeldByStyle` | B | stored only when true | same |
| `oc.effectsLiveStyle` | S | default `aurora` | same |
| `oc.effectsLiveIntensity` | S | `subtle(0.7)|normal|bold(1.6)` | same |
| `oc.effectsSurfaceStyle` | S | `solid|soft|brutal|paper|signal` (default solid; Glass/Crystal removed 2026-10-26) | same |
| `oc.effectsCorners` | S | `round|soft|sharp` or absent = style default; changing style clears corners/depth/density/edges | same |
| `oc.effectsDensity` | S | `comfortable|compact` or absent | same |
| `oc.effectsDepth`, `oc.effectsEdges` | S | `flat|raised`, `hairline|none`; written historically, now ignored (each style owns them) | same |
| `oc.effectsGlowStyle` | S | `off|classic|softRing`; default classic. Composer glow: owner likes it, keep. | same |
| `oc.effectsGlowColours`, `oc.effectsGlowSpeed` | S | `one|two` / `slow|normal|fast`; written, but the read path ignores them (app's own) | same |
| `oc.glass` | S | `off|weak|balanced|strong` (legacy; glass removed 2026-10-26, key still parsed) | `glass_choice.dart` |
| `oc.appearanceGuide` | S | `{done?,seen:[question],lastIn:{group:question},last?}` guided Appearance chips | `appearance_guide.dart` |
| `oc.tips` | S | `{tipId:{count,last?(ISO),done?}}`; max 2 shows; cooldown 2 min; "Show tips again" resets | `tips.dart` |
| `oc.nudges.shown` | S | `{nudgeWire:{shownAt(ISO),dismissed}}` (`approvals, review-changes, leave, compact, pin`) | `nudges.dart` |
| `oc.nudges.firstReplySeen` / `oc.nudges.projects` | B / S | first-reply flag / second-project detection | `nudges.dart` |
| `oc.firstRun.state` | S | `armed` (new device, welcome shown) / `done` | `first_run.dart` |
| `oc.firstRun.notifyAsk` | S | `pending` / `answered` | `first_run.dart` |
| `oc.notify.version` | I | `1` once the shared rules were migrated | `notification_preferences.dart` |
| `oc.notify.quietStart` / `quietEnd` | I | minutes after midnight 0..1439; quiet on only if both set | same |
| `oc.notify.wifiOnly` | B | | same |
| `oc.notify.checkInAfterMinutes` | I | 1..1440 (UI offers 15/30/60/120) | same |
| `oc.notify.quotaAlerts` | B | | same |
| `oc.notify.finishedRuns`, `oc.notify.requests` | B | default **true** | same |
| `oc.notifyRules.<id>` (legacy) | S | `ProfileNotifyRules{enabled,notifications,wifiOnly,quietStart,quietEnd,checkInAfterMinutes}` | `profile_monitor.dart:193` (kept, never deleted by migration; still read by the monitor until migrated; **swept**) |
| `oc.quotaMonitor.<id>` (legacy+live) | S | `{"version":1,"rules":{<QuotaProvider name>:{notifications,wifiOnly,quietStart,quietEnd,threshold...}}}` <=64 KiB | `provider_quota_monitor.dart:176` (swept) |
| `oc.keepLiveInBackground` | B | background live mode on/off. **Native reads** (`LivePauseReceiver`, `BackgroundPauseStore`). | `background/live_background.dart:256` |
| `oc.backgroundPauseDismissed` | S | id of the last pause the person hid | `background_pause_notice.dart:71` |
| `oc.readAloudConsent` | B | read-aloud consent given | `ui/screens/chat/read_aloud.dart:5` |
| `oc.replySpeed.inApp` | S | JSON of last in-app reply timing; removed on Linux reset | `builtin/reply_watch.dart:82` |
| `voice.selected_pack` / `voice.language` / `voice.auto_send` / `voice.read_aloud_natural` | S/S/B/B | pack id (default `base`), language enum name, auto-send (default false), natural voice chosen (default false) | `voice/model_manager.dart:60-62`, `voice/natural_voice.dart:192` |
| `oc.thermalGuard` | B | default **true** | `builtin/thermal_guard.dart:261` |
| `oc.thermalPause.<teamId>` | S | JSON `ThermalTeamHold` (AI Team paused by heat); restored on launch, unreadable ones removed | `thermal_guard.dart:262,515` (team id is the profile id of the team host, so the sweep matches) |
| `oc.dev.factsbook` | B | developer flag (also `--dart-define=OC_FACTSBOOK=true`) | `factsbook/factsbook_flag.dart:15` |
| `desktop_window_state_v1` | S | desktop window size/position (Linux/Windows/macOS only) | `desktop/window_state.dart:25` |

Locale resolution when `oc.appLocale` is absent: `resolveAppLocale` walks the device's language list and picks the first supported; Flutter's own fallback would pick Arabic
(alphabetical first) - never use a "first supported" rule. (RTL: Arabic.)

### 4.5 Files and directories (all under `filesDir`)

| Path | Owner | Content / rules |
|---|---|---|
| `draft-attachments-v1/<sha256(owner)>/<sha256(content)>.blob` (+ `.tmp`) | `state/draft_attachments.dart` | UTF-8 text of a `data:` URL, content-addressed. Owner `''` = legacy drafts. Limits: 5 attachments/draft, 32 MiB/draft, 256 MiB total. Written tmp->rename; verified by length + SHA-256 on read; bounded streaming reads. Collected only after metadata commit. |
| `prompt-photo-recovery-v1/...` | `state/prompt_photos.dart` | same vault layout; max photo 10 MiB; picker scales to 2048 px, quality 85 (an earlier 5 x full-frame prompt overflowed the server's model connection). |
| `prompt-stash-attachments-v1/...` | `state/prompt_shelf.dart` | same layout; owner key marker `oc.stashAttachmentVault.<id>`. **Never share a root with the draft vault** (its GC does not know stash references and would erase them). |
| `provider_logos/` | `domain/provider_logo_store.dart` | cached provider logos (rebuildable). |
| `voice_models/` | `voice/model_manager.dart`, `natural_voice.dart` | downloaded sherpa-onnx packs (large, rebuildable but costly). |
| `diagnostics/report_problem.json`, `crash-diagnostics/crash-diagnostics.json` | `diagnostics/*` | Report-a-problem draft state, last crash record. |
| `migrations/<jobId>/` | `builtin/migration/migration_archive_store.dart` | Termux -> Ubuntu migration archives (cleaned as orphans on launch). |
| `linux/ubuntu/**`, `linux/ubuntu.ready`, `projects/**` | Kotlin `BuiltinLinux` | In-app Ubuntu rootfs (marker file `ubuntu.ready`) and projects bound at `/root/projects`. Kotlin-owned. |
| `oc.teamEngine.<id>/`, `oc.teamEngineDeletion.<id>`, `oc.teamEngine.protection-required`, `oc.sharedProjects` | Kotlin `PhoneEngineNative`, `BuiltinLinux` | Phone project engine data / deletion tombstones / shared project binding list. Kotlin-owned. |
| cache dir `project-export-plan` | `builtin/project_export_controller.dart` | temp export plan. |

### 4.6 Native readers of Flutter's SharedPreferences (keys with the `flutter.` prefix)

| Kotlin class | Reads / writes |
|---|---|
| `WidgetV2Bridge`, `SessionsWidgetProvider`, `SessionWidgetRowEnv`... | `oc.widgetSessions` (app-wide JSON snapshot: <=12 sessions, <=12 projects, <=6 servers; titles, project display name, redacted reply excerpt), `oc.widgetStyle.<owner>`, `oc.widgetActions.<id>`, `oc.widgetTarget.<id>`, `oc.activeProfile`; clears `widgetStyle/widgetActions/widgetTarget` per profile |
| `WidgetStatusIdentity`, `WidgetStatusTickets` | `oc.activeProfile`, `oc.widgetTarget.<id>` `{profileID,scopeToken,fingerprint,directory,workspace}`, `oc.widgetFollowup.<id>` |
| `UsageWidgetRows`, `UsageWidgetRenderer` | `oc.widgetUsage.<id>` (per-profile entries), `oc.widgetUsageSubs` (`{v:1,entries:[...]}`), `oc.usageWidget.v1` (hiddenKeys, showSetupRows, windows) |
| `AttentionTileService` | `oc.attentionTile` |
| `PinnedSessionShortcuts` | `oc.pinnedShortcuts` (last published payload; launcher shortcut ids are `oc.session:<...>`) |
| `ScheduledPromptStore`, `...Claims`, `...Receipts`, `...PhoneLease`, `...Calendar` | `oc.scheduledPrompts.<id>` (versioned envelope: schedules <=100, runs <=500, <=4 MiB, digest), `oc.scheduleIndex.<id>`, `oc.scheduleClaims.<id>` (permanent occurrence tombstones - **must never be used to dedupe from history**), `oc.scheduleReceipts.<id>`, `oc.schedulePhoneLease.*`, `oc.scheduleDeleted.<id>`; reads `oc.profiles` to reject writes for deleted profiles |
| `BuiltinLinux` (recovery/idle), `LivePauseReceiver`, `BackgroundPauseStore` | `oc.phoneAgentOwner.<id>`, `oc.builtinRecovery.<id>`, `oc.automation.<id>`, `oc.builtinServerOwner`, `oc.keepLiveInBackground` |
| `NativeStrings`, `NativeNotificationLocale` | `oc.appLocale` (notification language follows the in-app choice) |

---------------------------------------------------------------------------------------------------

## 5. Deletion sweep (the transaction to reproduce)

Entry: `ConnectionController.deleteProfileAndLocalData(id, {queuedPrompts, keepQueuedPrompts, linuxGone})` -> `connection/deletion.dart`; then `ProfileStore.remove(id)`.
Result type `DeleteProfileResult{removedPreferenceKeys, removedQueuedPrompts, removedDrafts, clearedWidgetSnapshot, clearedPinnedShortcuts, clearedAttentionTile, removedProfile, failures[]}`; `partialDeletionMessage` is plain words ("The server was kept: X could not be deleted from this device ... Free up storage and try again.").

**Phase 0 (synchronous, before any await):** mark `_deletingReadProfiles`, close read-state consent, close gen-UI, close session-link and handoff stores, bump deletion revision, forget monitor identity (profile monitor, quota monitor, Paseo usage, usage subscriptions, computer usage), block pending-auth, drop in-memory integration attempts.
**Phase 1 (serialized behind other deletions):** drain automatic activity, automation policy (`pauseForDeletion`), link/handoff/consent/gen-UI; add profile to `_closedQueueProfiles` (no new prompt may join); drain monitors; `_genUiDeleteProfile`; `_paCloseForDeletion` (phone agents: auth, owned setup, host, feeds - failures tolerated only when the whole Linux is gone).
**Phase 2 `_deleteProfileAndLocalData` (everything verified before the row goes):**
1. cancel native scheduled prompts for the profile; await pending draft/auth/location/model-library/read/brief/pin/auto-approval/shelf writes; drain+forget session tail and inventory caches.
2. Compute `scopedKeys` and the retained set = phone-agent keys still shared + `oc.automaticActivity.<id>`, `oc.automation.<id>`, `oc.teamEngineDeleted.<id>`, `oc.scheduleDeleted.<id>`. Suspend widget snapshot republishing (`_widgetSnapshotSuspended`).
3. **Queue lane (serialized with flushes):** require queue readable; with a confirmed `QueuedPromptRemovalPlan`, compare live vs confirmed JSON (if changed -> `QueuedPromptRemovalException(changed)` and nothing removed); optionally `keepAsDrafts` into `oc.keptQueuedPrompts` first; then suspend built-in recovery, prepare Termux managed recovery, clear `BuiltinServerOwner`, bump shelf revision, dispose saved-prompts controller, close consent owners, delete phone team engine data, forget the quota collector, stop/drain orchestration, `prefs.reload()` (native workers write schedule keys outside Dart), rediscover scoped keys; rewrite `oc.offlineQueue` without the profile (failure -> failures list).
4. Drafts lane: rewrite `oc.sessionDrafts` without the profile (**and drop unattributable `profileID==''` drafts**); collect draft attachment files for that owner and for owner `''`.
5. Clear pending photo, home-screen widget snapshot, pinned shortcuts, attention tile (each reports cleared/failed).
6. Clear stash (metadata + files); only then run the generic scoped sweep (`removeScopedPreferences`, excluding retained keys) - **stash first, because the sweep would erase the owner marker needed to find orphan files after a failure**. Re-push `SharedProjectRoots` to native when `oc.sharedProjects.<id>` existed.
7. If `failures` is non-empty: return `removedProfile:false` (server kept, nothing half-deleted that the app cannot explain).
8. `ProfileStore.remove(id)`: close read consent; remove cross-profile handoff references; write new `oc.profiles`; clear `oc.activeProfile` if active; delete BYO SSH identity then BYO secret (verify); agent host cleanup + secret unless a surviving alias shares the owner; delete `pw.`/`oc.codexToken.`, `oc.teamEngineAuth.`, FactsBook secure keys, `oc.quotaToken.`, `oc.githubToken.`. **Any failure restores the previous `oc.profiles`, active id, and cancels handoff deletion**, keyring failures are mapped to `SecureStorageUnavailable`. Then sweep remaining scoped keys.
9. Remove `oc.automation.<id>` and `oc.automaticActivity.<id>` last; `OrchestrationStore.sweep` removes team keys + reserved secure key.
**whenComplete:** if the profile still exists (aborted), reopen everything (queue, consent, gen-UI, monitors, activity, policy, pending auth, link bindings, handoffs, built-in owner), restore the native schedule, and re-sync orchestration.

Also: sweep rule, key shapes that must be matched: any `oc.*` key where `key.endsWith('.<id>') || key.contains('.<id>.')`. Profile ids never contain `.` (digits/UUID/`[A-Za-z0-9_-]`), so one id cannot be a dotted segment of another; keep that property for any new id scheme (and never use `app` as an id: `oc.draft.<target>.app` is app-wide). Non-profile keys that must never match: `oc.profiles`, `oc.activeProfile`, `oc.offlineQueue`, `oc.sessionDrafts`, `oc.widgetSessions`, appearance/theme/effects.


## 6. Feature catalog (STATE-DATA-NN)

Format per feature: **What** (user-visible or internal capability) / **How** (files under `lib/state` unless noted) / **Data** / **Native** / **Must keep**.
`CC` = `ConnectionController` (`lib/state/connection.dart`, 1,116 lines + 52 `part` files under `lib/state/connection/`, ~17,000 lines).
CC is a ChangeNotifier built from ~35 mixins; its parts must be edited as one owner. It owns: current profile + live transports, session/permission/question
caches, the composer queue, notifications, widgets, monitors and the phone-agent backends. In a Kotlin/KMP rewrite split it by the ids below.

### A. Profiles, secrets and trust

**STATE-DATA-01 Server profiles (CRUD, active selection).**
How: `profiles.dart` `ProfileStore` (load/upsert/remove/setActiveId/active), models in `profiles/models.dart`. Static `Expando` caches per SharedPreferences instance so every screen sees one list; `changes` Listenable fires after saves (the app bar kept stale names before: "This phone" rename).
Data: `oc.profiles`, `oc.activeProfile` + secrets (4.3). Load order: retire legacy keys -> parse list (any parse error => empty list, no crash) -> migrate read-state consent for existing ids -> restore `cleartextOk`, `certificatePin` -> restore secrets in batches of 4 (`Future.wait`, perf: "overlap independent profile credential reads") -> `_migratePhoneAgentOwners`.
Must keep: `upsert` is transactional: validates the pin pair, registers secrets with the redactor *before* writing (a failing keyring may echo its input), writes metadata then secrets, and on any secret failure restores the previous `oc.profiles` (`_restoreProfiles`). Empty `teamEngineAuth` on save means "preserve the stored token" (editors rebuild profiles without it). A re-added fixed id after removal must reopen read state (`cf060261a`).
`loadForWidgetAction(id)`: one-shot restore of a single profile for launcher actions (reload prefs, no migrations, no writes).

**STATE-DATA-02 Credentials and "re-enter" recovery.**
How: `_restoreSecret`: any Keystore read error leaves the profile usable with `requiresPasswordReentry` (OpenCode) or `requiresCodexTokenReentry` (agent sockets; Paseo may have no password so only Codex marks "required" when empty). Registers every secret with `KitRedact.registerKnownSecret`; the diagnostics/redaction layer must know all secrets before they can reach a log.
Linux desktop only: `secureStorageProblem()` probes the keyring and gives a sentence before the user types a password that cannot be kept (`SecureStorageUnavailable`).
Must keep: no raw platform exception text ever reaches UI (`SecureStorageUnavailable.messageFor`).

**STATE-DATA-03 Transport trust: cleartext consent and certificate pin.**
How: `profiles.dart` `_syncCleartextConfirmation`, `_syncCertificatePin`, `profiles/validation.dart` (`cleartextOriginOf`, `serverUrlNeedsCleartextConfirmation`, `validateServerProfileUrl`, `normalizeServerProfileUrl`).
Rules: non-loopback `http://` requires a per-origin confirmation (`oc.cleartextOk.<id>` = exact `scheme://host:port`); a profile moved to another address or to HTTPS loses it; an editor that rebuilds a profile without the field keeps the stored value. Pin is `{origin, sha256}` for HTTPS only, lowercased, 64-hex; moving the profile never grants the new origin the old pin. `cleartextUnconfirmed` blocks connect with `cleartextUnconfirmedMessage`. Private/tailnet ranges: Tailscale `100.64.0.0/10`, `*.ts.net`, RFC1918, link-local, `fc00::/7`, `fe80::/10`, `*.local` (see `isPrivateNetworkHost`, `isTailnetHost`); Android's network-security-config cannot express 100.64/10, so the app checks addresses itself (demo lesson) - port `isLoopbackHost`/`isPrivateNetworkHost`/`isTailnetHost` verbatim.
URL rules: only `https://`/`http://`, no userinfo, no query/fragment/path (origin only), port 1..65535, no whitespace/control/backslash/`%` in host. Bare `host[:port]` expands: loopback -> `http://`, otherwise `https://`; bracketed/unbracketed IPv6 handled. Codex: `ws(s)://`, `ws://` only on loopback, no path, project dir absolute (`/` or `X:\`), token <=16384, no whitespace/control. Paseo: `ws(s)://`, host must be loopback or tailnet, path empty or `/ws`, password empty allowed, no spaces/commas (travels in a WebSocket subprotocol).
`plainServerName`: loopback names -> "This phone"; bare IP -> "Computer at <ip>".

**STATE-DATA-04 Backends, flavor detection and OC1->OC2 profile migration flags.**
How: `ServerBackend {openCode, codex, paseo}`; OpenCode profiles speak OpenCode 2 only (`flavor` always `v2` on read). `requiresOpenCode2Migration`/`openCode2HistoryPending` block connect for managed phone servers (`TermuxBridge.managesServerUrl` / `BuiltinLinux.managesServerUrl`): CC sets `lastError = managedRuntimeMismatchMessage`, `status=disconnected`, still writes the active profile; `confirmManagedOpenCode2Migration` polls (1 s, up to 3 min) and keeps the durable pending marker on failure/unknown. Setup capabilities are withheld until history import is confirmed (`serverCapabilitiesFor`). `serverVersion` is cached from the last probe.
Tests: `oc1_profile_upgrade_test.dart` (migration area in the catalog).

**STATE-DATA-05 Phone-agent owner aliasing.**
How: `profiles/phone_agent_owner.dart` `PhoneAgentOwnership`. Several local profiles (e.g. a protocol alias of "This phone") share ONE agent home (installed agents, sign-in, gates). Mapping `oc.phoneAgentOwner.<profileId> -> owner`. On load, first saved valid mapping wins; otherwise owner = highest score (checked home +4, cached chats +2, used +1) then creation order. Legacy gates (`oc.agentPhoneGate.<id>`) with a fingerprint are merged into the owner's once per donor (receipt list `oc.phoneAgentGateMigration.<owner>`), so retrying a check never resurrects old proof.
Deleting one alias keeps the owner's keys (`phoneAgentOwnerRetainedAfterRemoving`); deleting the last removes `agentFeed/phoneAgentsUsed/gates/install` and the secure host secret after asking native cleanup (`agentHostCleanup`).

**STATE-DATA-06 Saved sign-in reset ("Start fresh" on bootstrap failure).**
How: `ProfileStore.resetSavedSignIns` + `AppBootstrap.resetSavedSignIns`; UI in `main_parts/bootstrap_gate.dart` (confirm dialog). Works even if profile loading fails: invalidates retained profiles, enumerates secure keys by prefix (including orphans), registers them with the redactor, removes `oc.activeProfile` FIRST (a refusal keeps all secrets), deletes BYO identities, asks native to clean agent hosts, deletes secrets, re-reads to prove absence. Partial failure throws `SavedSignInResetException` and is safely retryable. Does not touch drafts, queues, settings.

**STATE-DATA-07 AI Team host config per profile.**
`OrchestrationConfig` embedded in profile JSON (provider gascity/fixture/phoneEngine, url, city, hostMode, hostKind, front, enabledAt). Null = plugin off (no controller, no keys). Unknown provider name falls back to `gascity`; unknown hostKind -> default for mode; a kind never contradicts the mode.

**STATE-DATA-08 Profile deletion with local-data sweep.** See section 5. Files: `connection/deletion.dart`, `profiles.dart` (`remove`, `removeScopedPreferences`, `profileScopedPreferenceKeys`), `queued_prompt_removal.dart`. UI contract: confirmation discloses `queuedPromptCountForProfile`, `draftCountForProfile`; offers "keep queued prompts as drafts" (moves them to `oc.keptQueuedPrompts`, shown in every server's Saved prompts, not swept).

**STATE-DATA-09 Bootstrap gate and startup order.**
How: `main.dart` -> `AppBootstrapGate` (`main_parts/bootstrap_gate.dart`) renders immediately, then post-frame: `AppBootstrap.create()` (SharedPreferences.getInstance + `ProfileStore.load`) -> build CC -> `migrateOlderDrafts()` -> `recoverPendingPhoto()` (Android only) -> `migrateNotificationPreferences()` (failure recorded, legacy stays in charge) -> show shell. After 8 s still opening the gate says so and offers Try again; any failure shows a recoverable error page with "Report a problem" and "Start fresh" (STATE-DATA-06). Perf marks: `app.main`, `app.first_frame`, `app.bootstrap`, `app.shell_frame` (`PerfTrace`). Crash diagnostics and report-problem start after the first frame.
Must keep: a blank native window must never result from a prefs/Keystore failure; drafts/photo/notification prerequisites complete before any conversation or connection is exposed.

### B. Connection controller (live server state)

**STATE-DATA-10 Connection lifecycle and transport pairs.**
How: `connection/connect.dart`, `lifecycle.dart`. `connect(profile)` validates by backend, bumps a generation, retires the old transport, builds `(ServerGateway, ServerOperationsGateway)` for OpenCode2 (`Api2Client` Basic auth username `opencode`, password; BYO tunnel uses the transient username; certificate pin only if pin origin == current origin), Codex, or Paseo; opens the per-location event stream (SSE) and the server-wide (global) stream; restores the saved location; loads sessions/catalog/permissions/questions/forms; syncs profile services (monitors, background, widgets, orchestration); starts wake lock for local Termux only. Every async step re-checks `generation`/`identical(api, currentApi)`/profile id so stale results are dropped (`_isCurrent`).
`redetectOnFailure`: one failed connect may re-probe the address (a server swapped between generations) before giving up; the retry passes false.
`disconnect({keepActive, silent})`. `retryConnection()` = manual reconnect keeping location and rendered data; concurrent taps share one future.
Isolated mode (`isIsolated`, demo/walkthrough): in-memory store and gateway pair, never touches network, native publishing or reconnect. Agent backend (`isAgentBackend`) and side backend (`isSideBackend`) are secondary controllers that own no profile-wide service (no monitors/background/widgets/active-profile writes).

**STATE-DATA-11 App lifecycle: suspend/resume and quiet reconnect.**
How: `suspendForLifecycle()` flushes any answer still in its Undo window (the app may not wake), cancels network work but keeps profile, location and rendered data, suspends agent backend and every side connection; `resumeFromLifecycle()` recreates ONE transport (concurrent resumes share a future); `prepareActionTransport()/prepareActionRepository()` complete as soon as the wake-time transport can carry requests (before sessions/catalog reload; OC1's multi-MB `/provider` once sat in front of every send after each app switch). Foreground actions must wait on that, not on the reload.
Quiet reconnect (`quiet_reconnect.dart`, `status.dart`): a return from the background reconnects without a banner for `resumeQuietGrace` 4 s; a drop on a previously healthy link stays quiet 15 s before saying so (owner 2026-10-27: a banner on every return is noise). `localServerBusy` (phone server alive but not answering, polled while disconnected) is not "dead" for 2 min; `agentCommandHoldOff` 10 min for a long agent command.

**STATE-DATA-12 Connection status model.**
`StreamStatus {connected, reconnecting, disconnected}`; `connectionStatusTimer` 8 s; `passwordRejected`, `lastError`/`lastFailure` (typed, never shown raw; "Details" holds technical text); `hasConnectedServer`, `isProfileReadable(id)`; capabilities come from the live gateway for the selected profile and from `api2ServerCapabilities` otherwise (`ServerCapabilities` flags gate features - never the flavor enum). Derived UI-state helpers: `finishedUnseen`, `stoppedByPerson`, `turnInterruptReason`, `turnStoppedFromThisApp`, `turnRunCheck` (asks the server whether a cut-off turn still runs; answer fresh for 20 s, 8 s timeout).

**STATE-DATA-13 Project/workspace location.**
How: `connection/locations.dart`, `profiles.dart` (`locationFor/setLocation/clearLocation/recentLocations/forgetRecentLocation`). The saved directory is restored on connect and is **kept even when the server catalog does not list it** (the catalog tracks known projects, not every folder/worktree; absence never authorizes switching). `_restoringSavedLocation` makes cached previews use the saved location; `revalidateRestoredLocation` confirms later; `probeProjectFolder` classifies a folder `exists|missing|unknown` (only an explicit not-found counts as missing; 8 s listing timeout, on a throwaway transport so a wrong path never rescopes live requests); `disposeFolderInstance` forgets a broken instance after creating a folder (OC1-era quirk; best effort).
A home folder / filesystem root / AI Team folder is never a project: `workspaceChoiceRequired` stays true so sessions cannot start until a real project is chosen, but existing conversations can still be read there (`selectLocationForExistingSession`). `normalizeDirectoryPath` strips trailing separators, keeps case; `projectContainsDirectory` treats worktrees and subfolders as inside the project; the catch-all `/` project covers nothing.
Codex/Paseo: the profile's `codexDirectory` is authoritative on connect (older OpenCode-style selections must not undo an edit).
Recent projects: max 8 per server; convenience data - write failures never fail a switch.

**STATE-DATA-14 Session list, busy status, pins and polling.**
How: `connection/sessions.dart`, `session_events.dart`, `chat_feed.dart`. Event-driven with a 5 s polling fallback (`enablePollingFallback`) that reads busy statuses alone for phone chats, refreshes sessions when `shouldPoll`, and is skipped while loading. Tracks: assistant message ids whose completion arrived live on this connection (cap 512), sessions in provider-retry backoff (from `session.status {retry}`, v2 `session.retry.scheduled`), a notification ordering snapshot (status-only notifications keep the list identity), `refreshSessions` paging (`ServerPage`), create/rename/delete, fork titles in plain words. Pins: `SessionPinStore` (`oc.sessionPins.<id>`, scope = `[directory, workspace]`). Chats-first Home feed merges conversations across projects (4 pages x 50, 120-char previews, 2 s debounce) and across connections (STATE-DATA-39/40).
Must keep: a run that ended while the app was away must not stay "Working" (`58343f841`); a stopped reply says "Stopped" not "Done" (`f0420edee`, `_stoppedByPerson` memory cleared on new prompt/open); busy status snapshots are overridden by later live run events (otherwise a mid-run read kept the row Working).

**STATE-DATA-15 Model, agent and variant selection.**
How: `connection/models.dart`, `catalog.dart`, `model_library.dart`, `profiles.dart`. Layers: profile default (`oc.model.<id>`, explicit flag, `oc.agent.<id>`, `oc.variant.<id>`), per-session override (`oc.sessionModels`, `oc.sessionAgents`: a chat's choice never changes another chat or the default), display-only last model per session (`oc.sessionDisplayModels`), names remembered across restarts (`oc.modelNames`, so a chip never flashes `claude-sonnet-5` before `Sonnet 5`), favorites + recent (8) with stable MRU cycling. Writes go through `_writeSelection`: a serialized, rollback-on-failure, acknowledged-before-published transaction (the Settings facade only shows acknowledged values via `confirmedSelectionPreference`).
Catalog: `catalogFreshFor` 10 min, `catalogReuseWindow` 60 s; reload shares in-flight loads; `retainWhere(available)` only after a successful refresh (a failed request never erases shortcuts). Provider runtime heal: providers listed connected but not loaded into the runtime ("limbo": `/provider` lists the full models.dev catalog, every prompt fails "Model not found") trigger a one-shot runtime refresh per location (`oc.providerRuntimeRefresh.v1...`), remembering unloadable sets (`oc.providerRuntimeUnloadable...`) so it never loops; manual "Reload providers" never stops a running reply (waits, reports `providerReloadWaitingOn`).
OC2 session selection (`SessionSelectionGateway`): the server keeps a conversation's model/mode; a cold reconnect may lack the record -> read once and say "loading".

**STATE-DATA-16 Permission requests and auto-approval.**
How: `connection/permissions.dart`, `feed_permissions.dart`, `request_events.dart`, `session_auto_approval.dart`. Outstanding asks by request id; hydration after (re)connect (events are not replayed); replies once/always/reject (OC2 reject carries an optional steering message - "steer, never queue"). Per-session `autoOnce` replies "once" only (never "always"); `inheritToChildren` walks the parent chain via `sessionsById`; server-wide approve-everything stored under key `*`; sweep on switching an automatic mode on answers every already-waiting request; failed automatic replies stay visible with their failure; bounded in-chat "auto-approved" record (memory only). The app also tells OC2 servers the choice (`3b90a4c65`: auto-approved conversations run tools on the server's own rules). A notification, sheet and inline card share one answer slot ("claim before waking the transport"). Notification quick replies use the live background transport directly (the foreground wake path counts as an app resume and clears every alert).
Related: repeated-permission offer after 3 requests of one kind (`oc.permissionConsent`, STATE-DATA-35), session metadata key `oc.mobile.permissionRequests` (server-side session metadata, not a local pref).

**STATE-DATA-17 Questions, forms and inbox (OpenCode 2).**
How: `questions.dart`, `feed_questions.dart`, `feed_question_reads.dart`, `feed_forms.dart`, `forms_inbox.dart`. Pending questions/forms (incl. global MCP elicitation forms with sentinel session `"global"`), the OC2 inbox (admitted-not-yet-delivered sends, feeds the pending-sends strip). Form events are ephemeral -> re-poll after every SSE (re)connect. A snapshot is bound to a live inventory, not a reusable request id; answers are routed by exact source identity (`67f5ab910`). 400 invalid-answer keeps the form open. Questions qualify for a notification quick reply only when typed reply is meaningful (alerts.dart head). Nothing here persists content.

**STATE-DATA-18 Offline prompt queue and flush (the highest-risk store).**
How: `offline_queue.dart` (`QueuedPrompt`, `OfflineQueueStore`, eviction), `connection/queue.dart` (CC lane). See 4.2 for format and limits.
Rules to reproduce exactly:
- `queuePrompt`: refuses (false) an entry over 20 MiB; storage failure throws `OfflineQueueWriteException` so the composer keeps its text; limits applied on first read and written back (expired > 14 d, > 50 entries, > 60 MiB, oldest first) and an `OfflineQueueEviction` notice is shown once ("Discarded N queued drafts: ...").
- Flush (`flushOfflineQueue`): oldest first, active profile only (others counted, "N drafts waiting for other servers"), requires `capabilities.offlinePromptQueue`, not in OC2-migration, `automationPolicy.allows(reconcileQueuedSends)` unless the entry was explicitly resent. Re-checks `(profileId, baseUrl, directory, workspace)` origin and `identical(api)` after every await. For staged-revert servers it first fetches the session and refuses with a 409-style "Review the staged revert" if reverted.
- Per entry: (1) apply model/agent via `SessionSelectionGateway` (intentional choices persisted with the entry); (2) write dispatch marker `dispatchedAt` (queue lane; a refusal stops the batch); (3) re-check policy (if changed, roll back the marker; a refused rollback keeps the conservative marker); (4) dispatch, with `CommandReceiptController` + `CommandReceipt{commandID:"queue:<id>"}` when the server supports receipts (confirmed state required) else `promptAsync`; (5) on acceptance persist removal (`recorded`); a refused removal leaves the marker (review, never a duplicate) and notes `_queuedPromptsAcceptedUnrecorded` (resend is then a guaranteed duplicate).
- Failure handling: pre-dispatch API error with status -> entry keeps `error` and flush continues; no status (offline) -> stop batch; after dispatch -> never retry automatically, entry waits in "review" (`queuedPromptReviewCount`); `ProductException` stops the batch.
- `resendQueuedPrompt(id)` (explicit, clears the marker durably first, authorizes only that entry, triggers a flush), `retryQueuedPrompt(id)` (for errors where nothing left the device), `removeQueuedPrompt(id)` (returns false while the entry is on the wire), `moveQueuedPrompts` (+ `undoQueuedPromptMove`) to the connected server, in one queue write. `_flushOfflineQueueAgain` coalesces flush requests during a flush.
Tests: `offline_queue_test.dart` (20 cases), `offline_queue_concurrency_test.dart`, `offline_queue_unconfirmed_test.dart`, `command_receipts*_test.dart`, `queued_prompt_removal*_test.dart`.

**STATE-DATA-19 Online sends use the same durable marker.** `holdOnlinePrompt/canDispatchOnlinePrompt/finishOnlinePrompt`: before an online send leaves, an entry with a marker is persisted so a process death shows a review entry rather than losing or duplicating the prompt; unknown outcomes stay for explicit review, confirmed acceptance and preflight refusals remove the marker; profile deletion fences online sends (`canDispatchOnlinePrompt` checks `_closedQueueProfiles` and `_deletingReadProfiles`).

**STATE-DATA-20 Command receipts journal and session-creation receipts.** `pending_command_journal.dart`, `session_creation_receipts.dart`, `domain/command_receipts.dart`, `command_receipt_transport.dart`. Metadata-only, per-profile, one writer, claims are atomic (`claim` true only for a new id; duplicates never replace), uncertain/terminal receipts can never become `sent` again, capacity blocks new claims but never evicts, malformed journal fails closed, reads re-check durable membership (`reload`) not the optimistic cache, `receiptID`s are random. Scope hash = `sha256(jsonEncode([profileId, baseUrl, directory, workspace]))`. Retry checks the original receipt by lookup (`checkQueuedPromptReceipt`); it never resends.

**STATE-DATA-21 Composer drafts per session + attachment vault.**
How: `session_drafts.dart`, `draft_attachments.dart`, `connection/drafts.dart`. Draft text autosaves per `(profile, session)`; attachments: `data:` URLs live as content-addressed files, `file:`/`http(s):` stay as metadata, other schemes refuse. Writes are ordered: files first, then metadata; GC of unreferenced files only after metadata succeeds. Store full (50) refuses new drafts instead of evicting unsent work; leaving a chat with an unsaveable draft tells the user. `SessionDraftStore.withoutProfile` also drops `profileID==''` legacy drafts when any server is removed. Migration of legacy unattributed drafts into Saved prompts (STATE-DATA-23) runs once per profile at bootstrap (`MigrationRunner`).
Tests: `session_draft_test.dart` (14), `draft_attachments_test.dart`.

**STATE-DATA-22 Prompt photo recovery.** `prompt_photos.dart`, `draft_photo_recovery.dart`. One native picker/camera request at a time; the origin (`profileID, sessionID, directory, workspace`) is committed to `oc.pendingPromptPhoto` BEFORE launch so a photo handed back after Android killed the app joins its own conversation's draft (never whichever chat happens to open); max 10 MiB; picker `maxEdge` 2048 px, quality 85. Channel: image_picker + `oc/camera` permissions (Kotlin `MainActivityMedia`, prefs `camera_permissions`).

**STATE-DATA-23 Saved prompts (stash), prompt history, undo.**
How: `prompt_shelf.dart` (store), `saved_prompts_controller.dart` (actions without confirmation dialogs; one-use memory-only Undo `SavedPromptUndo`), `connection/prompt_shelf.dart`, `queued_prompt_move.dart`, `migration_runner.dart`. One key per prompt; 50 per profile; attachments externalized to `prompt-stash-attachments-v1`; legacy inline attachments stay inline and are never truncated; `locationBound` prompts (file URLs or review references) warn when the location differs; unreadable shelf is refused (never silently dropped); a durable owner marker is committed before publishing files so a crash leaves discoverable orphans. Whole shelf operations are serialized across profiles because the disk budget is shared. History `oc.promptHistory.<id>`.
Tests: `stash_attachments_test.dart` (23), `stash_lifecycle_test.dart` (14), `saved_prompts_*`.

**STATE-DATA-24 Read state, unread dot, return brief, shared views.**
How: `connection/read_state.dart`, `session_read_state.dart`, `return_brief_state.dart`, `read_state_consent.dart`. `isSessionUnread` = remote idle watermark > both local watermark and (when sharing) remote `viewed`. Marking read happens only from a visible loaded chat, only for the exact observed idle; local watermark is recorded even if sharing is off or the call fails (no server write queued); sharing writes `viewed` to the server only when consent is on and not mid-save. Return-brief dismissal is stored by `[baseUrl, directory, workspace]`. Sharing default and journal: a profile that existed before the consent store gets the legacy value (missing legacy = ON in old builds; invalid data never grants); a profile re-added under a removed id is readable again; closed profiles refuse reads/writes.

**STATE-DATA-25 First-paint caches.** `session_inventory_cache.dart` (<=100 rows/64 KiB per profile, redacted titles), `session_tail_cache.dart` (12 text excerpts of one most-recent conversation, no tools/reasoning/attachments), `connection/session_cache.dart`, `phone_agents_cache.dart` (feeds), `context_usage_memory.dart`. Principle: cached rows never establish existence, running state, permissions or capabilities; they are replaced by live hydration; a tail excerpt is re-saved when leaving the chat or backgrounding, never while a revert is staged or the session is deleted; scope hashes bind caches to profile+backend+URL+username+folder so a moved server never shows another's rows.

**STATE-DATA-26 Small per-conversation memories.** `conversation_title_memory.dart` (keeps titles after helper records are archived), `chat_notice_memory.dart` (dismissed standing notes, "Auto-approve is on" shows once, folds away, owner 2026-10-27), `AgentTurnFailureMemory` (Paseo forgets failure text on restart; chat otherwise said "No reply came back" over a sign-in problem), `context_usage_memory.dart` (feeds the chats list rings).

**STATE-DATA-27 Staged revert (OpenCode 2).** `connection/revert.dart`. A review belongs to one connection+session+observed boundary; remote stage/clear/commit invalidates it; queue flush refuses while a revert is staged; history is reset when the fingerprint changes. In-memory.

**STATE-DATA-28 Turn stall watch and stop semantics.** `connection/turn_stall.dart`, `domain/turn_stall.dart`. Watches silent turns without blaming the model when a tool runs (a 15-minute test run once read as silence, owner 2026-10-27); stop/resume keeps which turns are open and which tools run; `_openTurnGrace` 3 min, phone-chat observation limit 15 s / poll timeout 4 s (`phone_chat.dart`). In-memory; no persistence.

### C. Notifications, background, launcher surfaces, attention

**STATE-DATA-29 Notification rules.** `notification_preferences.dart` + `connection/monitors.dart`. One shared rule set (quiet hours start/end, Wi-Fi only, check-in minutes, quota alerts) + two "what notifies me" switches (finished runs, requests; default on). Legacy per-server (`oc.notifyRules.<id>`) and per-quota-source (`oc.quotaMonitor.<id>`) records are merged ONCE (`mergeLegacyNotifyRules`; first pair wins, quiet toggle from quota = 22:00-08:00, wifi/quota on if any was on, first check-in) and kept untouched for older builds; until `oc.notify.version==1` the monitors read the legacy records. Quiet hours do not stop requests/failures - they post silently.

**STATE-DATA-30 Profile monitor and attention feed.** `profile_monitor.dart` (1,279 lines), `monitor_attention_reader.dart`, `attention_feed.dart`, `elsewhere_attention.dart`, `connection/attention.dart`. Background polling of saved servers' pending permissions/questions/forms and long-running sessions (check-in reminders once per observed busy interval); persisted: `oc.monitorAlerts.<id>` (L ids already alerted), `oc.monitorRoutes.<id>` (JSON map notification routing), `oc.monitorBusy.<id>` (observed busy intervals - observations, not server truth). "Needs you" across servers + conversations in other projects ("In other projects" on Work). Opt-in per server (`monitorOtherServers` automation behavior).

**STATE-DATA-31 Coding alerts and live status notification.** `connection/alerts.dart`, `surfaces.dart`, `background/notification_*`. Finished-run, failed, needs-input notifications with quick reply (RemoteInput, Kotlin `BackgroundConnectionService`/`CodingActionReceiver`), "Stop the reply" action for exactly the named chat while it still runs, generic tool sentence for the ongoing notification (`toolSentence`: never command, path, query or URL), first line of newest reply fetched with 2 s timeout, titles like "MacBook · api-server", opening a chat clears its finished alert. Content derived from the same truth as the Activity tab. Channel `oc/background`.

**STATE-DATA-32 Launcher surfaces.** `connection/surfaces.dart`, `widgets.dart` (600 lines), `background/widget_snapshot.dart`, `pinned_session_shortcuts.dart`, `attention_tile_snapshot.dart`, `usage_widget_snapshot.dart`. Snapshot publisher writes `oc.widgetSessions` (titles/project/redacted reply excerpt, <=12 sessions) + `oc.widgetTarget.<id>` (opaque routing kept OUT of the snapshot: `fingerprint` only) and asks native to redraw (`oc/background`); pinned shortcuts and the Quick Settings tile count follow the same ownership and deletion rules. Publishing is suspended while connecting, while the lifecycle has suspended the transport (entries stay as last published), and during profile deletion (`_widgetSnapshotSuspended`, otherwise a notify would republish deleted titles). Widget actions run through bounded on-demand connections in separate Dart engines today (`oc/widget_action`, `widget_action_runner.dart`, `widget_status_runner.dart`); in the rewrite they become Kotlin calls.
Privacy: widget shows titles/project names/reply excerpts only - never prompt text, tool input/output, reasoning, file names or server addresses.

**STATE-DATA-33 Background live mode and pause notice.** `background/live_background.dart` (`BackgroundLiveController`, `oc.keepLiveInBackground`), `background_pause_notice.dart`. Foreground service keeps transports; Android 15+ caps `dataSync` foreground services at 6 h per rolling 24 h; the pause receipt is native (`BackgroundPauseStore`); the notice has phases paused/resuming/failed, hidden state stored as `oc.backgroundPauseDismissed` (id of last hidden pause); only a confirmed start clears it.

**STATE-DATA-34 Automation policy and automatic activity.** `automation_policy.dart`, `automatic_activity.dart`, `domain/automation_policy.dart`. Per-profile `AutomationPolicy` (supervision high/balanced/autonomous, autoApprove, autoMergeOnGreen, 31 behavior switches (25 default-on + 6 consent-once): reconnect, retryIntake, reconcileQueuedSends, retryProviderErrors, restartPhoneServer, resumeSetup, recheckEnvironment, pollRestartHealth, thermalRecovery, reduceEffects, chooseDefaults, detectInstallations, preserveDrafts, followOutput, pollOpenPages, boundedPaging, routeReadyWork, wakeStalledPool, restartWorkers, recycleWorkers, holdAtProviderLimit, retryUnconfirmedAnswer, retryTransientTask, fallBackToDirectTask, applyCodePush + consent-once: restartDevServices, stopIdleHelpers, cleanCaches, updateWhenIdle, monitorOtherServers, monitorQuota). Non-consent behaviors default ON for a profile with no stored record; consent behaviors default OFF; corrupt = all OFF; redaction-stable persistence (refuses to save altered semantics). Every automatic act the app CONFIRMED (never attempts) is logged for "While you were away" (bounded, redacted, never feeds Needs you). Native reads the policy for restart decisions.

**STATE-DATA-35 Consent stores.** `in_flow_consent.dart` (consent once, in flow, by kind + `firstStart`), `repeated_permission_consent.dart`, `mobile_download_consent.dart` (voice model downloads on mobile data), `read_state_consent.dart`, `consent_owners.dart` (facade that closes/reopens all of a profile's consent owners during deletion), `first_reply_notify_offer.dart` ("Notify me when the agent needs me?" - asked once after the first reply of a first run; yes = requests notifications + background connection; both answers final; claims the question for the server before showing and saves first). All fail closed on unreadable data and refuse redaction-unstable writes.

**STATE-DATA-36 Teaching, nudges, first run, defaults, chips.** `tips.dart` (max 2 shows, 2-min cooldown, one per screen visit, blocked while a question waits/keyboard covers anchor/onboarding, "Show tips again"), `nudges.dart` (5 one-time cards; one active app-wide; none before the first reply; refused offer is not consumed), `first_run.dart`, `appearance_guide.dart`, `interaction_defaults.dart` (pure defaults policy: explicit > only option > last used > server default; "chosen for you" notice once per kind+profile; memory-based voice pack by physical RAM never `memoryClass`), `phrase_chips.dart` (see 4.2), `reader_preferences.dart`.

**STATE-DATA-37 App preferences (single acknowledged snapshot).** `app_preferences.dart` `AppPreferencesStore` (ChangeNotifier; one per prefs instance; revision counter). Reads once, publishes only after the platform acknowledges; multi-key writes restore attempted keys on refusal; if restore also fails, expose reloaded disk truth while still reporting failure; failed reload keeps confirmed memory and sets `_needsReload`. Snapshot = appearance, themePack, locale, effects (`KitEffects`), textSize, accent, reasoningExpanded, timestampsVisible. Style change resets Fine-tune knobs; Saver overrides theme/effects without altering stored choices. `AppLocaleStore` (device-wide, outside profile deletion), `GlassChoiceStore` (legacy), `settings_access.dart` (read-only Settings facade bound to explicit owners; failure reasons `unsupported, invalidValue, staleTarget, conflict, storageFailed, disposed, offline, needsSignIn, managed, outcomeUnknown`). `connection/settings.dart` publishes accepted choices to every controller.

### D. Agents on the phone, other connections, handoff

**STATE-DATA-38 Agents on this phone (Claude Code/Codex/Kilo/etc. via Paseo in In-app Ubuntu).**
How: `connection/phone_agents*.dart` (1,497 + ~2,000 lines), `domain/phone_agents*.dart`, `builtin/agents/*`, Kotlin `PhoneAgent*`. One host and one auth owner per phone (STATE-DATA-05). Each conversation talks to its own backend (`51f970d6b`): the OpenCode feed and the Paseo agent backend are merged into one Conversations list (`MergedChatFeed`, `ChatFeedSource`). The agent backend is a secondary CC (`<phoneId>.agents`) created on first use, kept connected while the helper runs, health-watched every 10 s, retried (3 s for 30 s then 15 s), recovers when Android stops the helper. Persisted: feed cache (<=200 rows), `phoneAgentsUsed`, signed-out map (list says "signed out" from first paint), per-profile default agent/model, gates, install marker, host secret. Sign-in state, certification cells, capability refresh after first handshake, phone check with 15 s/65 s limits, startup window 30 s with 3 s polling (`phone_agents_startup.dart`), idle admission hooks owned by native (`phone_agents_idle.dart`), saved rows before live (`phone_agents_saved.dart`), exact-identity routing of rows/cards/actions (`phone_agents_routes.dart`).
Must keep: opening a chat must not move it to the top as "Done - Just now" (`b4339921c`); a signed-in Claude Code is never sent to sign in (`5e161bbea`); a long silent tool call is not "Claude Code not answering"; "Claude Code stopped" is not flashed while the app restarts the helper itself; archive older helper copies of one conversation but keep its title (`23132e849`).

**STATE-DATA-39 Side connections (other saved servers in the one Conversations list).** `connection/side_connections.dart`. Eligible: OpenCode profiles, readable, not the same server as main (compare scheme/host/port). Default shown = on-this-phone servers (`BuiltinLinux`/loopback); stored override `oc.chatListShown.<id>`; saved rows `oc.sideFeed.<id>` paint at once; each side is a secondary CC that never becomes the active profile and never owns profile-wide services; edited profile reconnects (signature compare); In-app Ubuntu sides are started once.

**STATE-DATA-40 Continue with... / handoff links / session links.** `continue_with.dart`, `continue_with_connection.dart`, `agent_handoff_controller.dart`, `agent_handoff_links.dart`, `review_handoff.dart`, `session_address_controller.dart`, `session_link_bindings.dart`, `connection/agent_handoff.dart`. Moves a conversation to another agent (handoff file path + link between source and target sessions, navigation metadata only), opens `opencode-mobile://session` links after verifying the installation binding (origin + instanceId + verifiedAt); links removed with either profile.

### E. Generated UI, MCP, integrations

**STATE-DATA-41 Generated UI state.** `gen_ui_state.dart` (1,121 lines), `gen_ui_journal.dart`, `gen_ui_coverage.dart`, `connection/gen_ui.dart` (958 lines), `domain/genui/*`, `builtin/agents/gen_ui_*`. Cards come from tool results in message history; answers are dispatched with a persisted journal (routing/correlation only, never content), delayed Undo window (`DelayedAnswers`), recovery pass after restart (350 ms / 2 s timers), coverage so history recovery is bounded, per-server on/off (`oc.genui.enabled.<id>`).

**STATE-DATA-42 MCP catalog/connector search, integration sign-in and commands.** `mcp_catalog_host.dart`, `mcp_chat_controller.dart`, `mcp_connector_search_bridge.dart`, `setup_registry_store.dart`, `connection/connector_search.dart` (catalogue max age 24 h, search online budget 4 s), `integration_auth.dart` (OAuth starts, pending auth recovery with `PendingAuthStore`, local uncertainty markers "never evidence the server did not start"), `integration_commands.dart` (executable authentication requires explicit confirmation; identical concurrent taps share a launch; never retains command text, URLs, messages, raw errors). Registry browsing is OFF by default and never follows entry URLs.

**STATE-DATA-43 Worktrees and isolated tasks.** `connection/worktrees.dart`, `isolated_task_launch.dart`, `managed_move.dart`. "New task in a fresh worktree": subscribes to events BEFORE the create call, 45 s readiness timeout, requires `ServerCapabilities.worktreeCreate`, the launch is the only route into the session.

### F. AI Team, usage, setup, services

**STATE-DATA-44 AI Team orchestration store and controllers.** `orchestration.dart` (664), `orchestration/{types,cycles,mutations,stream}.dart`, `orchestration_store.dart`, `mutation_store.dart`, `team_*` (13 files), `phone_project_engine.dart`, `phone_team_setup.dart`, `connection/team.dart`, `connection/phone_chat.dart`. Keys `oc.orchestration.<id>.*` (snapshot, cursor, lastKnown, refreshedAt, discoveryDismissed [30-day TTL], workView.<run>, planningDismissed, phoneOfferDismissed, celebrated, mutations.<key>), `oc.teamModel/teamRoles/teamTaskRoles/teamWorkerStart/teamWorkspace/teamEditorDrafts.<id>`, `oc.teamEngineDeleted.<id>` (tombstone that blocks native resurrection), `oc.teamMigrationKept.<id>`. Mutations carry idempotency keys and receipts; "unproven phone team reads Off". Phone project engine = native Rust engine in `oc.teamEngine.<id>/` with bearer `oc.teamEngineAuth.<id>`. Team progress notification re-posted every 10 min inside native's 20-min self-timeout.

**STATE-DATA-45 Usage and quota.** `usage_subscriptions_controller.dart` (670), `usage_subscription_sources.dart`, `usage_subscription_alerts.dart` (45 min max age, 12 h reset window), `provider_quota_monitor.dart` (foreground 5 min, background 15 min, 10 s timeout), `paseo_usage_monitor.dart`, `computer_usage_source.dart`, `usage_overview.dart`, `usage_budgets.dart`, `budget_persistence.dart`. Keys `oc.usageAlerts.v1`, `oc.usageAlertSeen.v1`, `oc.usageLast.<connectionId>`, `oc.consumptionBudgets.<id>`, `oc.paseoUsage.<id>`, `oc.quotaMonitor.<id>`, flags `oc.quotaCollector.<id>`, `oc.usageComputerReader.<id>`; widget keys in 4.6. Usage monitor leaves no timer without opt-in. The collector runs in In-app Ubuntu (`tool/quota/collector.mjs`, read over loopback with `oc.quotaToken.<id>`).

**STATE-DATA-46 Phone setup engine state.** `setup_controller.dart`, `setup_session.dart`, `setup_audit_store.dart`, `setup_registry_store.dart`, `termux_host_setup.dart`, `phone_host.dart`, `local_server_controls.dart`, `local_agent_server.dart`, `in_app_auto_update.dart`, `phone_agent_host_port.dart`. Verifiable-transaction setup with audit history; pinned OpenCode runtime auto-update only when nothing runs (`shouldAutoUpdateInApp`: one try per run and pinned version). Job state itself lives on disk in Linux (`builtin/setup/*`, Kotlin `SetupJob`).

**STATE-DATA-47 Development services.** `development_services.dart`, `development_service_store.dart`, `detected_dev_servers.dart`, `dev_server_scan.dart`, `project_dev_server.dart`: saved configs + ownership receipts (<=20), restart behavior gated by `restartDevServices` consent.

**STATE-DATA-48 External A2A agents.** `external_agents.dart`: registry `oc.externalAgents` (<=20, <=250 KB), tasks `oc.a2aTasks.<agentId>` (<=30, <=4 MiB), bearer in secure `oc.a2aBearer.<agentId>`; deletion tombstones survive a failed delete so credentials are removed on retry.

**STATE-DATA-49 GitHub account for Git page.** `github_account.dart`, `git_page_controller.dart`, `git_workspace.dart`: device-flow sign-in (client id public, no secret), token JSON in secure `oc.githubToken.<id>`, handed to `git push/pull` in that profile's environment; registered with the redactor on every read/write.

**STATE-DATA-50 BYO VPS host (SSH adoption; off by default).** `byo_host_store.dart`, `byo_host_service.dart`, `byo_host_controller.dart`, `host/byo_host_ssh_runner.dart`, Kotlin `ByoHostSigner` (AndroidKeyStore EC key alias `oc.byoHostSsh.<id>`, channel `oc/byo_host_signer`). Record `oc.byoHost.<id>` (schema 1: profileId, target, hostKey, phase, hostId, bundleVersion, openCodeVersion, remotePort, revoked) + secure envelope. Ordered operations; plugin exceptions never leak (may contain credentials); deletion verifies absence and retries safely.

**STATE-DATA-51 FactsBook node pairing.** `factsbook/*` (9 files): pairing link `oc.factsbookNode.<id>` (prefs), token/owner seed/outbox in secure storage; developer flag `oc.dev.factsbook`; node passphrase `oc.factsbook-node.passphrase`; "token first, so a record never exists without it".

### G. Phone runtime markers

**STATE-DATA-52 Built-in server owner/recovery/stop markers.** `builtin_server_owner.dart` (`oc.builtinServerOwner`, serial writer with tombstone ordering on deletion), `builtin/builtin_server_recovery.dart` (`oc.builtinRecovery.<id>`, max 3 attempts, 15 s retry delay, steady check 45 s, recovering 5 s; Kotlin keeps its own budget/receipts in `builtin_server_recovery` prefs), `builtin/deliberate_stop.dart`, `termux/managed_server_recovery.dart`, `termux_migration_owner.dart`, `termux_running_server.dart`, `builtin/thermal_guard.dart` (`oc.thermalGuard` default on, holds `oc.thermalPause.<id>`), `builtin/reply_watch.dart` (`oc.replySpeed.inApp`), `linux_reset_flow.dart` (`oc.linuxReset.pending` phases erasing/installing, removes `oc.replySpeed.inApp`), `shared_project_roots.dart`, `tailscale_address.dart` (adds HTTPS to a reviewed bare hostname; never upgrades an explicit HTTP address or infers a hostname is private), `lifecycle_report.dart` (post-exit readiness report: observed now, never inferred from Android's historical exit record; one-line recovery notice with no native description, server response, profile or credentials; `everythingBack` means health checks answer, not that interrupted agent tasks resumed; tests `lifecycle_report_test.dart`, 15 cases).

**STATE-DATA-53 Scheduled prompts (Kotlin-owned store).** Dart `lib/scheduling/*` talks to Kotlin `ScheduledPrompt*` (channel `oc/scheduled_prompts`: `readStore`, `casStore`). Versioned envelope at `oc.scheduledPrompts.<id>` (<=100 schedules, <=500 runs, <=4 MiB), permanent occurrence claims, receipts, phone lease, deletion tombstone `oc.scheduleDeleted.<id>`; native rejects writes after the owning profile is removed. `ensureInteractiveSendAllowed` refuses an interactive send during a temporary phone run (`activePhoneLease`). Deletion calls `cancelProfile`; an aborted deletion calls `restoreProfile`.

**STATE-DATA-54 Diagnostics and reports.** `diagnostics/*` (`report_problem.json`, `crash-diagnostics.json`, perf trace), `oc.draft.<target>.app` for the report text. Technical error text is kept under "Details" and in the problem report, never as copy (no raw errors rule).

**STATE-DATA-55 Voice settings.** `voice/model_manager.dart` etc. (other agent's area) store `voice.*` keys and `voice_models/`; `mobile_download_consent` gates downloads on mobile data.


---------------------------------------------------------------------------------------------------

## 7. State lifecycles (sequences a rewrite must reproduce)

**7.1 Cold start.** (1) Native window paints; Flutter shows "opening" immediately. (2) Prefs + Keystore load (`ProfileStore.load`, secrets in batches of 4). (3) CC built. (4) `migrateOlderDrafts` -> `recoverPendingPhoto` -> `migrateNotificationPreferences`. (5) Shell shows; first-run detection (`FirstRun.observeServers`: no saved servers + no state => `armed` new device; a build upgraded over saved servers is `done`, never walked through the welcome). (6) Active profile connects (STATE-DATA-10), cached inventory/tail/feeds paint first (STATE-DATA-25), live data replaces them. (7) Services start for the main CC only (`_ownsProfileServices`): monitors, background live, widgets, orchestration, side connections, quota/usage monitors, thermal guard, built-in recovery.

**7.2 Connect.** validate (cleartext consent / pin / URL / token) -> generation++ -> retire old transport -> build gateway pair -> restore saved location (without waiting for the project list; revalidate later) -> event streams -> hydrate sessions, statuses, permissions, questions, forms, inbox, catalog (events are not replayed: refetch after reconnect) -> sync services -> flush offline queue (only when connected + policy allows). Failure classes: bad URL/consent (immediate `lastError`), password rejected (`passwordRejected`), wrong flavor (re-probe once), migration required, unreachable (reconnecting/quiet rules).

**7.3 Background / foreground.** `suspendForLifecycle`: flush Undo-pending answers, retire transport, keep data. Background live mode (foreground service) may keep the transport; alerts exist only while live mode runs, and replies go through that live transport. `resumeFromLifecycle`: rebuild one transport; foreground actions wait for transport readiness, not data reload; reconciliation refetches (no replay); quiet period 4 s.

**7.4 Sending a prompt online.** hold marker (persist) -> `ensureInteractiveSendAllowed` (schedule lease) -> `canDispatchOnlinePrompt` (deletion fence) -> network write (with receipt where supported) -> `finishOnlinePrompt` (accepted => remove marker; refusal before send => remove marker; unknown => leave for review) -> composer cleared only after the marker exists.

**7.5 Offline compose -> reconnect.** `queuePrompt` (snapshot model/agent/variant at send time) -> connection returns -> `flushOfflineQueue` (7.4 semantics per entry, oldest first, active profile only) -> `offlineFlushRevision++` with `lastFlushedPromptCount`, `lastFlushSkippedForOtherProfiles` for a one-shot "Sent N queued prompts" confirmation.

**7.6 Draft lifecycle.** type -> autosave (`SessionDraftStore`, attachments vault) -> navigate away/restart -> restore (sameLocation check for file-URL attachments) -> send/delete clears. Photo: origin committed -> camera -> app may die -> `recoverPendingPhoto` on next start joins the origin chat's draft.

**7.7 Switching project/location.** `setLocation` (acknowledged write) + `_rememberLocation` (recents <=8) -> bump `locationRevision` (all in-flight callbacks compare it and drop stale results) -> clear location data -> reload sessions/permissions/questions for the new scope; caches keyed by scope hash.

**7.8 Model selection.** Pick in chat -> `selectModelForSession` (serialized per session `_selectionMutations`; confirmation required for shared state) -> persist per-session choice -> remember display model/name -> update recents. Default pick -> `ProfileStore.setModel(explicit:true)`. Cycling uses favorites/recents order.

**7.9 Permission request.** event (or hydration) -> effective approval mode (session > ancestor with inherit > server-wide) -> automatic `once` reply (record bounded) or surface in chat/list/notification (single claimed answer slot) -> delayed-answer Undo window (`DelayedAnswers`, flushed on suspend) -> reply.

**7.10 Server removal.** Section 5.

**7.11 Linux reset / phone server removal.** `LinuxResetFlow` marks `oc.linuxReset.pending` (`erasing` -> `installing`), erases Linux, forgets what belonged to the old Linux (`afterErase`, returns plain-word leftovers), removes `oc.replySpeed.inApp`, reinstalls; `deleteProfileAndLocalData(linuxGone:true)` tolerates failures of agent/quota/phone-engine cleanup because their services lived in the erased Linux (`9dc308ffe`: the reset really forgets "This phone" even while it is the active connection).

**7.12 Termux -> In-app Ubuntu migration.** `domain/termux_migration_service.dart`, `builtin/migration/*`, `termux_migration_owner.dart`: one-time offer per source profile (`oc.termuxMigrationOffer.<id>`), job state `oc.termuxMigration.<id>`, archives in `filesDir/migrations/<job>`; orphan archives cleaned on launch; profile switcher moves the active profile to the new one.

### Timers and thresholds worth porting verbatim

| Where | Value |
|---|---|
| Bootstrap "still opening" | 8 s |
| Quiet resume grace / quiet drop notice | 4 s / 15 s |
| Local server alive-but-busy "long" | 2 min; agent command hold-off 10 min |
| Polling fallback (sessions/busy status) | 5 s |
| Turn run check freshness / timeout | 20 s / 8 s |
| Catalog fresh / reuse window | 10 min / 60 s |
| Folder probe listing timeout | 8 s |
| Managed OC2 migration confirm | poll 1 s up to 3 min |
| Open-turn grace / phone chat observation / poll timeout | 3 min / 15 s / 4 s |
| Chat feed | 4 pages x 50, 120-char preview, 2 s debounce |
| Phone agent backend watch / list refresh | 10 s / 2 s; startup window 30 s with 3 s polling; retry 3 s for 30 s then 15 s |
| Connector catalogue max age / online search budget | 24 h / 4 s |
| Quota monitor | foreground 5 min, background 15 min, timeout 10 s |
| Usage alerts | max age 45 min, reset window 12 h |
| Team progress re-post | 10 min (native self-timeout 20 min) |
| Built-in recovery | max 3 attempts, retry 15 s, steady 45 s, recovering 5 s |
| Worktree readiness | 45 s |
| Nudge/tip cooldown | tips 2 min; max 2 shows |
| Discovery offer dismissal TTL | 30 days |
| Queue limits | 14 days, 50 entries, 20 MiB/entry, 60 MiB total |
| Phrase chips | 2 sends, half-life 14 days, 4 chips, 50 entries, 40 chars |

---------------------------------------------------------------------------------------------------

## 8. Hard-won behaviors a rewrite must not lose (with evidence)

Commit ids are in `/home/eslam/Storage/Code/wt/oc_app-batch1` (`git log --oneline -- lib/state/...`).

1. **Never duplicate or lose a prompt** (`f29f47634` preserve uncertain sends without automatic replay; `e91f20e02` bound the offline queue and give the user a way out; `dfdd4ac8f` preserve saved input and expose confirmed corrupt-data recovery; `3ee0ddd45` guard interactive sends during temporary phone runs). Marker-before-send, removal-after-accept, review on unknown, corrupt blob fails closed, explicit empty save is the only recovery.
2. **Profile deletion fails closed and says so** (`9d9099cf7`, `e957246a3`, `4595cbce3`, `7c6d009c5` stop and drain shared writers before deletion, `856bde79b` read native schedule keys late and mock the channel, `cf060261a` a server added again under a removed id is readable again). Sweep-by-key-shape; draining writers before key discovery; stash before generic sweep.
3. **Secrets never reach logs/UI/diagnostics** (`509c8cd0d` register credentials at trusted ingress before capture). Register every secret on read/write *before* persistence; stores refuse to persist redaction-unstable content (consents, automation policy, handoff links store `KitRedact.text(...)`).
4. **Keyring failures are named, not raw** (`e2346919d` name keyring failures; `SecureStorageUnavailable`); an unreadable Keystore entry keeps the profile and flags re-entry instead of failing startup.
5. **Acknowledged-before-published preferences** (`005071bde` share controller preferences and pin privacy consent; `5b115eed4` publish acknowledged shared app preferences): SharedPreferences updates its cache before the platform write is confirmed, so after any refused write call `reload()`; never promote the optimistic cache to saved truth; use `commit()` semantics, not `apply()`.
6. **Per-session model/agent never leak** (`84f4de91c` scope the chat model picker to the session it was opened from; `862d22140` keep OC1 agent choices within their chat; `377b52401` model names remembered per server).
7. **Saved location restored without trusting the catalog** (`e2346919d` restore the last directory; `f09318464` persist project location per server): absence from the project list never switches projects.
8. **First paint from caches without lying** (`090f3a9d5` cache opening previews and overlap inventory refresh; `a741a07ac` save the opening excerpt again when leaving; `fe7052de0` keep inventory refresh bounded during event bursts; `ddd23e17d` load conversation inventory independently of Home).
9. **Honest status** (`3ea4a9381` honest stop lines, no false "connection dropped"; `8a74c21f4` quiet status rows, no false stall; `4208e6108` a busy phone server is not a dead one; `a0000cff4` a running command is activity, not silence; `6cd86fa36` keep short agent link drops quiet; `f0420edee`/`58343f841` Stopped vs Done, finished OC2 run no longer Working).
10. **Notification privacy and routing** (`9c29c77de` needs-you text names the chat; shell permissions ask "Run this command?"; `a1126fbd6` pass moment fields through the channel, keep a plan that beats the busy poll; `3541d1c7e` localize native notifications and follow app language - native reads `flutter.oc.appLocale`).
11. **Widgets/surfaces** (`883027d8b` widget actions through bounded on-demand connections; `f1f1a5789` keep widget connection targets out of snapshots; `513ac2838` widget reads conversations without moving the list's first paint; `a5fc8ffb3` conversations widget lists every project and connection; `ac04fe1e6` widget shows model display name, never a raw id).
12. **Paseo/agent specifics** (`ecf51a4f5` helper still starting is read every 5 s until conversations arrive; `f09ed2723` one retry timer; `143591096` list paints once, saved Claude rows show at once; `1f8a87c52` preserve phone agent ownership across protocol switches; `fa032af2e` ignore empty phone gates during owner migration; `fa80b4497` preserve legacy phone checks; `ecc159ef2` retain removal for partial payloads across restart; `9ab55c317` drain sign-in foreground leases before owner deletion).
13. **Draft/attachment durability** (`b4e4b7b87` freeze attachment snapshots and order durable writes; `e2a30576d` recover composer attachments from durable local drafts; `9b1ecb1e9` preserve server-scoped drafts and expose recoverable save failures; photo downscale to 2048 px after "WebSocket inbound queue overflow").
14. **Credential batching** (`9244e5666` overlap independent profile credential reads; keep fan-out bounded - 4).
15. **Cross-process/engine safety** (`ScheduledPromptStore` compare-and-set across isolates/processes; native rejects writes after profile removal; claims are permanent tombstones and are never derived from bounded history).
16. **Consent/default semantics** (`a905090a7` per-profile consent + resumable migration; `61e8e3a5d` "Use my default" restores read sharing to the server's own starting value and says what it reset; `cf060261a`).
17. **Retired keys are removed on load, not trusted** (`oc.budgets.*`, liquid-glass counters, old live-background on/off).
18. **Local-only first-run rules** (a build upgraded over saved servers is never welcomed; notify question only after the first reply).

---------------------------------------------------------------------------------------------------

## 9. Performance notes

- Startup cost today: `SharedPreferences.getInstance()` loads the entire XML (including `oc.offlineQueue` up to 60 MiB and promptStash inline attachments); `OfflineQueueStore` docs call this out. Mitigation in the rewrite: keep prefs tiny and put blobs in files/DB.
- Keystore reads are slow and batched (4 at a time) before the shell is exposed; a profile list of 5+ servers delays first frame. Prefer lazy secret reads at connect, but keep "re-enter" detection correct.
- Hot paths use memoization: `SessionPinStore.scope` memo (membership checks run thousands of times per build), `SessionInventoryCache._decoded`, `SessionTailCache._decoded`, `PhraseChipStore._cachedRaw`, ordering snapshots for the sessions list (identity comparison, status-only notifications keep the list). Port these as immutable snapshots compared by identity/revision.
- First paint budget: native demo cold start ~0.25 s vs Flutter ~0.4 s; the state layer's first-paint path = inventory + tail caches (<=64 KiB + <=100 KB) + saved feeds. Keep reads synchronous from memory after one prefs load.
- Event bursts: inventory refresh is bounded (`fe7052de0`); chat feed debounces 2 s; widget/shortcut/tile writes are coalesced by comparing payload strings (`_lastWritten`, `_prefs.getString(key) == encoded` checks before every write).
- Writers: avoid rewriting unchanged values (almost every store compares the encoded string before `setString`).
- `Expando`-based per-prefs singletons must become DI singletons; the `reload()` pattern should become a single suspend `ensureFresh()` only where another process can write (Kotlin workers).
- Draft vault reads are streaming with expected-size guards (a length check then `readAsBytes` is not a bound).
- Disk budgets: draft vault 256 MiB total, stash same, photo 10 MiB each; GC after metadata commit only.

---------------------------------------------------------------------------------------------------

## 10. Risks

1. **Secret migration is the only irreversible step.** Undecryptable entries (9.x->11.x jump, lock-screen/security changes, device restore) must degrade to re-entry per profile, never abort. Verify read-back before deleting anything; do not delete old secure files in the migration release.
2. **Strict JSON parsing would brick data.** The Dart parsers coerce (`value['id']?.toString()`, `(x as num?)?.toInt()`, unknown keys ignored, missing -> defaults). A strict kotlinx-serialization model would treat legitimate old data as corrupt, and the *fail-closed* behavior (queue unreadable => nothing sends, drafts refused, command journal blocks admission) would then lock users out. Use lenient `JsonElement` parsing that mirrors each Dart `fromJson` and port the corresponding tests first.
3. **Native readers of `flutter.*` keys** (4.6). If prefs move, every reader must change in the same release; otherwise widgets/tile/shortcuts/scheduler/recovery silently go blank or, worse, scheduled prompts stop firing.
4. **`apply()` vs `commit()`.** Flutter's legacy plugin uses `commit()` and callers branch on the returned bool (fail-closed). Kotlin code that uses `apply()` silently loses this property.
5. **Deleting by key shape** is a feature; table-based stores need an explicit, tested "owned data" registry per profile, plus the same verification and retention rules (tombstones, stash-before-sweep).
6. **Dispatch markers must survive the migration byte-for-byte.** Do not "tidy" `oc.offlineQueue` during migration (no eviction, no marker clearing, no re-enqueue). Apply limits only on the next normal read as today.
7. **Absent vs false vs corrupt** have different meanings in several stores (automation policy, read-state sharing default, notify rules `migrated`, consent). Preserve three-state reads.
8. **Time formats are mixed**: epoch ms ints (queue, drafts, read watermarks, return-brief/phrase-chip counters), ISO-8601 UTC strings (orchestration `enabledAt`, nudges, tips), fractional seconds none. Keep as stored.
9. **Hidden dependencies on `KitRedact`** (411 lines, lib/ui/kit/kit_redact.dart; used by ~30 state files): redaction patterns (provider keys, bearer, headers, `key=value`, URL userinfo, known exact secrets) must be ported and tested before any store, since stores refuse unstable writes.
10. **Dual engines in the old app disappear** (widget action runner, scheduled-prompt entrypoint, overlay): their prefs races are gone, but their semantic guarantees (bounded on-demand connections, lease, idempotent claims) must be re-expressed as Kotlin concurrency.
11. **Profile ids**: numeric strings generated as `microsecondsSinceEpoch`; some flows use UUIDs; the fixed built-in id re-use is explicitly supported. The id doubles as a path component in `oc.teamEngine.<id>` and in Keystore alias `oc.byoHostSsh.<id>` (`[A-Za-z0-9_-]{1,64}` there).
12. **Oversized/dangling data**: `oc.sessionDrafts` legacy `profileID==''` rows exist on long-lived installs; deletion removes them with any server. Keep that behavior or migrate them (STATE-DATA-21/23).
13. **Preview flavor** installs side-by-side with a different package; a native build must keep `applicationIdSuffix` semantics if previews remain.
14. **Scheduled-prompt envelope** is Kotlin-owned but its JSON is also parsed in Dart models (`lib/scheduling/models.dart`); migrating UI must reuse the Kotlin parser, not reimplement.
15. **Android auto-backup disabled** and `fullBackupContent` excludes everything: device-to-device restore yields prefs-less installs with Keystore keys gone - the new app must treat "no profiles" as a clean first run, never as migration failure.

---------------------------------------------------------------------------------------------------

## 11. Tests to port first (from the owner's catalog, area `state-data`, 159 behavior rows)

Highest-leverage Flutter test files for this area (counts are catalog rows): `usage_subscriptions_test` 27, `stash_attachments_test` 23, `offline_queue_test` 20, `profile_deletion_test` 19, `pending_command_journal_test` 16, `lifecycle_report_test` 15, `report_problem_test` 15, `session_draft_test` 14, `stash_lifecycle_test` 14, `usage_subscriptions_controller_test` 14, `scheduled_prompt_store_test` 13, `agent_handoff_links_test` 12, `offline_queue_concurrency_test` 12, `queued_prompt_removal_wiring_test` 11, `scheduled_prompt_models_test` 10, `session_selection_sync_test` 10, `settings_access_test` 10, then `automatic_activity_test`, `mobile_download_consent_test`, `offline_queue_unconfirmed_test`, `profile_sign_in_reset_test`, `session_inventory_paging_test`, `session_link_bindings_test`, `app_preferences_store_test`, `kit/kit_draft_manifest_test`, `phone_project_engine_controller_test`, `session_auto_approval_store_test`, `settings_scope_domain_test`, `setup_audit_store_test`, `setup_registry_test`, `command_receipts_test`, `profile_store_test`, `oc1_profile_upgrade_test`, `notification_preferences_test`, `settings_scope_controller_test`.
Migration rows to port as migration tests: notification-rules merge, OC1 profile upgrade flags, read-state consent migration; add new ones for secure-storage decrypt, both string-list encodings, double prefix, marker preservation, and idempotent re-run.
Catalog files: `/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl` (filter `"area":"state-data"`).
Writing order that de-risks the rewrite: (1) lenient JSON + redactor, (2) KV store with `commit` semantics + writer-lane/`closeProfile` primitive, (3) profile store + sweep, (4) offline queue + draft stores, (5) migration reader, (6) CC split.

---------------------------------------------------------------------------------------------------

## 12. Packages used by this area -> native/KMP replacement

| Dart package | Use here | Replacement (suggested) |
|---|---|---|
| `shared_preferences` 2.5.5 | all prefs | KMP `KeyValueStore` interface; Android actual = same `FlutterSharedPreferences` file (day 1) or DataStore (`datastore-preferences-core` 1.2.x supports Android/iOS/JVM per developer.android.com KMP guide; verify version at build time) |
| `flutter_secure_storage` 11.2.0 | secrets | `SecretStore` expect/actual: Android Keystore AES-GCM (non-exportable) + file/prefs envelope; iOS Keychain; Linux Secret Service/libsecret (today: keyring probe message). Avoid `androidx.security:security-crypto` (deprecated). Tink optional. |
| `path_provider` 2.1.6 | support dir (= `filesDir`) | `Context.filesDir` / expect-actual app-data dir |
| `crypto` 3.0.7 | SHA-256 for scopes, vault ids, digests | Okio `ByteString.sha256()` or kotlinx-io/`java.security` actual |
| `clock` | testable now | `kotlinx-datetime` `Clock` injectable |
| `flutter_riverpod` 3.4 | exposes CC/bootstrap | ViewModels + `StateFlow`; DI singleton for stores |
| `uuid`, `intl` | ids, date formatting | kotlin `Uuid`, `kotlinx-datetime` + ICU on Android |
| `image_picker` 1.2.3 | prompt photos (maxEdge 2048, quality 85) | Photo Picker / `ActivityResultContracts.TakePicture` + downscale |
| `dio` | provider logos | Ktor |
| `package_info_plus`, `shorebird_code_push` | version/update notice | `PackageManager`; in-app update notice stays (no persisted state) |
| `window_manager`, `screen_retriever` | desktop window state key | desktop actual later |
| `local_auth` | FactsBook device authenticator | `BiometricPrompt` |

`ChangeNotifier` + `Expando` singletons -> `StateFlow`/`SharedFlow` + DI; Dart `Future` writer lanes -> coroutine `Mutex`/single-thread dispatcher per profile; `Timer` -> coroutines with injected `TestScope` clocks.

---------------------------------------------------------------------------------------------------

## 13. Native bridges touching this area

| Channel | Kotlin class | Used for (state-data) |
|---|---|---|
| `oc/background` | `MainActivityBackgroundChannel` (+ `BackgroundConnectionService`, `WidgetV2Bridge`) | live mode on/off, widget/tile/shortcut redraw, status notification content, pause receipt |
| `oc/scheduled_prompts` | `ScheduledPromptChannel` + `ScheduledPromptStore/Worker/...` | `readStore`/`casStore`, `cancelProfile`, `restoreProfile`, `activePhoneLease` |
| `io.github.eslamasabry.opencode_mobile/builtin_linux` | `MainActivity.kt:592` -> `BuiltinLinux` | shared project roots push, recovery bind/unbind, phone-engine deletion, native idle admission |
| `oc/byo_host_signer` | `ByoHostSigner` | Keystore EC identity (create/sign/delete/deleteAll) |
| `oc/widget_action` (own Dart engine) | `WidgetActionRuntime` | widget actions run through bounded on-demand connections |
| `oc/widgets` | `WidgetOverlayActivity` | floating widget host (style/steer/voice panels) |
| `oc/shortcut` | `LaunchShortcutChannel` (+ `PinnedSessionShortcuts`) | launcher shortcuts |
| `oc/link`, `oc/share` | `LaunchLinkChannel`, `LaunchShareChannel` | `opencode-mobile://session` links, shared text |
| `oc/camera`, `oc/voice` | `MainActivityMedia` | permission memory prefs `camera_permissions`, `voice_permissions` |
| `oc/storage`, `oc/project_export` | `StorageAccess`, `ProjectExportBridge` | shared-storage folders, project zip export before "Clear storage" (`ManageSpaceActivity` guards it) |
| `oc/termux`, `oc/tailscale` | `MainActivity.kt` (Termux bridge), `TailscaleHandoff` | Termux managed servers, Tailscale handoff |
| `oc/lifecycle`, `oc/network` (+`/events`), `oc/thermal` (+`/events`) | `AppLifecycle`, `NetworkMonitor`, `ThermalMonitor` | exit history, connectivity, heat |
| `oc/crash_diagnostics` | `CrashDiagnosticsBridge`, `NativeCrashStore` | last-crash record |
| `oc/read-aloud`, `oc/local_pdf`, `.../local_terminal` | `ReadAloudBridge`, `LocalPdfBridge`, `LocalTerminal` | not state-data (listed for completeness) |

---------------------------------------------------------------------------------------------------

## 14. Suggested shape for the native state layer (for the architect; not a decision)

- `core-data` (KMP commonMain): `KeyValueStore` (typed get/put/remove/keys, `commit` result, `reload`), `SecretStore`, `BlobStore` (content-addressed vault with owner sharding), `ProfileScope` registry (`ownedBy(profileId): Set<Key>` + verified delete), `WriterLane` (serial lane + `close()` that stops admission then drains), `Redactor`.
- Per-feature repositories mirroring the stores in 4.x (one per STATE-DATA id above), each exposing `Flow` + suspend writes that return only after acknowledgement and each registering its keys with `ProfileScope`.
- `ConnectionController` split by id: `ConnectionSession` (10-12), `Location` (13), `SessionList` (14), `Selection` (15), `Approvals` (16-17), `PromptDelivery` (18-20, queue + receipts + online marker), `Composer` (21-23), `ReadState` (24), `Caches` (25-26), `Alerts/Surfaces` (29-33), `Agents` (38-40). Keep the "generation + identity re-check after every await" rule (use a `Job` per generation).
- Android actuals first; iOS/Linux actuals later behind the same interfaces.
- Day-1 `Migrator` as a separate module run before DI wiring (section 3).

---------------------------------------------------------------------------------------------------

## 15. Open questions / decisions for the owner

1. Keep `FlutterSharedPreferences` as the physical store on day 1 (recommended) or copy to DataStore/Room now? (Affects every Kotlin reader in 4.6.)
2. Move big blobs (`oc.offlineQueue`, stash inline attachments, `oc.sessionDrafts` index) out of prefs in the migration release or a later one?
3. Should FactsBook secrets and `oc.a2aBearer.*` join the "reset saved sign-ins" sweep (they are excluded today)?
4. Is an OpenCode-1-era profile (`requiresOpenCode2Migration`) still reachable by any user? (Owner: only the owner uses the app; if their phone has none, the migration blocker UI can be dropped but the booleans must still parse.)
5. Legacy `profileID==''` drafts, `oc.keptQueuedPrompts`, `oc.olderDraftsBackupV1.*`, `oc.glass`, `oc.effectsDepth/Edges/GlowColours/GlowSpeed` (written but ignored): migrate, ignore, or delete?
6. Retain `oc.shareSessionViews*` default behavior (legacy missing => on) or force a fresh explicit choice after the switch?
7. Delete the Flutter-era data after N native releases? (Recommend: not in the migration release.)

---------------------------------------------------------------------------------------------------

## 16. Evidence and method

Read in full or in substantial part: `profiles.dart` + 4 part files, `connection.dart`, `connection/deletion.dart`, `queue.dart` (flush), `locations.dart`, `models.dart`, `read_state.dart`, `session_cache.dart`, `phone_agents_cache.dart`, `side_connections.dart`, `offline_queue.dart`, `session_drafts.dart`, `draft_attachments.dart`, `prompt_shelf.dart`, `prompt_photos.dart`, `pending_command_journal.dart`, `pending_auth.dart`, `app_preferences.dart`, `notification_preferences.dart`, `nudges.dart`, `tips.dart`, `first_run.dart`, `phrase_chips.dart`, `interaction_defaults.dart`, `automation_policy.dart` (+domain), consent stores, `session_*` stores, `migration_runner.dart`, `byo_host_store.dart`, `github_account.dart`, `external_agents.dart`, `orchestration_store.dart` key section, bootstrap gate. Doc-level skim only (no deep read): `phone_agents.dart` (1,497), `gen_ui*.dart`, `integration_*.dart`, `catalog.dart`, `permissions.dart`, `alerts.dart`, `profile_monitor.dart`, `usage_*`, `team_*`, `factsbook/*`, `setup_*`, `termux_*` - their formats in section 4 come from key declarations and class docs, so a feature owner must read the file before porting its parser. Not covered here (other areas): `lib/api2`, `lib/domain` models, `lib/ui`, `lib/builtin`, Kotlin internals beyond the key readers in 4.6.
Package facts verified in `~/.pub-cache`: flutter_secure_storage 11.2.0 (ciphers, names, prefixes), shared_preferences_android 2.4.27 (`flutter.` prefix handled in Dart; list/double prefixes). Web checks (2026-11-01): `androidx.security:security-crypto` is deprecated (Tink/DataStore/Keystore recommended); DataStore KMP guide lists `datastore-core`/`datastore-preferences-core` 1.2.1 for Android/iOS/JVM - "stable" status not confirmed; verify before adopting.
