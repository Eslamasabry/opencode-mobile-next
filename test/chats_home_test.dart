import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

void clocked(
  String name,
  Future<void> Function(WidgetTester tester) body, {
  Size size = const Size(412, 915),
}) {
  testWidgets(name, (tester) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await withClock(Clock.fixed(_now), () => body(tester));
  });
}

FakeChatFeedSource _source() => FakeChatFeedSource(
  items: [
    chat(
      'a',
      'Fix the login bug',
      status: ChatStatus.needsYou,
      at: _now.subtract(const Duration(minutes: 2)),
      preview: 'Allow running the tests?',
    ),
    chat(
      'b',
      'Add dark mode',
      dir: '/root/projects/beta',
      project: 'beta',
      git: false,
      status: ChatStatus.running,
      at: _now.subtract(const Duration(minutes: 9)),
      preview: 'Editing theme.dart',
    ),
    chat(
      'c',
      'Explain the build',
      at: _now.subtract(const Duration(hours: 3)),
      preview: 'It runs gradle first.',
    ),
    chat(
      'd',
      'Old idea',
      dir: '/root/projects/beta',
      project: 'beta',
      git: false,
      at: _now.subtract(const Duration(days: 3)),
    ),
    chat(
      'sub',
      'Subagent work',
      parent: 'a',
      at: _now.subtract(const Duration(minutes: 1)),
    ),
  ],
  projects: [
    project('alpha', chats: 2, needs: 1, kind: 'dart'),
    project('beta', chats: 2, running: 1, git: false),
    project('quiet', chats: 0),
  ],
);

Future<void> _pump(
  WidgetTester tester,
  FakeChatsHost host, {
  ChatFeedFilter? initialFilter,
  Widget? home,
}) async {
  await tester.pumpWidget(
    chatsApp(host, home ?? ChatsHomeScreen(initialFilter: initialFilter)),
  );
  await tester.pump(const Duration(milliseconds: 300));
}

double _y(WidgetTester tester, String text) =>
    tester.getTopLeft(find.text(text).first).dy;

