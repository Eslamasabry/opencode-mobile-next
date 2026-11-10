import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';
import 'package:opencode_mobile/ui/widgets/setup_terminal.dart';

import 'support/first_run_path.dart';
import 'support/kit_field_finders.dart';
import 'support/server_editor.dart';

import '../tool/capture/fixtures.dart'
    show loadCaptureFonts, captureTheme, capturePng, writePng;

class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => const [];
}

class _DeniedCamera implements CameraPlatform {
  @override
  Future<bool> hasCamera() async => true;
  @override
  Future<CameraPermission> requestCameraPermission() async =>
      CameraPermission.denied;
  @override
  Future<void> openAppSettings() async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final rtl in [false, true]) {
    for (final page in [
      'tailscale',
      'host',
      'scanner',
      'saved',
      'output',
      'servers',
    ]) {
      testWidgets('setup $page at 320dp 2.5x ${rtl ? 'RTL' : 'LTR'}', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        // Reset inside the body: the binding verifies foundation debug
        // variables before tearDown callbacks run.
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        const secureChannel = MethodChannel(
          'plugins.it_nomads.com/flutter_secure_storage',
        );
        const tailscaleChannel = MethodChannel('oc/tailscale');
        SharedPreferences.setMockInitialValues({});
        messenger.setMockMethodCallHandler(
          secureChannel,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
        messenger.setMockMethodCallHandler(
          tailscaleChannel,
          (call) async => call.method == 'check' ? 'installed' : true,
        );
        addTearDown(() {
          messenger.setMockMethodCallHandler(secureChannel, null);
          messenger.setMockMethodCallHandler(tailscaleChannel, null);
        });
        final previousCamera = cameraPlatform;
        cameraPlatform = _DeniedCamera();
        addTearDown(() => cameraPlatform = previousCamera);
        final store = _Store(prefs: await SharedPreferences.getInstance());
        final controller = ConnectionController(store);
        final outputScroll = ScrollController();
        final boundary = GlobalKey();
        final home = switch (page) {
          'tailscale' => const TailscaleSetupScreen(
            initialAddress: 'https://workstation.example.ts.net',
          ),
          'host' => HostManagementScreen(controller: controller),
          'scanner' => const PairingScannerScreen(),
          'saved' => SavedServerConnectionCard(
            profileName: 'Workstation',
            baseUrl: 'https://server.example',
            error: null,
            attempts: 1,
            onChangeServer: () {},
            onRetry: () {},
          ),
          'output' => Scaffold(
            body: SetupTerminal(
              output:
                  r'$ cd /work/project'
                  '\n[oc] OpenCode is ready',
              running: false,
              controller: outputScroll,
            ),
          ),
          _ => const ServersScreen(),
        };
        // Arabic is exercised automatically once the complete catalog is integrated.
        // Before integration the RTL fixture explicitly proves direction/scale only.
        final hasArabic = AppLocalizations.supportedLocales.any(
          (locale) => locale.languageCode == 'ar',
        );
        final locale = Locale(rtl && hasArabic ? 'ar' : 'en');
        try {
          await tester.pumpWidget(
            RepaintBoundary(
              key: boundary,
              child: ProviderScope(
                overrides: [
                  bootstrapProvider.overrideWithValue(AppBootstrap(store)),
                  connProvider.overrideWithValue(controller),
                ],
                child: MaterialApp(
                  theme: captureTheme(light: true),
                  debugShowCheckedModeBanner: false,
                  locale: locale,
                  localizationsDelegates:
                      AppLocalizations.localizationsDelegates,
                  supportedLocales: AppLocalizations.supportedLocales,
                  builder: (context, child) => MediaQuery(
                    data: MediaQuery.of(
                      context,
                    ).copyWith(textScaler: const TextScaler.linear(2.5)),
                    child: Directionality(
                      textDirection: rtl
                          ? TextDirection.rtl
                          : TextDirection.ltr,
                      child: child!,
                    ),
                  ),
                  home: home,
                ),
              ),
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 300));
          expect(tester.takeException(), isNull);
          for (final field in tester.widgetList<TextField>(
            find.byType(TextField),
          )) {
            if (page == 'tailscale') {
              expect(field.textDirection, TextDirection.ltr);
            }
          }
          if (page == 'output') {
            // The setup output is a KitLogPanel: its lines read left to
            // right in any interface direction.
            final line = find.text('[oc] OpenCode is ready');
            expect(line, findsOneWidget);
            expect(
              tester
                  .widget<Directionality>(
                    find
                        .ancestor(
                          of: line,
                          matching: find.byType(Directionality),
                        )
                        .first,
                  )
                  .textDirection,
              TextDirection.ltr,
            );
          }
          if (page == 'scanner') {
            // The recovery state is a lazy ListView: at 2.5x the secondary
            // action sits below the fold and is built only once scrolled to.
            final action = find.byKey(
              const ValueKey('pairing-scanner-secondary'),
            );
            await tester.scrollUntilVisible(
              action,
              200,
              scrollable: find.byType(Scrollable).first,
            );
            await tester.pump();
            expect(action.hitTestable(), findsOneWidget);
          }
          if (page == 'host') {
            final docs = find.byKey(const ValueKey('host-docs-link'));
            await tester.scrollUntilVisible(
              docs,
              400,
              scrollable: find.byType(Scrollable).first,
            );
            // Built is not yet on screen: bring it the rest of the way.
            await tester.ensureVisible(docs);
            await tester.pump();
            expect(docs.hitTestable(), findsOneWidget);
          }
          if (page == 'servers') {
            await openFirstRunConnect(tester);
            await openServerManualAddress(tester);
            final address = find.byKey(const ValueKey('server-url-field'));
            await tester.ensureVisible(address);
            expect(
              editableOf(tester, address).textDirection,
              TextDirection.ltr,
            );
          }
          expect(tester.takeException(), isNull);
          if (const bool.fromEnvironment('E7_SETUP_CAPTURE')) {
            await writePng(
              'docs/qa/e7-setup/${locale.languageCode}-${rtl ? 'rtl' : 'ltr'}-$page-large.png',
              await capturePng(tester, boundary),
            );
          }
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          outputScroll.dispose();
          debugDefaultTargetPlatformOverride = null;
        }
      });
    }
  }
}
