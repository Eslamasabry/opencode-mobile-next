import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/development_service.dart';
import 'package:opencode_mobile/state/connection.dart';

const sampleService = DevelopmentService(
  id: 'vite',
  name: 'Shopfront preview',
  command: 'npm run dev',
  directory: '/work/shopfront',
  url: 'http://192.168.1.20:5173',
);

class ServiceRepository extends ProductRepository {
  final Map<String, ManagedShell> shells = {};
  final List<String> stops = [];
  final List<int> cursors = [];
  int starts = 0;
  String output = 'VITE ready in 410 ms\nLocal: http://localhost:5173/\n';
  Completer<void>? createGate;
  Completer<void>? infoGate;
  bool loseCreateResponse = false;
  bool failReads = false;

  @override
  Future<ManagedShell> startManagedShell({
    required String command,
    required String directory,
    required String ownerToken,
  }) async {
    starts++;
    final shell = ManagedShell(
      id: 'sh_$starts',
      command: command,
      directory: directory,
      ownerToken: ownerToken,
      status: ManagedShellStatus.running,
      startedAt: DateTime.fromMillisecondsSinceEpoch(starts * 1000),
    );
    shells[shell.id] = shell;
    await createGate?.future;
    if (loseCreateResponse) throw StateError('Connection lost after create');
    return shell;
  }

  @override
  Future<ManagedShell?> getManagedShell(String id) async {
    await infoGate?.future;
    if (failReads) throw StateError('Server unavailable');
    return shells[id];
  }

  @override
  Future<ManagedShellList> loadRunningShells() async => ManagedShellList(
    supported: true,
    shells: shells.values.where((s) => s.running).toList(),
  );

  @override
  Future<void> stopManagedShell(String id) async {
    stops.add(id);
    shells.remove(id);
  }

  @override
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  }) async {
    cursors.add(cursor);
    final bytes = utf8.encode(output);
    final end = min(bytes.length, cursor + limit);
    return ManagedShellOutput(
      text: utf8.decode(bytes.sublist(cursor, end), allowMalformed: true),
      cursor: end,
      size: bytes.length,
      truncated: cursor > 0,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class ServicesConnection extends ConnectionController {
  ServicesConnection(super.store, this.services);
  final ServiceRepository services;
  bool supported = true;
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(developmentServices: supported);
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async => services;
}