void main() {
  setUpAll(loadCaptureFonts);

  clocked('lists Needs you, Today, then Earlier, newest first', (tester) async {
    await _pump(tester, FakeChatsHost(_source()));
    expect(find.text('Conversations'), findsWidgets);
    expect(
      _y(tester, KitBidi.auto('Fix the login bug')),
      lessThan(_y(tester, KitBidi.auto('Add dark mode'))),
    );
    expect(
      _y(tester, KitBidi.auto('Add dark mode')),
      lessThan(_y(tester, KitBidi.auto('Explain the build'))),
    );
    expect(
      _y(tester, KitBidi.auto('Explain the build')),
      lessThan(_y(tester, KitBidi.auto('Old idea'))),
    );
    // The three group names, and a subagent is not a row.
    expect(find.text('Today'), findsOneWidget);
    expect(find.text('Earlier'), findsOneWidget);
    expect(find.text(KitBidi.auto('Subagent work')), findsNothing);
    // Status tags on the right, the time otherwise, the last line below.
    expect(find.text('Running'), findsWidgets);
    expect(find.text('3d ago'), findsOneWidget);
    expect(find.text(KitBidi.auto('Allow running the tests?')), findsOneWidget);
    // The project label with a Git badge only where the project is Git.
    expect(find.text('Git'), findsNWidgets(2));
  });

  clocked('the Needs you chip shows its count and filters', (tester) async {
    await _pump(tester, FakeChatsHost(_source()));
    expect(find.text('Needs you · 1'), findsOneWidget);
    await tester.tap(find.text('Needs you · 1'));
    await tester.pump();
    expect(find.text(KitBidi.auto('Fix the login bug')), findsOneWidget);
    expect(find.text(KitBidi.auto('Add dark mode')), findsNothing);
    expect(find.text(KitBidi.auto('Explain the build')), findsNothing);
    await tester.tap(find.text('Needs you · 1'));
    await tester.pump();
    expect(find.text(KitBidi.auto('Explain the build')), findsOneWidget);
  });

  clocked('Running filters, and with Needs you means either', (tester) async {
    await _pump(tester, FakeChatsHost(_source()));
    await tester.tap(find.byKey(const ValueKey('chats-filter-running')));
    await tester.pump();
    expect(find.text(KitBidi.auto('Add dark mode')), findsOneWidget);
    expect(find.text(KitBidi.auto('Fix the login bug')), findsNothing);
    await tester.tap(find.text('Needs you · 1'));
    await tester.pump();
    expect(find.text(KitBidi.auto('Add dark mode')), findsOneWidget);
    expect(find.text(KitBidi.auto('Fix the login bug')), findsOneWidget);
    expect(find.text(KitBidi.auto('Explain the build')), findsNothing);
  });

  clocked('the project sheet lists All projects and each project', (
    tester,
  ) async {
    await _pump(tester, FakeChatsHost(_source()));
    await tester.tap(find.text('All projects'));
    await tester.pumpAndSettle();
    expect(find.text('Show conversations from'), findsOneWidget);
    expect(find.text('4 conversations'), findsOneWidget);
    expect(find.text('2 conversations · needs you'), findsOneWidget);
    expect(find.text('2 conversations · 1 running'), findsOneWidget);
    expect(find.text('0 conversations'), findsOneWidget);
    expect(find.text('Open a project…'), findsOneWidget);
  });

  clocked('choosing a project filters, the chip names it', (tester) async {
    await _pump(tester, FakeChatsHost(_source()));
    await tester.tap(find.text('All projects'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(KitBidi.auto('beta')).last);
    await tester.pumpAndSettle();
    expect(find.text('Show conversations from'), findsNothing);
    expect(find.text(KitBidi.auto('Add dark mode')), findsOneWidget);
    expect(find.text(KitBidi.auto('Old idea')), findsOneWidget);
    expect(find.text(KitBidi.auto('Fix the login bug')), findsNothing);
    expect(find.text('All projects'), findsNothing);
    // Needs you counts inside the chosen project.
    expect(find.text('Needs you'), findsOneWidget);
  });

  clocked('Open a project… makes the chosen folder the filter', (tester) async {
    final host = FakeChatsHost(_source())
      ..openProjectResult = '/root/projects/quiet';
    await _pump(tester, host);
    await tester.tap(find.text('All projects'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open a project…'));
    await tester.pumpAndSettle();
    expect(host.projectOpens, 1);
    // The sheet closed before the browser opened: one sheet only.
    expect(find.text('Show conversations from'), findsNothing);
    // A folder without chats is an empty list that offers a start.
    expect(
      find.text('No conversations in ${KitBidi.auto('quiet')}'),
      findsOneWidget,
    );
    expect(
      find.text('Start a conversation in ${KitBidi.auto('quiet')}'),
      findsOneWidget,
    );
  });

  clocked('a temporary folder is never made the filter', (tester) async {
    final host = FakeChatsHost(_source())..openProjectResult = '/tmp/scratch';
    await _pump(tester, host);
    await tester.tap(find.text('All projects'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open a project…'));
    await tester.pumpAndSettle();
    expect(find.text('All projects'), findsOneWidget);
    expect(find.text(KitBidi.auto('Fix the login bug')), findsOneWidget);
  });

  clocked('no chats at all offers Start a chat', (tester) async {
    await _pump(tester, FakeChatsHost(FakeChatFeedSource()));
    expect(find.text('No conversations yet'), findsOneWidget);
    expect(find.text('Start a conversation'), findsOneWidget);
    expect(find.text('New conversation'), findsNothing);
  });

  clocked('no matches offers Clear filters, which clears them', (tester) async {
    final source = _source()
      ..items = [
        chat(
          'c',
          'Explain the build',
          at: _now.subtract(const Duration(hours: 3)),
        ),
      ];
    await _pump(tester, FakeChatsHost(source));
    await tester.tap(find.text('Needs you'));
    await tester.pump();
    expect(find.text('No matching conversations'), findsOneWidget);
    // Clear filters is the one primary action here (the shell allows one).
    expect(find.text('New conversation'), findsNothing);
    await tester.tap(find.text('Clear filters'));
    await tester.pump();
    expect(find.text(KitBidi.auto('Explain the build')), findsOneWidget);
  });

  clocked('a server that cannot list all projects says so, quietly', (
    tester,
  ) async {
    final source = _source()..acrossProjects = false;
    await _pump(tester, FakeChatsHost(source));
    expect(
      find.text(
        "Showing conversations in ${KitBidi.auto('alpha')} only. This server can't list all projects.",
      ),
      findsOneWidget,
    );
  });

  clocked('a partial list says some chats could not load', (tester) async {
    final source = _source()..complete = false;
    await _pump(tester, FakeChatsHost(source));
    expect(find.text("Some conversations couldn't load"), findsOneWidget);
  });

  clocked('tapping a row opens that conversation', (tester) async {
    final host = FakeChatsHost(_source());
    await _pump(tester, host);
    await tester.tap(find.text(KitBidi.auto('Explain the build')));
    await tester.pump();
    expect(host.opened, ['c']);
  });

  clocked('pull to refresh asks the source for a fresh list', (tester) async {
    final source = _source();
    await _pump(tester, FakeChatsHost(source));
    await tester.fling(
      find.text(KitBidi.auto('Fix the login bug')),
      const Offset(0, 500),
      1000,
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(source.refreshes, 1);
  });

  clocked('New chat opens the start screen on the filtered project', (
    tester,
  ) async {
    final host = FakeChatsHost(_source());
    await _pump(tester, host);
    await tester.tap(find.text('All projects'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(KitBidi.auto('beta')).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('New conversation'));
    await tester.pumpAndSettle();
    expect(find.text('What should we work on?'), findsOneWidget);
    expect(find.text(KitBidi.auto('beta')), findsWidgets);
  });

  clocked('an initial filter applies, and a new one replaces it', (
    tester,
  ) async {
    final host = FakeChatsHost(_source());
    await _pump(
      tester,
      host,
      initialFilter: const ChatFeedFilter(needsYou: true),
    );
    expect(find.text(KitBidi.auto('Fix the login bug')), findsOneWidget);
    expect(find.text(KitBidi.auto('Explain the build')), findsNothing);
    await _pump(
      tester,
      host,
      initialFilter: const ChatFeedFilter(running: true),
    );
    expect(find.text(KitBidi.auto('Add dark mode')), findsOneWidget);
    expect(find.text(KitBidi.auto('Fix the login bug')), findsNothing);
  });

  clocked('names the agent only when the feed mixes agents', (tester) async {
    await _pump(tester, FakeChatsHost(_source()));
    expect(find.text(KitBidi.auto('Claude Code')), findsNothing);
    final mixed = _source()
      ..items = [
        ..._source().items,
        chat(
          'cc',
          'Review the diff',
          at: _now.subtract(const Duration(minutes: 5)),
          agentId: 'claude',
          agentLabel: 'Claude Code',
        ),
      ];
    await _pump(tester, FakeChatsHost(mixed));
    expect(find.text(KitBidi.auto('Claude Code')), findsOneWidget);
  });

  clocked('large text does not overflow', (tester) async {
    await tester.pumpWidget(
      chatsApp(FakeChatsHost(_source()), const ChatsHomeScreen(), textScale: 2),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
  });
}
