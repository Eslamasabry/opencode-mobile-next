# 05. Native storage and the day-1 data migration

Written 2026-11-01. Binding parent: `01-architecture-decision.md` (§5 modules, §6.3 Konsist rules 7 and 9, §9.5 threading,
§9.7 startup and migration, §9.8 persistence, §9.9 identity, §10.2 upgrade journey, §11 P5, §12 D2–D9). Nothing here
overrides it. Where this file adds detail that 01 did not fix, the addition is marked **[new]**.

## For the owner (plain words)

1. When the new app installs over the old one, it keeps the old settings file exactly where it is and keeps using it, so
   your servers, choices, drafts, queued prompts, schedules and widgets are all still there.
2. Saved passwords are unlocked once with the old app's key and put into the new app's own locked store; the old copy
   is never deleted in that release. Any password or sign-in you typed that can't be read is listed once so you can
   enter it again; ones the app made itself are quietly made new.
3. Before anything else happens, the app makes a byte-for-byte safety copy of the old settings and password files, and
   the Linux folder, projects, voice models and attachments are never moved.
4. If something goes wrong, you see one plain screen with "Try moving your data again", "Report a problem" and
   "Restore data from before the update"; nothing is ever deleted silently.
5. For the first native release we also keep a tested script that builds a "go back" version of the old app on the day
   it is needed, and it can read everything the new app wrote. Every step is proven on the emulator with a real old
   install, and recorded on video, before your phone gets it.

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
- Step 0 (byte backup into `files/migration/flutter-v57/`) runs only on the first native launch, on the `StartupFence`
  thread, **never on the main thread** (§7.1, 01 §9.7 as amended 2026-11-01). Everything else runs in `MigrationRunner` on IO behind `MigrationGate`, and `EntryRouter`
  holds every launch intent until the gate opens.
- The migrator is `:android:migration` (S migration owner, Opus). It may depend only on `:core:common`,
  `:core:security`, `:core:data`, `:core:model` (+ `:core:db` when Room ships) (§6.1).
- Konsist rule 7: `getSharedPreferences(` only in `:core:data` `androidMain` and `:android:migration`. Rule 9: every
  per-profile key matches `oc.<what>.<profileId>` and is registered with `ProfileScope`.
- versionCode base **10000**, same package, signer lineages unchanged (§9.9, D2, D3).

**Added here [new]:** the journal format, the step list with ids (S0, M1–M6), the exact secure-storage decryptor, the
three legacy transforms the runner owns (L1–L3) and the ones engines own (L4–L9), the failure classes and their copy,
the rollback tiers (R0 fix forward, R1 Flutter rollback build, R2 in-app restore), the native preference ledger, the
`DartJson` canonical encoder and the frozen `ProfileFingerprintV1` projection, the gate-exempt `MigrationReader`, the
Kotlin-owned prefs accessor, the versionCode allocation rule, the Room v2 import, the v3 cleanup, the fixture recipe,
the tests, the work units and the owner decisions M-D1…M-D8.

**Source facts** below were read in `/home/eslam/Storage/Code/wt/oc_app-batch1` at HEAD `65e3f70a3` (app `2.1.0+57`,
tag `v2.1.0+57`) and in the plugin sources under `~/.pub-cache` (`flutter_secure_storage` 11.2.0,
`shared_preferences_android` 2.4.27, `path_provider_android` 2.3.1, `image_picker_android` 0.8.13+22 per
`pubspec.lock`, P5 §1.6). Tags: **[V]** read
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
public enum class UndecodableKind { UnknownType, BadJsonList, BadJavaList, BadDouble, BadBigInteger }

public interface KeyValueStore {
    /**
     * Logical keys only: never the "flutter." prefix. In-memory, cheap, immutable.
     * Only valid once MigrationGate is Open (§2.2): before that, debug/qa/test builds throw GateNotOpen.
     * Code that must read earlier uses MigrationReader instead.
     */
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
    /**
     * Atomic compare-and-set of one string key (schedule envelope CAS, widget snapshot patches).
     * Order: gate.awaitWritable(), then withContext(io), then lock → read → commit() → unlock with no
     * suspension point while the lock is held (§2.2).
     */
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
- `compareAndSet` uses one `ReentrantLock` for the file, so blocking (`goAsync`) and suspend callers share it. The
  order is fixed: first `gate.awaitWritable()`, then `withContext(dispatchers.io)`, and only inside that block
  `lock.lock()` → read → `Editor.commit()` → `unlock()` in `try/finally`, **with no suspension point while the lock is
  held**. (`ReentrantLock` is thread-bound; a coroutine that suspends inside `withLock {}` can resume on another thread
  and throw `IllegalMonitorStateException` or deadlock.) `commitBlocking` takes the same lock the same way.
  `ScheduledPromptStore`'s CAS moves onto it unchanged in meaning; the schedule double-fire guard depends on it. A
  Detekt rule in `:quality` (`NoSuspendInsideLock`) bans calls to suspend functions inside `withLock {}` and between
  `lock()`/`unlock()` in every module.
- **Every mutation of the file takes that one lock** (audit 04+05 #8), not only CAS: `commit`, `commitBlocking`,
  `compareAndSet`, `MigrationWriter` writes and the failure compensation. The lock is held from reading the previous
  values, through `Editor.commit()`, to the end of any compensation, so no other writer can land between them. The
  compensation is **conditional**: after a refused commit it puts a key back only if the in-memory value is still the
  one this edit wrote (another process-local writer cannot have changed it, because it would have needed the lock;
  this guards against a future path that bypasses the store). Kotlin surfaces that run in the same process reach the
  file only through `KeyValueStore` / `commitBlocking`, so per-profile lanes are not the serialization point for shared
  blobs (widget snapshots, the schedule index): the file lock is. `FlutterPrefsLockTest` races (a) CAS against a plain
  commit of the same key on two threads, 10,000 rounds, and (b) a refused write against a successful write of the same
  key (fake `Editor` that refuses the first commit), and asserts that no update is lost and no stale value is restored.
- `changes` comes from one `OnSharedPreferenceChangeListener` (held strongly), mapped to logical keys.
- **The gate covers reads and writes, with named exceptions.** 01 §9.7 step 5 says stores await `MigrationGate`; this
  is how:
  - **Engines start only after the gate is Open.** `:engine:entry`'s bootstrap calls `gate.awaitOpen()` before it
    constructs or starts any engine (Metro `Lazy`/`Provider` handles, never eager). So no engine reads `oc.profiles`
    before M2 has filled `SecretStore`, and no engine caches notification settings before L2.
  - **Writes** (including Kotlin surfaces) suspend on `gate.awaitWritable()`. The migrator writes through its own
    `MigrationWriter` capability, which the gate lets through.
  - **Reads through `KeyValueStore`** are in-memory and non-suspending, so they can't wait. Before the gate is Open
    they throw `GateNotOpen` in debug, `qa` and test builds. In release they return the value and add one to an
    `earlyRead` count in diagnostics, so a missed path is found without crashing a person's phone.
  - **`SecretStore.read`** suspends on `gate.awaitOpen()` for every caller except the migrator (§4.2).
  - **`MigrationReader`** (`:core:data` androidMain) is the only way to read before Open: raw prefs values and
    Flutter-format decoding, with no secrets. Konsist allows it only in `:android:migration`, the crash-loop guard and
    `MainActivity`'s locale read in `:androidApp` (§8.2), the phone-server restore receiver in `:android:linux` and
    the alarm re-arm in `:android:schedule` (§7.1). The gate screens in `:ui:shell` read only `GateState`, never a
    store.
  - Receivers, workers, widget providers, tiles and services follow §7.1.
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
| string starting `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy` | `BigInteger(s, 36)` → `Int64` when it fits in 64 bits (Dart receives an `int` through the message codec, P5 §1.6), else `Undecodable(BadBigInteger)` (never written by this app) | never written |
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

// :core:common — the only module allowed expect/actual (01 §5.2). commonMain has no SHA-256 and may not use
// java.* (01 §7.2), so the digest is a platform actual: androidMain and desktopMain wrap
// java.security.MessageDigest("SHA-256"); iosMain (later) wraps CommonCrypto CC_SHA256. No new library.
public expect class Sha256() {
    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)
    public fun digest(): ByteArray          // resets the instance
}
```

`DartJson.sha256Hex` and every common-code digest (`ProfileScope` blob owners, `BlobStore`'s streaming check, the
import hashes in §6.3) use `Sha256`. `Sha256Test` (commonTest) checks the NIST vectors on every target.

Escaping in `encode` follows Dart: `"` → `\"`, `\` → `\\`, `\b \t \n \f \r`, other code units below 0x20 and lone
surrogates as `\u` + four hex digits; `/` and non-ASCII are not escaped [U: hex case and lone-surrogate handling are
fixed by the golden vectors below, generated by Dart].

| Where Dart's encoding is load-bearing | Exact expression (Dart) |
|---|---|
| Schedule `profileFingerprint` and widget target `fingerprint` | `sha256(utf8(jsonEncode(profile.toJson()..remove('name'))))`; the v57 `toJson` key order: `id, name, baseUrl, backend, username, [codexDirectory], flavor, requiresOpenCode2Migration, openCode2HistoryPending, [serverVersion], [orchestration{provider,url,city,hostMode,hostKind,front,[enabledAt]}]` [V `profiles/models.dart:154,301`, `scheduling/agent_connection.dart:15`, `background/widget_action_runner.dart:19`]. Native computes it from the frozen `ProfileFingerprintV1` projection below, never from `ServerProfileJson.toJson` |
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
`:android:migration`) is the lenient port of `ServerProfile.fromJson`/`toJson` and owns the stored key order.

**`ProfileFingerprintV1` [new]** (`:core:model`, next to `ServerProfileJson`) is a separate, frozen projection used
only for the two fingerprints. It emits exactly the v57 fields above, minus `name`, in that order, with Dart's
conditional emission (`codexDirectory`, `serverVersion` and `orchestration` only when set; `enabledAt` only when set).
It never follows `toJson`. Reason: once R1 retires, §2.3 rule 1 no longer holds, and a new always-emitted profile field
in `toJson` would change every fingerprint. Stored schedules would then refuse to run and widget targets would stop
matching, with no visible error. A deliberate fingerprint change is a numbered migration step (§7.11) that rewrites the
stored `profileFingerprint`/`fingerprint` values in the same commit. `DartJsonTest` asserts that adding a field to
`ServerProfileJson` (a test-only subclass with one extra emitted field) leaves both fingerprints unchanged.

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
    /** Non-key resources (Keystore aliases, WorkManager tags, shortcuts, tiles, widget ledger, team dirs, agent homes)
     *  and EMBEDDED records inside app-wide JSON values (§3.5 steps 3–5); [order] lower runs first. */
    public fun registerCleaner(cleaner: ProfileResourceCleaner, order: Int)
    /** The sweep rule, verbatim from ProfileStore.profileScopedPreferenceKeys (§3.4). */
    public fun scopedKeys(profileId: ProfileId, store: KeyValueStore): Set<String>
    public fun ownedData(profileId: ProfileId, store: KeyValueStore, secrets: SecretStore): OwnedData
    public fun reservedSegments(): Set<String>
    /** §3.5 steps 6–7: runs the cleaners in order, then the generic sweep of scopedKeys minus every shape registered
     *  with retained = true. Computed LATE (after the lanes drain). Idempotent; counts per owner, never values. */
    public suspend fun sweep(profileId: ProfileId): Outcome<SweepReceipt>
}

public interface ProfileResourceCleaner {
    public val owner: String                                                   // module path, for the receipt
    public suspend fun clear(profileId: ProfileId): Outcome<Unit>
}
public data class SweepReceipt(val perOwner: Map<String, Int>, val failedOwners: List<String>)

public data class OwnedData(
    val prefKeys: Set<String>, val secretNames: Set<String>,
    val tables: List<String>, val blobOwners: List<Pair<BlobRoot, String>>,
)
```

`KeyShape` gains `val retained: Boolean = false` (`oc.automation.<id>` and the other §3.5 step-2 keys). **This is the
only `ProfileScope` shape** (Astra B1): 03 §10.5's earlier `registrations`/`sweep` interface and its
`ProfileScopeRegistration` are folded into it, and 03 now points here. `ProfileScopeContractTest` (slice 1, before the
freeze) compiles three real consumers against it: the M1 census (`scopedKeys`, `ownedData`), the deletion transaction
(`sweep`, cleaners) and the schedule store's registration.

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
per-profile entries (`oc.builtinRuntimeOwnership.<id>`, `oc.builtinRuntimeDrain.<id>`, `oc.builtinRuntimeRecipe.<id>`,
`oc.builtinRecoveryBudget.<id>`, `oc.builtinRecoveryReceipts.<id>`, and `restoreOwner`/`drainOwner` when they name the
id, all in `builtin_server_recovery`; `files/oc.teamEngine.<id>/`; agent homes in the rootfs) by the owning module's
`forgetProfile(id)` hook.

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
the profile, so a deleted server's prompts never linger in a frozen copy. The same `ProfileStructuralEdit` (steps 3–5,
7, 9) is applied to every retained Flutter-era copy (the S0 backup, `replaced-*`, `start-fresh-*`, each with its `.bak`
twin) and to the `v2-sources/` files, by the `FlutterEraSweep` cleaner (16 §1.4; Astra M1).

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
    /** Suspends until MigrationGate is Open (§2.2); only the migrator's own handle skips the wait. */
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
- The gate wait is in the binding, not in the store: Metro binds `SecretStore` for everyone as `GatedSecretStore`
  (awaits `gate.awaitOpen()` in `read`, `write`, `delete` and `names`), and binds the raw `KeystoreSecretStore` only
  into `:android:migration` as part of `MigrationWriter`. Konsist rule 7's allowlist covers this binding too.

### 4.3 Names: Appendix B lists every name, its owner, and what happens when it can't be migrated.

---

### 4.4 The credential ledger: durable transitions **[new]** (Astra B3)

`files/native/credential-ledger.json` (names, revisions and states only, never values; atomic write: `.tmp`, fsync,
rename; outside every snapshot, like the outbound tombstones) records, per secret name:

| Field | Meaning |
|---|---|
| `rev` | native revision, +1 on every native write or delete of that name |
| `state` | `live` or `deleted` (a **tombstone**: sign-out, Reset saved sign-ins, server removed) |
| `legacy` | the FSS ciphertext digest the native value last agreed with (`null` once native diverged) |
| `staleQueued` | true while the name is listed in `oc.native.staleLegacySecrets` for the R1 guard |
| `guardConsumed` | transition ids in which the R1 guard deleted this FSS entry (written by the guard, see §9.3) |

Rules:
1. **Invalidation is persisted first.** A native change or delete writes the ledger (`rev+1`, `state`, `legacy=null`,
   `staleQueued=true`) and adds the name to `oc.native.staleLegacySecrets` in the prefs file **before** it changes
   `SecretStore`. A crash between leaves a stale marker for a value that did not change, which costs one "Enter
   password" in R1, never a resurrected credential.
2. **M2 retries consult it** (above): any ledger record suppresses the retry.
3. **The R1 guard records what it consumed** before it deletes: it writes `oc.native.guardDeleted.<transitionId>` (the
   list of names, a Flutter-readable `StringList`) with `commit`, then deletes those FSS entries, then removes
   `oc.native.staleLegacySecrets`. The transition id is `oc.native.returnedAt`'s first value for that rollback period.
4. **`R1.return` reconciles against both** (§9.3 step 3): a name absent from FSS is a person's deletion only if it is in
   neither an unconsumed `oc.native.guardDeleted.*` record nor the ledger as `staleQueued`. Then it moves each such
   record into the ledger's `guardConsumed` and removes the pref, last.
5. `CredentialTransitionTest` (JVM, P5's `CredentialReconciler` + fault points) kills the process at every step of:
   native change A→B → R1 guard → death → native return (B survives); native reset → R1 (no credential) → return (stays
   deleted, no M2 refill); Flutter deletion in R1 → return (native deleted); and two rollback cycles back to back. J4b
   runs the first two on `emulator-5584`.

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

1. Read the source key raw. Classify it **with the Dart reader's own rule for that collection** (ported with the
   engine, §7.8), before splitting:
   - `oc.offlineQueue` and `oc.keptQueuedPrompts`: Dart's `OfflineQueueStore.load` fails the **whole** queue closed when
     the JSON is not a list, when any element fails `QueuedPrompt.fromJson` (empty `id`, `profileID` or `sessionID`) or
     when an `id` repeats [V `lib/state/offline_queue.dart:150-155,326-342`]. Such a queue is imported as one
     `collection_state` row (`readable = 0`, raw text kept) and nothing goes into `queued_prompt`. That is today's
     meaning: nothing sends, and only the person's explicit "clear" recovers it.
   - Other collections: whatever their Dart reader does with a bad element (skip, or fail closed), the import does the
     same, and records skipped elements in `collection_state` with their raw text instead of dropping them.
2. For a readable collection, split into JSON elements with lenient parsing; read only the fields the table keys need
   (`id`, `profileID`, `createdAt`, `dispatchedAt`, `updatedAt`) as primitive content.
3. In one transaction: `INSERT OR IGNORE` every row with `sha256 = DartJson.sha256Hex(entry_json)`.
4. Verify against what the Dart reader would have kept: imported row count + quarantined element count equals the
   element count, and the sorted list of row hashes equals the sorted list of hashes of the kept source elements.
5. Record `{count, quarantined, digest}` in the journal under `migrations."native.migration.v2".<collection>`.
6. Dispatch markers are copied byte-for-byte inside `entry_json`. The import never evicts, expires, clears a marker or
   re-enqueues (state-data risk 6).

**A collection that does not verify never blocks the gate.** Its transaction is rolled back, the collection stays on
its source key (it is not added to `oc.native.movedToDb`, and its engine keeps reading the key as on the switch line),
and the journal records `{state: "kept", reason}` with no retry until the next release's step. A database that cannot
be created or opened keeps every collection on its source key the same way; v2 never blocks the gate. The repository
interfaces stay the same (01 §9.8), so each engine's repository picks the key-backed or Room-backed source from
`oc.native.movedToDb`.

Re-run: a recorded collection is skipped; a crash between commit and journal write is detected by step 4 succeeding on
re-run (`INSERT OR IGNORE` makes it a no-op).

### 6.4 Source keys during the one-release window

- After a collection imports, its source key stays in the prefs file, **frozen**: native stops reading and writing it.
  `oc.native.movedToDb` lists the moved keys and the import time.
- Profile deletion still scrubs the deleted profile's rows from the frozen app-wide copies (§3.5).
- The next release's cleanup (`native.migration.v3`, §9.5) removes them.
- **If Room arrives because the P5 trigger fired** (01 §9.8), the frozen keys may not stay in the XML: the trigger
  was the size of that XML, and Android loads the whole file at every cold start. Resolved before fan-out (audit 04+05
  #12, 01 §9.8 amended): each moved collection's source value is written to
  `files/migration/v2-sources/<collection>.json` (`.tmp`, fsync, rename) with its SHA-256 in the journal, read back and
  verified, and only then removed from the prefs file in one `commit` (journal phases `copied → verified → removed`,
  each resumable; a crash between phases resumes, never re-imports). The S0 backup still holds the original bytes, so
  recoverability is the same as keeping the key. Acceptance: the post-migration candidate passes the **same** P5
  threshold that fired the trigger (median cold start within 50 ms of the 100 KB seed; no widget update or worker start
  over 100 ms loading prefs). When Room arrives at switch+1 without the trigger, the frozen keys may stay in the XML
  for the one release, as above. (This replaces the former owner decision M-D4.)
- The v2 import sets `rollback.flutterReadable = false` (the Flutter app can't read `oc.db`), which retires R1 (§9.3).
  The order is fixed for this and every step with `breaksFlutterReadability` (§7.11): **first** commit
  `oc.native.flutterReadable = false` to the prefs file (what the R1 guard reads), **then** write the journal flag,
  **and only then** write anything the Flutter app can't read (the first Room row, the first frozen source key). A
  crash between the steps leaves R1 refusing to open, never opening onto stale frozen data.

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
   3. crash-loop guard (files/native/crashloop.json, ≤ 4 KiB; the only file read on the main thread)
   4. build AndroidAppGraph (stores are lazy; every store that opens a backed-up file waits for the StartupFence,
      reads and writes wait for MigrationGate, §2.2)
   5. StartupFence.start()                         ← one IO thread, application scope, single-flight; NOT on main:
      a. RestoreTransaction.recoverBeforeOpen()    ← finishes an interrupted R2 / Start fresh (§9.4)
      b. S0 FlutterBackup.runIfNeeded()            ← first native launch only; crash-safe publish (S0 below)
      c. write files/native/app-locale (≤ 64 bytes) from a MigrationReader read of oc.appLocale
      d. fence passed → MigrationRunner.start()    ← same thread, then IO dispatcher
   6. warm the prefs file on IO (after the fence)
   (later) :engine:entry bootstrap: gate.awaitOpen() → construct and start engines
```

