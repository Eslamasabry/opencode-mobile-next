// Git in the chat, one-step save and send (vision slice, 2026-10-20): the
// real chat screen with the Git sheet its branch chip opens, one full phone
// screen per state at 412x915 dp (2x density), real fonts and blurred
// shadows. The project's Git answers come from a fake shell, so nothing
// touches a server or a repository; the data is invented.
//
// Run with:
//   TZ=UTC flutter test --no-pub --concurrency=1 \
//     tool/capture/vision_git_one_step_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-git-one-step/<scene>.png
// (--dart-define=OUT=<dir> writes elsewhere, e.g. a pass folder).
import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/git/git_shell.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/remote_shell.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/git_page_controller.dart';
import 'package:opencode_mobile/state/git_workspace.dart';
import 'package:opencode_mobile/state/github_account.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/git/git_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../../test/git/git_fixtures.dart' as fx;
import '../../test/git/git_test_support.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-git-one-step',
);

/// The repository as the fake shell tells it.
class _Repo {
  _Repo({
    this.changes = true,
    this.ahead = 0,
    this.behind = 0,
    this.conflicts = false,
  });

  bool changes;
  bool conflicts;
  int ahead;
  int behind;

  String get status =>
      '# branch.oid b196b9f452aac7110d9a5277edd4d698e72d9872\x00'
      '# branch.head main\x00'
      '# branch.upstream origin/main\x00'
      '# branch.ab +$ahead -$behind\x00'
      '${conflicts ? 'u UU N... 100644 100644 100644 100644 74377959ac5a323f34122043b694d786be25af09 577b1bae9d5574155dc4fde03bc954442e3f5225 d6c03cf539f4777d6541aca4eee1cf523fa11a27 src/basket.ts\x00' : ''}'
      '${changes ? '1 .M N... 100644 100644 100644 587be6b4 587be6b4 src/basket.ts\x00'
                '1 .M N... 100644 100644 100644 587be6b4 587be6b4 src/checkout_form.dart\x00'
                '? notes.md\x00' : ''}';

  String snapshot() => envelope({
    'status': ok(status),
    'unstaged': ok(
      changes
          ? '12\t3\tsrc/basket.ts\x0040\t9\tsrc/checkout_form.dart\x00'
          : '',
    ),
    'staged': ok(''),
    'log': ok(fx.log),
    'branches': ok(
      'refs/heads/main\x1f97f3f68\x1forigin/main\x1f\x1f*\x1f1792357278\x1fBasket\n'
      'refs/heads/feature/checkout\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1fFirst\n'
      'refs/remotes/origin/main\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1fFirst\n',
    ),
    'operation': ok(''),
    'remotes': ok('remote.origin.url https://github.com/acme/shopfront.git\n'),
    'identity': ok('Ada Lovelace\nada@example.com\n'),
  });
}

/// Answers every Git script from [repo]: fetch, the push plan, the push, a
/// commit, and otherwise a fresh snapshot. [hold] keeps the push running.
class _Shell implements GitShell {
  _Shell(this.repo, {this.pushNeedsSignIn = false});

  final _Repo repo;
  final bool pushNeedsSignIn;
  Completer<void>? hold;

  @override
  Future<String> run(String script, {required Duration timeout}) async {
    if (script.contains("'fetch'")) {
      return envelope({'upstream': ok('origin/main\n'), 'fetch': ok()});
    }
    if (script.contains("'symbolic-ref'")) {
      return envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      });
    }
    if (script.contains("'push'")) {
      await hold?.future;
      if (pushNeedsSignIn) {
        return envelope({
          'push': failed(
            "fatal: could not read Username for 'https://github.com': "
            'terminal prompts disabled\n',
          ),
        });
      }
      repo.ahead = 0;
      return envelope({'push': ok('To github.com:acme/shopfront.git\n')});
    }
    if (script.contains("'commit'")) {
      repo
        ..changes = false
        ..ahead += 1;
      return envelope({
        'add': ok(),
        'commit': ok(),
        'id': ok('57cdce1\n'),
        'files': ok('src/basket.ts\nsrc/checkout_form.dart\n'),
      });
    }
    return repo.snapshot();
  }
}

