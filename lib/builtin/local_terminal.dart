import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:xterm/core.dart' as xterm;

import '../platform/platform_capabilities.dart';

/// The app's one set of local shells; they outlive every screen.
final localTerminalProvider = Provider<LocalTerminalSessions>((ref) {
  final sessions = LocalTerminalSessions();
  ref.onDispose(sessions.dispose);
  return sessions;
});

/// What the Android side (LocalTerminal.kt) says about one shell.
@immutable
class LocalShellInfo {
  const LocalShellInfo({
    required this.id,
    required this.pid,
    this.running = true,
    this.exitCode,
    this.signIn = false,
  });

  factory LocalShellInfo.fromMap(Map<Object?, Object?> map) {
    int? asInt(Object? value) => value is num ? value.toInt() : null;
    return LocalShellInfo(
      id: asInt(map['id']) ?? -1,
      pid: asInt(map['pid']) ?? 0,
      running: map['running'] != false,
      exitCode: asInt(map['exitCode']),
      signIn: map['signIn'] == true,
    );
  }

  final int id;
  final int pid;
  final bool running;
  final int? exitCode;

  /// An agent's sign-in ([LocalTerminalSessions.startSignIn]), not a shell.
  final bool signIn;
}

/// One reading of `list`: the shells that exist, and how many processes run
/// as this app now (Android stops the app's extra processes past 32).
@immutable
class LocalTerminalListing {
  const LocalTerminalListing({required this.shells, this.processes});

  final List<LocalShellInfo> shells;
  final int? processes;
}

sealed class LocalTerminalEvent {
  const LocalTerminalEvent(this.id);

  final int id;
}

class LocalTerminalOutput extends LocalTerminalEvent {
  const LocalTerminalOutput(super.id, this.data);

  final Uint8List data;
}

class LocalTerminalExit extends LocalTerminalEvent {
  const LocalTerminalExit(super.id, this.code);

  final int code;
}

/// A sign-in asked the system to open a page (its stand-in `xdg-open`).
class LocalTerminalOpenUrl extends LocalTerminalEvent {
  const LocalTerminalOpenUrl(super.id, this.url);

  final String url;
}

/// The calls into LocalTerminal.kt. Tests pass a fake.
abstract class LocalTerminalBackend {
  Stream<LocalTerminalEvent> get events;

  /// Starts `bash -l`, or with [signInProfile] an agent's own sign-in
  /// ([signInProgram]: the program and its arguments) for that profile's
  /// agent account.
  Future<LocalShellInfo> start({
    required int rows,
    required int cols,
    String? signInProfile,
    List<String>? signInProgram,
  });

  Future<LocalTerminalListing> list();

  /// The recent output of a shell this Dart side has not seen yet.
  Future<Uint8List?> attach(int id);

  Future<void> write(int id, Uint8List data);

  /// The last output chunk of [id] is drawn: the next may come.
  Future<void> ack(int id);

  Future<void> resize(int id, {required int rows, required int cols});

  /// Stops the shell and everything started from it.
  Future<void> stop(int id);

  /// Forgets the shell, stopping it first when it still runs.
  Future<void> remove(int id);
}

class ChannelLocalTerminalBackend implements LocalTerminalBackend {
  ChannelLocalTerminalBackend({MethodChannel? methods, EventChannel? events})
    : _methods = methods ?? const MethodChannel(methodChannelName),
      _eventChannel = events ?? const EventChannel(eventChannelName);

  static const methodChannelName =
      'io.github.eslamasabry.opencode_mobile/local_terminal';
  static const eventChannelName =
      'io.github.eslamasabry.opencode_mobile/local_terminal/events';

  final MethodChannel _methods;
  final EventChannel _eventChannel;
  Stream<LocalTerminalEvent>? _events;

  @override
  Stream<LocalTerminalEvent> get events => _events ??= _eventChannel
      .receiveBroadcastStream()
      .map(_parseEvent)
      .where((event) => event != null)
      .cast<LocalTerminalEvent>();

  static LocalTerminalEvent? _parseEvent(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['id'];
    if (id is! int) return null;
    return switch (raw['type']) {
      'output' when raw['data'] is Uint8List => LocalTerminalOutput(
        id,
        raw['data'] as Uint8List,
      ),
      'exit' => LocalTerminalExit(id, (raw['code'] as num?)?.toInt() ?? -1),
      'openUrl' when raw['url'] is String => LocalTerminalOpenUrl(
        id,
        raw['url'] as String,
      ),
      _ => null,
    };
  }

