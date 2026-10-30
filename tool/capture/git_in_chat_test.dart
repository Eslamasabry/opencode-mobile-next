// Git in the chat (issue #104, option C): the real chat screen with the
// branch chip in its header, the Git sheet the chip opens, the Save changes
// file list, and the GitHub sign-in code step. The project's Git answers come
// from a fake server shell, so nothing touches a server or a repository.
// Writes one PNG per screen under /home/eslam/Storage2/launch-footage/
// git-in-chat/.
//
// Run with:
//   flutter test --no-pub --concurrency=1 tool/capture/git_in_chat_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/remote_shell.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/git_page_controller.dart';
import 'package:opencode_mobile/state/git_workspace.dart';
import 'package:opencode_mobile/state/github_account.dart';
import 'package:opencode_mobile/ui/git/git_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../../test/git/git_fixtures.dart' as fx;
import '../../test/git/git_test_support.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _outputDir = '/home/eslam/Storage2/launch-footage/git-in-chat';

const _status =
    '# branch.oid b196b9f452aac7110d9a5277edd4d698e72d9872\x00'
    '# branch.head feature/checkout-basket\x00'
    '# branch.upstream origin/feature/checkout-basket\x00'
    '# branch.ab +2 -3\x00'
    '1 .M N... 100644 100644 100644 587be6b4 587be6b4 src/basket.ts\x00'
    '1 .M N... 100644 100644 100644 587be6b4 587be6b4 src/checkout_form.dart\x00'
    '1 .M N... 100644 100644 100644 587be6b4 587be6b4 test/basket_test.ts\x00'
    '? docs/notes.md\x00';

const _numstat =
    '12\t3\tsrc/basket.ts\x00'
    '40\t9\tsrc/checkout_form.dart\x00'
    '6\t0\ttest/basket_test.ts\x00';

const _branches =
    'refs/heads/feature/checkout-basket\x1f97f3f68\x1forigin/feature/checkout-basket\x1fahead 2\x1f*\x1f1792357278\x1fWire the basket\n'
    'refs/heads/main\x1fb196b9f\x1forigin/main\x1f\x1f \x1f1792357277\x1fFirst commit\n'
    'refs/remotes/origin/main\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1fFirst commit\n';

/// After a save of the first two files only the other two are left.
String _snapshot({bool saved = false}) => envelope({
  'status': ok(
    saved
        ? _status
              .replaceAll(RegExp(r'1 \.M [^\x00]*src/basket.ts\x00'), '')
              .replaceAll(
                RegExp(r'1 \.M [^\x00]*src/checkout_form.dart\x00'),
                '',
              )
              .replaceFirst('+2 -3', '+3 -3')
        : _status,
  ),
  'unstaged': ok(_numstat),
  'staged': ok(''),
  'log': ok(fx.log),
  'branches': ok(_branches),
  'operation': ok(''),
  'remotes': ok('remote.origin.url https://github.com/acme/shopfront.git\n'),
  'identity': ok('Ada Lovelace\nada@example.com\n'),
});

