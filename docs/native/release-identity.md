# Native release identity

One page that says which key signs which build, which version codes are used, and which processor types ship. Read it
before building anything that lands on a phone. Story E01-S00 of the native plan (backlog `agile`, epic E01).

## Packages

| Build | Package | Installs over the Flutter app? |
|---|---|---|
| `switch` flavor (the real replacement) | `io.github.eslamasabry.opencode_mobile` | Yes, as an update, when signed with the same key and given a higher version code |
| `preview` flavor (side by side, for walks) | `io.github.eslamasabry.opencode_mobile.native` | No, it installs next to the Flutter app with its own data |

## Signing lineages

Android only updates an app in place when the new APK has the same signing certificate. Losing a key means the phones
that carry it can never update again without an uninstall, which deletes their projects.

| Lineage | Short fingerprint | Signs | Where the key lives |
|---|---|---|---|
| Owner local | `1DE5BF08…D60C` | The owner's phone and every test build installed on it, Flutter and native | The maintainer's build machine only, with encrypted backups kept privately (2026-11-06) |
| Public CI | `842284B2…1D1C` | Every public GitHub release since 2.0.0+56, including 2.1.4+62; the public native 3.x line | A CI secret, used only by the release workflow |
| Legacy CI | `2D010C21…D0EC` | Old stable releases before 2.0.0 only | CI history; not used for any current or future build |

Naming: `AGENTS.md` calls `2D010C21…` "older stable CI" and plan 15 §11.2 calls it "CI test". They are the same
certificate. This page settles the name as **Legacy CI**: nothing new is signed with it, and native never uses it.

Rules:
- Never rotate or substitute a signer.
- Before installing on the owner's phone, read its installed signer with read-only adb (`pm path`, pull `base.apk`,
  `apksigner verify --print-certs`) and compare with the APK's.
- Emulator and lab builds (`qa` build type) use the build machine's debug key and are never delivered.

## Version codes

- The first native build delivered to any phone uses **10010** or higher, and the code only ever goes up.
  `native/version.properties` holds the current value: 10010, `3.0.0`, taken for the first switch release (E01-S01).
- Release steps, gates and fix-forward: [release.md](release.md); the owner's switch day: [switch-day.md](switch-day.md).
- Each fix-forward release adds **+10**, leaving room for rebuilds of the same version.
- Native must always be above every Flutter version code a user can have: Flutter is at 62 (2.1.4+62) and owner test
  builds reach the 4000s (4610 on 2026-11-06), so 10010+ is safe for both lineages.
- `versionName` for the switch is 3.0.0.

## Processor types (ABIs)

| APK | Contents |
|---|---|
| `arm64-v8a` | Almost every current phone |
| `x86_64` | Emulators and the few x86 devices |
| universal | Both, for users who don't know which to pick |

32-bit `armeabi-v7a` is decision D-10 (pending): the plan recommends 64-bit only, with the bridge release telling
32-bit users that version 3 needs a 64-bit phone while 2.x keeps working.

## Decisions

| Id | Question | Owner answer (2026-11-06) |
|---|---|---|
| D-01 | Which lineage does the owner's phone carry, and which does public 3.0 use? | Owner phone stays on Owner local; public on Public CI; never rotate; the local key is backed up |
| D-02 | How does native ship and get fixes without Shorebird? | GitHub releases plus the in-app updater, fixing forward with a new version code (+10). Flutter keeps its Shorebird build tooling only until the switch, with no patches |
| D-10 | Ship native for 32-bit phones? | Pending |
