// P3.9 "Add a computer in one path": the agent question and connection help
// folded into Add server's steps (what runs there -> Tailscale when that is
// the way -> address or pairing -> check -> ready). Every entry starts the
// same flow, a check that runs long offers to stop, errors explain inline,
// and the flow ends on a ready moment.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/server_editor.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = [...seeded];

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => null;

  @override
  Future<String?> secureStorageProblem() async => null;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((p) => p.id == profile.id)
      ..add(profile);
  }
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  final connected = <ServerProfile>[];

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connected.add(profile);
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example.net',
);

/// Servers, with [seeded] saved (none shows the first-run welcome).
Future<(_Store, _Connection)> _pumpServers(
  WidgetTester tester, {
  List<ServerProfile> seeded = const [],
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    seeded: seeded,
  );
  final connection = _Connection(store);
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(connection),
      ],
      child: MaterialApp(
        routes: {'/home': (_) => const Scaffold(body: Text('home-route'))},
        home: const ServersScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (store, connection);
}

Future<(_Store, _Connection)> _openAddServer(WidgetTester tester) async {
  final state = await _pumpServers(tester, seeded: [_laptop]);
  await tester.tap(find.byKey(const ValueKey('servers-add')));
  await tester.pumpAndSettle();
  return state;
}

Future<void> _typeAddress(WidgetTester tester, String address) async {
  await openServerManualAddress(tester);
  await tester.enterText(
    find.byKey(const ValueKey('server-url-field')),
    address,
  );
  await tester.pump();
}

