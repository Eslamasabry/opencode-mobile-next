import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

class _RecordingConnection extends ConnectionController {
  _RecordingConnection(super.store);

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {}
}

Future<(_RecordingProfileStore, ConnectionController)> _state() async {
  SharedPreferences.setMockInitialValues({});
  final store = _RecordingProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (store, _RecordingConnection(store));
}

Widget _app(
  ProfileStore store,
  ConnectionController controller,
) => ProviderScope(
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
  final connect = find.byKey(const ValueKey('welcome-connect-card'));
  await tester.ensureVisible(connect);
  await tester.pumpAndSettle();
  await tester.tap(connect);
  await tester.pumpAndSettle();
}

/// The editor's own field list. `.first` because every text field carries its
/// own internal `Scrollable` under the same list; the outermost is the list.
final Finder _editorList = find
    .descendant(
      of: find.byKey(const ValueKey('server-profile-fields')),
      matching: find.byType(Scrollable),
    )
    .first;

/// Brings an editor row into view.
///
/// The rows live in a `ListView`, so a row below the fold is not merely
/// off-screen — it is not built at all, and `ensureVisible` on its own cannot
/// reach it. Scroll first, then settle it into view.
Future<void> _reveal(WidgetTester tester, Finder target) async {
  if (target.evaluate().isEmpty) {
    await tester.scrollUntilVisible(target, 120, scrollable: _editorList);
  }
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
}

Future<void> _enter(WidgetTester tester, String key, String text) async {
  final field = find.byKey(ValueKey(key));
  await _reveal(tester, field);
  await tester.enterText(field, text);
  await tester.pump();
}

Future<void> _test(WidgetTester tester) async {
  final button = find.byKey(const ValueKey('test-server-connection'));
  await _reveal(tester, button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => serverProbe = probeServerConnection);

  testWidgets('a v2 401 without a password focuses the password field', (
    tester,
  ) async {
    String? probedPassword;
    serverProbe = ({required baseUrl, username, password}) async {
      probedPassword = password;
      return const ServerProbeResult.failure(
        'This server requires its serve password.',
        flavor: ServerFlavor.v2,
        needsPassword: true,
      );
    };
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await _enter(tester, 'server-url-field', 'https://box.example:4097');
    await _test(tester);

    expect(probedPassword, isEmpty);
    expect(find.byKey(const ValueKey('server-probe-verdict')), findsOneWidget);
    expect(find.text('This is an OpenCode 2 server.'), findsOneWidget);
    expect(
      find.text('This server requires its serve password.'),
      findsOneWidget,
    );
    final password = tester.widget<TextField>(
      find.byKey(const ValueKey('server-password-field')),
    );
    expect(password.focusNode?.hasFocus, isTrue);
  });

  testWidgets('a rejected password shows the rotation verdict selected', (
    tester,
  ) async {
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure(
          'Password rejected. Copy the current "server password" line from '
          'the server output — it changes on every restart unless '
          'OPENCODE_PASSWORD is set.',
          flavor: ServerFlavor.v2,
          needsPassword: true,
        );
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await _enter(tester, 'server-url-field', 'https://box.example:4097');
    await _enter(tester, 'server-password-field', 'stale-password');
    await _test(tester);

    expect(find.textContaining('Password rejected'), findsOneWidget);
    // Select-all primes a clean repaste of the rotated password.
    final controllerText = tester
        .widget<TextField>(find.byKey(const ValueKey('server-password-field')))
        .controller!;
    expect(controllerText.selection.baseOffset, 0);
    expect(
      controllerText.selection.extentOffset,
      'stale-password'.length,
    );
  });

  testWidgets('a v2 success shows the flavor headline and saves the flavor', (
    tester,
  ) async {
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success(
          '0.0.0-beta-18600',
          flavor: ServerFlavor.v2,
        );
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await _enter(tester, 'server-url-field', 'https://box.example:4097');
    await _enter(tester, 'server-password-field', 'the-serve-password');
    await _test(tester);

    expect(find.text('OpenCode 2 · 0.0.0-beta-18600'), findsOneWidget);
    expect(find.text('Connected — save to finish.'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    final profile = store.saved.single;
    expect(profile.flavor, ServerFlavor.v2);
    expect(profile.serverVersion, '0.0.0-beta-18600');
    expect(profile.password, 'the-serve-password');
  });

  testWidgets('a v1 success is labeled limited and stays saveable', (
    tester,
  ) async {
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('0.3.5', flavor: ServerFlavor.v1);
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await _enter(tester, 'server-url-field', 'https://box.example:4096');
    await _test(tester);

    expect(find.text('OpenCode 1 · 0.3.5 — limited feature set'), findsOneWidget);
    expect(
      find.text(
        'This app targets OpenCode 2; some features are unavailable on v1 '
        'servers.',
      ),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const ValueKey('save-server-profile')));
    await tester.pumpAndSettle();
    expect(store.saved.single.flavor, ServerFlavor.v1);
  });

  testWidgets('the paste affordance fills the password from the clipboard', (
    tester,
  ) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.getData') {
          return const {'text': '  server password abc123DEF456==  '};
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    final paste = find.byKey(const ValueKey('server-password-paste'));
    await _reveal(tester, paste);
    await tester.tap(paste);
    await tester.pumpAndSettle();

    // The copied `server password ` line prefix and whitespace are trimmed.
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('server-password-field')),
          )
          .controller!
          .text,
      'abc123DEF456==',
    );
  });

  testWidgets('the reveal toggle keeps working under its design key', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    final toggle = find.byKey(const ValueKey('server-password-visibility'));
    await _reveal(tester, toggle);
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('server-password-field')),
          )
          .obscureText,
      isTrue,
    );
    await tester.tap(toggle);
    await tester.pump();
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('server-password-field')),
          )
          .obscureText,
      isFalse,
    );
  });

  testWidgets('a mid-session 401 raises the update-password banner action', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);
    controller.passwordRejected = true;

    Object? pushedArguments;
    await tester.pumpWidget(
      MaterialApp(
        onGenerateRoute: (settings) {
          if (settings.name == '/servers') {
            pushedArguments = settings.arguments;
            return MaterialPageRoute<void>(
              settings: settings,
              builder: (_) => const Scaffold(body: Text('servers-route')),
            );
          }
          return null;
        },
        home: Scaffold(
          body: ConnectionStatusBanner(controller: controller),
        ),
      ),
    );

    expect(find.text('Server password changed — reconnect.'), findsOneWidget);
    // Never a modal: the state renders as a MaterialBanner with one action.
    expect(find.byType(AlertDialog), findsNothing);
    final action = find.byKey(const ValueKey('banner-update-password'));
    expect(action, findsOneWidget);
    await tester.tap(action);
    await tester.pumpAndSettle();
    expect(find.text('servers-route'), findsOneWidget);
    expect(pushedArguments, 'edit-active');
  });
}
