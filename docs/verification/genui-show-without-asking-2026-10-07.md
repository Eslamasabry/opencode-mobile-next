# Agent cards: show without asking — 2026-10-07

Base: `6015aea4`, branch `feat/genui-be`. Coordinator reported APK 2168
rendering a real Claude card and Needs you row, but Asks-first prompted for
`mcp__oc-ui__show`. That is the user-reported trigger, not a new runtime test of
this patch. No APK was built or device modified for this fix.

## Behavior

The profile registration now adds exactly `mcp__oc-ui__show` to
`permissions.allow` in the profile's `CLAUDE_CONFIG_DIR/settings.json`.
Unrelated settings, ask/deny rules and permissions remain intact. An optional
private ownership flag in `owners.json` records whether the app added the rule.
Existing manifests migrate on enable; disable removes only an app-owned rule
and resets that ownership. A preexisting user-owned exact allowance is preserved.
Verification requires the allowance; reinstall repairs a missing rule. Atomic
writes, symlink checks and failed-install rollback apply to the settings too.

A second safeguard handles already-running sessions: the controller supplies
Paseo a profile-qualified callback. Only the raw exact tool name, tool-kind
request, known Claude session in the same directory, connected pinned daemon,
current epoch and current enabled/qualified profile can take this path. The
request's provider, if present, must also be Claude. The callback is bound for
both the stable phone feed and the active conversation backend.

The existing permission transport sends `once`, never `always` or
`updatedPermissions`. The request is hidden only while that exact reply is
queued; source/setting changes cancel admission. Failed sends remain visible
and are not automatically retried. Hydrated requests and late qualification
are covered. Other tools and unqualified sources keep normal approvals.

The [Claude settings documentation](https://code.claude.com/docs/en/settings)
defines user settings and `CLAUDE_CONFIG_DIR`. The
[permission documentation](https://code.claude.com/docs/en/permissions#mcp)
defines exact MCP tool rules and explains that explicit ask rules precede
allow rules, which is why the live fallback is also needed.

## Failing-then-passing evidence

All commands used the pinned Shorebird Flutter with `--no-pub --concurrency=1`
through `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test --`.

Before their corresponding implementations:

- Installer `--plain-name 'Claude show preallow'`: 4 failures (missing exact
  rule, missing-rule verification accepted, malformed settings accepted, legacy
  manifest migration missing), 2 existing-permission/rollback controls passed.
- Installer ownership-reset regression: expected independent user rule after
  second disable, actual rule removed; failed before the ownership reset fix.
- `test/gen_ui_show_permission_test.dart`: 5 positive cases failed with no
  reply; 4 source/name negative cases passed before interception implementation.
- Controller `--plain-name 'qualified cards'`: the feed emitted no reply before
  callback binding. Final integration covers feed and active chat, Asks-first
  remaining selected, other-tool approval and immediate disable revocation.
- Failed-send retry regression: expected one attempt, actual two after a
  listener rebound the callback; failed before the per-request attempt latch.

## Final checks

Candidate SHA-256 (sorted changed/new Dart paths + NUL + contents + NUL):
`134af15e1bd7f4363f9913a09d4fe9522f6a9d4edb450673eb34e567acbe7a2d`.

```text
flutter test --no-pub --concurrency=1 test/gen_ui_show_permission_test.dart test/gen_ui_install_test.dart test/phone_agents_controller_test.dart
flutter test --no-pub --concurrency=1 test/paseo_gateway_test.dart test/session_auto_approval_controller_test.dart test/gen_ui_controller_test.dart
flutter analyze --no-pub
```

| Test file | Passed |
|---|---:|
| gen_ui_show_permission_test.dart | 13 |
| gen_ui_install_test.dart | 30 |
| phone_agents_controller_test.dart | 25 |
| paseo_gateway_test.dart | 37 |
| session_auto_approval_controller_test.dart | 19 |
| gen_ui_controller_test.dart | 14 |

Total: **138 passed**, no skips or failures. Full analyzer: no issues.
Pinned Dart formatting used `--language-version=3.10`; `git diff --check` clean.
Changed Dart files remain below 1,500 lines. Frozen GenUI types and frontend
files are unchanged. No full-suite run, commit, push, APK build, signing,
release, or new emulator qualification was performed.