/// A server that can run commands: every Git script gets the one snapshot.
class _GitCaptureApi extends CaptureApi implements RemoteShellGateway {
  var _saved = false;

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    remoteShell: true,
    phoneTeamExecution: true,
    clientPromptMessageID: true,
    commandReceipts: true,
    setupConfigRead: true,
    setupMcpInventory: true,
    mcpRuntimeAdds: true,
    mcpChatConnect: true,
    mcpChatOAuth: true,
    mcpChatToolRefresh: true,
  );

  @override
  Future<RemoteShellResult> runShellCommand(
    String command, {
    String? directory,
    required Duration timeout,
  }) async {
    final String output;
    if (command.contains('fetch')) {
      output = envelope({'upstream': ok('origin/x\n'), 'fetch': ok()});
    } else if (command.contains("'commit'")) {
      _saved = true;
      output = envelope({
        'add': ok(),
        'commit': ok(),
        'id': ok('57cdce1\n'),
        'files': ok('src/basket.ts\nsrc/checkout_form.dart\n'),
      });
    } else {
      output = _snapshot(saved: _saved);
    }
    return RemoteShellResult(
      status: RemoteShellStatus.exited,
      output: output,
      exitCode: 0,
    );
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<List<MessageWithParts>> _transcript(String _) async => [
  MessageWithParts(
    info: MessageInfo(
      id: 'git-assistant',
      sessionID: checkoutSessionID,
      role: 'assistant',
    ),
    parts: [
      Part(
        type: 'text',
        text:
            'The basket now survives a restart, and the checkout form keeps '
            'your place if you go back.\n\nThe changes are ready to save.',
      ),
    ],
  ),
];

class _AwaitingAccount extends GitHubAccountController {
  _AwaitingAccount() : super(profileId: 'capture');
  @override
  GitHubSignInState get state => GitHubAwaitingUser(
    userCode: 'ABCD-1234',
    verificationUri: Uri.parse('https://github.com/login/device'),
    expiresAt: DateTime.utc(2030),
  );
  @override
  Future<String?> tokenForGit() async => null;
}

/// A few explicit frames: the chat underneath keeps a soft animation alive,
/// so the sheet cannot be waited on with pumpAndSettle.
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('the chat, the chip, the Git sheet and Save changes', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final prefs = await setupCapturePreferences();
    final api = _GitCaptureApi()
      ..busy = {}
      ..messagesHandler = _transcript;
    final controller = await captureController(prefs: prefs, api: api);
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
      ),
    );
    await _pumpFrames(tester);
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('chat-git-chip')), findsOneWidget);
    expect(find.textContaining('feature/checkout-basket'), findsOneWidget);
    await writePng(
      '$_outputDir/1-chat-with-branch-chip.png',
      await capturePng(tester, key),
    );

    await tester.tap(find.byKey(const ValueKey('chat-git-chip')));
    await _pumpFrames(tester);
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    await writePng(
      '$_outputDir/2-git-sheet.png',
      await capturePng(tester, key),
    );

    await tester.tap(find.byKey(const Key('git-primary')));
    await _pumpFrames(tester);
    expect(find.byKey(const Key('git-save')), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('git-save-message')),
      'Keep the basket after a restart',
    );
    await _pumpFrames(tester);
    await writePng(
      '$_outputDir/3-save-changes-file-picker.png',
      await capturePng(tester, key),
    );
    await tester.tap(find.byKey(const Key('git-file-test/basket_test.ts')));
    await tester.tap(find.byKey(const Key('git-file-docs/notes.md')));
    await _pumpFrames(tester);
    await tester.tap(find.byKey(const Key('git-save-only')));
    await _pumpFrames(tester);
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    await writePng(
      '$_outputDir/5-save-result.png',
      await capturePng(tester, key),
    );
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('the GitHub sign-in code step, over the real chat', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final prefs = await setupCapturePreferences();
    final api = _GitCaptureApi()
      ..busy = {}
      ..messagesHandler = _transcript;
    final controller = await captureController(prefs: prefs, api: api);
    addTearDown(controller.dispose);
    final key = GlobalKey();
    final account = _AwaitingAccount();
    // The in-app Linux offers the sign-in; the chat's own server does not, so
    // this sheet is opened for a page that does, over the same chat.
    final page = GitPageController(
      resolution: GitWorkspaceResolution.available(
        GitWorkspace(
          host: GitHost.phoneLinux,
          directory: '/root/projects/shopfront',
          shell: FakeGitShell([_snapshot(), _snapshot()]),
          credentials: FakeCredentialDrop(),
        ),
      ),
      account: account,
    );
    await page.refresh();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
      ),
    );
    await _pumpFrames(tester);
    showGitSheet(
      tester.element(find.byType(ChatScreen)),
      page: page,
      account: account,
    );
    await _pumpFrames(tester);
    await tester.tap(find.byKey(const Key('git-row-github')));
    await _pumpFrames(tester);
    expect(find.byKey(const Key('git-signin')), findsOneWidget);
    await writePng(
      '$_outputDir/4-github-sign-in-code.png',
      await capturePng(tester, key),
    );
    await tester.pumpWidget(const SizedBox());
  });
}
