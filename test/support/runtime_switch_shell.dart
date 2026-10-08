// The shell while this phone switches its own server from OpenCode 2 to
// OpenCode 1: shared by test/runtime_switch_status_test.dart and its golden.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Ubuntu whose server start never finishes on its own: the switch stays in
/// flight until [finish].
class SlowLinux extends BuiltinLinux {
  final _written = Completer<BuiltinLinuxRunResult>();

  /// Ends the start (once; later calls do nothing).
  void finish() {
    if (_written.isCompleted) return;
    _written.complete(
      const BuiltinLinuxRunResult(exitCode: 1, output: 'stopped by the test'),
    );
  }

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: false,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) => _written.future;
}

class _ShellApi extends OpenCodeApi {
  _ShellApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _ShellRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

ServerProfile _phone(String id, ServerFlavor flavor) =>
    ServerProfile(
        id: id,
        name: 'This phone',
        baseUrl: BuiltinLinux.serverUrl,
        flavor: flavor,
      )
      ..username = BuiltinLinux.serverUsername
      ..password = 'secret-$id';

final phoneTwo = _phone('oc2', ServerFlavor.v2);
final phoneOne = _phone('oc1', ServerFlavor.v1);

class _Store extends ProfileStore {
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => [phoneTwo, phoneOne];

  @override
  String? get activeId => phoneTwo.id;
}

/// Connected to OpenCode 2 on this phone, whose connection is now in
/// [snapshot]'s phase.
class SwitchController extends ConnectionController {
  SwitchController(super.store);

  ConnectionStatusSnapshot snapshot = const ConnectionStatusSnapshot(
    phase: ConnectionStatusPhase.reconnecting,
    profileId: 'oc2',
    serverName: 'This phone',
  );

  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;

  @override
  ServerProfile? get profile => phoneTwo;

  void show(ConnectionStatusPhase phase) {
    snapshot = ConnectionStatusSnapshot(
      phase: phase,
      profileId: 'oc2',
      serverName: 'This phone',
    );
    notifyListeners();
  }
}

/// One shell: Ubuntu, the starter and the controller, and the app around
/// [HomeScreen] with the app's status scope, as main.dart hosts it.
class RuntimeSwitchShell {
  RuntimeSwitchShell._(this.linux, this.starter, this.controller);

  static Future<RuntimeSwitchShell> create() async {
    SharedPreferences.setMockInitialValues({});
    final linux = SlowLinux();
    final controller =
        SwitchController(_Store(prefs: await SharedPreferences.getInstance()))
          ..api = _ShellApi()
          ..repository = _ShellRepository()
          ..status = StreamStatus.disconnected;
    return RuntimeSwitchShell._(
      linux,
      BuiltinServerStarter(linux: linux),
      controller,
    );
  }

  final SlowLinux linux;
  final BuiltinServerStarter starter;
  final SwitchController controller;

  /// The app's navigator, for pushing a page without the shell's pill.
  final navigator = GlobalKey<NavigatorState>();

  Widget app({required ThemeData theme, Key? boundaryKey}) {
    return RepaintBoundary(
      key: boundaryKey,
      child: ProviderScope(
        overrides: [
          connProvider.overrideWithValue(controller),
          builtinServerStarterProvider.overrideWithValue(starter),
        ],
        child: MaterialApp(
          navigatorKey: navigator,
          debugShowCheckedModeBanner: false,
          theme: theme,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          // Above the navigator, as main.dart hosts it: every page reads it.
          builder: (context, child) => AppConnectionStatusScope(
            controller: controller,
            navigatorKey: navigator,
            child: child!,
          ),
          home: const HomeScreen(),
        ),
      ),
    );
  }

  /// Ends a start still in flight and lets go of everything.
  void dispose() {
    if (starter.starting) linux.finish();
    controller.dispose();
    starter.dispose();
  }
}