**No ANR on the first-launch path (hard rule, audit 01+03 #8).** Nothing before the first frame or inside any
component callback copies, renames, hashes or parses a backed-up file on the main thread. The S0 copy (up to the
largest real prefs file, ≥ 60 MiB of queue on the owner's phone) runs on the fence thread. While the fence runs the
gate is `Running("S0")`, so the "Updating your app…" screen draws at once; an S0 failure becomes
`Blocked(LowSpace | BackupInvalid)` with the normal failure screen, never a crash in `onCreate`. `StartupFence` is the
only owner of the "first `getSharedPreferences` of a backed-up file happens after S0" rule (P5 open risk 5: loading a
file with a `.bak` twin renames it): `FlutterPrefsStore`, `MigrationReader` and the FSS reader call
`fence.awaitPassed()` before their first `getSharedPreferences`, and a Konsist rule allows that call for those three
files only. `KotlinPrefsFile` files are not in the backup and need no fence. Proof (W8, J8b): the `qa` build with
StrictMode `detectDiskReads/Writes` + `penaltyDeath` on the main thread, a 64 MiB seeded prefs file and
`qa-delay-ms=15000` on S0: the first frame draws, no StrictMode death, no ANR in `dumpsys activity anr` or
`ApplicationExitInfo`, and the receivers below finish inside their budgets.

- On the owner's phone the first native process is usually started by `MY_PACKAGE_REPLACED` right after
  `adb install -r`, with no UI. The runner finishes there. The UI part ("ask once", §8) reads the journal at the first
  UI launch.
- Receivers, workers, widget providers, tiles and services `await` the gate in their own coroutines (`goAsync`,
  `CoroutineWorker`), never on the main thread (01 §9.7). There are two kinds:
  - **Anything that may start a foreground service never waits for the gate first.** `MY_PACKAGE_REPLACED` and
    `BOOT_COMPLETED` give the app a temporary allowlist for starting foreground services of about **20 s**
    (`DEFAULT_BOOT_TIME_TEMP_ALLOWLIST_DURATION`, reason `REASON_PACKAGE_REPLACED` / `REASON_BOOT_COMPLETED`
    [R: AOSP `ActivityManagerConstants`; per-version value proven by J3b]). A WorkManager job has no such exemption,
    so on API 31+ a start from it throws `ForegroundServiceStartNotAllowedException`. Therefore:
    - `BuiltinRestoreReceiver` makes its decision from `MigrationReader` (raw reads of the restore keys, §7.4 M4) and
      the Kotlin-owned `builtin_server_recovery` file, which is not gated. If armed, it starts `BuiltinServerService`
      at once, inside the window, exactly as today (one try, no retry scheduler, `BuiltinRestoreReceiver.kt`). If
      `MigrationReader` reports the prefs file unreadable (Android loaded a non-empty file as an empty map, M1's
      test), the receiver fails closed: it does not start, and records `restoreReason = "updating"` in the
      Kotlin-owned file. The first activity start or H2 (both in the foreground) then starts the server once the gate
      opens, and never if M1 blocks. Any adopted-file write the running service makes goes through `commit`, which
      waits for the gate inside the service.
    - This also keeps the cold-start recipe current: the `PACKAGE_REPLACED` path accepts a recipe whose
      `packageVersion` is at most the new versionCode and rewrites it, while the `BOOT_COMPLETED` path needs an exact
      match [V `NativeServerRestoreEvent.kt` `recipeForEvent`]. A skipped update-time restore would leave the old
      versionCode in the recipe, and the next reboot would not restore either.
    - The widget action runner's 30 s short service and any other service started from a user tap follow the same
      rule: start inside the tap's exemption, then wait for the gate inside the service.
  - **Everything else has a component-specific budget** (audit 04+05 #9). Android counts cold-process start inside a
    broadcast's deadline, which is 10 s for a foreground broadcast on Android 13 and lower and 10–20 s on newer
    versions, and `goAsync()` does not extend it. So the budget is measured from **`ProcessStart.at`**, not from
    `onReceive`:

    | Component | May wait for the gate | Then |
    |---|---|---|
    | Broadcast receivers (alarm, widget update, notification action, `TIME_SET`, `LOCALE_CHANGED`, …) and `AppWidgetProvider`s | until `min(onReceive + 4 s, ProcessStart.at + 6 s)` | persist a follow-up record (kind + action only) to the journal's `followUp` list **first**, enqueue `MigrationFollowUpWorker`, then `finish()`; `finish()` always runs by `ProcessStart.at + 7 s` |
    | Restore and boot receivers that may start a foreground service | the StartupFence only, at most 3 s (never the gate) | start the service inside the exemption, or fail closed with `restoreReason = "updating"` (above) |
    | `TileService` callbacks (`onStartListening`, `onClick`) | never (main thread) | show the tile's last known state; update it from `appScope` when the gate opens |
    | Foreground services | never before `startForeground` (11 §9.2 rule 1) | after `startForeground`, inside the service, without a deadline; on `onTimeout` the body commits its receipt and stops |
    | `CoroutineWorker`s (incl. `MigrationFollowUpWorker`) | while the gate is `Running`, up to 9 min (WorkManager stops a worker at 10) | `Result.retry()` (exponential backoff, 30 s start); if the gate is `Blocked`, `Result.success()` after recording the kind in `followUp`, so a blocked install never spins; the gate replays `followUp` when it opens |

    `ScheduledPromptReceiver` re-arms alarms from a `MigrationReader` read (after the fence, inside the same budget)
    without waiting for the gate (alarms are OS state; M3 never touches schedules); its prefs writes
    (`oc.scheduleIndex.*`) wait. `MigrationFollowUpWorker` is a **normal (not expedited) one-time** WorkManager
    request with a unique name per kind. It never starts a foreground service, so it needs no `getForegroundInfo()`.
    On the fast path (no Room import) the gate opens in about a second, so this fallback is for the blocked and slow
    cases. `ReceiverBudgetTest` (Robolectric, fake clock) asserts `finish()` by `ProcessStart.at + 7 s` with a gate
    that never opens, for every receiver in the component freeze list.
  - `MigrationFollowUpWorker` is a new worker class. 01 §9.9 says the worker names must *equal* the Flutter list; the
    `:quality` component-freeze check is therefore written as **"a superset of the Flutter 2.1.0+57 list"** (every
    Flutter-era name present, unchanged), new names are listed in the native component ledger
    (`quality/src/test/resources/component-ledger.json`), and the coordinator amends the wording of 01 §9.9 to match.
- `MainActivity` draws the kit "Updating your app…" state on the first frame and the 8 s "Try again" rule from 01 §9.7
  (worded "Try moving your data again" and single-flight, §8.2).
  Before `setContent` it reads only `files/native/app-locale` (≤ 64 bytes, written by fence step 5c and updated by
  `:engine:settings` whenever `oc.appLocale` changes), never the prefs file, so the gate screens use the person's
  in-app language and direction. On the very first native launch the file does not exist until S0 is done: the
  first frames are the wordless kit "Opening" mark (no text, so no direction to get wrong), and the words appear in
  the right language as soon as the fence publishes the locale (§8.2).

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
  "gate": { "firstOpenAt": 1730457601000 },
  "rollback": { "flutterReadable": true, "lastReturnHandledAt": null, "returnsHandled": 0 },
  "pendingSwap": null,
  "followUp": [],
  "blocked": null
}
```

- `gate.firstOpenAt`: written by the runner right before the gate opens for the first time. Engines start only after
  Open (§2.2), so it is also "engines started". R2 is offered only while it is null (§9.4).
- `pendingSwap`: `{"kind": "restorePreUpdate" | "startFresh", "at": ms, "phase": "requested" | "moved"}` while an R2 or
  Start fresh file move is under way (§9.4).
- `followUp`: kinds of surface work handed to `MigrationFollowUpWorker` (§7.1), kind names only.
- `storageGeneration`: `prefs-v1` until the transaction that makes Room authoritative writes `room-v2` (R2 eligibility,
  §9.4). `outboundHold`: `true` after R2 or Start fresh until the person has reviewed restored outbound work (§9.4
  step 5). Both are additive fields (`schema` stays 1).
- The FSS snapshot used by `R1.return` (§9.3) is **not** in the journal: it lives in `files/native/fss-snapshot.json`
  (entry name → SHA-256 of the stored *ciphertext* string). It reveals nothing about the values (AES-GCM with a random
  IV), but it is still never added to a problem report.

- `source.kind`: `flutter` (Flutter data found), `none` (fresh install, or a device restore with no data — both are a
  clean first run, never a failure [R state-data risk 15]), `startFresh` (after the person chose Start fresh).
- Step `state`: `pending | running | done | failed`. `attempts` is incremented and written **before** the step runs, so
  a crash counts. Three attempts without `done` → `failed` → `blocked = {"reason": "StepCrashed", "step": "M2"}`.
- Written atomically: `migration.json.tmp`, `FileDescriptor.sync()`, rename, fsync of `files/native/`.
- Readers ignore unknown fields. A journal with a higher `schema` than the build understands is left untouched and
  every known step id is honoured as recorded (this can only happen through a downgrade, which Android refuses).
- The values are counts, states and times only. No secret, prompt text, server address or key name with user content.

### 7.3 `MigrationGate`, split by who may see it

01 §6.1 forbids `:ui:shell` to depend on `:core:data`, and `:android:migration` can't see `:core:domain`. The only
module all four users can see (`:core:data`, `:android:migration`, `:engine:entry`, `:ui:shell`) is `:core:model`. So
the state, notices and actions live there, and only the write gate stays in `:core:data`:

```kotlin
// :core:model commonMain, package io.github.eslamasabry.oc.core.model.migration
public sealed interface GateState {
    public data object Closed : GateState
    public data class Running(val step: String) : GateState
    public data class Open(val firstNativeLaunch: Boolean, val notices: MigrationNotices) : GateState
    public data class Blocked(
        val reason: BlockReason, val neededBytes: Long? = null,
        val restoreAvailable: Boolean,                   // R2 allowed now (§9.4)
    ) : GateState
}
public enum class BlockReason { LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed, BackupInvalid }

/** One row of the one-time sheet (§8.2). Only things the person must do appear; app-made secrets never do. */
public enum class ReentryKind { Password, Token, GitHubSignIn, ByoSetup }
public data class ReentryNotice(val profileId: ProfileId?, val kind: ReentryKind)   // null id = app-wide (GitHub)
public data class MigrationNotices(val reenter: List<ReentryNotice>)

/** Read-only view for the gate screens. */
public interface MigrationStatus {
    public val state: StateFlow<GateState>
}

/** Implemented by :android:migration; used by the :ui:shell gate screens through the app graph. */
public interface MigrationActions {
    /** Single-flight. While Running: keeps waiting on the same run (never a second runner). From Blocked: resets
     *  attempts of the failed step and runs again. */
    public suspend fun retry(): Outcome<Unit>
    public suspend fun restorePreUpdateData(): Outcome<Unit>   // R2, §9.4; refused unless restoreAvailable
    public suspend fun startFresh(): Outcome<Unit>              // keeps a copy, §8.1
    /** Gate-exempt: builds the report in memory and hands it to the share sheet; writes no pref (§8.3). */
    public suspend fun reportProblem(): Outcome<ShareablePayload>
    public suspend fun acknowledgeNotices()
}

// :core:data commonMain, package io.github.eslamasabry.oc.core.data.migration
public interface MigrationGate : MigrationStatus {
    public suspend fun awaitWritable()          // returns when Open; never returns while Blocked
    public suspend fun awaitOpen()              // the same, for reads and engine start (§2.2)
}
```

`:androidApp` binds the one runner-backed object as `MigrationGate`, `MigrationStatus` and `MigrationActions`.
`:ui:shell` sees only `MigrationStatus` and `MigrationActions` (from `:core:model`), and no new dependency edge is
needed. `ShareablePayload` (`:core:model`) is a byte array plus a MIME type and file name.

### 7.4 The steps

| Id | What | Reads | Writes | Marker | Typical time [U, measured in P5/W8] |
|---|---|---|---|---|---|
| **S0** | byte backup (StartupFence thread, never main) | Flutter + FSS XML files (raw bytes) | `files/migration/flutter-v57/` | `backup.state = done` | < 1 s for 100 MB (01 P5 acceptance) |
| **M1** | census and integrity | backup copy, live prefs, directory listings | journal only | `steps.M1` | < 200 ms |
| **M2** | secrets: decrypt FSS v11, re-encrypt into `SecretStore` | FSS files, Keystore | `oc_secrets_v1.xml`, `oc.secretReentry.<id>` | `steps.M2` | < 500 ms for 20 secrets |
| **M3** | prefs adoption: L1 retire, L2 notification merge, L3 read-state consent | live prefs | declared write set only | `steps.M3`, `oc.notify.version`, `oc.readStateConsentMigration.v1` | < 100 ms |
| **M4** | restore-authority check | live prefs vs backup, `builtin_server_recovery` (read only) | none | `steps.M4` | < 50 ms |
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
   `blocked = {"reason":"LowSpace","neededBytes":…}` and return (nothing touched). In the `qa` build type only, a
   file `files/native/qa-free-bytes` (a decimal number) replaces `availableBytes`, so J8 can run without filling the
   disk; the release build has no such code path.
4. **If `files/migration/flutter-v57/` already exists** (a crash after step 6 and before step 7): verify every file
   against its `manifest.json` (size and SHA-256). Verified → adopt it and go to step 7. Missing or failing manifest →
   `blocked = {"reason":"BackupInvalid"}`; the published directory is **never** copied onto, deleted or overwritten
   (it may be the only pre-update copy), and Report a problem says so. (Audit 04+05 #5; P5 `BackupPublisher`.)
5. Delete a leftover `files/migration/flutter-v57.partial/` (ours), create it, copy each file with
   `FileInputStream`/`FileOutputStream`, hashing while copying, `fd.sync()` each. A `.bak` twin is copied too and
   marked `"authoritative": true` in the manifest, because Android loads it instead of the `.xml`.
6. Write `manifest.json` in the partial dir (names, sizes, SHA-256, `authoritative`, copy time, native versionCode),
   fsync it, rename the partial dir to `files/migration/flutter-v57/`, fsync `files/migration/`.
7. Journal `backup.state = "done"`.

The candidate list also holds `flutter_image_picker_shared_preference.xml` (the picker's lost-result cache, M1 step 3
and M3 below). Raw file copies, on the StartupFence thread, before the first `getSharedPreferences` call of any of these
files in the process (01 §9.7). `BackupPublisherTest` injects a crash at each of the 16 filesystem operations (P5
`FaultPoints`); every restart ends in the same published, verified backup and a third run changes nothing. A finished backup is never
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
   `flutter.` prefix. The DataStore and default-prefs files are ignored afterwards [R network-data §8.2]. **The
   image-picker cache is not ignored** (audit 04+05 #11): it can hold the only path of a photo taken just before the
   process died (`ImagePickerCache`: `flutter_image_picker_image_path` string set, `…_type`, `…_error_code`,
   `…_pending_image_uri`; read by `retrieveLostData()` in `lib/state/prompt_photos.dart:304`). M1 records whether it
   holds a result; M3 step L4b imports it.
4. **Profiles.** Parse `oc.profiles` with `ServerProfileJson`: count, invalid ids, reserved-segment collisions, profiles
   carrying `requiresOpenCode2Migration` or `openCode2HistoryPending`.
5. **Directory inventory.** Name, file count and byte size at depth 1 of `files/`, `app_flutter/`, `cache/`,
   `code_cache/`, `no_backup/`, `databases/`, `app_webview/` (and depth 2 of `files/`, counting `files/linux/` and
   `files/projects/` as totals only), plus the app's external directories `getExternalFilesDir(null)` (where
   `files/factsbook` lives, `factsbook_node_host.dart:248`) and `externalCacheDir` (project exports,
   `ProjectExportBridge`). This records where Shorebird and the Flutter engine left files [U: exact Shorebird paths are
   not documented; the first real-install fixture (§10.4) records them], for the v3 cleanup.

6. **Interrupted atomic writes.** Presence and byte size (never contents) of the Flutter and Kotlin temp files left by a
   write that the process did not finish: `files/diagnostics/report_problem.pending`, `files/crash-diagnostics.pending`,
   `files/native-last-crash.properties.tmp`. Recorded in the journal as `pendingWrites` for the audit; handled by the
   owning engine on its first open (§7.8 "Interrupted Flutter writes"), never by the runner.

#### M2 — secrets (`FlutterSecretsMigration`)

The exact algorithm is in §7.5. Outcome per name: `migrated`, `alreadyMigrated`, `nativeNewer`, `flagged(reason)`, or
`countedOnly` (dropped features).

**Every name has a kind** (Appendix B), and the kind decides what a flag does. Several secrets are made by the app,
not typed by the person, so asking the person for them would ask for something they never knew:

| Kind | Names | When it can't be migrated |
|---|---|---|
| typed password | `pw.<id>` of a remote server | `oc.secretReentry.<id>` with `"pw"`; the profile loads with `requiresPasswordReentry`; sheet row "Enter password" |
| typed token | `oc.codexToken.<id>` | `oc.secretReentry.<id>` with `"codexToken"`; `requiresCodexTokenReentry`; sheet row "Enter token" (a Paseo profile may legitimately have none, so only a *found* entry that fails is flagged) |
| sign-in | `oc.githubToken.<id>` | `oc.secretReentry.<id>` with `"githubToken"`; the Git page shows "Sign in to GitHub"; sheet row "Sign in to GitHub again". It never sets `requiresPasswordReentry`: the server itself still connects |
| setup | `oc.byoHostSecrets.<id>` | `oc.secretReentry.<id>` with `"byoHostSecrets"`; the BYO host shows "Set up again"; sheet row "Set up again" naming the host. The server row is not locked |
| app-made | `pw.<id>` of the built-in phone server (the profile `ServerProfileJson` marks as built-in, exactly as Dart does), `oc.agentHostSecret.<owner>`, `oc.quotaToken.<id>`, `oc.teamEngineAuth.<id>` | **never listed and never sets a re-entry flag.** The owner renews it silently, as Dart does: the phone-server start makes a new password and rewrites `/root/.oc-builtin/server.password` when the stored one is missing (`lib/builtin/builtin_server.dart:240-246`); the agent host makes a new 64-hex secret when none is stored (`phone_agents_host.dart` `_password`); collector setup writes a new quota token; AI Team re-pairs when ported. The audit counts these as `renewedByOwner` |
| deferred | FactsBook names | copied (the port reads them later); never listed |
| dropped | `oc.a2aBearer.<agentId>` | **counted, not copied** (`countedOnly`): it stays in FSS and is deleted with the FSS files by v3. Nothing native would ever read or remove it from `SecretStore` |

- `oc.secretReentry.<profileId>` = `{"v":1,"names":["pw"],"reason":"UnwrapFailed","at":<ms>}` (names stored as the
  prefix only, never values). `:engine:profiles` reads it: a profile with a flagged `pw` or `codexToken` and no
  `SecretStore` entry loads with `requiresPasswordReentry` / `requiresCodexTokenReentry`. Each flag is cleared when the
  person saves a new value of that name. **A flagged secret is never treated as an empty password** (01 §9.7).
- `MigrationNotices.reenter` gets one `ReentryNotice` per (profile, kind) for the first four kinds only, so the sheet
  never shows an app-made secret and never locks a whole server for a GitHub or BYO failure.
- Retry: flagged names are retried at the next three launches (`secretRetries.remaining`), but only while
  `SecretStore` has no entry of that name **and the credential ledger (§4.4) has no record for it** (a value the person
  typed, the owner renewed, or a deliberate sign-out or reset always wins; "no value" after a reset is a tombstone, not
  a gap to refill, Astra B3).
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

| L4b | the plugin half of `PromptPhotoStore.recoverLostData()` (`lib/state/prompt_photos.dart:304`); the same step as 10 §16.3 M16, defined once here | none: the condition below turns false once it ran | the single JSON value `oc.pendingPromptPhoto` (fields `path`, `name`, `failed` only) | B-STATE-088, B-MIGR-009 |

L4b (audit 04+05 #11, Astra B2), against the real schema: `oc.pendingPromptPhoto` is **one JSON value**
`{id, profileID, sessionID, directory, workspace, ref, path, name, failed}` (`prompt_photos.dart:44-53`), not a family of
keys. Every picker launch first commits that origin record, so the case that only the plugin can recover is: the record
exists, `ref == null`, `path == null` and `failed != true`. Only then:

1. Read `shared_prefs/flutter_image_picker_shared_preference.xml` with P5's `ImagePickerCache.read` (plain XML, never
   the plugin; it is in the S0 backup).
2. Take the first existing, non-empty file among `flutter_image_picker_image_path` (string set) and
   `flutter_image_picker_pending_image_uri`.
3. Found → rewrite the JSON value with `path` and `name` set and every other field byte-identical (`DartJson`, §2.5),
   through `MigrationWriter`. Not found and the cache holds an error code → set `failed = true`. Not found and no error
   → leave the record unchanged (Flutter's `_discard` happens in the engine, not in the migration).
4. Never copy into the vault here: `:engine:composer`'s own recovery (10 §10.5 step 6) completes a record whose `path`
   is set exactly as Flutter's `readPending` does, storing the blob for the record's own `profileID` and conversation.
   The picker file stays until v3.

M5's declared write set includes `oc.pendingPromptPhoto` for L4b (below), so a successful import never trips
`Blocked(AuthorityChanged)`. Test `PickerOnlyRecoveryJourney` (J2, `qa` kill after the camera result is cached and
before Dart received it): the full migration reaches `GateState.Open`, and the photo is in the composer of the
conversation named by `sessionID`/`directory`/`workspace`, with no other key changed.

On the owner's phone L1–L3 are normally no-ops, because the Flutter app ran them at every launch. The census proves
it.

A refused commit → `Blocked(WriteRefused)` (a refused prefs commit almost always means a full disk), with "Try moving
your data again".
Nothing is lost: `commit()` is atomic (temp file + rename), so the file holds either the old or the new state.

#### M4 — restore-authority check

Kotlin restores the phone server after a reboot or update only if three Flutter-era keys say so [R builtin-linux §5.3,
trap T1], **and** the Kotlin-owned `builtin_server_recovery` file holds a matching `restoreOwner` and a compatible
cold-start recipe (`oc.builtinRuntimeRecipe.<p>`, bound to `packageVersion` and `rootfsGeneration`, Appendix D). If
either half is lost, the phone server silently stops coming back. M4 checks the Flutter half for equality and records
the Kotlin half (which it never writes).

1. For every profile id in `oc.profiles` and every id found in `oc.builtinRecovery.*` keys: the raw strings of
   `oc.builtinRecovery.<p>`, `oc.automation.<p>`, `oc.phoneAgentOwner.<p>`, and of `oc.builtinServerOwner`, must hash
   to the M1 baseline. A difference → `Blocked(AuthorityChanged)` (it can only be a bug; R2 is offered).
2. Evaluate the authority predicate (`version == 2 && nativeAuthority == true` on the recovery record; the automation
   policy parses and allows `restartPhoneServer`; the owner id matches `^[A-Za-z0-9_-]{1,80}$`). Record `armed` /
   `disarmed(reason)` counts. A disarmed state that was already disarmed in the backup is the Flutter state and is only
   recorded.
3. Read `builtin_server_recovery` (read only, through `KotlinPrefsFile`) and record counts only: `restoreOwner`
   present and equal to an armed profile; recipe present; recipe `rootfsGeneration` equal to the installer's
   `rootfsGeneration`; recipe `packageVersion` ≤ the native versionCode (the `PACKAGE_REPLACED` rule accepts and
   rewrites it; `BOOT_COMPLETED` needs an exact match, so after a missed update-time restore the next reboot won't
   restore either). A missing or incompatible Kotlin half is recorded as `disarmed(kotlinHalf)`, never blocked: it is
   Kotlin's own state and the restore receiver owns it (§7.1).
4. The predicate lives in `:android:migration` (it may not depend on `:android:linux`). `RestoreAuthorityParityTest` in
   `:androidApp` (which sees both modules) runs the same vectors through both predicates and requires equal answers.

#### M5 — audit (`MigrationAudit`)

`files/native/migration-audit.json` holds counts and digests, never values or full key names:

- prefs: bytes, key count, counts per decoded kind, `unchangedKeysDigest`, changed shapes with counts;
- profiles: count, invalid ids, reserved collisions, OC1-era flags;
- secrets: found, migrated, already migrated, native newer, flagged by reason and kind, `renewedByOwner` (app-made),
  `countedOnly` (dropped features), orphans, `_BACKUP` entries skipped (**no hashes of secrets**: a hash of a short
  password can be guessed);
- queue entries and entries with a dispatch marker; drafts (and legacy unattributed ones); stash prompts; pins; schedules
  (profiles, schedules, runs); widget targets;
- restore authority armed/disarmed, and the Kotlin-half counts from M4 step 3;
- files: vault file counts, voice-model bytes, `files/linux/ubuntu.ready` present.

M5 also hands one BASIC analytics event to `:androidApp`, which emits it through the analytics core in
`:engine:diagnostics` (16 N16-D9) once the gate is Open (`:android:migration` may not depend on engines):
`migration_result {state: Done|Recovered|StartedFresh, failedStep: StepId?, failureClass: BlockReason?,
servers: CountBucket, secretsFlagged: CountBucket, durationBucket: DurationBucket}`. Enums and buckets only (16 K-SEC-11):
no values, names, ids or paths. A `Blocked` run can't emit (engines don't start); its class is emitted as
`Recovered` with the failed step at the first later Open, or as `StartedFresh`. Until the owner picks a destination
(16 D16-1) the core's sender is `none`, so nothing leaves the phone.

**The "nothing else changed" proof:** `unchangedKeysDigest` = SHA-256 over the sorted `(key, sha256(raw value))`
pairs of every key outside the declared write sets (M2: `oc.secretReentry.*`; M3: the L1–L3 sets and L4b's `oc.pendingPromptPhoto`; the barrier `oc.native.flutterReadable`), computed once from
the live map now and once from `m1-baseline.bin`. They must be equal; otherwise `Blocked(AuthorityChanged)` is raised
with step `M5`. This also proves the offline queue and its
dispatch markers are byte-identical (state-data risk 6).

One diagnostics line: "Update moved your data: N servers, M saved sign-ins (K need the password again)." Counts only
(B-MIGR-011).

#### M6 — complete

M6 writes the FSS snapshot (`files/native/fss-snapshot.json`, §7.2) from the current FSS files, the baseline for the
first `R1.return` (§9.3); native never writes FSS, so it equals the S0 copy. Then journal
`migrations."native.migration.v1".complete = true`, then `oc.native.flutterReadable = true` (commit), then
`gate.firstOpenAt` (journal), then the gate opens with `firstNativeLaunch = true` and the notices.

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
        val aes = try { unwrapper.unwrap(decodeB64(wrapped)) }   // KeyAliasMissing: internal class : Exception()
            catch (e: KeyAliasMissing) { return all(SecretFailure.KeystoreAliasMissing) }
            catch (e: Exception) { return all(SecretFailure.UnwrapFailed) }
        return names.map { name -> decryptOne(name, data[PREFIX + name], aes) }
    }

    /**
     * The plugin writes android.util.Base64.DEFAULT (line breaks every 76 chars) and reads with Android's decoder,
     * which SKIPS every byte outside the alphabet and accepts missing padding (P5 §1.6, read in the AOSP source).
     * A strict decoder would call values Flutter reads fine "BadBase64". So this is P5's pure-Kotlin exact port of
     * android.util.Base64.decode(DEFAULT) (`decodeAndroidBase64`), which also runs in plain JVM tests, where
     * android.jar's Base64 throws "Method decode not mocked". It fails only where Android's decoder fails.
     */
    private fun decodeB64(text: String): ByteArray = decodeAndroidBase64(text)

    private fun decryptOne(name: String, raw: Any?, aes: SecretKey): FlutterSecret {
        val bytes = try { decodeB64(raw as String) }
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
   A name of the dropped kind (`oc.a2aBearer.*`) stops here as `countedOnly`.
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
- `no_backup/androidx.work.workdb*`. WorkManager upgrades its own database (2.10.5 → 2.12.0, Room schema 23 → 24 via
  `AutoMigration_23_24`); the worker class FQCNs stay frozen (01 §9.9), so queued work still resolves. **The reverse is
  destructive:** both versions build `WorkDatabase` with `fallbackToDestructiveMigration`, so opening a v24 database
  with 2.10.5 (R1 installed over native) drops every table and loses all enqueued, unique and periodic work [V: read
  in the 2.10.5 and 2.12.0 AARs today]. R1 therefore ships the native app's WorkManager version (§9.3).
- OS-held state: widget instances, pinned and dynamic shortcuts, Quick Settings tiles, notification channels and the
  person's channel settings, AlarmManager alarms (kept across a package replace [U]; H2 re-arms them anyway), runtime
  permissions, special app-ops (All files access, exact alarms, `POST_NOTIFICATIONS`, Termux `RUN_COMMAND`) and the
  battery exemption. They carry over because the package, signer and component names are unchanged (01 §9.9 component
  freeze), **and only if the native manifest still declares each permission** (section 11's manifest table is the
  source). §10.4 records the grants before the install and J1 asserts they are identical after it.

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

**Interrupted Flutter writes.** Dart wrote the problem report and the crash records as "write `<name>.pending`, flush,
rename over `<name>`" (DESKTOP-WEB-CROSS-54, 58). If the Flutter process died between the write and the rename, the
`.pending` file may hold newer data, or a partial write; nothing marks which. The native readers decide on purpose, the
same way the Dart readers did:

| File | Native reader | Rule |
|---|---|---|
| `files/diagnostics/report_problem.pending` | `:engine:diagnostics` `ReportStore.open()` | **discard**: the committed `report_problem.json` is the truth; the orphan is deleted and never resurrected (Dart `restore` discards any `.pending`, B-STATE-079). One `timing`-free diagnostics line "An unfinished report write from before the update was dropped" is added only when the file was non-empty |
| `files/crash-diagnostics.pending` | `:engine:diagnostics` `CrashRecords.open()` | **discard**: only `crash-diagnostics.json` counts (an unrenamed write was never committed); records still pass the consent-time filter |
| `files/native-last-crash.properties.tmp` | `NativeCrashStore` (kept Kotlin) | **discard**, as the Kotlin store already does on read |

Neither file is ever renamed into place by native code: a partial JSON could otherwise replace a good file. Fixtures:
the real-install dump (§10.4) gets a second variant with both `.pending` files planted (one valid, one truncated);
`AdoptedKeysFixtureTest` in `:engine:diagnostics` asserts that the committed files are read, both `.pending` files are
deleted, and the census of M1 item 6 recorded them.

### 7.9 First-launch hooks (run by `:androidApp` after the gate opens, once, marker `hooks.firstLaunch.v1`)

| Id | Action | Owner module | Catalog row |
|---|---|---|---|
| H1 | Mirror `oc.appLocale` into the per-app locale using the mechanism slice-0 proof P6 picks (Android 13+ `LocaleManager`). `oc.appLocale` stays the stored truth that `NativeStrings` reads; the two-way sync rule belongs to `:engine:settings`. The gate screens before H1 already use this locale (§8.2), so nothing flips when H1 runs | `:engine:settings` + `:android:platform` | B-L10N rows |
| H2 | Re-arm OS surfaces: `ScheduleEngine.rearmAll(FirstNativeLaunch)`; redraw every widget id of every provider FQCN; republish pinned shortcuts from `oc.pinnedShortcuts`; `TileService.requestListeningState` for both tiles; resume background live mode if `oc.keepLiveInBackground` (through the same restriction-aware path the restore receivers use) | `:android:schedule`, `:android:widgets`, `:android:background` | B-MIGR-013, B-ANDR-090 |
| H3 | Check every frozen notification channel id exists; create missing ones only (creating an existing id never changes the person's settings) | `:android:background` | B-MIGR-013 |

The phone-server restore after the update is the moved `BuiltinRestoreReceiver` (`MY_PACKAGE_REPLACED`). It does
**not** wait for the gate: it decides from `MigrationReader` and starts the service inside the broadcast's
foreground-service window (§7.1). M4 then confirms the keys it read were intact. Work that `MigrationFollowUpWorker`
was given (`followUp` in the journal) is also finished when the gate opens.

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
| an R2 or Start fresh move (`pendingSwap` set) | `RestoreTransaction.recoverBeforeOpen()` on the StartupFence thread (never main), before S0 and before any `getSharedPreferences`, finishes the move from the recorded phase (§9.4) |

The crash-loop guard (01 §9.7) is a second net: three crashes within 10 s of start open safe mode, which offers R2 only
while `gate.firstOpenAt` is null (§9.4).

### 7.11 Versioning of native data **[new]**

- **Migration ids are append-only:** `native.migration.v1` (first launch), `native.migration.v2` (Room import),
  `native.migration.v3` (Flutter leftovers cleanup). Each is a fixed list of step ids. A released step is never edited;
  a behaviour change is a new step under a new id.
- **Step interface** (`:android:migration`):
  ```kotlin
  internal interface MigrationStep {
      val id: String                         // "M2", "V2.queue", "V3.fssFiles"
      val breaksFlutterReadability: Boolean  // true: the runner LOWERS the barrier before run() writes anything
      suspend fun run(ctx: StepContext): StepResult
      suspend fun verify(ctx: StepContext): Boolean   // re-checked on re-run before skipping
  }
  ```
- **One compatibility barrier** (audit 04+05 #4; P5 `StepRunner`): the value is `oc.native.flutterReadable` in the
  prefs file (what R1's guards read, §9.3) **and** `rollback.flutterReadable` in the journal. The rules:
  1. `initBarrier()` writes `true` to both right after M1, before any write.
  2. A step with `breaksFlutterReadability` first writes `false` to the journal, then commits `false` to the prefs
     file, **reads it back from a fresh `SharedPreferences` read**, and only then makes its first write. A refused or
     unverified barrier write → `Blocked(WriteRefused)` with zero step writes.
  3. On every start, if the two disagree, `false` wins and is written to both (a crash between the two writes is
     repaired before anything else runs). The barrier never goes from `false` back to `true`.
  4. The R1 guards treat a **missing** pref as blocked when `files/native/migration.json` exists (§9.3).
  `StepRunnerTest` kills the process at every point of a breaking step and asserts that rollback is never allowed
  while the step may be half done.
- **Run order per step** (P5): journal `attempts+1, running` → `run()` (idempotent) → journal `committed` →
  `verify()` (read-only) → journal `verified`. A re-run re-verifies a `verified` step and skips it, and only verifies a
  `committed` one.
- **Value formats:** Flutter-era records keep their own version fields unchanged while `flutterReadable` is true (§2.3).
- **SecretStore:** version byte `0x01` and alias suffix `.v1`. Rotation = a new alias and a re-encrypt step.
- **Room:** an integer schema version with exported schemas and tested migrations.
- **Journal:** `schema: 1`; new fields are additive.

---

## 8. Failure: ask once, never lose silently

### 8.1 Failure classes

| Class | Detected in | What is kept | What the person sees | Way out |
|---|---|---|---|---|
| LowSpace | S0 | everything (nothing was touched) | "Your phone is almost full" screen | Try moving your data again; Open storage settings; Report a problem |
| PrefsUnreadable | M1 | the file and its backup; no write happens | failure screen | Try moving your data again; Report a problem; Start fresh (keeps a copy) |
| Secret unreadable (per name) | M2 | the FSS entry, untouched | **one** sheet listing what needs the person (by kind, §7.4 M2), then each item's own state ("Enter password", "Enter token", "Sign in to GitHub", "Set up again"); app-made secrets are renewed silently and never listed | automatic retry on 3 launches; the person types it |
| SecretStore unavailable (Keystore key can't be created) | M2 | FSS untouched | the same sheet, plus one plain sentence on the Servers page (`secret_store_unavailable`, W7) | retried every launch |
| WriteRefused | M3, M6 | the old state (atomic commit) | "Couldn't save on this phone" screen | Try moving your data again; Open storage settings; Report a problem |
| AuthorityChanged | M4, M5 | backup | failure screen | Restore data from before the update; Report a problem |
| StepCrashed (3 attempts) | any | everything | failure screen | Try moving your data again (resets attempts); Restore data from before the update; Start fresh |

"Restore data from before the update" (R2) appears only while `restoreAvailable` is true, which is only before the
gate has ever opened (§9.4). A failure in a later migration (v2, v3) offers Try moving your data again, Report a
problem and Start fresh.

"Start fresh (keeps a copy)" moves `FlutterSharedPreferences.xml` and `oc_secrets_v1.xml` into
`files/migration/start-fresh-<epochMs>/`, sets `source.kind = "startFresh"` and restarts the process. It is two-phase,
like R2 (§9.4): the move happens in the next process's `onCreate`, before any `getSharedPreferences` call, so a write
from the old process's in-memory map can't undo it. Nothing is deleted and the Linux files stay. A confirmation names
the effect: "You'll start with no saved servers and default settings. A copy of your old data stays on this phone."

A store that reads its own key as corrupt after adoption (for example an unreadable queue) shows that store's existing
recovery UI, as the Flutter app did. That is not a migration failure.

### 8.2 Screens and copy (`:ui:words` `composeResources/values*/strings_migration.xml`; Arabic and the five other languages from the l10n lane)

The gate screens are kit `KitScreen` states owned by `:ui:shell` (01 §5.2). They have no bars and no new levels; every
option is a visible row, and every row names its target.

| Key | English |
|---|---|
| `migration_updating` | Updating your app… |
| `migration_still_updating` | Still updating. The first start after an update can take a little longer. |
| `migration_try_again` | Try moving your data again |
| `migration_reenter_title` | A few sign-ins need you again |
| `migration_reenter_body` | The update couldn't read these saved sign-ins. Everything else came across. |
| `migration_reenter_row_password` | %1$s · Enter password |
| `migration_reenter_row_token` | %1$s · Enter token |
| `migration_reenter_row_github` | %1$s · Sign in to GitHub again |
| `migration_reenter_row_byo` | %1$s · Set up again |
| `migration_reenter_later` | Sign in to these later |
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

- `%1$s` is always the server's plain name (`plainServerName`), or the BYO host's name, so every row names its
  target. The copy keys change in every language at once (the l10n lane translates the new English; the Arabic file
  gets the same keys).
- **Try moving your data again** is single-flight (`MigrationActions.retry`, §7.3). In the 8 s "Still updating" state
  (01 §9.7) pressing it keeps waiting on the same run and never starts a second runner; only from a `Blocked` state
  does it reset the failed step's attempts and run again.
- **Ask once:** the re-enter sheet shows on the first UI launch after the gate opens with non-empty
  `notices.reenter`, then `acknowledgeNotices()` records it. After that, each item shows its own normal state
  ("Enter password" on the server row, "Sign in to GitHub" on the Git page, …). The sheet is not shown again.
- **Language and direction:** the gate screens show in the person's in-app language. `MainActivity` reads the tiny
  `files/native/app-locale` mirror (never the prefs file, §7.1) before `setContent` and wraps the gate UI in that
  locale and its layout direction (a configuration override for resources plus `LocalLayoutDirection`; the mechanism
  is 03 S21). On the very first native launch the mirror appears when the StartupFence has made the backup and read
  `oc.appLocale` (normally well under a second); until then the gate shows the wordless "Opening" mark, so no text ever
  shows in the wrong language. Absent `oc.appLocale` = follow the system list, never the first supported language. So
  someone who chose Arabic in the app sees the Updating, failure and almost-full screens in Arabic, right to left, even
  when their phone is in English, and nothing flips when H1 runs.
- **Details fold:** the step id and failure class only (for example `M2 · UnwrapFailed ×1`), next to Report a problem
  (01 §9.6). No exception text.
- Motion: the kit's standard 150–250 ms fade between "Updating" and the result; nothing under reduced motion.

### 8.3 Report a problem

The report adds `migration-audit.json` and the journal (both counts only) and the M1 directory inventory (names and
sizes). It never includes the backup, the FSS snapshot, secrets or prefs values.

On the gate screens the app's normal report flow can't be used: it saves the person's words in
`oc.draft.report-problem.app` and writes `files/diagnostics/report_problem.json`, and `KeyValueStore.commit` never
returns while the gate is `Blocked`. So the gate screens call the gate-exempt `MigrationActions.reportProblem()`:

- It builds the bundle in memory from the journal, the audit, the M1 inventory and the category-only crash record. It
  writes nothing to prefs and touches no store.
- It hands the bundle to the share sheet: as a file under `cache/migration-report/` through the app's `FileProvider`,
  or, if that write fails (`LowSpace`), as plain text in `EXTRA_TEXT`.
- There is no free-text field on this path, so nothing the person types is lost when the gate is closed.

`MigrationReportTest` (Robolectric) runs it under `Blocked(PrefsUnreadable)` and `Blocked(LowSpace)` and asserts that it
returns a payload and that the prefs file and `files/diagnostics/` are byte-identical before and after.

---

## 9. Rollback

### 9.1 Why it needs a plan

Android refuses to install a lower versionCode over a release build without an uninstall, and an uninstall deletes the
data [R memory apk-build-numbers]. The native line starts at 10000. Every Flutter build is below that, so "reinstall the
old app" is impossible unless the old app is rebuilt with a higher number.

**versionCode rule [new]:** one counter and one ledger for every APK of this package, so no build can strand another.

- The ledger is `docs/release/version-codes.md` in the native repo. `tools/release/next_version_code.sh` prints the
  largest of: every number in the ledger, every APK on the APK share server, the owner phone's installed versionCode
  (read-only `adb shell dumpsys package`, when the phone is connected) and the emulator's. The rule is the one memory
  apk-build-numbers learned the hard way: every hosted or owner-facing APK is above every APK already hosted or
  installed on the owner's phone or the emulator.

| Build | Number |
|---|---|
| owner candidates and releases | the next multiple of 10 above that largest number (base 10000, 01 D3) |
| agent and emulator proof builds | a block of 10 the coordinator hands out in the order the agents will install, written in the ledger before the build |
| R1 rollback build | largest + 1, computed on the day it is built (§9.3) |
| bridge (Flutter) builds | from the same allocator (15 §2.6); there is no second counter |

There is no upper range and no fixed starting number (Astra M5): the base 10000 is a floor only. A device that holds a
higher signed number (11 CR11-18: 900055 and 900058 on emulators) raises the next number above it; the allocator never
plans an uninstall. The upgrade journeys include an in-place `adb install -r` over the **highest** build present on
`emulator-5584` and over `flutter-final`, without uninstalling (J1 baselines).

- The next owner candidate after an R1 takes the next multiple of 10 above it.
- The ABI splits of one build share one versionCode (01 §9.9). Every build script checks, before hosting, that
  `aapt2 dump badging` reports exactly the allocated versionCode for each APK it made (arm64-v8a, x86_64, universal).

### 9.2 R0 — fix forward (the default, always available)

A native fix release with the next versionCode: the owner gets it over adb when around (memory auto-install), others
through the in-app updater (01 §9.9).

### 9.3 R1 — Flutter rollback build (only while `flutterReadable` is true)

- **When it is valid:** the switch line, before any step with `breaksFlutterReadability` (the Room v2 import is one).
  All state is then in the adopted prefs file in Flutter formats, so the old app reads everything the native app wrote.
  If the P5 trigger moves Room into the switch release, v2 runs on day one and **there is no R1 for that line**
  (M-D1; A1 and A6 then drop their R1 parts).
- **Owner lineage only.** R1 is signed with the owner's local key (01 D2), so it can replace only owner-lineage
  installs. Public CI-lineage users have R0 and R2. A CI-lineage R1 would need CI signing, which needs the owner's
  explicit approval, and is not planned.
- **Built on demand, never kept ready.** A copy built once at "installedNative + 1" stops installing as soon as the
  next owner candidate lands (several a day), so what is kept ready is a tested script, not an APK.
- **Prepared before the switch (W9):**
  1. **Pin down the Dart code on the owner's phone at the freeze.** The versionCode alone doesn't identify it: the
     phone runs local builds (4363 on 11-01) and Shorebird patches on top of a release. Record the release version,
     the Shorebird patch number (from Report a problem's app line, or `shorebird_code_push`'s current patch) and the
     integration commit that patch was built from, plus the installed versionCode and signer, in
     `docs/migration/flutter-final.md` of the native repo. **If the patch number or its commit is unknown, stop and
     ask the coordinator**; never guess.
  2. Branch `rollback/flutter-final` from exactly that commit. The fixture APK (§10.4) is built from the same commit.
  3. Changes on the branch (about 120 lines of Dart, 150 of Kotlin, one dependency line):
     - `lib/native_return_guard.dart` with one `nativeReturnGuard()`, called first in **every** Dart entrypoint:
       `main`, `widgetOverlayMain` and `manageSpaceMain` (`lib/main.dart:125,130`), `scheduledPromptMain`
       (`lib/scheduling/entrypoint.dart`) and `widgetActionMain` (`lib/background/widget_action_entrypoint.dart`):
       ```dart
       /// true = carry on. Touches nothing when the native data is not readable.
       Future<bool> nativeReturnGuard() async {
         final prefs = await SharedPreferences.getInstance();
         final readable = prefs.getBool('oc.native.flutterReadable');
         if (readable == false) return false;
         // Missing flag after native migration began = uncertain = blocked (§7.11 rule 4).
         final journal = File('${(await getApplicationSupportDirectory()).path}/native/migration.json');
         if (readable == null && journal.existsSync()) return false;
         // Native changed or signed out of these secrets: delete the stale FSS copies BEFORE anything reads them.
         final stale = prefs.getStringList('oc.native.staleLegacySecrets') ?? const [];
         final tid = prefs.getString('oc.native.returnedAt') ?? DateTime.now().toUtc().toIso8601String();
         if (stale.isNotEmpty) await prefs.setStringList('oc.native.guardDeleted.$tid', stale);  // record FIRST (§4.4)
         for (final name in stale) { await secureStorage.delete(key: name); }
         if (stale.isNotEmpty) await prefs.remove('oc.native.staleLegacySecrets');
         await prefs.setString('oc.native.returnedAt', DateTime.now().toUtc().toIso8601String());
         return true;
       }
       ```
       Before the deletion loop the guard commits `oc.native.guardDeleted.<transitionId>` with the same names (§4.4
       rule 3), so a death mid-loop is recoverable. `oc.native.staleLegacySecrets` (audit 04+05 #7; P5
       `CredentialReconciler.staleLegacyEntries`) is a list of secret **names**, never values. `SecretStore` adds a name
       to it, together with its credential-ledger record and **before** it changes the value (§4.4 rule 1),
       whenever native writes a different value for a name that still exists in FSS, or deletes it (sign-out, Reset
       saved sign-ins, server removed). So in R1 a native sign-out stays signed out and a native password change shows
       "Enter password" instead of silently using the old one. `NativeReturnGuard.kt` applies the same deletion before
       any Kotlin receiver reads FSS.
       On `false`, `main`, the overlay and Manage space show `CannotGoBackApp` (one sentence: "This older version
       can't open data from the newer one. Install the newer version again."); the two background entrypoints return
       at once.
     - `NativeReturnGuard.kt`: reads `flutter.oc.native.flutterReadable` from `FlutterSharedPreferences`. When it is
       false, `ScheduledPromptReceiver`, `BuiltinRestoreReceiver`, the widget providers, both tiles and every worker
       return without doing anything. When it is not false, every Kotlin writer of the Flutter file also writes
       `oc.native.returnedAt` (same value format), so a person who only uses widgets or schedules in R1 is still seen
       as having gone back.
     - `androidx.work:work-runtime` raised to the native app's pin (2.12.0 today; the build script reads it from the
       native repo's `gradle/libs.versions.toml`), so R1 opens WorkManager's v24 database instead of destroying it
       (§7.7).
  4. `tools/rollback/build_r1.sh <versionCode>` (on the VPS; `<versionCode>` from `next_version_code.sh`):
     - Reads `SHOREBIRD_FLUTTER_VERSION` from **that commit's** `scripts/release.sh` and uses the matching Shorebird
       Flutter cache. Today that is 3.47.1 at `91f8bd75…`; if the frozen commit takes the 3.47.8 TLS fix (memory
       dev-freeze-native-rewrite), it is 3.47.8. It refuses if the cache is missing or the version check fails.
     - Builds **one universal APK**: `<flutter> build apk --release --build-number=<versionCode>
       --build-name=3.0.0-back`. Never `--split-per-abi`: the pinned Flutter Gradle plugin sets each split's
       versionCode to ABI × 1000 + build number (arm64 = 2, x86_64 = 4; `FlutterPlugin.kt:673-679`,
       `FlutterPluginConstants.ABI_VERSION`), so 10001 would come out as 12001 on arm64 and 14001 on the x86_64
       emulator. Every later native build would then be refused as a downgrade until it passed 12001, and the only way
       past is an uninstall, which wipes the data.
     - Makes the arm64-v8a and x86_64 APKs from the universal one with `tool/release/split_apk_by_abi.py`, as the
       release flow does (it re-signs with the same key).
     - Signs with the owner's local key, then checks every APK with `aapt2 dump badging` (versionCode equals
       `<versionCode>`) and `apksigner verify --print-certs` (SHA-256 `1DE5BF08…D60C`). It stops on any mismatch.
     - Records the Flutter version, cache hash, commit, versionCode and APK SHA-256s in
       `docs/migration/flutter-final.md`. Not a Shorebird release or patch.
  5. Prove it in the round-trip journeys (§10.5 J4, J4b) on the switch candidate, with the R1 the script builds for
     the emulator's number. The R1-side half of `FlutterFormatCompatTest` (§10.6) runs with the same toolchain.
- **What the person sees in R1:** every setting, server, draft, queued prompt, stash and schedule, and WorkManager
  work keeps running (same WorkManager version). Native-only keys are ignored. Secrets come from the FSS files minus
  the stale names the guard deleted: a server added, a password changed or a sign-in removed after the update all show
  "Enter password"; an old credential never comes back.
- **Returning to native** (the next native build): the runner sees `oc.native.returnedAt` later than
  `rollback.lastReturnHandledAt` and runs step `R1.return` before the gate opens, so no engine is running:
  1. Load the last FSS snapshot (`files/native/fss-snapshot.json`: name → SHA-256 of the stored ciphertext). At the
     first return it is the one M6 wrote; after that, the one the previous return wrote. Never the S0 copy, so a
     second round trip doesn't overwrite passwords the person changed natively after the first.
  2. Every FSS entry that is new, or whose ciphertext digest differs from the snapshot, was written during this
     rollback: decrypt it (§7.5) and overwrite `SecretStore` (the rollback is newer). Entries equal to the snapshot
     keep the native value.
  3. Every name in the snapshot that is no longer in FSS **and is not in this transition's guard deletions** was
     deleted in R1 by the person (server removed, Reset saved sign-ins): delete it from `SecretStore`. A name the
     guard itself deleted (stale) keeps its native value. Each transition keeps its own baseline (the snapshot written
     at the end of the previous return, plus the stale list the guard consumed), so two rollback cycles never mix.
  4. Every profile id that native stores know (`ProfileScope` registries: `SecretStore` names, Room rows if any, blob
     owners, Kotlin-owned per-profile entries) but `oc.profiles` no longer lists was deleted in R1: run `ownedData`
     cleanup and the `forgetProfile(id)` hooks for it. Its prefs keys are already gone (same file, swept by R1).
  5. Clear the matching `oc.secretReentry` flags; re-run H2 (the Flutter build may have re-armed schedules and widgets).
  6. Write a new FSS snapshot, then record `lastReturnHandledAt` and `returnsHandled + 1`. Every sub-step is safe to
     repeat, and the snapshot is written last, so a kill anywhere resumes.
- **Retired** when `flutterReadable` becomes false, or by the owner (M-D1).

### 9.4 R2 — restore data from before the update (in-app, native)

Offered on the failure screen and in safe mode only while `files/migration/flutter-v57/` exists **and** verifies,
`gate.firstOpenAt` is null (before any engine has ever started) **and** the storage generation is `prefs-v1`
(`RestoreTransaction.eligibility()`, P5: refused with `GenerationNotRestorable` once the transaction that makes Room
authoritative has switched the generation to `room-v2`; audit 04+05 #6). After that, a crash loop days later must not
bring back the pre-update offline queue: prompts the native app already sent and removed would come back without
their dispatch markers and be sent again, and servers and passwords added since would vanish. Once the gate has
opened, safe mode offers only Start fresh (keeps a copy) and Report a problem; the way back is R0, or R1 on the owner
lineage.

It is two-phase, because the process holds a loaded `SharedPreferencesImpl` for the file: any commit from that
in-memory map (a Kotlin surface, `MigrationWriter`, a worker) would write the old state back over a restored or moved
file.

1. Runs only while the gate is `Blocked` or in safe mode, so no engine is writing, and only while `restoreAvailable`.
2. **Phase 1, this process:** journal `pendingSwap = {kind: "restorePreUpdate", at, phase: "requested"}`, stop the
   app's services, and restart with the app's restart helper (`Intent.makeRestartActivityTask` for `MainActivity`,
   then end the process; proven by J2). Nothing is moved in this process.
3. **Phase 2, next process:**
   `RestoreTransaction.recoverBeforeOpen()` (StartupFence step 5a, off the main thread) runs before S0 and before any
   `getSharedPreferences` call, through journaled phases (audit 04+05 #5; P5):
   - `staged`: copy each backed-up file into `shared_prefs/.restore-<at>/` (`.tmp`, fsync, rename), the
     `authoritative` `.bak` copy written as the `.xml`, and verify every hash against `manifest.json`;
   - `movingAside`: move the live `.xml` **and its `.bak` twin** of every restored name, and `oc_secrets_v1.xml`, into
     `files/migration/replaced-<at>/` (a `.bak` left behind would be loaded instead of the restored file);
   - `swapping`: rename the staged files into `shared_prefs/`; fsync the directory;
   - `resetting`: one atomic journal write: `backup = done`, steps M1–M6 `pending`, attempts 0, `outboundHold = true`,
     `pendingSwap = null`.
   Each phase checks what is already there; a kill at any of the filesystem operations resumes from the recorded
   phase and ends in the same state (`RestoreTransactionTest` crashes at every fault point).
4. The migration then runs again from the pre-update state. Kotlin-owned files are not touched.
5. **Nothing is ever sent twice after a restore** (hard rule; audit 04+05 #2). The gate rule above already means no
   native engine has sent anything, but the restore does not rely on it alone:
   - `files/native/outbound-tombstones.json` lives **outside** every snapshot and is never moved by R2 or Start fresh.
     The offline queue records a dispatch id there, and `ScheduledPromptClaims` records each occurrence key
     (`id:revision:dueAtMs`), **before** either reports success.
   - After R2, `outboundHold = true`: the queue does not flush and no schedule is re-armed. On first open the restored
     queue and schedules are partitioned (`OutboundTombstones.partition`): tombstoned entries are removed without a
     send; the rest wait on the "Prompts waiting to be checked" review (the same one as the marked prompt, 04 §13.4),
     and executable schedules stay paused until the person turns them back on.
   - Proof: J2-R2a/b assert that after a successful send and a fired schedule R2 is **refused** and nothing repeats;
     the partition itself is a recovery-layer test (`OutboundTombstonesTest`, eligibility bypassed and labelled), and
     Start fresh, which is offered after first use, is the user journey that exercises `outboundHold` (J2 kill points
     `fresh:*`).

Start fresh uses the same two phases and the same journaled move (`movingAside` → `resetting`, no staging) with
`kind = "startFresh"`, then `source.kind = "startFresh"`. It keeps the tombstones and sets `outboundHold` the same way.

### 9.5 Retention and the v3 cleanup (`native.migration.v3`)

Nothing Flutter-era is deleted in the switch release, **except the L1 retirements** (every `oc.budgets.*`,
`oc.glassLiquidActive`, `oc.glassLiquidStrikes`, `oc.glassLiquidOffUntil`, `oc.effectsLiveBackground`). The Flutter
app already removes those on every profile load (`lib/state/profiles.dart:224-255`), so R1 would remove them too. At
switch+2 at the earliest, and only with the owner's OK (M-D3), v3 deletes:

| Item | Condition |
|---|---|
| `files/migration/flutter-v57/`, `replaced-*`, `start-fresh-*`, `files/native/fss-snapshot.json` | R1 retired; no open failure |
| `FlutterSecureStorage*.xml`, `FlutterSecureKeyStorage.xml`, the FSS Keystore alias | every FSS name is in `SecretStore`, was renewed by its owner, was counted only (dropped A2A), or its flag was resolved by the person |
| Frozen v2 source keys, `oc.native.movedToDb` | v2 complete one release earlier |
| Keys coded **I**, **R**, **X** in Appendix A, and `oc.native.returnedAt` | — |
| `app_flutter/`, Shorebird and Flutter-engine leftovers | the paths recorded by M1 on the real-install fixture |

v3 sets `flutterReadable = false` (pref first, then the journal, §6.4).

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
| B-STATE-065 profile metadata kept when its password is unreadable; display choices persist | `ProfileStoreAdoptionTest` (`:engine:profiles`) + `AdoptedKeysFixtureTest` |
| B-STATE-061, 063 deletion forgets the shared phone owner, sweeps recovery budget, clears orphan widget snapshot; a refused write keeps the server | `ProfileDeletionTest` (`:engine:profiles`), with `AgentHomeCleanerTest` (17 §1.5) |
| B-STATE-064 sign-in reset removes only app-owned sign-ins, idempotent, never parses unreadable metadata | `SignInResetTest` (`:engine:profiles`; participants from `:engine:agents` and `:engine:integrations`, 17 §1.12) |
| B-STATE-130, 131, 133 setup audit and registry stores | `SetupAuditStoreTest`, `SetupRegistryTest` (`:engine:runtime`, 17 §4.3) |
| B-STATE-069 reader preferences per profile, swept | `ReaderPreferencesTest` (`:engine:media`) |
| B-STATE-031 download consent persists once under concurrent claims | `DownloadConsentTest` (`:engine:settings`, 07 §5 I) |
| B-STATE-004, 005 serialized, acknowledged preference writes | `PreferencesTest`, `DisplayPreferenceRollbackTest` (`:engine:settings`) |
| Interrupted Flutter writes (`.pending`) | `AdoptedKeysFixtureTest` (`:engine:diagnostics`, §7.8 fixture variant) |

The other "migration"-level rows (B-STATE-001/012/016/023/024/033/056/057/059/065/069/079/087/134/144/146/151,
B-CHAT-015/051/088, B-SEC-036/075/085/093/116/178/182, B-ANDR-090, B-MEDIA-022, B-KIT-006, B-LINUX-*) belong to the
engines that own those keys. Each of them also gets an `AdoptedKeysFixtureTest` assertion.

Two rows of the catalog's Appendix D list are deferred with their features (01 D6), so they get an explicit deferral
note instead of a test now:

| Row | Owner | Now | When the feature is ported |
|---|---|---|---|
| B-STATE-020 FactsBook secrets swept per profile | FactsBook | M2 copies the FactsBook names (Appendix B); `ProfileSweepShapesTest` covers their `oc.<what>.<id>` shapes; deferral recorded in the catalog coverage ratchet | `AdoptedKeysFixtureTest` in the FactsBook module, plus a deletion test that removes only the deleted server's FactsBook data |
| B-AGENT-303 old project snapshots without planner data still load | `:engine:team` | AI Team files and keys untouched (D in Appendix A, Appendix C); J1 checks they are byte-identical | `AdoptedKeysFixtureTest` in `:engine:team` reading a fixture snapshot without planner data |

### 10.2 JVM tests (VPS, T0/T1)

| Test | Module | Proves |
|---|---|---|
| `FlutterPrefsCodecTest` | `:core:data` (Android host test) | every row of §2.2: JSON list, legacy Java list (fixture made with `ObjectOutputStream` on the JVM), double, BigInteger, Set, int, float; refuses clash prefixes on write; `Undecodable` never carries the value |
| `FlutterPrefsStoreTest` (Robolectric, real `SharedPreferencesImpl`) | `:core:data` | commit semantics; refused commit restores the touched keys; CAS under contention from blocking and suspend callers at once (no `IllegalMonitorStateException`, no lost update); writes and `SecretStore` reads wait for the gate; `KeyValueStore.get` before Open throws `GateNotOpen` in the test build; `MigrationReader` reads before Open; `changes` flow |
| `GateOrderTest` | `:androidApp` test | `:engine:entry` constructs no engine and no store read happens (instrumented fake stores count reads) until the gate is Open, with the runner slowed by a test hook |
| `DartJsonTest`, `Sha256Test` | `:core:common` commonTest | all Dart golden vectors (§2.5); NIST SHA-256 vectors on every target; adding a field to `ServerProfileJson` leaves both `ProfileFingerprintV1` fingerprints unchanged |
| `ServerProfileJsonTest` | `:core:model` | lenient parse, L4 flags, `plainServerName`, unknown fields ignored, fingerprint equals the Dart vector |
| `ProfileSweepShapesTest`, `ReservedSegmentsTest` | `:core:data` | §3.4 rule over every ledger shape; no app-wide key ever matches a generated id |
| `PrefsLedgerTest` | `:quality` | every literal classified, none stale |
| `KeystoreSecretStoreTest` (software AEAD seam) | `:core:data` | format byte, AAD swap rejected, `names(prefix)`, read-back mismatch, redactor registration happens before persistence |
| `FlutterSecretsReaderTest` (plain JVM; `SoftwareRsaUnwrapper`; `java.util.Base64`, so no Android stubs are hit) | `:android:migration` | vectors from a JVM port of the plugin's write path, including line-wrapped `Base64.DEFAULT` values; every `SecretFailure`; `_BACKUP` skipped; markers mismatch flags all; `startsWith` prefix match |
| `SecretKindsTest` | `:android:migration` | one vector per kind (§7.4 M2): a typed password and a token are flagged and listed; GitHub and BYO are listed with their own row and never lock the server; the built-in phone server's `pw`, `agentHostSecret`, `quotaToken` and `teamEngineAuth` are never flagged or listed; `a2aBearer` is counted, not copied |
| `MigrationJournalTest` | `:android:migration` | atomic write, attempts before run, three attempts → blocked, unknown fields kept |
| `MigrationRunnerTest` (Robolectric; fixture XMLs copied into `shared_prefs/`) | `:android:migration` | full run on the real-install fixture; a re-run is a no-op (identical bytes); `interruptAtEveryStep` via `StepInterrupter`; write-set invariant; fresh install; no FSS files; `.bak` present; `retry()` while Running starts no second runner; R2 refused once `gate.firstOpenAt` is set and offered before; R2 and Start fresh move nothing in phase 1 and finish from every recorded phase; `R1.return` over two round trips (snapshot, deletions in R1, profiles deleted in R1); a `breaksFlutterReadability` step commits the pref before its first incompatible write |
| `MigrationReportTest` (Robolectric) | `:android:migration` | §8.3 under `Blocked(PrefsUnreadable)` and `Blocked(LowSpace)` |
| `GateLocaleTest` (Roborazzi, VPS) | `:androidApp` test | the failure screen with in-app Arabic and system English renders right to left in Arabic; absent `oc.appLocale` follows the system |
| `CorruptPrefsXmlTest` (Robolectric) | `:android:migration` | Android loads garbage as an empty map; M1 blocks; no write reaches the file |
| `LegacyTransformsTest` | `:android:migration` | L1–L3, ported case by case from `retired_prefs_test.dart`, `notification_preferences_test.dart` and the read-state consent tests |
| `RestoreAuthorityCheckTest` | `:android:migration` | M4 equality and predicate |
| `RestoreAuthorityParityTest` | `:androidApp` test | migrator predicate = `:android:linux` predicate on shared vectors |
| `MigrationAuditPrivacyTest` | `:android:migration` | planted passwords, tokens, prompt text and server addresses never appear in the journal, audit, report, log ring or the `migration_result` event; the event's fields are enums and buckets only (16 K-SEC-11) |
| `RoomImportTest`, `BigRowReadTest`, `RoomProfileIdSchemaTest` | `:core:db`, `:quality` | §6.3 counts and hashes; a queue with an empty `id` or a repeated `id` is imported as one unreadable `collection_state` row, exactly as Dart reads it, and the gate still opens; a collection that fails to verify stays on its source key; a 20 MiB row reads back; every table has `profile_id` or is allowlisted |

### 10.3 Instrumented tests (PC emulator `OC_API37`, T3)

- `SyntheticFlutterSecretsTest` (`:android:migration` androidTest): creates an RSA key under
  `<testPackage>.FlutterSecureStoragePluginKeyOAEP` with the plugin's exact `KeyGenParameterSpec` (copied from
  `KeyCipherImplementationRSAOAEP.makeAlgorithmParameterSpec`), wraps a random 16-byte AES key, writes FSS-format
  entries, runs M2 against the real AndroidKeyStore, asserts `SecretStore` contents, then deletes its own alias. It runs
  on any emulator without a Flutter install. Its entries include the JSON-valued secrets the UI seed can't make: a
  GitHub credential (`oc.githubToken.<id>`, the shape `GitHubDeviceFlow` stores) and a BYO envelope
  (`oc.byoHostSecrets.<id>`, `{schema:2,keyAlias,publicKey,deviceToken}`), both with fixture values, and it checks that
  every string field of 8+ characters was registered with the `Redactor`.
- `KeystoreSecretStoreDeviceTest`: the real Keystore AEAD round trip; the key survives a process restart.
- `UpgradeJourneyAssertions` (`:androidApp` androidTest, `qa` build type): runs right after the Gradle connected task
  installs the native APK over the seeded snapshot (§10.5), with
  `android.injected.androidTest.leaveApksInstalledAfterRun=true`. Compares the journal and audit against
  `seed-manifest.json` (counts and known fixture plaintexts).

### 10.4 Fixture dumps from a real Flutter install (emulator recipe)

Run on the PC, outside 00:00–02:00 (memory quiet-hours), holding the single emulator lock (memory emulator-lock-holder).
**Every command targets the emulator `emulator-5584` and nothing else** (hard rule; audit 04+05 #14). Never use the
owner's phone for this, not even for a read: no root, and its data is real. The script stops on the first failed
command and refuses to run before it has proven which device it talks to.

```bash
set -euo pipefail
PKG=io.github.eslamasabry.opencode_mobile
export ANDROID_SERIAL=emulator-5584          # adb, Maestro and the python helpers all read it; never unset below
ADB="adb -s $ANDROID_SERIAL"
# 0. Which Dart code wrote the owner's data: read from the record, never from the phone (§9.3 step 1,
#    docs/migration/flutter-final.md). The versionCode alone is not enough: local builds and Shorebird patches share
#    numbers. Stop if any value is unknown.
VC=<versionCode from docs/migration/flutter-final.md>
REL=<release version>; PATCH=<Shorebird patch number, or "none">; COMMIT=<integration commit of that patch>
OUT=android/migration/src/test/resources/fixtures/flutter-final-$REL-p$PATCH
mkdir -p "$OUT"

# 1. A fresh, rootable AVD (google_apis x86_64, API 37) on the fixed port 5584
emulator -avd OC_API37 -port 5584 -wipe-data -no-snapshot-load -no-boot-anim &
EMU_PID=$!
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
# Prove the target before any mutating command: an emulator, the expected AVD, the expected serial.
[ "$($ADB shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ]            || { echo "not an emulator"; exit 1; }
[ "$($ADB emu avd name | head -1 | tr -d '\r')" = OC_API37 ]           || { echo "wrong AVD"; exit 1; }
$ADB root                                                               # set -e stops here if root is refused
$ADB wait-for-device

# 2. The fixture APK: built from $COMMIT with the toolchain step of tools/rollback/build_r1.sh (same Flutter version),
#    --build-number=$VC, universal, owner's local key. Never a different commit with the same versionCode.
$ADB install -r flutter-final-$COMMIT-release.apk
$ADB shell dumpsys package $PKG | grep -E 'versionCode|signatures'

# 3. Fixture servers on the PC (../research/testing-perf.md §6.4): OC2 fixture/replay server, Paseo fixture

# 4. Seed through the UI (Flutter debug APKs don't run on the Shorebird engine)
maestro --device "$ANDROID_SERIAL" test e2e/seed/flutter-final.yaml
#    adds: an OC2 server with password fixture-pass-1; an HTTP-on-LAN server with cleartext consent; an HTTPS server
#    with a certificate pin; a Paseo profile with password fixture-paseo-1; a Codex profile with token
#    fixture-codex-1 (only the token's migration is checked, not a live Codex session);
#    settings changed in every Settings group (style Soft, motion Calm, Arabic, quiet hours 22:00-07:00);
#    a server-wide always-allow rule on the OC2 server (oc.autoApprove); read-state sharing switched off for one
#    server (L3); background live mode switched on (oc.keepLiveInBackground);
#    an offline queued prompt (fixture server stopped) and a second one left with a dispatch marker;
#    a draft with a photo; two saved prompts, one with an attachment; a pinned conversation; a learned phrase;
#    a scheduled prompt; built-in Linux installed and the phone server running;
#    on This phone, the agent host set up with a fixture agent and one finished agent chat (oc.agentHostSecret,
#    the agent home, oc.agentPhoneGate) [U: W8 picks the fixture agent; a real Claude account is never used];
#    a small voice pack downloaded, and the natural read-aloud voice switched on and downloaded
#    (voice.read_aloud_natural + the Kokoro files);
#    a BYO host set up against a throwaway SSH container on the PC (oc.byoHost, oc.byoHostSecrets, Keystore alias
#    oc.byoHostSsh) [U: if the BYO flow can't reach a local container, BYO is covered by SyntheticFlutterSecretsTest
#    only, and the gap is written in the evidence record].
#    No GitHub sign-in: the release build hard-codes github.com (lib/domain/git/github_device_flow.dart:22-24), so no
#    fixture can stand in, and a real sign-in needs an account and a browser. JSON-valued secrets are covered by
#    SyntheticFlutterSecretsTest (§10.3).
python3 e2e/seed/surfaces.py --serial "$ANDROID_SERIAL"
#    UIAutomator: a Conversations widget, the Status widget and the usage widget placed; both tiles added; a
#    conversation shortcut pinned from the launcher; one notification channel's importance changed in system Settings.

# 5. Edge values the UI can't make, written while the app is stopped
$ADB shell am force-stop $PKG
$ADB shell cat /data/data/$PKG/shared_prefs/FlutterSharedPreferences.xml > "$OUT/fsp.xml"
python3 tools/flutter-fixtures/add_edge_keys.py "$OUT/fsp.xml"
#    a legacy Java-serialized list, a double, an oc.budgets.* key, oc.notifyRules.<id> without oc.notify.version,
#    and an offline queue of at least 10 MB of fixture prompts (the two seeded entries and their markers kept)
$ADB push "$OUT/fsp.xml" /data/local"$OUT/fsp.xml"
$ADB shell 'cat /data/local"$OUT/fsp.xml" > /data/data/'$PKG'/shared_prefs/FlutterSharedPreferences.xml'   # keeps the app uid as owner
python3 tools/flutter-fixtures/add_team_files.py --serial "$ANDROID_SERIAL" $PKG
#    AI Team data as the owner's phone has it (deferred feature, never touched by native): files/oc.teamEngine.<id>/
#    with a fixture project snapshot without planner data (B-AGENT-303), and files/oc.teamEngine.protection-required,
#    which makes BuiltinLinux require confined proot. Owned by the app uid, written with adb root.
$ADB shell pm unstop $PKG        # or MY_PACKAGE_REPLACED is never delivered (memory restore-proof-recipe)

# 6. OS-held state before the install; J1 compares after it
$ADB shell dumpsys package $PKG > $OUT/package-before.txt              # runtime permission grants
$ADB shell appops get $PKG > $OUT/appops-before.txt                    # special app-ops
$ADB shell dumpsys shortcut | grep -A30 "Package: $PKG" > $OUT/shortcuts-before.txt
$ADB shell dumpsys notification --noredact | grep -A3 "pkg=$PKG" > $OUT/channels-before.txt
#    and whether the Flutter build starts the phone server with the protection marker present (recorded in
#    seed-manifest.json; J1 expects native to behave the same way)

# 7. Save the snapshot every journey starts from
$ADB emu avd snapshot save flutter-final-seeded

# 8. Dump (prefs and listings only; the rootfs is counted, never copied)
$ADB shell "cd /data/data/$PKG && tar -cf - shared_prefs" > $OUT/shared_prefs.tar
$ADB shell "cd /data/data/$PKG && find files app_flutter cache code_cache no_backup databases app_webview \
  -maxdepth 3 -not -path 'files/linux/*' -not -path 'files/projects/*' -exec stat -c '%F|%s|%n' {} +" > $OUT/tree.txt
$ADB shell "cd /data/data/$PKG && du -sk files/linux files/projects; find files/linux | wc -l" >> $OUT/tree.txt
$ADB shell "find /sdcard/Android/data/$PKG -maxdepth 3 -exec stat -c '%F|%s|%n' {} +" >> $OUT/tree.txt
python3 tools/flutter-fixtures/xml_to_prefs_json.py $OUT   # writes prefs.json for commonTest readers
cp e2e/seed/seed-manifest.json $OUT/                        # the fixture plaintexts and expected counts
```

- Shut down with `$ADB emu kill`, then `ps -p $EMU_PID` (exact PID, never by pattern). The python helpers refuse to run
  without `--serial emulator-5584` and re-check `ro.kernel.qemu` themselves.
- The FSS XMLs in the dump are ciphertext bound to that AVD's Keystore. They are kept for structure tests only. Secret
  decryption against them runs only on the snapshot (J1).
- Every value in the dump is fixture data typed by the seed flow. A reviewer checks `seed-manifest.json` against the
  dump before commit. No real credential is ever typed into the seed.
- `tools/flutter-fixtures/` also holds `dart_vectors.dart` (§2.5) and `extract_ledger.py` (§3.6). They run with the
  toolchain named by the frozen commit's `scripts/release.sh` against a checkout of that commit.
- Redo the recipe whenever the release, patch or commit on the owner's phone changes before the switch (in-flight
  branches, 01 D16).
- A public-lineage variant (`v2.1.0+57` from GitHub, CI signer) is recorded the same way before the first public native
  release (01 D2).

### 10.5 Upgrade journeys (T3, PC emulator, release or `qa` build of the native app)

Every native APK in these journeys takes its number from `next_version_code.sh` (§9.1), above whatever the emulator
holds. Every journey runs under the §10.4 preamble: `set -euo pipefail`, `ANDROID_SERIAL=emulator-5584`, every `adb`,
Maestro and helper call bound to that serial, and the emulator/AVD check before the first install. A bare `adb` in the
table below means `adb -s emulator-5584`.

| Id | Journey | Pass when |
|---|---|---|
| J1 | load `flutter-final-seeded` → `adb install -r native.apk` (versionCode ≥ 10000, same signer) → launch → `e2e/upgrade/*.yaml` + `UpgradeJourneyAssertions` + `adb root` file checks | servers connect without a password prompt (Codex token kept, no "Enter token"); settings equal; queued prompts still queued and **not resent**; the marked prompt waits for review; draft and photo back; saved prompts and pins back; always-allow still applies; the read-state choice kept; the schedule fires; all three widgets redraw; tiles answer; `dumpsys shortcut` lists the pinned shortcut and it opens its conversation; `dumpsys notification` shows the changed channel importance; runtime grants and app-ops equal the before-files; background live mode resumed; the agent chat resumes without sign-in; Linux starts without reinstall, and with the protection marker present it behaves as the Flutter build did; voice pack "Ready" and the natural voice active with no download; AI Team files, the FactsBook external directory, `files/linux/ubuntu.ready` and the rootfs inode count unchanged; backup exists; audit counts equal `seed-manifest.json`; S0's time for the ≥ 10 MB prefs file recorded in the evidence record |
| J1b | as J1, but first launch the Flutter app, let the phone server run and finish one agent turn, then `adb install -r` with no app opened afterwards | the restore receiver brings the phone server back by itself; no process of the app's uid with PPID 1 (`ps -A -o PID,PPID,USER,NAME`, memory proot-orphans-heat); `NativeComponentUpdateRecovery` had nothing to roll back |
| J1c | as J1 with the system language English, the in-app language Arabic and `files/native/qa-delay-ms` = 5000 (`qa` build type only) so the Updating screen shows | Updating screen and re-enter sheet in Arabic, right to left; nothing flips when H1 runs |
| J2 | as J1, with `files/native/qa-kill-after` = `S0`, `M1`, `M2:1`, `M2:all`, `M3`, `M4`, `M5`, `M6`, and (from a forced `Blocked` state) `R2:requested`, `R2:afterMove`, `fresh:requested`, `fresh:afterMove` in turn (`StepInterrupter`, compiled only into the `qa` build type), then relaunch | each ends in the J1 state (or the restored / fresh state for the R2 and Start fresh points); a further relaunch changes no byte of prefs, `oc_secrets_v1.xml`, journal or audit |
| J2-R2 | (a) J1, send one queued prompt and let one schedule fire, then force `Blocked` (`qa`) and open safe mode; (b) the same, then `adb reboot` | **R2 is not offered** (`gate.firstOpenAt` is set): only Start fresh and Report a problem; the fixture server records exactly one prompt and one occurrence after reconnect and reboot. (The production path cannot restore after first use, Astra N2; the tombstone partition is proved below the journey level by `OutboundTombstonesTest` and `RestoreTransactionTest`, which call `RestoreTransaction` directly with the eligibility check bypassed and are labelled as recovery-layer tests, not user journeys) |
| J2-S0 | as J1 with `qa-kill-after` = each of the 16 S0 fault points, and with a planted `FlutterSharedPreferences.xml.bak` | each relaunch ends with one verified backup whose manifest marks the `.bak` authoritative; a tampered published backup gives `Blocked(BackupInvalid)` and is never overwritten |
| J8b | the `qa` build with main-thread StrictMode `penaltyDeath`, a 64 MiB prefs file and `qa-delay-ms=15000` on S0; install, then launch, then a widget update and an alarm during S0 | first frame draws; no StrictMode death; no ANR in `ApplicationExitInfo`; every receiver calls `finish()` by `ProcessStart + 7 s` (§7.1) |
| J3 | after J1: `adb reboot` | the phone server is restored by itself (`ps -A` shows the server under the app uid); the recipe's `packageVersion` is the native versionCode; the schedule still fires; widgets redraw |
| J3b | as J1 with `qa-delay-ms` = 30000 (the gate stays closed past the 20 s window) → install, no app opened; then `adb reboot` with the same delay | after both the install and the reboot, the phone server is started inside the broadcast window (no `ForegroundServiceStartNotAllowedException` in logcat); alarms re-armed; surfaces redrawn once the gate opens |
| J4 | after J1: `build_r1.sh` with the next number → install R1 → the app opens with the same data; a scheduled prompt fires and the widget refreshes **under R1** (WorkManager kept its database); change one password, delete one server, add a draft → install the next native candidate | `aapt2` shows the allocated versionCode on every R1 APK; R1 shows all data; on return the changed password is in `SecretStore`, the deleted server's secrets and native-only data are gone, the draft is present, no duplicate send |
| J4b | J4, then change a different password in native, then J4 again with no password change in R1 | after the second return, the password changed natively between the round trips is kept (the snapshot, not S0, was compared) |
| J5 | fresh install of native (no Flutter data) | `source.kind = none`, first-run welcome, no failure |
| J6 | `adb install -r` of a lower-versionCode native build | refused by Android |
| J7 | two native updates in a row (next candidate, then the one after) | journal untouched, no step re-runs |
| J8 | (a) `files/native/qa-free-bytes` set below what S0 needs, then install and launch; (b) once before the switch on a real full disk: `am force-stop` **before** `adb install -r` (so `MY_PACKAGE_REPLACED` isn't delivered and S0 doesn't run), then fill `/data` with a file to just below what S0 needs, then launch. Android refuses `adb install` itself below its low-storage threshold, so the fill must come after the install | LowSpace screen; nothing changed; after freeing space (or deleting the override), "Try moving your data again" completes |

J1, J1b, J2 and J3 run before every owner APK that touches migration code (01 §10.2). J1c, J3b and J8 run on the
switch candidate. J4 and J4b run on the switch candidate, and again whenever the rollback branch or the native
WorkManager pin changes.

**Proof for the owner.** W8 records J1, J2 and J4 on the emulator with `adb shell screenrecord` (segments of at most
3 minutes) and edits them with the Remotion skills as memory live-video-proof says: real footage only, step captions,
a visible "×N" on speed-ups. It writes `docs/qa/native-upgrade-<date>/README.md` (memory audit-trail-rule): scope, the
native commit and APK SHA-256, the fixture's release, patch and commit, the R1 APK SHA-256, the device and Android
version, numbered steps with expected and actual, evidence (logcat lines, timings, video links), how to reproduce, and
what is **not** proven.

### 10.6 Reverse compatibility (only while R1 exists)

`FlutterFormatCompatTest` checks that the Flutter app can read what native wrote:

1. Native side (`:androidApp` test, Robolectric): drive each engine's writer for the high-risk keys (`oc.profiles`,
   `oc.offlineQueue` with a dispatch marker, `oc.sessionDrafts`, `oc.promptStash.*`, `oc.pendingCommands.*`,
   `oc.automation.*`, `oc.builtinRecovery.*`, `oc.scheduledPrompts.*`, `oc.sessionPins.*`, `oc.sessionViews.*`,
   `oc.widgetTarget.*`, `oc.notify.*`) and dump the physical `flutter.*` map as typed JSON.
2. Flutter side (a test on the `rollback/flutter-final` branch, run with the toolchain the frozen commit's
   `scripts/release.sh` names, §9.3): `SharedPreferences.setMockInitialValues(dump)`, then load each store
   (`OfflineQueueStore`, `ProfileStore`, `SessionDraftStore`, `PromptShelfStore`, `PendingCommandJournal`,
   `AutomationPolicyController`, …) and assert `readable` and equal values.

### 10.7 The owner's phone on switch day (runbook)

1. Read-only adb checks: installed versionCode and signer (`dumpsys package`; `apksigner verify --print-certs` on the
   pulled base APK) equal the owner lineage `1DE5BF08…D60C` (01 D2), and the release, Shorebird patch and commit equal
   `docs/migration/flutter-final.md` (the fixture was made from the same code). Free space ≥ 2 × the prefs size +
   50 MB (ask the owner, or read `df` for `/data`).
2. J1, J1b, J2, J3 green on the emulator for this exact APK (memory test-on-emulator-not-phone,
   stabilize-before-shipping); J4 green on this candidate with the script.
3. **Nothing is working on the phone.** Ask the owner, and check read-only: no turn running on This phone, no setup or
   component update job, no schedule due in the next 15 minutes, and `adb shell dumpsys activity services $PKG` shows
   at most the phone server's service. `adb install -r` kills every process of the app's uid, including the phone
   server and its Linux processes; that is expected, and the restore brings the server back. Don't stop the phone
   server from the app first: that records "stopped by you", and the server would then rightly not come back by itself.
4. The owner closes the Flutter app normally (no force-stop: a force-stopped package misses `MY_PACKAGE_REPLACED`).
5. `adb install -r` the arm64-v8a APK with the number from `next_version_code.sh`, while the owner is around (memory
   auto-install-latest-build).
6. Right after, read-only: `adb shell ps -A -o PID,PPID,USER,NAME` shows no process of the app's uid with PPID 1 (memory
   proot-orphans-heat); the phone server came back by itself; `NativeComponentUpdateRecovery` had nothing to roll back
   (from the audit or Phone health).
7. The owner opens the app. If the re-enter sheet shows, they do those sign-ins.
8. The owner sends "Report a problem" (counts only) so the audit can be compared with the bridge-release census
   (M-D2), or with expectations.
9. Within a day the owner reboots once, and the read-only check of step 6 runs again.
10. R1 is not kept as an APK. If the owner wants to go back before saying the native app is fine (M-D1), run
    `build_r1.sh` with the number `next_version_code.sh` gives on that day; J4 proved the script on this candidate.

---

## 11. Work units

One agent per unit, own worktree and branch (AGENTS.md). Sizes are estimates for planning, re-planned at M0 with
slice-0 throughput (01 P10).

| # | Unit | Modules | Owner / model | Slice | Needs | Est. size | Done when |
|---|---|---|---|---|---|---|---|
| W1 | P5 spike: S0 timing; prefs adopted by a Kotlin reader in a library module; one v11 secret decrypted from a real Flutter install; Room trigger numbers | `:android:migration` (spike), `:androidApp` | S migration (Opus) | 0 | 01 P1 skeleton | ~800 lines | 01 P5 acceptance recorded in `docs/proofs/P5.md` |
| W2 | `DartJson` + `Sha256` expect/actual, `FlutterPrefsCodec`, `KeyValueStore` + `FlutterPrefsStore` (gated reads, CAS lock order), `MigrationReader`, `WriterLane`, `KotlinPrefsFile`, `MigrationGate` (`:core:data`) and `GateState`/`MigrationStatus`/`MigrationActions`/`ReentryNotice` (`:core:model`) | `:core:common`, `:core:data`, `:core:model` | S core (Codex builds, owner rule codex-backend-claude-frontend) | 1 | interfaces frozen at end of slice 0 | ~1.6k + 1.6k tests | §10.2 rows for these green; Konsist rule 7 (and the `MigrationReader` allowlist) and the `NoSuspendInsideLock` Detekt rule pass; `:ui:shell` has no `:core:data` edge |
| W3 | `ProfileScope`, ledger, sweep tests, `ServerProfileJson`, `ProfileId` | `:core:data`, `:core:model`, `:quality` | S core (Codex) | 1 | W2 | ~700 + 900 tests | `PrefsLedgerTest`, `ProfileSweepShapesTest` green |
| W4 | `SecretStore` (Keystore AEAD + software seam) | `:core:data` | S core (Codex) | 1 | W2 | ~400 + 500 tests | JVM and device tests green |
| W5 | `BlobStore` | `:core:data` | S core (Codex) | 2 | W2 | ~400 + 600 tests | ported vault tests green |
| W6 | Runner: journal, `PendingSwap`, S0, M1–M6, secret kinds, L1–L3, M4 predicate and Kotlin-half counts, audit and `migration_result`, `StepInterrupter` (`qa-kill-after`, `qa-delay-ms`, `qa-free-bytes`, `qa` build only), `MigrationActions` incl. `reportProblem`, R2 and Start fresh (two-phase), `R1.return`, `MigrationFollowUpWorker` (normal request) and its component-ledger row | `:android:migration` | S migration (Opus) | 1–2 (core), 6 (hardening) | W2–W4 | ~3k + 3.5k tests | §10.2 runner rows green on the fixture |
| W7 | Gate screens, re-enter sheet (rows by kind), strings, the gate-UI locale override (with `:androidApp`'s `MainActivity` read) | `:ui:shell`, `:ui:words`, `:androidApp` (one function) | shell owner (Sonnet) | 2 | W2/W6 contract (`MigrationStatus`, `GateState`, `ReentryNotice`, `MigrationActions` from `:core:model`) | ~650 | Roborazzi goldens (light/dark, en/ar incl. `GateLocaleTest`, 200 % text) on the VPS; the screens appear in W8's J1c video |
| W8 | Fixture recipe (code identity, seed, surfaces, edge keys, AI Team files, OS-state before-files), J1–J8 incl. J1b, J1c, J3b, J4b, assertions, videos and evidence record | `e2e/`, `tools/flutter-fixtures/`, `:androidApp` androidTest | S migration (Opus) + coordinator (emulator lock) | 0 (J1 short form), 6 | W6 | ~1.6k | all journeys green on the switch candidate; J1, J2 and J4 recorded as edited videos (memory live-video-proof); `docs/qa/native-upgrade-<date>/README.md` written (memory audit-trail-rule) |
| W9 | R1 rollback branch (guard in all five entrypoints, `NativeReturnGuard.kt`, WorkManager pin), `tools/rollback/build_r1.sh`, `tools/release/next_version_code.sh` and the version-code ledger | `rollback/flutter-final` branch of the frozen Flutter repo; native repo `tools/` | Sonnet | 6 | W8; the code identity of §9.3 step 1 | ~120 Dart + ~150 Kotlin + ~200 shell | J4 and J4b green; `aapt2 dump badging` shows the allocated versionCode for every R1 APK; `apksigner` shows the owner signer; the Flutter version and cache hash recorded |
| W10 | `FlutterFormatCompatTest` | `:androidApp` test + rollback branch test | Sonnet | 6 | engines writing those keys | ~600 | green on the candidate |
| W11 | Room v2 import | `:core:db`, `:android:migration` | S migration (Opus) | switch+1 (or 6 if P5 fires) | engine repositories | ~1.5k + 1.5k tests | §6.3 verification green; J1 extended with v2 |
| W12 | v3 cleanup | `:android:migration` | Sonnet | ≥ switch+2 | M-D3 OK | ~400 | cleanup journey green |
| W13 | Census in the bridge release (if 01 D4 ships and M-D2 is yes) | frozen Flutter repo | Sonnet | before the switch | D4 | ~150 Dart | the counts appear in Report a problem |

Order: W1 → W2 → (W3, W4 in parallel) → W6 → (W5, W7, W8 in parallel) → W9, W10 → switch → W11 → W12.

Shared-library rule: `:core:data` has one owner at a time; W3 and W4 are sequential commits by the same core owner, or
two agents in separate packages with the interfaces frozen first.

### Acceptance checks for the switch release

- A1. J1, J1b, J1c, J2, J3, J3b, J5, J6, J7, J8 green on the release candidate; J4 and J4b green with an R1 built by
  the script, **unless Room ships in the switch release** (P5 trigger), in which case there is no R1 (§9.3, M-D1).
- A2. `unchangedKeysDigest` equal on the real-install fixture: only the M2 and M3 write sets changed.
- A3. Appendix A regenerated from the final Flutter commit; no unclassified key.
- A4. `PrefsLedgerTest`, `ProfileSweepShapesTest`, `RoomProfileIdSchemaTest` (if Room ships) green.
- A5. `MigrationAuditPrivacyTest` green; a manual check of one Report-a-problem bundle shows counts only.
- A6. `build_r1.sh` proven on this candidate (J4): its APKs carry the allocated versionCode and the owner signer. No
  R1 APK is hosted in advance. Not applicable when Room ships in the switch release.
- A7. The owner's phone checks in §10.7 steps 1–2 pass before the install.

---

## 12. Owner decisions for this section

Work proceeds on the default until the owner answers.

| # | Question | Default (recommended) |
|---|---|---|
| M-D1 | Keep a tested way to build a "go back" version of the old app (R1) for the switch line, and who closes it? | Yes, as a script proven on each candidate, built on the day it is needed (owner lineage only). It closes when the owner says the native app is fine, or when the Room import ships, whichever comes first. If the P5 trigger puts Room in the switch release, there is no R1 at all |
| M-D2 | Add a counts-only "migration check" to the last Flutter (bridge) release, so we know the real data shape before the switch? | Yes, if the bridge release (01 D4) ships |
| M-D3 | When may the Flutter-era copies be deleted (backup, old password files, `app_flutter/`)? | Not before switch+2, and only after the owner says yes |
| M-D4 | (Resolved 2026-11-01, audit 04+05 #12: when the P5 trigger brings Room into the switch release, the moved sources always go to verified files, §6.4. No owner decision left.) | — |
| M-D5 | The dead keys (`oc.glass`, `oc.effectsDepth/Edges/GlowColours/GlowSpeed`, `oc.olderDraftsBackupV1.*`, A2A data): keep them until v3, or delete at the switch? | Keep until v3. Nothing Flutter-era is deleted in the switch release except the L1 retirements the Flutter app already performs on every profile load (`lib/state/profiles.dart:224-255`) |
| M-D6 | Should "Reset saved sign-ins" also remove FactsBook and A2A secrets (excluded today)? | Yes, when those features are ported; until then they stay untouched |
| M-D7 | Read-state sharing: keep each server's current choice, or ask again after the switch? | Keep the current values |
| M-D8 | During R1's window, also write password changes back into the old app's password file, so going back never asks for a password? | No. It doubles where secrets are written; after going back, only passwords added or changed after the update have to be typed again |

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
| A restore or other foreground-service start waits for the gate past the broadcast's ~20 s window | the phone server silently stays down after the update or reboot | FGS starters never wait for the gate (§7.1); J3b |
| R1 installed over native opens WorkManager's newer database | every enqueued schedule run, widget job and periodic job is dropped | R1 ships the native WorkManager pin (§9.3); J4 fires a schedule under R1 |
| An APK carries a different versionCode than allocated (ABI splits) | every later build is refused as a downgrade; only an uninstall, which wipes data, gets past | universal build + split script; `aapt2` check before hosting (§9.1) |
| An engine reads a store before M2/M3 finish | a server asks for a password it has, or old notification settings are cached | engines start after Open; `GateNotOpen` tripwire; `GateOrderTest` (§2.2) |
| Real data shape differs from the fixture | an untested path on the owner's phone | M-D2 census; the fixture rebuilt from the exact release, patch and commit on the phone (§9.3 step 1) |

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
| `oc.a2aBearer.<agentId>` | secret | other | A2A (dropped, D6) | X | secure-storage name, not a pref; counted by M2, **not** copied; stays in FSS until v3 deletes the FSS files |
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
| `oc.native.returnedAt` | S (ISO) | app | R1 rollback build | new (R1) | written only by the Flutter rollback build, from every entrypoint and Kotlin writer whenever `oc.native.flutterReadable` is not false; consumed by native on return |
| `oc.native.movedToDb` | S (JSON) | app | `:android:migration` | new (v2) | logical keys moved to Room + import time |

## Appendix B. Secure-storage names (copied into `SecretStore` by M2)

Kind decides what an unreadable entry does (§7.4 M2): **typed** and **sign-in/setup** kinds are listed once on the
sheet, by kind and server; **app-made** ones are renewed silently by their owner and never listed; **dropped** ones are
counted, not copied.

| Name | Content | Kind | Native owner | If it can't be migrated |
|---|---|---|---|---|
| `pw.<id>` (remote server) | OpenCode server password | typed password | `:engine:profiles` | server shows "Enter password" (`requiresPasswordReentry`); sheet row "Enter password" |
| `pw.<id>` (built-in phone server) | password the app generated for the phone server | app-made | `:engine:runtime` | renewed at the next phone-server start, which rewrites `/root/.oc-builtin/server.password` (`lib/builtin/builtin_server.dart:240-246`); never listed |
| `oc.codexToken.<id>` | Codex token or Paseo daemon password | typed token | `:engine:profiles` | "Enter token" (`requiresCodexTokenReentry`; a Paseo profile may legitimately have none, so only a found entry that fails is flagged); sheet row "Enter token" |
| `oc.githubToken.<id>` | JSON GitHub credential | sign-in | `:engine:project` | Git page shows "Sign in to GitHub"; sheet row "Sign in to GitHub again"; the server still connects |
| `oc.byoHostSecrets.<id>` | JSON `{schema:2,keyAlias,publicKey,deviceToken}` (the SSH private key is the Keystore alias `oc.byoHostSsh.<id>`, untouched) | setup | `:engine:integrations` | BYO host shows "Set up again"; sheet row "Set up again" naming the host |
| `oc.agentHostSecret.<owner>` | 64-hex password of the on-phone agent host | app-made | `:engine:agents` | the agent host makes a new one when none is stored (`phone_agents_host.dart` `_password`); rotation by restart is designed in 17 §1.12 (triggers: Start fresh, unreadable after M2, a 4401 from the helper; never on a timer) [U: P17-1]; never listed |
| `oc.quotaToken.<id>` | usage-collector read token | app-made | `:engine:integrations` | collector setup writes a new token; never listed |
| `oc.teamEngineAuth.<id>` | phone project engine bearer | app-made | `:engine:team` (D6) | engine re-pairs when AI Team is ported; never listed |
| `oc.factsbook.<id>`, `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>`, `oc.factsbook-node.passphrase` | FactsBook token, owner seed, unsent notes, node passphrase | deferred | FactsBook (D6) | copied; handled when FactsBook is ported; never listed |
| `oc.a2aBearer.<agentId>` | external agent bearer | dropped | dropped (D6) | counted, not copied; deleted with the FSS files by v3 |
| `oc.orchestration.<id>.grant` | reserved, never written | — | — | — |
| `oc.secure.probe` | Linux keyring probe | — | desktop phase | not on Android |

Other Keystore entries the app owns, kept by the unchanged UID and never touched by the migration:
`<package>.FlutterSecureStoragePluginKeyOAEP` (read by M2 only), `oc.byoHostSsh.<id>` (BYO SSH key) and
`oc.phoneEngineBoundary.v1` (`PhoneEngineAttestation`, AI Team phone engine).

## Appendix C. Files and directories

| Path (under `/data/data/io.github.eslamasabry.opencode_mobile/`) | Owner today | Native owner | Migration |
|---|---|---|---|
| `files/linux/ubuntu/**`, `files/linux/ubuntu.ready` | Kotlin `BuiltinLinux` | `:android:linux` | untouched; J1 checks marker and inode count |
| `files/linux/setup.json`, `files/linux/setup.log` | Kotlin `SetupRunner` (resumable setup job, B-LINUX-007) | `:android:linux` | untouched; `setup.json` is a cross-version contract: native resumes a job the Flutter build started |
| `files/linux/server.log`, `files/linux/<service>.log`, `<service>.previous.log` | Kotlin `BuiltinLinux` service logs | `:android:linux` | untouched |
| `files/linux/proc/`, `files/linux/agent-root-view/` | Kotlin `BuiltinLinux` (proot fake `/proc`, agent root view) | `:android:linux`, `:android:agenthost` | untouched |
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
| `files/diagnostics/report_problem.json`, `.pending` | Dart | `:engine:diagnostics` | `.json` adopted; an orphan `.pending` counted by M1 item 6 and discarded on first open (§7.8) |
| `files/crash-diagnostics.json`, `files/crash-diagnostics-consent`, `files/native-last-crash.properties` | Dart + Kotlin `NativeCrashStore` | `:engine:diagnostics` + `:androidApp` | untouched; consent epoch kept |
| `files/crash-diagnostics.pending`, `files/native-last-crash.properties.tmp` | Dart, Kotlin (interrupted writes) | `:engine:diagnostics`, `NativeCrashStore` | counted by M1 item 6; discarded on first open (§7.8) |
| `files/.phone-engine-proof-<id>`, `files/.phone-engine-view-<id>`, `files/bb9-*.json` | QA fixtures | QA builds only | untouched |
| `files/native/` **[new]** | — | `:android:migration`, `:androidApp` | journal, audit, crash-loop record |
| `files/migration/flutter-v57/` **[new]** | — | `:android:migration` | S0 backup |
| `shared_prefs/*.xml` | see Appendices A and D | — | adopted / untouched |
| `app_flutter/` | Flutter engine and maybe Shorebird | — | listed by M1; deleted by v3 |
| `app_webview/` | Android WebView (dev preview) | `:ui:kit-content` WebView | carries over by itself |
| `cache/` (export plan, `image_picker` files, `proot-tmp`, `ubuntu-base.tar.gz`) | various | various | never cleared by the migration |
| `cache/migration-report/` **[new]** | — | `:android:migration` | the gate-exempt problem report for the share sheet (§8.3) |
| `files/native/fss-snapshot.json` **[new]** | — | `:android:migration` | FSS ciphertext digests for `R1.return` (§9.3); deleted by v3 |
| external `Android/data/<pkg>/files/` (`factsbook/`, `factsbook_node_host.dart:248`) | Dart FactsBook node | FactsBook (D6) | untouched; names and sizes recorded by M1 |
| external `Android/data/<pkg>/cache/` | Kotlin `ProjectExportBridge` | `:android:platform` | untouched; recorded by M1 |
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
| `builtin_server_recovery` | `runtimeGeneration`, `wanted`, `userStopped`, `owner`, `enabled`, `restoreOwner`, `restoreReason`, `idleState`, `oc.builtinRuntimeOwnership.<p>`, `oc.builtinRuntimeDrain.<p>`, `drainOwner`, `drainAll`, `oc.builtinRecoveryBudget.<p>`, `oc.builtinRecoveryReceipts.<p>`, `oc.builtinRuntimeRecipe.<p>` (cold-start recipe bound to `packageVersion` = `longVersionCode` and `rootfsGeneration`; the `PACKAGE_REPLACED` path accepts a recipe whose `packageVersion` ≤ the new versionCode and rewrites it, the `BOOT_COMPLETED` path needs an exact match), `backoffNext`, `backoffAt`, `backoffBoot` | `:android:linux` (M4 reads `restoreOwner` and the recipe, counts only) |
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
  `codex-backend-claude-frontend`, `no-raw-errors-rule`, `obvious-options-first`, `actions-name-their-target`,
  `live-video-proof`, `audit-trail-rule`, `proot-orphans-heat`, `freeze-owner-decisions-2026-11-01`.
- Toolchain and libraries read today [V]: the pinned Flutter's `FlutterPlugin.kt:673-679` and
  `FlutterPluginConstants.ABI_VERSION`; `tool/release/split_apk_by_abi.py`; the `work-runtime` 2.10.5 and 2.12.0 AARs
  (`WorkDatabase_Impl` versions 23/24, `fallbackToDestructiveMigration`); `BuiltinRestoreReceiver.kt`,
  `NativeServerRestoreEvent.kt`, `BuiltinLinux.kt` (recipe, receipts, backoff, protection marker), `SetupRunner.kt`.
- Not verified and left to the named proof: Shorebird's on-disk paths (M1 inventory on the first fixture), Android's
  empty-map behaviour for an unparsable prefs XML (`CorruptPrefsXmlTest`), Dart escape details (golden vectors),
  `CursorWindow` limits with the platform driver (`BigRowReadTest`), Keystore operation timings (W4), the ~20 s
  foreground-service allowlist after `MY_PACKAGE_REPLACED`/`BOOT_COMPLETED` on API 37 (J3b), and whether the phone-agent
  fixture and a local BYO SSH container can be seeded through the UI (W8).

## Review notes (2026-11-01 revision)

Every issue was checked against the source, the pinned toolchain or the plan before changing anything. Duplicates
were merged: the two `--split-per-abi` blockers; the two restore-receiver issues; the S0-comparison part of the two
`R1.return` issues.

**Fixed as proposed (verified first):**

- R1 build numbers. `FlutterPlugin.kt:673-679` and `ABI_VERSION` (arm64 = 2, x86_64 = 4) were confirmed in the pinned
  Flutter, and `build.gradle.kts` has no override. R1 is now one universal APK, split by `split_apk_by_abi.py`, with
  an `aapt2` versionCode check (§9.3).
- R1 is now built on demand through `build_r1.sh`, with a ledger-based number allocation that replaces "1–9
  reserved" (§9.1). It is owner lineage only.
- Gate types moved to `:core:model` (`MigrationStatus`, `MigrationActions`, `GateState`, `ReentryNotice`). 01 §6.1 was
  confirmed to forbid `:ui:shell` → `:core:data`. Only `awaitWritable`/`awaitOpen` stay in `:core:data` (§7.3).
- WorkManager. The 2.10.5 database is v23 and 2.12.0 is v24 (`AutoMigration_23_24`), and both use
  `fallbackToDestructiveMigration`, checked in the AARs. R1 now ships the native pin, and J4 fires a schedule under R1
  (§7.7, §9.3).
- Restore receiver. It decides from a gate-exempt read and starts the foreground service inside the ~20 s window. It
  is never handed to WorkManager, which keeps the existing one-try design (§7.1). J3b covers it.
- Reads are gated. Engines start only after Open, `SecretStore.read` waits, and `KeyValueStore.get` has a
  `GateNotOpen` tripwire. `MigrationReader` is the only early reader, with a Konsist allowlist (§2.2, §4.2).
  `GateOrderTest` covers it.
- Seed and J1 now include:
  - a launcher shortcut, a channel change, a queue of 10 MB or more, live mode, always-allow, read-state, a Codex
    token, the natural voice, the usage widget and the AI Team marker and files;
  - runtime grants and app-ops before and after the install;
  - J1b (install while the server runs, orphan check) and J1c (Arabic gate screens).
- Dropped the GitHub UI sign-in, since `github_device_flow.dart:22-24` hard-codes github.com. JSON-valued secrets go
  through `SyntheticFlutterSecretsTest`.
- `java.util.Base64` (strict, after stripping CR/LF) replaces `android.util.Base64`, so the reader test runs as a plain
  JVM test.
- `compareAndSet` has a fixed lock order with no suspension point under the lock, plus a `NoSuspendInsideLock` Detekt
  rule.
- `MigrationFollowUpWorker` is a normal (not expedited) request. The component freeze becomes "superset", plus a ledger
  row. **The coordinator must amend the "equal" wording in 01 §9.9.**
- J8 now has an override variant and a real-disk variant with force-stop before install.
- The R1 toolchain is read from the frozen commit's `scripts/release.sh`, which today is 3.47.1 and 3.47.8 if the TLS
  fix lands.
- `Sha256` is an expect/actual in `:core:common`, with no new library.
- Breaking steps commit the `flutterReadable` pref first. A1 and A6 are conditional on Room not shipping at the
  switch, and M-D1 says so.
- `R1.return` compares against a per-return FSS snapshot, not S0. It also handles secrets and profiles deleted in R1.
  The R1 guard writes `returnedAt` whenever the flag is not false. The copy for a changed password was corrected. J4b
  covers it.
- The guard runs in all five Dart entrypoints, and `NativeReturnGuard.kt` covers the Kotlin receivers and workers.
- §9.5 and M-D5 now carve out the L1 retirements.
- Copy:
  - "Try moving your data again" and "Sign in to these later".
  - `retry()` is single-flight.
  - Re-enter rows depend on the kind of secret, and app-made secrets (built-in `pw`, `agentHostSecret`,
    `quotaToken`, `teamEngineAuth`) are never listed, as confirmed in `builtin_server.dart:240-246` and
    `phone_agents_host.dart` `_password`.
- R2 is offered only before `gate.firstOpenAt`. R2 and Start fresh are two-phase, with the move done in the next
  `onCreate` (`PendingSwap`), and have J2 kill points.
- Report a problem has a gate-exempt `MigrationActions.reportProblem()` that writes nothing to prefs.
- The gate screens use `oc.appLocale` before the first frame, with no flip at H1.
- `ProfileFingerprintV1` is a frozen projection, separate from `toJson`.
- Runbook: pre-install "nothing is working" checks, a post-install PPID-1 and component-recovery check, and the code
  identity (release, patch and commit) recorded. The fixture and R1 are built from that commit.
- Appendix D adds the recipe, receipts and backoff keys. M4 now records the Kotlin half. The
  `oc.phoneEngineBoundary.v1` alias is listed.
- Appendix C adds `setup.json`/`setup.log`, the service logs, `proc/`, `agent-root-view/` and the external
  directories, and M1 inventories them.
- B-STATE-020 and B-AGENT-303 are added with deferral notes.
- A2A secrets are counted, not copied.
- There is one BASIC `migration_result` event (enums and buckets only, emitted through `:androidApp`, sender `none`
  until 16 D16-1).
- W8 adds edited videos and `docs/qa/native-upgrade-<date>/README.md`.

**Rejected or changed in part:**

- Foreground-service window: rejected the option to always start the foreground service in an "updating" state and
  decide later. It would start a foreground service and show a notification for people whose restore is disarmed. The
  receiver decides from a gate-exempt read inside the window instead.
- v2 import: rejected per-element quarantine or position keys for the offline queue. Dart's `OfflineQueueStore.load`
  fails the whole queue closed on one bad or duplicate entry (`offline_queue.dart:326-342`), so the import follows each
  collection's Dart rule. The point that one bad collection must never block the gate was accepted.
- Runbook: rejected "stop the phone server from the app" before the install. It records "stopped by you", and the
  server would then correctly not come back, which defeats the restore check. Everything else was accepted.
- Gated reads: `KeyValueStore.get` can't suspend, because it is in-memory and called from synchronous code. So it
  can't "wait": it throws in debug, qa and test builds and counts in release. The structural guarantee is that engines
  start only after Open.
- Seed items that need real accounts were adapted rather than dropped:
  - the phone agent uses a fixture agent, never a real Claude account;
  - the Codex profile checks only the token's migration;
  - BYO depends on a local SSH container, marked [U], with the synthetic test as fallback.

## Codex Sol audit follow-up (2026-11-01, second pass)

The Sol audit of 04+05 and the slice-0 P5 report (`docs/native/slice0/P5-readers.md` on `native/m0-2026-11-01`) were
checked against the text above. For this file:

| Finding | State |
|---|---|
| 2 BLOCKER, R2 can resend prompts and refire schedules | already fixed in substance (§9.4: R2 only before `gate.firstOpenAt`); hardened now with P5's `outbound-tombstones.json` outside every snapshot, `outboundHold`, and journeys J2-R2a/b (§9.4 step 5, 04 §13.4) |
| 3 BLOCKER (05 part), gate types in `:core:data` | already fixed (§7.3: `GateState`, `MigrationStatus`, `MigrationActions` in `:core:model`) |
| 4 MAJOR, two rollback flags, set too late, missing = allowed | fixed (§7.11 one barrier, journal + pref, lowered and read back before the first breaking write, `false` wins on repair; §9.3 guard blocks a missing flag once the journal exists) |
| 5 MAJOR, backup publish and restore not crash-safe; `.bak` twins | fixed (S0 steps 4–6: adopt-or-block a published backup, `.bak` copied as authoritative; §9.4 journaled `staged → movingAside → swapping → resetting` with `.bak` handling; J2-S0) |
| 6 MAJOR, R2 after Room | already fixed by the `firstOpenAt` rule; made explicit with the `storageGeneration` check (§9.4, §7.2) |
| 7 MAJOR, R1 reactivates old credentials, misses deletions | partly fixed before (per-return snapshot); fixed now with `oc.native.staleLegacySecrets` deleted by the R1 guard before bootstrap, and per-transition baselines (§9.3) |
| 8 MAJOR, CAS not atomic against plain commits | fixed (§2.2: one file lock for every mutation incl. compensation; conditional compensation; `FlutterPrefsLockTest`) |
| 9 MAJOR, 20 s / 8 s waits can ANR | fixed (§7.1 per-component budget table measured from process start; `ReceiverBudgetTest`; J8b) |
| 11 MAJOR, picker cache discarded | fixed (S0 backs it up; M1 records it; M3 L4b imports it into the record's own conversation) |
| 12 MAJOR, Room trigger keeps the oversized XML | fixed (§6.4: verified source files, journaled cutover, same P5 threshold as acceptance; M-D4 retired; 01 §9.8 amended) |
| 14 MAJOR, fixture recipe can hit the owner's phone | fixed (§10.4: `set -euo pipefail`, `ANDROID_SERIAL=emulator-5584`, emulator/AVD proof before any mutation, no phone read; §10.5 journeys bound the same way) |

Also folded in from P5: the startup fence (S0 off the main thread, before the first load of a backed-up file; 01 §9.7
amended), Android-exact Base64 decoding (§7.5), BigInteger → `Int64` (§2.2), the `image_picker_android` version, and
`flutter_image_picker_shared_preference.xml` in the backup. P5's ledger aliases (`oc.widgetUsage.<id>`,
`oc.factsbook-node.stopped`, `oc.providerRuntimeRefresh.v1.<id>.<enc>`) were already right in Appendix A; only the
research ledger used the short names. Still owed: the real-install census and the Keystore-alias-across-update proof
on the emulator (P5 §6), and moving P5's library from `native/tools/flutter-migration-readers/` into `:core:data` /
`:android:migration` (W2, W6) with its 84 tests.
