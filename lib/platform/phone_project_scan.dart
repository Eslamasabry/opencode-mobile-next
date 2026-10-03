import 'dart:async';
import 'dart:collection';
import 'dart:io';

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';

import 'phone_storage_folders.dart';

/// What a project on the phone is mostly made of, from the file that gave
/// it away. [git] is a repository with none of the others.
enum PhoneProjectKind {
  dart,
  node,
  python,
  rust,
  go,
  java,
  ruby,
  php,
  dotnet,
  cpp,
  git,
}

/// A project found on the phone's storage.
class PhoneProject {
  const PhoneProject({
    required this.name,
    required this.path,
    required this.kind,
    required this.hasGit,
  });

  final String name;
  final String path;
  final PhoneProjectKind kind;
  final bool hasGit;
}

/// Why a scan stopped.
enum PhoneScanEnd {
  /// Every folder within the depth limit was looked at.
  completed,

  /// The time cap came first; looking longer may find more.
  timedOut,

  /// Too many folders or results; the list is the first part.
  limited,

  /// Stopped by [PhoneProjectScan.cancel].
  cancelled,
}

/// One running scan: [projects] arrive as they are found; [end] says why it
/// stopped (it completes before the stream closes).
class PhoneProjectScan {
  PhoneProjectScan._(this._controller);

  /// A scan a test drives by hand: [emit] a project, then [finish].
  @visibleForTesting
  PhoneProjectScan.manual() : this._(StreamController<PhoneProject>());

  @visibleForTesting
  void emit(PhoneProject project) => _controller.add(project);

  @visibleForTesting
  void finish(PhoneScanEnd reason) => _finish(reason);

  void _finish(PhoneScanEnd reason) {
    if (!_end.isCompleted) _end.complete(reason);
    unawaited(_controller.close());
  }

  /// Folders looked at so far.
  int get visited => _visited;
  int _visited = 0;

  @visibleForTesting
  set visited(int value) => _visited = value;

  /// True once [cancel] was called.
  bool get cancelled => _cancelled;

  final StreamController<PhoneProject> _controller;
  final Completer<PhoneScanEnd> _end = Completer<PhoneScanEnd>();
  bool _cancelled = false;

  Stream<PhoneProject> get projects => _controller.stream;

  Future<PhoneScanEnd> get end => _end.future;

  /// Stops looking. Safe to call more than once or after the end.
  void cancel() {
    _cancelled = true;
  }
}

/// Looks for projects in the phone's storage (dart:io, so it needs All files
/// access): breadth first, a few levels down, never into a project it found.
class PhoneProjectScanner {
  PhoneProjectScanner({
    String? root,
    this.maxDepth = 5,
    this.maxVisited = 20000,
    this.maxResults = 200,
  }) : root = root ?? PhoneStorageFolders.root;

  final String root;
  final int maxDepth;
  final int maxVisited;
  final int maxResults;

  /// Files (or folders) that make the folder holding them a project, in the
  /// order that decides its kind; `.git` and `*.sln`/`*.csproj` are checked
  /// on their own.
  static const markers = <String, PhoneProjectKind>{
    'pubspec.yaml': PhoneProjectKind.dart,
    'package.json': PhoneProjectKind.node,
    'pyproject.toml': PhoneProjectKind.python,
    'requirements.txt': PhoneProjectKind.python,
    'Cargo.toml': PhoneProjectKind.rust,
    'go.mod': PhoneProjectKind.go,
    'pom.xml': PhoneProjectKind.java,
    'build.gradle': PhoneProjectKind.java,
    'build.gradle.kts': PhoneProjectKind.java,
    'Gemfile': PhoneProjectKind.ruby,
    'composer.json': PhoneProjectKind.php,
    'CMakeLists.txt': PhoneProjectKind.cpp,
    'Makefile': PhoneProjectKind.cpp,
  };

  /// Folders that are never worth going into.
  static const skipped = {
    'Android',
    'node_modules',
    'build',
    '.dart_tool',
    'venv',
    '.venv',
    '__pycache__',
    'target',
    'dist',
  };

  /// The kind of project [names] (the entries of one folder) make it, or
  /// null when it is not a project.
  static ({PhoneProjectKind kind, bool git})? classify(Iterable<String> names) {
    final set = names.toSet();
    final git = set.contains('.git');
    PhoneProjectKind? kind;
    for (final entry in markers.entries) {
      if (set.contains(entry.key)) {
        kind = entry.value;
        break;
      }
    }
    kind ??= set.any((n) => n.endsWith('.sln') || n.endsWith('.csproj'))
        ? PhoneProjectKind.dotnet
        : null;
    kind ??= git ? PhoneProjectKind.git : null;
    return kind == null ? null : (kind: kind, git: git);
  }

  /// What [path] is, read from the folder's own entries: its kind (null
  /// when no marker names one) and whether it holds `.git`. Unreadable folders
  /// read as plain.
  static Future<({PhoneProjectKind? kind, bool git})> inspect(
    String path,
  ) async {
    final names = <String>[];
    try {
      await for (final entity in Directory(path).list(followLinks: false)) {
        names.add(entity.path.substring(entity.path.lastIndexOf('/') + 1));
      }
    } on FileSystemException {
      return (kind: null, git: false);
    }
    final found = classify(names);
    return (kind: found?.kind, git: names.contains('.git'));
  }