  @override
  Future<LocalShellInfo> start({
    required int rows,
    required int cols,
    String? signInProfile,
    List<String>? signInProgram,
  }) async {
    final raw = await _methods.invokeMethod<Map<Object?, Object?>>('start', {
      'rows': rows,
      'cols': cols,
      'signInProfile': ?signInProfile,
      'signInProgram': ?signInProgram,
    });
    return LocalShellInfo.fromMap(raw ?? const {});
  }

  @override
  Future<LocalTerminalListing> list() async {
    final raw = await _methods.invokeMethod<Map<Object?, Object?>>('list');
    final shells = raw?['sessions'];
    final processes = raw?['processes'];
    return LocalTerminalListing(
      shells: [
        if (shells is List)
          for (final shell in shells)
            if (shell is Map) LocalShellInfo.fromMap(shell),
      ],
      processes: processes is num ? processes.toInt() : null,
    );
  }

  @override
  Future<Uint8List?> attach(int id) =>
      _methods.invokeMethod<Uint8List>('attach', {'id': id});

  @override
  Future<void> write(int id, Uint8List data) =>
      _methods.invokeMethod<void>('write', {'id': id, 'data': data});

  @override
  Future<void> ack(int id) => _methods.invokeMethod<void>('ack', {'id': id});

  @override
  Future<void> resize(int id, {required int rows, required int cols}) =>
      _methods.invokeMethod<void>('resize', {
        'id': id,
        'rows': rows,
        'cols': cols,
      });

  @override
  Future<void> stop(int id) => _methods.invokeMethod<void>('stop', {'id': id});

  @override
  Future<void> remove(int id) =>
      _methods.invokeMethod<void>('remove', {'id': id});
}

enum LocalShellState { starting, running, exited, failed }

/// One shell and its screen contents. The [terminal] lives as long as the
/// shell, not the screen that shows it, so leaving the terminal and coming
/// back finds the same scrollback.
class LocalShell extends ChangeNotifier {
  LocalShell._(this._backend, this.number, {int? id, int pid = 0})
    : _id = id,
      _pid = pid {
    terminal = _LocalTerminalScreen(
      maxLines: scrollbackLines,
      onOutput: send,
      onResize: (cols, rows, _, _) => _resized(rows: rows, cols: cols),
    );
  }

  /// Lines kept above the screen.
  static const scrollbackLines = 10000;

  final LocalTerminalBackend _backend;

  /// "Shell 1", "Shell 2": the order they were opened in.
  final int number;

  late final xterm.Terminal terminal;

  int? _id;
  int _pid;
  int? _exitCode;
  String? _failure;
  Timer? _resizeTimer;
  (int, int)? _sentSize;
  late final _decoder = const Utf8Decoder(
    allowMalformed: true,
  ).startChunkedConversion(_TerminalSink(this));

  int? get id => _id;
  int get pid => _pid;
  int? get exitCode => _exitCode;
  String? get failure => _failure;

  LocalShellState get state => _failure != null
      ? LocalShellState.failed
      : _exitCode != null
      ? LocalShellState.exited
      : _id == null
      ? LocalShellState.starting
      : LocalShellState.running;

  bool get running => state == LocalShellState.running;

  /// For an agent sign-in: the page Claude asked the system to open. The
  /// screen showing the sign-in checks the address before opening it.
  void Function(String url)? onOpenUrl;

  /// Applied to everything typed before it is sent: the key bar's sticky
  /// Ctrl and Alt. Null sends text as typed.
  String Function(String text)? inputFilter;

  /// Sends typed text or a key's bytes to the shell.
  void send(String text) {
    final id = _id;
    if (id == null || !running || text.isEmpty) return;
    final filtered = inputFilter?.call(text) ?? text;
    unawaited(
      _backend.write(id, utf8.encode(filtered)).catchError((Object _) {}),
    );
  }

  void _output(Uint8List data) {
    _decoder.add(data);
  }

  void _resized({required int rows, required int cols}) {
    _resizeTimer?.cancel();
    // The keyboard sliding in resizes on every frame; the shell needs the end.
    _resizeTimer = Timer(const Duration(milliseconds: 80), _sendSize);
  }

  void _sendSize() {
    final id = _id;
    if (id == null || !running) return;
    final size = (terminal.viewHeight, terminal.viewWidth);
    if (size == _sentSize) return;
    _sentSize = size;
    unawaited(
      _backend
          .resize(id, rows: size.$1, cols: size.$2)
          .catchError((Object _) {}),
    );
  }

