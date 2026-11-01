# Linux backup and restore S3: verification, 2026-11-01

Branch `feat/linux-backup-2026-11-01`.

## What a backup is
One zip, saved where the person picks with Android's "Save to":

| Entry | What it holds |
|---|---|
| `manifest.json` | Format 1, kind, date, app version, ABI, the entries |
| `apt-manual.txt`, `npm-global.json`, `pipx.txt` | What was installed, restored by name |
| `home.tar.gz` | Settings files on the allow list (`.bashrc`, `.gitconfig`, `.ssh/authorized_keys`, `config`, `known_hosts`, `.config/{git,nvim,htop}`, …) |
| `services.tar.gz` | Service definitions and on/off choices |
| `projects.tar.gz` | `/root/projects` without `node_modules`, `.venv`, `build`, `dist`, … |

**Never in a backup** (owner decision): sign-ins, tokens, `.env` files, key files, and private SSH keys (`id_*`). A `.git/config` with a password in its remote also stays on the phone.

**Restore:**
- installs missing packages and tools by name, and lists the ones Ubuntu no longer has;
- keeps a settings file it replaces as `.before-restore`;
- never overwrites a project: a project that already exists comes back as "name (restored)";
- starts the services that were on.

**Projects only** is the former This phone › Export projects (owner decision). Reset offers **Back up first**.

**Ships as a patch:** it reuses the existing zip writer (`oc/project_export`) and file picker, and reads zips in pure Dart.

## Tests (VPS)
| File | Result |
|---|---|
| `test/linux_backup_plan_test.dart`: the real backup and restore scripts under `/bin/sh`, with stand-ins for apt, dpkg, npm and pipx | pass |
| `test/linux_backup_test.dart`: zip reader on Python-made zips; the controller over a temporary rootfs | pass |
| `test/linux_backups_page_test.dart`: choices, primary, Projects only, restore row, every ending, nothing while busy, Arabic | pass |
| `test/linux_manager_screens_test.dart`: hub row, Reset › Back up first | pass |
| Neighbours: `search_index_test`, `kit_ratchet_test`, `linux_services_ui_test`, `linux_vitals_test`, `manage_space_screen_test`, `text_size_test`, the This phone tests and goldens | pass |
| `flutter analyze` on the changed files | no issues |

**Goldens:** 15 This phone goldens were regenerated. The only change is the Export projects row, which is gone by design (it moved into Backups).

## Each test fails without its fix (mutations)
| Mutation | Test that then fails |
|---|---|
| A remote URL with a password kept in projects | holds what it should, and no secret |
| A project of the same name overwritten | a restore puts back, keeps what is there |
| No `.before-restore` copy | a restore puts back, keeps what is there |
| Any zip name extracted | extracts only backup names |
| The stage left behind | the zip holds the manifest first… cleaned |
| A link where the restore folder goes followed | a link … is refused |
| A newer backup called "not a backup" | a zip that is not a backup, or a newer one |
| Restore usable while a job runs | nothing can start while one runs |
| Reset's Back up first ignored | Reset offers "Back up first" |
| `.ssh/id_*` rule removed, with the whole `.ssh` folder allowed | no secret (with the rule on, it passes) |

## Renders (real fonts, light Solid)
`/home/eslam/Storage2/wt/shots-2026-10-31/linux-s3-device/{1-backups,2-saving,3-saved,4-restored,5-not-a-backup}.png`

## Device proof (emulator-5584, APK 4470, no debug hooks)
The plugin registrant is present: dexdump counts `GeneratedPluginRegistrant` 6 times.

**Bug found on the device (APK 4430), fixed in `7fe40608d`:**
- under proot, tar could not create its own `.tar.gz` ("Cannot open: Function not implemented"), and a failed backup left an empty zip in Downloads;
- tar now only streams through the shell and gzip, and "Save to" is asked only once the parts are made;
- the script tests now run against a tar that fails the same way, and fail on the old code.

**The video:** live recording edited with Remotion, `/home/eslam/Storage2/wt/shots-2026-10-31/linux-s3-device/backup-demo.mp4` (1080×1920, 37 s, no sound). The raw recordings `bk-1.mp4` and `bk-2.mp4` are next to it.

| Step | Seen |
|---|---|
| Linux › Backups | "No backup yet" on the hub row; Setup and projects chosen |
| Back up setup and projects | The job panel ("Backing up Linux", Stop the backup), then Android's "Save to" in Downloads |
| SAVE | "Backup saved: 33.1 KB, as opencode-linux-setup-projects-2026-11-01.zip" |
| The demo project `backup-demo` removed, then Restore from a backup | The picker, then "Restore this backup?" with what stays, what is replaced and what is added |
| Restore | "Linux is restored. Back: projects 3…". `backup-demo/README.md` was back with its content. The other two projects came back as "(restored)" copies, as designed. |

**Cleaned up afterwards:**
- the demo project, the "(restored)" copies and the zips in Downloads, including the two 0-byte ones the 4430 run left;
- the settings files the restore had replaced were put back.

## Removal closes the S3 persistence rows (2026-11-01, freeze pass)

The architecture's persistence table (2-architecture.md §5) says Remove OpenCode clears every `oc.linux.*` key and that the Remove question says backups stay. Neither was built; S3 only wrote `oc.linux.lastBackupAt`.

| Change | Where |
|---|---|
| `forgetLinuxDevicePreferences` removes every key under `oc.linux.` (not `oc.linuxReset.*`, not profile keys); called once removal worked, in `_removePhoneServer` | `lib/builtin/linux_backup/linux_backup.dart`, `lib/ui/widgets/phone_server_card.dart` |
| Reset does **not** call it: Reset keeps the stamp, as the table says | `linux_reset.dart` unchanged |
| Once a backup was saved, both Remove choices add the line "Backups you saved stay where you put them" (en, ar). With no backup saved the question is unchanged, so the Remove goldens do not move. | `removeFromPhoneBackupsKept` |

Tests written, **not run** (owner's freeze: analyzer only): `test/linux_backup_test.dart` (group "device settings of Linux"), `test/phone_server_remove_keep_projects_test.dart` (no mention without a backup; both sheets say it, cancel keeps the stamp, removal clears `oc.linux.*` and leaves other keys; a failed removal keeps the stamp). `flutter analyze lib` and the four changed files: no issues.

**Not in S3 and not built here:** S4 package search, S6 apps catalog (SSH spike first), S7 full copy and S5/S2b native work (one release), S8 Running merge. The size estimate before a backup (design 3-ui.md §2.6, "Measuring…") was not part of S3's finish line and is still open.
