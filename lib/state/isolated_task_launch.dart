import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/models.dart';
import '../domain/server_gateway.dart';

/// Where a fresh-worktree task launch currently stands.
enum IsolatedTaskPhase {
  /// Nothing requested yet.
  idle,

  /// The create request is in flight. Its outcome is unknown until the
  /// server answers, so a cancel here cannot promise that nothing exists.
  creating,

  /// The server returned the worktree directory; its setup has not reported.
  preparing,

  /// `worktree.ready` arrived for this directory.
  ready,

  /// No readiness event within the wait; the worktree exists but its setup
  /// state is unconfirmed. The user may keep waiting or open it anyway.
  unconfirmed,

  /// `worktree.failed` arrived, or the create call itself failed.
  failed,

  /// Switching scope and creating the blank session.
  opening,

  /// The session exists in the worktree's scope.
  opened,

  /// The user stopped waiting. Anything the server created stays listed.
  cancelled,
}

/// One explicit "start a task in a fresh worktree" run.
///
/// Subscribes to [events] *before* the create call so a `worktree.ready` that
/// lands before the create response is retained and matched once the
/// directory is known. Correlation is by project and normalized directory.
/// Cancelling and timing out never delete anything: a worktree the server
/// created stays in its inventory for the manual Worktrees screen.
class IsolatedTaskLaunch extends ChangeNotifier {
  IsolatedTaskLaunch({
    required this.project,
    required Stream<EventEnvelope> events,
    required Future<WorktreeInfo> Function() create,
    required Future<Session> Function(String directory) open,
    this.requestedName,
    this.readinessTimeout = const Duration(seconds: 45),
  }) : _events = events,
       _create = create,
       _open = open;

  final WorkspaceProject project;
  final String? requestedName;
  final Duration readinessTimeout;

  final Stream<EventEnvelope> _events;
  final Future<WorktreeInfo> Function() _create;
  final Future<Session> Function(String directory) _open;

  IsolatedTaskPhase _phase = IsolatedTaskPhase.idle;
  WorktreeInfo? _worktree;
  Session? _session;
  String? _message;
  Object? _failure;
  Object? _openError;
  StreamSubscription<EventEnvelope>? _subscription;
  Timer? _timer;
  bool _disposed = false;

  /// Readiness events seen before the directory was known, newest per
  /// directory. Only project-matching entries are kept.
  final Map<String, EventEnvelope> _early = {};

  IsolatedTaskPhase get phase => _phase;

  /// The created worktree once the server has answered, whatever happened
  /// afterwards. Null while creating, or when the create call failed before
  /// returning a directory.
  WorktreeInfo? get worktree => _worktree;
  Session? get session => _session;

  /// Server-provided `worktree.failed` text, when any.
  String? get message => _message;

  /// The error thrown by the create call, when it failed before answering.
  Object? get failure => _failure;

  /// The last error from an open attempt; the launch stays openable.
  Object? get openError => _openError;

  bool get canCancel =>
      _phase == IsolatedTaskPhase.creating ||
      _phase == IsolatedTaskPhase.preparing ||
      _phase == IsolatedTaskPhase.unconfirmed;

  bool get canOpen =>
      _phase == IsolatedTaskPhase.ready ||
      _phase == IsolatedTaskPhase.unconfirmed;

  bool get isTerminal =>
      _phase == IsolatedTaskPhase.opened ||
      _phase == IsolatedTaskPhase.failed ||
      _phase == IsolatedTaskPhase.cancelled;

  /// Creates the worktree and waits for its readiness event. Safe to call
  /// once; later calls are ignored.
  Future<void> start() async {
    if (_phase != IsolatedTaskPhase.idle || _disposed) return;
    _set(IsolatedTaskPhase.creating);
    _subscription = _events.listen(_onEvent);
    WorktreeInfo created;
    try {
      created = await _create();
    } catch (error) {
      if (_disposed) return;
      if (_phase == IsolatedTaskPhase.cancelled) return;
      _failure = error;
      _finish(IsolatedTaskPhase.failed);
      return;
    }
    if (_disposed) return;
    _worktree = created;
    if (_phase == IsolatedTaskPhase.cancelled) {
      // The user stopped waiting before the answer came back. Record what
      // the server made so the UI can name it; never remove it.
      notifyListeners();
      return;
    }
    final early = _early.remove(_key(created.directory));
    if (early != null) {
      _apply(early);
      return;
    }
    _set(IsolatedTaskPhase.preparing);
    _armTimer();
  }

