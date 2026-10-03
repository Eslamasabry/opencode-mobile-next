import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/chats_fakes.dart';

FakeChatFeedSource _source({String? lastUsed}) => FakeChatFeedSource(
  projects: [
    project('alpha', kind: 'dart'),
    project('beta', git: false),
  ],
  lastUsed: lastUsed,
);

/// Hosts the start screen the way Home does: pushed over a first page.
Future<void> _open(
  WidgetTester tester,
  FakeChatsHost host, {
  String? directory,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    chatsApp(
      host,
      Builder(
        builder: (context) => TextButton(
          onPressed: () => showNewChat(context, directory: directory),
          child: const Text('go'),
        ),
      ),
    ),
  );
  await tester.tap(find.text('go'));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(loadCaptureFonts);

  testWidgets('shows the question and the last used project on its chip', (
    tester,
  ) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
    );
    expect(find.text('What should we work on?'), findsOneWidget);
    expect(find.text(KitBidi.auto('alpha')), findsOneWidget);
    expect(find.text('Git'), findsOneWidget);
  });

  testWidgets('a directory given wins over the last used one', (tester) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
      directory: '/root/projects/beta',
    );
    expect(find.text(KitBidi.auto('beta')), findsOneWidget);
    expect(find.text('Git'), findsNothing);
  });

  testWidgets('a temporary last project is not offered', (tester) async {
    await _open(tester, FakeChatsHost(_source(lastUsed: '/tmp/scratch')));
    expect(find.text('Choose a project'), findsOneWidget);
  });

  testWidgets('without a project sending is off and says why', (tester) async {
    final host = FakeChatsHost(_source());
    await _open(tester, host);
    expect(find.text('Choose a project'), findsOneWidget);
    expect(
      find.text('Choose a project to start a conversation.'),
      findsOneWidget,
    );
    expect(host.fake.started, isEmpty);
  });

  testWidgets('the chip opens Open a project and its choice updates it', (
    tester,
  ) async {
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
      ..openProjectResult = '/root/projects/beta';
    await _open(tester, host);
    await tester.tap(find.byKey(const ValueKey('chats-new-project')));
    await tester.pumpAndSettle();
    expect(host.projectOpens, 1);
    expect(find.text(KitBidi.auto('beta')), findsOneWidget);
    expect(find.text(KitBidi.auto('alpha')), findsNothing);
  });

  testWidgets('choosing a project enables sending', (tester) async {
    final host = FakeChatsHost(_source())
      ..openProjectResult = '/root/projects/beta';
    await _open(tester, host);
    await tester.tap(find.byKey(const ValueKey('chats-new-project')));
    await tester.pumpAndSettle();
    expect(
      find.text('Choose a project to start a conversation.'),
      findsNothing,
    );
  });

  testWidgets('send starts the chat in the project and opens it', (
    tester,
  ) async {
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'));
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    expect(host.fake.started, [
      (directory: '/root/projects/alpha', prompt: 'Fix the build'),
    ]);
    expect(host.shown, ['ses_started']);
  });

  testWidgets('an error is said in plain words and keeps the draft', (
    tester,
  ) async {
    final source = _source(lastUsed: '/root/projects/alpha')
      ..startError = StateError('SocketException: connection refused');
    final host = FakeChatsHost(source);
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    expect(host.shown, isEmpty);
    expect(find.text('Fix the build'), findsOneWidget);
    expect(find.textContaining('SocketException'), findsNothing);
    expect(find.textContaining('StateError'), findsNothing);
  });

  testWidgets('large text does not overflow', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      chatsApp(
        FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
        const NewChatScreen(),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
