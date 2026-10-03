import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/navigation/last_project.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

/// The conversation's project, as a small chip under the title, with a
/// compact menu of what can be done there (chats-first shell).
const _sessionID = 'session-1';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api({required String? directory, this.capable = true})
    : super(baseUrl: 'http://localhost') {
    current = Session(
      id: _sessionID,
      title: 'Fix checkout',
      directory: directory,
    );
  }

  final bool capable;
  late Session current;

  @override
  ServerCapabilities get capabilities => ServerCapabilities(
    clientPromptMessageID: true,
    setupConfigRead: true,
    setupMcpInventory: true,
    fileBrowsing: capable,
    terminal: capable,
    sessionDiff: capable,
  );

  @override
  Future<List<Session>> sessions() async => [current];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Session> session(String id) async => current;
}

Future<ConnectionController> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected
    ..directory = api.current.directory
    ..sessionsById = {api.current.id: api.current};
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController conn, {
  Brightness brightness = Brightness.dark,
}) async {
  tester.view.physicalSize = const Size(390, 760);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        theme: brightness == Brightness.dark
            ? AppTheme.dark()
            : AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ChatScreen(sessionID: _sessionID),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Finder get _chip => find.byKey(const ValueKey('chat-project-chip'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('the title carries a chip naming the project, with a label', (
    tester,
  ) async {
    final conn = await _controller(_Api(directory: '/root/projects/IPTV_King'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    expect(_chip, findsOneWidget);
    // Under the title, not beside it.
    expect(
      tester.getTopLeft(_chip).dy,
      greaterThan(
        tester.getBottomLeft(find.byKey(const Key('chat-title'))).dy - 1,
      ),
    );
    expect(find.bySemanticsLabel(RegExp('Project .*IPTV_King')), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tapping the chip opens Files, Terminal and Changes', (
    tester,
  ) async {
    final conn = await _controller(_Api(directory: '/root/projects/IPTV_King'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    await tester.tap(_chip);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('chat-project-files')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-project-terminal')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-project-changes')), findsOneWidget);
    expect(find.text('Files'), findsOneWidget);
    expect(find.text('Terminal'), findsOneWidget);
    expect(find.text('Changes'), findsOneWidget);
  });

  testWidgets('a server with none of the project tools shows no chip', (
    tester,
  ) async {
    final api = _Api(directory: '/root/projects/IPTV_King', capable: false);
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    // Nothing to open from the project: no chip at all.
    expect(_chip, findsNothing);
  });

  testWidgets('opening a conversation makes its project the last used', (
    tester,
  ) async {
    final conn = await _controller(_Api(directory: '/root/projects/IPTV_King'));
    addTearDown(conn.dispose);
    expect(lastUsedProjectOf(conn), isNull);
    await _pumpChat(tester, conn);
    expect(lastUsedProjectOf(conn), '/root/projects/IPTV_King');
  });

  testWidgets('a temporary folder is never shown as a project', (tester) async {
    final conn = await _controller(_Api(directory: '/tmp/scratch'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    expect(_chip, findsNothing);
    expect(find.text('scratch'), findsNothing);
  });
}
