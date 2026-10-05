import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:opencode_mobile/builtin/local_terminal.dart';

/// LocalTerminal.kt as the Dart side sees it: shells that start at once,
/// output pushed by the test, and every call recorded.
class FakeLocalTerminalBackend implements LocalTerminalBackend {
  final _events = StreamController<LocalTerminalEvent>.broadcast();
  final calls = <String>[];

  /// Bytes written per shell id.
  final written = <int, List<int>>{};

  /// Shells Android already runs before the Dart side asks (a restarted
  /// engine), with the output it kept for them.
  final existing = <LocalShellInfo>[];
  final recent = <int, Uint8List>{};

  /// When set, `start` fails with this message.
  String? startFailure;

  /// When set, `start` waits for it.
  Completer<void>? startGate;

  int processes = 5;
  int _nextId = 1;
  int acks = 0;

  @override
  Stream<LocalTerminalEvent> get events => _events.stream;

  void output(int id, String text) => _events.add(
    LocalTerminalOutput(id, Uint8List.fromList(utf8.encode(text))),
  );

  void outputBytes(int id, List<int> bytes) =>
      _events.add(LocalTerminalOutput(id, Uint8List.fromList(bytes)));

  void exit(int id, int code) => _events.add(LocalTerminalExit(id, code));

  void openUrl(int id, String url) =>
      _events.add(LocalTerminalOpenUrl(id, url));

  String writtenText(int id) =>
      utf8.decode(written[id] ?? const [], allowMalformed: true);

  @override
  Future<LocalShellInfo> start({
    required int rows,
    required int cols,
    String? signInProfile,
  }) async {
    calls.add(
      signInProfile == null
          ? 'start $rows x $cols'
          : 'sign-in $signInProfile $rows x $cols',
    );
    await startGate?.future;
    final failure = startFailure;
    if (failure != null) throw StateError(failure);
    final id = _nextId++;
    return LocalShellInfo(id: id, pid: 1000 + id);
  }

  @override
  Future<LocalTerminalListing> list() async {
    calls.add('list');
    return LocalTerminalListing(shells: existing, processes: processes);
  }

  @override
  Future<Uint8List?> attach(int id) async {
    calls.add('attach $id');
    return recent[id];
  }

  @override
  Future<void> write(int id, Uint8List data) async {
    written.putIfAbsent(id, () => []).addAll(data);
  }

  @override
  Future<void> ack(int id) async => acks++;

  @override
  Future<void> resize(int id, {required int rows, required int cols}) async {
    calls.add('resize $id $rows x $cols');
  }

  @override
  Future<void> stop(int id) async {
    calls.add('stop $id');
    exit(id, 129);
  }

  @override
  Future<void> remove(int id) async {
    calls.add('remove $id');
  }

  Future<void> close() => _events.close();
}
