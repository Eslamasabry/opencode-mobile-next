# Notifications: a question or a plain result (vision slice, 2026-10-20)

Branch `feat/vision-notifications` (from int `433981f66`). Pick: **A's copy + B's summary**
(`screen-concepts/b2-notifications/concept-a.png` for the copy and the two answers,
`concept-b.png` for "While you were away: 2 finished" as one grouped item), owner decision 1
(permission asks are answered outside the chat, always with the exact command; questions with
options or free text open the chat).

## 1. Technical-honesty audit (written before building)

What posts notifications today: `ConnectionController` (`lib/state/connection/alerts.dart`) for the
server in use and each side connection, `ProfileMonitor` (`monitors.dart`) for watched servers,
the AI Team (`team.dart`) and the usage monitor. All of them call
`BackgroundLive.showCodingAlert` (`lib/background/live_background.dart`) → `oc/background`
→ `BackgroundConnectionService.showCodingAlert` (Kotlin), which already groups "needs you" and
"finished" alerts, posts a count-only public version for the lock screen and routes Allow once /
Deny through `CodingActionReceiver` back into Dart (`answerPermissionRequest`, OC2
`POST /api/session/{id}/permission/{requestID}/reply`).

What each poster really knows:

1. **Server in use / side connections**: the full `PermissionRequest` (`commandPreview`,
   `filePath`, `permission`, `patterns`), the pending question's own words and choices, the
   session title and directory, the saved server name, the first line of the newest reply, and a
   reply path.
2. **Watched servers** (`MonitoredRequest`): only `{id, sessionID, kind, title, directory}`. No
   command, no file, **no reply path**.
3. **AI Team**: ids only by design (TEAM-203); `subtext` is the saved server name.
4. **Usage monitor**: a provider name and a token; no session.

| Element of the pick | Source | Verdict |
|---|---|---|
| Title = the question, naming the action and its target ("Run npm install?") | `PermissionRequest.commandPreview` (OC2 `Permission.Request` metadata/resources), `filePath`, `permission` | **Build.** Dart reduces the command to its program and sub-command ("npm install"), the file to its name ("settings.dart"), masked by `redactForNotification`; native puts it in a localized template ("Run %1$s?", "Edit %1$s?", "Read %1$s?", "Work in %1$s?"). No command or file → "Allow a request?" with **no Allow button** (never answer what you cannot read). |
| Body = the exact command, as today | `commandPreview ?? filePath`, masked | **Keep.** Unchanged source; the body no longer repeats the title. |
| Question title for a question | `PendingQuestion.prompts.first.question` | **Build.** The question's own words, masked and cut at 80 characters; body = its choices joined with " · " when it has them. |
| Subtitle = server · project | `profile.name` (saved server name; "This phone" for the in-app server), `Session.directory` last folder | **Build.** Composed in Dart; replaces the chat title in the subtitle (one subtitle, no extra lines). Watched servers: the saved name and `MonitoredRequest.directory`. AI Team keeps the server name alone (no project travels by design). |
| "Needs you" in amber (concept A) | Android header colour (`setColor`) | **Reject.** Android tints the app icon and action labels with `setColor`, not a word; an amber Allow/Reject would read as a warning. The amber "Needs you" lives in the app; the shade carries the question. Never red: no `setColor` anywhere (checked). |
| Two actions: Allow once / Reject | `AlertButtons.plan` + `CodingActionReceiver` + Dart `codingAlertAction` | **Adapt.** Already Allow once / "Deny"; relabelled **Reject** to match the in-app card. Allow once keeps `setAuthenticationRequired` (lock screen asks for unlock first, pick "Adapt"). |
| Other questions: Open | Content intent (`MainActivity` with session + profile) | **Build.** One "Open" action for questions, forms, and watched-server asks (no reply path there). Removes the quick-Reply and option buttons (owner decision 1: questions open the chat). |
| "Not now" third reply | — | **Reject** (pick): dismissing is not now. |
| "Details" link inside the card | Android draws the card | **Remove.** Expanding the notification (BigText) shows the full masked command; a link would be app styling Android will not render. |
| Finished work grouped: "3 conversations finished" | `GROUP_FINISHED` + `refreshGroupSummary` (exists) | **Adapt.** Group summary copy "N conversations finished"; its lines name each conversation (stored in a private extra), not "Finished: …" repeated. Single finished alert: **"Finished: <chat title>"**, body = first line of the reply. |
| "While you were away" wording | Finished alerts post only while the app is backgrounded (`_lifecycleWasBackgrounded`) | **Adapt.** True by construction, so the summary says the count only; "While you were away" would be a second phrase for the same fact. |
| "4 files changed" on a finished item (concept A) | Not read when the alert posts (the diff is fetched in the app) | **Reject.** No source at post time; the reply's first line is the plain result. |
| "Waiting 18 min" | Android's own `when` stamp | **Adapt.** Android shows the post time; nothing to add. |
| Stopped conversation | `stoppedReasonToken` (reason token only) | **Adapt.** "Stopped: <chat>" + the reason sentence; "Tap to review." suffixes removed. Never red. |
| Lock screen | `setPublicVersion` (exists) | **Keep.** Count-only fixed copy ("A request needs you", "A conversation finished"); no command, chat or project on the lock screen. |
| Agent identity | persona registry | **N/A.** Native copy names no agent except the existing validated short name fallback; nothing new invented. |
| App-styled cards, mint buttons, big type, aurora (both concepts) | Android draws notifications | **Remove** (image artifacts). |

Capability gating: notifications are posted only when `platformCapabilities.supportsNotifications`,
the OS grant and the per-server rule allow it (unchanged). Allow once / Reject appear only for an
ask the poster can answer (`allowActions`, set false for watched servers, which have no reply
path), never by flavor.

