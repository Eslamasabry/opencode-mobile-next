import 'support/fake_server_capabilities.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/composer_tools.dart';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api({this.files = true}) : super(baseUrl: 'http://localhost');

  final bool files;
  final List<String> queries = [];
  final List<({String text, List<PromptAttachment> attachments})> prompts = [];

  @override
  String? get directory => '/srv/app';

  @override
  ServerCapabilities get capabilities => files
      ? testServerCapabilities
      : const ServerCapabilities(fileBrowsing: false);

  @override
  Future<List<String>> findFile(String query) async {
    queries.add(query);
    return const [
      'lib/api/client.dart',
      'lib/server.dart',
      'README.md',
      'test/',
    ];
  }

  @override
  Future<List<FileNode>> listFiles(String path) async => path == 'lib'
      ? [FileNode(name: 'server.dart', path: 'lib/server.dart', isDir: false)]
      : [
          FileNode(name: 'lib', path: 'lib', isDir: true),
          FileNode(name: 'pubspec.yaml', path: 'pubspec.yaml', isDir: false),
        ];

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompts.add((text: text, attachments: attachments));
  }
}

Future<ConnectionController> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..directory = '/srv/app'
    ..catalog = const CatalogSnapshot(
      providers: [],
      models: [],
      agents: [
        CatalogAgent(id: 'server-helper', mode: 'subagent', hidden: false),
      ],
    )
    ..status = StreamStatus.connected;
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _pump(WidgetTester tester, ConnectionController controller) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await _settle(tester);
}

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
  // The strip waits for the typing to settle before it asks the server.
  await tester.pump(const Duration(milliseconds: 200));
  await tester.pump();
}

String _text(WidgetTester tester) => tester
    .widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('chat-composer-field')),
        matching: find.byType(TextField),
      ),
    )
    .controller!
    .text;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('@ with a query lists matching project files', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, 'look at @ser');

    expect(api.queries, ['ser']);
    expect(
      find.byKey(const Key('inline-file-lib/server.dart')),
      findsOneWidget,
    );
    expect(find.textContaining('server.dart'), findsOneWidget);
    expect(find.textContaining('lib'), findsWidgets);
    // The best name match leads the server's order.
    final first = tester.getTopLeft(
      find.byKey(const Key('inline-file-lib/server.dart')),
    );
    final other = tester.getTopLeft(
      find.byKey(const Key('inline-file-lib/api/client.dart')),
    );
    expect(first.dy, lessThan(other.dy));
  });

  testWidgets('a folder result is left out of the @ suggestions', (
    tester,
  ) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, '@ser');

    expect(
      find.byKey(const Key('inline-file-lib/server.dart')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('inline-file-test/')), findsNothing);
    expect(find.byKey(const Key('inline-file-test')), findsNothing);
    // Three files, one agent: no row without a file name.
    expect(
      find.byKey(const Key('inline-file-lib/api/client.dart')),
      findsOneWidget,
    );
  });

  testWidgets('agents still suggest, first, beside the files', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, '@ser');

    final agent = find.byKey(const Key('inline-agent-server-helper'));
    expect(agent, findsOneWidget);
    expect(
      tester.getTopLeft(agent).dy,
      lessThan(
        tester
            .getTopLeft(find.byKey(const Key('inline-file-lib/server.dart')))
            .dy,
      ),
    );
  });

  testWidgets('picking a file writes @path and stages one file, which Send '
      'carries with its range', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, 'read @ser');
    await tester.tap(find.byKey(const Key('inline-file-lib/server.dart')));
    await tester.pump();

    expect(_text(tester), 'read @lib/server.dart ');
    expect(find.textContaining('server.dart'), findsWidgets);

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await _settle(tester);

    final sent = api.prompts.single;
    expect(sent.text, 'read @lib/server.dart');
    final file = sent.attachments.single.toJson();
    expect(file['url'], 'file:///srv/app/lib/server.dart');
    expect(file['mime'], 'text/plain');
    expect(file['source'], {
      'type': 'file',
      'path': 'lib/server.dart',
      'text': {'value': '@lib/server.dart', 'start': 5, 'end': 21},
    });
  });

  testWidgets('deleting the mention text drops its attachment', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, '@ser');
    await tester.tap(find.byKey(const Key('inline-file-lib/server.dart')));
    await tester.pump();
    await _type(tester, 'never mind');

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await _settle(tester);

    expect(api.prompts.single.text, 'never mind');
    expect(api.prompts.single.attachments, isEmpty);
  });

  testWidgets('the + menu opens the project sheet and a pick inserts the '
      'mention', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await openComposerTools(tester);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('composer-tool-project-file')));
    await _settle(tester);

    expect(find.byKey(const Key('project-file-sheet')), findsOneWidget);
    // Before a query, the top-level files.
    expect(
      find.byKey(const ValueKey('project-file-pubspec.yaml')),
      findsOneWidget,
    );

    await tester.enterText(
      find.byKey(const Key('project-file-search')),
      'server',
    );
    await tester.pump(const Duration(milliseconds: 400));
    await _settle(tester);
    expect(api.queries, contains('server'));
    await tester.tap(
      find.byKey(const ValueKey('project-file-lib/server.dart')),
    );
    await _settle(tester);

    expect(find.byKey(const Key('project-file-sheet')), findsNothing);
    expect(_text(tester), '@lib/server.dart ');
  });

  testWidgets('the file sheet lists folders and one tap opens a folder, with '
      'a way back', (tester) async {
    final api = _Api();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await openComposerTools(tester);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('composer-tool-project-file')));
    await _settle(tester);

    expect(find.byKey(const ValueKey('project-folder-lib/')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('project-folder-lib/')));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('project-file-lib/server.dart')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('project-file-pubspec.yaml')),
      findsNothing,
    );

    await tester.tap(find.byKey(const Key('project-file-back')));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('project-file-pubspec.yaml')),
      findsOneWidget,
    );
  });

  testWidgets('a server without file browsing has no file rows and no '
      'project option', (tester) async {
    final api = _Api(files: false);
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pump(tester, controller);

    await _type(tester, '@ser');
    expect(api.queries, isEmpty);
    expect(find.byKey(const Key('inline-file-lib/server.dart')), findsNothing);
    expect(find.byKey(const Key('inline-agent-server-helper')), findsOneWidget);

    await openComposerTools(tester);
    await _settle(tester);
    expect(find.byKey(const Key('composer-tool-project-file')), findsNothing);
  });
}