  /// Starts looking. [timeLimit] is the whole scan's cap.
  PhoneProjectScan start({Duration timeLimit = const Duration(seconds: 10)}) {
    final controller = StreamController<PhoneProject>();
    final scan = PhoneProjectScan._(controller);
    unawaited(_run(scan, controller, timeLimit));
    return scan;
  }

  Future<void> _run(
    PhoneProjectScan scan,
    StreamController<PhoneProject> controller,
    Duration timeLimit,
  ) async {
    final clock = Stopwatch()..start();
    var end = PhoneScanEnd.completed;
    var visited = 0;
    var found = 0;
    final queue = Queue<(String, int)>()..add((root, 0));
    while (queue.isNotEmpty) {
      if (scan._cancelled) {
        end = PhoneScanEnd.cancelled;
        break;
      }
      if (clock.elapsed >= timeLimit) {
        end = PhoneScanEnd.timedOut;
        break;
      }
      if (visited >= maxVisited) {
        end = PhoneScanEnd.limited;
        break;
      }
      final (path, depth) = queue.removeFirst();
      visited++;
      scan._visited = visited;
      final folders = <String>[];
      final names = <String>[];
      try {
        await for (final entity in Directory(path).list(followLinks: false)) {
          final name = entity.path.substring(entity.path.lastIndexOf('/') + 1);
          names.add(name);
          if (entity is Directory) folders.add(entity.path);
        }
      } on FileSystemException {
        continue; // Unreadable: skipped silently.
      }
      if (path != root) {
        final project = classify(names);
        if (project != null) {
          controller.add(
            PhoneProject(
              name: path.substring(path.lastIndexOf('/') + 1),
              path: path,
              kind: project.kind,
              hasGit: project.git,
            ),
          );
          found++;
          if (found >= maxResults) {
            end = PhoneScanEnd.limited;
            break;
          }
          continue; // Never into a project.
        }
      }
      if (depth >= maxDepth) continue;
      folders.sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
      for (final folder in folders) {
        final name = folder.substring(folder.lastIndexOf('/') + 1);
        if (name.startsWith('.') || skipped.contains(name)) continue;
        queue.add((folder, depth + 1));
      }
    }
    scan._finish(end);
  }
}

enum PhoneScanPhase { idle, scanning, done }

/// The state of "Find projects" for the life of one sheet: the results so
/// far, whether it is still looking, and why it stopped. Nothing is stored.
class PhoneProjectScanState extends ChangeNotifier {
  PhoneProjectScanState(this._start);

  final PhoneProjectScan Function(Duration timeLimit) _start;

  final List<PhoneProject> projects = [];
  PhoneScanEnd? end;
  Duration limit = const Duration(seconds: 10);
  PhoneScanPhase phase = PhoneScanPhase.idle;

  PhoneProjectScan? _scan;
  bool _disposed = false;
  Timer? _tick;
  DateTime? _startedAt;
  Duration _elapsed = Duration.zero;
  int _checked = 0;

  /// How long the look has run (frozen when it stops).
  Duration get elapsed => scanning && _startedAt != null
      ? clock.now().difference(_startedAt!)
      : _elapsed;

  /// Folders looked at so far.
  int get checked => scanning ? (_scan?.visited ?? 0) : _checked;

  void _freeze() {
    _tick?.cancel();
    _tick = null;
    _elapsed = _startedAt == null
        ? Duration.zero
        : clock.now().difference(_startedAt!);
    _checked = _scan?.visited ?? _checked;
  }

  bool get scanning => phase == PhoneScanPhase.scanning;

  /// True when opening "Find projects" again can show what is there.
  bool get hasResult =>
      phase == PhoneScanPhase.done && end != PhoneScanEnd.cancelled;

  /// Looks again from nothing, with [timeLimit] as the cap.
  void run({Duration timeLimit = const Duration(seconds: 10)}) {
    _scan?.cancel();
    projects.clear();
    end = null;
    limit = timeLimit;
    phase = PhoneScanPhase.scanning;
    final scan = _scan = _start(timeLimit);
    _startedAt = clock.now();
    _tick?.cancel();
    // The timer and the counts move a few times a second, not per folder.
    _tick = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (!_disposed && scanning) notifyListeners();
    });
    notifyListeners();
    scan.projects.listen((project) {
      if (_disposed || _scan != scan) return;
      projects.add(project);
      notifyListeners();
    });
    unawaited(
      scan.end.then((reason) {
        if (_disposed || _scan != scan) return;
        _freeze();
        phase = PhoneScanPhase.done;
        end = reason;
        notifyListeners();
      }),
    );
  }

  /// Back: stops the scan; what was found stays for this sheet.
  void cancel() {
    final scan = _scan;
    if (scan == null || !scanning) return;
    scan.cancel();
    _freeze();
    _scan = null;
    phase = PhoneScanPhase.done;
    end = PhoneScanEnd.cancelled;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _tick?.cancel();
    _scan?.cancel();
    super.dispose();
  }
}
