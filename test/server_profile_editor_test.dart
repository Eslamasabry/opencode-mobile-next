import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';

class _RecordingProfileStore extends ProfileStore {
  _RecordingProfileStore({required super.prefs});

  final saved = <ServerProfile>[];

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => null;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..clear()
      ..add(profile);
  }
}

/// Records connect attempts without touching the network; on success it
/// installs a transport so ServersScreen sees the connection as established.
class _RecordingConnection extends ConnectionController {
  _RecordingConnection(super.store, {this.succeed = true});

  final bool succeed;
  final connected = <ServerProfile>[];

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connected.add(profile);
    if (succeed) {
      api = FakeServerGateway(baseUrl: profile.baseUrl);
    } else {
      api = null;
      lastError = 'Cannot reach ${profile.baseUrl}: refused';
    }
  }
}

Future<(_RecordingProfileStore, ConnectionController)> _state() async {
  SharedPreferences.setMockInitialValues({});
  final store = _RecordingProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (store, _RecordingConnection(store));
}

Widget _app(
  _RecordingProfileStore store,
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
    routes: {
      '/home': (_) => const Scaffold(body: Text('home-route')),
      ...routes,
    },
    home: const ServersScreen(),
  ),
);

Future<void> _openEditor(WidgetTester tester) async {
  // With no saved profile the Servers screen shows the first-run welcome;
  // its connect card is the path into the editor. Large text scales can push
  // the card below the fold, so bring it fully on screen before tapping.
  await openFirstRunConnect(tester);
  // Pairing leads; the address is under "Enter the address instead".
  await openServerManualAddress(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const termuxChannel = MethodChannel('oc/termux');
  setUp(() {
    // Save & connect checks the connection first; here it answers, so the
    // save and connect behaviour below is what is under test.
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('1.0.0');
    // These editor tests have no local Termux installation. Discovery must
    // receive a concrete platform answer instead of an unanswered channel.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(termuxChannel, (call) async {
          expect(call.method, 'getCapabilities');
          return {'installed': false};
        });
  });
  tearDown(() {
    serverProbe = probeServerConnection;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(termuxChannel, null);
  });
  testWidgets(
    'Tailscale reaches existing authentication, retains draft through handoff, and retries a failed probe',
    (tester) async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const channel = MethodChannel('oc/tailscale');
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => call.method == 'check' ? 'installed' : true,
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final oldProbe = serverProbe;
      var probes = 0;
      serverProbe = ({required baseUrl, username, password}) async {
        probes++;
        expect(baseUrl, 'https://work.example.ts.net');
        expect(password, 'synthetic-password');
        return probes == 1
            ? const ServerProbeResult.failure(
                'Server unreachable',
                suggestsMissingServer: true,
              )
            : const ServerProbeResult.success('test');
      };
      addTearDown(() => serverProbe = oldProbe);
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      // The Tailscale door left the first-run welcome (UX plan 5.4); on this
      // screen it is offered once there are servers to manage.
      store.saved.add(
        ServerProfile(
          id: 'existing',
          name: 'Existing',
          baseUrl: 'https://existing.example',
        ),
      );
      await tester.pumpWidget(_app(store, controller));
      await tester.pumpAndSettle();
      // Tailscale is one of Add server's ways in (R3).
      await tester.tap(find.byKey(const ValueKey('servers-add')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('welcome-tailscale-card')),
      );
      await tester.tap(find.byKey(const ValueKey('welcome-tailscale-card')));
      await tester.pumpAndSettle();
      // Tailscale is a step of Add server (P3.9), not a page it leaves for.
      expect(
        find.byKey(const ValueKey('server-tailscale-step')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('server-tailscale-continue')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'work.example.ts.net',
      );
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      expect(probes, 0);
      expect(store.saved.single.id, 'existing');
      expect(
        find.byKey(const ValueKey('server-backend-selector')),
        findsNothing,
      );
      final password = find.byKey(const ValueKey('server-password-field'));
      await tester.scrollUntilVisible(
        password,
        220,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.enterText(password, 'synthetic-password');
      tester.testTextInput.hide();
      await tester.scrollUntilVisible(
        find.text('Test connection'),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Test connection'));
      await tester.pumpAndSettle();
      expect(probes, 1);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('server-test-failure')),
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Tailscale setup and recovery'),
        -220,
        scrollable: find.byType(Scrollable).first,
      );
      // Back to the Tailscale step, and on again: the draft is kept.
      await tester.ensureVisible(find.text('Tailscale setup and recovery'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tailscale setup and recovery'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Open Tailscale'));
      await tester.tap(find.text('Open Tailscale'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('server-tailscale-continue')));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('server-password-replace')),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      // The typed password is held, never put back into a field (SEC-3);
      // the next check still sends it.
      expect(
        find.byKey(const ValueKey('server-password-replace')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<TextField>(
              find.descendant(
                of: find.byKey(const ValueKey('server-url-field')),
                matching: find.byType(TextField),
              ),
            )
            .controller!
            .text,
        'https://work.example.ts.net',
      );
      tester.testTextInput.hide();
      await tester.scrollUntilVisible(
        find.text('Test connection'),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Test connection'));
      await tester.pumpAndSettle();
      expect(probes, 2);
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();
      expect(store.saved.single.password, 'synthetic-password');
      expect(
        store.prefs.getBool('oc.tailscale.${store.saved.single.id}'),
        isTrue,
      );
      await openReadyServer(tester);
      expect(find.text('home-route'), findsOneWidget);
    },
  );

  testWidgets(
    'saved Tailscale profile keeps guidance and rejects HTTP before probe or connect',
    (tester) async {
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      final profile = ServerProfile(
        id: 'private-server',
        name: 'Private server',
        baseUrl: 'https://work.example.ts.net',
        username: '',
        password: '',
      );
      store.saved.add(profile);
      await store.prefs.setBool('oc.tailscale.private-server', true);
      final oldProbe = serverProbe;
      var probes = 0;
      serverProbe = ({required baseUrl, username, password}) async {
        probes++;
        return const ServerProbeResult.success('test');
      };
      addTearDown(() => serverProbe = oldProbe);
      await tester.pumpWidget(_app(store, controller));
      await tester.longPress(
        find
            .descendant(
              of: find.byKey(const ValueKey('saved-server-rows')),
              matching: find.byType(KitRow),
            )
            .first,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Edit'));
      await tester.pumpAndSettle();
      expect(find.text('Tailscale setup and recovery'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'http://100.64.0.1:4096',
      );
      tester.testTextInput.hide();
      await tester.scrollUntilVisible(
        find.text('Test connection'),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Test connection'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();
      expect(probes, 0);
      expect((controller as _RecordingConnection).connected, isEmpty);
      expect(store.saved.single.baseUrl, 'https://work.example.ts.net');
    },
  );

  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('adding a server hides the app-wide line about the current '
      'server', (tester) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    final conditions = ValueNotifier<List<KitStatus>>(const [
      KitStatus(
        kind: KitStatusKind.connection,
        icon: AppIconography.restart,
        message: 'Old server is not answering',
      ),
    ]);
    addTearDown(conditions.dispose);
    await tester.pumpWidget(
      KitStatusScope(conditions: conditions, child: _app(store, controller)),
    );
    await tester.pumpAndSettle();
    await _openEditor(tester);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.text('Old server is not answering'), findsNothing);
  });

  testWidgets('the last field scrolls clear of the pinned Save & connect', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);
    final more = find.byKey(
      const ValueKey('server-editor-more-options-header'),
    );
    await tester.drag(
      find.byKey(const ValueKey('server-profile-fields')),
      const Offset(0, -2000),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getBottomLeft(more).dy,
      lessThanOrEqualTo(
        tester.getTopLeft(find.byKey(const ValueKey('save-server-profile'))).dy,
      ),
    );
  });

  testWidgets('remote setup opens a full-screen URL-first editor', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    // Titled with what was chosen on Add server's first step.
    expect(find.widgetWithText(KitTopBar, 'OpenCode'), findsOneWidget);
    expect(find.text('Save & connect'), findsOneWidget);
    // Address and password only; the rest waits under More options.
    expect(find.text('AUTHENTICATION'), findsNothing);
    expect(find.text('More options'), findsOneWidget);
    expect(find.byKey(const ValueKey('server-username-field')), findsNothing);

    final url = tester.widget<TextField>(
      find.descendant(
        of: find.byKey(const ValueKey('server-url-field')),
        matching: find.byType(TextField),
      ),
    );
    expect(url.focusNode?.hasFocus, isFalse);
    expect(url.textInputAction, TextInputAction.next);
  });

  testWidgets(
    'server editor saves normalized credentials and reveals password',
    (tester) async {
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await _openEditor(tester);

      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'HTTPS://server.example:4096/',
      );
      await openServerMoreOptions(tester);
      await tester.enterText(
        find.byKey(const ValueKey('server-username-field')),
        'opencode',
      );
      await tester.enterText(
        find.byKey(const ValueKey('server-password-field')),
        'test-secret',
      );
      await tester.pump();
      await tester.ensureVisible(
        find.byKey(const ValueKey('server-password-visibility')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('server-password-visibility')),
      );
      await tester.pump();

      expect(
        tester
            .widget<TextField>(
              find.descendant(
                of: find.byKey(const ValueKey('server-password-field')),
                matching: find.byType(TextField),
              ),
            )
            .obscureText,
        isFalse,
      );
      expect(find.byTooltip('Hide Server password'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();

      final profile = store.saved.single;
      expect(profile.name, 'server.example');
      expect(profile.baseUrl, 'https://server.example:4096');
      expect(profile.username, 'opencode');
      expect(profile.password, 'test-secret');
    },
  );

  testWidgets('editor remains usable above a compact large-text keyboard', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller, textScale: 2));
    await _openEditor(tester);
    tester.view.viewInsets = const FakeViewPadding(bottom: 260);
    await tester.pumpAndSettle();

    final save = find.byKey(const ValueKey('save-server-profile'));
    expect(save, findsOneWidget);
    expect(tester.getRect(save).bottom, lessThan(380));

    await tester.tap(save);
    await tester.pumpAndSettle();
    // The URL field now starts empty (no https:// pre-seed), so an empty
    // save surfaces the enter-a-URL error instead of the incomplete-URL one.
    expect(find.text('Enter a server URL.'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('server-password-field')),
      180,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('server-profile-fields')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('server-password-field')), findsOneWidget);
    expect(save, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('saving a new profile auto-connects and lands in the app', (
    tester,
  ) async {
    final (store, controller) = await _state();
    final connection = controller as _RecordingConnection;
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://server.example:4096',
    );
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    // "Save to finish" holds: the saved profile connected and the servers
    // list was left for the product shell.
    expect(connection.connected, hasLength(1));
    expect(connection.connected.single.baseUrl, 'https://server.example:4096');
    expect(store.saved, hasLength(1));
    await openReadyServer(tester);
    expect(find.text('home-route'), findsOneWidget);
  });

  testWidgets('a new profile that cannot connect stays saved with guidance', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final connection = _RecordingConnection(store, succeed: false);
    addTearDown(connection.dispose);
    await tester.pumpWidget(_app(store, connection));
    await _openEditor(tester);

    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://server.example:4096',
    );
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(connection.connected, hasLength(1));
    expect(store.saved, hasLength(1));
    expect(find.text('home-route'), findsNothing);
    expect(
      find.textContaining('was saved, but it could not connect'),
      findsOneWidget,
    );
  });

  testWidgets('editing a saved non-active profile never auto-connects', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    store.saved.add(
      ServerProfile(
        id: 'server-1',
        name: 'Workstation',
        baseUrl: 'https://box.example:4096',
        username: '',
        password: '',
      ),
    );
    final connection = _RecordingConnection(store);
    addTearDown(connection.dispose);
    await tester.pumpWidget(_app(store, connection));

    await tester.longPress(
      find
          .descendant(
            of: find.byKey(const ValueKey('saved-server-rows')),
            matching: find.byType(KitRow),
          )
          .first,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();

    await openServerMoreOptions(tester);

    await tester.enterText(
      find.byKey(const ValueKey('server-name-field')),
      'Renamed box',
    );
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(connection.connected, isEmpty);
    expect(store.saved.single.name, 'Renamed box');
    expect(find.text('home-route'), findsNothing);
  });

  testWidgets('closing a changed server editor requires confirmation', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = _RecordingProfileStore(
      prefs: await SharedPreferences.getInstance(),
    );
    store.saved.add(
      ServerProfile(
        id: 'server-1',
        name: 'Workstation',
        baseUrl: 'https://box.example:4096',
        username: '',
        password: '',
      ),
    );
    final controller = _RecordingConnection(store);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.longPress(
      find
          .descendant(
            of: find.byKey(const ValueKey('saved-server-rows')),
            matching: find.byType(KitRow),
          )
          .first,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();

    await openServerMoreOptions(tester);
    await tester.enterText(
      find.byKey(const ValueKey('server-name-field')),
      'Renamed box',
    );
    await tester.tap(find.byKey(const ValueKey('server-editor-close')));
    await tester.pumpAndSettle();

    expect(find.text('Discard server changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
  });

  // Emulator QA B4: Close on "What runs there" asked "Discard server
  // changes?" although that step shows nothing typed.
  testWidgets('closing the first step of Add server never asks', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    Future<void> openAddServer() async {
      await chooseComputerOnWelcome(tester);
    }

    await openAddServer();

    // Straight from the first step: nothing entered, nothing asked.
    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('server-editor-close')));
    await tester.pumpAndSettle();
    expect(find.text('Discard server changes?'), findsNothing);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsNothing);

    // Back from an address that was started, then Close: the first step
    // still has nothing of its own to lose.
    await openAddServer();
    await openServerManualAddress(tester);
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://box.example',
    );
    await tester.tap(find.byKey(const ValueKey('server-editor-back')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('server-editor-close')));
    await tester.pumpAndSettle();
    expect(find.text('Discard server changes?'), findsNothing);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsNothing);
    expect(store.saved, isEmpty);
  });
}
