// Golden renders of "chats first": Chats home (mixed list, Needs you only,
// the project filter sheet, the two empty states) and the new chat start
// screen (with a project, and asking for one), at 412x915 and 1280x800,
// dark and light, with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/chats_home_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show WorkspaceProject;
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart'
    show leftoverNoticeLine;
import 'package:opencode_mobile/ui/widgets/work_status_line.dart'
    show WorkRunawayNotice;
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

enum _Scene {
  homeMixed('chats_home_mixed'),
  homeNeedsYou('chats_home_needs'),
  homeFilterSheet('chats_home_sheet'),
  homeEmpty('chats_home_empty'),
  homeNoMatch('chats_home_nomatch'),
  newChatProject('chats_new_project'),
  newChatChoose('chats_new_choose'),
  homeLeftover('chats_home_leftover'),
  newChatCopy('chats_new_copy');

  const _Scene(this.name);
  final String name;
}

FakeChatFeedSource _source(_Scene scene) {
  final items = [
    chat(
      'a',
      'Fix the login bug',
      status: ChatStatus.needsYou,
      at: _now.subtract(const Duration(minutes: 2)),
      preview: 'Allow running the tests?',
    ),
    chat(
      'e',
      'Which database should we use',
      dir: '/root/projects/gamma',
      project: 'gamma',
      git: false,
      status: ChatStatus.needsYou,
      at: _now.subtract(const Duration(minutes: 40)),
      preview: 'Postgres or SQLite for the sync queue?',
    ),
    chat(
      'b',
      'Add dark mode',
      dir: '/root/projects/beta',
      project: 'beta',
      git: false,
      status: ChatStatus.running,
      at: _now.subtract(const Duration(minutes: 9)),
      preview: 'Editing lib/ui/theme.dart',
    ),
    chat(
      'c',
      'Explain the build',
      at: _now.subtract(const Duration(hours: 3)),
      preview: 'It runs gradle first, then signs the bundle.',
    ),
    chat(
      'd',
      'Rename the settings page and move its tests next to it',
      dir: '/root/projects/beta',
      project: 'beta',
      git: false,
      at: _now.subtract(const Duration(days: 3)),
      preview: 'Done. Four files changed.',
    ),
    chat(
      'f',
      'Plan the release notes',
      dir: '/root/projects/gamma',
      project: 'gamma',
      git: false,
      at: _now.subtract(const Duration(days: 9)),
    ),
  ];
  if (scene == _Scene.homeFilterSheet) {
    // Conversations in temp and home folders add the Other folders row.
    items.addAll([
      chat(
        'g',
        'Scratch idea',
        dir: '/tmp/scratch',
        project: 'tmp',
        git: false,
        at: _now.subtract(const Duration(days: 2)),
      ),
      chat(
        'h',
        'Home notes',
        dir: '/root',
        project: 'Home',
        git: false,
        at: _now.subtract(const Duration(days: 4)),
      ),
    ]);
  }
  return FakeChatFeedSource(
    items: scene == _Scene.homeEmpty ? const [] : items,
    projects: [
      project('alpha', chats: 2, needs: 1, kind: 'dart'),
      project('beta', chats: 2, running: 1, git: false, kind: 'node'),
      project('gamma', chats: 2, needs: 1, git: false),
    ],
    lastUsed: scene == _Scene.newChatChoose ? null : '/root/projects/alpha',
  );
}

Future<void> _mount(
  WidgetTester tester,
  _Scene scene, {
  required bool light,
  required Size size,
  required GlobalKey boundary,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final host = FakeChatsHost(_source(scene));
  if (scene == _Scene.homeLeftover) {
    host.leftover = Builder(
      builder: (context) => leftoverNoticeLine(
        context,
        WorkRunawayNotice(
          identity: 1,
          helper: 'node',
          project: 'beta',
          busyFor: '12 min',
          onStop: () {},
          onDismiss: () {},
        ),
      ),
    );
  }
  if (scene == _Scene.newChatCopy) {
    host.copyProject = const WorkspaceProject(
      id: 'p-alpha',
      name: 'alpha',
      directory: '/root/projects/alpha',
      worktrees: [],
      updatedAt: 1,
    );
  }
  final Widget home = switch (scene) {
    _Scene.homeNeedsYou => const ChatsHomeScreen(
      initialFilter: ChatFeedFilter(needsYou: true),
    ),
    _Scene.homeNoMatch => const ChatsHomeScreen(
      initialFilter: ChatFeedFilter(
        running: true,
        projectDirectory: '/root/projects/alpha',
      ),
    ),
    _Scene.newChatProject ||
    _Scene.newChatChoose ||
    _Scene.newChatCopy => const NewChatScreen(),
    _ => const ChatsHomeScreen(),
  };
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: chatsApp(host, home, light: light),
    ),
  );
  await tester.pump(const Duration(milliseconds: 400));
  if (scene == _Scene.homeFilterSheet) {
    await tester.tap(find.byKey(const ValueKey('chats-filter-project')));
    await tester.pumpAndSettle();
  } else {
    await tester.pumpAndSettle();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  const sizes = {'': Size(412, 915), '_1280x800': Size(1280, 800)};
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: suffix, value: size) in sizes.entries) {
      for (final scene in _Scene.values) {
        final name = '${scene.name}$suffix';
        testWidgets('$name · $mode', (tester) async {
          final boundary = GlobalKey();
          debugDefaultTargetPlatformOverride =
              TargetPlatform.android; // ARCH-11
          await withClock(Clock.fixed(_now), () async {
            await _mount(
              tester,
              scene,
              light: light,
              size: size,
              boundary: boundary,
            );
          });
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('${name}_$mode.png'),
          );
          await tester.pumpWidget(const SizedBox.shrink());
          debugDefaultTargetPlatformOverride = null;
        });
      }
    }
  }
}
