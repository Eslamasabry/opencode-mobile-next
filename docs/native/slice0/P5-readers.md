# Slice 0, proof P5: Flutter data readers

Written 2026-11-01 on branch `native/p5-readers-2026-11-01`. Code: `native/tools/flutter-migration-readers/`.
Plan: `docs/design/native-rewrite-2026-11-01/plan/05-data-migration.md` (called "05" below). Audit:
`docs/design/native-rewrite-2026-11-01/audit/codex-04-protocols-networking+05-data-migration.md` ("the audit").

## 0. Status

| State | What |
|---|---|
| Implemented | A Kotlin/JVM library with no runtime dependencies. It reads Android `FlutterSharedPreferences.xml` (and the `SharedPreferencesAsync` stores), decrypts `flutter_secure_storage` 11.2.0 data, maps every key to a typed model, and has crash-safe journal, backup, restore and verify helpers. |
| Verified | 84 `kotlin.test` tests pass on the JVM (§5). They use fixtures generated from the confirmed formats, and committed copies of those fixtures. |
| Not done | Not yet moved into `:android:migration`. Not run on an emulator, and no Android instrumented test yet. Nothing was read from a real Flutter install. The emulator proof (§6) is the next step. |
| Committed / pushed | Yes, on `native/p5-readers-2026-11-01`. No PR, no CI run, no tag, no release. |

The library has its own `settings.gradle.kts`. It does not include `native/settings.gradle.kts` or
`native/build-logic`, and it is not included by them. Run it with:

```bash
cd native/tools/flutter-migration-readers
./gradlew test               # 84 tests, about 15 s after the first download
./gradlew generateFixtures   # writes a fresh synthetic fixture set to build/fixtures
```

Pins: Kotlin 2.4.21 and Gradle 9.7.1, the same as 01 §8. The bytecode targets JVM 17, so the code can move
into an Android module unchanged.

---

## 1. Formats confirmed

### 1.1 Versions and sources

The versions come from `pubspec.lock`. Each package archive was downloaded from pub.dev on 2026-11-01. Its
SHA-256 equals the `sha256` that `pubspec.lock` records, so the sources read are the ones the 2.1.0+57 build
compiled.

| Package | Version | Archive SHA-256 (matches `pubspec.lock`) | Source |
|---|---|---|---|
| `shared_preferences` | 2.5.5 | `c3025c55…dd10bf` | <https://pub.dev/packages/shared_preferences/versions/2.5.5> (`lib/src/shared_preferences_legacy.dart`) |
| `shared_preferences_android` | 2.4.27 | `0634e64b…b5cc7` | <https://pub.dev/packages/shared_preferences_android/versions/2.4.27> (`android/src/main/kotlin/io/flutter/plugins/sharedpreferences/*.kt`, `lib/src/*.dart`) |
| `flutter_secure_storage` | 11.2.0 | `d4e1fb6b…95bb8` | <https://pub.dev/packages/flutter_secure_storage/versions/11.2.0> (`android/src/main/java/com/it_nomads/fluttersecurestorage/**`, `lib/options/android_options.dart`) |
| `image_picker_android` | 0.8.13+22 | `1c0c3879…1cf54` | <https://pub.dev/packages/image_picker_android/versions/0.8.13+22> (`ImagePickerCache.java`) |
| AOSP `frameworks/base` (master mirror) | — | — | [`SharedPreferencesImpl.java`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/app/SharedPreferencesImpl.java), [`XmlUtils.java`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/com/android/internal/util/XmlUtils.java), [`Xml.java`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/util/Xml.java), [`Base64.java`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/util/Base64.java) |

The archive URLs follow the pattern `https://pub.dev/api/archives/<package>-<version>.tar.gz`. Note that 05
cites `image_picker_android` 0.8.13+25, but the lock file pins **0.8.13+22**. The two versions have the same
cache keys.

### 1.2 `FlutterSharedPreferences.xml` (legacy API; this is the only API the app uses)

- **File:** `shared_prefs/FlutterSharedPreferences.xml` (`SHARED_PREFERENCES_NAME`, `SharedPreferencesPlugin.kt:33`).
- **Key prefix:** `static String _prefix = 'flutter.';` (`shared_preferences_legacy.dart:22`). The app never
  calls `setPrefix`.
- **Encodings:** `FlutterPrefsCodec` implements these.

