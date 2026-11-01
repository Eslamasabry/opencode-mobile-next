/// The real [LinuxServices]: the files in the app's own Ubuntu, read and
/// written straight from Android's side (`<app files>/linux/ubuntu/...`,
/// as [BuiltinRootfsFolders] does), and the native service `svc` that runs
/// the supervisor ([BuiltinLinux.startService]).
///
/// Links inside the rootfs are never followed: an absolute link a program
/// made inside Ubuntu would point somewhere else on the phone when read
/// from outside. Every write is a temp file renamed into place.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../ui/kit/kit_log_panel.dart' show KitLogLine;
import '../builtin_folders.dart';
import '../builtin_linux.dart';
import '../builtin_server.dart' show builtinLinuxProvider;
import 'linux_service_files.dart';
import 'linux_services.dart';
import 'oc_svd_script.dart';

class RootfsLinuxServices implements LinuxServices {
  RootfsLinuxServices({
    required this.linux,
    Future<Directory?> Function()? locateRootfs,
    this.pollEvery = const Duration(milliseconds: 250),
    DateTime Function()? clock,
  }) : _locate = locateRootfs ?? BuiltinRootfsFolders.locateRootfs,
       _clock = clock ?? DateTime.now;

  final BuiltinLinux linux;
  final Future<Directory?> Function() _locate;
  final DateTime Function() _clock;

  /// How often [remove] and a last [stop] look whether a service is off.
  final Duration pollEvery;

  Future<void> _ensuring = Future.value();

  // --- paths --------------------------------------------------------------

  Future<_Paths> _paths() async {
    final root = await _locate();
    if (root == null) {
      throw const LinuxServiceException(LinuxServiceProblem.linuxMissing);
    }
    final paths = _Paths(root.path);
    // A folder on the way that is a link (made inside Ubuntu) would lead
    // reads and writes elsewhere on the phone: refuse it.
    for (final path in paths.chain) {
      if (await FileSystemEntity.type(path, followLinks: false) ==
          FileSystemEntityType.link) {
        throw LinuxServiceException(
          LinuxServiceProblem.writeFailed,
          details: 'link: $path',
        );
      }
    }
    return paths;
  }

  /// Makes [path] (under the rootfs) a real folder, refusing links on the
  /// way down from [base].
  static Future<void> _folder(String base, String path) async {
    var at = base;
    for (final part in path.substring(base.length).split('/')) {
      if (part.isEmpty) continue;
      at = '$at/$part';
      final type = await FileSystemEntity.type(at, followLinks: false);
      if (type == FileSystemEntityType.notFound) {
        await Directory(at).create();
      } else if (type != FileSystemEntityType.directory) {
        throw LinuxServiceException(
          LinuxServiceProblem.writeFailed,
          details: 'not a folder: $at',
        );
      }
    }
  }

  static Future<String?> _readFile(String path) async {
    if (await FileSystemEntity.type(path, followLinks: false) !=
        FileSystemEntityType.file) {
      return null;
    }
    try {
      return await File(path).readAsString();
    } on Object {
      return null;
    }
  }

  static Future<void> _write(String base, String path, String text) async {
    try {
      await _folder(base, File(path).parent.path);
      final temp = File('$path.tmp-$pid');
      await temp.writeAsString(text, flush: true);
      await temp.rename(path);
    } on LinuxServiceException {
      rethrow;
    } on Object catch (error) {
      throw LinuxServiceException(
        LinuxServiceProblem.writeFailed,
        details: '$error',
      );
    }
  }

  static Future<void> _delete(String path) async {
    final type = await FileSystemEntity.type(path, followLinks: false);
    if (type == FileSystemEntityType.notFound) return;
    try {
      // A link is removed itself, never what it points at.
      await (type == FileSystemEntityType.link ? Link(path) : File(path))
          .delete();
    } on Object {
      // Gone already, or not ours to delete: nothing to do.
    }
  }

  // --- reading ------------------------------------------------------------

