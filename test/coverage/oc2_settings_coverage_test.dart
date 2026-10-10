// Coverage ratchet for what an OpenCode 2 server sends the settings pages: the
// always-allowed actions and the managed commands Development services lists.
// The wire bodies are served to the app's real OpenCode 2 gateway and the
// pages are drawn from what it parsed (see paseo_coverage_support.dart).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/development_service.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/development_service_fakes.dart';
import 'lists_support.dart';
import 'paseo_coverage_support.dart';
import 'settings_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc2_settings', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final repository = Api2OperationsGateway(client: client);
      for (final variant in family.cases) {
        final body = variant['payload'];
        wire.handler = (r) => _route(body, r);
        try {
          fetched[variant['id'] as String] = switch (variant['kind']) {
            'saved' => {'saved': await repository.listSavedPermissions()},
            _ => {'shells': await repository.loadRunningShells()},
          };
        } on ProductException catch (error) {
          fail('${variant['id']}: ${error.message}: ${error.cause}');
        }
      }
      client.close();
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 settings · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      if (variant['kind'] == 'saved') {
        final repository = SettingsRepository()
          ..saved = data['saved'] as dynamic;
        final controller = await listsController(
          repository: repository,
          api: ListsApi(api2ServerCapabilities),
        );
        final screens = ListsScreens(
          tester,
          controller,
          GlobalKey(),
          'oc2-settings-$id',
        );
        await screens.savedPermissions();
        File(
          'build/coverage/oc2settings_$id.txt',
        ).writeAsStringSync(flat(screens.text));
        final problems = checkCase(family, variant, screens.text);
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(seconds: 2));
        controller.dispose();
        expect(problems, isEmpty, reason: flat(screens.text));
        return;
      }
      // Development services: a saved service whose start the server's
      // command answers for.
      final list = data['shells'] as ManagedShellList;
      final services = ServiceRepository();
      for (final shell in list.shells) {
        services.shells[shell.id] = shell;
      }
      final shell = list.shells.single;
      final connection = await _servicesConnection(services);
      await DevelopmentServiceStore(
        preferences: connection.store.prefs,
        profileID: 'laptop',
        canWrite: () => true,
      ).save(
        DevelopmentService(
          id: 'vite',
          name: 'Shopfront preview',
          command: shell.command,
          directory: '/work/shopfront',
          url: 'http://192.168.1.20:5173',
          run: DevelopmentServiceRun(
            ownerToken: 'owner_settings',
            shellID: shell.id,
            startedAt: shell.startedAt.millisecondsSinceEpoch,
          ),
        ),
      );
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: DevelopmentServicesScreen(controller: connection),
          ),
        ),
      );
      await settle(tester);
      final details = find.text('Details');
      if (details.evaluate().isNotEmpty) {
        await tester.ensureVisible(details.first);
        await tester.tap(details.first);
        await settle(tester);
      }
      final text = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'lists-oc2-settings-$id-1');
      File('build/coverage/oc2settings_$id.txt').writeAsStringSync(flat(text));
      final problems = checkCase(family, variant, text);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 3));
      connection.dispose();
      expect(problems, isEmpty, reason: flat(text));
    });
  }
}

Future<ServicesConnection> _servicesConnection(
  ServiceRepository repository,
) async {
  // flutter_secure_storage hangs inside a widget test unless it is answered.
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(
        const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
        (_) async => null,
      );
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.upsert(
    ServerProfile(
      id: 'laptop',
      name: 'Laptop',
      baseUrl: 'http://192.168.1.20:4097',
    ),
  );
  await store.setActiveId('laptop');
  return ServicesConnection(store, repository)
    ..directory = '/work/shopfront'
    ..status = StreamStatus.connected;
}

Object? _route(Object? body, WireRequest r) {
  final path = r.path.startsWith('/api') ? r.path.substring(4) : r.path;
  switch (path) {
    case '/project/current':
      return {
        'data': {
          'id': 'prj_shopfront',
          'canonical': '/work/shopfront',
          'time': {'created': 1, 'updated': 1},
          'sandboxes': <String>[],
        },
      };
    case '/permission/saved':
      return {'data': body};
    case '/shell':
      return {'data': body};
  }
  return null;
}