  void _started(LocalShellInfo info, (int, int) size) {
    _id = info.id;
    _pid = info.pid;
    _sentSize = size;
    notifyListeners();
    // The screen may have laid out while the shell started.
    _sendSize();
  }

  void _failed(String message) {
    _failure = message;
    notifyListeners();
  }

  void _exited(int code) {
    if (_exitCode != null) return;
    _exitCode = code;
    notifyListeners();
  }

  @override
  void dispose() {
    _resizeTimer?.cancel();
    super.dispose();
  }
}

/// The xterm screen with two of its 4.0.0 faults fixed, both seen on the
/// emulator (docs/qa/local-terminal-2026-09-24/README.md):
///
/// - a double-width character (CJK, most emoji) that meets the last column
///   went into that column, half past the edge, and the next line began with
///   its empty second half; a terminal wraps the whole character instead,
///   leaving the last column blank;
/// - clearing the scrollback (`clear` sends `ESC [3J`) left every line's
///   index counting the lines removed, so a selection landed that many lines
///   too far down and Copy copied nothing.
class _LocalTerminalScreen extends xterm.Terminal {
  _LocalTerminalScreen({super.maxLines, super.onOutput, super.onResize});

  @override
  void writeChar(int char) {
    final lastColumn = viewWidth - 1;
    final before = buffer.currentLine;
    // The cursor reads as the last column both on it and just past it (a
    // wrap pending); only the first can leave the character half outside.
    final onLast = buffer.cursorX == lastColumn;
    super.writeChar(char);
    if (!onLast || !autoWrapMode) return;
    final after = buffer.currentLine;
    if (identical(after, before) ||
        before.getWidth(lastColumn) != 2 ||
        before.getCodePoint(lastColumn) != char) {
      return;
    }
    before.resetCell(lastColumn);
    after.setCell(0, char, 2, cursor);
    after.setCell(1, 0, 0, cursor);
    buffer.setCursorX(2);
  }

  @override
  void eraseScrollbackOnly() {
    final buffer = this.buffer;
    if (buffer.height <= buffer.viewHeight) return;
    // The library trims the list without renumbering what stays; taking the
    // screen's lines out and putting them back numbers them from 0.
    final screen = [
      for (var i = buffer.scrollBack; i < buffer.height; i++) buffer.lines[i],
    ];
    buffer.lines.clear();
    screen.forEach(buffer.lines.push);
  }
}

class _TerminalSink implements Sink<String> {
  _TerminalSink(this._shell);

  final LocalShell _shell;

  @override
  void add(String data) => _shell.terminal.write(data);

  @override
  void close() {}
}

/// The shells of this app's built-in Ubuntu, for as long as the app runs.
///
/// Android owns the processes (LocalTerminal.kt); this holds what the screen
/// draws. [load] adopts shells a previous run of the Dart side left behind.
class LocalTerminalSessions extends ChangeNotifier {
  LocalTerminalSessions({LocalTerminalBackend? backend})
    : _backend = backend ?? ChannelLocalTerminalBackend();

  /// A shell is proot plus bash: what one costs against Android's limit on
  /// an app's extra processes.
  static const processesPerShell = 2;

  /// Android stops an app's extra processes past this many.
  static const androidProcessLimit = 32;

  /// The terminal runs on Android only: proot ships in the APK.
  static bool get supported => platformCapabilities.isAndroid;

  final LocalTerminalBackend _backend;
  final List<LocalShell> _shells = [];

  /// Agent sign-ins: on a terminal, but not listed with the shells.
  final List<LocalShell> _signIns = [];
  StreamSubscription<LocalTerminalEvent>? _subscription;
  Future<void>? _loading;
  bool _loaded = false;
  int _nextNumber = 1;
  int? _processes;

  List<LocalShell> get shells => List.unmodifiable(_shells);

  /// The shell the screen showed last, so coming back shows it again.
  LocalShell? current;
  bool get loaded => _loaded;

  /// Processes running as this app at the last [load] or [refreshProcesses].
  int? get processes => _processes;

  void _listen() {
    _subscription ??= _backend.events.listen(_event, onError: (Object _) {});
  }

  void _event(LocalTerminalEvent event) {
    final shell = _shellWithId(event.id);
    switch (event) {
      case LocalTerminalOutput(:final data):
        if (shell != null) shell._output(data);
        // Answered after a turn of the event loop, so a frame due now is
        // drawn before the next chunk is parsed; always answered, so an
        // unknown shell does not hold its output back.
        Timer.run(
          () => unawaited(_backend.ack(event.id).catchError((Object _) {})),
        );
      case LocalTerminalExit(:final code):
        shell?._exited(code);
        if (shell != null) notifyListeners();
      case LocalTerminalOpenUrl(:final url):
        shell?.onOpenUrl?.call(url);
    }
  }