  Future<bool> _supervisorRunning() async {
    try {
      return (await linux.status()).serviceRunning(linuxServicesNativeName);
    } on Object {
      return false;
    }
  }

  Future<List<String>> _names(_Paths p) async {
    final dir = Directory(p.defs);
    if (await FileSystemEntity.type(dir.path, followLinks: false) !=
        FileSystemEntityType.directory) {
      return const [];
    }
    final names = <String>[];
    await for (final entity in dir.list(followLinks: false)) {
      if (entity is! File) continue;
      final file = entity.path.split('/').last;
      if (!file.endsWith('.svc')) continue;
      final name = file.substring(0, file.length - 4);
      if (linuxServiceNameValid(name)) names.add(name);
    }
    return names..sort();
  }

  Future<LinuxServiceDef?> _def(_Paths p, String name) async {
    final text = await _readFile(p.def(name));
    return text == null ? null : decodeServiceDef(name, text);
  }

  Future<bool> _want(_Paths p, String name) async =>
      (await _readFile(p.want(name)))?.trim() == 'on';

  Future<LinuxServiceStateFile?> _state(_Paths p, String name) async {
    for (var attempt = 0; attempt < 2; attempt++) {
      final text = await _readFile(p.state(name));
      if (text == null) return null;
      final parsed = LinuxServiceStateFile.parse(text);
      if (parsed != null) return parsed;
      // Caught while the supervisor rewrote it: once more.
      await Future<void>.delayed(const Duration(milliseconds: 40));
    }
    return null;
  }

  Future<List<LinuxServiceDef>> _defs(_Paths p) async => [
    for (final name in await _names(p)) ?await _def(p, name),
  ];

  @override
  Future<List<LinuxServiceState>> read() async {
    final p = await _paths();
    final running = await _supervisorRunning();
    final states = <LinuxServiceState>[];
    for (final name in await _names(p)) {
      final def = await _def(p, name);
      if (def == null) continue;
      states.add(
        combineServiceState(
          def: def,
          want: await _want(p, name),
          state: await _state(p, name),
          supervisorRunning: running,
        ),
      );
    }
    return sortServices(states);
  }

  @override
  Stream<List<LinuxServiceState>> watch({
    Duration every = const Duration(seconds: 2),
  }) {
    late final StreamController<List<LinuxServiceState>> controller;
    Timer? timer;
    var reading = false;
    Future<void> tick() async {
      if (reading) return;
      reading = true;
      try {
        final states = await read();
        if (!controller.isClosed) controller.add(states);
      } on Object catch (error, stack) {
        if (!controller.isClosed) controller.addError(error, stack);
      } finally {
        reading = false;
      }
    }

    controller = StreamController<List<LinuxServiceState>>(
      onListen: () {
        unawaited(tick());
        timer = Timer.periodic(every, (_) => unawaited(tick()));
      },
      onPause: () => timer?.cancel(),
      onResume: () => timer = Timer.periodic(every, (_) => unawaited(tick())),
      onCancel: () => timer?.cancel(),
    );
    return controller.stream;
  }

  // --- acts ---------------------------------------------------------------

  Future<LinuxServiceDef> _require(_Paths p, String name) async {
    if (!linuxServiceNameValid(name)) {
      throw LinuxServiceException(
        LinuxServiceProblem.invalidName,
        details: 'name $name',
      );
    }
    return await _def(p, name) ??
        (throw LinuxServiceException(
          LinuxServiceProblem.invalidName,
          details: 'no service $name',
        ));
  }

  @override
  Future<void> save(LinuxServiceDef def) async {
    final p = await _paths();
    final others = [
      for (final other in await _defs(p))
        if (other.name != def.name) other,
    ];
    final isNew = await _def(p, def.name) == null;
    validateServiceDef(def, others: others, isNew: isNew);
    await _write(p.root, p.def(def.name), encodeServiceDef(def));
    if (isNew) await _write(p.root, p.want(def.name), 'off\n');
  }