## 2. Final copy per notification type

Android draws every notification; the app owns the words and the actions. No emulator is
available on the build server, so this table stands in for renders. Each row has a JUnit test in
`android/app/src/test/.../StatusNotificationModelTest.kt` (`AlertCopyTest`, `AlertButtonsTest`)
and the Dart half in `test/notification_text_test.dart` / `test/notification_moments_test.dart`.

Subtitle (Android's header line, beside the app name) is always **server · project** where both
are known, e.g. "MacBook · api-server"; AI Team keeps the server name alone.

| Type | Title (shade) | Body | Actions | Lock screen |
|---|---|---|---|---|
| Permission: command | Run npm install? | `npm install --save left-pad` (masked) | Allow once (asks to unlock) · Reject | A request needs you |
| Permission: edit a file | Edit settings.dart? | `lib/ui/settings.dart` | Allow once · Reject | A request needs you |
| Permission: read a file | Read .env? | `config/.env` | Allow once · Reject | A request needs you |
| Permission: folder outside the project | Work in api? | `/srv/api` | Allow once · Reject | A request needs you |
| Permission the shade cannot show (no command or file; watched server; repost after a failed answer) | Allow OpenCode to continue? | — | Open | A request needs you |
| Question with words | Which branch should I use? | main · develop (only ≤3 short choices) | Open | A request needs you |
| Form / question without words | OpenCode has a question for you | — | Open | A request needs you |
| Finished | Finished: Add combo counter | First line of the reply | — (tap opens) | A conversation finished |
| Several finished (group) | 3 conversations finished | One line per conversation name | — | OpenCode · 3 conversations finished |
| Several waiting (group) | 2 need you | One line per question | — | OpenCode · 2 need you |
| Stopped | Stopped: Fix login | The provider rejected the key. Sign in again to continue. (reason token only) | — | OpenCode · a chat stopped |
| Check-in | OpenCode is still working | Open the conversation to check in. | — | same |
| Test (Settings › Send a test notification) | Notifications are on | Questions and finished work will arrive like this. | — | same |
| Usage / AI Team | unchanged fixed copy | unchanged | — | unchanged |

"OpenCode" is the validated short agent/server name when the backend is an agent host, else
"OpenCode". Never red: no `setColor`; the error kind keeps `CATEGORY_ERROR` only for Android's
ranking.

Arabic (values-ar): تشغيل npm install؟ · تعديل settings.dart؟ · السماح لـ OpenCode بالمتابعة؟ ·
اكتمل: … · توقف: … · السماح مرة واحدة / رفض / فتح · اكتملت 3 محادثات · طلب ينتظرك.

## 3. In-app Notifications page

The only part of this pick the app draws. Renders (412×915 dp at 2×, real fonts, blurred
shadows) by `tool/capture/vision_notifications_test.dart` into
`/home/eslam/vision-assets/out/vision-notifications/`.

| Pass | Seen | Changed |
|---|---|---|
| 1 | New detail lines cut ("Several come …"); "Wi-\nFi" broken title; "runs" engine word; the off state's only action a tertiary word | Shorter details; "Check servers on Wi-Fi only"; "Check in on long work"; off-state notice + one solid mint "Turn on notifications in Android" |
| 2 | One-word last lines ("now.", "hours.", "Wi-Fi.") | "Shows whether Android lets them through.", "Silent during the hours you choose.", "Checks pause without Wi-Fi." |
| 3 | 320 dp at 2× text: icon tiles + switch squeeze titles to "Finished convers…" and break "con/versation" mid-word | The page's icon tiles step aside from 2× text (screen-local; the kit row is unchanged for other screens) |
| 4 | Screen standard (first catalog run): rows of three lines, an Arabic detail cut off | Every detail one line: "Several arrive as one notification.", "Answer them from the notification.", "A reminder after the time you set.", "Set in Usage › Alerts.", "See if Android lets them through." |
| 5 | 320 dp at 2×: switch rows still cut "Finished conversati…", "Approvals and questi…" | Titles "Finished work" and "Questions for you" (the pick's own words) |

Final images: `main-dark.png`, `main-light.png`, `notifications-off-dark.png`,
`small-320-text2-dark.png`, `arabic-rtl-dark.png`; passes in `pass1/`–`pass3/`.

Open finding: the screen standard counts 9 rows in the first view (limit 7). Getting under it
means moving two settings (Usage alerts link, Send a test notification) off this page, which is a
product decision outside this pick; left for the owner.

## 4. Verification

- Kotlin: `:app:testReleaseUnitTest` (`AlertCopyTest`, `AlertButtonsTest`,
  `StatusNotificationModelTest*`: 38 tests) and `:app:lintRelease` pass; detekt
  (`tool/qa/kotlin_static_analysis.sh`): zero new findings.
- Dart: `flutter analyze` clean. Affected tests pass: notification_text, notification_moments,
  background_action, background_coding_alert, background_live, team_gate_rows_notifications,
  native_notification_copy, release_blockers, notifications_settings_screen, settings_search_rows,
  settings_hub.
- Widgetbook: new use-case `Settings › Notifications` (allowed / Android blocks); removed from
  `coverage_baseline.json`.
- Not run: the full suite (by instruction), no emulator. `tool/capture/check_in_reminders_test.dart`
  fails 6 scenes at base `433981f66` as well (pumpAndSettle timeout, inbox text), so not caused
  here.

## 5. Shipping notes

Native and Dart change together (new `askAction`/`askTarget` and `test` kind on `oc/background`):
this needs a new release, not a Shorebird patch. A Dart-only patch on the old native side would
show the old fixed titles and the test notification would report "not delivered".