  LocalShell? _shellWithId(int id) {
    for (final shell in [..._shells, ..._signIns]) {
      if (shell.id == id) return shell;
    }
    return null;
  }

  /// Reads the shells Android runs, once; shells this side does not know
  /// yet come back with their recent output.
  Future<void> load() => _loading ??= _load();

  Future<void> _load() async {
    _listen();
    try {
      final listing = await _backend.list();
      _processes = listing.processes;
      for (final info in listing.shells) {
        // A sign-in left from an earlier run belongs to no screen now.
        if (info.signIn) {
          unawaited(_backend.remove(info.id).catchError((Object _) {}));
          continue;
        }
        if (_shellWithId(info.id) != null) continue;
        final shell = LocalShell._(
          _backend,
          _nextNumber++,
          id: info.id,
          pid: info.pid,
        );
        final recent = await _backend.attach(info.id);
        if (recent != null && recent.isNotEmpty) shell._output(recent);
        if (!info.running) shell._exited(info.exitCode ?? -1);
        _shells.add(shell);
      }
    } catch (_) {
      // No shells to adopt; starting one reports its own failure.
    }
    _loaded = true;
    notifyListeners();
  }

  Future<void> refreshProcesses() async {
    try {
      _processes = (await _backend.list()).processes;
      notifyListeners();
    } catch (_) {}
  }

  /// Opens a new shell. It is listed at once as starting; its [LocalShell.state]
  /// says when it runs or why it could not start.
  LocalShell startShell({int rows = 24, int cols = 80}) {
    _listen();
    final shell = LocalShell._(_backend, _nextNumber++);
    shell.terminal.resize(cols, rows);
    _shells.add(shell);
    notifyListeners();
    unawaited(_launch(shell));
    return shell;
  }

  /// Starts an agent's own sign-in ([program]: its login command) for
  /// [profileId]'s agent account on a terminal of its own (not listed in
  /// [shells]). [endSignIn] removes it.
  LocalShell startSignIn(
    String profileId,
    List<String> program, {
    int rows = 24,
    int cols = 80,
  }) {
    _listen();
    final shell = LocalShell._(_backend, 0);
    shell.terminal.resize(cols, rows);
    _signIns.add(shell);
    unawaited(_launch(shell, signInProfile: profileId, signInProgram: program));
    return shell;
  }

  /// Stops and forgets a sign-in started by [startSignIn].
  Future<void> endSignIn(LocalShell shell) async {
    _signIns.remove(shell);
    shell.onOpenUrl = null;
    final id = shell.id;
    if (id != null) {
      try {
        await _backend.remove(id);
      } catch (_) {}
    }
    shell.dispose();
  }

  Future<void> _launch(
    LocalShell shell, {
    String? signInProfile,
    List<String>? signInProgram,
  }) async {
    final size = (shell.terminal.viewHeight, shell.terminal.viewWidth);
    try {
      final info = await _backend.start(
        rows: size.$1,
        cols: size.$2,
        signInProfile: signInProfile,
        signInProgram: signInProgram,
      );
      shell._started(info, size);
      unawaited(refreshProcesses());
    } catch (error) {
      shell._failed(
        error is PlatformException ? (error.message ?? error.code) : '$error',
      );
    }
    notifyListeners();
  }

  /// Stops [shell]; it stays listed as ended until [remove].
  Future<void> stop(LocalShell shell) async {
    final id = shell.id;
    if (id == null) return;
    try {
      await _backend.stop(id);
    } catch (_) {}
  }

  /// Stops and forgets [shell].
  Future<void> remove(LocalShell shell) async {
    _shells.remove(shell);
    notifyListeners();
    final id = shell.id;
    if (id != null) {
      try {
        await _backend.remove(id);
      } catch (_) {}
    }
    shell.dispose();
    unawaited(refreshProcesses());
  }

  /// Replaces an ended [shell] with a new one in its place.
  LocalShell restart(LocalShell shell) {
    final rows = shell.terminal.viewHeight;
    final cols = shell.terminal.viewWidth;
    unawaited(remove(shell));
    return startShell(rows: rows, cols: cols);
  }

  @override
  void dispose() {
    unawaited(_subscription?.cancel());
    for (final shell in [..._shells, ..._signIns]) {
      shell.dispose();
    }
    super.dispose();
  }
}
