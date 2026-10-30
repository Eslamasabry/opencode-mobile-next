import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/widgets.dart';

import '../../domain/models.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/shell_output.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/product_states.dart' show productErrorDetails;
import '../widgets/running_agents_strip.dart';

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

// A transport can be replaced while reconnecting to the same location. Pin
// actual scope, not its refresh counter, so that reconnection stays usable.
typedef _WorkScope = (String?, String?, String?, String?);

_WorkScope _scope(ConnectionController conn) =>
    (conn.profile?.id, conn.profile?.baseUrl, conn.directory, conn.workspace);

/// How a command ended, as the reader cares about it. A shell reports only a
/// status and an exit code; 143 and 130 are "someone stopped it" (SIGTERM,
/// Ctrl-C), not a failure of the command.
enum _Outcome { running, done, failed, timedOut, stopped, idle, unknown }

_Outcome _outcome(ManagedShell shell) => switch (shell.status) {
  ManagedShellStatus.running => _Outcome.running,
  ManagedShellStatus.killed => _Outcome.stopped,
  ManagedShellStatus.timeout => _Outcome.timedOut,
  ManagedShellStatus.unknown => _Outcome.unknown,
  ManagedShellStatus.exited => switch (shell.exitCode) {
    null || 0 => _Outcome.done,
    143 || 130 || 137 => _Outcome.stopped,
    _ => _Outcome.failed,
  },
};

/// The one word for how a piece of work stands (STATE-9: a mark always
/// travels with its word).
String _outcomeWord(AppLocalizations l10n, _Outcome outcome) =>
    switch (outcome) {
      _Outcome.running => l10n.workRunning,
      _Outcome.done => l10n.workFinished,
      _Outcome.failed => l10n.runningWorkFailed,
      _Outcome.timedOut => l10n.workTimedOut,
      _Outcome.stopped => l10n.workStopped,
      _Outcome.idle => l10n.workIdle,
      _Outcome.unknown => l10n.workUnknown,
    };

/// The row mark for an outcome, always with its word. Idle and unknown are
/// "not running and not a result": the hollow ring.
KitTaskState _mark(_Outcome outcome) => switch (outcome) {
  _Outcome.running => KitTaskState.working,
  _Outcome.done => KitTaskState.done,
  _Outcome.failed || _Outcome.timedOut => KitTaskState.failed,
  _Outcome.stopped => KitTaskState.stopped,
  _Outcome.idle || _Outcome.unknown => KitTaskState.waiting,
};

/// A command as a person would say it: the programs it runs, not where they
/// live or what environment they were given. `FOO=1 /tmp/x/bin/flutter test
/// && ./build.sh` reads "flutter test && build.sh". The full text is on the
/// command's own screen.
String shortCommand(String command) {
  final parts = command.trim().split(RegExp(r'\s+(&&|\|\||;|\|)\s+'));
  final joins = RegExp(
    r'\s+(&&|\|\||;|\|)\s+',
  ).allMatches(command.trim()).map((m) => m[1]!).toList();
  final out = StringBuffer();
  for (var i = 0; i < parts.length; i++) {
    final words = parts[i].split(RegExp(r'\s+'))
      ..removeWhere((word) => word.isEmpty);
    while (words.length > 1 &&
        RegExp(r'^[A-Za-z_][A-Za-z0-9_]*=').hasMatch(words.first)) {
      words.removeAt(0);
    }
    if (words.isNotEmpty && words.first.contains('/')) {
      final name = words.first.split('/').last;
      if (name.isNotEmpty) words[0] = name;
    }
    if (i > 0) out.write(' ${joins[i - 1]} ');
    out.write(words.join(' '));
  }
  final result = out.toString().trim();
  return result.isEmpty ? command : result;
}

String _clockText(int seconds) {
  final s = seconds.clamp(0, 365 * 86400);
  return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
}

String _elapsed(ManagedShell shell) => _clockText(
  (shell.completedAt ?? clock.now()).difference(shell.startedAt).inSeconds,
);

