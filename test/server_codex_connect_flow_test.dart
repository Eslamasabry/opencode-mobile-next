import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/state/codex_connection_probe.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RecordingProfileStore extends ProfileStore {
  _RecordingProfileStore({required super.prefs});

  final saved = <ServerProfile>[];

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => null;

  @override
  Future<String?> secureStorageProblem() async => null;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..clear()
      ..add(profile);
  }
}

class _RecordingConnection extends ConnectionController {
  _RecordingConnection(super.store, {this.succeed = true, this.connectGate});

  final bool succeed;
  final Completer<void>? connectGate;
  final connected = <ServerProfile>[];

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connected.add(profile);
    final gate = connectGate;
    if (gate != null) await gate.future;
    if (!succeed) {
      api = null;
      lastError = 'The Codex server could not be reached.';
      return;
    }
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }
}

Widget _app(_RecordingProfileStore store, ConnectionController controller) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(controller),
      ],
      child: MaterialApp(
        routes: {'/home': (_) => const Scaffold(body: Text('home-route'))},
        home: const ServersScreen(),
      ),
    );

Future<void> _openEditor(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
  await tester.pumpAndSettle();
}

class _ProbeSocket implements CodexSocket {
  final input = StreamController<Object?>.broadcast(sync: true);
  final sent = <Map<String, dynamic>>[];
  bool closed = false;

  @override
  Stream<Object?> get messages => input.stream;

  @override
  void send(String message) {
    final request = jsonDecode(message) as Map<String, dynamic>;
    sent.add(request);
    final id = request['id'];
    switch (request['method']) {
      case 'initialize':
        input.add(
          jsonEncode({
            'id': id,
            'result': {'userAgent': 'codex'},
          }),
        );
      case 'thread/list':
        input.add(
          jsonEncode({
            'id': id,
            'result': {'data': []},
          }),
        );
    }
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await input.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Codex add flow uses dedicated fields and saves its scope', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final controller = _RecordingConnection(store);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    expect(
      find.byKey(const ValueKey('server-backend-selector')),
      findsOneWidget,
    );
    expect(find.widgetWithText(ChoiceChip, 'OpenCode'), findsOneWidget);
    await tester.tap(find.text('Codex (experimental)'));
    await tester.pump();

