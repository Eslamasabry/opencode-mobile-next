# BD9 — device CI blocked

Finish line: locally prove a nightly job's PhoneEngine instrumentation and launch → server → conversation-list smoke on emulator-5554 before enabling it. Non-goal: launching paid/live model work, inventing device evidence or silently replacing the existing instrumentation with a fake test.

Read-only device session under `flock /home/eslam/Storage/tmp/oc-emulator.lock`:

- emulator-5554 is ready.
- Installed stable app: versionName 1.2.0, versionCode 2187.
- Installed instrumentation targets only `io.github.eslamasabry.opencode_mobile.preview`; no matching stable androidTest APK is installed.
- This branch is 1.2.0+52, has no android/key.properties and no android/gradlew.

Current source prerequisite: `tool/qa/phone_engine_acceptance.sh` requires a matching app/test APK, Ubuntu initialized, the phone loopback server, an authenticated provider/model, explicit isolated/stable QA selection and `--allow-model-spend`. PhoneEngineAcceptance starts real engine/server work; this is not an offline launch smoke. Model spend/live-server changes and signing secrets are not authorized here. A lower build number cannot replace the stable installation using the permitted `install -r`; uninstall/downgrade is forbidden.

BLOCKED on coordinator/runtime lane: supply an installable higher-version candidate with the same test signer and permitted signing setup, and a credential-free bounded PhoneEngine acceptance mode / CI Ubuntu provisioning contract, or separately authorize a dedicated provisioned model fixture and spend. Then add integration_test dependency/real UI smoke and android-device.yml nightly job, run those exact commands under build/device locks and record safe logs/screenshots. BD5 static-analysis validation alone does not establish those prerequisites. No speculative runnable workflow or mock device pass was added; no signing/build/install/model command ran. No dependency on frontend code is hidden by a scaffold.