| Dart type | Stored as | Source |
|---|---|---|
| `bool` | `<boolean>` | `LegacySharedPreferencesPlugin.setBool` |
| `int` | `<long>` (`putLong`) | `setInt(key, Long)` |
| `double` | `<string>` = `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"` + Kotlin `Double.toString()` | `setDouble`, `DOUBLE_PREFIX` (`SharedPreferencesPlugin.kt:38`) |
| `String` | `<string>`. Refused if it starts with the list, double or BigInteger prefix. | `setString` |
| `List<String>` | `<string>` = `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!"` + Dart `jsonEncode(list)` | Dart `setValue('StringList')` → `setEncodedStringList` (`lib/src/shared_preferences_android.dart`); `JSON_LIST_PREFIX` (`strings.dart:12`) |
| older list | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"` + `Base64.DEFAULT(ObjectOutputStream(list))`. The allowlist is `ArrayList`, `Arrays$ArrayList`, `String` and `String[]`. | `ListEncoder`, `StringListObjectInputStream.kt` |
| BigInteger (legacy plugin only) | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"` + base-36 text | `LegacySharedPreferencesPlugin.BIG_INTEGER_PREFIX` |
| `Set<String>` (pre-2020 writers) | `<set>` | `transformPref`. Note: **the plugin rewrites it into the Java-list form with `apply()` while it reads it.** |

- **Decode order:** `transformPref` checks the list prefix first (the JSON form, then the Java form), then
  BigInteger, then double. The codec keeps that order.
- **What the Dart side sees** (`getAllWithParameters`):
  - A `<null>` value is skipped (`value != null`).
  - A JSON list goes through `jsonDecode(..).cast<String>().toList()`. A non-string element throws, and that
    breaks the whole `getInstance()`.
  - A Java `Integer` arrives as a Dart `int`, and `Float` and `Double` arrive as a Dart `double`.
- **Android load rules** (`SharedPreferencesImpl.loadFromDisk`):
  - If `<name>.xml.bak` exists, the `.xml` is deleted and the `.bak` is renamed over it. **The `.bak` file is
    authoritative.**
  - If `XmlUtils.readMapXml` throws, the exception is caught and an **empty map** is installed. The next
    commit then overwrites the file.
  - `readThisValueXml` and `readThisPrimitiveValueXml` reject the whole file on an unknown tag, a bad number,
    or a `boolean` that is not `true` or `false` (any case). A duplicate name keeps the last value.
  - Parsing stops at `</map>`.
- **Serializer:** SharedPreferences writes text XML (`Xml.newFastSerializer()`), not ABX
  (`Xml.newBinarySerializer`). Control characters are written as `&#1;`…`&#31;`. A strict XML 1.0 parser
  such as JAXP rejects those references, so the library has its own small, tolerant parser.
  - **[R]:** The escape table comes from `FastXmlSerializer`. That file returned 404 at the mirror path, so
    the table was not re-read today. The reader accepts every numeric reference either way.
- **`Base64.DEFAULT`** (AOSP `Base64.java`):
  - Encoding writes lines of 76 characters (`LINE_GROUPS = 19`), each ending in `\n`, **including the last
    one**. In the XML, that newline is stored as `&#10;`.
  - Decoding **skips every byte that is not in the alphabet**: whitespace, and also junk such as `*` or `@`.
    Padding is optional. A misplaced `=`, or a lone trailing sextet, is an error.
  - The codec ports this state machine exactly, so a value decodes here exactly when it decodes in the plugin.

### 1.3 `SharedPreferencesAsync` stores (detect and count only)

The app does not use them: `grep -rn 'SharedPreferencesAsync\|SharedPreferencesWithCache' lib` returns
nothing. If one of them ever exists, `AsyncBackends` reads it:

- **DataStore:** `files/datastore/FlutterSharedPreferences.preferences_pb`, created by
  `preferencesDataStore("FlutterSharedPreferences")` (`SharedPreferencesPlugin.kt:40`).
  - The format is the protobuf `PreferenceMap{map<string,Value> preferences=1}`. `Value` is a oneof of
    boolean 1, float 2, integer 3, long 4, string 5, string_set 6, double 7 and bytes 8.
  - Keys have no prefix, and doubles are real doubles.
  - **[R]:** The field numbers come from the androidx `preferences.proto` and were not re-read from source
    today. The reader and writer are tested against each other only.
- **SharedPreferences backend:** `shared_prefs/<package>_preferences.xml`, from
  `PreferenceManager.getDefaultSharedPreferences`. It also has no prefix.

### 1.4 `flutter_secure_storage` 11.2.0, with the options this app uses

`const FlutterSecureStorage()` appears at 8 call sites with no `AndroidOptions`. The Dart defaults
(`android_options.dart`) are therefore `resetOnError: true`, `RSA_ECB_OAEPwithSHA_256andMGF1Padding`,
`AES_GCM_NoPadding`, no namespace and no key prefix.

