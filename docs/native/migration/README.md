# Flutter store ledger

Every piece of data the released Flutter app (2.1.4+62, `origin/master` at `2dec68a25`) keeps on an Android phone, and
how the native app reads it. Story E00-S01.

| File | What |
|---|---|
| [`store-ledger.json`](store-ledger.json) | one row per store: preference keys and key families, other SharedPreferences files and their keys, secure-storage names, files and folders, WorkManager names, alarms, notification channels, widgets, tiles, shortcuts, manifest components, intent extras the OS holds, permissions, Keystore aliases |
| [`flutter-2.1.4-names.json`](flutter-2.1.4-names.json) | what `FlutterStoreScanner` finds in the 2.1.4 tree (`lib/`, `android/app/src/main/`), with the first file that writes each name |

## A row

| Field | Meaning |
|---|---|
| `kind`, `name` | the store and the literal the Flutter code uses (a family ends in `.`); `aliases` are other spellings the scanner sees |
| `shape`, `store`, `physicalKey` | the full key with its id placeholder, and where it lives on the phone |
| `flutterWriter` | the first Flutter file (2.1.4 tree) that writes it |
| `nativeReader` | the native file that reads it (`READ`), or `none yet` / `kept untouched` / `deferred` / `dropped` / `not stored` |
| `status` | `READ`, `MISSING`, `KEPT`, `DEFERRED`, `DROPPED`, `NOT_STORED`, `NEW` (definitions in the file) |
| `decision` | the plan decision behind the handling: plan 05 §7.8 codes, Appendix B/C/D, or 01 D6/D7 and the §9.9 component freeze |
| `fallback` | what the person sees, in plain words, when the value can't be read |
| `audit` | set on rows the 2026-11-06 store inventory found missing; they stay `MISSING` until a native reader lands |

`nativeReader` is the first native main-source file that holds the key literal; files in `:android:migration`,
`:engine:migration` and `:feature:gate` count only for keys the migration itself transforms (L1/L2/L3), the secure
storage files, and the image-picker cache. A key that native names but does not adopt (the `pausedByUser` tile flag
lives under a new native key) is `MISSING` with a note.

## Gaps this ledger records (`MISSING`, 21 rows)

- Audit 2026-11-06: `oc.chatTail.`, `oc.lastProject.`, `oc.chatNoticesSeen.`, `oc.withdrawnPrompts.`,
  `oc.providerRuntimeRefresh.v1.`, `oc.providerRuntimeUnloadable.`, `oc.scheduleReceipts.`,
  `local_agents_offer_skipped` (now READ by `:feature:onboarding` `LocalAgentsOffer` and in Appendix A.3, E00-S06;
  still missing from `FlutterKey`),
  `oc_background_native` / `pausedByUser`, `camera_permissions` / `camera_requested`, `voice_permissions` /
  `microphone_requested`, `files/termux-setup-v2.json`, `TermuxResultService` (and its `oc.executionId` and
  `oc.setupToken` extras).
- Found while building the ledger: `oc.recentLocations.` (no native reader); `files/oc-new-project-staging/`;
  the scheduled-prompt alarm (Flutter arms `oc-schedule://…`, native uses `oc-schedule-open`, so it can't match or
  cancel a Flutter alarm); `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared by no native
  manifest although `:android:background` sends the request intent, so Android drops the grant on update.

## Checks

`FlutterKeyRegistryTest` in `native/tools/flutter-migration-readers` (standalone Gradle build):

```bash
cd native/tools/flutter-migration-readers
./gradlew test                                             # snapshot + ledger checks
git worktree add ../../../../flutter-2.1.4 origin/master   # optional: rescan the real tree
./gradlew test -PflutterTree=../../../../flutter-2.1.4     # also fails on a name the tree writes with no row
./gradlew scanFlutterTree -PflutterTree=../../../../flutter-2.1.4 -PflutterCommit=$(git rev-parse origin/master)
```

- `B-MIGR-005 every name the released 2_1_4 tree writes has a store ledger row` — snapshot vs ledger.
- `B-MIGR-005 rescanning a Flutter checkout finds the snapshot and nothing unmapped` — runs with `-PflutterTree`.
- `B-MIGR-005 a key added to the Flutter tree without a ledger row fails the check` — the red case, on a temp tree.
- `B-MIGR-005 each ledger row names its writer, its native reader or decision, and the fallback words` — and every
  `READ` reader file exists.
- `B-MIGR-007 every registry shape and secret name has a store ledger row` — `FlutterKey` / `SecretKey` agree with it.
- `B-MIGR-005 the 2026-11-06 audit gaps are MISSING rows`.

When a native reader lands, change the row to `READ` with its path; the audit test then needs the gap removed from
its list in the same commit.

## Scanner limits

The scanner is textual: it over-matches (metadata fields, log tags, in-memory keys get `NOT_STORED` rows with the
reason) rather than miss a key. Names built only from non-literal parts at run time, the keys inside Kotlin-owned
prefs files, Dart files written next to an unscanned directory variable, alarms and the OS-held stores come from plan 05
Appendices C and D and were added by hand.
