ORDER: 44-settings-appearance
ENGINE: sonnet
DEPENDS: 13,22,24,31,32,33,34,35,36,37
WRITES: native/feature/settings/src/commonMain/kotlin/**/ui/** native/feature/settings/src/commonMain/kotlin/**/*Screen.kt native/feature/settings/src/commonMain/kotlin/**/*Sheet*.kt native/feature/settings/src/desktopTest/kotlin/**/rb/o44/** native/feature/settings/build.gradle.kts native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PersonalPrefs.kt native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PreferencesChoices.kt native/ui/words/src/commonMain/composeResources/values/strings_rb44.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb44.xml docs/design/native-ui-rebuild/waivers/44.tsv docs/design/native-ui-rebuild/evidence/44.md
GATE: native/tool/design/gate_order.sh 44
TIMEOUT_MIN: 480
---
# Order 44-settings-appearance: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 480 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-44`, branch `native/rb-44-settings-appearance`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/44.gate.tsv` (217 goldens). Gate command: `native/tool/design/gate_order.sh 44`.
- Depends on: order 13, order 22, order 24, order 31, order 32, order 33, order 34, order 35, order 36, order 37. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-44-sonnet` (standard §11).

## 2. Screens

Native modules: `native/feature/settings`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-I01` | I01 | `SettingsScreen` (hub, search, `SettingsServersBlock`) | page | `SettingsRoute.Hub` (Top; list pane) + `HighlightArrival` |
| `screen-I02` | I02 | `SettingsGroupScreen` (`settings/settings_group_page.dart`) | page | `SettingsRoute.Group(group)` (detail pane) |
| `screen-I03` | I03 | `ByServerScreen` | page | `SettingsRoute.ByServer(kind)` |
| `screen-I04` | I04 | `AppearanceSettingsScreen` (+ `appearance_one_page.dart`, `showThemePackPreview` local, `GlassChoiceControl`) | page | `SettingsRoute.Appearance` |
| `screen-I05` | I05 | `NotificationsSettingsScreen` (page all / notifications / background, section slugs) | page | `SettingsRoute.Notifications(page, section)` |
| `screen-I06` | I06 | `PermissionsAutomationScreen` + `AutomationSettingsSection` | page | `SettingsRoute.PermissionsAutomation` |
| `screen-I07` | I07 | `SavedPermissionsScreen` | page | `SettingsRoute.SavedPermissions(profileId, projectDir?)` |
| `screen-I08` | I08 | `PrivacySettingsScreen` | page | `SettingsRoute.Privacy` |
| `screen-I09` | I09 | `showLanguageSheet` / `LanguageSettingsTile` | sheet | local sheet of I02 |
| `screen-I10` | I10 | `PhraseChipsSettingsRow`, `TranscriptDisplayToggles`, `DefaultShellRow` (sheet) | part/sheet | switch rows in I02 Conversations; default shell = local sheet, loaded lazily |
| `screen-I11` | I11 | `VoiceSettingsScreen` (`lib/voice/voice_ui.dart`) + `showVoiceAutomaticSetupSheet` | page | `SettingsRoute.Voice` (automatic setup sheet local) |
| `screen-I12` | I12 | `AiSetupScreen` | page | `SettingsRoute.AiSetup(profileId)` |
| `screen-I13` | I13 | `IntegrationsScreen(mode: providers)` (+ key dialog, OAuth sheet, device-code dialog, credential sheet, pending-auth recovery) | page | `SettingsRoute.Providers(profileId, connectProviderId?)` |
| `screen-I14` | I14 | `CapabilitiesScreen` / `openTools` (tabs MCP, Commands, Tools, Skills, References, External agents) | page | `SettingsRoute.Tools(profileId, section, from?)` (with `from`, Skills applies to that conversation and References is a picker for it, C-slash); MCP tab body = the former `IntegrationsScreen(mode: mcp)`; **External agents tab dropped** |
| `screen-I15` | I15 | `showMcpAddSheet`, `McpCatalogScreen`, `McpSetupScreen` | sheet/page | add sheet local to I14; `SettingsRoute.McpCatalog(profileId)`; `SettingsRoute.McpSetup(profileId)` |
| `screen-I16` | I16 | `CommandsScreen`, `ToolsScreen`, `SkillsScreen` (+ skill activation sheet), `ReferencesScreen` | page (embedded) | tab bodies of I14 |
| `screen-I17` | I17 | `ServerPluginsSection` | part | section of E04 |
| `screen-I18` | I18 | `ManageSpaceScreen` + `ProjectExportScreen` (`lib/manage_space_main.dart`) | page | `ManageSpaceActivity`: `ManageSpaceRoute.Home`, `ManageSpaceRoute.ExportProjects` |
| `screen-I20` | I20 | freeze tip `settings/updates_screen.dart` | page | `SettingsRoute.Updates` (This app; Android only, `AppSelfUpdate`) |
| `screen-I19` | I19 | Keyboard shortcuts row; Try the demo row | row | rows in I02 Help (shortcuts row only when a hardware keyboard has been seen, or desktop) |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/settings_*`, `test/goldens/languages_*`, `test/goldens/gallery_*`, `test/revamp/goldens/settings_*`, `test/revamp/goldens/aisetup_*`, `test/revamp/goldens/library_*`, `test/revamp/goldens/library2_*` — 217 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

Appearance (Settings › Appearance): rebuild `lib/ui/screens/settings/appearance_one_page.dart` and `appearance_screen.dart` exactly, and offer **Glass** in the Style question as at GLASS_REF (`appearance_*` and `kit_option_card.dart` @ 1bbdc3f9c show where Glass sat). Order of styles: Solid, Glass, Soft, Brutal, Paper, Signal. Picking a style switches it live (standard §5.6). The live background picker lists every look from order 31-33 plus `auroraImage` (order 35). Lead prerequisite: `glass` in `:engine:settings` `SurfaceStyle` (standard §5.7).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/settings/src/commonMain/kotlin/**/ui/**`
- `native/feature/settings/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/settings/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/settings/src/desktopTest/kotlin/**/rb/o44/**`
- `native/feature/settings/build.gradle.kts`
- `native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PersonalPrefs.kt`
- `native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PreferencesChoices.kt`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb44.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb44.xml`
- `docs/design/native-ui-rebuild/waivers/44.tsv`
- `docs/design/native-ui-rebuild/evidence/44.md`

## 4. Gate and done

1. `cd ~/wt/rb-44 && native/tool/design/gate_order.sh 44` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o44.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/44.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
