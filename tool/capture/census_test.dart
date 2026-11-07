// The screen census: every page of the UI ledger
// (docs/design/ui-ledger/ledger.json) the harness can reach, rendered dark,
// 412x915 dp at 2x with the app's real fonts, for the owner's one-by-one
// review. Images: docs/qa/screen-census/<area>/<page-id>[--<state>].png.
// Manifest: docs/qa/screen-census/manifest.json. Guide:
// docs/qa/screen-census/README.md.
//
//   flutter test tool/capture/census_test.dart
//   flutter test tool/capture/census_test.dart --dart-define=CENSUS_AREA=a-shell
//   flutter test tool/capture/census_test.dart \
//       --dart-define=CENSUS_AREA=a-shell --dart-define=CENSUS_PAGE=activity
//
// Lives under tool/ so `flutter test` on test/ never runs it. A shot that
// fails records its error in the manifest (`notRendered: "render failed: …"`)
// and the run moves on; the failing shots are also printed as `census: …`
// lines in the test output.
import 'census/areas/a_shell.dart';
import 'census/areas/b1_chat_screen.dart';
import 'census/areas/b2_chat_screen.dart';
import 'census/areas/c_chat_compose.dart';
import 'census/areas/d_chat_sheets.dart';
import 'census/areas/e_workspace.dart';
import 'census/areas/f_files_review_terminal.dart';
import 'census/areas/g_servers.dart';
import 'census/areas/i1_team_core.dart';
import 'census/areas/i2_team_sheets.dart';
import 'census/areas/j1_settings_more.dart';
import 'census/areas/j2_library.dart';
import 'census/areas/k_session_misc.dart';
import 'census/census_core.dart';

void main() => runCensus([
  aShellArea,
  b1ChatScreenArea,
  b2ChatScreenArea,
  cChatComposeArea,
  dChatSheetsArea,
  eWorkspaceArea,
  fFilesReviewTerminalArea,
  gServersArea,
  i1TeamCoreArea,
  i2TeamSheetsArea,
  j1SettingsMoreArea,
  j2LibraryArea,
  kSessionMiscArea,
]);
