import 'dart:math';

import 'package:flutter/foundation.dart';

import '../domain/development_service.dart';
import '../domain/managed_shell.dart';
import 'development_service_store.dart';

/// One visible project panel. Every remote mutation resolves the current
/// transport, then rechecks scope. Only a receipt written by Start can authorize
/// control of a shell; command text or a discovered PID never grants ownership.
class DevelopmentServices extends ChangeNotifier {
  DevelopmentServices({
    required this.store,
    required this.directory,
    required this.workspace,
    required this.resolveGateway,
    required this.isCurrent,
    required this.supported,
  }) {
    reload();
  }

  final DevelopmentServiceStore store;
  final String directory;
  final String? workspace;
  final Future<ManagedShellGateway?> Function() resolveGateway;
  final bool Function() isCurrent;
  final bool Function() supported;
  List<DevelopmentService> services = const [];
  final Map<String, ManagedShell> _observed = {};
  final Map<String, String> logs = {};
  bool busy = false;
  bool readable = true;
  Object? error;
  int _generation = 0;
  bool _disposed = false;

  static String newID() {
    final random = Random.secure();
    return List.generate(
      24,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }

  void reload() {
    try {
      services = store
          .load()
          .where((s) => s.directory == directory && s.workspace == workspace)
          .toList();
      readable = true;
    } catch (failure) {
      services = const [];
      readable = false;
      error = failure;
    }
  }

  DevelopmentService _service(String id) =>
      services.firstWhere((s) => s.id == id);

  DevelopmentServiceStatus status(DevelopmentService service) {
    if (service.run == null) return DevelopmentServiceStatus.notStarted;
    if (service.run!.stopped) return DevelopmentServiceStatus.stopped;
    final shell = _observed[service.id];
    if (shell == null || !_matches(service, shell)) {
      return DevelopmentServiceStatus.unknown;
    }
    return switch (shell.status) {
      ManagedShellStatus.running => DevelopmentServiceStatus.running,
      ManagedShellStatus.exited ||
      ManagedShellStatus.killed ||
      ManagedShellStatus.timeout => DevelopmentServiceStatus.stopped,
      ManagedShellStatus.unknown => DevelopmentServiceStatus.unknown,
    };
  }

  int? exitCode(DevelopmentService service) => _observed[service.id]?.exitCode;

  bool canStart(DevelopmentService service) =>
      supported() &&
      !busy &&
      isCurrent() &&
      readable &&
      (status(service) == DevelopmentServiceStatus.notStarted ||
          status(service) == DevelopmentServiceStatus.stopped);

  bool canStop(DevelopmentService service) =>
      supported() &&
      !busy &&
      isCurrent() &&
      status(service) == DevelopmentServiceStatus.running;

  void invalidateRuntime() {
    _generation++;
    _observed.clear();
    logs.clear();
    busy = false;
    _notify();
  }

  bool _current(int generation) =>
      !_disposed && isCurrent() && generation == _generation;

  void _check(int generation) {
    if (!_current(generation)) {
      throw StateError('The connection or project changed');
    }
  }

  Future<ManagedShellGateway> _gateway(int generation) async {
    _check(generation);
    if (!supported()) throw StateError('Service control is unavailable');
    final gateway = await resolveGateway();
    _check(generation);
    if (gateway == null) throw StateError('Reconnect to check this service');
    return gateway;
  }

  Future<void> _action(Future<void> Function(int generation) body) async {
    if (busy || !isCurrent() || _disposed || !readable) return;
    final generation = _generation;
    busy = true;
    error = null;
    _notify();
    try {
      await body(generation);
    } catch (failure) {
      if (_current(generation)) {
        error = failure;
        _observed.clear();
      }
    } finally {
      if (_current(generation)) {
        reload();
        busy = false;
        _notify();
      }
    }
  }

  Future<void> register(DevelopmentService service) =>
      _action((generation) async {
        if (service.directory != directory ||
            service.workspace != workspace ||
            service.run != null ||
            service.name.trim().isEmpty ||
            service.name.length > 80 ||
            service.command.trim().isEmpty ||
            service.command.length > 4096) {
          throw StateError('Invalid project service');
        }
        await store.save(service);
      });

  bool _matches(DevelopmentService service, ManagedShell shell) {
    final run = service.run;
    return run != null &&
        !run.stopped &&
        shell.ownerToken == run.ownerToken &&
        shell.command == service.command &&
        shell.directory == service.directory &&
        (run.shellID == null || shell.id == run.shellID) &&
        (run.startedAt == null ||
            shell.startedAt.millisecondsSinceEpoch == run.startedAt);
  }

  Future<ManagedShell?> _reconcile(
    DevelopmentService service,
    ManagedShellGateway gateway,
    int generation,
  ) async {
    final run = service.run;
    if (run == null || run.stopped) return null;
    ManagedShell? shell;
    if (run.shellID != null) {
      shell = await gateway.getManagedShell(run.shellID!);
    } else {
      // Recover only this persisted, pre-POST ownership nonce. Never adopt a
      // shell because its command/name/PID looks familiar.
      final inventory = await gateway.loadRunningShells();
      _check(generation);
      final matches = inventory.shells
          .where((s) => _matches(service, s))
          .toList();
      if (matches.length == 1) shell = matches.single;
    }
    _check(generation);
    if (shell == null || !_matches(service, shell)) {
      _observed.remove(service.id);
      return null;
    }
    if (run.shellID == null) {
      await store.updateRun(
        service.id,
        DevelopmentServiceRun(
          ownerToken: run.ownerToken,
          shellID: shell.id,
          startedAt: shell.startedAt.millisecondsSinceEpoch,
        ),
        expectedOwner: run.ownerToken,
      );
      _check(generation);
    }
    _observed[service.id] = shell;
    return shell;
  }

  Future<void> refresh() => _action((generation) async {
    reload();
    _observed.clear();
    if (!supported() ||
        !services.any((s) => s.run != null && !s.run!.stopped)) {
      return;
    }
    final gateway = await _gateway(generation);
    for (final service in services) {
      await _reconcile(service, gateway, generation);
    }
  });

  Future<void> start(String id) =>
      _action((generation) => _start(id, generation));

  Future<void> _start(String id, int generation) async {
    final service = _service(id);
    final state = status(service);
    if (state != DevelopmentServiceStatus.notStarted &&
        state != DevelopmentServiceStatus.stopped) {
      throw StateError('Confirm the previous run before starting another');
    }
    final gateway = await _gateway(generation);
    final owner = newID();
    await store.updateRun(id, DevelopmentServiceRun(ownerToken: owner));
    reload();
    _check(generation);
    // Failure or a lost response deliberately leaves the pending nonce saved.
    final shell = await gateway.startManagedShell(
      command: service.command,
      directory: directory,
      ownerToken: owner,
    );
    final pending = service.withRun(DevelopmentServiceRun(ownerToken: owner));
    if (!_matches(pending, shell) || !shell.id.startsWith('sh_')) {
      throw StateError('The server did not confirm ownership of this command');
    }
    // Preserve the receipt even if this route closed while POST was pending.
    // The store refuses writes after profile/config deletion or a newer start.
    await store.updateRun(
      id,
      DevelopmentServiceRun(
        ownerToken: owner,
        shellID: shell.id,
        startedAt: shell.startedAt.millisecondsSinceEpoch,
      ),
      expectedOwner: owner,
    );
    if (_current(generation)) {
      _observed[id] = shell;
      logs.remove(id);
    }
  }

  Future<void> stop(String id) =>
      _action((generation) => _stop(id, generation));

  Future<void> _stop(String id, int generation) async {
    final service = _service(id);
    final gateway = await _gateway(generation);
    final shell = await _reconcile(service, gateway, generation);
    if (shell == null) {
      throw StateError('Ownership could not be confirmed; nothing was stopped');
    }
    _check(generation);
    await gateway.stopManagedShell(shell.id);
    _check(generation);
    final remaining = await gateway.getManagedShell(shell.id);
    _check(generation);
    if (remaining != null) {
      throw StateError('The server has not confirmed the stop');
    }
    await store.updateRun(
      id,
      DevelopmentServiceRun(
        ownerToken: service.run!.ownerToken,
        shellID: shell.id,
        startedAt: shell.startedAt.millisecondsSinceEpoch,
        stopped: true,
      ),
      expectedOwner: service.run!.ownerToken,
    );
    _observed.remove(id);
    logs.remove(id);
    reload();
  }

  Future<void> restart(String id) => _action((generation) async {
    await _stop(id, generation);
    _check(generation);
    await _start(id, generation);
  });

  Future<void> readLogs(String id) => _action((generation) async {
    final gateway = await _gateway(generation);
    final service = _service(id);
    final shell = await _reconcile(service, gateway, generation);
    if (shell == null) {
      throw StateError('The command log could not be confirmed');
    }
    final head = await gateway.readManagedShellOutput(
      shell.id,
      cursor: 0,
      limit: 1,
    );
    _check(generation);
    if (head.size < 0) throw const FormatException('Invalid log size');
    final start = max(0, head.size - 65536);
    final tail = await gateway.readManagedShellOutput(
      shell.id,
      cursor: start,
      limit: 65536,
    );
    _check(generation);
    if (tail.cursor < start || tail.cursor > tail.size) {
      throw const FormatException('Invalid log cursor');
    }
    var text = tail.text;
    if (text.length > 65536) text = text.substring(text.length - 65536);
    logs[id] = text
        .replaceAll(RegExp(r'\x1B\][^\x07]*(?:\x07|\x1B\\)'), '')
        .replaceAll(RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]'), '')
        .replaceAll('\r\n', '\n')
        .replaceAll('\r', '\n');
  });

  Future<void> forgetRun(String id) => _action((generation) async {
    await store.updateRun(id, null);
    _observed.remove(id);
    logs.remove(id);
  });

  Future<void> remove(String id) => _action((generation) async {
    await store.remove(id);
    _observed.remove(id);
    logs.remove(id);
  });

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}
