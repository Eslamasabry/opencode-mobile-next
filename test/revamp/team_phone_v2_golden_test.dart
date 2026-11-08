// Golden renders of slice-P1.7, AI Team on the phone through phone setup
// v2: the in-app inventory (AI Team installed), the pre-flight that tells a
// 32-bit phone why (never hidden), and a failed setup row with "Report this
// failure" (P8.4). Light, the app's real fonts, DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/team_phone_v2_golden_test.dart
// and look at every changed image before committing it.

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/setup_progress_view.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/fake_setup_engine.dart';
import 'screen_phone_1_fixtures.dart';

/// The in-app registry plus AI Team and voice typing, as a host lists it.
const _registry = [
  ...FakeSetupEngine.fakeRegistry,
  SetupComponent(
    id: 'aiteam',
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'true',
    installScript: 'true',
    dependsOn: ['essentials', 'opencode'],
    estimatedSeconds: 150,
    downloadBytes: 112000000,
  ),
  SetupComponent(
    id: 'voice',
    title: 'Voice typing',
    shortTitle: 'Voice typing',
    checkScript: '',
    installScript: '',
    estimatedSeconds: 45,
    downloadBytes: 150000000,
  ),
];

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved});

  final List<ServerProfile> saved;
  String? _active;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => _active;

  @override
  Future<void> setActiveId(String? id) async => _active = id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    final i = saved.indexWhere((p) => p.id == profile.id);
    if (i < 0) {
      saved.add(profile);
    } else {
      saved[i] = profile;
    }
  }
}

Future<void> _settle(
  WidgetTester tester, {
  Duration tick = const Duration(milliseconds: 100),
}) async {
  for (var i = 0; i < 15; i++) {
    await tester.pump(tick);
  }
}

/// A connection that has [profile] as its connected server.
Future<ConnectionController> _connection(
  ServerProfile profile, {
  Map<String, Object> prefs = const {},
}) async {
  SharedPreferences.setMockInitialValues(prefs);
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    saved: [profile],
  );
  await store.setActiveId(profile.id);
  final controller = ConnectionController(store)
    ..directory = '/root/projects/calc';
  controller.adoptConnectedProfileForTesting(profile);
  controller.syncOrchestration();
  return controller;
}

Future<void> _shot(
  WidgetTester tester,
  String name, {
  required ConnectionController controller,
  required Widget Function(BuildContext context) home,
  Size size = phoneSize,
  Duration settleTick = const Duration(milliseconds: 100),
  Future<void> Function()? act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
            connProvider.overrideWithValue(controller),
          ],
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: true),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: Builder(builder: home),
          ),
        ),
      ),
    );
    await _settle(tester, tick: settleTick);
    if (act != null) await act();
    await _settle(tester, tick: settleTick);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/$name.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
  await tester.pumpWidget(const SizedBox.shrink());
  controller.dispose();
  await tester.pump();
}

void main() {
  setUpAll(loadCaptureFonts);

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in [
      'plugins.it_nomads.com/flutter_secure_storage',
      'oc/background',
      'oc/shortcut',
      'oc/voice',
    ]) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
    }
  });
  tearDown(() {
    debugPlatformCapabilities = null;
  });

  testWidgets('inventory proof: installed in-app AI Team intro', (
    tester,
  ) async {
    final previous = PhoneSetup.engine;
    PhoneSetup.engine = FakeSetupEngine(registry: _registry)
      ..optionalInstalled = {'python', 'aiteam'};
    addTearDown(() => PhoneSetup.engine = previous);
    final controller = await _connection(inAppProfile);
    await _shot(
      tester,
      'inventory_in_app_team_installed_light',
      controller: controller,
      home: (_) => TeamIntroScreen(
        controller: controller,
        deviceProbe: () async => okDevice,
      ),
      act: () async {
        final en = lookupAppLocalizations(const Locale('en'));
        expect(find.text(en.teamIntroInstalledTitle), findsOneWidget);
        await tester.ensureVisible(find.text(en.teamIntroInstalledTitle));
        await _settle(tester);
      },
    );
  });

  testWidgets('inventory proof: installed in-app phone tools', (tester) async {
    final boundary = GlobalKey();
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      light: true,
      boundary: boundary,
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
    );
    PhoneSetup.engine = FakeSetupEngine(registry: _registry)
      ..optionalInstalled = {'python', 'aiteam'};
    setupToolsChanged.value++;
    await _settle(tester);
    final header = find.byKey(const ValueKey('this-phone-installed-header'));
    await tester.ensureVisible(header);
    await _settle(tester);
    await tester.tap(header);
    await _settle(tester);
    await tester.ensureVisible(find.text('AI Team').last);
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/inventory_in_app_tools_installed_light.png'),
    );
    await unmountPhone(tester);
  });

  testWidgets('intro: a 32-bit phone is told why', (tester) async {
    final controller = await _connection(inAppProfile);
    await _shot(
      tester,
      'team_v2_intro_preflight_light',
      controller: controller,
      home: (_) => TeamIntroScreen(
        controller: controller,
        deviceProbe: () async => const VoiceDeviceInfo(
          availableStorageBytes: 20000000000,
          memoryClassMb: 256,
          totalMemoryMb: 3072,
          supportedAbis: ['armeabi-v7a', 'armeabi'],
          hasMicrophone: true,
        ),
      ),
      act: () async {
        final notice = find.byKey(const ValueKey('team-intro-on-computer'));
        await tester.scrollUntilVisible(
          notice,
          200,
          scrollable: find.descendant(
            of: find.byKey(const ValueKey('team-intro')),
            matching: find.byType(Scrollable),
          ),
        );
      },
    );
  });

  testWidgets('a failed setup row offers Report this failure', (tester) async {
    final controller = await _connection(inAppProfile);
    await _shot(
      tester,
      'team_v2_setup_failed_report_light',
      controller: controller,
      home: (_) => Scaffold(
        body: SingleChildScrollView(
          child: SetupProgressView(
            progress: const SetupProgress(
              state: SetupState.failed,
              jobId: 'job-1',
              overall: .7,
              current: 'aiteam',
              adding: ['aiteam'],
              logTail:
                  'Downloading AI Team · 2 of 3\ncurl: (6) Could not '
                  'resolve host: github.com',
              components: [
                ComponentProgress(
                  id: 'aiteam',
                  state: ComponentState.failed,
                  error: 'curl: (6) Could not resolve host: github.com',
                ),
              ],
            ),
            components: _registry,
            title: 'Adding AI Team',
            onContinue: () {},
          ),
        ),
      ),
    );
  });
}
