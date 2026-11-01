# 05. Native storage and the day-1 data migration

Written 2026-11-01. Binding parent: `01-architecture-decision.md` (§5 modules, §6.3 Konsist rules 7 and 9, §9.5 threading,
§9.7 startup and migration, §9.8 persistence, §9.9 identity, §10.2 upgrade journey, §11 P5, §12 D2–D9). Nothing here
overrides it. Where this file adds detail that 01 did not fix, the addition is marked **[new]**.

## For the owner (plain words)

1. When the new app installs over the old one, it keeps the old settings file exactly where it is and keeps using it, so
   your servers, choices, drafts, queued prompts, schedules and widgets are all still there.
2. Saved passwords are unlocked once with the old app's key and put into the new app's own locked store; the old copy
   is never deleted in that release, and any password that can't be read is listed once so you can type it again.
3. Before anything else happens, the app makes a byte-for-byte safety copy of the old settings and password files, and
   the Linux folder, projects, voice models and attachments are never moved.
4. If something goes wrong, you see one plain screen with "Try again", "Report a problem" and "Restore data from before
   the update"; nothing is ever deleted silently.
5. For the first native release we also keep a ready-made "go back" build of the old app that can read everything the
   new app wrote, and every step is proven on the emulator with a real old install before your phone gets it.

Decision needed: no — the eight owner questions in §12 have defaults, and work proceeds on those defaults.

---

## 0. What this section takes as given and what it adds

**Taken from 01 (binding):**

- The Android `KeyValueStore` *is* the adopted `shared_prefs/FlutterSharedPreferences.xml`. The `flutter.` prefix and
  every Flutter encoding stay (§9.7 step 3, §9.8).
- `SecretStore` is a Keystore AES-256-GCM key (non-exportable), AAD = the entry name, in its own prefs file (§9.8).
- `BlobStore` is files where they are today. Files never move (§9.7).
- Room `oc.db` is **switch+1 by default**, pulled into the switch release only by the P5 trigger (§9.8, D9). Every
  table has `profile_id` or is allowlisted. The import records counts and a SHA-256 per blob. Source keys stay for one
  release.
- Step 0 (byte backup into `files/migration/flutter-v57/`) is the only synchronous migration work, and only on the
  first native launch (§9.7). Everything else runs in `MigrationRunner` on IO behind `MigrationGate`, and `EntryRouter`
  holds every launch intent until the gate opens.
- The migrator is `:android:migration` (S migration owner, Opus). It may depend only on `:core:common`,
  `:core:security`, `:core:data`, `:core:model` (+ `:core:db` when Room ships) (§6.1).
- Konsist rule 7: `getSharedPreferences(` only in `:core:data` `androidMain` and `:android:migration`. Rule 9: every
  per-profile key matches `oc.<what>.<profileId>` and is registered with `ProfileScope`.
- versionCode base **10000**, same package, signer lineages unchanged (§9.9, D2, D3).

**Added here [new]:** the journal format, the step list with ids (S0, M1–M6), the exact secure-storage decryptor, the
three legacy transforms the runner owns (L1–L3) and the ones engines own (L4–L9), the failure classes and their copy,
the rollback tiers (R0 fix forward, R1 Flutter rollback build, R2 in-app restore), the native preference ledger, the
`DartJson` canonical encoder, the Kotlin-owned prefs accessor, the Room v2 import, the v3 cleanup, the fixture recipe,
the tests, the work units and the owner decisions M-D1…M-D8.

**Source facts** below were read in `/home/eslam/Storage/Code/wt/oc_app-batch1` at HEAD `65e3f70a3` (app `2.1.0+57`,
tag `v2.1.0+57`) and in the plugin sources under `~/.pub-cache` (`flutter_secure_storage` 11.2.0,
`shared_preferences_android` 2.4.27, `path_provider_android` 2.3.1, `image_picker_android` 0.8.13). Tags: **[V]** read
in source today; **[R]** from a map, research file or memory; **[U]** unverified, proven by the named test.

Two corrections to the maps, verified in source today:

- On Android the crash files live in `files/` itself (`files/crash-diagnostics.json`,
  `files/crash-diagnostics-consent`, `files/native-last-crash.properties`), because `CrashDiagnosticsBridge` returns
  `filesDir` as the directory. `crash-diagnostics/` is the non-Android fallback only [V].
- `app_flutter/` holds no first-party data. Draft, stash and photo vaults, voice models and logos are under `files/`
  (`getApplicationSupportDirectory()` = `context.filesDir` in `path_provider_android` 2.3.1) [V]. Catalog row
  **B-MIGR-009** ("app_flutter is read") is therefore proven by showing the `files/` vaults and models stay readable
  without re-download, and `app_flutter/` is only inventoried (§7.4 M1). The catalog owner should reword the row.

---

## 1. The stores at a glance

| Store | Android home (day 1) | Module and API | Holds | Per-profile rule | Written by |
|---|---|---|---|---|---|
| `KeyValueStore` | `shared_prefs/FlutterSharedPreferences.xml`, keys `flutter.<key>`, adopted in place | `:core:data` `KeyValueStore` (common) + `FlutterPrefsStore` (androidMain) | the ~200 `oc.*` shapes and 4 `voice.*` keys (Appendix A) | key shape `oc.<what>.<profileId>[.<more>]` | engines through `WriterLane`s; `:android:*` surfaces; the migrator's own `MigrationWriter` |
| Kotlin-owned prefs | 8 files (`oc_lifecycle`, `builtin_server_recovery`, …) unchanged | `:core:data` androidMain `KotlinPrefsFile` + `Context.kotlinPrefs(file)` | runtime, background, permission memory (Appendix D) | owned by the module; per-profile entries cleaned by that module's deletion hook | the moved Kotlin code in `:android:*` |
| `SecretStore` | `shared_prefs/oc_secrets_v1.xml` + Keystore alias `oc.secrets.aesgcm.v1` **[new]** | `:core:data` `SecretStore` + `KeystoreSecretStore` | passwords, tokens (Appendix B) | names keep today's shapes (`pw.<id>`, `oc.codexToken.<id>`, …) | `:engine:profiles` and owners in Appendix B; the migrator once |
| `BlobStore` | `files/draft-attachments-v1/`, `files/prompt-stash-attachments-v1/`, `files/prompt-photo-recovery-v1/` | `:core:data` `BlobStore` | content-addressed attachment payloads | owner dir = `sha256(profileId)` | `:engine:composer` |
| Other files | exactly where they are (Appendix C) | the owning module | rootfs, projects, models, diagnostics | module-specific | owning module |
| Room `oc.db` | `databases/oc.db` (switch+1) | `:core:db` | queue, drafts index, stash, receipts, first-paint caches | `profile_id` column on every table | `:engine:delivery`, `:engine:composer`, `:engine:connection` |
| Migration journal | `files/native/migration.json` **[new]** | `:android:migration` `MigrationJournal` | step markers, counts, rollback flag | — | the migrator only |
| Byte backup | `files/migration/flutter-v57/` | `:android:migration` `FlutterBackup` | copies of the Flutter prefs and secure-storage XMLs | — | step S0 only |
| Audit | `files/native/migration-audit.json` **[new]** | `:android:migration` `MigrationAudit` | counts and digests, never values | — | step M5 |

No fourth persistence technology is added. DataStore is used only on desktop/iOS later (01 §8).

---

## 2. `KeyValueStore`

### 2.1 Interface (`:core:data` commonMain, package `io.github.eslamasabry.oc.core.data.kv`)

```kotlin
public sealed interface PrefValue {
    public data class Bool(val value: Boolean) : PrefValue
    public data class Int64(val value: Long) : PrefValue          // Dart int is always a Long on disk
    public data class Dbl(val value: Double) : PrefValue
    public data class Str(val value: String) : PrefValue          // raw string; JSON is decoded by the owning engine
    public data class StrList(val value: List<String>) : PrefValue
    public data class Undecodable(val kind: UndecodableKind) : PrefValue   // never carries the raw value
}
public enum class UndecodableKind { UnknownType, BadJsonList, BadJavaList, BadDouble }

public interface KeyValueStore {
    /** Logical keys only: never the "flutter." prefix. In-memory, cheap, immutable. */
    public fun get(key: String): PrefValue?
    public fun keys(prefix: String = ""): Set<String>
    /** Logical keys changed by any writer in this process (engines and :android:* surfaces). */
    public val changes: SharedFlow<Set<String>>
    /**
     * One atomic platform commit. Suspends until MigrationGate allows writes.
     * On refusal: the touched keys are put back to their previous in-memory values and
     * Failure.StorageRefused is returned (the "restore attempted keys" rule of AppPreferencesStore).
     */
    public suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit>
    /** Atomic compare-and-set of one string key (schedule envelope CAS, widget snapshot patches). */
    public suspend fun compareAndSet(key: String, expected: String?, next: String?): Outcome<Boolean>
}

public interface PrefEdit {
    public fun putString(key: String, value: String)
    public fun putBool(key: String, value: Boolean)
    public fun putLong(key: String, value: Long)
    public fun putStringList(key: String, value: List<String>)
    public fun putDouble(key: String, value: Double)
    public fun remove(key: String)
    // No putInt, putFloat or putStringSet: the Flutter reader would mis-handle them (§2.3).
}
```

Typed keys are declared once per owning module and registered with `ProfileScope` (§3.3):

```kotlin
// example in :engine:connection
internal object ConnectionKeys {
    val model = ProfileKey.string("oc.model")            // physical: flutter.oc.model.<profileId>
    val sessionPins = ProfileKey.string("oc.sessionPins")
    val activeProfile = AppKey.string("oc.activeProfile")
}
```

### 2.2 Android: the adopted Flutter file (`:core:data` androidMain)

`FlutterPrefsStore(context, gate: MigrationGate, dispatchers: AppDispatchers)`:

- Opens `context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)` once (the only call site for
  this file outside `:android:migration`).
- Maps logical `k` ↔ physical `"flutter.$k"`. Physical keys without the prefix are not exposed (none are expected; M1
  counts them).
- Decodes with `FlutterPrefsCodec` (below). Encodes with the same encodings Flutter writes.
- `commit` runs on `dispatchers.io`, calls `SharedPreferences.Editor.commit()` (never `apply()`), and branches on the
  returned boolean. Before committing it reads the previous values of the touched keys; on `false` it writes those back
  in a second `commit()` (memory is restored even if the disk write fails again) and returns `Failure.StorageRefused`.
  Reason: `SharedPreferencesImpl` updates its in-memory map before the disk write, so a refused write otherwise leaves
  memory ahead of disk [R state-data §8.5].
- `compareAndSet` holds one `ReentrantLock` for the file across read and commit, so blocking (`goAsync`) and suspend
  callers share it. `ScheduledPromptStore`'s CAS moves onto it unchanged in meaning.
- `changes` comes from one `OnSharedPreferenceChangeListener` (held strongly), mapped to logical keys.
- **The store gates writes, not reads.** Every write (including Kotlin surfaces) suspends on `gate.awaitWritable()`;
  the migrator writes through its own `MigrationWriter` capability, which the gate lets through. Reads are in-memory
  and not blocked by the store, because the gate screen, the crash-loop guard and the migrator itself must read. The
  components 01 §9.7 lists (receivers, workers, widget providers, tiles, services) still await the gate before doing
  their work (§7.1).
- `:android:*` modules running inside synchronous framework callbacks may use the Android-only extension
  `KeyValueStore.commitBlocking(lane, edit)` (`:core:data` androidMain), which asserts it is not on the main thread.

`FlutterPrefsCodec` (`:core:data` androidMain; constants verified in `shared_preferences_android` 2.4.27 [V]):

| Stored | Decode | Native writes it as |
|---|---|---|
| `boolean` | `Bool` | `putBoolean` |
| `long` | `Int64` | `putLong` |
| `int` (only if a Kotlin writer ever used `putInt`) | `Int64` | never written |
| `float` | `Dbl` | never written |
| `Set<String>` | `StrList` | never written |
| string starting `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!` | strip, decode JSON string array → `StrList`; bad JSON → `Undecodable(BadJsonList)` | `"…Qu!" + json` |
| string starting `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu` (no `!`) | Base64 → `ObjectInputStream` limited to `java.util.ArrayList`, `java.util.Arrays$ArrayList`, `java.lang.String`, `[Ljava.lang.String;` → `StrList`; else `Undecodable(BadJavaList)` | never written; a native write of that key writes the JSON form, which Flutter also reads |
| string starting `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu` | strip, `toDouble()` → `Dbl`; bad → `Undecodable(BadDouble)` | `DOUBLE_PREFIX + value.toString()` |
| string starting `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy` | `BigInteger(s, 36).toString()` → `Str` (never written by this app) | never written |
| any other string | `Str` | `putString`; **refuse** (`Failure.InvalidValue`) a value that starts with any of the three prefixes, as the plugin does |

`Undecodable` keys are never rewritten by the migrator; the owning engine treats them as "unreadable" with its existing
fail-closed rule.

### 2.3 The format-compatibility rule for the switch line **[new]**

While the journal's `rollback.flutterReadable` is `true` (§9), every native writer of a Flutter-era key must write what
the Dart writer wrote:

1. Same key, same type, same JSON field names, same `"v"`/`"version"`/`"schemaVersion"` value. No added fields in
   existing records: several Dart readers fail closed on unknown shapes (automation policy with an unknown version reads
   ALL OFF; `oc.sessionLinkBinding` must have exactly 4 keys; stash identity is checked on read) [R state-data §4.2, §10.7].
2. Only `Boolean`, `Long`, `String` (with the list and double prefixes) are written to the Flutter file.
3. New data goes under **new keys**, named by §3.1 and listed in the native ledger (§3.6). The Flutter app ignores keys
   it does not know.
4. A change that needs a new format is a numbered migration step (§7.11) that sets `flutterReadable = false`. It needs
   the owner's OK (M-D1), because it retires the R1 rollback build.

Enforced by `FlutterFormatCompatTest` (§10.6) and by review.

### 2.4 Desktop and iOS (later phases, for shape only)

- Desktop: in-memory `KeyValueStore` until the desktop phase (01 §7.2). Then a DataStore Preferences file under
  `$XDG_DATA_HOME/io.github.eslamasabry.opencode_mobile/native/`, same logical keys, no `flutter.` prefix. A
  `LinuxFlutterImporter` reads the Flutter Linux build's `shared_preferences.json` (native JSON types, `flutter.`
  prefix) and its single libsecret item (attribute `account = io.github.eslamasabry.opencode_mobile.secureStorage`,
  value = JSON object of all secrets), with the same journal rules [R network-data §8.5].
- iOS: no Flutter iOS install base is known, so there is nothing to import. If one appears, the same journal pattern
  applies with Keychain.

### 2.5 `DartJson`: byte-exact encodings that keys and fingerprints depend on **[new]**

Several stored keys and digests are built from Dart's `jsonEncode`, `Uri.encodeComponent` and
`DateTime.toIso8601String()`. A Kotlin re-implementation that differs by one byte silently orphans data (pins vanish,
schedules refuse to run, caches never match). So one canonical port lives in `:core:common`
(`io.github.eslamasabry.oc.core.common.dart.DartJson`):

```kotlin
public object DartJson {
    /** dart:convert jsonEncode for null, Boolean, Long, Double, String, List, Map (insertion order kept). */
    public fun encode(value: Any?): String
    /** Dart Uri.encodeComponent: keeps A-Z a-z 0-9 - _ . ! ~ * ' ( ), UTF-8 percent-escapes the rest, uppercase hex. */
    public fun encodeComponent(text: String): String
    /** Dart DateTime.toUtc().toIso8601String(): yyyy-MM-ddTHH:mm:ss.mmmZ, 6 fraction digits when micros != 0. */
    public fun iso8601Utc(epochMicros: Long): String
    /** Lowercase hex SHA-256 of the UTF-8 bytes (Dart crypto sha256.convert(utf8.encode(s)).toString()). */
    public fun sha256Hex(text: String): String
}
```

