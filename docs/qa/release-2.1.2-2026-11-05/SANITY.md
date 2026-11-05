# Release sanity 2.1.2+60 (candidate 46a381155, build 4505)

APK: /home/eslam/Storage2/wt/rel-212/build/vps-apk/app-release-4505.apk (103 MB), signer
1DE5BF08...D60C (matches the local release key), GeneratedPluginRegistrant present.
/home/eslam/Storage2/wt/apk-share does not exist, so no copy was made.
Base for the update test: app-release-4501.apk (2.1.0 line).
Devices: OC_API37 (Android 17, Landlock tier), OC_API35 (Android 15, proot tier), both x86_64 AVDs.

| # | Check | OC_API37 | OC_API35 | Evidence (shots/) |
|---|-------|----------|----------|-------------------|
| 1 | Update in place 4501 -> 4505 | PASS (note 1) | PASS | 01, 02, 30 |
| 2 | New conversation: Add an agent card, setup, back keeps draft, RTL, label | PASS | not required | 03, 04, 05 |
| 3 | Built-in Linux: setup, stop/start phone server, terminal, manager pages | PASS | PASS (setup, stop/start, terminal) | 14-19, 31-33 |
| 4 | Phone agents: Antigravity listed, install, sign-in screen, remove, others | PASS (note 2) | PASS (install + remove) | 06-13, 34, 35 |
| 5 | Git: sign-in entry, remote step, existing actions | PASS; repo picker BLOCKED (owner GitHub sign-in) | not required | 20-25 |
| 6 | Smoke: Settings groups, Arabic/English, chat stream + Stop, notifications, widgets | PASS | not required | 26-28 |

## Details
1. Both AVDs already held newer builds (4523 / 4513), so Android refused to downgrade; the app was
   uninstalled and 4501 installed fresh, then data was created and 4505 installed with `install -r`.
   Survived: phone server connection, both chats, Light appearance, Arabic language, built-in Linux,
   project. No crash. The draft typed on 4501 was not actually captured (my tap missed the composer), so
   draft survival across the update is not proven; draft persistence was verified on 4505 itself (leave
   and reopen the chat keeps the text).
2. Antigravity (agy) shows in the agents list with Gemini CLI still listed. Gemini was kept on purpose
   (docs/qa/issue-116-antigravity/README.md: retained for existing use, with migration guidance), so
   "instead of Gemini" in the brief is not what the build does. Gemini install and removal also work and
   the Gemini sign-in sheet tells the user to install Antigravity. agy installs (66 MB), the app runs its
   check, then shows "Sign in with Antigravity" with plain-words guidance and an "Open sign-in page"
   button (goes through the external-link confirm for accounts.google.com). The terminal shows agy's
   Google sign-in URL and "paste the authorization code" prompt. Stopped there: Google sign-in needs the
   owner. Removal reports "Antigravity removed. Freed 239 MB." and the list returns to Not installed.
   No raw error text seen anywhere.
- Check 2: card has content-desc "إضافة وكيل" (Arabic) and sits in the agent row beside OpenCode;
  Claude Code drops to its own row. Back from setup returns to the draft with "Keep this draft" intact.
- Check 5: the Git sheet offers "Sign in to GitHub" (device-code flow); the repository picker only
  appears once signed in (source: git_sheet_steps.dart), so it could not be exercised; covered by widget
  tests only. Existing actions work: sheet opens, shows "Save 1 file" for an untracked file, save opens
  the "Your name on saves" identity step.
- Check 6: five top-level groups (Server, Agent, Conversations, This app, Help) all open; language
  switched Arabic then English; chat replies from the free Big Pickle model, Stop ends a long reply with
  "You stopped this reply."; notifications page loads. The app registers seven launcher widget
  providers (dumpsys appwidget), not two; all present. I did not open the launcher picker.

## Environment notes (not app defects)
- adb screencap aborts on these AVDs (hasReadColorBufferDma assertion); shots use `adb emu screenrecord screenshot`.
- The same gfx assertion twice crashed system_server (TaskSnapshotPer) when the app left the foreground,
  and once the whole emulator died; the app and its data recovered each time.
- Observations, unchanged by this release as far as the diff shows: This phone page says "Connected" under
  OpenCode 2 while the server is stopped; "Agents on this phone" summary lists Claude Code while the agents
  list says Claude Code is Not installed. Worth a look but not regressions I can attribute to 4505.

VERDICT: CERTIFIED
