# Android smoke matrix (Android 12 to 16)

Covers issue #8. Run this before every sideload release and tick the "Smoke
matrix results" box in the pull request
([template](../../.github/PULL_REQUEST_TEMPLATE.md)). Record results in
`docs/qa/smoke-<version>-<YYYY-MM-DD>/README.md` (format:
[README.md](README.md)); paste the table below with PASS, FAIL or N/A and a
link to evidence.

Use a release build signed with the key the maintainer's phone trusts. Debug
builds do not work with the Shorebird engine.

## Devices

| Android | API | Where | Notes |
|---|---|---|---|
| 12 | 31 | emulator | first version with authenticated notification actions |
| 13 | 33 | emulator | notification permission prompt appears |
| 14 | 34 | emulator | foreground service type enforcement |
| 15 | 35 | emulator | `dataSync` 6 hour limit |
| 16 | 36 | emulator | |
| 16 QPR | 36.1 | emulator | promoted-notification path |
| any | | physical device 1 | the maintainer's phone |
| any | | physical device 2 | a second vendor, ideally Samsung or Xiaomi |

At least two physical devices. Record model, Android version, ABI, and
whether Termux is installed.

## Checklist (per Android version)

Copy this block once per version. Each line has a pass condition.

### 1. Install and upgrade

- [ ] Clean install opens to first run with no crash or ANR.
- [ ] Upgrade over the previous release keeps profiles, the server password and
      queued drafts (signed with the same certificate; compare
      `apksigner verify --print-certs` with the known fingerprint first).
- [ ] After a Shorebird patch the app starts on the patched code (Settings,
      About shows the patch number).
- [ ] Uninstall removes everything; reinstall starts clean.

### 2. Connect

- [ ] Add a server by HTTPS URL with credentials: connects, status shows
      connected.
- [ ] Add a server by loopback or adb reverse: connects.
- [ ] Plain HTTP to a private network address: asks for confirmation, refuses
      to connect until confirmed, connects after.
- [ ] Wrong password: plain-language error, no raw exception text.
- [ ] Airplane mode on then off: the app shows offline, then reconnects and
      refetches without duplicating messages.

### 3. Chat

- [ ] Cold start to chat open under 3 s on the physical devices
      (Settings, App diagnostics, Performance).
- [ ] Stream a long answer (more than 2 minutes): scrolls, no jank, no ANR.
- [ ] Rotate and change font scale to 2x: composer and send stay usable.
- [ ] Send while offline: a visible queued draft that sends once on reconnect.
- [ ] Arabic locale: layout mirrors, composer and transcript readable.

### 4. Permission

- [ ] A permission request shows the card with the composer focused.
- [ ] Allow once and Deny both reply exactly once.
- [ ] A request resolved elsewhere dismisses the card without an error.

### 5. Background

- [ ] Turn on Keep live in the background; the ongoing notification appears.
- [ ] Background the app for 10 minutes with a run active; return: transcript
      is current, no duplicate text.
- [ ] Android 15+ and 16: after the `dataSync` limit the notification is
      removed and the app says live mode stopped (see
      [doze-standby.md](doze-standby.md)).
- [ ] The in-app battery prompt appears when exemption is missing.

### 6. Notifications

- [ ] Permission notification posts with Allow once, Deny and Reply.
- [ ] On a locked device, Allow once asks to unlock (Android 12+).
- [ ] Tapping the notification opens the right session.
- [ ] A request answered in the app removes its notification.
- [ ] Notification permission denied (Android 13+): the app explains and
      continues to work in the foreground.
- [ ] Home widget and Quick Settings tile show the correct count.

### 7. Built-in Linux

- [ ] Setup downloads and unpacks; progress survives backgrounding.
- [ ] Interrupt setup (kill the app mid-way): it resumes, not restarts.
- [ ] The in-app server starts, a prompt gets a reply.
- [ ] Storage: low disk shows a readable message, not a stack trace.
- [ ] Remove the built-in server: space is returned (Manage storage).

### 8. AI Team

- [ ] Team engine reports readiness and its protection tier.
- [ ] A small task runs to a merged result on a test project.
- [ ] The process count stays within the platform limit during a run.
- [ ] Turning AI Team off stops its processes.

### 9. Storage access

- [ ] Opening a project under `/sdcard` asks for All files access and explains
      why.
- [ ] Denied: the project is not shown as empty; it says access is needed.
- [ ] Granted: files list and edit; a new shared folder asks to restart the
      phone's OpenCode when AI Team is on.
- [ ] A project in app storage never asks for All files access.

### 10. Crash and ANR baseline

- [ ] `adb logcat -b crash -d` is empty after the run.
- [ ] `adb shell dumpsys activity processes | grep -i anr` shows none.
- [ ] No "app isn't responding" dialog at any step.

## Also covered elsewhere

Widget, voice model download and the full battery protocol live in their own
records: [doze-standby.md](doze-standby.md) for Doze and standby buckets.
Run the voice model download once per release on one physical device and note
it under section 3.

## Result table

| Step | 12 | 13 | 14 | 15 | 16 | 16.1 | Phone 1 | Phone 2 |
|---|---|---|---|---|---|---|---|---|
| 1 Install and upgrade | | | | | | | | |
| 2 Connect | | | | | | | | |
| 3 Chat | | | | | | | | |
| 4 Permission | | | | | | | | |
| 5 Background | | | | | | | | |
| 6 Notifications | | | | | | | | |
| 7 Built-in Linux | | | | | | | | |
| 8 AI Team | | | | | | | | |
| 9 Storage access | | | | | | | | |
| 10 Crash and ANR | | | | | | | | |
