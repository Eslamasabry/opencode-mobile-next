import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<(ProfileStore, ConnectionController)> _state() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return (store, ConnectionController(store));
}

/// Presents saved profiles without touching the real secure-storage channel,
/// which is unmocked in widget tests and would hang a real upsert.
class _SeededStore extends ProfileStore {
  _SeededStore({required super.prefs, required this.seeded});

  final List<ServerProfile> seeded;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);
}

Widget _app(
  ProfileStore store,
  ConnectionController controller, {
  double textScale = 1,
  Map<String, WidgetBuilder> routes = const {},
}) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(store)),
    connProvider.overrideWithValue(controller),
  ],
  child: MaterialApp(
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child ?? const SizedBox.shrink(),
    ),
    routes: routes,
    home: const ServersScreen(),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('bare pasted addresses gain the right scheme', () {
    expect(
      normalizeServerProfileUrl('192.168.1.7:4096'),
      'https://192.168.1.7:4096',
    );
    expect(
      normalizeServerProfileUrl('localhost:4096'),
      'http://localhost:4096',
    );
    expect(normalizeServerProfileUrl('127.0.0.1'), 'http://127.0.0.1');
    expect(
      normalizeServerProfileUrl('box.tail1234.ts.net'),
      'https://box.tail1234.ts.net',
    );
    // Already-schemed and implausible values pass through untouched.
    expect(
      normalizeServerProfileUrl('https://host:4096'),
      'https://host:4096',
    );
    expect(normalizeServerProfileUrl('not a url'), 'not a url');
    expect(normalizeServerProfileUrl(''), '');
  });

  testWidgets('first run shows the welcome paths instead of an empty list', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));

    expect(find.byKey(const ValueKey('first-run-welcome')), findsOneWidget);
    expect(find.text('Connect to your computer'), findsOneWidget);
    expect(find.text('Run OpenCode on this phone'), findsOneWidget);
    expect(find.text('Learn how OpenCode works'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
  });

  testWidgets('welcome routes reach the guide and Termux setup', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        store,
        controller,
        routes: {
          '/guide': (_) => Scaffold(
            appBar: AppBar(title: const Text('Guide')),
            body: const Text('guide-route'),
          ),
          '/termux-setup': (_) => Scaffold(
            appBar: AppBar(title: const Text('Termux')),
            body: const Text('termux-route'),
          ),
        },
      ),
    );

    await tester.tap(find.byKey(const ValueKey('welcome-guide-card')));
    await tester.pumpAndSettle();
    expect(find.text('guide-route'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('welcome-termux-card')));
    await tester.pumpAndSettle();
    expect(find.text('termux-route'), findsOneWidget);
  });

  testWidgets('a saved profile keeps the ordinary server list', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = _SeededStore(
      prefs: prefs,
      seeded: [
        ServerProfile(
          id: 'server-1',
          name: 'Workstation',
          baseUrl: 'https://box.example:4096',
          username: '',
          password: '',
        ),
      ],
    );
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));

    expect(find.byKey(const ValueKey('first-run-welcome')), findsNothing);
    expect(find.text('Workstation'), findsOneWidget);
    expect(find.text('Add server'), findsOneWidget);
  });

  testWidgets('welcome renders at 320dp with 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller, textScale: 2));

    expect(find.byKey(const ValueKey('first-run-welcome')), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('welcome-guide-card')),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.byKey(const ValueKey('welcome-guide-card')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('pasting a bare address into the editor fills the scheme', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      '192.168.1.7:4096',
    );
    await tester.pump();

    expect(
      tester
          .widget<TextField>(find.byKey(const ValueKey('server-url-field')))
          .controller
          ?.text,
      'https://192.168.1.7:4096',
    );
  });

  testWidgets('test connection reports success with the server version', (
    tester,
  ) async {
    final previous = serverProbe;
    addTearDown(() => serverProbe = previous);
    final probed = <String>[];
    serverProbe = ({required baseUrl, username, password}) async {
      probed.add(baseUrl);
      return const ServerProbeResult.success('1.18.23');
    };

    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'box.example:4096',
    );
    await tester.pump();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('test-server-connection')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const ValueKey('test-server-connection')));
    await tester.pumpAndSettle();

    expect(probed, ['https://box.example:4096']);
    expect(find.byKey(const ValueKey('server-test-success')), findsOneWidget);
    expect(find.textContaining('OpenCode 1.18.23'), findsOneWidget);
  });

  testWidgets('test connection explains a refused connection', (tester) async {
    final previous = serverProbe;
    addTearDown(() => serverProbe = previous);
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure(
          'The connection was refused. Is opencode serve running on that '
          'host and port?',
        );

    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://box.example:4096',
    );
    await tester.pump();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('test-server-connection')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const ValueKey('test-server-connection')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
    expect(find.textContaining('refused'), findsOneWidget);

    // Editing any field clears the stale verdict.
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://box.example:4097',
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('server-test-failure')), findsNothing);
  });

  testWidgets(
    'timeout and refused verdicts point at the host setup guide',
    (tester) async {
      final previous = serverProbe;
      addTearDown(() => serverProbe = previous);
      serverProbe = ({required baseUrl, username, password}) async =>
          const ServerProbeResult.failure(
            'The connection was refused. Is opencode serve running on that '
            'host and port?',
            suggestsMissingServer: true,
          );

      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          store,
          controller,
          routes: {
            '/guide': (_) => Scaffold(
              appBar: AppBar(title: const Text('Guide')),
              body: const Text('guide-route'),
            ),
          },
        ),
      );
      await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'https://box.example:4096',
      );
      await tester.pump();
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('test-server-connection')),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.tap(find.byKey(const ValueKey('test-server-connection')));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
      expect(find.textContaining('No server there yet?'), findsOneWidget);

      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('server-test-guide')),
        200,
        scrollable: find
            .descendant(
              of: find.byKey(const ValueKey('server-profile-fields')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('server-test-guide')));
      await tester.pumpAndSettle();
      expect(find.text('guide-route'), findsOneWidget);
    },
  );

  testWidgets('a DNS failure verdict stays about the address, not the server', (
    tester,
  ) async {
    final previous = serverProbe;
    addTearDown(() => serverProbe = previous);
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure(
          'That host name could not be found. Check the address spelling.',
        );

    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://box.exampel:4096',
    );
    await tester.pump();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('test-server-connection')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const ValueKey('test-server-connection')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
    expect(
      find.textContaining('Check the address spelling'),
      findsOneWidget,
    );
    expect(find.textContaining('No server there yet?'), findsNothing);
  });

  testWidgets('an invalid url never reaches the probe', (tester) async {
    final previous = serverProbe;
    addTearDown(() => serverProbe = previous);
    var calls = 0;
    serverProbe = ({required baseUrl, username, password}) async {
      calls += 1;
      return const ServerProbeResult.success(null);
    };

    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'ftp://box.example',
    );
    await tester.pump();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('test-server-connection')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const ValueKey('test-server-connection')));
    await tester.pumpAndSettle();

    expect(calls, 0);
    expect(
      find.text('Server URLs must use https://, or http:// for local Termux.'),
      findsOneWidget,
    );
  });
}