| Item | Value | Source |
|---|---|---|
| Data file | `shared_prefs/FlutterSecureStorage.xml` | `FlutterSecureStorageConfig.DEFAULT_PREF_NAME`, `getEffectiveDataPrefsName()` |
| Entry name | `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg` + `_` + name. The prefix is Base64 of "This is the prefix for a secure storage\n". | `DEFAULT_KEY_PREFIX`, `addPrefixToKey` |
| Entry value | `Base64.DEFAULT( IV[12] ‖ AES-GCM ciphertext ‖ tag[16] )`, UTF-8 plaintext, **no AAD** | `StorageCipherImplementationGCM.encrypt`, `writeUnsafe` (`Base64.encodeToString(result, 0)`) |
| AES key | 16 random bytes (AES-128) | `StorageCipherImplementationGCM.keySize = 16` |
| Wrapped key | `shared_prefs/FlutterSecureKeyStorage.xml`, entry `AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK`, `Base64.DEFAULT` of the RSA-OAEP wrap. It is written with **`apply()`**. | `SHARED_PREFERENCES_KEY`, `getEffectiveKeyStoragePrefsName()` |
| Keystore key | An RSA key pair in `AndroidKeyStore`, alias `<package>.FlutterSecureStoragePluginKeyOAEP`, i.e. `io.github.eslamasabry.opencode_mobile.FlutterSecureStoragePluginKeyOAEP` | `KeyCipherImplementationRSAOAEP` |
| Unwrap | `Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")`, `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)`, `unwrap(bytes, "AES", Cipher.SECRET_KEY)` | `getRSACipher`, `getAlgorithmParameterSpec` |
| Algorithm markers | `FlutterSecureSAlgorithmKey` and `FlutterSecureSAlgorithmStorage`. They are read from `FlutterSecureStorageConfiguration:FlutterSecureStorage.xml` first, then from `FlutterSecureStorageConfiguration.xml`, separately for each key. | `NamespacedConfigSource`, `StorageCipherFactory` |
| Plugin's own backup entries | Names ending in `_BACKUP`, from "migrate with backup". They are skipped. | `MigrationBackup`, `UpgradeInspector.BACKUP_SUFFIX` |

**Marker rules.** The v11 plugin handles the markers as follows, and `FlutterSecureStorageReader` matches it:

- **Missing** (either marker): `StorageCipherFactory` uses the default algorithms and the plugin **tries** to
  decrypt. The reader tries too, and the GCM tag makes a wrong guess fail safely.
- **v9 values** (`RSA_ECB_PKCS1Padding` / `AES_CBC_PKCS7Padding`): `isRemoved` is true, so v11 cannot read the
  data. Every name is flagged `RemovedCipher` without an attempt.
- **Keystore-AES key cipher**: flagged `KeystoreAesKeyCipher`. The app never used it.
- **Tink `EncryptedSharedPreferences` keysets**: counted only, because v11 cannot read that store.

**Other plugin facts that affect the native side:**

- `readAllUnsafe` matches names with `contains(prefix)` and strips them with `replaceFirst`.
  - It does **not** skip `_BACKUP` names, so the Flutter "Reset saved sign-ins" flow can see
    `pw.<id>_BACKUP`.
  - The reader uses `startsWith(prefix + "_")` and skips `_BACKUP`. For real data the result is the same.
- `KeyCipherImplementationRSAOAEP`'s constructor calls `createRSAKeysIfNeeded`. **Never instantiate the plugin
  classes from native code:** if the alias were missing, that call would silently create a new key, and the
  old data could then never be decrypted.

### 1.5 `image_picker_android` lost-data cache (audit finding 11)

The cache is `shared_prefs/flutter_image_picker_shared_preference.xml` (`ImagePickerCache.SHARED_PREFERENCES_NAME`).
It holds these keys:

- `flutter_image_picker_image_path`: a string set.
- `…_type`: `image` or `video`.
- `…_error_code` and `…_error_message`.
- `…_max_width` and `…_max_height`: `putLong(Double.doubleToRawLongBits)`.
- `…_image_quality`: `putInt`.
- `…_pending_image_uri`.

`ImagePickerCache.read` returns the lost result. S0 now backs this file up too (§3).

### 1.6 Corrections to the plan and the research notes

