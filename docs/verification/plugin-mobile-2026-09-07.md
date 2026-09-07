# Personal plugin actions and bundled task view

Finish line: users link current server commands to a plugin for one location,
restore those personal links after restart, and review chat/arguments before
running; task tool results use a bounded, first-party mobile view.
Non-goal: discovered remote UI, inferred plugin attribution, install/enable
controls, or automatic command execution.

## Source and behavior

The existing verified plugin inventory supplies ID/source/status only. It does
not supply command attribution. `Link commands` therefore makes an explicitly
personal association. Inventory and command lists are fetched again before
opening review and immediately before dispatch. A removed command, failed
plugin, connection/location change, or removed profile refuses dispatch. The
shared command dialog retains its existing destination, arguments, retry and
session-selection behavior. Opening a link performs read-only validation and
opens review; only Run may create a session or invoke the command.

`oc.pluginCommandMappings.<profileId>` stores schema version 1. Scope hashes bind
links to endpoint, account name, directory and workspace without storing those
location values in the mapping blob. Existing per-profile deletion sweeps own
the key; before/after-write guards prevent resurrection during deletion. Writes
serialize, reload and merge to preserve unrelated links from overlapping views.
Each profile holds at most 64 plugin/location mappings and each mapping at most
16 commands; names are at most 200 characters. Existing storage needs no
migration. Clearing a plugin's selections removes that mapping. The confirmed
Clear personal links action removes the entire profile's mapping history,
including unavailable plugins and previous locations, through the same durable
write queue. A failed platform removal preserves the visible links and reports
failure; other profiles are unaffected.

`MobileTaskView` is an app-bundled schema (`opencode.todo`, version 1), adapted
from the existing todo tool payload. It accepts at most 64 known-status tasks,
1,024 characters per task and 32,768 total characters. Unsupported versions,
actions, oversized content and unknown statuses use the existing plain output
or bounded plain task fallback. Server-provided text is ordinary Text, never
Markdown actions or executable code. The only control filters the local view;
it neither changes task statuses nor contacts a server. The view labels statuses
as server-reported, rather than treating them as independently verified success.

## Privacy and accessibility

Mappings contain plugin IDs and command names, never arguments, credentials or
provider configuration. No downloaded code, credential forms, arbitrary URL
launches or new network endpoints are introduced. Run uses the domain gateway.
Plugin rows remain flat; task status has a visible text label as well as an icon.
The command picker scrolls at large text sizes; Material controls retain keyboard
and screen-reader behavior. No nested cards were added.

## Checks prepared for the integration owner

- `flutter test --concurrency=1 test/plugin_command_mappings_test.dart test/mobile_task_view_test.dart test/plugins_screen_test.dart test/library_commands_test.dart test/tool_card_test.dart`
- `flutter test --concurrency=1 tool/capture/plugin_mobile_test.dart`
- Integration analyzer and final complete serial manifest gate.

Tests cover restart/scope isolation, overlapping writes, failed saves, deletion
races, explicit linking/review, command removal before and after review, bounded
schema fallback, local-only filtering, RTL and large text. Capture fixtures are
synthetic and write personal-link/task-view light and dark PNGs under
`docs/qa/plugins/`. The implementation worker formatted changed Dart files but
did not run tests/builds, call a live server, commit, sign or publish.

Plugin long-press shortcut integration remains an F6 dependency. The mapping
store and shared review dialog are available to that owner; shortcuts must open
review and revalidate current scope instead of directly executing commands.

Root verification: the five focused files passed 38 tests. After adding the
confirmed profile-wide clear action, the two affected files and capture harness
passed 19 tests, including both themes. The integration analyzer is clean.
These are overlapping focused runs, not complete-suite or native-device proof.
