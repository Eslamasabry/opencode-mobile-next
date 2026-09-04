import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/shell_output.dart';
import '../widgets/product_states.dart';
import '../widgets/running_agents_strip.dart';

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

// A transport can be replaced while reconnecting to the same location. Pin
// actual scope, not its refresh counter, so that reconnection stays usable.
typedef _WorkScope = (String?, String?, String?, String?);

_WorkScope _scope(ConnectionController conn) =>
    (conn.profile?.id, conn.profile?.baseUrl, conn.directory, conn.workspace);

String _status(AppLocalizations l10n, ManagedShell shell) =>
    switch (shell.status) {
      ManagedShellStatus.running => l10n.workRunning,
      ManagedShellStatus.exited =>
        shell.exitCode == null
            ? l10n.workFinished
            : l10n.workExitCode(shell.exitCode!),
      ManagedShellStatus.timeout => l10n.workTimedOut,
      ManagedShellStatus.killed => l10n.workStopped,
      ManagedShellStatus.unknown => l10n.workUnknown,
    };

String _elapsed(ManagedShell shell) {
  final seconds = (shell.completedAt ?? DateTime.now())
      .difference(shell.startedAt)
      .inSeconds
      .clamp(0, 365 * 86400);
  return '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';
}

Future<String?> showRunningWorkSheet(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
  required Set<String> shellIDs,
}) => showModalBottomSheet<String>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: true,
  builder: (_) => RunningWorkSheet(
    controller: controller,
    sessionID: sessionID,
    shellIDs: shellIDs,
  ),
);

