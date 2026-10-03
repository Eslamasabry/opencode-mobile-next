# Agents on phone — C5 backend verification

> Policy update 2026-10-03: resume-only admission is superseded. Unverified agents
> are shown and selectable for new chats after install/sign-in gates; reopening
> requires the explicit "Starts a new chat" acknowledgement. Historical checks
> below describe their original candidate. See the current frontend contracts.

Branch `codex/agents-on-phone`, base `ff04beb6`; 2026-10-03.
Finish line: callable pinned phone-agent setup, private Claude subscription login,
safe runtime rows and one merged chat-feed contract ready for Claude's controller/UI.
Non-goals: kit UI/controller edits, provider credentials in the app, releases,
public hosting, or claiming unmeasured device/account/resume qualification.

## Implemented and admission

Nine catalog descriptors preserve real arm64/x64 pins and unknown size values.
The shared setup engine installs oc, Node (needed by Paseo), locked Paseo 0.9.2
and catalog agents; only the account/bootstrap step uses guest root. Installer,
Paseo and CLI launches use guest UID/GID 1000. PRoot identity mapping is not an
OS sandbox; its root view reports platform-owned files as UID 0, so bootstrap
cannot require a real `chown` to persist UID 1000. The phone check tests actual
shared project read/write instead. oc's `/root` is masked, with the existing
native projects storage bound underneath; root OpenCode keeps its original bind.

The daemon is loopback-only, relay/proxy disabled, password in the profile's
Keystore slot. Private stdout/stderr never reach setup or terminal transcripts.
Claude login uses the actual pinned `auth login --claudeai` stdin contract and
sanitized subscription status; no new OAuth client or API-key input. Fresh
phone Claude chats request the host's `default`/ask mode and fail if absent.
Delete drains captured processes before removing profile credentials/home.
Deleted native owners are intentionally tombstoned until app process restart;
clear-all-sign-ins needs controller client closure and an app restart action.

All capability proof starts false. The four-step ARM64 check proves install,
version/project access, daemon and authenticated hello only. ACP load/auth
negotiation is still absent from pinned Paseo 0.9.2: candidate installation and
discovery do not unlock generic ACP dispatch. Other vendors' phone sign-in
handlers remain unavailable. Runtime plan-reset times stay unknown unless the
host explicitly supplies them; login status cannot prove a plan reset time.

## Checks

Pinned `flutter pub get` completed; Dart 3.10 formatting and `git diff --check`.
19 focused Flutter files pass (265 tests): catalog, runtime gates, private auth,
agent/host scripts, host lifecycle/deletion, Paseo feed, merged feed, existing
Paseo/ACP clients, setup engine, profile deletion/storage/sign-in reset, legacy
Claude scripts, file-size ratchet and architecture boundaries. See local
`build/traycer/c5-focused.log`; no handwritten lib file exceeds 1,500 lines.
Whole-repository pinned `flutter analyze --no-pub`: clean, no added ignores.
Native private-auth harness: 10 passing fake-process scenarios, JDK 17/Kotlin.
Android `:app:compileReleaseKotlin`: passed with Gradle 9.5.0, one worker,
`-Ptarget-platform=android-arm64`, 2 GiB heap and in-process Kotlin compiler.
This is compilation, not installation, signing, physical-device or runtime proof.

The temporary x64 PC npm closure loaded node-pty/sherpa-onnx/esbuild and reported
Paseo 0.9.2; PC Node was 24.11.1, not the phone's pin. No global installs,
real account login, emulator/ARM64 run, live agent prompt or restart-load proof.
Full Flutter suite is left to the coordinator; no push/tag/PR/signing/release.

## Controller handoff

The existing phone profile retains OpenCode; switch its gateway behind the
agent chip, never create another phone profile. Supply domain host/auth rows,
load and verify native provider resume before granting `resumeVerified`, seed
draft provider identity, merge scoped feed sources and keep route identity.
Before deletion/reset: close auth, cancel owned setup, stop/dispose host and
close feeds; then ProfileStore drains native home and sweeps profile keys.
Detailed UI/copy/qualification contract: [agents frontend contract](../design/agents-frontend-contract.md).

## Owner policy amendment — 2026-10-03

Unverified restoration no longer hides or blocks new chats. Catalog/phone/host
rows keep false resume proof, "Can't reopen old chats" and "Starts a new chat".
Install/sign-in/readiness and native-provider identity guards remain. Reopened
ACP or missing-handle rows require explicit acknowledgement and a different
new draft ID; current live chats continue, and disconnect retires that admission.
Both frontend contracts specify the controller/UI action; no UI or connection
files were edited. Nine focused files/130 tests pass on this amendment candidate,
including size/architecture gates; pinned whole-repository analysis is clean.
Local transcript: `build/traycer/c5-policy-focused.log`. This is fake verification,
not new physical-device, account, session-load, deployment or release proof.
