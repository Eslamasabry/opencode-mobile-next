# Good first issues

Five small, self-contained tasks found by reading the repository on
2026-10-02. Each one names the file, what is wrong, and how you know it is
done. None needs a device, a server, or access to credentials. Read
[CONTRIBUTING.md](../../CONTRIBUTING.md) first for the gates and the
boundaries (kit-only UI, gateway boundary, localization).

If you take one, say so on the matching issue (or open one titled as below
and label it `good first issue`) so two people do not do the same work.

## 1. Reconcile the Flutter pin in AGENTS.md

- **Where:** `AGENTS.md` line 9 says Flutter **3.47.1**. `CONTRIBUTING.md`,
  `pubspec.yaml`, `docs/desktop.md` and the desktop and SDK workflows
  (`.github/workflows/desktop-linux.yml`, `desktop-windows.yml`,
  `sdk-regenerate.yml`) say **3.47.2**.
- **Task:** run the pinned Flutter from the Shorebird cache with
  `flutter --version`, decide which number is true, and fix the document
  that is wrong. If the cache path hash in `AGENTS.md` is also stale, fix it.
- **Done when:** every mention of the pin in the repository agrees. A
  `grep -rn "3\.47\." --include=*.md --include=*.yml --include=*.yaml .`
  shows one version.
- **Size:** docs only. No Flutter tests.

## 2. Refresh docs/localization-todo.md

- **Where:** `docs/localization-todo.md` opens with "Status: English only"
  and "only about 30 strings go through it". The app now ships
  `lib/l10n/app_ar.arb` next to `app_en.arb`, and
  `test/l10n_coverage_test.dart` records a baseline of one hardcoded
  literal.
- **Task:** rewrite the status paragraph and tick the work items that are
  done (the Arabic locale, the coverage ratchet). Leave the genuinely open
  items (plurals and dates through `intl`, golden tests per locale) open.
  Check each claim against the code before ticking it.
- **Done when:** the page matches what `lib/l10n/` and the coverage test
  show today.
- **Size:** docs only.

## 3. Move the last hardcoded string in chat_screen.dart into the ARB files

- **Where:** `test/l10n_coverage_test.dart` ends with
  `const _baseline = <String, int>{'lib/ui/screens/chat_screen.dart': 1};`.
  That is the one user-visible literal left under `lib/ui`.
- **Task:** find it (the test prints the per-file count; temporarily lower
  the baseline to 0 and the failure message points at the file), add the
  key to `lib/l10n/app_en.arb` with an `@` description and to
  `lib/l10n/app_ar.arb`, use `AppLocalizations`, then set the baseline to
  empty.
- **Done when:** `flutter test --concurrency=1 test/l10n_coverage_test.dart`
  passes with an empty baseline. Run `flutter gen-l10n` once, after the copy
  is settled.
- **Note:** `chat_screen.dart` is a single-owner file. Check with the
  maintainer before editing it so you do not collide with open work.
- **Size:** about 10 lines plus two ARB entries.

## 4. Test and document the one deprecated server call

- **Where:** `lib/api/opencode_api.dart` around line 1156 is the only
  `// ignore: deprecated_member_use` in the client. It is the old
  `permissionRespond` route, reached from `_respondLegacyPermission` only
  as a fallback: `_canUseLegacyPermissionReply` allows it on a 404 or 405
  that is not a `PermissionNotFoundError`, when the request carries both a
  legacy session id and a legacy permission id.
- **Task:** add a comment above the ignore saying which server versions
  still need this route. Then add a unit test (fakes as in
  `test/chat_permission_test.dart`, or a Dio adapter) covering the four
  cases of `_canUseLegacyPermissionReply`: 404 with legacy ids falls back,
  405 with legacy ids falls back, 404 `PermissionNotFoundError` does not,
  and 404 without legacy ids does not.
- **Done when:** the comment names a version or capability, the test fails
  if the fallback condition changes, and `flutter analyze` is clean with no
  new ignores.
- **Size:** one comment, one test file.

## 5. Refresh the HANDOVER.md "Latest" section

- **Where:** `HANDOVER.md` starts with "Latest (2026-09-11, later)" and
  describes checkpoint 1.0.42+43 and "Sprint C next". The branch history
  since then (releases, built-in Linux, AI Team phone engine) is not
  reflected.
- **Task:** read `git log --oneline -40` and `CHANGELOG.md`, then replace
  the stale "Latest" block with a short, accurate one (current version,
  what is merged, what is next). Do not delete older sections; move them
  under a "History" heading if they are no longer current.
- **Done when:** a new contributor reading the first screen of the file gets
  the current state. No claims you did not verify in the log.
- **Size:** docs only.

## Not on this list on purpose

Anything touching credentials, signing, releases, notifications that
approve commands, or the OpenCode 2 event reducer. Those need the
maintainer in the loop; see [SECURITY.md](../../SECURITY.md).
