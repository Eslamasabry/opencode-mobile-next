# Flutter 2.1.4 fixture: seed, dump, check (E00-S02)

Migration is tested against data shaped like a real 2.1.4+62 install, not the old 4501 build
(`docs/native/qa/migration-proof-2026-11-03` only covered 4501 → 10003).

**State: not seeded yet.** `native/e2e/fixtures/flutter-2.1.4/seed-manifest.json` is the template; the dump files and
the AVD snapshot don't exist until the steps below run on the PC.

| File | What |
|---|---|
| `seed_2_1_4.sh` | the device recipe (plan 05 §10.4 for 2.1.4): proves it talks to the e2e AVD on `emulator-5584`, checks the APK's versionCode (≥ 4612) and signer (local release key), seeds each step in the manifest, records permissions, app-ops, shortcuts and channels, dumps prefs and the file tree, saves snapshot `flutter-2.1.4-seeded` |
| `xml_to_prefs_json.py` | `shared_prefs.tar` → `prefs.json` for commonTest readers |
| `check_dump.py` | refuses a dump that is incomplete, unreviewed, from the wrong build, signer or AVD, misses a seeded key, or holds anything shaped like a real credential; every seeded name must have a row in `docs/native/migration/store-ledger.json` |

## Order

1. Maintainer go-ahead to build and sign: `origin/master` (2.1.4+62) with the release Flutter, universal,
   `--build-number` ≥ 4612, the owner's local key. Record commit, Flutter version and versionCode.
2. Write the Maestro seed flows `e2e/seed/flutter-2.1.4/<seed id>.yaml` against the 2.1.4 UI (one per manifest seed).
3. `E2E_AVD=… APK=… VC=… COMMIT=… tools/flutter-fixtures/seed_2_1_4.sh` from `native/`.
4. Fill in the manifest, read every dump file, then `python3 -I tools/flutter-fixtures/check_dump.py e2e/fixtures/flutter-2.1.4`.

Tests (no device): `python3 -I -m unittest discover -s native/tools/flutter-fixtures/tests`.
