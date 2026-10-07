# FA5 fallback agent names

Candidate: sol/ba-agent-cert after merge of feat/genui-fe at 7f7293f9.
Finish line: the unchecked Cards fallback names the managed agents, including
OpenCode 2, without granting readiness. Non-goal: changing frontend copy or
qualifying a runtime.

The fallback now supplies the same adapter set requested by the managed
installer as `GenUiSetupUnavailable.affected`. `agents` remains empty.
The existing FA5 frontend consumes affected names without further UI changes.

Pinned Flutter 3.47.1, machine_lock test, --no-pub --concurrency=1:
- New OpenCode 2 controller regression: passed.
- Reverted just the fallback fix: regression failed (affected was empty).
- Restored: gen_ui_controller_test.dart + gen_ui_install_test.dart: 77 passed.

No UI/native changes, device session, APK build, signing or release.
Coordinator owns screenshot and full-suite integration coverage.