  /// Stops waiting. Does not undo a create that may already have run.
  void cancel() {
    if (!canCancel) return;
    _timer?.cancel();
    _timer = null;
    _finish(IsolatedTaskPhase.cancelled);
  }

  /// From [IsolatedTaskPhase.unconfirmed], waits another [readinessTimeout].
  void keepWaiting() {
    if (_phase != IsolatedTaskPhase.unconfirmed) return;
    _set(IsolatedTaskPhase.preparing);
    _armTimer();
  }

  /// Opens a blank session inside the worktree. Allowed when ready, or when
  /// unconfirmed only if the caller passes [acceptUnconfirmed] to record the
  /// user's explicit choice. A failed open keeps the launch openable.
  Future<void> open({bool acceptUnconfirmed = false}) async {
    final directory = _worktree?.directory;
    if (directory == null || !canOpen) return;
    if (_phase == IsolatedTaskPhase.unconfirmed && !acceptUnconfirmed) return;
    final previous = _phase;
    _openError = null;
    _timer?.cancel();
    _timer = null;
    _set(IsolatedTaskPhase.opening);
    try {
      final session = await _open(directory);
      if (_disposed) return;
      _session = session;
      _finish(IsolatedTaskPhase.opened);
    } catch (error) {
      if (_disposed) return;
      _openError = error;
      _set(previous);
    }
  }

  void _onEvent(EventEnvelope event) {
    if (event.type != 'worktree.ready' && event.type != 'worktree.failed') {
      return;
    }
    final directory = event.directory?.trim() ?? '';
    if (directory.isEmpty) return;
    final eventProject = event.project?.trim() ?? '';
    if (eventProject.isNotEmpty && eventProject != project.id) return;
    final known = _worktree?.directory;
    if (known == null) {
      _early[_key(directory)] = event;
      return;
    }
    if (_key(directory) != _key(known)) return;
    if (_phase != IsolatedTaskPhase.preparing &&
        _phase != IsolatedTaskPhase.unconfirmed) {
      return;
    }
    _apply(event);
  }

  void _apply(EventEnvelope event) {
    _timer?.cancel();
    _timer = null;
    if (event.type == 'worktree.ready') {
      final current = _worktree!;
      final name = event.properties['name']?.toString().trim();
      final branch = event.properties['branch']?.toString().trim();
      _worktree = WorktreeInfo(
        name: name?.isNotEmpty == true ? name! : current.name,
        directory: current.directory,
        branch: branch?.isNotEmpty == true ? branch : current.branch,
      );
      _set(IsolatedTaskPhase.ready);
      return;
    }
    final message = event.properties['message']?.toString().trim();
    _message = message?.isNotEmpty == true ? message : null;
    _finish(IsolatedTaskPhase.failed);
  }

  void _armTimer() {
    _timer?.cancel();
    _timer = Timer(readinessTimeout, () {
      if (_phase != IsolatedTaskPhase.preparing) return;
      _set(IsolatedTaskPhase.unconfirmed);
    });
  }

  void _finish(IsolatedTaskPhase phase) {
    _subscription?.cancel();
    _subscription = null;
    _set(phase);
  }

  void _set(IsolatedTaskPhase phase) {
    _phase = phase;
    if (!_disposed) notifyListeners();
  }

  static String _key(String directory) {
    var value = directory.trim().replaceAll('\\', '/');
    while (value.length > 1 && value.endsWith('/')) {
      value = value.substring(0, value.length - 1);
    }
    return value;
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _subscription?.cancel();
    super.dispose();
  }
}
