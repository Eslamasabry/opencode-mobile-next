import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';
import 'package:opencode_mobile/ui/screens/manage_project_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart'
    show captureApp, capturePng, loadCaptureFonts, writePng;
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
      MaterialApp(home: DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Register service'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).at(0), 'Preview');
    await tester.enterText(find.byType(TextField).at(1), 'npm run dev');
    await tester.ensureVisible(find.text('Save service'));
    await tester.tap(find.text('Save service'));
    await tester.pumpAndSettle();
    expect(find.text('Preview'), findsOneWidget);
    expect(gateway.starts, 0);
    expect(
      connection.store.prefs.getString('oc.developmentServices.laptop'),
      contains('npm run dev'),
    );
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'start requires confirmation, logs are plain text, stop affects owned ID',
    (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway);
      addTearDown(connection.dispose);
      await seed(connection);
      await tester.pumpWidget(
        MaterialApp(home: DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Start'));
      await tester.tap(find.text('Start'));
      await tester.pumpAndSettle();
      expect(gateway.starts, 0);
      await tester.tap(find.widgetWithText(FilledButton, 'Start').last);
      await tester.pumpAndSettle();
      expect(gateway.starts, 1);
      expect(find.text('Running command'), findsOneWidget);
      await tester.ensureVisible(find.text('Logs'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Logs'));
      await tester.pumpAndSettle();
      expect(find.textContaining('VITE ready'), findsOneWidget);
      Navigator.of(tester.element(find.textContaining('VITE ready'))).pop();
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Stop'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stop'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Stop'));
      await tester.pumpAndSettle();
      expect(gateway.stops, ['sh_1']);
      expect(find.text('Stopped'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'unsupported profile retains save/copy/Visit without fake controls',
    (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway)
        ..supported = false;
      addTearDown(connection.dispose);
      await seed(connection);
      await tester.pumpWidget(
        MaterialApp(home: DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Start'), findsNothing);
      await tester.ensureVisible(find.text('Visit'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Visit'));
      await tester.pumpAndSettle();
      expect(find.text('Open insecure HTTP link?'), findsOneWidget);
      expect(find.text('192.168.1.20:5173'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(gateway.starts, 0);
      await tester.pumpWidget(const SizedBox());
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
      MaterialApp(home: DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    connection.directory = '/another';
    connection.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.textContaining('Reopen Development services'), findsOneWidget);
    expect(find.text('Start'), findsNothing);
    expect(find.text('Shopfront preview'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Manage project opens the development services destination', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: ManageProjectScreen(controller: connection, project: null),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Development services'));
    await tester.pumpAndSettle();
    expect(find.byType(DevelopmentServicesScreen), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('location revision invalidates even when the path is unchanged', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await seed(connection);
    await tester.pumpWidget(
      MaterialApp(home: DevelopmentServicesScreen(controller: connection)),
    );
    await tester.pumpAndSettle();
    connection.locationRevision++;
    connection.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.textContaining('Reopen Development services'), findsOneWidget);
    expect(find.text('Start'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  for (final variant in ['light', 'dark', 'large']) {
    testWidgets('service layout and capture $variant', (tester) async {
      final gateway = ServiceRepository();
      final connection = await connectionFor(gateway);
      addTearDown(connection.dispose);
      await seed(connection);
      tester.view.physicalSize = Size(variant == 'large' ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = variant == 'large'
          ? 2
          : 1;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final output = Platform.environment['OC_SERVICE_CAPTURE_DIR'];
      if (output != null) await loadCaptureFonts();
      final boundary = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary,
          controller: connection,
          light: variant != 'dark',
          home: DevelopmentServicesScreen(controller: connection),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      if (output != null) {
        await writePng(
          '$output/$variant-top.png',
          await capturePng(tester, boundary, pixelRatio: 2),
        );
      }
      await tester.scrollUntilVisible(
        find.text('Remove configuration'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      if (output != null) {
        await writePng(
          '$output/$variant-controls.png',
          await capturePng(tester, boundary, pixelRatio: 2),
        );
      }
      await tester.ensureVisible(find.text('Start'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Start'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Start').last);
      await tester.pumpAndSettle();
      expect(gateway.starts, 1);
      expect(find.text('Running command'), findsOneWidget);
      await tester.ensureVisible(find.text('Stop'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      if (output != null) {
        await writePng(
          '$output/$variant-running.png',
          await capturePng(tester, boundary, pixelRatio: 2),
        );
      }
      await tester.ensureVisible(find.text('Logs'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Logs'));
      await tester.pumpAndSettle();
      expect(find.textContaining('VITE ready'), findsOneWidget);
      expect(tester.takeException(), isNull);
      if (output != null) {
        await writePng(
          '$output/$variant-logs.png',
          await capturePng(tester, boundary, pixelRatio: 2),
        );
      }
      Navigator.of(tester.element(find.textContaining('VITE ready'))).pop();
      await tester.pumpAndSettle();
      gateway.failReads = true;
      await tester.tap(find.byTooltip('Refresh status'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Status unknown'),
        -200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();
      expect(find.text('Stop'), findsNothing);
      expect(tester.takeException(), isNull);
      if (output != null) {
        await writePng(
          '$output/$variant-unknown.png',
          await capturePng(tester, boundary, pixelRatio: 2),
        );
      }
      gateway.failReads = false;
      connection.connectionRevision++;
      connection.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.text('Running command'), findsOneWidget);
      expect(gateway.starts, 1);
      await tester.pumpWidget(const SizedBox());
    });
  }
}