| Where | It said | Source says | Handled |
|---|---|---|---|
| `research/network-data.md` §8.3 | Missing markers → treat every entry as unreadable | v11 **attempts** decryption with the defaults when markers are missing (`StorageCipherFactory`) | The reader attempts, like the app does today |
| 05 §2.2 | BigInteger → `Str` | Through the standard message codec, the Dart side gets an **int** | `Int64` when it fits in 64 bits, otherwise `Undecodable(BadBigInteger)` |
| 05 §7.5 code | `Base64.decode` strict | Android's decoder skips any byte outside the alphabet and padding is optional | Exact port (`decodeAndroidBase64`) |
| 05 S0 step 2 | Five XML files | The picker cache can hold the only link to a photo (audit 11) | `BackupPublisher.DEFAULT_FILES` adds `flutter_image_picker_shared_preference` |
| 05 §1 sources | `image_picker_android` 0.8.13+25 | `pubspec.lock`: 0.8.13+22 | Read +22 |
| Ledger | `oc.widgetUsage` (exact), `oc.factsbook`, `oc.providerRuntimeRefresh.` | Stored as `oc.widgetUsage.<id>`, `oc.factsbook-node.stopped`, `oc.providerRuntimeRefresh.v1.<id>.<enc>` | Aliases are listed in `FlutterKeyRegistryTest` |
| Fixture path | Real names include a colon (`FlutterSecureStorageConfiguration:FlutterSecureStorage.xml`) | — | Committed as-is. A Windows checkout cannot create that file. |

---

## 2. What the library contains

| Package | Type | Purpose |
|---|---|---|
| `xml` | `AndroidPrefsXml`, `SharedPrefsFile`, `AndroidPrefsXmlWriter`, `RawPref` | Parses the bytes the way Android would, including the `.bak` rule and `AndroidLoad.Loaded` vs `LoadsEmpty`. It also keeps a lenient salvage of what it could read, which M1 compares against the live map. The writer uses the `FastXmlSerializer` layout. |
| `prefs` | `FlutterPrefsCodec`, `AsyncBackends`, `ImagePickerCache` | The encodings in §1.2 to §1.5. `Undecodable` never carries a value. |
| `secure` | `FlutterSecureStorageReader`, `FssKeyUnwrapper`, `SoftwareRsaOaepUnwrapper`, `FssFixtureWriter`, `SecretText` | Per-name results that never throw, never write and never delete. The Keystore step sits behind an interface. `SecretText.toString()` is `***`. |
| `model` | `FlutterKey` (188 shapes), `SecretKey` (14), `KeyClassifier`, `FlutterPrefsModelBuilder`, `ProfileSweep` | The typed mapping in §2.1. |
| `migration` | `JournalStore`/`MigrationJournal`, `StepRunner`, `RollbackGuard`, `BackupPublisher`, `RestoreTransaction`, `OutboundTombstones`, `CredentialReconciler`, `PrefsBaseline`, `Fs`, `FaultPoints` | The crash-safety helpers in §3. |
| `fixtures` | `FixtureGenerator` (+ `generateFixtures` task) | Synthetic app data dirs. Every value is made up, and the RSA key is a throwaway test key. |
| `json` | `MiniJson` | A JSON parser, plus `jsonEncode` with Dart's escaping and double formatting. Used for list values and the journal. |

### 2.1 The typed key mapping

- **Key shapes.** `FlutterKey` has one enum entry per key shape in `map/state-data.md` §4 and 05 Appendix
  A.1–A.4. Each entry has a pattern, an `IdForm`, an expected type, a scope, an id kind, an owner module and
  05 §7.8 handling codes.
  - `IdForm` is one of `Exact`, `Suffix` (with the `<phoneId>.agents` pseudo-profile), `IdThenMore`,
    `LastSegment` or `Opaque`.
  - Handling codes include `A`, `AK`, `L1`–`L3`, `R`, `I`, `D`, `X`, `DbAtV2` and `New`.
- **Ledger check.** `FlutterKeyRegistryTest` reads the Flutter gate's own
  `test/fixtures/coverage/settings_preferences_ledger.json` (188 entries) and checks two things: every entry
  has a native shape, and the scopes agree. A key added on the Flutter side fails this build until it gets a
  row.
- **Classification.** `KeyClassifier.classify(logicalKey)` returns the shape, the profile (or owner or team)
  id, and the qualifier. A key with no shape gets `UnknownReason`: `NoShape`, `BadIdShape` or
  `SecretShapeInPrefs`.
- **The model.** `FlutterPrefsModelBuilder.build(map)` groups the keys into `app`, `byId[profileId]`,
  `unknown` and `unprefixed` (physical keys without `flutter.`).
  - **Nothing is dropped:** `toPhysicalMap()` returns the input map exactly, with the same keys, order and
    raw values. A test checks this.
  - Type mismatches and undecodable values are flagged, not dropped.
  - `oc.profiles` is read leniently, and ids that hold data but have no profile are listed.
- **Sweep.** `ProfileSweep.scopedKeys` is the 05 §3.4 rule, verbatim. A test checks that it takes every key
  the model attributes to a profile and none of another profile's keys.

---

## 3. The audit's migration findings and how this library handles them

The audit's verdict was BLOCKED. Its findings 2, 4, 5, 6, 7 and 11 concern the migration, and the helpers
here implement the audit's concrete fixes for them. Findings 8, 9, 12 and 14 also touch migration but sit
outside a reader library; they are recorded as requirements below. Where the audit and 05 disagree, the audit
wins (DRAFT-STATUS), and the table says so.