/// "Work in this conversation": the agents and commands this conversation
/// started, in one list ordered by urgency (failed first, then running, then
/// the rest newest first). Each row carries its own mark and word; there are
/// no state sections.
Future<String?> showRunningWorkSheet(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
  required Set<String> shellIDs,
  Future<BackgroundWorkResult?> Function()? onBackground,
  BackgroundWorkSupport backgroundSupport = BackgroundWorkSupport.unavailable,
  BackgroundWorkSupport Function()? readBackgroundSupport,
  bool Function()? canBackground,
  Listenable? availabilityChanges,
}) => showKitSheet<String>(
  context,
  title: _strings(context).runningWorkTitle,
  icon: AppIconography.playCircle,
  body: (_) => RunningWorkSheet(
    controller: controller,
    sessionID: sessionID,
    shellIDs: shellIDs,
    onBackground: onBackground,
    backgroundSupport: backgroundSupport,
    readBackgroundSupport: readBackgroundSupport,
    canBackground: canBackground,
    availabilityChanges: availabilityChanges,
  ),
);

/// The body of the "Work in this conversation" sheet. It is not its own scroll view: the
/// sheet frame scrolls it (KIT-17).
class RunningWorkSheet extends StatefulWidget {
  const RunningWorkSheet({
    super.key,
    required this.controller,
    required this.sessionID,
    this.shellIDs = const {},
    this.onBackground,
    this.backgroundSupport = BackgroundWorkSupport.unavailable,
    this.readBackgroundSupport,
    this.canBackground,
    this.availabilityChanges,
  });
  final ConnectionController controller;
  final String sessionID;
  final Set<String> shellIDs;
  final Future<BackgroundWorkResult?> Function()? onBackground;
  final BackgroundWorkSupport backgroundSupport;
  final BackgroundWorkSupport Function()? readBackgroundSupport;
  final bool Function()? canBackground;
  final Listenable? availabilityChanges;
  @override
  State<RunningWorkSheet> createState() => _RunningWorkSheetState();
}