Escaping in `encode` follows Dart: `"` → `\"`, `\` → `\\`, `\b \t \n \f \r`, other code units below 0x20 and lone
surrogates as `\u` + four hex digits; `/` and non-ASCII are not escaped [U: hex case and lone-surrogate handling are
fixed by the golden vectors below, generated by Dart].

| Where Dart's encoding is load-bearing | Exact expression (Dart) |
|---|---|
| Schedule `profileFingerprint` and widget target `fingerprint` | `sha256(utf8(jsonEncode(profile.toJson()..remove('name'))))`; `toJson` key order: `id, name, baseUrl, backend, username, [codexDirectory], flavor, requiresOpenCode2Migration, openCode2HistoryPending, [serverVersion], [orchestration{provider,url,city,hostMode,hostKind,front,[enabledAt]}]` [V `profiles/models.dart:154,301`, `scheduling/agent_connection.dart:15`, `background/widget_action_runner.dart:19`] |
| `oc.sessionInventory.<id>` scope | `sha256(jsonEncode([backend, flavor, baseUrl, username, directory, workspace]))` |
| Command receipt scope (`oc.pendingCommands`) | `sha256(jsonEncode([profileId, baseUrl, directory, workspace]))` |
| `oc.sessionViews.<id>` map keys | `jsonEncode([baseUrl, directory, workspace, sessionId])` |
| `oc.returnBrief.<id>` map keys | `jsonEncode([baseUrl, directory, workspace])` |
| `oc.sessionPins.<id>` scope keys | `jsonEncode([directory, workspace])` |
| `oc.sessionDrafts` storage key | `jsonEncode([profileID, sessionID])`, or bare `sessionID` when `profileID` is empty |
| `oc.providerRuntimeRefresh.v1.<id>.<enc>` / `oc.providerRuntimeUnloadable.<id>.<enc>` | `Uri.encodeComponent("<dir or '<default>'>\n<ws or '<default>'>")` |
| Vault paths | `<sha256(owner)>/<sha256(content)>.blob` |
| Orchestration `enabledAt` | `enabledAt.toUtc().toIso8601String()` |

The fingerprint is recomputed from the parsed profile (never from the stored string), exactly as Dart does, so the
stored JSON's spacing does not matter. `ServerProfileJson` (`:core:model`, shared by `:engine:profiles` and
`:android:migration`) is the lenient port of `ServerProfile.fromJson`/`toJson` and owns that key order.

Golden vectors: `tools/flutter-fixtures/dart_vectors.dart` (§10.4) prints every expression above for a fixed list of
inputs (ASCII, Arabic, emoji, control characters, lone surrogate, empty, null workspace) and is committed as
`core/common/src/commonTest/resources/dart-vectors.json`. `DartJsonTest` must match all of them.

---

## 3. Per-profile naming, `ProfileScope` and the deletion sweep

### 3.1 Key naming rules (unchanged from today, now enforced by Konsist rule 9)

1. Every key starts with `oc.` (the four `voice.*` keys and `desktop_window_state_v1` are the frozen exceptions).
2. A per-profile key is `oc.<what>.<profileId>` or `oc.<what>.<profileId>.<more>` (`oc.promptStash.<id>.<stashId>`,
   `oc.orchestration.<id>.<field>`) or `oc.<what>.v1.<profileId>.<more>` (`oc.providerRuntimeRefresh.v1…`).
3. A new app-wide key uses only segments listed in `ProfileScope.reservedSegments()` (§3.2), and generated ids never
   take one of them. `.app` is reserved for the app-wide draft (`oc.draft.<target>.app`).
4. Agent-backend keys written for the phone's agent pseudo-profile end in `.<phoneId>.agents` and fall under the phone
   profile's sweep.
5. A new native key gets a ledger row (§3.6) in the same commit.

### 3.2 `ProfileId` (`:core:model`)

```kotlin
@JvmInline public value class ProfileId private constructor(public val value: String) {
    public companion object {
        private val SHAPE = Regex("^[A-Za-z0-9_-]{1,128}$")
        /** Existing ids are accepted as stored (lenient); this only rejects impossible shapes. */
        public fun parse(raw: String): ProfileId?
        /** New ids: Dart-compatible microseconds-since-epoch string, never a word. */
        public fun newId(clock: Clock): ProfileId
    }
}
```

- Ids never contain `.`, so one id can't be a dotted segment of another (the sweep depends on it).
- **[new] Reserved segments.** `ProfileScope.reservedSegments()` is computed from the ledger: every dotted segment of
  every app-wide key (`app`, `native`, `notify`, `nudges`, `firstRun`, `transcript`, `phone`, …). `newId` never
  produces one (it only produces digits). M1 counts existing ids that collide (expected 0) and reports the count; it
  never renames an id.

### 3.3 `ProfileScope` (`:core:data` commonMain)

```kotlin
public enum class KeyScope { Profile, Owner, App, Shared, Other }   // Owner = keyed by a phone-agent owner id

public data class KeyShape(
    val prefix: String,          // "oc.model." or exact "oc.activeProfile"
    val scope: KeyScope,
    val type: PrefType,          // Bool, Int64, Dbl, Str, StrList
    val ownerModule: String,     // ":engine:connection"
    val why: String,             // the ledger sentence
)

public interface ProfileScope {
    public fun register(shape: KeyShape)
    public fun registerSecret(prefix: String, ownerModule: String)           // "pw.", "oc.codexToken."
    public fun registerTable(table: String, profileColumn: String = "profile_id")
    public fun registerBlobRoot(root: BlobRoot)                                // owner dir = sha256(profileId)
    /** The sweep rule, verbatim from ProfileStore.profileScopedPreferenceKeys (§3.4). */
    public fun scopedKeys(profileId: ProfileId, store: KeyValueStore): Set<String>
    public fun ownedData(profileId: ProfileId, store: KeyValueStore, secrets: SecretStore): OwnedData
    public fun reservedSegments(): Set<String>
}

public data class OwnedData(
    val prefKeys: Set<String>, val secretNames: Set<String>,
    val tables: List<String>, val blobOwners: List<Pair<BlobRoot, String>>,
)
```

Registration happens in each owning module through a Metro multibinding (`@ContributesIntoSet` of `KeyShape`), so there
is no central list to edit. `:quality` checks the registered set against the ledger.

### 3.4 The sweep rule (verbatim)

```kotlin
fun scopedKeys(id: ProfileId, store: KeyValueStore): Set<String> {
    val suffix = ".${id.value}"; val infix = ".${id.value}."
    return store.keys("oc.").filterTo(mutableSetOf()) { it.endsWith(suffix) || it.contains(infix) }
}
```

It is computed **late** (after writer lanes drain), because Kotlin surfaces and the schedule worker write profile keys
outside the engines [R state-data §5 step 3]. Owned data outside the Flutter prefs file is reached through the
registries: secrets by prefix + id, Room tables by `profile_id`, blob roots by `sha256(id)`, and the Kotlin-owned
per-profile entries (`oc.builtinRuntimeOwnership.<id>`, `oc.builtinRecoveryBudget.<id>` in `builtin_server_recovery`,
`files/oc.teamEngine.<id>/`, agent homes in the rootfs) by the owning module's `forgetProfile(id)` hook.

### 3.5 The storage contract for profile deletion (implemented in `:engine:profiles`)

The deletion transaction itself is `:engine:profiles`' slice (01 §9.8). Storage requires this order, taken from
`connection/deletion.dart` [R state-data §5]:

1. Close admission synchronously for every per-profile writer lane; drain them.
2. Compute `scopedKeys` and the retained set (shared phone-agent owner keys, `oc.automaticActivity.<id>`,
   `oc.automation.<id>`, `oc.teamEngineDeleted.<id>`, `oc.scheduleDeleted.<id>`).
3. Queue lane: rewrite `oc.offlineQueue` without the profile (or move to `oc.keptQueuedPrompts` when asked); refuse if
   the live queue differs from the confirmed plan.
4. Drafts lane: rewrite `oc.sessionDrafts` without the profile **and without `profileID == ''` rows**; collect vault
   files for the owner and for owner `''`.
5. Clear pending photo, widget snapshot entries, pinned shortcuts, tile entries (each reports cleared/failed).
6. Stash (metadata, then files) **before** the generic sweep, because the sweep erases the owner marker needed to find
   orphan files.
7. Generic sweep of `scopedKeys` minus retained.
8. If any step failed: keep the profile, report plain words, reopen everything.
9. Profile row + secrets as one unit: write the new `oc.profiles`, clear `oc.activeProfile` if active, delete secrets
   (BYO SSH identity first), verify absence; any failure restores the previous `oc.profiles`.
10. Remove `oc.automation.<id>` and `oc.automaticActivity.<id>` last.

During the one-release window after the Room import (§6.4), steps 3 and 4 also rewrite the frozen source keys without
the profile, so a deleted server's prompts never linger in a frozen copy.

### 3.6 The native preference ledger **[new]**

The Flutter repo has a gate, `test/coverage/settings_preferences_coverage_test.dart`, with
`test/fixtures/coverage/settings_preferences_ledger.json` (188 entries: 107 profile, 49 global, 12 shared, 20 other) [V].
The native repo carries it forward:

- File: `quality/src/test/resources/prefs-ledger.json`. Seeded from the Flutter ledger plus the six Kotlin-only keys,
  the four `voice.*` keys and the new native keys (Appendix A). Each entry: `scope` (`profile|owner|app|shared|other`),
  `type`, `ownerModule`, `handling` (Appendix A codes), `why`.
- `PrefsLedgerTest` (`:quality`, Konsist + text scan): every `"oc.` string literal in native sources is in the ledger,
  and no ledger entry is stale.
- `ProfileSweepShapesTest` (`:core:data` commonTest): port of the Flutter test "the sweep takes every per-server key
  shape" — seed `<shape>doomed` and `<shape>keeper` for every profile shape, sweep `doomed`, nothing of `keeper` and no
  app-wide key is taken (B-STATE-060, B-SEC-039).
- At the freeze, `tools/flutter-fixtures/extract_ledger.py` re-extracts the ledger from the final Flutter commit (the
  in-flight branches may add keys such as `oc.linux.*`, 01 D16). Any new key gets an Appendix A row before the switch
  (acceptance A3).

---

## 4. `SecretStore`

### 4.1 Format **[new names; design from 01 §9.8]**

| Item | Value |
|---|---|
| Keystore key | AES-256, `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, no user authentication (workers read secrets), no StrongBox; alias **`oc.secrets.aesgcm.v1`** |
| File | `shared_prefs/oc_secrets_v1.xml` (excluded from backup: `allowBackup="false"` and the existing exclude-everything rules stay) |
| Entry name | the secret's name verbatim (`pw.<id>`, `oc.codexToken.<id>`, …); names are ids, not secret |
| Entry value | `Base64.NO_WRAP( 0x01 ‖ IV[12] ‖ ciphertext ‖ tag[16] )`, IV generated by Keystore, AAD = UTF-8 of the entry name (stops swapping ciphertexts between entries, which flutter_secure_storage allowed) |
| Version | first byte `0x01`; a future format writes `0x02` under a new migration step |

### 4.2 Kotlin

```kotlin
// :core:data commonMain
public interface SecretStore {
    public suspend fun read(name: String): Outcome<String?>          // Failure.SecretUnavailable on Keystore errors
    public suspend fun write(name: String, value: String): Outcome<Unit>
    public suspend fun delete(name: String): Outcome<Unit>
    public suspend fun names(prefix: String): Outcome<Set<String>>    // "reset saved sign-ins" enumerates orphans
}

// :core:data androidMain
internal class KeystoreSecretStore(
    private val prefs: SharedPreferences,          // "oc_secrets_v1"
    private val keystore: KeystoreAead,            // seam: AndroidKeystoreAead in production, SoftwareAead in JVM tests
    private val redactor: Redactor,
    private val dispatchers: AppDispatchers,
) : SecretStore
```

- Every `read` and `write` registers the value with the `Redactor` **before** returning or persisting (a failing
  Keystore may echo its input) [R state-data §8.3].
- `write` commits, then reads back and compares; a mismatch deletes the entry and returns `Failure.SecretUnavailable`.
- Values are cached in memory after the first read (Keystore decrypt is ~5–20 ms each [U, measured in W4]).
- Keystore failures map to `Failure.SecretUnavailable`, and `:ui:words` maps that to the plain sentence the Flutter app
  used for `SecureStorageUnavailable`. No exception text reaches copy.
- Never a plaintext fallback.

### 4.3 Names: Appendix B lists every name, its owner, and what happens when it can't be migrated.

---

## 5. `BlobStore` and the other files

```kotlin
public enum class BlobRoot(public val dir: String) {
    Drafts("draft-attachments-v1"), Stash("prompt-stash-attachments-v1"), Photos("prompt-photo-recovery-v1")
}
public interface BlobStore {
    public suspend fun put(root: BlobRoot, owner: String, content: String): Outcome<BlobRef>   // UTF-8 data: URL text
    public suspend fun read(ref: BlobRef, expectedBytes: Long, expectedSha256: String): Outcome<String>
    public suspend fun collect(root: BlobRoot, owner: String, keep: Set<String>): Outcome<Int>
}
```

- Layout unchanged: `files/<root>/<sha256(owner)>/<sha256(content)>.blob`, written to `.tmp` then renamed, verified by
  length + SHA-256 on read with a bounded streaming read. Owner `''` = legacy unattributed drafts.
- Stash and drafts never share a root (the drafts GC doesn't know stash references).
- Collection runs only after the metadata commit. Limits: 5 attachments and 32 MiB per draft, 256 MiB per root, photos
  10 MiB [R state-data §4.5].
- Every other file (Appendix C) stays where it is and keeps its owner. The migrator only lists names and sizes.

---

## 6. Room `oc.db` (switch+1 by default; earlier only if the P5 trigger fires)

### 6.1 Rules

- One database `databases/oc.db`, Room 3 (`androidx.room3` 3.0.3) with `BundledSQLiteDriver` (01 §8). The bundled driver
  is **required**, not optional, for rows larger than 2 MB: the platform driver reads through `CursorWindow` (2 MB by
  default) and a 20 MiB queued prompt would fail to load [U: proven by `BigRowReadTest` in W11].
- Every table has `profile_id TEXT NOT NULL` (`''` allowed for legacy unattributed rows) or is on the allowlist in
  `RoomProfileIdSchemaTest` (`:quality`) with a reason. `ProfileDao.deleteEverything(profileId)` runs in one
  `immediateTransaction`.
- Schemas exported to `core/db/schemas/` and committed. Migrations are tested with Room's migration test helper.
- Wire and record shapes are stored as `TEXT` JSON (canonical `DartJson.encode` of the parsed element), never normalised
  into columns beyond the keys the queries need.

### 6.2 Tables (version 1)

| Table | Key | Columns used by queries | Payload | Source key | Allowlist |
|---|---|---|---|---|---|
| `queued_prompt` | `entry_id` | `profile_id`, `position`, `created_at`, `dispatched_at` | `entry_json`, `sha256` | `oc.offlineQueue` | — |
| `kept_prompt` | `entry_id` | `profile_id` (of a removed server), `position` | `entry_json`, `sha256` | `oc.keptQueuedPrompts` | yes: app-owned on purpose, never swept |
| `session_draft` | `storage_key` | `profile_id`, `session_id`, `updated_at` | `draft_json`, `sha256` | `oc.sessionDrafts` | — |
| `stash_prompt` | (`profile_id`, `stash_id`) | `created_at` | `prompt_json`, `sha256` | `oc.promptStash.<id>.<stashId>` | — |
| `prompt_history` | (`profile_id`, `ordinal`) | — | `text` | `oc.promptHistory.<id>` | — |
| `command_receipt` | (`profile_id`, `command_id`) | `state`, `created_at` | `receipt_json` | `oc.pendingCommands.<id>` | — |
| `first_paint_cache` | (`profile_id`, `kind`) | `saved_at` | `blob_json` | `oc.sessionInventory.<id>`, `oc.chatTail.<id>`, `oc.mainFeed.<id>`, `oc.sideFeed.<id>`, `oc.agentFeed.<owner>` | — |
| `collection_state` | (`collection`, `profile_id`) | `readable` | `raw_if_unreadable` | any source that failed to parse | — |

`collection_state` keeps today's fail-closed meaning: an unreadable source queue is imported as `readable = 0` with its
raw text, so nothing sends and only the person's explicit "clear" recovers it, exactly as before.

### 6.3 The import step `native.migration.v2`

One step per collection (`V2.queue`, `V2.drafts`, `V2.stash`, `V2.history`, `V2.receipts`, `V2.caches`), each:

1. Read the source key raw. Split into JSON elements with lenient parsing; read only the fields the table keys need
   (`id`, `profileID`, `createdAt`, `dispatchedAt`, `updatedAt`) as primitive content.
2. In one transaction: `INSERT OR IGNORE` every row with `sha256 = DartJson.sha256Hex(entry_json)`.
3. Verify: row count equals element count; the sorted list of row hashes equals the sorted list of source element hashes.
4. Record `{count, digest}` in the journal under `migrations."native.migration.v2".<collection>`.
5. Dispatch markers are copied byte-for-byte inside `entry_json`. The import never evicts, expires, clears a marker or
   re-enqueues (state-data risk 6).

Re-run: a recorded collection is skipped; a crash between commit and journal write is detected by step 3 succeeding on
re-run (`INSERT OR IGNORE` makes it a no-op).

### 6.4 Source keys during the one-release window

- After a collection imports, its source key stays in the prefs file, **frozen**: native stops reading and writing it.
  `oc.native.movedToDb` lists the moved keys and the import time.
- Profile deletion still scrubs the deleted profile's rows from the frozen app-wide copies (§3.5).
- The next release's cleanup (`native.migration.v3`, §9.5) removes them.
- Because the frozen keys stay in the XML for one release, the cold-start gain of moving the queue arrives one release
  later. If P5 fired *because of* the queue's size, the coordinator may amend 01 §9.8 to keep the sources as files under
  `files/migration/v2-sources/` instead, with the same recoverability and a smaller XML (owner decision M-D4).
- The v2 import sets `rollback.flutterReadable = false` (the Flutter app can't read `oc.db`), which retires R1 (§9.3).

---

## 7. The day-1 migration

### 7.1 Where and when it runs

```
process starts (launcher, MY_PACKAGE_REPLACED, BOOT_COMPLETED, widget update, alarm, notification action)
├─ ContentProviders: none may touch prefs. WorkManager uses on-demand initialization
│  (OcApplication implements Configuration.Provider; the androidx.startup WorkManagerInitializer is removed
│  with tools:node="remove"), so no worker can run before step S0.            [new]
└─ OcApplication.onCreate()                       (returns at once in any process other than the main one)
   1. uncaught-exception handler (NativeCrashStore)
   2. Redactor
   3. crash-loop guard (files/native/crashloop.json)
   4. S0 FlutterBackup.runIfNeeded()               ← synchronous, first native launch only
   5. build AndroidAppGraph (stores are lazy; writes wait for MigrationGate)
   6. MigrationRunner.start()                      ← IO dispatcher, application scope
   7. warm the prefs file on IO