| Finding | Fix in this library | Tests | Still needed in `:android:migration` |
|---|---|---|---|
| **2 BLOCKER**: R2 can repeat sent prompts and fired schedules | `OutboundTombstones` stores dispatch ids and occurrence keys in `files/native/outbound-tombstones.json`, **outside** every snapshot. `partition()` splits restored outbound work into "already done, never resend" and "hold for review". After R2, the journal holds `outboundHold = true`. | `RestoreTransactionTest`: restore puts back q1 and q2, q2 is tombstoned; tombstones survive two restores | The queue, `ScheduledPromptClaims` and the schedule engine record a tombstone **before** they report success. Flushes and schedule re-arming honour `outboundHold` until the person reviews. Journey: "send → restore → reconnect/reboot". |
| **4 MAJOR**: rollback guard reads the wrong flag, too late | One barrier, `oc.native.flutterReadable`, kept in both the journal and the prefs file. `StepRunner.initBarrier()` writes `true` after M1, journal first. A breaking step **lowers it and reads it back before its first write**. After a crash, `initBarrier` repairs the pref from the journal. `RollbackGuard.evaluate`: `true` → allowed; `false` → blocked; missing **and** `files/native/migration.json` exists → blocked as "Uncertain". | `StepRunnerTest`: barrier durable before apply; crash between journal and pref repaired; refused write blocks before any write; a crash at every point of a run never leaves rollback allowed while a breaking step may be half done | The R1 Dart guard implements the same table, including the journal-file check. 05 §9.3 step 2 must be amended: as written there, a missing flag reads as allowed. |
| **5 MAJOR**: backup and restore are not interruption-safe | `BackupPublisher` (S0): copies into a partial dir and publishes it with an atomic rename. A published dir is **verified against its manifest and adopted**, never copied onto. A published dir that fails verification blocks (`BackupInvalid`). `.bak` twins are copied and marked `authoritative`. `RestoreTransaction` (R2): journaled phases `staged → movingAside → swapping → resetting`. Live `.xml` **and `.xml.bak`** files are moved aside. `recoverBeforeOpen()` finishes an interrupted restore before any store opens. `FaultPoints` sits between every filesystem operation. | `BackupPublisherTest` and `RestoreTransactionTest`: a crash at **every** fault point, then a restart, ends in the reference state; a third run changes nothing | Call `recoverBeforeOpen()` in `Application.onCreate` before any `getSharedPreferences`. "Start fresh" uses the same phase pattern (not written yet). |
| **6 MAJOR**: R2 after Room becomes authoritative | `StorageGeneration` is `prefs-v1` or `room-v2`. `RestoreTransaction.eligibility()` refuses R2 outside `prefs-v1`. | `RestoreTransactionTest`: refused with `GenerationNotRestorable` | The Room import switches the generation in the transaction that makes Room authoritative. |
| **7 MAJOR**: R1 loses credential changes and deletions | `CredentialReconciler` has three parts. Native revisions and tombstones give `staleLegacyEntries`, the FSS entries the R1 guard deletes **before** Flutter bootstraps. A per-transition baseline of **ciphertext** fingerprints (never secret hashes) drives `onReturn`: overwrite, delete or keep. Each return compares against **its own** transition's baseline. | `CredentialAndBaselineTest`: native edit, native sign-out, Flutter deletion, two rollback cycles | `:core:data` keeps the revision and tombstone record. The R1 guard deletes the stale names. J4 gets these cases. |
| **11 MAJOR**: the image-picker recovery source is discarded | `ImagePickerCache.read` returns the lost result. S0 backs up the cache file. | `AsyncBackendsAndPickerTest`, `BackupPublisherTest` | M3 (or `:engine:composer`) imports the result into `oc.pendingPromptPhoto` and checks its conversation scope before the cache is used up. Upgrade fixture: kill the app after the camera result, before Dart receives it. |
| **8 MAJOR**: CAS vs commit | Not a reader concern. Recorded for `FlutterPrefsStore`: every mutation (commit, CAS, migration write, compensation) holds one file-wide lock through read, persist and compensate. | — | W2 |
| **9, 12, 14 MAJOR** | ANR budgets, Room retention, emulator serial binding | — | 14 is part of the emulator proof (§6). 9 and 12 belong to the runner and the Room slice. |

**Journal → commit marker → verify.** `StepRunner` runs each step in this order:

1. Write `attempts+1` and `running` to the journal, atomically, before any write of the step.
2. `apply()`. It must be idempotent.
3. Write `committed`.
4. `verify()`. It must be read-only.
5. Write `verified`.

