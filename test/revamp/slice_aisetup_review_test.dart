// Behaviour of slice-aisetup-review: the AI setup page, review only (owner
// decision 2026-09-28). The server's settings page opens it on a server that
// shares its configuration; it shows the settings in effect (OpenCode 1) or
// the ordered sources (OpenCode 2), the tool servers by urgency in plain
// words, and read-only suggestions; nothing on it applies or undoes a change,
// and no credential in the configuration ever renders.
import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/setup_assistant.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart'
    show paseoServerCapabilities;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/setup_session.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/screens/settings/ai_setup_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Answers `/config` and `/mcp` like an OpenCode 1 server.
class _Http implements HttpClientAdapter {
  _Http(this.routes);
  final Map<String, Object?> routes;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode(routes[options.path] ?? const {}),
    routes.containsKey(options.path) ? 200 : 404,
    headers: {
      Headers.contentTypeHeader: ['application/json'],
    },
  );

  @override
  void close({bool force = false}) {}
}

class _Api extends OpenCodeApi {
  _Api({this.paseo = false, Map<String, Object?> routes = const {}})
    : super(baseUrl: 'http://203.0.113.10:4747') {
    dio.httpClientAdapter = _Http(routes);
  }
  final bool paseo;

  @override
  ServerCapabilities get capabilities =>
      paseo ? paseoServerCapabilities : super.capabilities;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.32');
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final _profile = ServerProfile(
    id: 'server',
    name: 'Laptop',
    baseUrl: 'http://203.0.113.10:4747',
  );

  @override
  List<ServerProfile> get profiles => [_profile];

  @override
  String? get activeId => _profile.id;
}

/// A setup adapter the test controls: each read answers from [config] and
/// [servers], or throws [failure], or waits on [hold].
class _Gateway implements SetupConfigGateway {
  Map<String, Object?> config = const {};
  List<SetupMcpStatus> servers = const [];
  SetupFailure? failure;
  Completer<void>? hold;
  int reads = 0;

  @override
  SetupSupport get support =>
      const SetupSupport(readConfig: true, mcpInventory: true);

  @override
  Future<Map<String, Object?>> readConfig() async {
    reads++;
    await hold?.future;
    if (failure != null) throw failure!;
    return config;
  }

  @override
  Future<void> patchConfig(Map<String, Object?> patch) =>
      throw UnimplementedError('review only');

  @override
  Future<List<SetupMcpStatus>> listMcpServers() async => servers;
}