    expect(
      find.byKey(const ValueKey('codex-server-name-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('codex-server-address-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('codex-project-directory-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('codex-connection-token-field')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('server-pairing-actions')), findsNothing);
    expect(find.byKey(const ValueKey('server-password-field')), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('codex-server-name-field')),
      'Remote Codex',
    );
    await tester.enterText(
      find.byKey(const ValueKey('codex-server-address-field')),
      'ws://127.0.0.1:4500',
    );
    await tester.enterText(
      find.byKey(const ValueKey('codex-project-directory-field')),
      '/work/project',
    );
    await tester.enterText(
      find.byKey(const ValueKey('codex-connection-token-field')),
      'token-value',
    );
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    final profile = store.saved.single;
    expect(profile.backend, ServerBackend.codex);
    expect(profile.baseUrl, 'ws://127.0.0.1:4500');
    expect(profile.codexDirectory, '/work/project');
    expect(profile.codexToken, 'token-value');
    expect(controller.connected.single.backend, ServerBackend.codex);
  });

  testWidgets('Codex save failure keeps token and project inputs editable', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final controller = _RecordingConnection(store, succeed: false);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);
    await tester.tap(find.text('Codex (experimental)'));
    await tester.pump();
    await tester.enterText(
      find.byKey(const ValueKey('codex-server-address-field')),
      'ws://127.0.0.1:4500',
    );
    await tester.enterText(
      find.byKey(const ValueKey('codex-project-directory-field')),
      '/work/project',
    );
    await tester.enterText(
      find.byKey(const ValueKey('codex-connection-token-field')),
      'token-value',
    );
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('codex-project-directory-field')),
          )
          .controller
          ?.text,
      '/work/project',
    );
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('codex-connection-token-field')),
          )
          .controller
          ?.text,
      'token-value',
    );
    expect(find.byKey(const ValueKey('server-save-failure')), findsOneWidget);
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('codex-project-directory-field')),
          )
          .enabled,
      isTrue,
    );
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('codex-connection-token-field')),
          )
          .enabled,
      isTrue,
    );
  });

  testWidgets(
    'A failed save error retires after an edited credential passes Test connection',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = _RecordingProfileStore(
        prefs: await SharedPreferences.getInstance(),
      );
      final controller = _RecordingConnection(store, succeed: false);
      addTearDown(controller.dispose);
      final previousProbe = serverProbe;
      addTearDown(() => serverProbe = previousProbe);
      serverProbe = ({required baseUrl, username, password}) async =>
          const ServerProbeResult.success('2.0.0', flavor: ServerFlavor.v2);

      await tester.pumpWidget(_app(store, controller));
      await _openEditor(tester);
      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'https://box.example:4097',
      );
      await tester.enterText(
        find.byKey(const ValueKey('server-password-field')),
        'initial-token',
      );
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('server-save-failure')), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('server-password-field')),
        'replacement-token',
      );
      await tester.pump();
      expect(find.byKey(const ValueKey('server-save-failure')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('test-server-connection')));
      await tester.pumpAndSettle();
      await tester.drag(
        find.byKey(const ValueKey('server-profile-fields')),
        const Offset(0, -300),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('server-save-failure')), findsNothing);
      expect(
        find.byKey(const ValueKey('server-probe-verdict')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('server-password-field')),
            )
            .controller
            ?.text,
        'replacement-token',
      );
    },
  );

  testWidgets('Editing an existing Codex profile connects and opens Home', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    store.saved.add(
      ServerProfile(
        id: 'codex-1',
        name: 'Remote Codex',
        baseUrl: 'ws://127.0.0.1:4500',
        backend: ServerBackend.codex,
        codexDirectory: '/work/project',
        codexToken: 'token-value',
      ),
    );
    final controller = _RecordingConnection(store);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(controller.connected, hasLength(1));
    expect(controller.connected.single.backend, ServerBackend.codex);
    expect(find.text('home-route'), findsOneWidget);
  });

  testWidgets(
    'Codex submit freezes fields and backend until connect completes',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = _RecordingProfileStore(
        prefs: await SharedPreferences.getInstance(),
      );
      final gate = Completer<void>();
      final controller = _RecordingConnection(store, connectGate: gate);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await _openEditor(tester);
      await tester.tap(find.text('Codex (experimental)'));
      await tester.pump();
      await tester.enterText(
        find.byKey(const ValueKey('codex-server-name-field')),
        'Remote Codex',
      );
      await tester.enterText(
        find.byKey(const ValueKey('codex-server-address-field')),
        'ws://127.0.0.1:4500',
      );
      await tester.enterText(
        find.byKey(const ValueKey('codex-project-directory-field')),
        '/work/project',
      );
      await tester.enterText(
        find.byKey(const ValueKey('codex-connection-token-field')),
        'token-value',
      );
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pump();

      expect(controller.connected, hasLength(1));
      final submitted = controller.connected.single;
      expect(submitted.backend, ServerBackend.codex);
      expect(submitted.codexDirectory, '/work/project');
      expect(submitted.codexToken, 'token-value');
      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('codex-project-directory-field')),
            )
            .enabled,
        isFalse,
      );
      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('codex-connection-token-field')),
            )
            .enabled,
        isFalse,
      );
      expect(
        tester
            .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'OpenCode'))
            .onSelected,
        isNull,
      );

      // A test attempt against disabled controls must leave both the submitted
      // snapshot and the editor values unchanged while connect is pending.
      await tester.tap(
        find.byKey(const ValueKey('codex-project-directory-field')),
        warnIfMissed: false,
      );
      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('codex-project-directory-field')),
            )
            .focusNode!
            .hasFocus,
        isFalse,
      );
      await tester.tap(
        find.widgetWithText(ChoiceChip, 'OpenCode'),
        warnIfMissed: false,
      );
      await tester.pump();
      expect(submitted.backend, ServerBackend.codex);
      expect(submitted.codexDirectory, '/work/project');
      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('codex-project-directory-field')),
            )
            .controller
            ?.text,
        '/work/project',
      );

      gate.complete();
      await tester.pumpAndSettle();
      expect(find.text('home-route'), findsOneWidget);
      expect(store.saved.single.codexDirectory, '/work/project');
    },
  );

  test(
    'Codex probe performs authenticated handshake and scoped thread read',
    () async {
      final socket = _ProbeSocket();
      String? endpoint;
      String? capturedToken;
      String? capturedDirectory;
      final result = await probeCodexConnection(
        baseUrl: 'ws://127.0.0.1:4500',
        token: 'token-value',
        directory: '/work/project',
        gatewayFactory:
            ({
              required String baseUrl,
              required String token,
              required String directory,
            }) {
              endpoint = baseUrl;
              capturedToken = token;
              capturedDirectory = directory;
              return CodexGateway(
                transport: CodexTransport(
                  endpoint: baseUrl,
                  token: token,
                  socketFactory: (_, _) async => socket,
                ),
                directory: directory,
              );
            },
      );

      expect(result.ok, isTrue);
      expect(endpoint, 'ws://127.0.0.1:4500');
      expect(capturedToken, 'token-value');
      expect(capturedDirectory, '/work/project');
      expect(socket.sent.map((request) => request['method']), [
        'initialize',
        'initialized',
        'thread/list',
      ]);
      expect(socket.sent.last['params'], containsPair('cwd', '/work/project'));
      expect(socket.closed, isTrue);
    },
  );
}