class _RunningWorkSheetState extends State<RunningWorkSheet>
    with WidgetsBindingObserver {
  late final _WorkScope _pinnedScope;
  bool _active = true;
  bool _loading = true;
  bool _refreshing = false;
  bool? _shellSupport;
  Object? _error;
  List<ManagedShell> _shells = [];
  final Map<String, Session> _related = {};
  Object? _agentsError;
  bool _promoting = false;
  BackgroundWorkResult? _promotion;
  Timer? _timer;
  StreamSubscription<EventEnvelope>? _events;
  late int _revision;
  bool get _scopeMatches => _pinnedScope == _scope(widget.controller);
  bool get _visible =>
      mounted && _active && (ModalRoute.of(context)?.isCurrent ?? true);

  @override
  void initState() {
    super.initState();
    _pinnedScope = _scope(widget.controller);
    _revision = widget.controller.dataRefreshRevision;
    _active =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    widget.availabilityChanges?.addListener(_changed);
    _events = widget.controller.events.listen((event) {
      if ((event.type.startsWith('shell.') ||
              event.type == 'session.created' ||
              event.type == 'session.updated' ||
              event.type == 'session.deleted') &&
          _visible) {
        unawaited(_refresh());
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_visible && _shellSupport != false) unawaited(_refresh());
    });
  }

  void _changed() {
    if (!mounted) return;
    setState(() {});
    final next = widget.controller.dataRefreshRevision;
    if (next != _revision) {
      _revision = next;
      _shellSupport = null;
      if (_visible) unawaited(_refresh());
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _active = state == AppLifecycleState.resumed;
    if (_active && _visible) unawaited(_refresh());
  }

  Future<void> _refresh() async {
    if (!mounted || _refreshing || !_scopeMatches || !_active) return;
    final conn = widget.controller;
    final repo = conn.repository;
    if (repo == null || conn.status != StreamStatus.connected) {
      setState(() => _loading = false);
      return;
    }
    _refreshing = true;
    try {
      // Session lists may omit child sessions. Resolve the family through the
      // existing children endpoint without mutating the global session cache.
      if (conn.capabilities.projectManagement) {
        try {
          final current =
              conn.sessionsById[widget.sessionID] ??
              await repo.getSessionDetails(widget.sessionID);
          final children = <Session>[];
          // Fetch the current session's own children as well as siblings when
          // viewing a child; both may be absent from the global latest page.
          for (final owner in {current.id, ?current.parentID}) {
            children.addAll(await repo.listSessionChildren(owner));
            if (!mounted || !_scopeMatches || repo != conn.repository) return;
          }
          if (!mounted || !_scopeMatches || repo != conn.repository) return;
          _related
            ..clear()
            ..addAll({
              current.id: current,
              for (final child in children) child.id: child,
            });
          _agentsError = null;
        } catch (error) {
          if (!mounted || !_scopeMatches || repo != conn.repository) return;
          _agentsError = error;
        }
      }
      final result = await repo.loadRunningShells();
      final knownIDs = {
        ...widget.shellIDs,
        for (final shell in _shells) shell.id,
      };
      final retained = <ManagedShell>[];
      if (result.supported) {
        for (final id in knownIDs.difference(
          result.shells.map((shell) => shell.id).toSet(),
        )) {
          final shell = await repo.getManagedShell(id);
          if (shell != null) retained.add(shell);
        }
      }
      if (!mounted || !_scopeMatches || repo != conn.repository) return;
      final related = {
        widget.sessionID,
        for (final session in {...conn.sessionsById, ..._related}.values)
          if (session.parentID == widget.sessionID) session.id,
      };
      setState(() {
        _shellSupport = result.supported;
        _shells = result.supported
            ? [...result.shells, ...retained]
                  .where(
                    (shell) =>
                        related.contains(shell.sessionID) ||
                        widget.shellIDs.contains(shell.id),
                  )
                  .toList()
            : [];
        _error = null;
      });
    } catch (error) {
      if (mounted && _scopeMatches && repo == conn.repository) {
        setState(() => _error = error);
      }
    } finally {
      _refreshing = false;
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    unawaited(_events?.cancel());
    widget.controller.removeListener(_changed);
    widget.availabilityChanges?.removeListener(_changed);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _promote() async {
    setState(() => _promoting = true);
    try {
      final result = await widget.onBackground!();
      if (mounted && _scopeMatches) setState(() => _promotion = result);
      if (mounted && _scopeMatches) await _refresh();
    } finally {
      if (mounted) setState(() => _promoting = false);
    }
  }

  /// Stops one running agent, asked first: it ends running work (DATA-11).
  /// The stop runs inside the question, so a failure keeps it open with Try
  /// again instead of closing on an error.
  Future<void> _stopAgent(RunningAgentEntry entry) async {
    final l10n = _strings(context);
    final title = entry.labelFor(l10n);
    final conn = widget.controller;
    final stopped = await showKitConfirm(
      context,
      title: l10n.runningWorkStopAgentTitle(title),
      body: l10n.runningWorkStopAgentBody,
      confirmLabel: l10n.runningWorkStopAgentConfirm,
      kind: KitConfirmKind.stop,
      confirmKey: const Key('running-work-stop-agent-confirm'),
      action: () async {
        final api = await conn.prepareActionTransport();
        if (api == null || !_scopeMatches) {
          throw ProductException(l10n.workDisconnected);
        }
        conn.noteStopFromThisApp(entry.session.id);
        try {
          await api.abort(entry.session.id);
        } catch (_) {
          conn.noteStopFromThisApp(entry.session.id, asked: false);
          rethrow;
        }
      },
    );
    if (stopped && mounted && _visible) await _refresh();
  }

  Widget _agentRow(RunningAgentEntry entry, _Outcome outcome, bool offline) {
    final l10n = _strings(context);
    final title = entry.labelFor(l10n);
    final word = _outcomeWord(l10n, outcome);
    final canStop = outcome == _Outcome.running && !offline;
    return KitRow(
      key: ValueKey('work-agent-${entry.session.id}'),
      leading: KitTaskMark(state: _mark(outcome), label: word),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(text: l10n.runningWorkAgentState(word)),
      trailing: const KitChevron(),
      onTap: () => KitSheet.close(context, entry.session.id),
      menu: [
        KitMenuItem(
          label: l10n.workOpenLiveConversation(title),
          icon: AppIconography.chat,
          onSelected: () => KitSheet.close(context, entry.session.id),
        ),
        if (canStop)
          KitMenuItem(
            key: ValueKey('work-agent-stop-${entry.session.id}'),
            label: l10n.runningWorkStopAgent(title),
            icon: AppIconography.stopCircle,
            destructive: true,
            onSelected: () => unawaited(_stopAgent(entry)),
          ),
      ],
    );
  }

  Widget _shellRow(ManagedShell shell, bool offline) {
    final l10n = _strings(context);
    final conn = widget.controller;
    final outcome = _outcome(shell);
    final word = _outcomeWord(l10n, outcome);
    return KitRow(
      key: ValueKey('work-shell-${shell.id}'),
      leading: KitTaskMark(state: _mark(outcome), label: word),
      title: shortCommand(shell.command),
      // Live work gets room to be recognised; finished work is a line.
      titleMaxLines: outcome == _Outcome.running ? 2 : 1,
      supporting: TextSpan(
        text: l10n.runningWorkCommandState(
          l10n.workStatusElapsed(word, _elapsed(shell)),
        ),
      ),
      trailing: const KitChevron(),
      enabled: !offline,
      disabledReason: offline ? l10n.runningWorkOffline : null,
      onTap: offline
          ? null
          : () async {
              if (!_scopeMatches || conn.status != StreamStatus.connected) {
                return;
              }
              await pushKitPage<void>(
                context,
                (_) => ShellOutputScreen(controller: conn, shell: shell),
              );
              if (mounted && _visible) await _refresh();
            },
    );
  }

  static DateTime _shellTime(ManagedShell shell) =>
      shell.completedAt ?? shell.startedAt;

  static DateTime _agentTime(RunningAgentEntry entry) =>
      DateTime.fromMillisecondsSinceEpoch(
        entry.session.time?.updated ?? entry.session.time?.created ?? 0,
      );

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final tokens = KitTokens.of(context);
    final conn = widget.controller;
    final agents = runningAgentEntries(
      sessionID: widget.sessionID,
      sessions: {...conn.sessionsById, ..._related},
      busy: conn.busySessions,
      includeIdle: true,
    ).where((entry) => !entry.current).toList();
    final offline = conn.status != StreamStatus.connected;
    final canBackground =
        widget.canBackground?.call() ??
        (widget.onBackground != null && _promotion == null);
    final gap = SizedBox(height: tokens.space3);

    // One list ordered by urgency (owner rule 2026-09-27): first what needs
    // the person (failed or timed-out commands), then what works now (busy
    // agents, then running commands), then everything else merged by
    // recency, newest first. The mark and word on each row say how it
    // stands; there are no state sections.
    bool needsYou(ManagedShell shell) => switch (_outcome(shell)) {
      _Outcome.failed || _Outcome.timedOut => true,
      _ => false,
    };
    final needs = [
      for (final shell in _shells)
        if (needsYou(shell)) shell,
    ]..sort((a, b) => _shellTime(b).compareTo(_shellTime(a)));
    final working = <Widget>[
      for (final entry in agents)
        if (entry.busy && !offline) _agentRow(entry, _Outcome.running, false),
      for (final shell in _shells)
        if (_outcome(shell) == _Outcome.running) _shellRow(shell, offline),
    ];
    final rest = <(DateTime, Widget Function())>[
      for (final shell in _shells)
        if (_outcome(shell) != _Outcome.running && !needsYou(shell))
          (_shellTime(shell), () => _shellRow(shell, offline)),
      for (final entry in agents)
        if (!entry.busy || offline)
          (
            _agentTime(entry),
            () => _agentRow(
              entry,
              offline ? _Outcome.unknown : _Outcome.idle,
              offline,
            ),
          ),
    ]..sort((a, b) => b.$1.compareTo(a.$1));
    final rows = [
      for (final shell in needs) _shellRow(shell, offline),
      ...working,
      for (final (_, row) in rest) row(),
    ];
    final blocked =
        widget.onBackground != null &&
        (canBackground || _promoting || _promotion != null);

    final children = <Widget>[];
    void add(Widget child) {
      if (children.isNotEmpty) children.add(gap);
      children.add(child);
    }

    if (!_scopeMatches) {
      add(
        KitStateView(
          icon: AppIconography.info,
          title: l10n.runningWorkScopeChangedTitle,
          body: l10n.workContextChanged,
          size: KitStateSize.inline,
        ),
      );
    } else {
      if (offline) {
        add(
          KitNotice(message: l10n.workDisconnected, icon: AppIconography.sync),
        );
      }
      if (_agentsError case final error?) {
        add(
          KitNotice.error(
            message: l10n.runningWorkAgentsFailed,
            error: error,
            details: productErrorDetails(error),
            retry: KitAction(label: l10n.workRetry, onPressed: _refresh),
          ),
        );
      }
      if (_error case final error?) {
        add(
          KitNotice.error(
            message: l10n.runningWorkCommandsFailed,
            error: error,
            details: productErrorDetails(error),
            retry: KitAction(label: l10n.workRetry, onPressed: _refresh),
          ),
        );
      }
      if (_loading) {
        add(const KitSkeletonRows(count: 3));
      } else if (rows.isEmpty && _error == null && _agentsError == null) {
        add(
          KitStateView(
            icon: AppIconography.checkCircle,
            title: l10n.runningWorkEmptyTitle,
            body: l10n.runningWorkEmptyBody,
            size: KitStateSize.inline,
          ),
        );
      } else if (rows.isNotEmpty) {
        add(KitRowGroup(margin: EdgeInsets.zero, children: rows));
      }
      // Only while the work above holds the conversation, right under it:
      // what moving it frees, then the act (map: running-work-sheet).
      if (blocked) {
        add(
          _promotion == null
              ? KitNotice(
                  key: const Key('running-work-background'),
                  icon: AppIconography.lowPriority,
                  message: l10n.runningWorkBackgroundBody,
                  actions: [
                    KitAction(
                      key: const Key('running-work-background-action'),
                      label: _promoting
                          ? l10n.workBackgroundPending
                          : l10n.runningWorkBackgroundAction,
                      working: _promoting,
                      onPressed:
                          offline ||
                              !_scopeMatches ||
                              _promoting ||
                              !canBackground
                          ? null
                          : _promote,
                      disabledReason: offline ? l10n.runningWorkOffline : null,
                    ),
                  ],
                )
              : KitNotice(
                  key: const Key('running-work-background'),
                  tone: AppStatusTone.ok,
                  message: switch (_promotion!) {
                    BackgroundWorkResult.promoted =>
                      l10n.backgroundWorkPromoted,
                    BackgroundWorkResult.unchanged => l10n.backgroundWorkNoop,
                    BackgroundWorkResult.requested =>
                      l10n.workBackgroundRequested,
                  },
                ),
        );
      }
    }
    return Column(
      key: const Key('running-work-sheet'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: children,
    );
  }
}

/// A command's own page (map: shell-output): the command as a person would
/// say it, how it stands, its two acts kept apart, and its output filling
/// the page. The command as typed, its folder and its exit code sit in the
/// one Details fold at the end.
class ShellOutputScreen extends StatefulWidget {
  const ShellOutputScreen({
    super.key,
    required this.controller,
    required this.shell,
  });
  final ConnectionController controller;
  final ManagedShell shell;
  @override
  State<ShellOutputScreen> createState() => _ShellOutputScreenState();
}

class _ShellOutputScreenState extends State<ShellOutputScreen>
    with WidgetsBindingObserver {
  late final ShellOutputController _output = ShellOutputController(
    gateway: widget.controller.repository!,
    shell: widget.shell,
  );
  final _lines = KitLogBuffer();
  String _shownText = '';
  late final _WorkScope _pinnedScope;
  late int _revision;
  StreamSubscription<EventEnvelope>? _events;
  Timer? _timer;
  bool _active = true;
  bool _mutating = false;
  bool _stopped = false;
  bool _reconcilePending = true;

  /// The time limit set from this page: the seconds chosen (0 = none) and
  /// when the server's clock for it started ("replace timeout from now").
  /// The server does not report a limit it was started with, so nothing is
  /// shown until one is set here.
  int? _limitSeconds;
  DateTime? _limitFrom;

  /// A failed change of the time limit, said on the page with Try again.
  int? _failedLimit;
  Object? _limitError;

  bool get _sameScope => _pinnedScope == _scope(widget.controller);
  bool get _visible =>
      mounted && _active && (ModalRoute.of(context)?.isCurrent ?? true);
  bool get _connected => widget.controller.status == StreamStatus.connected;
  bool get _canMutate =>
      _sameScope &&
      widget.controller.repository != null &&
      _connected &&
      !_mutating &&
      !_stopped &&
      _output.available &&
      _output.shell.running;

  @override
  void initState() {
    super.initState();
    _pinnedScope = _scope(widget.controller);
    _revision = widget.controller.dataRefreshRevision;
    _active =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_connectionChanged);
    _output.addListener(_outputChanged);
    _events = widget.controller.events.listen((event) {
      final info = event.properties['info'];
      final id = event.properties['id'] ?? (info is Map ? info['id'] : null);
      if (event.type.startsWith('shell.') &&
          id == widget.shell.id &&
          _visible) {
        unawaited(_refresh());
      }
    });
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _refresh(reconcile: true),
    );
    // Polls only while this page is on top and the command still writes;
    // the output panel follows the newest line until the person scrolls up.
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!_visible || !_output.available) return;
      if (_output.shell.running || _output.hasMore) unawaited(_refresh());
      // The elapsed time and the time left move on between reads.
      if (_output.shell.running && !_stopped) setState(() {});
    });
  }

  Future<void> _refresh({bool reconcile = false}) async {
    if (reconcile) _reconcilePending = true;
    if (!_sameScope || !_connected || !_visible || _stopped) return;
    final repo = widget.controller.repository;
    if (repo == null) return;
    final changed = repo != _output.gateway;
    _output.bind(repo);
    if (_output.refreshing) return;
    final needsIdentity = _reconcilePending || changed;
    _reconcilePending = false;
    await _output.refresh(reconcileServer: needsIdentity);
    if (mounted && needsIdentity && _output.error != null) {
      _reconcilePending = true;
    }
  }

  void _connectionChanged() {
    if (!mounted) return;
    setState(() {});
    final revision = widget.controller.dataRefreshRevision;
    if (_revision != revision ||
        widget.controller.repository != _output.gateway) {
      _revision = revision;
      unawaited(_refresh(reconcile: true));
    }
  }

  void _outputChanged() {
    if (!mounted) return;
    final text = _output.displayText;
    if (text != _shownText) {
      _shownText = text;
      _lines.replaceText(text);
    }
    setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _active = state == AppLifecycleState.resumed;
    if (_active) unawaited(_refresh(reconcile: true));
  }

  /// Stop is asked first (it ends running work and removes the saved output,
  /// DATA-11). The stop runs inside the question: a failure keeps it open
  /// with the reason and Try again. "Copy output first" is the safer path.
  Future<void> _stop() async {
    if (!_canMutate) return;
    final l10n = _strings(context);
    final output = _output.displayText;
    final repo = widget.controller.repository!;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.workStopTitle,
      body: l10n.workStopDescription,
      confirmLabel: l10n.workStop,
      kind: KitConfirmKind.stop,
      confirmKey: const Key('shell-output-stop-confirm'),
      alternative: output.isEmpty
          ? null
          : KitAction(
              key: const Key('shell-output-copy-first'),
              label: l10n.shellOutputCopyFirst,
              icon: AppIconography.copy,
              onPressed: () => unawaited(KitCopy.copy(context, output)),
            ),
      action: () async {
        if (!_sameScope) throw ProductException(l10n.workContextChanged);
        await repo.stopManagedShell(widget.shell.id);
      },
    );
    if (!mounted || !confirmed) return;
    if (_sameScope) setState(() => _stopped = true);
    await _refresh();
  }

  Future<void> _chooseLimit() async {
    if (!_canMutate) return;
    final l10n = _strings(context);
    final seconds = await showKitChoiceSheet<int>(
      context,
      title: l10n.shellOutputLimitTitle,
      subtitle: l10n.workTimeoutDescription,
      sheetKey: const Key('shell-output-limit-sheet'),
      selected: _limitSeconds,
      choices: [
        for (final option in <int, String>{
          60: l10n.workTimeoutOneMinute,
          300: l10n.workTimeoutFiveMinutes,
          900: l10n.workTimeoutFifteenMinutes,
          3600: l10n.workTimeoutOneHour,
          0: l10n.workTimeoutNone,
        }.entries)
          KitChoice(value: option.key, title: option.value),
      ],
    );
    if (!mounted || seconds == null) return;
    await _applyLimit(seconds);
  }

  Future<void> _applyLimit(int seconds) async {
    if (!_canMutate) return;
    setState(() {
      _mutating = true;
      _limitError = null;
      _failedLimit = null;
    });
    try {
      await widget.controller.repository!.setManagedShellTimeout(
        widget.shell.id,
        seconds == 0 ? null : Duration(seconds: seconds),
      );
      if (mounted && _sameScope) {
        setState(() {
          _limitSeconds = seconds;
          _limitFrom = clock.now();
        });
        await _refresh();
      }
    } catch (error) {
      if (mounted && _sameScope) {
        setState(() {
          _limitError = error;
          _failedLimit = seconds;
        });
      }
    } finally {
      if (mounted) setState(() => _mutating = false);
    }
  }

  /// Seconds until the limit set here stops the command; null when none is
  /// known.
  int? get _secondsLeft {
    final limit = _limitSeconds;
    final from = _limitFrom;
    if (limit == null || limit == 0 || from == null) return null;
    return limit - clock.now().difference(from).inSeconds;
  }

  String _statusWords(AppLocalizations l10n) {
    if (_stopped) return l10n.workStopped;
    if (!_sameScope || !_connected || !_output.available) {
      return l10n.workUnknown;
    }
    final shell = _output.shell;
    final outcome = _outcome(shell);
    final base = l10n.workStatusElapsed(
      _outcomeWord(l10n, outcome),
      _elapsed(shell),
    );
    if (outcome != _Outcome.running) return base;
    final left = _secondsLeft;
    if (left != null) {
      return l10n.workStatusElapsed(
        base,
        l10n.shellOutputStopsIn(_clockText(left)),
      );
    }
    if (_limitSeconds == 0) {
      return l10n.workStatusElapsed(base, l10n.shellOutputNoLimit);
    }
    return base;
  }

  @override
  void dispose() {
    _timer?.cancel();
    unawaited(_events?.cancel());
    widget.controller.removeListener(_connectionChanged);
    WidgetsBinding.instance.removeObserver(this);
    _output.removeListener(_outputChanged);
    _output.dispose();
    _lines.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final tokens = KitTokens.of(context);
    final shell = _output.shell;
    final running = _output.available && shell.running && !_stopped;
    final outcome = _outcome(shell);
    final left = running ? _secondsLeft : null;
    final canRefresh = _sameScope && _connected && !_stopped;
    final gap = SizedBox(height: tokens.space3);

    final notices = <Widget>[
      if (!_sameScope)
        KitNotice(message: l10n.workContextChanged, icon: AppIconography.info)
      else if (!_connected)
        KitNotice(message: l10n.workDisconnected, icon: AppIconography.sync)
      else if (!_stopped && !_output.available)
        KitNotice(
          message: _output.serverRestarted
              ? l10n.workRestarted
              : l10n.workUnavailable,
          icon: AppIconography.info,
        ),
      if (_output.error case final error?)
        KitNotice.error(
          message: l10n.shellOutputReadFailed,
          error: error,
          details: productErrorDetails(error),
          retry: KitAction(
            label: l10n.workRetry,
            onPressed: () => _refresh(reconcile: true),
          ),
        ),
      if (_limitError case final error?)
        KitNotice.error(
          key: const Key('shell-output-limit-failed'),
          message: l10n.shellOutputLimitFailed,
          error: error,
          details: productErrorDetails(error),
          retry: KitAction(
            label: l10n.workRetry,
            onPressed: _canMutate ? () => _applyLimit(_failedLimit!) : null,
          ),
        ),
      // About to hit its time limit: say it while it can still be changed.
      if (left != null && left <= 60)
        KitNotice(
          key: const Key('shell-output-about-to-stop'),
          icon: AppIconography.timer,
          message: l10n.shellOutputAboutToStop(_clockText(left)),
        ),
      if (_output.trimmed)
        KitNotice(message: l10n.workTrimmed, icon: AppIconography.info),
    ];

    final details = <KitTechnicalValue>[
      KitTechnicalValue(l10n.shellOutputDetailCommand, widget.shell.command),
      if (widget.shell.directory case final directory?
          when directory.isNotEmpty)
        KitTechnicalValue(l10n.shellOutputDetailFolder, directory),
      if (!running && shell.exitCode != null)
        KitTechnicalValue(l10n.shellOutputDetailExit, '${shell.exitCode}'),
      KitTechnicalValue(l10n.shellOutputDetailId, widget.shell.id),
    ];

    return KitScreen(
      topBar: KitTopBar(
        title: l10n.workOutput,
        actions: [
          KitAction(
            key: const Key('shell-output-refresh'),
            label: l10n.workRefresh,
            icon: AppIconography.retry,
            onPressed: canRefresh ? () => _refresh(reconcile: true) : null,
            disabledReason: canRefresh ? null : l10n.runningWorkOffline,
          ),
        ],
      ),
      width: KitScreenWidth.reading,
      loading: _output.refreshing || _mutating,
      loadingLabel: l10n.shellOutputReading,
      body: LayoutBuilder(
        builder: (context, constraints) => ListView(
          key: const Key('shell-output-content'),
          padding: KitScreen.padding(context),
          children: [
            KitText.mono(shortCommand(widget.shell.command), selectable: true),
            SizedBox(height: tokens.space1),
            KitText(
              _statusWords(l10n),
              key: const Key('shell-output-status'),
              role: KitTextRole.secondary,
              tone: outcome == _Outcome.failed || outcome == _Outcome.timedOut
                  ? KitTextTone.primary
                  : KitTextTone.secondary,
              tabular: true,
            ),
            for (final notice in notices) ...[gap, notice],
            if (running) ...[
              gap,
              // Two acts kept apart: changing the limit is ordinary, Stop
              // ends the command (KitActionStack keeps thumbs off it).
              KitActionStack(
                secondary: KitAction(
                  key: const Key('shell-output-limit'),
                  label: l10n.workTimeout,
                  icon: AppIconography.timer,
                  onPressed: _canMutate ? _chooseLimit : null,
                ),
                tertiary: [
                  KitAction(
                    key: const Key('shell-output-stop'),
                    label: l10n.workStop,
                    icon: AppIconography.stopCircle,
                    destructive: true,
                    onPressed: _canMutate ? _stop : null,
                  ),
                ],
              ),
            ],
            gap,
            // The page exists to show this log, so it fills the window
            // instead of ending mid-screen (map: shell-output).
            SizedBox(
              height: constraints.maxHeight * .72,
              child: KitLogPanel(
                panelKey: const Key('shell-output-text'),
                lines: _lines,
                live: running,
                size: KitLogSize.fill,
                emptyText: running ? l10n.workNoOutput : l10n.workNoFinalOutput,
                ended: running || !_output.available
                    ? null
                    : KitLogEnd(
                        exitCode: _stopped ? null : shell.exitCode,
                        failed:
                            !_stopped &&
                            (outcome == _Outcome.failed ||
                                outcome == _Outcome.timedOut),
                        reason: _stopped || outcome == _Outcome.stopped
                            ? l10n.workStopped
                            : outcome == _Outcome.timedOut
                            ? l10n.workTimedOut
                            : null,
                      ),
              ),
            ),
            if (_output.hasMore && !_stopped && _sameScope && _connected) ...[
              gap,
              KitButton.tertiary(
                key: const Key('shell-output-more'),
                label: l10n.workMoreOutput,
                onPressed: _output.refreshing ? null : _refresh,
              ),
            ],
            gap,
            KitDetailsFold(values: details),
          ],
        ),
      ),
    );
  }
}
