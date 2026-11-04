RESULT: APPROVED WITH FIXES

# Approval: 31-build-REST-1 (REST native migration, part 1)

Branch `native/p12x/job-31-build-REST-1`. Job commit `92d1facb`. Merged `origin/native/p12-head` (`cde0afb4`) first with
no conflicts (it only touched `docs/native/units/INTEGRATE-P12.md`). Review only: Gradle could not run here (Google Maven
is blocked), so the build VPS compiles next. No tests and no APK, as the owner asked.

## What was checked

- Every "built now" row against the Flutter source (`lib/`, `test/`) and the catalogue row, and checked that it is wired
  into the real app graph (Metro contributions, entries, settings host), not left as a stub.
- Spot checks of the "already there" claims (BuildRestGates, ReleaseCheck, the git remote validators, KitLogBuffer,
  ReportProblem.recordTimings, the locale-driven channel relocalisation in PlatformWiring, UsageWindow clamps,
  MaterialBridge surfaceTint, resolveMotion, the release tests): all true at the cited places.
- Every `Res.string` / `Res.plurals` the job references exists in both `values/` and `values-ar/`.
- Kit only: every new drawing (KitSurface, KitSwatch, the style specs, the shape knob) is inside `ui/kit`, `ui/kit-chat`,
  `ui/kit-content` or `ui/design`. Features only arrange kit parts.
- Types and names against their declarations: GatewayEvent/MessageInfo/Part fields, PromptTimeline/PromptSignal,
  HttpSpanRecorder, NetClient.attach, Oc2Gateway `request(...)`, BuiltinLinux.run, ManagedCardsInstaller/CardsStatus,
  ActiveContextGateway, the settings enum mappings (stored and kit Corners/Density/Style enums have the same names, so
  the `first { }` mappings cannot throw).

## Security and privacy

- B-BUILD-026: spans carry method, path template (query stripped, opaque segments become `:id`), status and bytes.
- B-BUILD-027: prompt tracing carries session and message ids only, never prompt text.
- `ApiRouteGateway`: the gateway adds its own origin and Basic credentials; the session id is path-segment encoded;
  `ApiRouteReply.toString()` never prints the body. No credential reaches logs.
- Cards install runs only the app-authored scripts in the built-in Linux; a launch failure is exit -1 (a failure, never
  success). No regression found.

## Findings and fixes (commit `8c660b48`)

1. **Cards "managed" test differed from Flutter (B-CHAT-040..042, 047).** The job decided the phone's own server by
   `oc.builtinServerOwner == profileId`. Flutter `_genUiManaged` decides by the profile's address being the built-in
   server (`BuiltinLinux.managesServerUrl`, `http://127.0.0.1:4097`). Fixed: `PhoneCardsQualifier.managed` now uses
   `ManagedLocalServer.isBuiltin(profile.baseUrl)`.
2. **Active-context missing-route latch was lost on every reopen (B-CHAT-002).** `open()` built a new
   `ActiveContextGateway` each time and `close()` dropped it, so a server without the route was asked again on every
   open and the bar action never hid. Flutter keeps `activeContextSupported` per connection and hides the entry. Fixed:
   one gateway per bound connection, kept across close and reopen. `available()` returns false once it has latched off.
   Opening a latched gateway shows Unsupported without making a request.
3. **Dead code: `ui/kit/.../capability/KitEnableFlows.kt`.** The file added a registry for B-KIT-013 (a row the job
   left blocked), and nothing used it. The report also said in B-BUILD-003 that no such registry exists. Removed. Both
   rows stay blocked until the KIT-013 slice, which needs its consumers.
4. Report corrected for 1–3 (B-CHAT-001 now names where the action lives, B-CHAT-040 names the managed rule, B-KIT-013
   is reworded).

## Notes, not blocking

- Active context opens from the conversation bar's line action. Flutter opens it from Session context. It is reachable
  and labelled. Moving it into `ChatContextSheet` would match Flutter's entry point exactly.
- B-KIT-042/046/048 are honestly marked partial (Signal lamps and slip, the 200/120 ms style cross-fade, some hook
  consumers). B-KIT-041's contrast gate across combinations is not proved until the testing phase.
- `GatewaySendLink.prompt` records `prompt.sent` even when `beforeSend` cancels the send. The timeline only ends at
  idle/error, and `PromptTimeline` caps tracked sessions at 16, so this stays bounded.
- The "blocked" rows (B-BUILD-003..006, 011, 012; B-CHAT-046, 048, 108, 124; B-KIT-013, 060) name real missing
  prerequisites. None hides a stub.
