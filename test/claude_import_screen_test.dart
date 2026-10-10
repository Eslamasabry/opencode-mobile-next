// "Import from Claude Code": reachable from the new-conversation screen, a
// list of what was begun in Claude Code (what it was about, its project
// folder, when it was last used, never an id), a tap imports it and opens it.
import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../tool/capture/fixtures.dart';
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'support/chats_fakes.dart';

final _now = DateTime.utc(2026, 10, 10, 12);

class _Import implements ProviderConversationImportGateway {
  var found = ImportableConversations(
    items: [
      ImportableConversation(
        agentId: 'claude',
        handle: 'native-session-aaaa',
        directory: '/root/projects/alpha',
        lastUsed: _now.subtract(const Duration(hours: 2)),
        title: 'Release notes for 1.3',
      ),
      ImportableConversation(
        agentId: 'claude',
        handle: 'native-session-bbbb',
        directory: '/root/projects/alpha',
        lastUsed: _now.subtract(const Duration(days: 3)),
        firstPrompt: 'Why does the login screen flicker?',
      ),
      ImportableConversation(
        agentId: 'claude',
        handle: 'native-session-cccc',
        directory: '/root/projects/alpha',
        lastUsed: _now.subtract(const Duration(days: 20)),
      ),
    ],
    alreadyImported: 2,
  );
  Object? listError;
  Object? importError;
  Completer<void>? hold;
  final imported = <ImportableConversation>[];
  int lists = 0;

  @override
  bool get providerImportSupported => true;

  @override
  String get providerImportDirectory => '/root/projects/alpha';

  @override
  Future<ImportableConversations> importableConversations() async {
    lists++;
    if (listError != null) throw listError!;
    return found;
  }

  @override
  Future<Session> importConversation(ImportableConversation c) async {
    imported.add(c);
    await hold?.future;
    if (importError != null) throw importError!;
    return Session(
      id: 'imported-1',
      title: 'Imported',
      directory: c.directory,
      time: SessionTime(created: 1, updated: 2),
    );
  }
}

final _boundary = GlobalKey();

Future<(FakeChatsHost, _Import)> _open(
  WidgetTester tester, {
  void Function(_Import import)? setUpImport,
  bool supported = true,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final import = _Import();
  setUpImport?.call(import);
  final host = FakeChatsHost(
    FakeChatFeedSource(
      projects: [project('alpha', kind: 'dart')],
      lastUsed: '/root/projects/alpha',
    ),
  )..conversationImport = supported ? import : null;
  await withClock(Clock.fixed(_now), () async {
    await tester.pumpWidget(
      RepaintBoundary(
        key: _boundary,
        child: chatsApp(
          host,
          Builder(
            builder: (context) => TextButton(
              onPressed: () => showNewChat(context),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
  });
  return (host, import);
}

Future<void> _openImport(WidgetTester tester) async {
  await withClock(Clock.fixed(_now), () async {
    await tester.tap(find.byKey(const ValueKey('chats-new-import-claude')));
    await tester.pumpAndSettle();
  });
}

void main() {
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  testWidgets('a server that cannot import has no entry', (tester) async {
    await _open(tester, supported: false);
    expect(find.byKey(const ValueKey('chats-new-import-claude')), findsNothing);
  });

  testWidgets('the list says what, where and when; never an id', (
    tester,
  ) async {
    await _open(tester);
    expect(find.text('Import from Claude Code'), findsOneWidget);
    await _openImport(tester);
    expect(find.text('Release notes for 1.3'), findsOneWidget);
    expect(find.text('Why does the login screen flicker?'), findsOneWidget);
    expect(find.text('Untitled conversation'), findsOneWidget);
    expect(find.text('alpha · 2h ago'), findsOneWidget);
    expect(find.text('alpha · 3d ago'), findsOneWidget);
    expect(find.textContaining('native-session'), findsNothing);
    expect(find.text('2 more conversations are already here.'), findsOneWidget);
  });

  testWidgets('a tap imports that conversation and opens it', (tester) async {
    final (host, import) = await _open(tester);
    await _openImport(tester);
    await tester.tap(find.text('Release notes for 1.3'));
    await tester.pumpAndSettle();
    expect(import.imported.single.handle, 'native-session-aaaa');
    expect(host.shown, ['imported-1']);
  });

  testWidgets('it says Importing while it works and rests the other rows', (
    tester,
  ) async {
    final (_, import) = await _open(
      tester,
      setUpImport: (i) => i.hold = Completer<void>(),
    );
    await _openImport(tester);
    await tester.tap(find.text('Release notes for 1.3'));
    await tester.pump();
    expect(find.text('Importing…'), findsOneWidget);
    await tester.tap(find.text('Why does the login screen flicker?'));
    await tester.pump();
    expect(import.imported, hasLength(1), reason: 'one at a time');
    import.hold!.complete();
    await tester.pumpAndSettle();
  });

  testWidgets('a failed import says so in plain words and stays', (
    tester,
  ) async {
    final (host, _) = await _open(
      tester,
      setUpImport: (i) => i.importError = StateError('session file is gone'),
    );
    await _openImport(tester);
    await tester.tap(find.text('Release notes for 1.3'));
    await tester.pumpAndSettle();
    expect(
      find.text('That conversation could not be imported. Try again.'),
      findsOneWidget,
    );
    expect(find.textContaining('session file'), findsNothing);
    expect(host.shown, isEmpty);
    expect(find.text('Release notes for 1.3'), findsOneWidget);
  });

  testWidgets('nothing to import says so', (tester) async {
    await _open(
      tester,
      setUpImport: (i) => i.found = const ImportableConversations(items: []),
    );
    await _openImport(tester);
    expect(find.text('Nothing to import'), findsOneWidget);
  });

  testWidgets('a failed read offers Try again', (tester) async {
    final (_, import) = await _open(
      tester,
      setUpImport: (i) => i.listError = StateError('boom'),
    );
    await _openImport(tester);
    expect(find.text('Could not look'), findsOneWidget);
    expect(find.textContaining('boom'), findsNothing);
    import.listError = null;
    await tester.tap(find.byKey(const Key('claude-import-retry')));
    await tester.pumpAndSettle();
    expect(find.text('Release notes for 1.3'), findsOneWidget);
    expect(import.lists, 2);
  });

  // The look gate: build/coverage/od-agent-import-*.png.
  testWidgets('look: the entry, the list, importing, empty', (tester) async {
    Future<void> shot(String name) async => writePng(
      'build/coverage/od-agent-import-$name.png',
      await capturePng(tester, _boundary, pixelRatio: 1),
    );
    final (_, import) = await _open(
      tester,
      setUpImport: (i) => i.hold = Completer<void>(),
    );
    await shot('1-entry');
    await _openImport(tester);
    await shot('2-list');
    await tester.tap(find.text('Release notes for 1.3'));
    await tester.pump();
    await shot('3-importing');
    import.hold!.complete();
    await tester.pumpAndSettle();
  });

  testWidgets('look: nothing to import', (tester) async {
    await _open(
      tester,
      setUpImport: (i) => i.found = const ImportableConversations(items: []),
    );
    await _openImport(tester);
    await writePng(
      'build/coverage/od-agent-import-4-empty.png',
      await capturePng(tester, _boundary, pixelRatio: 1),
    );
  });
}
