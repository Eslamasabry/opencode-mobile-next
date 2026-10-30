// The "This phone" page (ThisPhoneScreen) for OpenCode running inside the
// app, over a stood-in Linux. Nothing here starts a process.
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';
import '../fakes/setup_inventory.dart';

/// The in-app Linux, stood in for: installed, with the server running.
class _FakeLinux extends BuiltinLinux {
  _FakeLinux({this.recovery = false});
  final bool recovery;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: !recovery,
    serverPort: BuiltinLinux.serverPort,
    bytesUsed: 734003200,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '2.1.1\n');

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    if (recovery) {
      throw const BuiltinLinuxException(
        'The app cannot safely identify previous work.',
        code: 'runtime_ownership_unknown',
      );
    }
  }

  @override
  Future<void> stopServer() async {}

  @override
  Future<String> serverLog({int tailBytes = 32768}) async => '';

  @override
  Future<BuiltinProjectStorage> projectStorage() async =>
      const BuiltinProjectStorage(
        runtimeBytes: 734003200,
        projectsBytes: 52428800,
        measuredAtMilliseconds: 0,
      );
}

Widget _scene({
  bool recovery = false,
  Set<String> installed = const {},
  bool inventoryUnavailable = false,
}) => FakeAppScene<SetupInventoryScene>(
  autoTap: recovery ? 'this-phone-start' : null,
  platform: FakePlatform.android,
  create: () => SetupInventoryScene.create(
    installed: installed,
    unavailable: inventoryUnavailable,
  ),
  builder: (context, scene) {
    final app = scene.app;
    app.store.profiles.first.password = 'catalog-only-password';
    final linux = _FakeLinux(recovery: recovery);
    return ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(app.store)),
        connProvider.overrideWithValue(app.connection),
        builtinLinuxProvider.overrideWithValue(linux),
      ],
      child: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
    );
  },
);

CatalogComponent thisPhoneScreen() => catalogComponent(
  area: 'Servers',
  screen: 'ThisPhoneScreen',
  name: 'This phone',
  useCases: [
    WidgetbookUseCase(
      name: 'OpenCode running in the app',
      builder: (_) => _scene(),
    ),
    WidgetbookUseCase(
      name: 'Python and AI Team installed',
      builder: (_) => _scene(installed: const {'python', 'aiteam'}),
    ),
    WidgetbookUseCase(
      name: 'Installed tools inventory unavailable',
      builder: (_) => _scene(inventoryUnavailable: true),
    ),
    WidgetbookUseCase(
      name: 'Previous work needs Android recovery',
      builder: (_) => _scene(recovery: true),
    ),
  ],
);
