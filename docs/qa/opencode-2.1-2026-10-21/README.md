# OpenCode 2 pin: 2.0.29 → 2.1.1 (2026-10-21)

Branch `feat/opencode-2.1` (from int `741aa7615`). `@opencode/cli` 2.1.1 was
published 2026-10-20 and is the latest stable (2.0.30–2.0.32 and 2.1.0 lie in
between). Upstream has no release notes, so the sources are the two servers'
own `/openapi.json` and the commit subjects between tags `v2.0.29` and
`v2.1.1` in `anomalyco/opencode` (234 commits, `commits-v2.0.29..v2.1.1.txt`).

## Result

- **No route the app uses moved, was removed, or changed shape.** The
  `/openapi.json` diff has no removed operation. [`Api2Dialect.stable`] covers
  2.0.x and 2.1.x without any new translation.
- **Live proof `tool/qa/oc2_live_proof_test.dart`: 46/46 on 2.1.1 and 46/46
  on 2.0.29**, prompting the free model `opencode/glm-5.3-flash-free` (the
  suggested `muse-spark-1.3-contributor-free` is not listed on either server).
- Every current 2.0.29 pin is now 2.1.1, with new tarball checksums.

## `/openapi.json` diff (exact; `openapi-diff-2.0.29-to-2.1.1.txt`)

118 → 119 paths. Produced by `diff.py` over both servers' live documents.

| Change | App impact |
|---|---|
| + `PATCH /api/permission/saved/{id}` (edit a saved permission) | None. The app lists and deletes saved permissions; editing them would be a new feature. |
| + `POST /api/experimental/voice/session` (+ `Voice*` schemas) | None. Unused. |
| `GET /api/session` + `archived=true\|false` query | None today. Probed: the default list still includes archived sessions on both versions; the app filters by `time.archived`. |
| `PATCH /api/session/{id}` body + `archived` | None today (see *Archive* below). |
| `GET /api/session/{id}/message` `type` filter now accepts `idle` | None. The app does not filter by `idle` (2.0.29 answers 400; 2.1.1 answers 200). |
| `POST /api/experimental/session/import` takes `messages` as plain JSON and upgrades pre-2.1 exports | Positive. Exports taken from 2.0.x import into 2.1. |
| `Session.Message.*`: provider replay data `providerState` / `state` / `providerResultState` → `native`; `Session.ProviderContext` removed | None. The app parses text/reasoning `state` into an unused map; the tool `state` (lifecycle) is unchanged. Proven by the live 2.1.1 fixture test. |
| Compaction message: `recent` removed (no longer required) | None. The app reads `summary` / `error` only. |
| `Credential.Key.configuration` removed | None. Not read. |
| `Config.Model.Capabilities` + `reasoning`, `temperature`; `Config.ModelEncoded` + `releaseDate`; `Model/Provider.Compatibility` + `streamUsage`; `Config.experimental` + `todo` | None. Additive config/catalog fields. |

## Events (not described by `/openapi.json`; event payloads are opaque there)

From commit `acdcdb8dd refactor: v2.1 minor breaking changes`, plus the live
probe (`probe.py`, `probe-2.0.29.json`, `probe-2.1.1.json`):

| Change | App impact |
|---|---|
| `session.compacted` removed from the event manifest | None. **Probed: 2.0.29 never emitted it either** during a manual compaction; both versions send `session.compaction.started/delta/ended`. The app's `session.compacted` case stays for the beta. |
| `session.compaction.started/ended`: `recent` removed | None. Not read. |
| `session.text.ended`, `session.reasoning.ended`, tool events, `session.step.*`: `state`/`resultState`/`providerState` → `native` | None. Not read from events. |
| New on 2.1.1: `session.archived`, `session.unarchived` | Not added to the event union: the app has no archive feature on OpenCode 2 yet. Add them when archiving is wired. |
| Also seen on 2.1.1 only: `project.updated`, `session.renamed` after a compaction | Already handled or ignored, as before. |

## Archive (restored in 2.1, `fa9da987b feat(core): restore session archiving`)

Probed on 2.1.1: `PATCH /session/{id} {archived:true}` → 204, `time.archived`
set, `session.archived` emitted; `{archived:false}` → 204 and cleared.
On 2.0.29 the same PATCH also answers **204 but changes nothing**. So
`ServerCapabilities.sessionArchive` stays `false`: a static flag cannot be true
for 2.1 without lying on 2.0.x, and capabilities do not yet follow the server
version. Enabling archive is a follow-up slice (version-aware capability plus
an unarchive path).

## Other commits worth knowing (server-side, no client change)

`fd6589239` compaction summarizes the whole conversation; `8f7db12b2`
todowrite tool behind `experimental.todo`; `d889cb204`/`412b2b1d8` model
reasoning from config; `49f52ff2d` `streamUsage`; `c38d83c0c` idle message
filter; `fede5ddda` edit saved permissions; `d4a6223d0`/`31dc76214` voice
sessions; many provider retry/stream fixes.

## Pins changed