/// The chat's own server: it can run commands, so the chip names the branch.
class _Api extends CaptureApi implements RemoteShellGateway {
  _Api(this.repo);
  final _Repo repo;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(remoteShell: true, permissionRequests: true);

  @override
  Future<RemoteShellResult> runShellCommand(
    String command, {
    String? directory,
    required Duration timeout,
  }) async => RemoteShellResult(
    status: RemoteShellStatus.exited,
    output: command.contains('fetch')
        ? envelope({'upstream': ok('origin/main\n'), 'fetch': ok()})
        : repo.snapshot(),
    exitCode: 0,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

MessageWithParts _say(String id, String role, String text) => MessageWithParts(
  info: MessageInfo(id: id, sessionID: checkoutSessionID, role: role),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: text)],
);

Future<List<MessageWithParts>> _transcript(String _) async => [
  _say('u1', 'user', 'The basket empties when the app restarts. Fix it?'),
  _say(
    'a1',
    'assistant',
    'Found it: the basket only lived in memory. I now keep it on the phone '
        'and load it when the app starts.',
  ),
  _say('u2', 'user', 'Also keep my place in the checkout form.'),
  _say(
    'a2',
    'assistant',
    'Done. The checkout form remembers where you were if you go back. '
        'Both changes are ready to save.',
  ),
];

class _Account extends GitHubAccountController {
  _Account(this._state) : super(profileId: 'capture');
  final GitHubSignInState _state;
  @override
  GitHubSignInState get state => _state;
  @override
  Future<String?> tokenForGit() async => null;
}

enum _Step { home, review, sending, sent, notSent }

class _Scene {
  const _Scene(
    this.file,
    this.step, {
    this.light = false,
    this.width = 412,
    this.height = 915,
    this.scale = 1,
    this.locale,
    this.changes = true,
    this.ahead = 0,
    this.behind = 0,
    this.conflicts = false,
    this.writeForMe = false,
  });

  final String file;
  final _Step step;
  final bool light;
  final double width;
  final double height;
  final double scale;
  final Locale? locale;
  final bool changes;
  final int ahead;
  final int behind;

  /// A merge left a file with conflicts (the sheet offers to ask the agent).
  final bool conflicts;

  /// The review with the agent's own summary put in by "Write it for me".
  final bool writeForMe;
}

