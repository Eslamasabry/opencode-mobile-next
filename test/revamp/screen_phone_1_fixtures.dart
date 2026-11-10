// Shared set-up for screen-phone-1's behaviour tests and goldens: the phone
// setup screens behind a fake engine and a stood-in in-app Linux.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import '../../tool/capture/audit/audit_env.dart';
import '../support/fake_setup_engine.dart';

const phoneSize = Size(412, 915);
const wideSize = Size(1280, 800);

/// A phone the pre-flight (P0.8) finds nothing wrong with.
const okDevice = VoiceDeviceInfo(
  availableStorageBytes: 20000000000,
  memoryClassMb: 256,
  totalMemoryMb: 8192,
  supportedAbis: ['arm64-v8a'],
  hasMicrophone: true,
);

/// The in-app Linux, stood in for.
class PhoneLinux extends BuiltinLinux {
  PhoneLinux({
    this.installed = true,
    this.running = false,
    this.openCode = true,
    this.log = '',
    this.uninstallError,
  });

  bool installed;
  bool running;
  bool openCode;
  String log;
  String? uninstallError;
  int uninstalls = 0;
  int starts = 0;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    serverRunning: running,
    serverPort: running ? BuiltinLinux.serverPort : null,
    bytesUsed: installed ? 734003200 : null,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async => openCode
      ? const BuiltinLinuxRunResult(exitCode: 0, output: '1.18.29\n')
      : const BuiltinLinuxRunResult(exitCode: 1, output: '');

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    running = true;
  }

  @override
  Future<void> stopServer() async => running = false;

  @override
  Future<String> serverLog({int tailBytes = 32768}) async => log;

  /// The remove sheet's reading: the runtime and the projects kept apart.
  @override
  Future<BuiltinProjectStorage> projectStorage() async =>
      const BuiltinProjectStorage(
        runtimeBytes: 734003200,
        projectsBytes: 52428800,
        measuredAtMilliseconds: 0,
      );

  @override
  Future<void> uninstall() async {
    uninstalls++;
    final error = uninstallError;
    if (error != null) throw BuiltinLinuxException(error);
    installed = false;
    running = false;
    openCode = false;
  }
}

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved});

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => saved.isEmpty ? null : saved.first.id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((item) => item.id == profile.id)
      ..add(profile);
  }

  @override
  Future<void> setActiveId(String? id) async {}
}

/// The in-app profile a finished setup saves.
final inAppProfile = ServerProfile(
  id: 'phone',
  name: 'This phone, built-in (OpenCode 1)',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
  password: 'secret',
  serverVersion: '1.18.29',
);

/// Screen A with every probe answered at once.
Widget startScreen({bool inApp = false}) => PhoneSetupStartScreen(
  inAppProbe: () async => inApp,
  deviceProbe: () async => okDevice,
  openProgress: (_) async {},
);

/// Mounts [home] as the app would, with the providers the phone screens
/// read, at [size]. Returns the fake engine behind `PhoneSetup.engine`.
Future<FakeSetupEngine> pumpPhone(
  WidgetTester tester, {
  required Widget home,
  Size size = phoneSize,
  bool light = false,
  SetupProgress? progress,
  Set<String> optionalInstalled = const {},
  int optionalFailures = 0,
  List<ServerProfile> profiles = const [],
  BuiltinLinux? linux,
  GlobalKey? boundary,
  Map<String, WidgetBuilder> routes = const {},
  void Function(ConnectionController controller)? configure,
  List<Override> overrides = const [],
  Duration readyTimeout = const Duration(seconds: 90),
  double pixelRatio = 1,
  Locale? locale,
  double textScale = 1,
  bool animations = false,
}) async {
  tester.view.physicalSize = size * pixelRatio;
  tester.view.devicePixelRatio = pixelRatio;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(
    _Store(prefs: prefs, saved: [...profiles]),
  );
  addTearDown(controller.dispose);
  configure?.call(controller);
  final engine = FakeSetupEngine()
    ..optionalInstalled = optionalInstalled
    ..optionalFailures = optionalFailures;
  if (progress != null) engine.emit(progress);
  final previous = PhoneSetup.engine;
  PhoneSetup.engine = engine;
  addTearDown(() => PhoneSetup.engine = previous);
  final phone = linux ?? PhoneLinux();
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
          connProvider.overrideWithValue(controller),
          builtinLinuxProvider.overrideWithValue(phone),
          builtinServerStarterProvider.overrideWith((ref) {
            final starter = BuiltinServerStarter(
              linux: phone,
              readyTimeout: readyTimeout,
              pollInterval: Duration.zero,
            );
            ref.onDispose(starter.dispose);
            return starter;
          }),
          ...overrides,
        ],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: locale == null
              ? captureTheme(light: light)
              : AppTheme.forLocale(captureTheme(light: light), locale),
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              disableAnimations: !animations,
              textScaler: TextScaler.linear(textScale),
            ),
            child: auditWrap(context, child!),
          ),
          routes: {
            '/': (_) => home,
            for (final entry in routes.entries) entry.key: entry.value,
          },
        ),
      ),
    ),
  );
  // Long enough for a drawing's entrance to finish.
  for (var i = 0; i < 15; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  return engine;
}

/// Ends a test that mounted a phone screen: nothing left pending.
Future<void> unmountPhone(WidgetTester tester) async {
  KitUndo.commitPending();
  // The page's connection polls a dead in-app server every 10 s; a finished
  // test ends that poll before the test's own pending-timer check.
  ConnectionController? connection;
  final apps = find.byType(MaterialApp);
  if (apps.evaluate().isNotEmpty) {
    connection = ProviderScope.containerOf(
      tester.element(apps.first),
      listen: false,
    ).read(connProvider);
  }
  await tester.pumpWidget(const SizedBox.shrink());
  connection?.dispose();
  debugDefaultTargetPlatformOverride = null;
}