Future<void> _tapKey(WidgetTester tester, String key) async {
  final target = find.byKey(ValueKey(key));
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

final _kindStep = find.byKey(const ValueKey('server-kind-step'));
final _tailscaleStep = find.byKey(const ValueKey('server-tailscale-step'));
final _readyStep = find.byKey(const ValueKey('server-ready-step'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const termux = MethodChannel('oc/termux');
  const tailscale = MethodChannel('oc/tailscale');

  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      termux,
      (call) async => call.method == 'getCapabilities'
          ? <String, Object>{'installed': false}
          : null,
    );
    messenger.setMockMethodCallHandler(
      tailscale,
      (call) async => call.method == 'check' ? 'installed' : true,
    );
    debugPlatformCapabilities = const PlatformCapabilities.android();
  });

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(termux, null);
    messenger.setMockMethodCallHandler(tailscale, null);
    serverProbe = probeServerConnection;
    debugPlatformCapabilities = null;
  });

  testWidgets('Add server and "On my computer" start the same flow', (
    tester,
  ) async {
    // Beside a saved server: the list's one Add.
    await _openAddServer(tester);
    expect(_kindStep, findsOneWidget);
    expect(find.text('Step 1 of 4 · What runs there'), findsOneWidget);
    expect(find.byKey(const ValueKey('server-backend-opencode')), findsOne);

    // First run: the welcome's "On my computer" lands on the same step,
    // not on a separate agent question.
    await tester.pumpWidget(const SizedBox.shrink());
    await _pumpServers(tester);
    await _tapKey(tester, 'welcome-choice-computer');
    expect(_kindStep, findsOneWidget);
    expect(find.text('Step 1 of 4 · What runs there'), findsOneWidget);
    expect(find.text('Which agent first?'), findsNothing);
  });

  testWidgets('a kind answers the first step and Back returns to it', (
    tester,
  ) async {
    await _openAddServer(tester);
    await chooseServerKind(tester, kind: 'codex');
    expect(_kindStep, findsNothing);
    expect(find.text('Step 2 of 4 · Address and sign-in'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('codex-server-address-field')),
      findsOneWidget,
    );

    await _tapKey(tester, 'server-editor-back');
    expect(_kindStep, findsOneWidget);
    // Nothing was typed, so Close leaves without asking.
    await _tapKey(tester, 'server-editor-close');
    expect(find.byKey(const ValueKey('server-profile-editor')), findsNothing);
  });

  testWidgets('a check that runs long offers Cancel, and its answer is '
      'then ignored', (tester) async {
    final (store, connection) = await _openAddServer(tester);
    await chooseServerKind(tester);
    final pending = Completer<ServerProbeResult>();
    serverProbe = ({required baseUrl, username, password}) => pending.future;
    await _typeAddress(tester, 'https://build.example.net');
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pump();

    // The check is a step of its own while it runs.
    expect(find.text('Step 3 of 4 · Checking'), findsOneWidget);
    final cancel = find.byKey(const ValueKey('server-check-cancel'));
    await tester.pump(slowCheckAfter - const Duration(milliseconds: 500));
    expect(cancel, findsNothing);
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(milliseconds: 300));
    expect(cancel, findsOneWidget);
    expect(
      find.text(
        'build.example.net has not answered yet. A slow network can take a '
        'while.',
      ),
      findsOneWidget,
    );

    await tester.tap(cancel);
    await tester.pumpAndSettle();
    expect(cancel, findsNothing);
    expect(find.text('Step 2 of 4 · Pair or enter the address'), findsOne);
    final save = tester.widget<FilledButton>(
      find.descendant(
        of: find.byKey(const ValueKey('save-server-profile')),
        matching: find.byType(FilledButton),
      ),
    );
    expect(save.onPressed, isNotNull);

    // The late answer changes nothing: no verdict, nothing saved.
    pending.complete(const ServerProbeResult.success('2.0.10'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-test-success')), findsNothing);
    expect(store.saved.map((p) => p.id), ['laptop']);
    expect(connection.connected, isEmpty);
  });

  testWidgets('a quick check never offers Cancel', (tester) async {
    await _openAddServer(tester);
    await chooseServerKind(tester);
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure('The connection was refused.');
    await _typeAddress(tester, 'https://build.example.net');
    await _tapKey(tester, 'test-server-connection');
    await tester.pump(slowCheckAfter + const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-check-cancel')), findsNothing);
    expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
  });

  testWidgets('the flow ends in a ready moment, whose one way on opens the '
      'server', (tester) async {
    final (store, connection) = await _openAddServer(tester);
    await chooseServerKind(tester);
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('2.0.10', flavor: ServerFlavor.v2);
    await _typeAddress(tester, 'https://build.example.net');
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    final added = store.saved.singleWhere((p) => p.id != 'laptop');
    expect(connection.connected.single.id, added.id);
    expect(_readyStep, findsOneWidget);
    expect(find.text('Step 4 of 4 · Ready'), findsOneWidget);
    expect(find.text('build.example.net is connected'), findsOneWidget);
    expect(find.text('Connected to build.example.net'), findsOneWidget);
    expect(find.byKey(const ValueKey('server-link-linked')), findsOneWidget);
    expect(find.text('home-route'), findsNothing);

    await tester.tap(find.text('Open build.example.net'));
    await tester.pumpAndSettle();
    expect(find.text('home-route'), findsOneWidget);
  });

  testWidgets('closing the ready moment also lands in the app', (tester) async {
    await _openAddServer(tester);
    await chooseServerKind(tester);
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('2.0.10', flavor: ServerFlavor.v2);
    await _typeAddress(tester, 'https://build.example.net');
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(_readyStep, findsOneWidget);
    await _tapKey(tester, 'server-editor-close');
    expect(find.text('home-route'), findsOneWidget);
  });

  testWidgets('Tailscale is a step of the flow, not a page it leaves for', (
    tester,
  ) async {
    final (store, _) = await _openAddServer(tester);
    await _tapKey(tester, 'welcome-tailscale-card');

    expect(_tailscaleStep, findsOneWidget);
    expect(find.byType(TailscaleSetupScreen), findsNothing);
    expect(find.byType(TailscalePhoneSteps), findsOneWidget);
    expect(find.text('Step 2 of 5 · Tailscale on this phone'), findsOneWidget);

    await _tapKey(tester, 'server-tailscale-continue');
    expect(find.text('Step 3 of 5 · Pair or enter the address'), findsOne);
    // The tailnet address is typed here; pairing and the command are not
    // the way on this path.
    expect(find.byKey(const ValueKey('server-url-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('connect-command')), findsNothing);

    // Back walks the steps: Tailscale, then the first step.
    await _tapKey(tester, 'server-editor-back');
    expect(_tailscaleStep, findsOneWidget);
    await _tapKey(tester, 'server-editor-back');
    expect(_kindStep, findsOneWidget);

    // Saving through it remembers the way.
    await _tapKey(tester, 'welcome-tailscale-card');
    await _tapKey(tester, 'server-tailscale-continue');
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('2.0.10', flavor: ServerFlavor.v2);
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://work.example.ts.net',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    final added = store.saved.singleWhere((p) => p.id != 'laptop');
    expect(store.prefs.getBool('oc.tailscale.${added.id}'), isTrue);
    expect(_readyStep, findsOneWidget);
  });

  testWidgets('a public http:// address explains itself and offers '
      'Tailscale', (tester) async {
    var probes = 0;
    serverProbe = ({required baseUrl, username, password}) async {
      probes++;
      return const ServerProbeResult.success('2.0.10');
    };
    await _openAddServer(tester);
    await chooseServerKind(tester);
    await _typeAddress(tester, 'http://192.0.2.20:4096');
    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();

    expect(probes, 0);
    final advice = find.byKey(const ValueKey('server-remote-http-advice'));
    expect(advice, findsOneWidget);
    expect(find.textContaining('public'), findsNothing);
    expect(find.textContaining('relay'), findsNothing);
    await _tapKey(tester, 'server-remote-http-tailscale');
    expect(_tailscaleStep, findsOneWidget);
  });

  testWidgets('a password typed before stepping back is held, never shown', (
    tester,
  ) async {
    const typed = 'fixture-not-a-live-password';
    String? sent;
    serverProbe = ({required baseUrl, username, password}) async {
      sent = password;
      return const ServerProbeResult.success('2.0.10');
    };
    await _openAddServer(tester);
    await _tapKey(tester, 'welcome-tailscale-card');
    await _tapKey(tester, 'server-tailscale-continue');
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      'https://work.example.ts.net',
    );
    await tester.enterText(
      find.byKey(const ValueKey('server-password-field')),
      typed,
    );
    await tester.pump();
    await _tapKey(tester, 'server-editor-back');
    await _tapKey(tester, 'server-tailscale-continue');

    expect(
      find.byKey(const ValueKey('server-password-replace')),
      findsOneWidget,
    );
    for (final text in tester.widgetList<EditableText>(
      find.byType(EditableText),
    )) {
      expect(text.controller.text, isNot(contains(typed)));
    }
    await _tapKey(tester, 'test-server-connection');
    expect(sent, typed);
  });
}
