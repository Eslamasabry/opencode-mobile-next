# Audit fix C (classes C12, C13, C15, C18, C19), 2026-11-09

Branch `native/audit-fix-c`. Before images are from the audit renders (audit-41); the home pair is the repo golden before and after.

| Class | Fix (at the root) | Guard test |
|---|---|---|
| C12 blank loading pages | Settings Storage, Linux home and the setup journey show the kit loading state with words | `C12 ...` in SettingsScreenUiTest, LinuxScreensTest, SetupJourneyGateTest |
| C13 top-bar actions cut | `KitTopBar` action share 35% to 50% (hub bar 45% to 55%); title keeps 90 dp | `C13 ...` in TopBarTitleLineTest |
| C15 two accent buttons | Save and connect is secondary while Scan code leads; `KitPrimaryButtonKey` marks accent buttons in semantics | `C15 ...` in ServerEditorParityTest |
| C18 dead end | Opening-failed fixture now carries the Settings door, golden re-recorded | gear assertion in E20S09OpeningGoldensTest |
| C19 mid-word break | A step's file name with no space is cut in the middle on one line at 1.3x text | `C19 ...` in KitToolRowTest |

Not changed: the Write diff still wraps on a phone window. That is Flutter's rule (wrap on compact, scroll from medium), so it was left as is.
After renders for C12, C13, C15 and C19 are not included; the guard tests above are the proof for them.
