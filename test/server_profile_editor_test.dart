import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
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
      api = OpenCodeApi(baseUrl: profile.baseUrl);
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
  final connect = find.byKey(const ValueKey('welcome-connect-card'));
  await tester.ensureVisible(connect);
  await tester.pumpAndSettle();
  await tester.tap(connect);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('remote setup opens a full-screen URL-first editor', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Add server'), findsOneWidget);
    expect(find.text('Save server'), findsOneWidget);
    expect(find.text('AUTHENTICATION'), findsOneWidget);

    final url = tester.widget<TextField>(
      find.byKey(const ValueKey('server-url-field')),
    );
    expect(url.focusNode?.hasFocus, isTrue);
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
      await tester.enterText(
        find.byKey(const ValueKey('server-username-field')),
        'opencode',
      );
      await tester.enterText(
        find.byKey(const ValueKey('server-password-field')),
        'test-secret',
      );
      await tester.tap(find.byKey(const ValueKey('toggle-server-password')));
      await tester.pump();

      expect(
        tester
            .widget<TextField>(
              find.byKey(const ValueKey('server-password-field')),
            )
            .obscureText,
        isFalse,
      );
      expect(find.byTooltip('Hide server password'), findsOneWidget);

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
    final url = tester.widget<TextField>(
      find.byKey(const ValueKey('server-url-field')),
    );
    expect(url.decoration?.errorMaxLines, 3);
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

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();

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
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await _openEditor(tester);

    await tester.enterText(
      find.byKey(const ValueKey('server-name-field')),
      'Workstation',
    );
    await tester.tap(find.byTooltip('Close server editor'));
    await tester.pumpAndSettle();

    expect(find.text('Discard server changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
  });
}
