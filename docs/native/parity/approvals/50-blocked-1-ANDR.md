RESULT: APPROVED WITH FIXES

# Approval — job 50-blocked-1-ANDR (native migration second pass, Android rows)

Branch `native/p12x/job-50-blocked-1-ANDR`. Job commits reviewed: `a9ebf44a`, `ef20e662`, `c78d8a44`,
`463b6896` (base `518d4340`). Report: `docs/native/parity/migration/50-blocked-1-ANDR.md` (37 rows: 28
built now, 7 already there, 2 blocked). Rows were checked against `catalog/behaviors.jsonl`, the Flutter
sources in `lib/`, `test/` and the Flutter app's Kotlin in `android/`, the binding plan (plan/01, plan/11)
and `native/AGENTS.md`. Per the owner, no tests were written or run and no APK was built. Android was not
compiled here (Google Maven is blocked), so the build VPS compiles next; every symbol the fixes rely on
was checked by name, import and signature against the sources.

## Merge of `native/p12-head` (`8fd3b2a5`)

Clean merge, no conflicts. Only `BuiltinLinux.kt` was touched on both sides (job: heat-guard AI Team
recipe/resume; head: confirmed-recovery Undo and the best-effort Claude sign-in comment); Git kept both
sides. No duplicate top-level names in the `graph` package, and no `build.gradle.kts` dependency lists
changed on either side.

## Findings and fixes

1. **B-ANDR-068: release-lineage guard broke every release packaging (fixed).** `verifyProductionLineage`
   ran before every `package*Release` task and failed when the release build type has no Gradle signing
   config. That is the normal case in this repo: the migration proof, the build server and the T2 ladder
   package releases unsigned (or debug-signed) and sign afterwards with `apksigner`. It also read
   `project.extensions` at execution time, which the enabled configuration cache rejects. The guard now
   captures the signing values at configuration time and verifies the certificate only when a release key
   is configured in Gradle; unsigned builds are left to the signer comparison at delivery (root
   AGENTS.md). The two allowed digests match the AGENTS.md signer list.
2. **B-ANDR-068: network security config comment (fixed).** The policy is identical to Flutter's and
   matches the SEC gate shape, but the comment named Dart as the boundary; it now names the native
   `TrustGateInterceptor` (core/net) and per-profile consent.
3. **B-ANDR-108/109: exported launcher entry read routing extras (fixed, security + parity).**
   `WidgetConfigureActivity` is exported but inherited code that took the window mode
   (`steer`/`voice`/`project`) from the incoming intent, so any app could open the message overlay with
   a crafted target, and the launcher (which never sends a mode) could never reach the project window.
   As in Flutter's `WidgetRouteActivity.modeFor`, the configure entry now chooses the window from the
   widget's provider: the Conversations widget opens `project`, the others `configure`. The private
   `WidgetRouteActivity` path is unchanged.
4. **B-ANDR-109: voice box needed an extra tap (fixed).** Flutter's voice box starts dictation as soon as
   it opens (`test/widget_overlay_test.dart`); `WidgetActionViewModel` now starts dictation on open for the
   new-conversation window. Sending still needs an explicit Send.
5. **B-ANDR-110: duplicate copy keys (fixed).** The job re-declared `thermalGuardSetting` and
   `thermalGuardSettingDetail` in `strings_phone.xml` (English and Arabic, with a differing Arabic text)
   although both already exist in `strings_common`/`strings_settings`; duplicate Compose resource names
   fail resource generation. The duplicates are removed; `thermalGuardSaveFailed` stays.
6. **Report corrections.** B-ANDR-019 now cites the production adapter (`AgentTerminalWiring.kt:312`);
   B-ANDR-068 and B-ANDR-108/109 describe what is actually built after the fixes; the storage note
   records the copy-key change.

## Verified as claimed

- **Sign-in / idle / setup / monitor (001, 019, 020, 035, 041, 045, 050, 055, 057, 081):** ownership
  registry bound in the graph and used by the sign-in foreground and terminal host; `IdleStartActivity`
  is `exported="false"` and resumes only through the validated idle receipt; setup taps reach
  `PhoneSetupRoute.Setup`; the loopback monitor reader uses encrypted credentials registered with the
  redactor, no redirects, capped bodies, and is wired into `MonitorRuntimeWiring`.
- **Heat guard (041, 086, 088, 090, 110):** `ThermalRuntime` is started and has a profile closer; holds
  use `oc.thermalPause.<id>` (registered profile-scoped, swept on deletion); recovery reads the per-profile
  automation policy; the saved AI Team recipe is captured in-process from the app's own service, kept in
  private preferences, never displayed, and the legacy fallback is a fixed loopback script on port 8472
  that honours the disabled marker and never restarts OpenCode. The switch uses kit parts.
- **Quota and plan alerts (065–067, 095–100):** collector and subscription delivery are reachable from
  launch; enrollment, visibility/background, Wi-Fi fail-closed, quiet hours, 45-minute freshness,
  reset-sensitive dedupe and refused-delivery rollback match `lib/state/usage_subscription_alerts.dart`;
  only hashed keys and opaque tokens are stored; the Plans widget preferences are kit-only and publish
  after durable storage; copy exists in English and Arabic.
- **Widget actions (103–107, 118–121):** cold actions check health first, confirm the exact request,
  dispatch once with durable claims and never replay; the team row talks only to a loopback engine with
  a redacted Bearer token and one POST; scheduled checks use one read-only transport and fence late
  results; model names are sanitized and step text is generic. All `Res.string` keys exist once in both
  locales.
- **Blocked (038, 048):** the option-button clause does conflict with binding plan/11 §10.4 and CR11-16;
  an owner decision is needed, not app work.

## Noted, not changed

- `AgentWidgetWiring.publish`/`clearForProfile` have no external caller (pre-existing; `PlatformHub` is
  the real publisher).
- Small accepted differences: the overlay shows Open/Close after a send instead of Flutter's 1 s
  auto-close; the project window needs Save; steer copy is one generic hint; Wi-Fi-only skips the whole
  subscription evaluation rather than only alerting plans; a refused reset message rolls back the level
  record too; `ThermalRuntime.start()` does not retry a failed store warm-up.