```

- On the owner's phone the first native process is usually started by `MY_PACKAGE_REPLACED` right after
  `adb install -r`, with no UI. The runner finishes there. The UI part ("ask once", §8) reads the journal at the first
  UI launch.
- Receivers, workers, widget providers, tiles and services `await` the gate in their own coroutines (`goAsync`,
  `CoroutineWorker`), never on the main thread (01 §9.7). They wait at most **20 s** (`goAsync` background-broadcast
  budget is larger, but 20 s leaves margin [U per Android version]); on timeout they hand the work to an expedited
  one-shot `MigrationFollowUpWorker` (WorkManager) that awaits the gate without a deadline. On the fast path (no Room
  import) the gate opens in about a second, so this fallback is for the blocked and slow cases.
- `MainActivity` draws the kit "Updating your app…" state on the first frame and the 8 s "Try again" rule from 01 §9.7.

### 7.2 The journal: `files/native/migration.json` **[new]**

```json
{
  "schema": 1,
  "source": { "kind": "flutter", "prefsBytes": 812345, "prefsKeys": 412, "secureEntries": 7,
              "foundAt": 1730457600000, "installerLastUpdateTime": 1730457590000 },
  "backup": { "state": "done", "dir": "files/migration/flutter-v57", "at": 1730457600120,
              "files": [ { "name": "FlutterSharedPreferences.xml", "bytes": 812345, "sha256": "…" } ] },
  "steps": {
    "M1": { "state": "done", "attempts": 1, "at": 1730457600300, "result": { } },
    "M2": { "state": "done", "attempts": 1, "at": 1730457600700, "result": { "found": 7, "migrated": 6, "flagged": 1 } }
  },
  "migrations": { "native.migration.v1": { "complete": true, "at": 1730457601000 } },
  "hooks": { "firstLaunch.v1": { "state": "done", "at": 1730457605000 } },
  "secretRetries": { "remaining": 3 },
  "rollback": { "flutterReadable": true, "lastReturnHandledAt": null },
  "blocked": null
}
```

- `source.kind`: `flutter` (Flutter data found), `none` (fresh install, or a device restore with no data — both are a
  clean first run, never a failure [R state-data risk 15]), `startFresh` (after the person chose Start fresh).
- Step `state`: `pending | running | done | failed`. `attempts` is incremented and written **before** the step runs, so
  a crash counts. Three attempts without `done` → `failed` → `blocked = {"reason": "StepCrashed", "step": "M2"}`.
- Written atomically: `migration.json.tmp`, `FileDescriptor.sync()`, rename, fsync of `files/native/`.
- Readers ignore unknown fields. A journal with a higher `schema` than the build understands is left untouched and
  every known step id is honoured as recorded (this can only happen through a downgrade, which Android refuses).
- The values are counts, states and times only. No secret, prompt text, server address or key name with user content.

### 7.3 `MigrationGate` (`:core:data` commonMain)

```kotlin
public sealed interface GateState {
    public data object Closed : GateState
    public data class Running(val step: String) : GateState
    public data class Open(val firstNativeLaunch: Boolean, val notices: MigrationNotices) : GateState
    public data class Blocked(val reason: BlockReason, val neededBytes: Long? = null) : GateState
}
public enum class BlockReason { LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed }
public data class MigrationNotices(val reenter: List<ProfileId>)   // drives the one-time sheet (§8.2)