`grep -rn "2\.0\.29" lib tool scripts docs test widgetbook` before and after.
Changed: `TermuxBridge.defaultOpenCodeVersion`, `TermuxRuntime` pin
(`bridge_models.dart`), both `requested_version` lines in `manager_script.dart`
(reviewed hashes in `test/builtin_linux_test.dart` and
`test/fixtures/termux_script_hashes.json` regenerated with
`WRITE_SCRIPT_HASHES=1` after checking that the pin is the only Termux source change), `setup_commands.dart`, `guide_screen.dart`,
`server_settings_screen.dart`, `server_probe.dart` upgrade command,
`agent_catalog.dart`, `OpenCodePins` (`lib/builtin/setup/components.dart`),
`scripts/host/ubuntu-opencode.sh`, the BYO host bundle pins
(`scripts/byo-host/*`, `release-pins.json`; `bundleVersion` left at 2.0.0 since
no bundle is published, `appVersions` is empty), the `app_en.arb` placeholder
example, `docs/technical-overview.md`, `docs/ubuntu-host.md`, and the tests,
fakes, census and coverage fixtures that mirror the pinned version.

Left on purpose: dated evidence in `docs/qa/**`, `docs/design/**`,
`docs/verification/**`; comments that name what was probed on 2.0.29
(`opencode_history_copy.dart`, `capabilities.dart` worktree note); tests whose
names describe 2.0.29 features; one-off probes in `tool/qa/*.py`;
`isExactServerVersion` format tests.

Tarballs (downloaded from the registry, SHA-512 compared with
`npm view @opencode/<pkg>@2.1.1 dist.integrity`: **both match**):

| Package | SHA-256 | Bytes |
|---|---|---|
| `@opencode/cli-linux-arm64@2.1.1` | `cfa3ae6363fc3082f03072297069d81f53559a761371fdb4b56fe6bfa01ca066` | 97 694 833 |
| `@opencode/cli-linux-x64-baseline@2.1.1` | `216d3861d0a167adad5850282439bae13483203f4b110f0510f260118bba2858` | 98 936 625 |

Each holds only `package/package.json` and `package/bin/opencode`.

## Client changes besides pins

- `lib/api2/dialect.dart`: doc note that 2.1 changed no route (no code change).
- `lib/api2/gateway_mappers.dart`: "probed live" note now names 2.1.1, and
  explains why `sessionArchive` stays false. Capabilities unchanged.
- Tests: `test/api2_dialect_test.dart` pins that the 2.1 additions pass through;
  `test/api2_gateway_mappers_test.dart` maps a live 2.1.1 page
  (`test/fixtures/api2/messages_2_1_1.json`, from `capture.py`, with paths and
  ids rewritten: reasoning with `native`, a `read` tool call, compaction without `recent`).

## Commands

```bash
# isolated installs (HOME/XDG under ~/oc21-spike/{old,new}/home)
npm install -g --prefix ~/oc21-spike/old/prefix @opencode/cli@2.0.29
npm install -g --prefix ~/oc21-spike/new/prefix @opencode/cli@2.1.1
opencode serve --port 4197 --hostname 127.0.0.1   # 2.0.29, scratch git repo
opencode serve --port 4196 --hostname 127.0.0.1   # 2.1.1, scratch git repo
curl -u opencode:$PW http://127.0.0.1:<port>/openapi.json > oa.json
python3 -I diff.py old/oa.json new/oa.json
python3 -I probe.py <port> <pwfile> <workdir> probe-<v>.json
git log --format='%h %s' v2.0.29..v2.1.1      # blobless fetch of both tags

OC2_LIVE_URL=http://127.0.0.1:4196 OC2_LIVE_PASSWORD=… OC2_LIVE_DIR=… \
  OC2_LIVE_MODEL=opencode/glm-5.3-flash-free \
  flutter test -j 2 tool/qa/oc2_live_proof_test.dart     # and 4197 for 2.0.29
flutter test -j 2 test/api2_*_test.dart <every touched test> \
  test/server_probe_test.dart test/agent_catalog_test.dart …   # 46 files
flutter test -j 2 test/coverage/
python3 -m unittest discover -s test/byo_host -p '*_test.py'
flutter analyze
```

The servers' per-run passwords were read from their own logs into mode-600
files and are in no file here.

## Results

| Check | Result |
|---|---|
| Live proof, 2.1.1 | **46/46**, model replied `pong` (`live-proof-2.1.1.txt`) |
| Live proof, 2.0.29 | **46/46**, model replied `pong` (`live-proof-2.0.29.txt`) |
| Raw compaction + archive probe | both versions (`probe-*.json`) |
| 46 affected unit test files | 545 passed, 1 skipped, 1 failed: the intended manager-script hash. After regeneration `test/builtin_linux_test.dart` passed 25/25 |
| `test/coverage/` | 460 passed |
| BYO host Python tests | 37 passed |
| `flutter analyze` | No issues |
| `tool/qa/preflight.sh 741aa7615` | OK, after refreshing `termux_script_hashes.json` (9 scripts embed the pin) |

Not run: the full suite, emulator/device, widgetbook checks (no screen
changed; only a fake's version string).