On a rerun, a `verified` step is re-verified and skipped, and a `committed` step is only verified. Three
attempts give `StepCrashed`, so the app blocks instead of looping. "Try again" resets only the failed steps.
The journal holds states and counts only, and keeps unknown fields so a newer build's fields survive.

**Verify.** `PrefsBaseline` implements M1's baseline and M5's "nothing else changed" check.

- **Stored form.** For each key it stores `sha256(physical key) → {shape, sha256(typed raw value)}`. Key names
  are hashed because some carry directory paths, and nothing else is kept.
- **Write sets.** They are declared by shape, the way 05 declares them (`V1_DECLARED_WRITES` = L1–L3, M2
  re-entry flags and the barrier).
- **What it catches.** Tests change the queue, change a value's type, and lose an unknown key. Each is
  reported as a change.

---

## 4. The Android Keystore step: what `:android:migration` must do

The JVM tests replace exactly one call: `FssKeyUnwrapper.unwrap(wrapped)`. They use
`SoftwareRsaOaepUnwrapper`, which has the same transformation and parameters and a software RSA key. On
Android, `:android:migration` must:

1. **Run off the main thread, after S0, and read the raw files.** Read from the S0 backup copy, or from
   `getSharedPreferences(name).all` once `recoverBeforeOpen` and S0 are done. Build `FssStores` for
   `FlutterSecureStorage`, `FlutterSecureKeyStorage` and both config files, from the same source.
2. **Implement the unwrapper without touching the plugin:**

   ```kotlin
   internal class AndroidKeystoreUnwrapper(private val packageName: String) : FssKeyUnwrapper {
       override fun unwrap(wrappedKey: ByteArray): SecretKey {
           val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
           val alias = FssFormat.keystoreAlias(packageName)        // "<package>.FlutterSecureStoragePluginKeyOAEP"
           val priv = ks.getKey(alias, null) as? PrivateKey ?: throw KeystoreAliasMissingException()
           // never generate a key here: a new key can never decrypt the old data
           return Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround").run {
               init(Cipher.UNWRAP_MODE, priv, fssOaepSpec())   // SHA-256 digest, MGF1 with SHA-1
               unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) as SecretKey
           }
       }
   }
   ```

   - The plugin generated the key with `KeyGenParameterSpec(PURPOSE_DECRYPT|PURPOSE_ENCRYPT, digests
     SHA-256, ECB, padding RSA_OAEP)`, a 25-year certificate, no user authentication and no StrongBox. With
     no user authentication, a lock-screen change does not invalidate it.
   - Keep the transformation, the `AndroidKeyStoreBCWorkaround` provider and the OAEP parameters identical to
     the plugin's. The plugin is the only proven way to unwrap this key, and changing any of them is
     untested.
   - The unwrapped key is a software `SecretKeySpec`, 16 bytes. AES-GCM decryption then runs in the normal
     provider: `FlutterSecureStorageReader` does that part, and it is shared code.
3. **Rely on the same app UID.** Keystore entries belong to the app UID. The same package plus the same
   signing certificate gives the same UID, so the alias stays reachable after `adb install -r`. A different
   signer is refused by the package manager before any of this runs.
4. **Map failures to `SecretFailure` names only:**
   - `getKey` returns null → `KeystoreAliasMissing`. Seen on a restore to a new device, or after a Keystore
     wipe.
   - Any other exception → `UnwrapFailed`.
   - Log no exception text, because a failing provider can echo its input.
5. **Handle each decrypted secret in this order:**
   1. Register it with the `Redactor` (`SecretText.reveal()` is called once, there). For a JSON value,
      register its string fields too.
   2. Compare it with `SecretStore`: equal → `alreadyMigrated`; different → `nativeNewer`.
   3. Otherwise write it, then read it back.
6. **Never:**
   - write to any FSS file;
   - create or delete the FSS alias;
   - instantiate `com.it_nomads.fluttersecurestorage.*`;
   - apply `resetOnError`.
7. **Instrumented test (`SyntheticFlutterSecretsTest`):**
   1. Generate an RSA key under `<testPackage>.FlutterSecureStoragePluginKeyOAEP` with the plugin's exact
      `KeyGenParameterSpec`.
   2. Build `FssFixtureWriter(keystoreCert.publicKey, SecureRandom())` and write `FixtureGenerator.SECRETS` into
      real `shared_prefs/` files.
   3. Read them with `FssStores.fromDirectory` and `AndroidKeystoreUnwrapper`, and assert that the plaintexts
      match.
   4. Delete the alias.

   The writer and the fixture secrets are in `src/main` for this reason.

**Measure on the emulator:** the unwrap and decryption time for 20 secrets. 05 estimates under 500 ms for M2.

---

## 5. Test results

