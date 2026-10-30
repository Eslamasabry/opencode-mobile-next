// Regenerate deliberately:
//   flutter test --update-goldens test/chat_project_chip_test.dart
// and look at every changed image before committing it.
//
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart' show FilesScreen;
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/models.dart' show FileNode;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/navigation/last_project.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/complete_message_history.dart';

/// The conversation's project, as a small chip under the title that opens
/// the project's Files page (no bottom bar, owner 2026-10-20).
const _sessionID = 'session-1';

class _Api extends FakeServerGateway with CompleteMessageHistory {
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
    projectManagement: capable,
  );

  // This fixture exercises the project chip, without claiming the folder is
  // empty when its contents were not supplied.
  @override
  Future<List<FileNode>> listFiles(String path) async =>
      throw ApiException('Project contents are unavailable in this fixture.');

  @override
  Future<List<Session>> sessions() async => [current];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Session> session(String id) async => current;
}

Future<ConnectionController> _controller(
  _Api api, {
  bool second = false,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
        'username': '',
      },
      if (second)
        {
          'id': 'profile-2',
          'name': 'Work box',
          'flavor': 'v2',
          'baseUrl': 'http://localhost:2',
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
  double textScale = 1,
}) async {
  tester.view.physicalSize = const Size(390, 760);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
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

/// The quiet line's words, without the invisible direction marks.
String _lineText(WidgetTester tester) => tester
    .widgetList<Text>(find.descendant(of: _chip, matching: find.byType(Text)))
    .map((text) => text.data!)
    .join()
    .replaceAll(RegExp('[\u2066-\u2069\u200e\u200f]'), '');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

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
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets(
    "tapping the chip opens the project's Files page, and Back returns",
    (tester) async {
      final conn = await _controller(
        _Api(directory: '/root/projects/IPTV_King'),
      );
      addTearDown(conn.dispose);
      await _pumpChat(tester, conn);
      await tester.tap(_chip);
      await tester.pumpAndSettle();
      // One tap: the project's files, its tools one more in the bar's ⋯.
      expect(find.byKey(const ValueKey('project-files-page')), findsOneWidget);
      expect(find.byType(FilesScreen), findsOneWidget);
      expect(find.textContaining('IPTV_King'), findsWidgets);
      await tester.tap(find.byKey(const ValueKey('project-hub-menu')));
      await tester.pumpAndSettle();
      expect(find.text('Terminal'), findsOneWidget);
      expect(find.byKey(const ValueKey('project-hub-changes')), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('project-files-page')), findsNothing);
      expect(_chip, findsOneWidget);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.android),
  );

  testWidgets('a server with none of the project tools shows no chip', (
    tester,
  ) async {
    final api = _Api(directory: '/root/projects/IPTV_King', capable: false);
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    // Nothing to open from the project: no chip at all.
    expect(_chip, findsNothing);
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets('opening a conversation makes its project the last used', (
    tester,
  ) async {
    final conn = await _controller(_Api(directory: '/root/projects/IPTV_King'));
    addTearDown(conn.dispose);
    expect(lastUsedProjectOf(conn), isNull);
    await _pumpChat(tester, conn);
    expect(lastUsedProjectOf(conn), '/root/projects/IPTV_King');
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets('a temporary folder is never shown as a project', (tester) async {
    final conn = await _controller(_Api(directory: '/tmp/scratch'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    expect(_chip, findsNothing);
    expect(find.text('scratch'), findsNothing);
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets('one server: the line is the project alone, 48dp tall', (
    tester,
  ) async {
    final conn = await _controller(_Api(directory: '/root/projects/IPTV_King'));
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    expect(_lineText(tester), 'IPTV_King');
    expect(find.textContaining('Test server'), findsNothing);
    expect(tester.getSize(_chip).height, greaterThanOrEqualTo(48));
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets('two servers: the same line adds the server, no extra row', (
    tester,
  ) async {
    final conn = await _controller(
      _Api(directory: '/root/projects/IPTV_King'),
      second: true,
    );
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    expect(_lineText(tester), 'IPTV_King \u00b7 Test server');
    expect(
      find.bySemanticsLabel(RegExp('Project .*IPTV_King on Test server')),
      findsWidgets,
    );
    // The server is no longer a line of its own.
    expect(find.text('Test server'), findsNothing);
    await expectLater(
      find.byType(MaterialApp),
      matchesGoldenFile('goldens/chat_one_bar_two_servers_dark.png'),
    );
    await tester.tap(_chip);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('project-files-page')), findsOneWidget);
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));

  testWidgets('at text size 2.0 the title stays beside the back arrow', (
    tester,
  ) async {
    final conn = await _controller(
      _Api(directory: '/root/projects/IPTV_King'),
      second: true,
    );
    addTearDown(conn.dispose);
    await tester.pumpWidget(const SizedBox());
    final nav = GlobalKey<NavigatorState>();
    tester.view.physicalSize = const Size(390, 760);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(conn)],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          navigatorKey: nav,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: SizedBox()),
        ),
      ),
    );
    unawaited(
      nav.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const ChatScreen(sessionID: _sessionID),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final back = tester.getRect(find.byTooltip('Back'));
    final title = tester.getRect(find.byKey(const Key('chat-title')));
    expect(title.top, lessThan(back.bottom));
    expect(title.left, greaterThanOrEqualTo(back.right - 1));
    expect(_chip, findsOneWidget);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byType(MaterialApp),
      matchesGoldenFile('goldens/chat_one_bar_title_text2_dark.png'),
    );
  }, variant: TargetPlatformVariant.only(TargetPlatform.android));
}
