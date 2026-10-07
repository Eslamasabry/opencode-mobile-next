# BC2 — storage admission at point of use

Existing APIs remain `ChannelSetupEngine.run` → `BuiltinLinux.startSetup`, native `SetupRunner.status()`, and private `PhoneAgentHost.start`.

Each Dart setup spec adds `data.requiredFreeBytes` as a decimal **String** (the native decoder ignores non-String data). The value is twice that component's download size with a 300 MB floor, saturating at signed 64-bit max. Existing `runtime`, `openCodeChanged` and `waitMinutes` data remain intact. Old specs default to 300 MB.

Native storage is read again before each unskipped component is published `running`. Low storage records component/job `failed` without launching its download, native install, script or app step. Completed earlier components remain done; retry uses existing resume. Entirely skipped jobs need no storage admission. The nearest existing app-data ancestor is measured before initial Linux setup; no missing directory is mistaken for a zero-capacity filesystem.

The private agent host requires 128 MB before writing config, clearing locks or launching a process. Failure throws only: “There is not enough free space on this phone. Free some storage and try again.” Map this fixed sentence to localized storage copy; offer free storage and Retry. Technical disk errors belong only in Details. No UI, localization or connection library is changed by BC.

Pure Dart API: `checkSetupStoragePreflight(int? availableBytes, {required int downloadBytes}) -> SetupPreflightResult`. Unknown initial readings retain the existing allow-to-try behavior; native point-of-use admission measures app storage freshly.

No credentials or user content enter storage errors. Persisted format change is additive `data` metadata; no migration is needed.

Evidence: `docs/qa/BC2-2026-10-07/README.md` (host runner/launch regressions and production storage policy on Android). This is not an installed APK integration claim.
