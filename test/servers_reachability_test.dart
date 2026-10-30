// What the Servers page keeps reachable after the clutter pass: for
// a person with no server connected (no shell, so no Settings), Report a bug
// and the Setup guide in its top-bar menu.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart'
    show ServerPageScreen;

import 'support/profile_monitor_fixture.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

Future<ConnectionController> _controller() async {
  final store = await monitorStore(count: 1);
  return ConnectionController(
    store,
    monitorGatewayFactory: (_) =>
        (gateway: MonitorTestGateway(), operations: MonitorTestOperations()),
  );
}

Widget _app(ConnectionController controller, Widget home) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

Future<void> _finish(WidgetTester tester, ConnectionController c) async {
  await tester.pumpWidget(const SizedBox.shrink());
  c.dispose();
  await tester.pump();
}

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
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null),
  );

  testWidgets('as the root page, the menu holds About, Report a problem and '
      'the Setup guide; the guide opens', (tester) async {
    final controller = await _controller();
    await tester.pumpWidget(_app(controller, const ServersScreen()));
    await tester.pump();

    await tester.tap(_key('servers-menu'));
    await tester.pumpAndSettle();
    final panel = tester.widget<KitMenuPanel>(find.byType(KitMenuPanel));
    // The Welcome keeps its first page to the wordmark and the question:
    // About waits here (vision 2026-10-20).
    expect(panel.items.map((item) => item.label), [
      _en.e7SetupAboutNotices,
      _en.e7LibraryReportABug,
      _en.onboardingSetupGuide,
    ]);

    await tester.tap(find.text(_en.onboardingSetupGuide));
    await tester.pumpAndSettle();
    expect(find.byType(GuideScreen), findsOneWidget);
    await _finish(tester, controller);
  });

  testWidgets('with no server saved, the welcome has the same menu', (
    tester,
  ) async {
    final store = await monitorStore(count: 0);
    final controller = ConnectionController(store);
    await tester.pumpWidget(_app(controller, const ServersScreen()));
    await tester.pump();

    await tester.tap(_key('servers-menu'));
    await tester.pumpAndSettle();
    expect(find.text(_en.e7LibraryReportABug), findsOneWidget);
    expect(find.text(_en.onboardingSetupGuide), findsOneWidget);
    await _finish(tester, controller);
  });

  testWidgets('opened from Settings, the menu does not repeat Settings', (
    tester,
  ) async {
    final controller = await _controller();
    await tester.pumpWidget(
      _app(
        controller,
        Builder(
          builder: (context) => Center(
            child: GestureDetector(
              onTap: () =>
                  pushKitPage<void>(context, (_) => const ServersScreen()),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byType(ServersScreen), findsOneWidget);
    expect(_key('servers-menu'), findsNothing);
    expect(find.text(_en.e7LibraryReportABug), findsNothing);
    await _finish(tester, controller);
  });

  testWidgets('tapping a server opens its own page and does not connect', (
    tester,
  ) async {
    final controller = await _controller();
    await tester.pumpWidget(_app(controller, const ServersScreen()));
    await tester.pump();

    final row = find.byKey(const ValueKey('saved-server-profile-1'));
    expect(row, findsOneWidget);
    await tester.tap(row);
    await tester.pumpAndSettle();

    expect(find.byType(ServerPageScreen), findsOneWidget);
    expect(controller.isConnected, isFalse);
    expect(controller.profile, isNull);
    await _finish(tester, controller);
  });
}
