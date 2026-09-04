import 'dart:async';
import 'dart:convert';

import 'package:opencode_mobile/api/product_repository.dart';

ManagedShell sampleShell({
  String id = 'sh_a',
  String sessionID = 'ses_a',
  String command = 'flutter test --concurrency=1',
  ManagedShellStatus status = ManagedShellStatus.running,
}) => ManagedShell(
  id: id,
  command: command,
  status: status,
  sessionID: sessionID,
  startedAt: DateTime.now().subtract(const Duration(minutes: 2, seconds: 14)),
  exitCode: status == ManagedShellStatus.exited ? 0 : null,
);

class FakeManagedShellRepository extends ProductRepository {
  bool supported = true;
  List<ManagedShell> shells = [sampleShell()];
  String identity = 'server-1';
  String output =
      '✓ Composer layout\n✓ Draft restoration\nRunning navigation tests…\n';
  int listReads = 0;
  int infoReads = 0;
  int outputReads = 0;
  int stopCalls = 0;
  Object? failure;
  final cursors = <int>[];
  final timeouts = <Duration?>[];
  final pages = <int, ManagedShellOutput>{};
  Completer<ManagedShell?>? pendingInfo;

  @override
  Future<ManagedShellList> loadRunningShells() async {
    listReads++;
    if (failure != null) throw failure!;
    return ManagedShellList(
      supported: supported,
      shells: shells.where((s) => s.running).toList(),
    );
  }

  @override
  Future<ManagedShell?> getManagedShell(String id) async {
    infoReads++;
    if (pendingInfo != null) return pendingInfo!.future;
    if (failure != null) throw failure!;
    for (final shell in shells) {
      if (shell.id == id) return shell;
    }
    return null;
  }

  @override
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  }) async {
    outputReads++;
    cursors.add(cursor);
    if (failure != null) throw failure!;
    if (pages.containsKey(cursor)) return pages[cursor]!;
    final bytes = utf8.encode(output);
    final start = cursor.clamp(0, bytes.length);
    return ManagedShellOutput(
      text: utf8.decode(bytes.sublist(start)),
      cursor: bytes.length,
      size: bytes.length,
      truncated: false,
    );
  }

  @override
  Future<void> stopManagedShell(String id) async {
    if (failure != null) throw failure!;
    stopCalls++;
    shells.removeWhere((shell) => shell.id == id);
  }

  @override
  Future<ManagedShell> setManagedShellTimeout(
    String id,
    Duration? timeout,
  ) async {
    if (failure != null) throw failure!;
    timeouts.add(timeout);
    return shells.firstWhere((shell) => shell.id == id);
  }

  @override
  Future<String?> managedShellServerIdentity() async => identity;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
