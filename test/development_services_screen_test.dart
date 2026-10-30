import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/development_service_fakes.dart';

Future<ServicesConnection> connectionFor(ServiceRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.upsert(
    ServerProfile(
      id: 'laptop',
      name: 'Laptop',
      baseUrl: 'http://192.168.1.20:4097',
    ),
  );
  await store.setActiveId('laptop');
  return ServicesConnection(store, repository)
    ..directory = sampleService.directory
    ..status = StreamStatus.connected;
}

Future<void> seed(ServicesConnection connection) => DevelopmentServiceStore(
  preferences: connection.store.prefs,
  profileID: 'laptop',
  canWrite: () => true,
).save(sampleService);

Widget app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Finder rich(String text) => find.textContaining(text, findRichText: true);

final _row = find.byKey(const ValueKey('development-service-vite'));

Future<void> openMenu(WidgetTester tester) async {
  await tester.longPress(_row);
  await tester.pumpAndSettle();
}

Future<void> done(WidgetTester tester) async {
  KitUndo.commitPending();
  await tester.pumpWidget(const SizedBox());
  await tester.pumpAndSettle();
}

/// A row's start or stop button: its tooltip names the service (R2),
/// "Start Web app".
Finder _tip(String verb) => find.byWidgetPredicate(
  (widget) =>
      widget is Tooltip &&
      (widget.message ?? widget.richMessage?.toPlainText() ?? '').startsWith(
        '$verb ',
      ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  testWidgets('register is explicit, persists, and does not start a process', (
    tester,
  ) async {
    final gateway = ServiceRepository();
    final connection = await connectionFor(gateway);
    addTearDown(connection.dispose);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    expect(find.text('No dev commands yet'), findsOneWidget);
    await tester.tap(find.text('Register service'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('development-services-name')),
      'Preview',
    );
    await tester.enterText(
      find.byKey(const ValueKey('development-services-command')),
      'npm run dev',
    );
    await tester.tap(find.text('Save service'));
    await tester.pumpAndSettle();
    expect(find.text('Preview'), findsOneWidget);
    expect(gateway.starts, 0);
    expect(
      connection.store.prefs.getString('oc.developmentServices.laptop'),
      contains('npm run dev'),
    );
    expect(
      connection.store.prefs.getKeys().where((k) => k.startsWith('oc.draft.')),
      isEmpty,
      reason: 'a saved service clears its drafts',
    );
    await done(tester);
  });

  testWidgets('the editor says what is missing and refuses a duplicate name', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Register service'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save service'));
    await tester.pumpAndSettle();
    expect(find.text('Enter a name.'), findsOneWidget);
    expect(find.text('Enter a command, such as npm run dev.'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('development-services-name')),
      'shopfront PREVIEW',
    );
    await tester.enterText(
      find.byKey(const ValueKey('development-services-command')),
      'npm start',
    );
    await tester.enterText(
      find.byKey(const ValueKey('development-services-url')),
      'ftp://nope',
    );
    await tester.tap(find.text('Save service'));
    await tester.pumpAndSettle();
    expect(
      find.text('A service with this name already exists.'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Enter an http or https address without a user name or password.',
      ),
      findsOneWidget,
    );
    await done(tester);
  });

  testWidgets(
    'draft carry: typed input survives swipe, reopen and a restart; the '
    'profile sweep removes it',
    (tester) async {
      final connection = await connectionFor(ServiceRepository());
      addTearDown(connection.dispose);
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register service'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('development-services-name')),
        'Storybook',
      );
      await tester.enterText(
        find.byKey(const ValueKey('development-services-command')),
        'npm run storybook',
      );
      await tester.pumpAndSettle();
      // A swipe or back closes the sheet without asking: nothing is lost.
      Navigator.of(
        tester.element(find.byKey(const ValueKey('development-services-name'))),
      ).pop();
      await tester.pumpAndSettle();
      expect(find.text('npm run storybook'), findsNothing);

      await tester.tap(find.text('Register service'));
      await tester.pumpAndSettle();
      expect(find.text('Storybook'), findsOneWidget);
      expect(find.text('npm run storybook'), findsOneWidget);
      Navigator.of(
        tester.element(find.byKey(const ValueKey('development-services-name'))),
      ).pop();
      await tester.pumpAndSettle();

      // The screen (or the process) goes away and comes back.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Register service'));
      await tester.pumpAndSettle();
      expect(find.text('npm run storybook'), findsOneWidget);

      final drafts = connection.store.prefs
          .getKeys()
          .where((k) => k.startsWith('oc.draft.developmentService.'))
          .toSet();
      expect(drafts, hasLength(2));
      expect(
        connection.store.profileScopedPreferenceKeys('laptop'),
        containsAll(drafts),
      );
      await connection.store.removeScopedPreferences('laptop');
      expect(
        connection.store.prefs.getKeys().where(
          (k) => k.startsWith('oc.draft.'),
        ),
        isEmpty,
      );
      await done(tester);
    },
  );

  testWidgets(
    'Start runs at once with Stop as its undo; Stop asks and affects the '
    'owned ID; the log follows the run',
    (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway);
      addTearDown(connection.dispose);
      await seed(connection);
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      expect(rich('Not started'), findsOneWidget);
      expect(rich('npm run dev'), findsOneWidget);
      await tester.tap(_tip('Start'));
      await tester.pumpAndSettle();
      expect(gateway.starts, 1);
      expect(rich('Running command'), findsOneWidget);
      expect(find.text('Shopfront preview started'), findsOneWidget);
      KitUndo.commitPending();
      await tester.pumpAndSettle();

      // Tapping a running row opens its log.
      await tester.tap(_row);
      await tester.pumpAndSettle();
      expect(rich('VITE ready'), findsOneWidget);
      expect(find.text('Shopfront preview · Logs'), findsOneWidget);
      await tester.tap(find.text('Stop').last);
      await tester.pumpAndSettle();
      expect(find.text('Stop Shopfront preview?'), findsOneWidget);
      expect(gateway.stops, isEmpty);
      await tester.tap(
        find.byKey(const ValueKey('development-services-confirm')),
      );
      await tester.pumpAndSettle();
      expect(gateway.stops, ['sh_1']);
      expect(rich('Stopped'), findsOneWidget);
      await done(tester);
    },
  );

  testWidgets(
    'the open log follows new output on its own, without flicker, and stops '
    'reading once the sheet closes',
    (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway);
      addTearDown(connection.dispose);
      await seed(connection);
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      await tester.tap(_tip('Start'));
      await tester.pumpAndSettle();
      KitUndo.commitPending();
      await tester.pumpAndSettle();
      await tester.tap(_row);
      await tester.pumpAndSettle();
      expect(rich('VITE ready'), findsOneWidget);

      // New output lands on the server while the sheet is open.
      gateway.output = '${gateway.output}page reload src/App.tsx\n';
      final before = gateway.cursors.length;
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(seconds: 1));
        // The earlier lines never blank out while it reads again.
        expect(rich('VITE ready'), findsOneWidget);
      }
      await tester.pumpAndSettle();
      expect(rich('page reload src/App.tsx'), findsOneWidget);
      // One read at a time: the polls did not pile up.
      final reads = gateway.cursors.length - before;
      expect(reads, inInclusiveRange(2, 8));

      // Closed: no more reads.
      Navigator.of(
        tester.element(find.byKey(const ValueKey('development-services-logs'))),
      ).pop();
      await tester.pumpAndSettle();
      final closed = gateway.cursors.length;
      await tester.pump(const Duration(seconds: 10));
      expect(gateway.cursors.length, closed);
      await done(tester);
    },
  );

  testWidgets('Undo after Start stops the command it started', (tester) async {
    final gateway = ServiceRepository();
    final connection = await connectionFor(gateway);
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await tester.tap(_tip('Start'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('development-services-undo-start')),
    );
    await tester.pumpAndSettle();
    expect(gateway.stops, ['sh_1']);
    await done(tester);
  });

  testWidgets('Remove of a saved service offers Undo, which brings it back', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await openMenu(tester);
    await tester.tap(find.text('Remove configuration'));
    await tester.pumpAndSettle();
    expect(find.text('Shopfront preview removed'), findsOneWidget);
    expect(_row, findsNothing);
    await tester.tap(
      find.byKey(const ValueKey('development-services-undo-remove')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Shopfront preview'), findsOneWidget);
    expect(
      connection.store.prefs.getString('oc.developmentServices.laptop'),
      contains('npm run dev'),
    );
    await done(tester);
  });

  testWidgets('Remove of a running service asks and says it keeps running', (
    tester,
  ) async {
    final gateway = ServiceRepository();
    final connection = await connectionFor(gateway);
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await tester.tap(_tip('Start'));
    await tester.pumpAndSettle();
    KitUndo.commitPending();
    await tester.pumpAndSettle();
    await openMenu(tester);
    await tester.tap(find.text('Remove configuration'));
    await tester.pumpAndSettle();
    expect(find.text('Remove Shopfront preview?'), findsOneWidget);
    expect(find.textContaining('keeps running on the server'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('development-services-confirm')),
    );
    await tester.pumpAndSettle();
    expect(_row, findsNothing);
    expect(gateway.stops, isEmpty, reason: 'remove never stops the command');
    await done(tester);
  });

  testWidgets(
    'unsupported profile explains, keeps save/copy/Visit, no fake controls',
    (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway)
        ..supported = false;
      addTearDown(connection.dispose);
      await seed(connection);
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      expect(
        find.textContaining('cannot start and track development commands'),
        findsOneWidget,
      );
      expect(_tip('Start'), findsNothing);
      await openMenu(tester);
      expect(find.text('Start'), findsNothing);
      expect(find.text('Copy command'), findsOneWidget);
      await tester.tap(find.text('Visit'));
      await tester.pumpAndSettle();
      expect(find.text('Open insecure HTTP link?'), findsOneWidget);
      // The kit link gate (06102116) keeps the host in sight inside its
      // one sentence; "Don't open" is the way back on http.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('external-link-confirm')),
          matching: find.textContaining(
            '192.168.1.20:5173',
            findRichText: true,
          ),
        ),
        findsOneWidget,
      );
      await tester.tap(find.text("Don't open"));
      await tester.pumpAndSettle();
      expect(gateway.starts, 0);
      await done(tester);
    },
  );

  testWidgets('scope change removes retained configuration/actions', (
    tester,
  ) async {
    final gateway = ServiceRepository();
    final connection = await connectionFor(gateway);
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    connection.directory = '/another';
    connection.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.textContaining('Reopen Development services'), findsOneWidget);
    expect(_tip('Start'), findsNothing);
    expect(find.text('Shopfront preview'), findsNothing);
    await done(tester);
  });

  // Manage project merged into the Project tab (slice-P3.11a).
  testWidgets('the Project tab opens the development services destination', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await tester.pumpWidget(app(ProjectHub(controller: connection)));
    await tester.pumpAndSettle();
    // The Files page keeps the project's tools in its bar's ⋯.
    await tester.tap(find.byKey(const ValueKey('project-hub-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Development services'));
    await tester.pumpAndSettle();
    expect(find.byType(DevelopmentServicesScreen), findsOneWidget);
    await done(tester);
  });

  testWidgets('location revision invalidates even when the path is unchanged', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    connection.locationRevision++;
    connection.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.textContaining('Reopen Development services'), findsOneWidget);
    expect(_tip('Start'), findsNothing);
    await done(tester);
  });

  testWidgets('an unreadable run says so and offers Forget in the menu', (
    tester,
  ) async {
    final gateway = ServiceRepository();
    final connection = await connectionFor(gateway);
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      app(DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await tester.tap(_tip('Start'));
    await tester.pumpAndSettle();
    KitUndo.commitPending();
    gateway.failReads = true;
    connection.connectionRevision++;
    connection.notifyListeners();
    await tester.pumpAndSettle();
    expect(rich('Status unknown'), findsOneWidget);
    expect(_tip('Stop'), findsNothing);
    await openMenu(tester);
    expect(find.text('Forget last run'), findsOneWidget);
    await done(tester);
  });

  for (final (label, size, scale) in [
    ('phone', const Size(412, 915), 1.0),
    ('narrow large text', const Size(320, 844), 2.0),
    ('wide', const Size(1280, 800), 1.0),
  ]) {
    testWidgets('service list lays out without overflow: $label', (
      tester,
    ) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway);
      addTearDown(connection.dispose);
      await seed(connection);
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = scale;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(_tip('Start'));
      await tester.pumpAndSettle();
      KitUndo.commitPending();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(_row);
      await tester.pumpAndSettle();
      expect(rich('VITE ready'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await done(tester);
    });
  }
}