class RunningWorkSheet extends StatefulWidget {
  const RunningWorkSheet({
    super.key,
    required this.controller,
    required this.sessionID,
    this.shellIDs = const {},
  });
  final ConnectionController controller;
  final String sessionID;
  final Set<String> shellIDs;
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
    _events = widget.controller.events.listen((event) {
      if (event.type.startsWith('shell.') && _visible) unawaited(_refresh());
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
      final result = await repo.loadRunningShells();
      if (!mounted || !_scopeMatches || repo != conn.repository) return;
      final related = {
        widget.sessionID,
        for (final session in conn.sessionsById.values)
          if (session.parentID == widget.sessionID) session.id,
      };
      setState(() {
        _shellSupport = result.supported;
        _shells = result.supported
            ? result.shells
                  .where(
                    (shell) =>
                        shell.running &&
                        (related.contains(shell.sessionID) ||
                            widget.shellIDs.contains(shell.id)),
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
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final theme = Theme.of(context);
    final conn = widget.controller;
    final agents = runningAgentEntries(
      sessionID: widget.sessionID,
      sessions: conn.sessionsById,
      busy: conn.busySessions,
    ).where((entry) => !entry.current && entry.busy).toList();
    final disconnected = conn.status != StreamStatus.connected;
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .8,
        ),
        child: ListView(
          key: const Key('running-work-sheet'),
          shrinkWrap: true,
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.workTitle,
                    style: theme.textTheme.titleLarge,
                  ),
                ),
                IconButton(
                  tooltip: l10n.workRefresh,
                  onPressed: disconnected || !_scopeMatches ? null : _refresh,
                  icon: const Icon(Icons.refresh_rounded),
                ),
                IconButton(
                  tooltip: l10n.workClose,
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
            Text(l10n.workDescription, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 16),
            if (!_scopeMatches)
              Text(l10n.workContextChanged)
            else ...[
              if (disconnected) _Notice(text: l10n.workDisconnected),
              if (_error != null)
                _Notice(
                  text: productErrorText(_error!),
                  action: TextButton(
                    onPressed: _refresh,
                    child: Text(l10n.workRetry),
                  ),
                ),
              if (_loading)
                const Center(child: CircularProgressIndicator())
              else if (agents.isEmpty && _shells.isEmpty && _error == null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Column(
                    children: [
                      const Icon(Icons.task_alt_rounded, size: 32),
                      const SizedBox(height: 12),
                      Text(l10n.workEmpty, style: theme.textTheme.titleMedium),
                      const SizedBox(height: 4),
                      Text(
                        l10n.workEmptyDescription,
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              if (agents.isNotEmpty) ...[
                Text(l10n.workAgents, style: theme.textTheme.titleSmall),
                for (final entry in agents)
                  ListTile(
                    key: ValueKey('work-agent-${entry.session.id}'),
                    contentPadding: const EdgeInsets.symmetric(vertical: 4),
                    leading: const Icon(Icons.account_tree_outlined),
                    title: Text(
                      entry.label,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(l10n.workRunning),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => Navigator.pop(context, entry.session.id),
                  ),
              ],
              if (_shells.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(l10n.workCommands, style: theme.textTheme.titleSmall),
                for (final shell in _shells)
                  ListTile(
                    key: ValueKey('work-shell-${shell.id}'),
                    contentPadding: const EdgeInsets.symmetric(vertical: 6),
                    leading: const Icon(Icons.terminal_rounded),
                    title: Text(
                      shell.command,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(
                      l10n.workStatusElapsed(
                        _status(l10n, shell),
                        _elapsed(shell),
                      ),
                    ),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: disconnected
                        ? null
                        : () async {
                            if (!_scopeMatches ||
                                conn.status != StreamStatus.connected) {
                              return;
                            }
                            await Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) => ShellOutputScreen(
                                  controller: conn,
                                  shell: shell,
                                ),
                              ),
                            );
                            if (mounted && _visible) await _refresh();
                          },
                  ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

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
  late final _WorkScope _pinnedScope;
  late int _revision;
  final _scroll = ScrollController();
  StreamSubscription<EventEnvelope>? _events;
  Timer? _timer;
  bool _active = true;
  bool _follow = true;
  bool _mutating = false;
  bool _stopped = false;
  bool _reconcilePending = true;
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
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_follow &&
          _visible &&
          _output.available &&
          (_output.shell.running || _output.hasMore)) {
        unawaited(_refresh());
      }
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
    setState(() {});
    if (_follow) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _scroll.hasClients) {
          _scroll.jumpTo(_scroll.position.maxScrollExtent);
        }
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _active = state == AppLifecycleState.resumed;
    if (_active) unawaited(_refresh(reconcile: true));
  }

  Future<void> _stop() async {
    if (!_canMutate) return;
    final l10n = _strings(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.workStopTitle),
        content: Text(l10n.workStopDescription),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.workCancel),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.workStop),
          ),
        ],
      ),
    );
    if (!mounted || confirmed != true || !_canMutate) return;
    await _change(() async {
      await widget.controller.repository!.stopManagedShell(widget.shell.id);
      if (mounted && _sameScope) setState(() => _stopped = true);
    });
  }

  Future<void> _timeout() async {
    if (!_canMutate) return;
    final l10n = _strings(context);
    final seconds = await showModalBottomSheet<int>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * .8,
          ),
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            children: [
              Text(
                l10n.workTimeoutTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              Text(l10n.workTimeoutDescription),
              for (final option in <int, String>{
                60: l10n.workTimeoutOneMinute,
                300: l10n.workTimeoutFiveMinutes,
                900: l10n.workTimeoutFifteenMinutes,
                3600: l10n.workTimeoutOneHour,
                0: l10n.workTimeoutNone,
              }.entries)
                ListTile(
                  title: Text(option.value),
                  onTap: () => Navigator.pop(context, option.key),
                ),
            ],
          ),
        ),
      ),
    );
    if (!mounted || seconds == null || !_canMutate) return;
    await _change(() async {
      await widget.controller.repository!.setManagedShellTimeout(
        widget.shell.id,
        seconds == 0 ? null : Duration(seconds: seconds),
      );
      if (mounted && _sameScope) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.workTimeoutSaved)));
      }
    });
  }

  Future<void> _change(Future<void> Function() action) async {
    setState(() => _mutating = true);
    try {
      await action();
      if (mounted && _sameScope) await _refresh();
    } catch (error) {
      if (mounted && _sameScope) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(productErrorText(error))));
      }
    } finally {
      if (mounted) setState(() => _mutating = false);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    unawaited(_events?.cancel());
    widget.controller.removeListener(_connectionChanged);
    WidgetsBinding.instance.removeObserver(this);
    _output.removeListener(_outputChanged);
    _output.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final theme = Theme.of(context);
    final output = _output.displayText;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.workOutput),
        actions: [
          IconButton(
            tooltip: l10n.workCopyOutput,
            onPressed: output.isEmpty
                ? null
                : () async {
                    await Clipboard.setData(ClipboardData(text: output));
                    if (context.mounted) {
                      ScaffoldMessenger.of(
                        context,
                      ).showSnackBar(SnackBar(content: Text(l10n.workCopied)));
                    }
                  },
            icon: const Icon(Icons.copy_rounded),
          ),
          IconButton(
            tooltip: l10n.workRefresh,
            onPressed: _sameScope && _connected && !_stopped
                ? () => _refresh(reconcile: true)
                : null,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: NotificationListener<ScrollUpdateNotification>(
          onNotification: (event) {
            if (_follow &&
                event.dragDetails != null &&
                event.metrics.pixels < event.metrics.maxScrollExtent - 24) {
              setState(() => _follow = false);
            }
            return false;
          },
          child: ListView(
            key: const Key('shell-output-content'),
            controller: _scroll,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              SelectableText(
                widget.shell.command,
                style: theme.textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                _stopped
                    ? l10n.workStopped
                    : !_sameScope || !_connected || !_output.available
                    ? l10n.workUnknown
                    : l10n.workStatusElapsed(
                        _status(l10n, _output.shell),
                        _elapsed(_output.shell),
                      ),
              ),
              const SizedBox(height: 8),
              if (_output.available && _output.shell.running && !_stopped)
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: [
                    OutlinedButton.icon(
                      onPressed: _canMutate ? _timeout : null,
                      icon: const Icon(Icons.timer_outlined),
                      label: Text(l10n.workTimeout),
                    ),
                    TextButton.icon(
                      style: TextButton.styleFrom(
                        foregroundColor: theme.colorScheme.error,
                      ),
                      onPressed: _canMutate ? _stop : null,
                      icon: const Icon(Icons.stop_circle_outlined),
                      label: Text(l10n.workStop),
                    ),
                  ],
                ),
              if (!_stopped &&
                  _output.available &&
                  (_output.shell.running || _output.hasMore))
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.workFollow),
                  value: _follow,
                  onChanged: _sameScope && _connected
                      ? (value) {
                          setState(() => _follow = value);
                          if (value) unawaited(_refresh());
                        }
                      : null,
                ),
              if (!_sameScope)
                _Notice(text: l10n.workContextChanged)
              else if (!_connected)
                _Notice(text: l10n.workDisconnected)
              else if (!_stopped && !_output.available)
                _Notice(
                  text: _output.serverRestarted
                      ? l10n.workRestarted
                      : l10n.workUnavailable,
                ),
              if (_output.error != null)
                _Notice(
                  text: productErrorText(_output.error!),
                  action: TextButton(
                    onPressed: () => _refresh(reconcile: true),
                    child: Text(l10n.workRetry),
                  ),
                ),
              if (_output.trimmed) _Notice(text: l10n.workTrimmed),
              if (_output.refreshing || _mutating)
                const LinearProgressIndicator(minHeight: 2),
              Container(
                key: const Key('shell-output-text'),
                margin: const EdgeInsets.only(top: 12),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: SelectableText(
                  output.isEmpty
                      ? (_output.shell.running && !_stopped && _output.available
                            ? l10n.workNoOutput
                            : l10n.workNoFinalOutput)
                      : output,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontFamily: 'monospace',
                    height: 1.5,
                  ),
                ),
              ),
              if (_output.hasMore && !_stopped && _sameScope && _connected)
                TextButton(
                  onPressed: _output.refreshing ? null : _refresh,
                  child: Text(l10n.workMoreOutput),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.text, this.action});
  final String text;
  final Widget? action;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [Text(text), ?action],
    ),
  );
}