Run on 2026-11-01 (JDK 21, Gradle 9.7.1, Kotlin 2.4.21) with `./gradlew test`. Result: **84 tests, 0 failed,
0 skipped.** The compiler runs with `allWarningsAsErrors` and `explicitApi()`.

| Class | Tests | What it proves |
|---|---|---|
| `AndroidPrefsXmlTest` | 12 | Every element type, in file order. The `&#N;` control-character escapes round-trip. **Truncation at every byte offset** loads empty and never throws. Garbage, wrong roots, a bad primitive, an unknown tag or an unknown entity loads empty, as Android does. Duplicates keep the last value. A nameless value is skipped and trailing content ignored. ABX, malformed UTF-8 and BOM handling. `.bak` is authoritative. `toString` never prints values. |
| `FlutterPrefsCodecTest` | 12 | The prefix constants equal the Base64 sentences. Native types map to Dart types. JSON lists encode with Dart escaping. Broken JSON lists are undecodable. Legacy Java lists decode (`ArrayList` and `Arrays$ArrayList`). Classes outside the allowlist (`LinkedList`, `Date`, `ArrayList<Integer>`) are refused. Doubles cover NaN, ±0 and exponents. BigInteger decodes, and overflow is undecodable. Writing a string that clashes with a prefix is refused. Android Base64 encode layout and decode semantics. |
| `AsyncBackendsAndPickerTest` | 4 | DataStore round trip for every type. A truncated or garbage `preferences_pb` is reported as corrupt and never crashes. Census of both async stores. The image-picker lost result. |
| `FlutterSecureStorageReaderTest` | 14 | Decrypts every fixture secret, including Arabic text, an emoji and an empty value. Reads real XML files with `&#10;` in the Base64. Legacy-file, missing, removed, Keystore-AES and unknown markers. `_BACKUP` entries, foreign keys and Tink keysets. No wrapped key, a bad wrapped key, a missing alias, the wrong RSA key and the wrong key length. Per-entry failures (`AuthFailed`, `TooShort`, `BadBase64`, `NotAString`, `NotUtf8`) leave the other entries readable. Ciphertext can be swapped between names (no AAD), which is why `SecretStore` adds AAD. No leaks through `toString`. |
| `FlutterKeyRegistryTest` | 10 | Ledger cross-check (all 188 entries) and scope agreement. Classification of every id form. Unknown-key reasons. Secret-name classification. The model is lossless, covers every registry shape and flags type mismatches. `oc.profiles` edge cases. The sweep agrees with the model. |
| `MiniJsonTest` | 5 | Order and number kinds match `jsonDecode`. Errors never echo input. Dart `jsonEncode` escaping (lone surrogates as lowercase `\udXXX`) and Dart `double.toString` forms. |
| `BackupPublisherTest` | 4 | A verified byte copy with a manifest, with the sources only read. **A crash at each of its 16 fault points**, then a restart, gives the same published backup, and a third run changes nothing. A backup that has been tampered with blocks and is never overwritten. Fresh install. Low space writes nothing, and "Try again" works afterwards. |
| `StepRunnerTest` | 10 | Steps run once, and a rerun only re-verifies. **A crash at every point converges** to the same end state. The barrier is durable before a breaking step writes. A journal/pref split is repaired. A refused barrier write blocks with zero writes. Three crashes give `StepCrashed`, and "Try again" works. Verify regressions block. The guard table. Rollback is never allowed while a breaking step may be half done. Unknown journal fields survive. |
| `RestoreTransactionTest` | 4 | Restore puts the pre-update bytes back, moves the native secret store and the stale `.bak` aside, leaves Kotlin-owned files untouched, sets the outbound hold and keeps the tombstones. **A crash at every fault point** is finished by `recoverBeforeOpen`. R2 is refused after Room, without a backup, or with a damaged backup. Two restore cycles. |
| `CredentialAndBaselineTest` | 5 | Stale legacy entries. Reconciliation of changes, additions and deletions. Two rollback cycles. The baseline accepts only declared writes and catches the queue, a type change and a lost unknown key. The baseline file holds no plaintext. |
| `CommittedFixturesTest` | 4 | The committed prefs XML is byte-equal to the generator's output (drift guard). Every committed store reads back, including FSS with the committed test key. **The whole S0 → M1 baseline → barrier → M2 → M5 pipeline on the committed files, crashed at each of its fault points,** reruns to the same secrets and prefs, and a further rerun changes **no byte**. A half-truncated live prefs file is caught by M1 before any write, while S0 has already made the backup. |

**Fixtures.**

- **Files.** `src/test/resources/fixtures/synthetic-v1/` holds `shared_prefs/` (prefs, three FSS files and the
  picker cache), `files/datastore/…preferences_pb`, `seed-manifest.json` and a throwaway PKCS#8 RSA test key.