const _scenes = [
  _Scene('1-home-dark', _Step.home),
  _Scene('1-home-light', _Step.home, light: true),
  _Scene('2-review-dark', _Step.review),
  _Scene('2-review-light', _Step.review, light: true),
  _Scene('3-sending-dark', _Step.sending),
  _Scene('4-saved-and-sent-dark', _Step.sent),
  _Scene('5-send-only-dark', _Step.home, changes: false, ahead: 2),
  _Scene('5-get-latest-dark', _Step.home, changes: false, behind: 3),
  _Scene('6-not-sent-sign-in-dark', _Step.notSent),
  _Scene('7-home-320dp-2x-text', _Step.home, width: 320, height: 712, scale: 2),
  _Scene(
    '7-review-320dp-2x-text',
    _Step.review,
    width: 320,
    height: 712,
    scale: 2,
  ),
  _Scene('8-home-arabic-dark', _Step.home, locale: Locale('ar')),
  _Scene('8-review-arabic-dark', _Step.review, locale: Locale('ar')),
  _Scene('9-review-write-for-me-dark', _Step.review, writeForMe: true),
  _Scene('9-conflicts-ask-agent-dark', _Step.home, conflicts: true),
];

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('ONLY');

Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 14; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// Opens the review: the main action where there is one, else the row.
Future<void> _openReview(WidgetTester tester) async {
  final primary = find.byKey(const Key('git-primary'));
  await tester.tap(
    primary.evaluate().isNotEmpty
        ? primary
        : find.byKey(const Key('git-row-save')),
  );
  await _frames(tester);
  expect(find.byKey(const Key('git-save')), findsOneWidget);
  // The person leaves the notes out of this save.
  final notes = find.byKey(const Key('git-file-notes.md'));
  // Near the top, clear of the pinned buttons at large text.
  unawaited(Scrollable.ensureVisible(tester.element(notes), alignment: 0.1));
  await _frames(tester);
  await tester.tap(notes);
  await _frames(tester);
  final list = find.byType(Scrollable).last;
  await tester.drag(list, const Offset(0, 400));
  await _frames(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    // Arabic falls back to Noto, as on a device; AppTheme.forLocale names
    // the platform sans (Roboto) first.
    Future<void> load(String family, List<String> paths) async {
      final loader = FontLoader(family);
      for (final path in paths) {
        final bytes = File(path).readAsBytesSync();
        loader.addFont(Future.value(ByteData.sublistView(bytes)));
      }
      await loader.load();
    }

    await load('Noto Sans Arabic', const [
      'test/fixtures/fonts/NotoSansArabic-Regular.ttf',
      'test/fixtures/fonts/NotoSansArabic-Bold.ttf',
    ]);
    await load('sans-serif', const [
      'tool/capture/fonts/Roboto-Regular.ttf',
      'tool/capture/fonts/Roboto-Medium.ttf',
      'tool/capture/fonts/Roboto-Bold.ttf',
    ]);
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
    KitMotion.loops = true;
  });

  for (final scene in _scenes) {
    if (_only.isNotEmpty && !_only.split(',').contains(scene.file)) continue;
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      final size = Size(scene.width, scene.height);
      tester.view.physicalSize = size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;

      final repo = _Repo(
        changes: scene.changes,
        ahead: scene.ahead,
        behind: scene.behind,
        conflicts: scene.conflicts,
      );
      final prefs = await setupCapturePreferences();
      final api = _Api(repo)
        ..busy = {}
        ..messagesHandler = _transcript;
      final controller = await captureController(prefs: prefs, api: api);
      addTearDown(controller.dispose);
      final shell = _Shell(repo, pushNeedsSignIn: scene.step == _Step.notSent);
      final account = _Account(
        scene.step == _Step.notSent
            ? const GitHubSignedOut()
            : const GitHubSignedIn('ada'),
      );
      final page = GitPageController(
        resolution: GitWorkspaceResolution.available(
          GitWorkspace(
            host: GitHost.phoneLinux,
            directory: '/root/projects/shopfront',
            shell: shell,
            credentials: FakeCredentialDrop(),
          ),
        ),
        account: account,
      );
      addTearDown(page.dispose);
      await page.refresh();
      await page.checkForUpdates();

      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: checkoutSessionID),
          boundaryKey: key,
          controller: controller,
          light: scene.light,
          aboveNavigator: (child) => Builder(
            builder: (context) {
              Widget body = MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                child: child,
              );
              if (scene.locale case final locale?) {
                body = Localizations.override(
                  context: context,
                  locale: locale,
                  child: Theme(
                    // The app's own Arabic type, as main.dart applies it.
                    data: AppTheme.forLocale(Theme.of(context), locale),
                    child: body,
                  ),
                );
              }
              return body;
            },
          ),
        ),
      );
      await _frames(tester);
      expect(find.byKey(const ValueKey('chat-git-chip')), findsOneWidget);
      unawaited(
        showGitSheet(
          tester.element(find.byType(ChatScreen)),
          page: page,
          account: account,
          // As the chat opens it: the agent can be asked, and its last
          // reply's first sentence is the summary.
          onAskAgent: (_) {},
          summary: 'Keep the basket and the checkout form across restarts.',
        ),
      );
      await _frames(tester);
      expect(find.byKey(const Key('git-home')), findsOneWidget);

      switch (scene.step) {
        case _Step.home:
          break;
        case _Step.review:
          await _openReview(tester);
          if (scene.writeForMe) {
            final write = find.byKey(const Key('git-save-write-for-me'));
            unawaited(Scrollable.ensureVisible(tester.element(write)));
            await _frames(tester);
            await tester.tap(write);
            await _frames(tester);
          }
        case _Step.sending:
          await _openReview(tester);
          shell.hold = Completer<void>();
          await tester.tap(find.byKey(const Key('git-save-button')));
          await _frames(tester);
        case _Step.sent || _Step.notSent:
          await _openReview(tester);
          await tester.tap(find.byKey(const Key('git-save-button')));
          await _frames(tester);
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      shell.hold?.complete();
      await _frames(tester);
      await tester.pumpWidget(const SizedBox.shrink());
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