  /// A token the supervisor has not seen: a new value means "(re)start now".
  String _kickToken() => '${_clock().microsecondsSinceEpoch}';

  @override
  Future<void> start(String name) async {
    final p = await _paths();
    await _require(p, name);
    final running = await _supervisorRunning();
    final state = running ? await _state(p, name) : null;
    await _write(p.root, p.want(name), 'on\n');
    // A service that runs already is left alone; any other is started now,
    // whatever its backoff or a give-up said.
    final phase = state?.phase;
    if (phase != LinuxServicePhase.running &&
        phase != LinuxServicePhase.stopping) {
      await _write(p.root, p.kick(name), '${_kickToken()}\n');
    }
    await ensureSupervisor();
  }

  @override
  Future<void> stop(String name) async {
    final p = await _paths();
    final def = await _require(p, name);
    await _write(p.root, p.want(name), 'off\n');
    // Nothing else on: the supervisor goes too once this one is off, so no
    // idle process and no "running" notification are left behind.
    unawaited(_retireIfIdle(p, wait: def.stopTimeout));
  }

  @override
  Future<void> restart(String name) async {
    final p = await _paths();
    await _require(p, name);
    await _write(p.root, p.kick(name), '${_kickToken()}\n');
    await ensureSupervisor();
  }

  @override
  Future<void> remove(String name) async {
    final p = await _paths();
    final def = await _require(p, name);
    await _write(p.root, p.want(name), 'off\n');
    if (await _supervisorRunning()) {
      await _waitOff(p, name, def.stopTimeout + const Duration(seconds: 2));
    }
    for (final path in [
      p.def(name),
      p.want(name),
      p.kick(name),
      p.state(name),
      p.log(name),
      '${p.log(name)}.1',
    ]) {
      await _delete(path);
    }
    unawaited(_retireIfIdle(p, wait: Duration.zero));
  }

  /// Waits until [name] is off (or not running at all), at most [limit].
  Future<void> _waitOff(_Paths p, String name, Duration limit) async {
    final until = _clock().add(limit);
    while (true) {
      final phase = (await _state(p, name))?.phase;
      if (phase != LinuxServicePhase.running &&
          phase != LinuxServicePhase.stopping &&
          phase != LinuxServicePhase.starting) {
        return;
      }
      if (!_clock().isBefore(until)) return;
      await Future<void>.delayed(pollEvery);
    }
  }

  Future<bool> _anyOn(_Paths p) async {
    for (final name in await _names(p)) {
      if (await _want(p, name) && await _def(p, name) != null) return true;
    }
    return false;
  }

  Future<void> _retireIfIdle(_Paths p, {required Duration wait}) async {
    try {
      if (await _anyOn(p) || !await _supervisorRunning()) return;
      for (final name in await _names(p)) {
        await _waitOff(p, name, wait + const Duration(seconds: 2));
      }
      // Checked again: someone may have started one meanwhile.
      if (await _anyOn(p)) return;
      await linux.stopService(linuxServicesNativeName);
    } on Object catch (error) {
      debugPrint('linux services: idle supervisor not stopped: $error');
    }
  }

  @override
  Future<List<KitLogLine>> logTail(String name, {int bytes = 65536}) async {
    final p = await _paths();
    if (!linuxServiceNameValid(name)) return const [];
    final current = await _tail(p.log(name), bytes);
    var text = current.text;
    var cut = current.cut;
    if (!cut && current.length < bytes) {
      final older = await _tail('${p.log(name)}.1', bytes - current.length);
      if (older.length > 0) {
        text = older.text + text;
        cut = older.cut;
      }
    }
    return serviceLogLines(text, cutStart: cut);
  }

  static Future<({String text, int length, bool cut})> _tail(
    String path,
    int bytes,
  ) async {
    if (await FileSystemEntity.type(path, followLinks: false) !=
        FileSystemEntityType.file) {
      return (text: '', length: 0, cut: false);
    }
    RandomAccessFile? file;
    try {
      file = await File(path).open();
      final size = await file.length();
      final skip = size > bytes ? size - bytes : 0;
      await file.setPosition(skip);
      final data = await file.read(size - skip);
      return (
        text: utf8.decode(data, allowMalformed: true),
        length: data.length,
        cut: skip > 0,
      );
    } on Object {
      return (text: '', length: 0, cut: false);
    } finally {
      await file?.close();
    }
  }