- **Coverage.**
  - The prefs fixture has a value for every `FlutterKey` shape, for two profiles plus an orphan id.
  - It includes `.agents` pseudo-profile keys, legacy Java lists (both list classes), a `<set>`, an int, a
    float, a `<null>`, BigInteger and double prefixes, unknown keys, a bad id and an unprefixed key.
  - It includes a draft with markup, control characters, Arabic and an emoji.
- **Generator.** `FixtureGenerator` in `src/main` builds all of them, and `./gradlew generateFixtures`
  regenerates them.

**Build note (this container only).** Maven Central returned HTTP 429 through the session proxy. The tests
were run with a local Gradle init script that puts Google's Maven Central mirror
(`maven-central.storage-download.googleapis.com`) first. That script is not committed: the build uses plain
`mavenCentral()`.

---

## 6. Open risks for the emulator proof (install over the real Flutter 2.1.0+57)

1. **Keystore alias across the update.** The plan relies on standard UID behaviour but has not proven it. The
   proof: install the Flutter release, add a server with a password, `adb install -r` a native build with the
   same signer and versionCode ≥ 10000, then run the reader with `AndroidKeystoreUnwrapper`. Expect
   `KeystoreAliasMissing` = 0.
2. **Which signer.** The 2.1.0+57 public sideload release is signed by `842284B2…1D1C`, and the owner's phone
   build by the local key `1DE5BF08…D60C` (AGENTS.md). The emulator proof must use one lineage end to end, and
   the native APK must have the matching certificate, or Android refuses the update. Record which lineage was
   used. Never substitute a signer.
3. **The real file layout differs from the synthetic fixture.** Points to check on the real files:
   - **Escapes.** The control-character escapes are only [R] (§1.2): check them on the real XML.
   - **Wrapped-key file missing.** It can be missing if the process died right after the first secure write
     (the wrapped key is written with `apply()`). That would show as `NoWrappedKey`.
   - **`.bak` twins.** Check whether any exist.
   - **Markers.** Check that they are in the namespaced config file, not only the legacy one.
   - **Encoding counts.** Record the M1-style census (`PrefsCensus`, `FssCensus`): stored kinds, Java lists
     (expected 0), unknown keys and undecodable values.
4. **Tolerant parser vs KXmlParser.** The parser here accepts what `FastXmlSerializer` writes. If Android's
   KXmlParser rejects something this parser accepts (for example `&#0;`), M1 would see "we parse it, Android
   loads it empty" and block instead of losing data. That is safe, but it would be a false block. Run M1 on
   the real files and on a fixture with every control character, and compare with
   `getSharedPreferences(..).all`.
5. **Something touches prefs before S0.** WorkManager's initializer, a ContentProvider, or a receiver that
   reads prefs before S0 would load (and with `.bak` present, *rename*) the files before the backup. Prove it
   with a `.bak` planted in the fixture: S0 must copy both files and mark the `.bak` authoritative.
6. **`resetOnError: true` in the Flutter app.** A decrypt failure on the Flutter side already deleted all
   secrets. A real install may therefore have fewer secrets than servers. The census must not call that a
   migration failure: it shows up as servers with no `pw.<id>`.
7. **Plugin side effect on `<set>` values.** Reading a `<set>` through the plugin rewrites it. Native code
   must never call the plugin. A Flutter rollback build (R1) will, and it is harmless there.
8. **Size and time.** 05 budgets S0 at under 1 s for 100 MB and M2 at under 500 ms. The library streams and
   hashes, but the prefs file can hold a 60 MiB queue. Measure S0, an M1 parse of the largest real file and
   the M2 decrypt time on the emulator.
9. **Emulator command targeting (audit 14).** Bind every `adb` and Maestro command to one verified emulator
   serial (`ANDROID_SERIAL`, check `ro.kernel.qemu` and the AVD name before any mutation), use `set -euo
   pipefail`, and stop when `adb root` fails. Never run the recipe with the owner's phone attached.
10. **Shorebird.** If the emulator runs the 2.1.0+57 release with a Shorebird patch, write down which patch.
    Patches change Dart code, not the storage formats read here, but the runtime state should match the
    phone's.
11. **The colon in a file name.** `FlutterSecureStorageConfiguration:FlutterSecureStorage.xml` cannot be
    checked out on Windows. That is fine on Linux and macOS.

## 7. Next actions

1. Move `xml`, `prefs`, `secure`, `model` and `migration` into `:core:data` / `:android:migration` once the
   skeleton lands, keeping the tests. Add `AndroidKeystoreUnwrapper` and `SyntheticFlutterSecretsTest` (§4).
2. Run the emulator proof (§6) and record the real-install census next to this file.
3. Amend 05 §7.5, §9.3 and §9.4 with the corrections in §1.6 and §3.