Future<ConnectionController> _connection({
  bool paseo = false,
  Map<String, Object?> routes = const {},
}) async {
  // Setup history needs the profile saved, as it is in the app.
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'server', 'name': 'Laptop', 'baseUrl': 'http://203.0.113.10:4747'},
    ]),
  });
  return ConnectionController(
      _Store(prefs: await SharedPreferences.getInstance()),
    )
    ..api = _Api(paseo: paseo, routes: routes)
    ..version = '1.18.32'
    ..status = StreamStatus.connected;
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// A window tall enough that the whole page is built (the list is lazy).
void _tall(WidgetTester tester) {
  tester.view.physicalSize = const Size(900, 4000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

/// Every piece of text on screen, including folded-open details.
String _screenText(WidgetTester tester) => [
  for (final widget in tester.allWidgets)
    if (widget is RichText)
      widget.text.toPlainText()
    else if (widget is EditableText)
      widget.controller.text,
].join('\n');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        ),
  );
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
    KitRedact.clearKnownSecrets();
  });

  Future<(ConnectionController, _Gateway, SetupSession)> open(
    WidgetTester tester, {
    _Gateway? gateway,
    bool supported = true,
    bool connected = true,
  }) async {
    _tall(tester);
    final connection = await _connection();
    if (!connected) connection.status = StreamStatus.disconnected;
    final fake = gateway ?? _Gateway();
    final session = SetupSession(
      connection,
      gatewayFor: (_) => supported ? fake : null,
    );
    addTearDown(session.dispose);
    addTearDown(connection.dispose);
    await tester.pumpWidget(
      _app(
        AiSetupScreen(
          controller: connection,
          serverName: 'Laptop',
          session: session,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return (connection, fake, session);
  }

  group('entry', () {
    testWidgets('the server settings page opens AI setup', (tester) async {
      _tall(tester);
      final connection = await _connection(
        routes: {
          '/config': {'model': 'anthropic/claude-sonnet-4'},
          '/mcp': {
            'docs': {'status': 'connected'},
          },
        },
      );
      addTearDown(connection.dispose);
      await tester.pumpWidget(
        _app(ServerSettingsScreen(controller: connection)),
      );
      await tester.pumpAndSettle();

      final entry = find.byKey(const Key('ai-setup-entry'));
      expect(entry, findsOneWidget);
      expect(
        find.text('Models, tools and suggestions for this server'),
        findsOneWidget,
      );
      await tester.tap(entry);
      await tester.pumpAndSettle();

      // Through the real OpenCode 1 setup adapter.
      expect(find.byType(AiSetupScreen), findsOneWidget);
      expect(find.text('anthropic/claude-sonnet-4'), findsOneWidget);
      expect(find.text('docs'), findsOneWidget);
      expect(find.text('Connected'), findsOneWidget);
    });

    testWidgets('a server without setup sharing gets no entry', (tester) async {
      final connection = await _connection(paseo: true);
      addTearDown(connection.dispose);
      await tester.pumpWidget(
        _app(ServerSettingsScreen(controller: connection)),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('ai-setup-entry')), findsNothing);
      expect(find.text('AI setup'), findsNothing);
    });
  });

  testWidgets('no credential in the configuration ever renders', (
    tester,
  ) async {
    const secrets = [
      'SYNTHETICPROVIDERKEY0123456789',
      'ghsecretpass',
      'SYNTHETICBEARER0123456789',
      'SYNTHETICARGTOKEN0123',
      'SYNTHETICENVVALUE0123',
      'SYNTHETICPLAINPASSWORD',
    ];
    final connection = await _connection(
      routes: {
        '/config': {
          'model': 'anthropic/claude-sonnet-4',
          'provider': {
            'anthropic': {
              'options': {
                'apiKey': 'sk-ant-api03-SYNTHETICPROVIDERKEY0123456789',
              },
            },
            'local': {'password': 'SYNTHETICPLAINPASSWORD'},
          },
          'mcp': {
            'github': {
              'type': 'remote',
              'url': 'https://octo:ghsecretpass@mcp.example.com/sse',
              'headers': {'Authorization': 'Bearer SYNTHETICBEARER0123456789'},
            },
            'files': {
              'type': 'local',
              'command': [
                'npx',
                'files-server',
                '--token',
                'SYNTHETICARGTOKEN0123',
              ],
              'environment': {'FILES_TOKEN': 'SYNTHETICENVVALUE0123'},
            },
          },
        },
        '/mcp': {
          'github': {'status': 'needs_auth'},
          'files': {'status': 'failed'},
        },
      },
    );
    addTearDown(connection.dispose);
    _tall(tester);
    await tester.pumpWidget(
      _app(AiSetupScreen(controller: connection, serverName: 'Laptop')),
    );
    await tester.pumpAndSettle();

    // Open everything that folds: all settings, and every line of them.
    await tester.tap(find.text('All settings'));
    await tester.pumpAndSettle();
    final showAll = find.textContaining('Show all');
    if (showAll.evaluate().isNotEmpty) {
      await tester.tap(showAll.first);
      await tester.pumpAndSettle();
    }
    final text = _screenText(tester);
    // The fold did open: the settings' own names are there.
    expect(text, contains('"provider"'));
    expect(text, contains('mcp.example.com'));
    for (final secret in secrets) {
      expect(text, isNot(contains(secret)), reason: secret);
    }
    final semantics = tester.getSemantics(find.byType(AiSetupScreen));
    expect(semantics, isNotNull);
  });

  testWidgets('OpenCode 1: settings in effect, tools by urgency, suggestions, '
      'and nothing that changes the server', (tester) async {
    final gateway = _Gateway()
      ..config = {
        'default_agent': 'build',
        'provider': {
          'anthropic': <String, Object?>{},
          'openai': <String, Object?>{},
        },
        'permission': {'bash': 'ask', 'edit': 'allow'},
      }
      ..servers = const [
        SetupMcpStatus(name: 'docs', status: 'connected'),
        SetupMcpStatus(name: 'archive', status: 'disabled'),
        SetupMcpStatus(name: 'search', status: 'failed'),
        SetupMcpStatus(name: 'github', status: 'needs_auth'),
        SetupMcpStatus(name: 'boot', status: 'pending'),
        SetupMcpStatus(name: 'odd', status: 'unknown'),
      ];
    await open(tester, gateway: gateway);

    expect(
      find.text('Review only. Changes are made on the server for now.'),
      findsOneWidget,
    );
    // Suggestions: the tool that needs sign-in, the failed one, the model.
    expect(find.text('Sign in to github'), findsOneWidget);
    expect(find.text("Check search's settings"), findsOneWidget);
    expect(find.text('Choose a default model'), findsOneWidget);
    expect(find.text('Add tool servers'), findsNothing);

    // Tools, most urgent first, each in plain words.
    final order = ['github', 'search', 'boot', 'odd', 'archive', 'docs'];
    final tops = [
      for (final name in order) tester.getTopLeft(find.text(name).last).dy,
    ];
    expect(tops, orderedEquals([...tops]..sort()));
    for (final word in [
      'Needs sign-in',
      'Failed',
      'Waiting',
      'Unknown',
      'Off',
      'Connected',
    ]) {
      expect(find.text(word), findsOneWidget, reason: word);
    }

    // Settings in effect.
    expect(find.text('Settings in effect'), findsOneWidget);
    expect(find.text('Not set: the server picks'), findsOneWidget);
    expect(find.text('build'), findsOneWidget);
    expect(find.text('anthropic, openai'), findsOneWidget);
    expect(find.text('2 rules'), findsOneWidget);
    expect(find.text('Configuration sources'), findsNothing);

    // Review only: nothing applies, proposes or undoes.
    final text = _screenText(tester);
    for (final word in ['Apply', 'Undo', 'Save', 'Propose']) {
      expect(text, isNot(contains(word)), reason: word);
    }
  });

  testWidgets('OpenCode 2: ordered sources as reported, never merged', (
    tester,
  ) async {
    final gateway = _Gateway()
      ..config = {
        'sources': [
          {
            'type': 'document',
            'path': '/home/me/.config/opencode/opencode.json',
            'info': {'model': 'example/global', 'mcp': <String, Object?>{}},
          },
          {
            'type': 'document',
            'path': '/projects/app/opencode.json',
            'info': {'model': 'example/project'},
          },
          {'type': 'inline'},
        ],
      }
      ..servers = const [SetupMcpStatus(name: 'docs', status: 'connected')];
    await open(tester, gateway: gateway);

    expect(find.text('Configuration sources'), findsOneWidget);
    expect(find.text('Settings in effect'), findsNothing);
    // No merged model is invented, so no model suggestion either.
    expect(find.text('Choose a default model'), findsNothing);
    final global = find.text('/home/me/.config/opencode/opencode.json');
    final project = find.text('/projects/app/opencode.json');
    expect(global, findsOneWidget);
    expect(project, findsOneWidget);
    expect(
      tester.getTopLeft(global).dy,
      lessThan(tester.getTopLeft(project).dy),
    );
    expect(find.text('1. Sets model, mcp'), findsOneWidget);
    expect(find.text('2. Sets model'), findsOneWidget);
    expect(find.text('Source without a file (inline)'), findsOneWidget);
    expect(find.text('3. Sets nothing'), findsOneWidget);
  });

  group('states', () {
    testWidgets('loading', (tester) async {
      final gateway = _Gateway()..hold = Completer<void>();
      final connection = await _connection();
      addTearDown(connection.dispose);
      final session = SetupSession(connection, gatewayFor: (_) => gateway);
      addTearDown(session.dispose);
      await tester.pumpWidget(
        _app(
          AiSetupScreen(
            controller: connection,
            serverName: 'Laptop',
            session: session,
          ),
        ),
      );
      await tester.pump();
      expect(find.byKey(const ValueKey('ai-setup-loading')), findsOneWidget);
      gateway.hold!.complete();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('ai-setup-empty')), findsOneWidget);
    });

    testWidgets('empty', (tester) async {
      await open(tester);
      expect(find.text('Nothing set up yet'), findsOneWidget);
    });

    testWidgets('unsupported', (tester) async {
      final (_, gateway, _) = await open(tester, supported: false);
      expect(find.text("AI setup isn't available"), findsOneWidget);
      expect(gateway.reads, 0);
      expect(find.byKey(const ValueKey('ai-setup-refresh')), findsNothing);
    });

    testWidgets('needs sign-in offers this server\'s sign-in', (tester) async {
      final gateway = _Gateway()
        ..failure = const SetupFailure(
          SetupFailureCode.needsSignIn,
          'Sign in to this server to inspect setup.',
        );
      await open(tester, gateway: gateway);
      expect(find.text('Sign-in needed'), findsOneWidget);
      expect(find.text('Change sign-in for Laptop'), findsOneWidget);
    });

    testWidgets('error: plain words, reason only under Details, Try again', (
      tester,
    ) async {
      final gateway = _Gateway()
        ..failure = const SetupFailure(
          SetupFailureCode.transport,
          'The server could not load setup information.',
        );
      await open(tester, gateway: gateway);
      expect(find.text("Couldn't read setup"), findsOneWidget);
      expect(
        find.text('The server could not load setup information.'),
        findsNothing,
      );

      gateway
        ..failure = null
        ..config = {'model': 'example/model'};
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('example/model'), findsOneWidget);
      expect(gateway.reads, 2);
    });

    testWidgets('offline before any read', (tester) async {
      final (connection, gateway, _) = await open(tester, connected: false);
      expect(find.text("You're offline"), findsOneWidget);
      expect(gateway.reads, 0);
      await tester.pumpWidget(const SizedBox.shrink());
      connection.dispose();
    });

    testWidgets('offline keeps the last read, reconnecting reads again', (
      tester,
    ) async {
      final gateway = _Gateway()..config = {'model': 'example/model'};
      final (connection, _, _) = await open(tester, gateway: gateway);
      expect(find.text('example/model'), findsOneWidget);

      connection.status = StreamStatus.disconnected;
      connection.notifyListeners();
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('ai-setup-offline-notice')),
        findsOneWidget,
      );
      expect(find.text('example/model'), findsOneWidget);
      expect(gateway.reads, 1);

      gateway.config = {'model': 'example/newer'};
      connection.status = StreamStatus.connected;
      connection.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.text('example/newer'), findsOneWidget);
      expect(gateway.reads, 2);
      await tester.pumpWidget(const SizedBox.shrink());
      connection.dispose();
    });
  });

  // A plain test: the owner's lifecycle runs on real microtasks.
  test('a location change recreates the setup owner and reads again', () async {
    final connection = await _connection();
    addTearDown(connection.dispose);
    final gateways = <_Gateway>[];
    final session = SetupSession(
      connection,
      gatewayFor: (_) {
        final gateway = _Gateway()
          ..config = {'model': 'example/${gateways.length}'};
        gateways.add(gateway);
        return gateway;
      },
    );
    addTearDown(session.dispose);
    await session.settled;
    expect(session.snapshot.config['model'], 'example/0');

    connection.directory = '/projects/other';
    connection.notifyListeners();
    await session.settled;
    expect(gateways, hasLength(2));
    expect(session.snapshot.config['model'], 'example/1');
  });
}