public interface MigrationGate {
    public val state: StateFlow<GateState>
    public suspend fun awaitWritable()          // returns when Open; never returns while Blocked
}
public interface MigrationActions {             // implemented by :android:migration, used by the :ui:shell gate screen
    public suspend fun retry(): Outcome<Unit>
    public suspend fun restorePreUpdateData(): Outcome<Unit>   // R2, §9.4
    public suspend fun startFresh(): Outcome<Unit>              // keeps a copy, §8.1
    public suspend fun acknowledgeNotices()
}
```

### 7.4 The steps

| Id | What | Reads | Writes | Marker | Typical time [U, measured in P5/W8] |
|---|---|---|---|---|---|
| **S0** | byte backup (sync, `onCreate`) | Flutter + FSS XML files (raw bytes) | `files/migration/flutter-v57/` | `backup.state = done` | < 1 s for 100 MB (01 P5 acceptance) |
| **M1** | census and integrity | backup copy, live prefs, directory listings | journal only | `steps.M1` | < 200 ms |
| **M2** | secrets: decrypt FSS v11, re-encrypt into `SecretStore` | FSS files, Keystore | `oc_secrets_v1.xml`, `oc.secretReentry.<id>` | `steps.M2` | < 500 ms for 20 secrets |
| **M3** | prefs adoption: L1 retire, L2 notification merge, L3 read-state consent | live prefs | declared write set only | `steps.M3`, `oc.notify.version`, `oc.readStateConsentMigration.v1` | < 100 ms |
| **M4** | restore-authority check | live prefs vs backup | none | `steps.M4` | < 50 ms |
| **M5** | count-and-hash audit | everything above | `files/native/migration-audit.json`, one diagnostics line | `steps.M5` | < 300 ms |
| **M6** | complete | — | journal `native.migration.v1.complete`, `oc.native.flutterReadable = true` | — | — |

Then the gate opens and `:androidApp` runs the first-launch hooks H1–H3 (§7.9).

#### S0 — byte backup (`FlutterBackup.runIfNeeded(context)`)

1. If `files/native/migration.json` exists and `backup.state == "done"` or `source.kind != "flutter"` → return
   (fast path: one `File.exists` and one small read, well under the 50 ms `onCreate` budget).
2. Candidate files (each with its `.bak` twin, which Android treats as authoritative on load when it exists):
   `FlutterSharedPreferences.xml`, `FlutterSecureStorage.xml`, `FlutterSecureKeyStorage.xml`,
   `FlutterSecureStorageConfiguration.xml`, `FlutterSecureStorageConfiguration:FlutterSecureStorage.xml`.
   If none exists → write `source.kind = "none"`, mark `native.migration.v1` complete, return.
3. Free space: `StatFs(filesDir).availableBytes >= sum(sizes) + 16 MiB`, else write
   `blocked = {"reason":"LowSpace","neededBytes":…}` and return (nothing touched).
4. Delete a leftover `files/migration/flutter-v57.partial/` (ours), create it, copy each file with
   `FileInputStream`/`FileOutputStream`, hashing while copying, `fd.sync()` each.
5. Write `manifest.json` in the partial dir (names, sizes, SHA-256, copy time, native versionCode).
6. Rename the partial dir to `files/migration/flutter-v57/`, fsync `files/migration/`.
7. Journal `backup.state = "done"`.

Raw file copies, before the first `getSharedPreferences` call in the process (01 §9.7). A finished backup is never
overwritten, so it always holds the pre-update state. The directory name is fixed by 01 §9.7 even when the source is a
local 4xxx build; `manifest.json` records the real source (the installed versionCode is not readable after the update,
so it records file times and the native versionCode, and the fixture recipe records the Flutter build).

#### M1 — census and integrity (read-only)

1. **Prefs integrity.** Parse the backup copy Android would load (`.bak` if present, else `.xml`) with the migrator's
   own `MapXmlParser` (`android.util.Xml.newPullParser()`; elements `map, string, boolean, long, int, float, set, null`).
   Compare its `flutter.`-key count with `getSharedPreferences("FlutterSharedPreferences").all` filtered the same way.
   - File non-empty, own parse finds keys, live map empty → **Blocked(PrefsUnreadable)**. Android's
     `SharedPreferencesImpl` loads an unparsable file as an empty map, and the first commit would overwrite the file
     [U: AOSP behaviour, proven by `CorruptPrefsXmlTest` in Robolectric]. No write may happen.
   - Counts differ otherwise → record `backupParseMismatch`, continue (the live map is what the app uses).
   - **Baseline.** Before any write in this process, M1 writes `files/native/m1-baseline.bin`: the SHA-256 of the raw
     value of every live `flutter.*` key (hashes only; prefs hold no secrets). M4 and M5 compare against it, so a crash
     and restart between steps keeps the comparison valid. It is deleted when v1 completes.
2. **Codec census.** Count live values per decoded kind, including legacy Java-serialized lists and `Undecodable`.
3. **Strays.** Presence and key count (never values) of `files/datastore/FlutterSharedPreferences.preferences_pb`,
   `shared_prefs/io.github.eslamasabry.opencode_mobile_preferences.xml`,
   `shared_prefs/flutter_image_picker_shared_preference.xml`, and any physical key in the Flutter file without the
   `flutter.` prefix. All are ignored afterwards [R network-data §8.2].
4. **Profiles.** Parse `oc.profiles` with `ServerProfileJson`: count, invalid ids, reserved-segment collisions, profiles
   carrying `requiresOpenCode2Migration` or `openCode2HistoryPending`.
5. **Directory inventory.** Name, file count and byte size at depth 1 of `files/`, `app_flutter/`, `cache/`,
   `code_cache/`, `no_backup/`, `databases/`, `app_webview/` (and depth 2 of `files/`, counting `files/linux/` and
   `files/projects/` as totals only). This records where Shorebird and the Flutter engine left files [U: exact
   Shorebird paths are not documented; the first real-install fixture (§10.4) records them], for the v3 cleanup.

#### M2 — secrets (`FlutterSecretsMigration`)

The exact algorithm is in §7.5. Outcome per name: `migrated`, `alreadyMigrated`, `nativeNewer`, or `flagged(reason)`.

- Flagged names whose owner is a profile id get `oc.secretReentry.<profileId>` =
  `{"v":1,"names":["pw"],"reason":"UnwrapFailed","at":<ms>}` (names stored as the prefix only, never values).
  `:engine:profiles` reads it: a profile with a flagged name and no `SecretStore` entry loads with
  `requiresPasswordReentry` (or `requiresCodexTokenReentry` for agent sockets). It is cleared when the person saves a
  new value. **A flagged secret is never treated as an empty password** (01 §9.7).
- Retry: flagged names are retried at the next three launches (`secretRetries.remaining`), but only while
  `SecretStore` has no entry of that name (a value the person typed always wins).
- Orphan names (no matching profile) are migrated too and counted. "Reset saved sign-ins" removes them as it does today.

#### M3 — prefs adoption (`LegacyTransforms`)

Adoption means no copy and no conversion: the owning engines read the same keys in the same formats. M3 runs only the
three one-shot transforms the Flutter app may not have finished. Each is a line-for-line port of the Dart function,
idempotent, guarded by its own marker, and committed through `MigrationWriter`:

| Id | Port of | Marker | Write set | Catalog rows |
|---|---|---|---|---|
| L1 | `ProfileStore._retireQuotaBudgets` + `_retirePreferences` (`lib/state/profiles.dart:218–255`) | none needed (removal is idempotent) | remove every `oc.budgets.*`, `oc.glassLiquidActive`, `oc.glassLiquidStrikes`, `oc.glassLiquidOffUntil`, `oc.effectsLiveBackground` | B-STATE-081, B-STATE-068 |
| L2 | `NotificationPreferences.migrate(profileIDs)` with the pure `mergeLegacyNotifyRules` (`lib/state/notification_preferences.dart:118,238`) | `oc.notify.version == 1` | `oc.notify.quietStart`, `quietEnd`, `wifiOnly`, `checkInAfterMinutes`, `quotaAlerts`, `version`; sources `oc.notifyRules.<id>` / `oc.quotaMonitor.<id>` untouched | B-MIGR-001 |
| L3 | `ReadStateConsent.migrateExistingProfiles(ids)` (`lib/state/read_state_consent.dart`) | `oc.readStateConsentMigration.v1.complete` | `oc.shareSessionViews.<id>`, `…Default.<id>`, `…Origin.<id>`, the journal key; legacy `oc.shareSessionViews` read once | B-MIGR-003, B-SEC-142 |

On the owner's phone all three are normally no-ops, because the Flutter app ran them at every launch. The census
proves it.

A refused commit → `Blocked(WriteRefused)` (a refused prefs commit almost always means a full disk), with Try again.
Nothing is lost: `commit()` is atomic (temp file + rename), so the file holds either the old or the new state.

#### M4 — restore-authority check

Kotlin restores the phone server after a reboot or update only if three Flutter-era keys say so [R builtin-linux §5.3,
trap T1]. If they are lost, the phone server silently stops coming back.

1. For every profile id in `oc.profiles` and every id found in `oc.builtinRecovery.*` keys: the raw strings of
   `oc.builtinRecovery.<p>`, `oc.automation.<p>`, `oc.phoneAgentOwner.<p>`, and of `oc.builtinServerOwner`, must hash
   to the M1 baseline. A difference → `Blocked(AuthorityChanged)` (it can only be a bug; R2 is offered).
2. Evaluate the authority predicate (`version == 2 && nativeAuthority == true` on the recovery record; the automation
   policy parses and allows `restartPhoneServer`; the owner id matches `^[A-Za-z0-9_-]{1,80}$`). Record `armed` /
   `disarmed(reason)` counts. A disarmed state that was already disarmed in the backup is the Flutter state and is only
   recorded.
3. The predicate lives in `:android:migration` (it may not depend on `:android:linux`). `RestoreAuthorityParityTest` in
   `:androidApp` (which sees both modules) runs the same vectors through both predicates and requires equal answers.

#### M5 — audit (`MigrationAudit`)

`files/native/migration-audit.json` holds counts and digests, never values or full key names:

- prefs: bytes, key count, counts per decoded kind, `unchangedKeysDigest`, changed shapes with counts;
- profiles: count, invalid ids, reserved collisions, OC1-era flags;
- secrets: found, migrated, already migrated, native newer, flagged by reason, orphans, `_BACKUP` entries skipped
  (**no hashes of secrets**: a hash of a short password can be guessed);
- queue entries and entries with a dispatch marker; drafts (and legacy unattributed ones); stash prompts; pins; schedules
  (profiles, schedules, runs); widget targets;
- restore authority armed/disarmed;
- files: vault file counts, voice-model bytes, `files/linux/ubuntu.ready` present.

**The "nothing else changed" proof:** `unchangedKeysDigest` = SHA-256 over the sorted `(key, sha256(raw value))`
pairs of every key outside the declared write sets (M2: `oc.secretReentry.*`; M3: the L1–L3 sets), computed once from
the live map now and once from `m1-baseline.bin`. They must be equal; otherwise `Blocked(AuthorityChanged)` is raised
with step `M5`. This also proves the offline queue and its
dispatch markers are byte-identical (state-data risk 6).

One diagnostics line: "Update moved your data: N servers, M saved sign-ins (K need the password again)." Counts only
(B-MIGR-011).

#### M6 — complete

Journal `migrations."native.migration.v1".complete = true`, then `oc.native.flutterReadable = true` (commit), then the
gate opens with `firstNativeLaunch = true` and the notices.

### 7.5 flutter_secure_storage 11.2.0: the exact decryption

All names verified in the plugin's Java source today [V]: `FlutterSecureStorageConfig.java`,
`StorageCipherImplementationGCM.java`, `KeyCipherImplementationRSAOAEP.java`, `NamespacedConfigSource.java`,
`MigrationBackup.java`. The app constructs `const FlutterSecureStorage()` everywhere (8 call sites, no
`AndroidOptions`), so the defaults apply: no namespace, `resetOnError: true` on the Dart side.

| Item | Exact value |
|---|---|
| Data file | `shared_prefs/FlutterSecureStorage.xml` |
| Entry name | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg" + "_" + <name>` |
| Backup entries to skip | names ending `_BACKUP` (left by the plugin's own v10→v11 migration backup) |
| Entry value | `Base64.DEFAULT` of `IV[12] ‖ AES-GCM ciphertext ‖ tag[16]`, no AAD, UTF-8 plaintext |
| Wrapped key file / entry | `shared_prefs/FlutterSecureKeyStorage.xml`, entry `AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK` (Base64 of an RSA-wrapped **16-byte** AES key) |
| Keystore alias | `context.packageName + ".FlutterSecureStoragePluginKeyOAEP"` |
| Unwrap | `Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")`, `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)`, `unwrap(bytes, "AES", Cipher.SECRET_KEY)` |
| Algorithm markers | `FlutterSecureSAlgorithmKey` = `RSA_ECB_OAEPwithSHA_256andMGF1Padding`, `FlutterSecureSAlgorithmStorage` = `AES_GCM_NoPadding`; read from `FlutterSecureStorageConfiguration:FlutterSecureStorage` first, then `FlutterSecureStorageConfiguration` |

```kotlin
// :android:migration — io.github.eslamasabry.oc.android.migration.secrets
internal enum class SecretFailure { MarkersMismatch, NoWrappedKey, KeystoreAliasMissing, UnwrapFailed,
    BadBase64, TooShort, AuthFailed, NotUtf8, ReadBackMismatch, SecretStoreUnavailable }

internal sealed interface FlutterSecret {
    val name: String
    class Plain(override val name: String, val value: String) : FlutterSecret
    class Failed(override val name: String, val reason: SecretFailure) : FlutterSecret
}

/** Seam: AndroidKeystoreUnwrapper in production; SoftwareRsaUnwrapper (JCA RSA key) in JVM tests. */
internal fun interface AesKeyUnwrapper { fun unwrap(wrapped: ByteArray): SecretKey }

internal class FlutterSecretsReader(
    private val data: Map<String, *>,         // FlutterSecureStorage prefs .all (read-only)
    private val wrappedKeyB64: String?,       // FlutterSecureKeyStorage entry
    private val markers: Pair<String?, String?>,
    private val unwrapper: AesKeyUnwrapper,
) {
    private companion object {
        const val PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_"
        const val KEY_ALG = "RSA_ECB_OAEPwithSHA_256andMGF1Padding"
        const val STORAGE_ALG = "AES_GCM_NoPadding"
    }

    fun readAll(): List<FlutterSecret> {                       // never throws; per-entry results
        val names = data.keys.filter { it.startsWith(PREFIX) && !it.endsWith("_BACKUP") }
            .map { it.removePrefix(PREFIX) }
        if (names.isEmpty()) return emptyList()
        fun all(reason: SecretFailure) = names.map { FlutterSecret.Failed(it, reason) }
        val (keyAlg, storageAlg) = markers
        if ((keyAlg != null && keyAlg != KEY_ALG) || (storageAlg != null && storageAlg != STORAGE_ALG))
            return all(SecretFailure.MarkersMismatch)          // v9-era data: never attempted
        val wrapped = wrappedKeyB64 ?: return all(SecretFailure.NoWrappedKey)
        val aes = try { unwrapper.unwrap(Base64.decode(wrapped, Base64.DEFAULT)) }   // KeyAliasMissing: internal class : Exception()
            catch (e: KeyAliasMissing) { return all(SecretFailure.KeystoreAliasMissing) }
            catch (e: Exception) { return all(SecretFailure.UnwrapFailed) }
        return names.map { name -> decryptOne(name, data[PREFIX + name], aes) }
    }

    private fun decryptOne(name: String, raw: Any?, aes: SecretKey): FlutterSecret {
        val bytes = try { Base64.decode(raw as String, Base64.DEFAULT) }
            catch (e: Exception) { return FlutterSecret.Failed(name, SecretFailure.BadBase64) }
        if (bytes.size < 12 + 16) return FlutterSecret.Failed(name, SecretFailure.TooShort)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, bytes, 0, 12))
                doFinal(bytes, 12, bytes.size - 12)
            }
        } catch (e: Exception) { return FlutterSecret.Failed(name, SecretFailure.AuthFailed) }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plain)).toString()
        } catch (e: CharacterCodingException) { return FlutterSecret.Failed(name, SecretFailure.NotUtf8) }
        return FlutterSecret.Plain(name, text)
    }
}

internal class AndroidKeystoreUnwrapper(private val packageName: String) : AesKeyUnwrapper {
    override fun unwrap(wrapped: ByteArray): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "$packageName.FlutterSecureStoragePluginKeyOAEP"
        val priv = ks.getKey(alias, null) as? PrivateKey ?: throw KeyAliasMissing()   // never create it
        return Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround").run {
            init(Cipher.UNWRAP_MODE, priv,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
            unwrap(wrapped, "AES", Cipher.SECRET_KEY) as SecretKey
        }
    }
}
```

M2 then, per `Plain` secret, in this order:

1. `redactor.registerKnownSecret(value)`; if the value parses as a JSON object (GitHub credential, BYO envelope), also
   register every string field of 8 or more characters. This happens before any log line or `SecretStore` call.
2. If `SecretStore.read(name)` returns a value: equal → `alreadyMigrated`; different → `nativeNewer` (kept, never
   overwritten).
3. Else `SecretStore.write(name, value)`, which reads back and compares; failure → `Failed(ReadBackMismatch)` or
   `Failed(SecretStoreUnavailable)`.

Never: write to any FSS file, create or delete the FSS Keystore alias, or apply `resetOnError`. Exception messages are
never logged; only `SecretFailure` names reach the journal. The Keystore entry stays reachable because the package and
signer, and so the app UID, are unchanged [R network-data §8.3; proven by P5 and §10.5].

### 7.6 Legacy behaviours that stay with their engines

These Flutter behaviours are not one-shot migrations the runner can finish. They are part of how the store loads, so
the owning engine ports them with its store (and its catalog rows):

| Id | Behaviour | Owner | Why not in the runner |
|---|---|---|---|
| L4 | OC1 → OC2 profile flags: `flavor` read back as `v2`; `requiresOpenCode2Migration` true when a non-codex/paseo profile's stored `flavor != "v2"`; `openCode2HistoryPending` kept | `ServerProfileJson` (`:core:model`), used by `:engine:profiles` and by M1's census | Dart applied it at read time and never rewrote `oc.profiles` on load; the runner applies the same rule (for counting) with zero writes, which is the idempotent equivalent 01 §9.7 asks for (B-MIGR-002) |
| L5 | Phone-agent owner mapping and legacy gate adoption (`_migratePhoneAgentOwners`, receipts `oc.phoneAgentGateMigration.<owner>`) | `:engine:agents` (called by `:engine:profiles` load) | also assigns owners to new profiles; scoring rules (+4/+2/+1) are engine logic (B-LINUX-118) |
| L6 | Motion fold: legacy `oc.effectsCelebrations == false` + `full` → `calm` | `:engine:settings` | read-time rule (B-STATE-003, B-KIT-006) |
| L7 | Older unattributed drafts → Saved prompts, once per profile (`oc.savedPromptMigrationV1.<id>`, backup `oc.olderDraftsBackupV1.<id>`) | `:engine:composer` | needs the vault and shelf; runs in the draft lane at bootstrap for the active profile (B-STATE-087) |
| L8 | Stash inline attachments → files (`migrateAttachments`) | `:engine:composer` | per prompt, deferrable, never truncates (B-STATE-134) |
| L9 | Usage alert thresholds from old per-source rules (`migrated` flag inside `oc.usageAlerts.v1`) | `:engine:integrations` | needs the subscription bridge (B-STATE-151) |
| — | Built-in recovery budget legacy → native marker | `:android:linux` (Kotlin, moved as is) | already Kotlin (B-LINUX-032) |
| — | FactsBook passphrase file → secure storage | FactsBook (deferred, 01 D6) | data untouched until ported (B-SEC-075) |
| — | `plainServerName` ("This phone", "Computer at <ip>") | `ServerProfileJson` | read rule |

### 7.7 What the migration never touches

- Every file in Appendix C, including `files/linux/**` (the rootfs with `ubuntu.ready`), `files/projects/**`,
  `files/oc.sharedProjects`, `files/oc.teamEngine.<id>/`, vaults, `voice_models/`, diagnostics and crash files (the
  consent epoch is kept), `app_flutter/` (inventory only), `app_webview/` (WebView data carries over by itself) and
  `cache/` (it may hold the `image_picker` file that `oc.pendingPromptPhoto.path` points at, so the migration never
  clears the cache).
- The eight Kotlin-owned prefs files (Appendix D). The moved Kotlin keeps their names and formats.
- `no_backup/androidx.work.workdb*`. WorkManager upgrades its own database (2.10.5 → 2.12.0); the worker class FQCNs
  stay frozen (01 §9.9), so queued work still resolves.
- OS-held state: widget instances, pinned and dynamic shortcuts, Quick Settings tiles, notification channels and the
  person's channel settings, AlarmManager alarms (kept across a package replace [U]; H2 re-arms them anyway), runtime
  permissions and the battery
  exemption. They carry over because the package, signer and component names are unchanged (01 §9.9 component freeze).

### 7.8 What each key gets

Appendix A lists every key with its handling code:

| Code | Meaning |
|---|---|
| **A** | adopted: the owning engine reads and writes this key in the same format; no conversion |
| **A-K** | adopted, and also read or written by Kotlin in an `:android:*` module through `KeyValueStore` (cross-component contract; moving it later means moving every reader at once) |
| **L1/L2/L3** | touched by the runner's transform (§7.4 M3) |
| **R** | retired: removed by L1 |
| **I** | ignored: adopted untouched, never read by native code, removed by the v3 cleanup (M-D5) |
| **D** | deferred feature (AI Team, FactsBook, Termux per 01 D6/D7): untouched until its port; still swept by shape |
| **X** | dropped feature (A2A, 01 D6): untouched; removed by the v3 cleanup |
| **M2** | a secure-storage name: copied into `SecretStore` by M2 |
| **DB at v2** | moved into Room by `native.migration.v2` (§6) |
| **N** | not a stored value (a route name, a server-side metadata field) |

Every engine that adopts a key ports the Dart reader as lenient `JsonElement` parsing and its Dart tests first (strict
parsing would brick legitimate old data, state-data risk 2). `AdoptedKeysFixtureTest` in each engine's `commonTest`
reads the real-install fixture (§10.4, converted to `prefs.json` so common code needs no XML parser) and asserts every
key it owns decodes as readable.

### 7.9 First-launch hooks (run by `:androidApp` after the gate opens, once, marker `hooks.firstLaunch.v1`)

| Id | Action | Owner module | Catalog row |
|---|---|---|---|
| H1 | Mirror `oc.appLocale` into the per-app locale using the mechanism slice-0 proof P6 picks (Android 13+ `LocaleManager`). `oc.appLocale` stays the stored truth that `NativeStrings` reads; the two-way sync rule belongs to `:engine:settings` | `:engine:settings` + `:android:platform` | B-L10N rows |
| H2 | Re-arm OS surfaces: `ScheduleEngine.rearmAll(FirstNativeLaunch)`; redraw every widget id of every provider FQCN; republish pinned shortcuts from `oc.pinnedShortcuts`; `TileService.requestListeningState` for both tiles; resume background live mode if `oc.keepLiveInBackground` (through the same restriction-aware path the restore receivers use) | `:android:schedule`, `:android:widgets`, `:android:background` | B-MIGR-013, B-ANDR-090 |
| H3 | Check every frozen notification channel id exists; create missing ones only (creating an existing id never changes the person's settings) | `:android:background` | B-MIGR-013 |

The phone-server restore after the update is the moved `BuiltinRestoreReceiver` (`MY_PACKAGE_REPLACED`), which awaits
the gate; M4 guarantees its keys are intact.

### 7.10 Idempotence and interruption

| Killed during | On the next start |
|---|---|
| S0 copy | the partial dir is ours: deleted and redone; source files were only read |
| between S0 and M1 | M1 runs; S0 is not redone (the backup must hold the pre-update state) |
| M2 after k of n secrets | names already in `SecretStore` with an equal value are `alreadyMigrated`; the rest continue |
| an M3 commit | `commit()` is atomic (temp file + rename): the file holds the old or the new state; markers make each transform a no-op once done |
| M4, M5 | read-only; rerun |
| after the M6 commit, before the gate opened | the journal says complete; the gate opens at once |
| any step three times | `failed` → `Blocked(StepCrashed)` → the failure screen instead of a crash loop |

The crash-loop guard (01 §9.7) is a second net: three crashes within 10 s of start open safe mode, which offers R2.

### 7.11 Versioning of native data **[new]**

- **Migration ids are append-only:** `native.migration.v1` (first launch), `native.migration.v2` (Room import),
  `native.migration.v3` (Flutter leftovers cleanup). Each is a fixed list of step ids. A released step is never edited;
  a behaviour change is a new step under a new id.
- **Step interface** (`:android:migration`):
  ```kotlin
  internal interface MigrationStep {
      val id: String                         // "M2", "V2.queue", "V3.fssFiles"
      val breaksFlutterReadability: Boolean  // true sets rollback.flutterReadable = false on completion
      suspend fun run(ctx: StepContext): StepResult
      suspend fun verify(ctx: StepContext): Boolean   // re-checked on re-run before skipping
  }
  ```
- **Value formats:** Flutter-era records keep their own version fields unchanged while `flutterReadable` is true (§2.3).
- **SecretStore:** version byte `0x01` and alias suffix `.v1`. Rotation = a new alias and a re-encrypt step.
- **Room:** an integer schema version with exported schemas and tested migrations.
- **Journal:** `schema: 1`; new fields are additive.

---

## 8. Failure: ask once, never lose silently

### 8.1 Failure classes

| Class | Detected in | What is kept | What the person sees | Way out |
|---|---|---|---|---|
| LowSpace | S0 | everything (nothing was touched) | "Your phone is almost full" screen | Try again; Open storage settings |
| PrefsUnreadable | M1 | the file and its backup; no write happens | failure screen | Try again; Report a problem; Start fresh (keeps a copy) |
| Secret unreadable (per name) | M2 | the FSS entry, untouched | **one** sheet listing the servers that need a password, then each server's own "Enter password" state | automatic retry on 3 launches; the person types it |
| SecretStore unavailable (Keystore key can't be created) | M2 | FSS untouched | the same sheet, plus one plain sentence on the Servers page (`secret_store_unavailable`, W7) | retried every launch |
| WriteRefused | M3, M6 | the old state (atomic commit) | "Couldn't save on this phone" screen | Try again; Open storage settings |
| AuthorityChanged | M4, M5 | backup | failure screen | Restore data from before the update; Report a problem |
| StepCrashed (3 attempts) | any | everything | failure screen | Try again (resets attempts); Restore data from before the update; Start fresh |

"Start fresh (keeps a copy)" moves `FlutterSharedPreferences.xml` and `oc_secrets_v1.xml` into
`files/migration/start-fresh-<epochMs>/`, sets `source.kind = "startFresh"` and restarts the process. Nothing is deleted
and the Linux files stay. A confirmation names the effect: "You'll start with no saved servers and default settings.
A copy of your old data stays on this phone."

A store that reads its own key as corrupt after adoption (for example an unreadable queue) shows that store's existing
recovery UI, as the Flutter app did. That is not a migration failure.

### 8.2 Screens and copy (`:ui:words` `composeResources/values*/strings_migration.xml`; Arabic and the five other languages from the l10n lane)

The gate screens are kit `KitScreen` states owned by `:ui:shell` (01 §5.2). They have no bars and no new levels; every
option is a visible row, and every row names its target.

| Key | English |
|---|---|
| `migration_updating` | Updating your app… |
| `migration_still_updating` | Still updating. The first start after an update can take a little longer. |
| `migration_try_again` | Try again |
| `migration_reenter_title` | Some servers need their password again |
| `migration_reenter_body` | The update couldn't read these saved passwords. Everything else came across. |
| `migration_reenter_row` | %1$s · Enter password |
| `migration_reenter_later` | Later |
| `migration_failed_title` | The update couldn't finish moving your data |
| `migration_failed_body` | Nothing was deleted. Your data from before the update is safe on this phone. |
| `migration_restore_row` | Restore data from before the update |
| `migration_restore_confirm` | This puts back your settings and servers as they were before the update. Changes since then are kept in a copy. |
| `migration_start_fresh_row` | Start fresh (keeps a copy of your old data) |
| `migration_report_row` | Report a problem |
| `migration_space_title` | Your phone is almost full |
| `migration_space_body` | The update needs %1$s of free space to keep a safety copy of your data. Free up space, then try again. |
| `migration_save_failed_title` | Couldn't save on this phone |
| `migration_open_storage_row` | Open storage settings |

- **Ask once:** the re-enter sheet shows on the first UI launch after the gate opens with non-empty
  `notices.reenter`, then `acknowledgeNotices()` records it. After that, each server's row shows its normal
  "Enter password" state. The sheet is not shown again.
- **Details fold:** the step id and failure class only (for example `M2 · UnwrapFailed ×1`), next to Report a problem
  (01 §9.6). No exception text.
- Motion: the kit's standard 150–250 ms fade between "Updating" and the result; nothing under reduced motion.

### 8.3 Report a problem

The report adds `migration-audit.json` and the journal (both counts only) and the M1 directory inventory (names and
sizes). It never includes the backup, secrets or prefs values.

---

## 9. Rollback

### 9.1 Why it needs a plan

Android refuses to install a lower versionCode over a release build without an uninstall, and an uninstall deletes the
data [R memory apk-build-numbers]. The native line starts at 10000. Every Flutter build is below that, so "reinstall the
old app" is impossible unless the old app is rebuilt with a higher number.

**versionCode rule [new]:** native releases step by 10 (10000, 10010, 10020, …). Numbers ending 1–9 are reserved for
rollback builds. The next native build after a rollback takes the next multiple of 10. This keeps 01 D3 (base 10000).

### 9.2 R0 — fix forward (the default, always available)

A native fix release with the next versionCode: the owner gets it over adb when around (memory auto-install), others
through the in-app updater (01 §9.9).

### 9.3 R1 — Flutter rollback build (only while `flutterReadable` is true)

- **When it is valid:** the switch line, before any step with `breaksFlutterReadability` (the Room v2 import is one).
  All state is then in the adopted prefs file in Flutter formats, so the old app reads everything the native app wrote.
- **Prepared before the switch (W9):**
  1. Branch `rollback/flutter-final` from the Flutter commit installed on the owner's phone at the freeze (record
     versionCode, commit and signer in `docs/migration/flutter-final.md` of the native repo).
  2. Add one file, `lib/native_return_guard.dart` (about 60 lines), called first in `main()`:
     ```dart
     final prefs = await SharedPreferences.getInstance();
     final readable = prefs.getBool('oc.native.flutterReadable');
     if (readable == false) { runApp(const CannotGoBackApp()); return; }   // touches nothing
     if (readable == true) {
       await prefs.setString('oc.native.returnedAt', DateTime.now().toUtc().toIso8601String());
     }
     ```
     `CannotGoBackApp` shows one sentence: "This older version can't open data from the newer one. Install the newer
     version again." No other Flutter change.
  3. Build with the pinned toolchain,
     `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter build apk --release
     --build-number=<installedNative+1> --build-name=3.0.0-back --split-per-abi`, signed with the owner's local key
     (01 D2). Not a Shorebird release or patch. Keep the APK on the VPS and the APK share server.
  4. Prove it in the round-trip journey (§10.5 J4) before the switch.
- **What the person sees in R1:** every setting, server, draft, queued prompt, stash and schedule. Native-only keys are
  ignored. Secrets come from the untouched FSS files, so a server added after the switch, or a password changed after
  it, shows "Enter password" (the Flutter app's existing path).
- **Returning to native** (the next native build): the runner sees `oc.native.returnedAt` later than
  `rollback.lastReturnHandledAt` and runs step `R1.return`:
  1. For every FSS entry that is missing from, or whose ciphertext differs from, its S0 backup copy (so it was written
     during the rollback): decrypt and overwrite `SecretStore` (the rollback period is newer). Entries unchanged since
     S0 keep the native value.
  2. Clear the matching `oc.secretReentry` flags; re-run H2 (the Flutter build may have re-armed schedules and widgets).
  3. Record `lastReturnHandledAt`. Prefs need nothing: it is the same file.
- **Retired** when `flutterReadable` becomes false, or by the owner (M-D1).

### 9.4 R2 — restore data from before the update (in-app, native)

Offered on the failure screen and in safe mode only, while `files/migration/flutter-v57/` exists:

1. Runs only while the gate is `Blocked` or in safe mode, so no engine is writing.
2. Moves the live `FlutterSharedPreferences.xml` and `oc_secrets_v1.xml` into `files/migration/replaced-<epochMs>/`.
3. Copies the backup's Flutter XMLs (prefs and the FSS files) back into `shared_prefs/`. Kotlin-owned files are not
   touched.
4. Resets the journal to `backup = done`, steps M1–M6 `pending`, attempts 0, and restarts the process (the
   SharedPreferences cache lives in memory, so a restart is required).
5. The migration then runs again from the pre-update state.

### 9.5 Retention and the v3 cleanup (`native.migration.v3`)

Nothing Flutter-era is deleted in the switch release. At switch+2 at the earliest, and only with the owner's OK (M-D3),
v3 deletes:

| Item | Condition |
|---|---|
| `files/migration/flutter-v57/`, `replaced-*`, `start-fresh-*` | R1 retired; no open failure |
| `FlutterSecureStorage*.xml`, `FlutterSecureKeyStorage.xml`, the FSS Keystore alias | every FSS name is in `SecretStore` or its flag was resolved by the person |
| Frozen v2 source keys, `oc.native.movedToDb` | v2 complete one release earlier |
| Keys coded **I**, **R**, **X** in Appendix A, and `oc.native.returnedAt` | — |
| `app_flutter/`, Shorebird and Flutter-engine leftovers | the paths recorded by M1 on the real-install fixture |

v3 sets `flutterReadable = false`.

---

## 10. Tests

Native tests are named after catalog rows (Konsist rule 12), for example
`` `B-MIGR-006 servers reconnect without a password prompt after the update`() ``. They are written first by the
test-writer lane from `../catalog/behaviors.jsonl` (01 §10.3).

### 10.1 Catalog rows this section owns

| Rows | Test (module) |
|---|---|
| B-MIGR-004 in-place update identity | upgrade journey J1 (§10.5), component freeze (`:quality`) |
| B-MIGR-005 preferences carried unchanged | `MigrationRunnerTest.unchangedKeysDigest` + J1 Maestro settings checks |
| B-MIGR-006 secrets re-stored, servers reconnect | `FlutterSecretsReaderTest`, `SyntheticFlutterSecretsTest`, J1 |
| B-MIGR-007 per-profile data scoped and swept | `ProfileSweepShapesTest`, `AdoptedKeysFixtureTest`s, J1 delete-a-server flow |
| B-MIGR-008 files kept, Linux starts without reinstall | J1 (`ubuntu.ready` unchanged, rootfs inode count unchanged, server starts) |
| B-MIGR-009 files readable, no model re-download | J1 (vault files open, voice pack "Ready") |
| B-MIGR-010 one-shot, resumable, no duplicates | `MigrationRunnerTest.interruptAtEveryStep`, J2 |
| B-MIGR-011 no secrets exposed, counts only | `MigrationAuditPrivacyTest` (planted secrets never appear in the journal, audit, logs or report) |
| B-MIGR-013 schedules, widgets, shortcuts, tile, live mode | J1 + J3 (reboot) |
| B-MIGR-001 / 002 / 003 | `LegacyTransformsTest` (L2, L3), `ServerProfileJsonTest` (L4) |
| B-STATE-081, 068 | `LegacyTransformsTest` (L1) |
| B-SEC-039, B-STATE-060 | `PrefsLedgerTest`, `ProfileSweepShapesTest` |
| B-SEC-142 | `LegacyTransformsTest` (L3) |
| B-LINUX-032 restore budget, builtin-linux trap T1 | `RestoreAuthorityCheckTest`, `RestoreAuthorityParityTest`, J3 |
| B-MIGR-012 (Shorebird ends, notice stays) | owned by the updater slice; listed for completeness |

The other "migration"-level rows (B-STATE-001/012/016/023/024/033/056/057/059/065/069/079/087/134/144/146/151,
B-CHAT-015/051/088, B-SEC-036/075/085/093/116/178/182, B-ANDR-090, B-MEDIA-022, B-KIT-006, B-LINUX-*) belong to the
engines that own those keys. Each of them also gets an `AdoptedKeysFixtureTest` assertion.

### 10.2 JVM tests (VPS, T0/T1)

| Test | Module | Proves |
|---|---|---|
| `FlutterPrefsCodecTest` | `:core:data` (Android host test) | every row of §2.2: JSON list, legacy Java list (fixture made with `ObjectOutputStream` on the JVM), double, BigInteger, Set, int, float; refuses clash prefixes on write; `Undecodable` never carries the value |
| `FlutterPrefsStoreTest` (Robolectric, real `SharedPreferencesImpl`) | `:core:data` | commit semantics; refused commit restores the touched keys; CAS under contention; writes wait for the gate, reads do not; `changes` flow |
| `DartJsonTest` | `:core:common` commonTest | all Dart golden vectors (§2.5) |
| `ServerProfileJsonTest` | `:core:model` | lenient parse, L4 flags, `plainServerName`, unknown fields ignored, fingerprint equals the Dart vector |
| `ProfileSweepShapesTest`, `ReservedSegmentsTest` | `:core:data` | §3.4 rule over every ledger shape; no app-wide key ever matches a generated id |
| `PrefsLedgerTest` | `:quality` | every literal classified, none stale |
| `KeystoreSecretStoreTest` (software AEAD seam) | `:core:data` | format byte, AAD swap rejected, `names(prefix)`, read-back mismatch, redactor registration happens before persistence |
| `FlutterSecretsReaderTest` (`SoftwareRsaUnwrapper`) | `:android:migration` | vectors from a JVM port of the plugin's write path; every `SecretFailure`; `_BACKUP` skipped; markers mismatch flags all; `startsWith` prefix match |
| `MigrationJournalTest` | `:android:migration` | atomic write, attempts before run, three attempts → blocked, unknown fields kept |
| `MigrationRunnerTest` (Robolectric; fixture XMLs copied into `shared_prefs/`) | `:android:migration` | full run on the real-install fixture; a re-run is a no-op (identical bytes); `interruptAtEveryStep` via `StepInterrupter`; write-set invariant; fresh install; no FSS files; `.bak` present |
| `CorruptPrefsXmlTest` (Robolectric) | `:android:migration` | Android loads garbage as an empty map; M1 blocks; no write reaches the file |
| `LegacyTransformsTest` | `:android:migration` | L1–L3, ported case by case from `retired_prefs_test.dart`, `notification_preferences_test.dart` and the read-state consent tests |
| `RestoreAuthorityCheckTest` | `:android:migration` | M4 equality and predicate |
| `RestoreAuthorityParityTest` | `:androidApp` test | migrator predicate = `:android:linux` predicate on shared vectors |
| `MigrationAuditPrivacyTest` | `:android:migration` | planted passwords, tokens, prompt text and server addresses never appear in the journal, audit, report or log ring |
| `RoomImportTest`, `BigRowReadTest`, `RoomProfileIdSchemaTest` | `:core:db`, `:quality` | §6.3 counts and hashes, a 20 MiB row reads back, every table has `profile_id` or is allowlisted |

### 10.3 Instrumented tests (PC emulator `OC_API37`, T3)

- `SyntheticFlutterSecretsTest` (`:android:migration` androidTest): creates an RSA key under
  `<testPackage>.FlutterSecureStoragePluginKeyOAEP` with the plugin's exact `KeyGenParameterSpec` (copied from
  `KeyCipherImplementationRSAOAEP.makeAlgorithmParameterSpec`), wraps a random 16-byte AES key, writes FSS-format
  entries, runs M2 against the real AndroidKeyStore, asserts `SecretStore` contents, then deletes its own alias. It runs
  on any emulator without a Flutter install.
- `KeystoreSecretStoreDeviceTest`: the real Keystore AEAD round trip; the key survives a process restart.
- `UpgradeJourneyAssertions` (`:androidApp` androidTest, `qa` build type): runs right after the Gradle connected task
  installs the native APK over the seeded snapshot (§10.5), with
  `android.injected.androidTest.leaveApksInstalledAfterRun=true`. Compares the journal and audit against
  `seed-manifest.json` (counts and known fixture plaintexts).

### 10.4 Fixture dumps from a real Flutter install (emulator recipe)

Run on the PC, outside 00:00–02:00 (memory quiet-hours), holding the single emulator lock (memory emulator-lock-holder).
Never use the owner's phone for this: no root, and its data is real.

```bash
PKG=io.github.eslamasabry.opencode_mobile
VC=<versionCode of the Flutter build on the owner's phone at the freeze>       # read-only: adb shell dumpsys package $PKG | grep versionCode
OUT=android/migration/src/test/resources/fixtures/flutter-final-$VC

# 1. A fresh, rootable AVD (google_apis x86_64, API 37)
emulator -avd OC_API37 -wipe-data -no-snapshot-load -no-boot-anim &
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb root

# 2. The final Flutter build, x86_64 release, owner's local key
adb install -r flutter-final-$VC-x86_64-release.apk
adb shell dumpsys package $PKG | grep -E 'versionCode|signatures'

# 3. Fixture servers on the PC (../research/testing-perf.md §6.4): OC2 fixture/replay server, Paseo fixture

# 4. Seed through the UI (Flutter debug APKs don't run on the Shorebird engine)
maestro test e2e/seed/flutter-final.yaml
#    adds: an OC2 server with password fixture-pass-1; an HTTP-on-LAN server with cleartext consent; an HTTPS server
#    with a certificate pin; a Paseo profile with password fixture-paseo-1; a GitHub sign-in (device-flow fixture);
#    settings changed in every Settings group (style Soft, motion Calm, Arabic, quiet hours 22:00-07:00);
#    an offline queued prompt (fixture server stopped) and a second one left with a dispatch marker;
#    a draft with a photo; two saved prompts, one with an attachment; a pinned conversation; a learned phrase;
#    a scheduled prompt; built-in Linux installed and the phone server running; a small voice pack downloaded;
#    a Conversations widget and both tiles added (UIAutomator script e2e/seed/surfaces.py)

# 5. Edge values the UI can't make, written while the app is stopped
adb shell am force-stop $PKG
adb shell cat /data/data/$PKG/shared_prefs/FlutterSharedPreferences.xml > /tmp/fsp.xml
python3 tools/flutter-fixtures/add_edge_keys.py /tmp/fsp.xml   # a legacy Java-serialized list, a double, an oc.budgets.* key, oc.notifyRules.<id> without oc.notify.version
adb push /tmp/fsp.xml /data/local/tmp/fsp.xml
adb shell 'cat /data/local/tmp/fsp.xml > /data/data/'$PKG'/shared_prefs/FlutterSharedPreferences.xml'   # keeps the app uid as owner
adb shell pm unstop $PKG        # or MY_PACKAGE_REPLACED is never delivered (memory restore-proof-recipe)

# 6. Save the snapshot every journey starts from
adb emu avd snapshot save flutter-final-seeded

# 7. Dump (prefs and listings only; the rootfs is counted, never copied)
mkdir -p $OUT
adb shell "cd /data/data/$PKG && tar -cf - shared_prefs" > $OUT/shared_prefs.tar
adb shell "cd /data/data/$PKG && find files app_flutter cache code_cache no_backup databases app_webview \
  -maxdepth 3 -not -path 'files/linux/*' -not -path 'files/projects/*' -exec stat -c '%F|%s|%n' {} +" > $OUT/tree.txt
adb shell "cd /data/data/$PKG && du -sk files/linux files/projects; find files/linux | wc -l" >> $OUT/tree.txt
python3 tools/flutter-fixtures/xml_to_prefs_json.py $OUT   # writes prefs.json for commonTest readers
cp e2e/seed/seed-manifest.json $OUT/                        # the fixture plaintexts and expected counts
```

- The FSS XMLs in the dump are ciphertext bound to that AVD's Keystore. They are kept for structure tests only. Secret
  decryption against them runs only on the snapshot (J1).
- Every value in the dump is fixture data typed by the seed flow. A reviewer checks `seed-manifest.json` against the
  dump before commit.
- `tools/flutter-fixtures/` also holds `dart_vectors.dart` (§2.5) and `extract_ledger.py` (§3.6). They run with the
  pinned Flutter against a checkout of the frozen Flutter repo at the final commit.
- Redo the recipe whenever the Flutter build on the owner's phone changes before the switch (in-flight branches, 01 D16).
- A public-lineage variant (`v2.1.0+57` from GitHub, CI signer) is recorded the same way before the first public native
  release (01 D2).

### 10.5 Upgrade journeys (T3, PC emulator, release or `qa` build of the native app)

| Id | Journey | Pass when |
|---|---|---|
| J1 | load `flutter-final-seeded` → `adb install -r native.apk` (versionCode ≥ 10000, same signer) → launch → `e2e/upgrade/*.yaml` + `UpgradeJourneyAssertions` + `adb root` file checks | servers connect without a password prompt; settings equal; queued prompt still queued and **not resent**; the marked prompt waits for review; draft and photo back; saved prompts and pins back; the schedule fires; the widget redraws; tiles answer; Linux starts without reinstall; voice pack "Ready"; `files/linux/ubuntu.ready` and the rootfs inode count unchanged; backup exists; audit counts equal `seed-manifest.json` |
| J2 | as J1, with `files/native/qa-kill-after` = `S0`, `M1`, `M2:1`, `M2:all`, `M3`, `M4`, `M5`, `M6` in turn (`StepInterrupter`, compiled only into the `qa` build type), then relaunch | each ends in the J1 state; a further relaunch changes no byte of prefs, `oc_secrets_v1.xml`, journal or audit |
| J3 | after J1: `adb reboot` | the phone server is restored by itself (`ps -A` shows the server under the app uid); the schedule still fires; widgets redraw |
| J4 | after J1: install the R1 build (native+1) → check the app opens with the same data → change one password and add a draft → install native+10 | R1 shows all data; on return the changed password is in `SecretStore`, the draft is present, no duplicate send |
| J5 | fresh install of native (no Flutter data) | `source.kind = none`, first-run welcome, no failure |
| J6 | `adb install -r` of a lower-versionCode native build | refused by Android |
| J7 | two native updates in a row (10000 → 10010) | journal untouched, no step re-runs |
| J8 | seeded with less free space than the backup needs (fill the AVD's data partition with a file) | LowSpace screen; nothing changed; Try again works after freeing space |

J1, J2 and J3 run before every owner APK that touches migration code (01 §10.2). J4 runs once before the switch and
again if R1 is rebuilt.

### 10.6 Reverse compatibility (only while R1 exists)

`FlutterFormatCompatTest` checks that the Flutter app can read what native wrote:

1. Native side (`:androidApp` test, Robolectric): drive each engine's writer for the high-risk keys (`oc.profiles`,
   `oc.offlineQueue` with a dispatch marker, `oc.sessionDrafts`, `oc.promptStash.*`, `oc.pendingCommands.*`,
   `oc.automation.*`, `oc.builtinRecovery.*`, `oc.scheduledPrompts.*`, `oc.sessionPins.*`, `oc.sessionViews.*`,
   `oc.widgetTarget.*`, `oc.notify.*`) and dump the physical `flutter.*` map as typed JSON.
2. Flutter side (a test on the `rollback/flutter-final` branch, run with the pinned Flutter):
   `SharedPreferences.setMockInitialValues(dump)`, then load each store (`OfflineQueueStore`, `ProfileStore`,
   `SessionDraftStore`, `PromptShelfStore`, `PendingCommandJournal`, `AutomationPolicyController`, …) and assert
   `readable` and equal values.

### 10.7 The owner's phone on switch day (runbook)

1. Read-only adb checks: installed versionCode and signer (`dumpsys package`; `apksigner verify --print-certs` on the
   pulled base APK) equal the owner lineage `1DE5BF08…D60C` (01 D2). Free space ≥ 2 × the prefs size + 50 MB (ask the
   owner, or read `df` for `/data`).
2. J1–J3 green on the emulator for this exact APK (memory test-on-emulator-not-phone, stabilize-before-shipping).
3. The owner closes the Flutter app normally (no force-stop: a force-stopped package misses `MY_PACKAGE_REPLACED`).
4. `adb install -r opencode-mobile-3.0.0-arm64-v8a.apk` while the owner is around (memory auto-install-latest-build).
5. The owner opens the app. If the re-enter sheet shows, they type those passwords.
6. The owner sends "Report a problem" (counts only) so the audit can be compared with the bridge-release census
   (M-D2), or with expectations.
7. Read-only check that the phone server came back (`adb shell ps -A | grep opencode`). Within a day the owner reboots
   once and checks it again.
8. Keep R1 (native+1) ready on the APK share server until the owner says the native app is fine (M-D1).

---

## 11. Work units

One agent per unit, own worktree and branch (AGENTS.md). Sizes are estimates for planning, re-planned at M0 with
slice-0 throughput (01 P10).

| # | Unit | Modules | Owner / model | Slice | Needs | Est. size | Done when |
|---|---|---|---|---|---|---|---|
| W1 | P5 spike: S0 timing; prefs adopted by a Kotlin reader in a library module; one v11 secret decrypted from a real Flutter install; Room trigger numbers | `:android:migration` (spike), `:androidApp` | S migration (Opus) | 0 | 01 P1 skeleton | ~800 lines | 01 P5 acceptance recorded in `docs/proofs/P5.md` |
| W2 | `DartJson`, `FlutterPrefsCodec`, `KeyValueStore` + `FlutterPrefsStore`, `WriterLane`, `KotlinPrefsFile`, `MigrationGate` interface | `:core:common`, `:core:data` | S core (Codex builds, owner rule codex-backend-claude-frontend) | 1 | interfaces frozen at end of slice 0 | ~1.5k + 1.5k tests | §10.2 rows for these green; Konsist rule 7 passes |
| W3 | `ProfileScope`, ledger, sweep tests, `ServerProfileJson`, `ProfileId` | `:core:data`, `:core:model`, `:quality` | S core (Codex) | 1 | W2 | ~700 + 900 tests | `PrefsLedgerTest`, `ProfileSweepShapesTest` green |
| W4 | `SecretStore` (Keystore AEAD + software seam) | `:core:data` | S core (Codex) | 1 | W2 | ~400 + 500 tests | JVM and device tests green |
| W5 | `BlobStore` | `:core:data` | S core (Codex) | 2 | W2 | ~400 + 600 tests | ported vault tests green |
| W6 | Runner: journal, S0, M1–M6, L1–L3, M4 predicate, audit, `StepInterrupter`, `MigrationActions`, R2 | `:android:migration` | S migration (Opus) | 1–2 (core), 6 (hardening) | W2–W4 | ~2.5k + 3k tests | §10.2 runner rows green on the fixture |
| W7 | Gate screens, re-enter sheet, strings | `:ui:shell`, `:ui:words` | shell owner (Sonnet) | 2 | W6 contract (`GateState`, `MigrationActions`) | ~600 | Roborazzi goldens (light/dark, en/ar, 200 % text) on the VPS; emulator captures sent to the owner |
| W8 | Fixture recipe, seed flows, J1–J8, assertions | `e2e/`, `tools/flutter-fixtures/`, `:androidApp` androidTest | S migration (Opus) + coordinator (emulator lock) | 0 (J1 short form), 6 | W6 | ~1.2k | J1–J8 green on the switch candidate |
| W9 | R1 rollback build + guard | `rollback/flutter-final` branch of the frozen Flutter repo | Sonnet | 6 | W8 | ~120 Dart | J4 green |
| W10 | `FlutterFormatCompatTest` | `:androidApp` test + rollback branch test | Sonnet | 6 | engines writing those keys | ~600 | green on the candidate |
| W11 | Room v2 import | `:core:db`, `:android:migration` | S migration (Opus) | switch+1 (or 6 if P5 fires) | engine repositories | ~1.5k + 1.5k tests | §6.3 verification green; J1 extended with v2 |
| W12 | v3 cleanup | `:android:migration` | Sonnet | ≥ switch+2 | M-D3 OK | ~400 | cleanup journey green |
| W13 | Census in the bridge release (if 01 D4 ships and M-D2 is yes) | frozen Flutter repo | Sonnet | before the switch | D4 | ~150 Dart | the counts appear in Report a problem |

Order: W1 → W2 → (W3, W4 in parallel) → W6 → (W5, W7, W8 in parallel) → W9, W10 → switch → W11 → W12.

Shared-library rule: `:core:data` has one owner at a time; W3 and W4 are sequential commits by the same core owner, or
two agents in separate packages with the interfaces frozen first.

### Acceptance checks for the switch release

- A1. J1, J2, J3, J5, J6, J7, J8 green on the release candidate; J4 green on the R1 build.
- A2. `unchangedKeysDigest` equal on the real-install fixture: only the M2 and M3 write sets changed.
- A3. Appendix A regenerated from the final Flutter commit; no unclassified key.
- A4. `PrefsLedgerTest`, `ProfileSweepShapesTest`, `RoomProfileIdSchemaTest` (if Room ships) green.
- A5. `MigrationAuditPrivacyTest` green; a manual check of one Report-a-problem bundle shows counts only.
- A6. The R1 APK built, signed with the owner lineage, and hosted.
- A7. The owner's phone checks in §10.7 steps 1–2 pass before the install.

---

## 12. Owner decisions for this section

Work proceeds on the default until the owner answers.

| # | Question | Default (recommended) |
|---|---|---|
| M-D1 | Keep a ready "go back" build of the old app (R1) for the switch line, and who closes it? | Yes. It closes when the owner says the native app is fine, or when the Room import ships, whichever comes first |
| M-D2 | Add a counts-only "migration check" to the last Flutter (bridge) release, so we know the real data shape before the switch? | Yes, if the bridge release (01 D4) ships |
| M-D3 | When may the Flutter-era copies be deleted (backup, old password files, `app_flutter/`)? | Not before switch+2, and only after the owner says yes |
| M-D4 | If the P5 trigger moves Room into the switch release: keep the moved keys in the settings file for one release (01 §9.8 as written), or as files beside it? | As 01 says (in the file), unless the trigger fired because of the queue's size; then as files |
| M-D5 | The dead keys (`oc.glass`, `oc.effectsDepth/Edges/GlowColours/GlowSpeed`, `oc.olderDraftsBackupV1.*`, A2A data): keep them until v3, or delete at the switch? | Keep until v3 (nothing is deleted in the switch release) |
| M-D6 | Should "Reset saved sign-ins" also remove FactsBook and A2A secrets (excluded today)? | Yes, when those features are ported; until then they stay untouched |
| M-D7 | Read-state sharing: keep each server's current choice, or ask again after the switch? | Keep the current values |
| M-D8 | During R1's window, also write password changes back into the old app's password file, so going back never asks for a password? | No. It doubles where secrets are written; going back asks only for passwords changed after the update |

These do not reopen 01 §12: D2 (signers), D3 (versionCode 10000), D4 (bridge release), D6 (deferred features), D7
(Termux) and D9 (Room timing) are taken as decided there.

---

## 13. Risks

| Risk | Consequence | Guard |
|---|---|---|
| A strict native parser reads legitimate old data as corrupt | a fail-closed store locks the person out (queue won't send) | lenient ports, `AdoptedKeysFixtureTest` per engine on the real-install fixture |
| `DartJson` differs by a byte | pins, schedules, caches and drafts silently orphaned | Dart golden vectors (§2.5) |
| Prefs XML unparsable at the switch | Android loads it as empty and the first write destroys it | M1 integrity check blocks all writes; S0 backup |
| FSS undecryptable (Keystore change, v9 data) | servers need passwords | per-entry flags, one sheet, retries, FSS kept |
| Restore authority keys disturbed | phone server stops coming back after reboot | M4 equality + predicate; J3 |
| A worker or provider writes prefs before S0 | backup not the pre-update state | WorkManager on-demand init; no ContentProvider touches prefs; `.bak` copied |
| Native writes a format Flutter can't read during the R1 window | going back shows data as corrupt | §2.3 rule; `FlutterFormatCompatTest` |
| A large prefs file slows cold start | slower first frame | P5 trigger → Room; measured, not guessed |
| Real data shape differs from the fixture | an untested path on the owner's phone | M-D2 census; the fixture rebuilt from the exact build on the phone |

---

## Appendix A. Every SharedPreferences key the Flutter app and its Kotlin side write

Physical name on Android: `flutter.` + the key. Built from `test/fixtures/coverage/settings_preferences_ledger.json`
(188 entries, all classified by the Flutter gate) at HEAD `65e3f70a3`, plus the Kotlin-only keys, the `voice.*` keys
and the new native keys. Types: S string (JSON unless noted), B bool, I int (stored as Long), L string list. Scope:
`profile` = swept by §3.4; `app` = device-wide; `shared` = app-wide blob with per-profile entries removed by the deletion
transaction; `other` = see note. "secret" rows are secure-storage names the ledger also lists; their store is
`SecretStore` (Appendix B). Formats and caps for each key: `../map/state-data.md` §4.

### A.1 Ledger keys (Dart writers)

| Key shape | Type | Scope | Native owner | Handling | Note |
|---|---|---|---|---|---|
| `oc.a2aBearer.<agentId>` | secret | other | A2A (dropped, D6) | X | secure-storage name, not a pref; copied to SecretStore by M2, never read |
| `oc.a2aTasks.<agentId>` | S | other | A2A (dropped, D6) | X | <=30 tasks, <=4 MiB |
| `oc.activeProfile` | S | app | `:engine:profiles` | A-K | Kotlin `WidgetStatusIdentity` reads |
| `oc.agent.<id>` | S | profile | `:engine:connection` | A |  |
| `oc.agentFeed.<owner>` | S | profile | `:engine:agents` | A (DB at v2) | first-paint cache <=200 rows |
| `oc.agentHandoffs.<id>` | S | profile | `:engine:agents` | A | canonical copy under the source profile |
| `oc.agentHostSecret.<owner>` | secret | profile | `:engine:agents` | M2 | secure-storage name; ledger lists it |
| `oc.agentInstall.<owner>` | S | profile | `:engine:agents` | A | resume marker |
| `oc.agentModel.<id>` | S | profile | `:engine:agents` | A |  |
| `oc.agentPhoneGate.<owner>` | S | profile | `:engine:agents` | A | gate fingerprints; merged by the on-load owner rule (L5) |
| `oc.agentSignedOut.<id>` | S | profile | `:engine:agents` | A |  |
| `oc.agentTurnFailures.<id>` | L | profile | `:engine:agents` | A | <=100, redacted |
| `oc.appearance` | S | app | `:engine:settings` | A | default dark |
| `oc.appearanceAccent` | S | app | `:engine:settings` | A |  |
| `oc.appearanceGuide` | S | app | `:engine:settings` | A |  |
| `oc.appLocale` | S | app | `:engine:settings` | A-K + H1 | Kotlin `NativeStrings` reads; H1 mirrors it into the per-app locale |
| `oc.attentionTile` | S | shared | `:engine:attention` | A-K | Kotlin `AttentionTileService` reads; per-profile entries removed by deletion |
| `oc.autoApprove.<id>` | S | profile | `:engine:requests` | A | key `*` = server-wide |
| `oc.automaticActivity.<id>` | S | profile | `:engine:settings` | A | retained until deletion commit |
| `oc.automation.<id>` | S | profile | `:engine:settings` | A-K, M4 | **restore authority**; Kotlin `BuiltinLinux` reads; absent/false/corrupt differ |
| `oc.backgroundPauseDismissed` | S | app | `:engine:attention` | A |  |
| `oc.budgets.<id>` | S | other | — | R (L1) | retired; removed by L1 |
| `oc.builtinRecovery.<id>` | S | profile | `:engine:runtime` | A-K, M4 | **restore authority** (`version==2 && nativeAuthority==true`) |
| `oc.builtinServerOwner` | S | shared | `:engine:runtime` | A-K, M4 | one phone-runtime pointer; tombstoned on deletion |
| `oc.byoHost.<id>` | S | other | `:engine:integrations` | A | record schema 1; secret half is `oc.byoHostSecrets.<id>` |
| `oc.byoHostSecrets.<id>` | secret | profile | `:engine:integrations` | M2 | secure-storage name |
| `oc.byoHostSsh.<id>` | Keystore alias | other | `:engine:integrations` | K | AndroidKeyStore EC key, not a pref; survives by UID |
| `oc.certificatePin.<id>` | S | profile | `:engine:profiles` | A | valid only for the profile's current HTTPS origin |
| `oc.chat` | — | other | — | N | route name, not stored |
| `oc.chatAgent.<id>` | S | profile | `:engine:agents` | A |  |
| `oc.chatListShown.<id>` | B | profile | `:engine:connection` | A | absent = default by server kind |
| `oc.chatNoticesSeen.<id>` | L | profile | `:engine:connection` | A | <=400 |
| `oc.chatTail.<id>` | S | profile | `:engine:connection` | A (DB at v2) | first-paint cache |
| `oc.cleartextOk.<id>` | S | profile | `:engine:profiles` | A | exact origin string |
| `oc.codexToken.<id>` | secret | profile | `:engine:profiles` | M2 | secure-storage name |
| `oc.consumptionBudgets.<id>` | S | profile | `:engine:integrations` | A |  |
| `oc.contextUsage.<id>` | S | profile | `:engine:connection` | A | <=200 |
| `oc.conversationTitles.<id>` | L | profile | `:engine:connection` | A | <=300 |
| `oc.defaultNotices.<id>` | L | profile | `:engine:settings` | A |  |
| `oc.defaultProject.<id>` | S | profile | `:engine:settings` | A |  |
| `oc.dev.factsbook` | B | app | FactsBook (D6) | D | developer flag |
| `oc.developmentServices.<id>` | S | profile | `:engine:project` | A | <=20, <256 KiB |
| `oc.draft.<target>.<id or app>` | S | profile | `:engine:composer` | A | `.app` = app-wide (never swept) |
| `oc.draftAttachmentVault` | B | shared | `:engine:composer` | A | GC runs only when set |
| `oc.effectsActivityGlow` | B | app | `:engine:settings` | A |  |
| `oc.effectsBackgroundOn` | B | app | `:engine:settings` | A | live backgrounds (D11) |
| `oc.effectsCelebrations` | B | app | `:engine:settings` | A (L6 read rule) | legacy; folded into Motion at read |
| `oc.effectsCorners` | S | app | `:engine:settings` | A |  |
| `oc.effectsDensity` | S | app | `:engine:settings` | A |  |
| `oc.effectsDepth` | S | app | `:engine:settings` | I | written historically, ignored |
| `oc.effectsEdges` | S | app | `:engine:settings` | I | written historically, ignored |
| `oc.effectsGlowColours` | S | app | `:engine:settings` | I | read path ignores it |
| `oc.effectsGlowSpeed` | S | app | `:engine:settings` | I | read path ignores it |
| `oc.effectsGlowStyle` | S | app | `:engine:settings` | A | composer glow (owner likes it) |
| `oc.effectsLiveBackground` | B | other | — | R (L1) | retired |
| `oc.effectsLiveHeldByStyle` | B | app | `:engine:settings` | A |  |
| `oc.effectsLiveIntensity` | S | app | `:engine:settings` | A |  |
| `oc.effectsLiveStyle` | S | app | `:engine:settings` | A |  |
| `oc.effectsMotion` | S | app | `:engine:settings` | A | full/calm/off |
| `oc.effectsSurfaceStyle` | S | app | `:engine:settings` | A | solid default; unknown name reads as solid |
| `oc.externalAgents` | S | app | A2A (dropped, D6) | X |  |
| `oc.factsbook-node.stopped` | B | app | FactsBook (D6) | D | ledger entry `oc.factsbook` is this key |
| `oc.factsbook.<id>` | secret | profile | FactsBook (D6) | M2 | secure-storage name |
| `oc.factsbookNode.<id>` | S | profile | FactsBook (D6) | D | pairing link |
| `oc.factsbookOutbox.<id>` | secret | profile | FactsBook (D6) | M2 | secure-storage name |
| `oc.factsbookOwner.<id>` | secret | profile | FactsBook (D6) | M2 | secure-storage name |
| `oc.firstRun.notifyAsk` | S | other | `:engine:settings` | A |  |
| `oc.firstRun.state` | S | other | `:engine:settings` | A | an upgraded install is `done`, never welcomed |
| `oc.freeModelNoteDismissed.<id>` | L | profile | `:engine:connection` | A | bounded |
| `oc.genui.covered.<id>` | S | profile | `:engine:requests` | A |  |
| `oc.genui.enabled.<id>` | B | profile | `:engine:requests` | A |  |
| `oc.genui.journal.<id>` | S | profile | `:engine:requests` | A | routing metadata only |
| `oc.githubToken.<id>` | secret | profile | `:engine:project` | M2 | secure-storage name; JSON value |
| `oc.glass` | S | app | `:engine:settings` | I | glass removed 2026-10-26 |
| `oc.glassLiquidActive` | B | other | — | R (L1) | retired |
| `oc.glassLiquidOffUntil` | S | other | — | R (L1) | retired |
| `oc.glassLiquidStrikes` | I | other | — | R (L1) | retired |
| `oc.inFlowConsent.<id>` | S | profile | `:engine:settings` | A | three-state |
| `oc.keepLiveInBackground` | B | app | `:engine:attention` | A-K | Kotlin `LivePauseReceiver`, `BackgroundPauseStore` read |
| `oc.keptQueuedPrompts` | S | shared | `:engine:delivery` | A (DB at v2) | app-owned on purpose; never swept |
| `oc.lastProject.<id>` | S | profile | `:engine:connection` | A |  |
| `oc.linuxReset.pending` | S | app | `:engine:runtime` | A | resumable marker |
| `oc.location.<id>` | S | profile | `:engine:connection` | A |  |
| `oc.mainFeed.<id>` | S | profile | `:engine:agents` | A (DB at v2) | first-paint cache |
| `oc.managedServerRecovery.<id>` | S | profile | Termux (D7) | D |  |
| `oc.mobile.permissionRequests` | — | other | — | N | server-side session metadata field |
| `oc.mobileDownloadConsent.<id>` | S | profile | `:engine:media` | A |  |
| `oc.model.<id>` | S | profile | `:engine:connection` | A | `provider\|model` |
| `oc.modelExplicit.<id>` | B | profile | `:engine:connection` | A |  |
| `oc.modelLibrary.<id>` | S | profile | `:engine:connection` | A |  |
| `oc.modelNames.<id>` | S | profile | `:engine:connection` | A | <=40 |
| `oc.monitorAlerts.<id>` | L | profile | `:engine:attention` | A |  |
| `oc.monitorBusy.<id>` | S | profile | `:engine:attention` | A |  |
| `oc.monitorRoutes.<id>` | S | profile | `:engine:attention` | A |  |
| `oc.notify.checkInAfterMinutes` | I | app | `:engine:attention` | A (L2) |  |
| `oc.notify.finishedRuns` | B | app | `:engine:attention` | A | default true |
| `oc.notify.quietEnd` | I | app | `:engine:attention` | A (L2) | minutes after midnight |
| `oc.notify.quietStart` | I | app | `:engine:attention` | A (L2) | minutes after midnight |
| `oc.notify.quotaAlerts` | B | app | `:engine:attention` | A (L2) |  |
| `oc.notify.requests` | B | app | `:engine:attention` | A | default true |
| `oc.notify.version` | I | app | `:engine:attention` | L2 marker | `1` once merged |
| `oc.notify.wifiOnly` | B | app | `:engine:attention` | A (L2) |  |
| `oc.notifyRules.<id>` | S | profile | `:engine:attention` | A (L2 source) | legacy; kept untouched, swept |
| `oc.nudges.firstReplySeen` | B | app | `:engine:settings` | A |  |
| `oc.nudges.projects` | S | app | `:engine:settings` | A |  |
| `oc.nudges.shown` | S | app | `:engine:settings` | A |  |
| `oc.offlineQueue` | S | shared | `:engine:delivery` | A (DB at v2) | **dispatch markers byte-for-byte**; app-wide; up to 60 MiB |
| `oc.olderDraftsBackupV1.<id>` | S | profile | `:engine:composer` | I | "one release" backup nothing reads |
| `oc.orchestration.<id>.<field>` | S/L | profile | `:engine:team` (D6) | D | all AI Team plugin keys |
| `oc.paseoUsage.<id>` | B | profile | `:engine:integrations` | A |  |
| `oc.pendingAuth.<id>` | S | other | `:engine:integrations` | A | recovery metadata only |
| `oc.pendingCommands.<id>` | S | profile | `:engine:delivery` | A (DB at v2) | tombstones never evicted |
| `oc.pendingPromptPhoto` | S | shared | `:engine:composer` | A | `path` may point into `cache/` (image_picker) |
| `oc.permissionConsent.<id>` | S | profile | `:engine:requests` | A |  |
| `oc.phoneAgentGateMigration.<owner>` | L | profile | `:engine:agents` | A | L5 receipt list |
| `oc.phoneAgentOwner.<id>` | S | profile | `:engine:agents` | A-K, M4 | **restore authority**; Kotlin `BuiltinLinux` reads |
| `oc.phoneAgentsUsed.<id>` | B | profile | `:engine:agents` | A |  |
| `oc.phoneServerStopped.<id>` | B | profile | `:engine:runtime` | A |  |
| `oc.phraseChips.<id>` | S | profile | `:engine:composer` | A |  |
| `oc.phraseChips.enabled` | B | app | `:engine:composer` | A | device-wide |
| `oc.pinnedShortcuts` | S | shared | `:engine:attention` | A-K | Kotlin `PinnedSessionShortcuts` reads |
| `oc.profiles` | S | other | `:engine:profiles` | A-K (L4 read rule) | Kotlin `ScheduledPromptStore` reads; existence check for ~12 stores |
| `oc.promptHistory.<id>` | S | profile | `:engine:composer` | A (DB at v2) |  |
| `oc.promptStash.<id>.<stashId>` | S | profile | `:engine:composer` | A (DB at v2) | <=50 per profile; key identity validated on read |
| `oc.providerRuntimeRefresh.v1.<id>.<enc>` | B | profile | `:engine:connection` | A | `<enc>` = Dart `Uri.encodeComponent` |
| `oc.providerRuntimeUnloadable.<id>.<enc>` | S | profile | `:engine:connection` | A |  |
| `oc.quotaCollector.<id>` | B | profile | `:engine:integrations` | A |  |
| `oc.quotaMonitor.<id>` | S | profile | `:engine:integrations` | A (L2 source) | legacy+live |
| `oc.quotaToken.<id>` | secret | profile | `:engine:integrations` | M2 | secure-storage name |
| `oc.readAloudConsent` | B | app | `:engine:media` | A |  |
| `oc.readerPreferences.<id>` | S | profile | `:engine:settings` | A |  |
| `oc.readStateConsentMigration.v1` | S | other | `:engine:connection` | L3 journal |  |
| `oc.recentLocations.<id>` | S | profile | `:engine:connection` | A | <=8 |
| `oc.replySpeed.inApp` | S | app | `:engine:runtime` | A |  |
| `oc.returnBrief.<id>` | S | profile | `:engine:connection` | A | keys are Dart `jsonEncode` lists |
| `oc.savedPromptMigrationV1.<id>` | B | profile | `:engine:composer` | A (L7 marker) | older drafts moved once |
| `oc.scheduleDeleted.<id>` | B | profile | `:engine:schedule` | A-K | tombstone; Kotlin writes |
| `oc.scheduledPrompts.<id>` | S | profile | `:engine:schedule` | A-K | versioned envelope, CAS; fingerprint = Dart jsonEncode |
| `oc.secure.probe` | secret | other | desktop phase | N | Linux keyring probe; not on Android |
| `oc.session_id` | — | other | — | N | server metadata field name |
| `oc.sessionAgents.<id>` | S | profile | `:engine:connection` | A | <=200 |
| `oc.sessionDisplayModels.<id>` | S | profile | `:engine:connection` | A | <=200 |
| `oc.sessionDrafts` | S | shared | `:engine:composer` | A (DB at v2) | app-wide; storage key = Dart `jsonEncode([profileID,sessionID])` |
| `oc.sessionInventory.<id>` | S | profile | `:engine:connection` | A (DB at v2) | scope = sha256(Dart jsonEncode(...)) |
| `oc.sessionLinkBinding.<id>` | S | profile | `:engine:connection` | A | exactly 4 keys |
| `oc.sessionModels.<id>` | S | profile | `:engine:connection` | A | <=200 |
| `oc.sessionPins.<id>` | S | profile | `:engine:connection` | A | scope = Dart `jsonEncode([dir, ws])` |
| `oc.sessionViews.<id>` | S | profile | `:engine:connection` | A | <=2048; keys are Dart `jsonEncode` lists |
| `oc.setupAudit.<id>` | S | profile | `:engine:integrations` | A |  |
| `oc.setupRegistry.<id>` | S | profile | `:engine:integrations` | A |  |
| `oc.sharedProjects.<id>` | L | profile | `:engine:runtime` | A | union mirrored to Kotlin file `files/oc.sharedProjects` |
| `oc.shareSessionViews` | B | app | `:engine:connection` | L3 source | legacy app-wide value, read once |
| `oc.shareSessionViews.<id>` | B | profile | `:engine:connection` | A (L3) | absent = profile default |
| `oc.shareSessionViewsDefault.<id>` | B | profile | `:engine:connection` | A (L3) |  |
| `oc.shareSessionViewsOrigin.<id>` | S | profile | `:engine:connection` | A (L3) |  |
| `oc.sideFeed.<id>` | S | profile | `:engine:connection` | A (DB at v2) | first-paint cache |
| `oc.stashAttachmentVault.<id>` | B | profile | `:engine:composer` | A | owner marker; survives until files collected |
| `oc.tailscale.<id>` | B | profile | `:engine:profiles` | A |  |
| `oc.teamEditorDrafts.<id>` | S | profile | `:engine:team` (D6) | D |  |
| `oc.teamEngineAuth.<id>` | secret | profile | `:engine:team` (D6) | M2 | secure-storage name |
| `oc.teamEngineDeleted.<id>` | B | profile | `:engine:team` (D6) | D | tombstone; retained through deletion |
| `oc.teamMigrationKept.<id>` | B | profile | `:engine:team` (D6) | D |  |
| `oc.teamModel.<id>` | S | profile | `:engine:team` (D6) | D |  |
| `oc.teamRoles.<id>` | S | profile | `:engine:team` (D6) | D |  |
| `oc.teamTaskRoles.<id>` | S | profile | `:engine:team` (D6) | D |  |
| `oc.teamWorkerStart.<id>` | I | profile | `:engine:team` (D6) | D |  |
| `oc.teamWorkspace.<id>` | S | profile | `:engine:team` (D6) | D |  |
| `oc.termuxMigration.<jobId>` | S | other | Termux (D7) | D |  |
| `oc.termuxMigrationOffer.<id>` | B | profile | Termux (D7) | D |  |
| `oc.textSize` | S | app | `:engine:settings` | A |  |
| `oc.themePack` | S | app | `:engine:settings` | A | 31 packs |
| `oc.thermalGuard` | B | app | `:engine:runtime` | A | default true |
| `oc.thermalPause.<teamId>` | S | other | `:engine:runtime` | A | team id = host profile id |
| `oc.tips` | S | app | `:engine:settings` | A |  |
| `oc.transcript.reasoningExpanded` | B | app | `:engine:settings` | A |  |
| `oc.transcript.timestampsVisible` | B | app | `:engine:settings` | A |  |
| `oc.usageAlerts.v1` | S | shared | `:engine:integrations` | A (L9 flag) | `migrated` flag inside |
| `oc.usageAlertSeen.v1` | S | shared | `:engine:integrations` | A |  |
| `oc.usageComputerReader.<id>` | B | profile | `:engine:integrations` | A |  |
| `oc.usageLast.<connectionId>` | S | profile | `:engine:integrations` | A |  |
| `oc.usageWidget.v1` | S | app | `:engine:attention` | A-K | Kotlin `UsageWidgetRows` reads |
| `oc.variant.<id>` | S | profile | `:engine:connection` | A |  |
| `oc.widgetFollowup.<id>` | S | profile | `:engine:attention` | A-K | Kotlin `WidgetStatusTickets` writes |
| `oc.widgetSessions` | S | shared | `:engine:attention` | A-K | app-wide snapshot; Kotlin reads and patches |
| `oc.widgetTarget.<id>` | S | profile | `:engine:attention` | A-K | fingerprint = Dart jsonEncode |
| `oc.widgetUsage.<id>` | S | profile | `:engine:attention` | A-K |  |
| `oc.widgetUsageSubs` | S | shared | `:engine:attention` | A-K |  |
| `oc.withdrawnPrompts.<id>` | L | profile | `:engine:connection` | A | `<sessionID>\|<messageID>` |

### A.2 Keys written only by Kotlin (into the same file)

| Key shape | Type | Scope | Native owner | Handling | Note |
|---|---|---|---|---|---|
| `oc.widgetStyle.<owner>` | S | profile | `:android:widgets` | A-K | Kotlin-only writer (`WidgetOverlayStyle`, `WidgetV2Bridge`) |
| `oc.widgetActions.<id>` | S | profile | `:android:widgets` | A-K | Kotlin-only writer (`WidgetActionBridge`) |
| `oc.scheduleIndex.<id>` | S | profile | `:android:schedule` | A-K | Kotlin-only |
| `oc.scheduleClaims.<id>` | S | profile | `:android:schedule` | A-K | permanent occurrence tombstones; never derive from history |
| `oc.scheduleReceipts.<id>` | S | profile | `:android:schedule` | A-K | Kotlin-only |
| `oc.schedulePhoneLease.<key>` | S | app | `:android:schedule` | A-K | includes `oc.schedulePhoneLease.phone` |

### A.3 Keys outside the `oc.` namespace

| Key | Type | Scope | Native owner | Handling | Note |
|---|---|---|---|---|---|
| `voice.selected_pack` | S | app | `:engine:media` | A | default `base`; not swept (no `oc.` prefix) |
| `voice.language` | S | app | `:engine:media` | A |  |
| `voice.auto_send` | B | app | `:engine:media` | A | default false |
| `voice.read_aloud_natural` | B | app | `:engine:media` | A | default false |
| `desktop_window_state_v1` | S | app | desktop phase | N | Linux/Windows/macOS Flutter builds only |

### A.4 New native keys **[new]**

| Key shape | Type | Scope | Owner | Handling | Note |
|---|---|---|---|---|---|
| `oc.secretReentry.<id>` | S (JSON) | profile | `:engine:profiles` | new (M2) | `{"v":1,"names":[...],"reason":"...","at":ms}`; cleared when the person saves a new secret |
| `oc.native.flutterReadable` | B | app | `:android:migration` | new (M6) | mirror of the journal's rollback flag; read by the R1 guard |
| `oc.native.returnedAt` | S (ISO) | app | R1 rollback build | new (R1) | written only by the Flutter rollback build; consumed by native on return |
| `oc.native.movedToDb` | S (JSON) | app | `:android:migration` | new (v2) | logical keys moved to Room + import time |

## Appendix B. Secure-storage names (copied into `SecretStore` by M2)

| Name | Content | Native owner | If it can't be migrated |
|---|---|---|---|
| `pw.<id>` | OpenCode server password | `:engine:profiles` | server shows "Enter password" (`requiresPasswordReentry`) |
| `oc.codexToken.<id>` | Codex token or Paseo daemon password | `:engine:profiles` | "Enter token" (`requiresCodexTokenReentry`; a Paseo profile may legitimately have none) |
| `oc.teamEngineAuth.<id>` | phone project engine bearer | `:engine:team` (D6) | engine re-pairs when AI Team is ported |
| `oc.quotaToken.<id>` | usage-collector read token | `:engine:integrations` | collector setup writes a new token |
| `oc.githubToken.<id>` | JSON GitHub credential | `:engine:project` | Git page shows "Sign in to GitHub" |
| `oc.byoHostSecrets.<id>` | JSON `{schema:2,keyAlias,publicKey,deviceToken}` (the SSH private key is the Keystore alias `oc.byoHostSsh.<id>`, untouched) | `:engine:integrations` | BYO host shows "Set up again" |
| `oc.agentHostSecret.<owner>` | 64-hex password of the on-phone agent host | `:engine:agents` | the agent host rotates its password on next start; the agents section owns the rotation path [U: Paseo rotation proven in that slice] |
| `oc.factsbook.<id>`, `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>`, `oc.factsbook-node.passphrase` | FactsBook token, owner seed, unsent notes, node passphrase | FactsBook (D6) | handled when FactsBook is ported |
| `oc.a2aBearer.<agentId>` | external agent bearer | dropped (D6) | none |
| `oc.orchestration.<id>.grant` | reserved, never written | — | — |
| `oc.secure.probe` | Linux keyring probe | desktop phase | not on Android |

## Appendix C. Files and directories

| Path (under `/data/data/io.github.eslamasabry.opencode_mobile/`) | Owner today | Native owner | Migration |
|---|---|---|---|
| `files/linux/ubuntu/**`, `files/linux/ubuntu.ready` | Kotlin `BuiltinLinux` | `:android:linux` | untouched; J1 checks marker and inode count |
| `files/linux/ubuntu/home/oc/.oc-profiles/<id>/` | Kotlin (agent homes) | `:android:agenthost` | untouched |
| `files/projects/**` | Kotlin | `:android:linux` | untouched |
| `files/oc.sharedProjects` | Kotlin | `:android:linux` | untouched |
| `files/oc.teamEngine.<id>/`, `files/oc.teamEngineDeletion.<id>`, `files/oc.teamEngine.protection-required` | Kotlin `PhoneEngineNative` | `:android:team` (D6) | untouched |
| `files/termux-setup-v2.json`, `files/migrations/<jobId>/` | Termux setup and migration | Termux (D7) | untouched |
| `files/draft-attachments-v1/` | Dart vault | `:engine:composer` via `BlobStore` | untouched, same layout |
| `files/prompt-stash-attachments-v1/` | Dart vault | `:engine:composer` | untouched |
| `files/prompt-photo-recovery-v1/` | Dart vault | `:engine:composer` | untouched |
| `files/provider_logos/` | Dart cache | `:engine:integrations` | untouched (rebuildable) |
| `files/voice_models/` | Dart + sherpa | `:engine:media` / `:android:media` | untouched; no re-download (B-MIGR-009) |
| `files/diagnostics/report_problem.json`, `.pending` | Dart | `:engine:diagnostics` | untouched |
| `files/crash-diagnostics.json`, `files/crash-diagnostics-consent`, `files/native-last-crash.properties` | Dart + Kotlin `NativeCrashStore` | `:engine:diagnostics` + `:androidApp` | untouched; consent epoch kept |
| `files/.phone-engine-proof-<id>`, `files/.phone-engine-view-<id>`, `files/bb9-*.json` | QA fixtures | QA builds only | untouched |
| `files/native/` **[new]** | — | `:android:migration`, `:androidApp` | journal, audit, crash-loop record |
| `files/migration/flutter-v57/` **[new]** | — | `:android:migration` | S0 backup |
| `shared_prefs/*.xml` | see Appendices A and D | — | adopted / untouched |
| `app_flutter/` | Flutter engine and maybe Shorebird | — | listed by M1; deleted by v3 |
| `app_webview/` | Android WebView (dev preview) | `:ui:kit-content` WebView | carries over by itself |
| `cache/` (export plan, `image_picker` files, `proot-tmp`, `ubuntu-base.tar.gz`) | various | various | never cleared by the migration |
| `code_cache/` | ART / Flutter | — | ignored |
| `no_backup/androidx.work.workdb*` | WorkManager | WorkManager | upgraded by WorkManager; worker FQCNs frozen |
| `databases/` | (none today) | `:core:db` (`oc.db`) | created at v2 |

## Appendix D. Kotlin-owned SharedPreferences files (kept; accessed through `KotlinPrefsFile`)

```kotlin
// :core:data androidMain — the only other getSharedPreferences call site (Konsist rule 7)
public enum class KotlinPrefsFile(public val fileName: String) {
    Lifecycle("oc_lifecycle"), BackgroundNative("oc_background_native"), BackgroundPause("oc_background_pause"),
    ServerRecovery("builtin_server_recovery"), ComponentWriter("builtin_component_writer"),
    ServiceDiagnostics("builtin_service_diagnostics"), CameraPermissions("camera_permissions"),
    VoicePermissions("voice_permissions"),
}
public fun Context.kotlinPrefs(file: KotlinPrefsFile): SharedPreferences =
    getSharedPreferences(file.fileName, Context.MODE_PRIVATE)
```

| File | Keys | Native owner |
|---|---|---|
| `oc_lifecycle` | `running_services` (csv), `seen_exit_timestamp` | `:android:platform` |
| `oc_background_native` | `pausedByUser` | `:android:background` |
| `oc_background_pause` | `phase`, `reason`, `at`, `taskRemovalHintAt` | `:android:background` |
| `builtin_server_recovery` | `runtimeGeneration`, `wanted`, `userStopped`, `owner`, `enabled`, `restoreOwner`, `restoreReason`, `idleState`, `oc.builtinRuntimeOwnership.<p>`, `oc.builtinRuntimeDrain.<p>`, `drainOwner`, `drainAll`, `oc.builtinRecoveryBudget.<p>` | `:android:linux` |
| `builtin_component_writer` | `rootfsGeneration`, `ticket`, `generation` | `:android:linux` |
| `builtin_service_diagnostics` | `names` (string set), `<name>.exit\|uptime\|exitAt\|stopRequested\|restarts\|launched` | `:android:linux` |
| `camera_permissions` | `camera_requested` | `:android:platform` |
| `voice_permissions` | `microphone_requested` | `:android:media` |
| `flutter_image_picker_shared_preference` (plugin) | image_picker lost-data | ignored; listed by M1; v3 deletes it |
| `FlutterSecureStorage`, `FlutterSecureKeyStorage`, `FlutterSecureStorageConfiguration*` (plugin) | §7.5 | read by M2 only; v3 deletes |

## Appendix E. Sources

- `01-architecture-decision.md` §5, §6, §8, §9.5–§9.9, §10.2, §11 P5/P6, §12.
- Maps: `../map/state-data.md` (§1–§5, §8, §10, §15), `../map/builtin-linux.md` (§5.3, trap T1),
  `../map/android-platform.md` (native prefs), `../map/media-voice.md` (permission prefs).
- Research: `../research/network-data.md` (§0, §6–§11), `../research/testing-perf.md` (§6.6).
- Catalog: `../catalog/behaviors.jsonl` (rows cited in §10.1).
- Flutter source (read-only) at HEAD `65e3f70a3`: `lib/state/profiles.dart`, `profiles/models.dart`,
  `migration_runner.dart`, `prompt_shelf.dart`, `prompt_photos.dart`, `draft_attachments.dart`,
  `usage_subscriptions_controller.dart`, `pending_auth.dart`, `scheduling/agent_connection.dart`,
  `background/widget_action_runner.dart`, `diagnostics/crash_diagnostics.dart`, `diagnostics/report_problem.dart`,
  `test/coverage/settings_preferences_coverage_test.dart` and its ledger, `android/app/src/main/AndroidManifest.xml`,
  `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, and the Kotlin readers listed in Appendix A.2/D.
- Plugin source (`~/.pub-cache/hosted/pub.dev/`): `flutter_secure_storage-11.2.0/android/…` (`FlutterSecureStorageConfig`,
  `StorageCipherImplementationGCM`, `KeyCipherImplementationRSAOAEP`, `NamespacedConfigSource`, `MigrationBackup`,
  `lib/options/android_options.dart`), `path_provider_android-2.3.1` (`path_provider_android_real.dart`),
  `image_picker_android-0.8.13+25` (`SHARED_PREFERENCES_NAME`).
- Memory: `no-users-yet`, `apk-build-numbers`, `restore-proof-recipe`, `auto-install-latest-build`,
  `test-on-emulator-not-phone`, `quiet-hours-vps-only`, `emulator-lock-holder`, `dev-freeze-native-rewrite-2026-11-01`,
  `codex-backend-claude-frontend`, `no-raw-errors-rule`, `obvious-options-first`, `actions-name-their-target`.
- Not verified and left to the named proof: Shorebird's on-disk paths (M1 inventory on the first fixture), Android's
  empty-map behaviour for an unparsable prefs XML (`CorruptPrefsXmlTest`), Dart escape details (golden vectors),
  `CursorWindow` limits with the platform driver (`BigRowReadTest`), Keystore operation timings (W4).
