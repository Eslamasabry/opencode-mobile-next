// Shared set-up for screen-phone-1's behaviour tests and goldens: the phone
// setup screens behind a fake engine, a stood-in in-app Linux, and the
// `oc/termux` channel answering the storage tools by verb.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
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
Widget startScreen({TermuxRunningServer? termux, bool inApp = false}) =>
    PhoneSetupStartScreen(
      termuxProbe: () async => termux ?? const TermuxRunningServer.absent(),
      inAppProbe: () async => inApp,
      deviceProbe: () async => okDevice,
      openProgress: (_) async {},
    );

/// No Termux job on the phone: phone setup's start screen also reads
/// Termux's setup engine (320269a2, P1.7), which must not reach the
/// platform channel in a test. Call from `setUp`.
void useNoTermuxJob() {
  final previous = PhoneSetup.termux;
  PhoneSetup.termux = FakeSetupEngine();
  addTearDown(() => PhoneSetup.termux = previous);
}

/// Mounts [home] as the app would, with the providers the phone screens
/// read, at [size]. Returns the fake engine behind `PhoneSetup.engine`.
Future<FakeSetupEngine> pumpPhone(
  WidgetTester tester, {
  required Widget home,
  Size size = phoneSize,
  bool light = false,
  SetupProgress? progress,
  Set<String> optionalInstalled = const {},
  List<ServerProfile> profiles = const [],
  BuiltinLinux? linux,
  GlobalKey? boundary,
  Map<String, WidgetBuilder> routes = const {},
  void Function(ConnectionController controller)? configure,
  List<Override> overrides = const [],
  Duration readyTimeout = const Duration(seconds: 90),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(
    _Store(prefs: prefs, saved: [...profiles]),
  );
  addTearDown(controller.dispose);
  configure?.call(controller);
  final engine = FakeSetupEngine()..optionalInstalled = optionalInstalled;
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
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
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
  await tester.pumpWidget(const SizedBox.shrink());
  debugDefaultTargetPlatformOverride = null;
}

String _rootfs(String path) =>
    '/data/data/com.termux/files/usr/var/lib/proot-distro/containers/opencode-ubuntu/rootfs$path';

/// A finished storage scan: one cache that can be cleaned, the rest listed.
String storageReport({bool stale = false}) => jsonEncode({
  'scanned_at': 1788800000,
  'cleanup_policy': 2,
  'stale': stale,
  'total_bytes': 46 * 1024 * 1024 * 1024,
  'categories': [
    {
      'key': 'build_caches',
      'bytes': 8700000000,
      'deletable': true,
      'paths': [
        {'path': _rootfs('/root/.gradle/caches'), 'bytes': 8000000000},
        {
          'path': '/data/data/com.termux/files/home/.npm/_cacache',
          'bytes': 700000000,
        },
      ],
    },
    {
      'key': 'agent_scratch',
      'bytes': 11000000000,
      'deletable': false,
      'paths': [
        {'path': _rootfs('/tmp/opencode/abc'), 'bytes': 11000000000},
      ],
    },
    {
      'key': 'opencode',
      'bytes': 400000000,
      'deletable': false,
      'paths': [
        {'path': _rootfs('/usr/local/lib/node_modules'), 'bytes': 400000000},
      ],
    },
  ],
  'projects': [
    {
      'name': 'IPTV_King',
      'path': _rootfs('/root/projects/IPTV_King'),
      'bytes': 3600000000,
      'build_bytes': 3000000000,
    },
  ],
});

/// The `oc/termux` channel as a phone whose storage tools answer by verb.
class StorageChannel {
  StorageChannel({this.state = 'idle', this.log = '', this.report});

  String state;
  String log;
  String? report;
  int scanStarts = 0;
  final cleans = <String>[];

  Map<String, Object> _result(String stdout) => {
    'stdout': stdout,
    'stderr': '',
    'exitCode': 0,
    'err': -1,
    'errorMessage': '',
  };

  Future<Object?> _handle(MethodCall call) async {
    if (call.method != 'runInTermux') return true;
    final script = (call.arguments as Map)['script'] as String;
    if (script.contains('nohup "\$TOOLS" storage-scan')) {
      scanStarts++;
      return _result('tools-started:4242\n');
    }
    final verb = RegExp(
      r'''exec "\$TOOLS" ([a-z-]+)(?: '([^']*)')?\n$''',
    ).firstMatch(script);
    switch (verb?.group(1)) {
      case 'storage-status':
        return _result(
          'state=$state\n__OC_TOOLS_LOG__\n$log\n__OC_TOOLS_JSON__\n${report ?? ''}\n',
        );
      case 'storage-cancel':
        return _result('{"cancelled":true}\n');
      case 'storage-clean':
        cleans.add(verb!.group(2)!);
        return _result('{"freed_bytes":0,"removed":[],"refused":[]}\n');
    }
    return _result('');
  }

  void install() {
    const channel = MethodChannel('oc/termux');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, _handle);
    addTearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });
  }
}
