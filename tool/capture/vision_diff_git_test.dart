// Vision slice evidence: the Changes screen, the Git sheet and a permission
// ask on a list row, one full phone screen per image, light and dark. The
// data is invented and nothing touches a server.
//
// Run with:
//   flutter test --no-pub --concurrency=1 \
//     --dart-define=OUT=/home/eslam/Storage2/launch-footage/vision-diff-git/after \
//     tool/capture/vision_diff_git_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/git_page_controller.dart';
import 'package:opencode_mobile/state/git_workspace.dart';
import 'package:opencode_mobile/ui/git/git_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show permissionRequestCard;
import 'package:opencode_mobile/ui/screens/review_workspace.dart';

import '../../test/git/git_fixtures.dart' as fx;
import '../../test/git/git_test_support.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-diff-git/after',
);

const _status =
    '# branch.oid b196b9f452aac7110d9a5277edd4d698e72d9872\x00'
    '# branch.head main\x00'
    '# branch.upstream origin/main\x00'
    '# branch.ab +1 -0\x00'
    '1 .M N... 100644 100644 100644 587be6b4 587be6b4 game.js\x00'
    '1 .M N... 100644 100644 100644 587be6b4 587be6b4 index.html\x00';

String _snapshot() => envelope({
  'status': ok(_status),
  'unstaged': ok('30\t5\tgame.js\x008\t2\tindex.html\x00'),
  'staged': ok(''),
  'log': ok(fx.log),
  'branches': ok(
    'refs/heads/main\x1f97f3f68\x1forigin/main\x1fahead 1\x1f*\x1f1792357278\x1fCombo\n'
    'refs/remotes/origin/main\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1fFirst\n',
  ),
  'operation': ok(''),
  'remotes': ok('remote.origin.url https://github.com/acme/neon-breaker.git\n'),
  'identity': ok('Ada Lovelace\nada@example.com\n'),
});

Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _shot(WidgetTester tester, GlobalKey key, String name) async =>
    writePng('$_out/$name.png', await capturePng(tester, key));

FileDiff _file(String path, String patch, int add, int del) =>
    FileDiff(file: path, patch: patch, additions: add, deletions: del);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('changes screen $mode', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: CaptureApi(),
      );
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: ReviewWorkspace(
            loadWorkingTreeDiffs: () async => [
              _file(
                'game.js',
                '@@ -41,5 +41,6 @@\n const state = {\n   score: 0,\n-  combo: false,\n+  combo: 0,\n+  shake: 0,\n };',
                30,
                5,
              ),
              _file(
                'index.html',
                '@@ -3,2 +3,8 @@\n-<title>Breaker</title>\n+<title>Neon breaker</title>\n+<div id="combo"></div>',
                8,
                2,
              ),
              _file(
                'style.css',
                '@@ -1,1 +1,4 @@\n-body { margin: 0 }\n+body { margin: 0; background: #05070a }\n+#combo { color: #5ef2b0 }',
                4,
                1,
              ),
            ],
          ),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      await _frames(tester);
      expect(find.byKey(const Key('review-question')), findsOneWidget);
      await _shot(tester, key, '1-changes-$mode');
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('git sheet $mode', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: CaptureApi(),
      );
      addTearDown(controller.dispose);
      final key = GlobalKey();
      final page = GitPageController(
        resolution: GitWorkspaceResolution.available(
          GitWorkspace(
            host: GitHost.phoneLinux,
            directory: '/root/projects/neon-breaker',
            shell: FakeGitShell([_snapshot(), _snapshot(), _snapshot()]),
            credentials: FakeCredentialDrop(),
          ),
        ),
      );
      await page.refresh();
      await tester.pumpWidget(
        captureApp(
          home: const Scaffold(body: SizedBox.expand()),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      showGitSheet(tester.element(find.byType(Scaffold)), page: page);
      await _frames(tester);
      expect(find.byKey(const Key('git-home')), findsOneWidget);
      await _shot(tester, key, '2-git-sheet-$mode');
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('permission ask on a list row $mode', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: CaptureApi(),
      );
      addTearDown(controller.dispose);
      final key = GlobalKey();
      final permission = PermissionRequest(
        id: 'ask-1',
        sessionID: 'neon',
        permission: 'bash',
        patterns: const ['npm install'],
        metadata: const {'command': 'npm install --save-dev vitest'},
        always: const ['npm *'],
      );
      await tester.pumpWidget(
        captureApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: permissionRequestCard(
                    context,
                    inList: true,
                    permission: permission,
                    who: 'Claude Code',
                    onAllow: () {},
                    onReject: () {},
                  ),
                ),
              ),
            ),
          ),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      await _frames(tester);
      await _shot(tester, key, '3-inbox-permission-$mode');
      await tester.pumpWidget(const SizedBox());
    });
  }
}
