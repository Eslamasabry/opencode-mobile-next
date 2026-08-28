import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
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

Future<(_RecordingProfileStore, ConnectionController)> _state() async {
  SharedPreferences.setMockInitialValues({});
  final store = _RecordingProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (store, ConnectionController(store));
}

Widget _app(
  _RecordingProfileStore store,
  ConnectionController controller, {
  double textScale = 1,
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
    expect(
      find.text(
        'Enter a complete server URL, such as https://server.example:4096.',
      ),
      findsOneWidget,
    );

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
