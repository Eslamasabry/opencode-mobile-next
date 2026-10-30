// The Terminal page (TerminalPage): a live shell at once, on a stood-in
// server or a stood-in built-in Linux. Nothing here starts a process.
import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/state/project_dev_server.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

const _serverOutput =
    'dev@laptop:~/shopfront\$ flutter test\r\n'
    '00:04 +41: All tests passed!\r\n'
    'dev@laptop:~/shopfront\$ git status --short\r\n'
    ' M lib/checkout/checkout_page.dart\r\n'
    'dev@laptop:~/shopfront\$ ';

const _phoneOutput =
    '\x1b[1;32mroot@localhost\x1b[0m:\x1b[1;34m~/neon-breaker\x1b[0m# npm run dev &\r\n'
    '[1] 4182\r\n'
    '  \x1b[32mVITE v5.4.2\x1b[0m  ready in 412 ms\r\n'
    '  Local:   \x1b[36mhttp://localhost:5173/\x1b[0m\r\n'
    '\x1b[1;32mroot@localhost\x1b[0m:\x1b[1;34m~/neon-breaker\x1b[0m# ';

TerminalProcess _process(String id, String title, {bool running = true}) =>
    TerminalProcess(
      id: id,
      title: title,
      command: running ? 'bash' : 'flutter',
      arguments: const [],
      directory: '/srv/shopfront',
      running: running,
      pid: 4000,
      exitCode: running ? null : 1,
    );

class _Channel implements TerminalChannel {
  _Channel() {
    _output.add(_serverOutput);
  }
  final _output = StreamController<String>();
  @override
  Stream<String> get output => _output.stream;
  @override
  int? get cursor => null;
  @override
  void write(String value) {}
  @override
  Future<void> close() async {
    if (!_output.isClosed) await _output.close();
  }
}

/// A server with [processes] as its terminals.
class _TerminalRepository implements ProductRepository {
  _TerminalRepository(this.processes);
  List<TerminalProcess> processes;

  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<TerminalProcess>> listTerminals() async => [...processes];
  @override
  Future<TerminalProcess> createTerminal({String? title}) async {
    final made = _process('pty-new', title ?? 'Terminal 1');
    processes = [...processes, made];
    return made;
  }

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) async =>
      _Channel();
  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// The built-in Linux, stood in for.
class _Linux extends BuiltinLinux {
  _Linux({required this.installed});
  final bool installed;
  @override
  Future<BuiltinLinuxStatus> status() async =>
      BuiltinLinuxStatus(installed: installed, phase: BuiltinLinuxPhase.idle);
}

/// LocalTerminal.kt, stood in for: each shell starts at once and prints a
/// dev server starting.
class _Backend implements LocalTerminalBackend {
  final _events = StreamController<LocalTerminalEvent>.broadcast();
  var _next = 1;
  @override
  Stream<LocalTerminalEvent> get events => _events.stream;
  @override
  Future<LocalShellInfo> start({
    required int rows,
    required int cols,
    String? signInProfile,
    List<String>? signInProgram,
  }) async {
    final id = _next++;
    Timer.run(
      () => _events.add(
        LocalTerminalOutput(id, Uint8List.fromList(utf8.encode(_phoneOutput))),
      ),
    );
    return LocalShellInfo(id: id, pid: 4000 + id);
  }

  @override
  Future<LocalTerminalListing> list() async =>
      const LocalTerminalListing(shells: []);
  @override
  Future<Uint8List?> attach(int id) async => null;
  @override
  Future<void> write(int id, Uint8List data) async {}
  @override
  Future<void> ack(int id) async {}
  @override
  Future<void> resize(int id, {required int rows, required int cols}) async {}
  @override
  Future<void> stop(int id) async {}
  @override
  Future<void> remove(int id) async {}
  Future<void> close() => _events.close();
}

class _Scene implements SceneResource {
  _Scene(this.app, {required bool installed, required bool devServer})
    : linux = _Linux(installed: installed),
      dev = ProjectDevServer(
        look: () async => devServer ? const DevPreviewLink(port: 5173) : null,
      );
  final FakeApp app;
  final _Linux linux;
  final ProjectDevServer dev;
  final backend = _Backend();
  late final sessions = LocalTerminalSessions(backend: backend);

  @override
  void dispose() {
    dev.dispose();
    sessions.dispose();
    unawaited(backend.close());
    app.dispose();
  }
}

WidgetbookUseCase _case(
  String name, {
  List<TerminalProcess> terminals = const [],
  TerminalSource source = TerminalSource.server,
  bool installed = true,
  bool devServer = false,
}) => WidgetbookUseCase(
  name: name,
  builder: (_) => FakeAppScene<_Scene>(
    platform: FakePlatform.android,
    create: () async => _Scene(
      await FakeApp.create(
        connected: true,
        repository: _TerminalRepository([...terminals]),
      ),
      installed: installed,
      devServer: devServer,
    ),
    builder: (context, scene) => ProviderScope(
      overrides: [
        builtinLinuxProvider.overrideWithValue(scene.linux),
        localTerminalProvider.overrideWithValue(scene.sessions),
      ],
      child: TerminalPage(
        controller: scene.app.connection,
        initialSource: source,
        localSupported: true,
        linux: scene.linux,
        sessions: scene.sessions,
        devServer: scene.dev,
      ),
    ),
  ),
);

CatalogComponent terminalScreen() => catalogComponent(
  area: 'Servers',
  screen: 'TerminalPage',
  name: 'Terminal',
  useCases: [
    _case(
      'Built-in Linux, dev server running',
      source: TerminalSource.phone,
      devServer: true,
    ),
    _case('A server terminal', terminals: [_process('pty-1', 'build-server')]),
    _case(
      'Three terminals as tabs',
      terminals: [
        _process('pty-1', 'build-server'),
        _process('pty-2', 'tests', running: false),
        _process('pty-3', 'lint', running: false),
      ],
    ),
    _case(
      'Built-in Linux not set up',
      source: TerminalSource.phone,
      installed: false,
    ),
  ],
);