  // --- heat ---------------------------------------------------------------

  /// Whether the heat guard holds every service off now (the `hold` file).
  Future<bool> heatHeld() async {
    try {
      final p = await _paths();
      return await FileSystemEntity.type(p.hold, followLinks: false) !=
          FileSystemEntityType.notFound;
    } on LinuxServiceException {
      return false;
    }
  }

  /// Holds every service off ([held]) or gives them back. The person's
  /// choices (`want`) are never touched: the supervisor stops what runs
  /// while the file is there and starts what is on once it is gone.
  Future<void> setHeatHold(bool held) async {
    final p = await _paths();
    if (held) {
      await _write(p.root, p.hold, 'critical\n');
    } else {
      await _delete(p.hold);
    }
  }

  // --- supervisor ---------------------------------------------------------

  @override
  Future<void> ensureSupervisor() {
    // One at a time: two callers must not start `svc` twice (a second
    // start restarts it, and every service with it).
    final next = _ensuring.then((_) => _ensure());
    _ensuring = next.catchError((Object _) {});
    return next;
  }

  Future<void> _ensure() async {
    final _Paths p;
    try {
      p = await _paths();
    } on LinuxServiceException {
      return; // no Linux: nothing to run
    }
    final changed = await _installScript(p);
    final running = await _supervisorRunning();
    final anyOn = await _anyOn(p);
    if (running && !changed) return;
    if (!running && !anyOn) return;
    if (running && changed && !anyOn) {
      // An old supervisor with nothing to run: retire it, the next start
      // uses the new one.
      await _retireIfIdle(p, wait: Duration.zero);
      return;
    }
    try {
      // A running `svc` is restarted by this (a newer script). Its SIGTERM
      // would read as the notification's Stop and turn every service off:
      // `keep` tells it this stop is the app's own and keeps the choices.
      if (running) await _write(p.root, p.keep, 'restart\n');
      await linux.startService(linuxServicesNativeName, ocSvdLaunchScript());
    } on Object catch (error) {
      await _delete(p.keep);
      throw LinuxServiceException(
        LinuxServiceProblem.supervisorDown,
        details: '$error',
      );
    }
  }

  /// Writes the supervisor when the file differs from this app's copy; true
  /// when it did.
  Future<bool> _installScript(_Paths p) async {
    final script = ocSvdScript();
    if (await _readFile(p.script) == script) return false;
    try {
      await _write(p.root, p.script, script);
      return true;
    } on LinuxServiceException catch (error) {
      throw LinuxServiceException(
        LinuxServiceProblem.supervisorDown,
        details: error.details,
      );
    }
  }
}

class _Paths {
  _Paths(this.root);

  final String root;

  String get home => '$root$linuxServicesHome';
  String get logs => '$root$linuxServicesLogs';
  String get defs => '$home/defs';
  String get script => '$home/oc-svd';
  String get hold => '$home/hold';
  String get keep => '$home/keep';
  String def(String name) => '$defs/$name.svc';
  String want(String name) => '$home/want/$name';
  String kick(String name) => '$home/kick/$name';
  String state(String name) => '$home/state/$name';
  String log(String name) => '$logs/$name.log';

  /// Every folder between the rootfs and the files read or written.
  List<String> get chain => [
    '$root/root',
    '$root/root/.oc-builtin',
    home,
    defs,
    '$home/want',
    '$home/kick',
    '$home/state',
    '$root/var',
    '$root/var/log',
    logs,
  ];
}

/// The app's one [LinuxServices].
final linuxServicesProvider = Provider<LinuxServices>(
  (ref) => RootfsLinuxServices(linux: ref.watch(builtinLinuxProvider)),
);
