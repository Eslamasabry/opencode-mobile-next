part of '../chat_screen.dart';

class _BackgroundWorkBar extends StatelessWidget {
  const _BackgroundWorkBar({
    required this.canMove,
    required this.moving,
    required this.moveLabel,
    required this.runningCount,
    required this.onMove,
    required this.onOpenRunning,
  });

  final bool canMove;
  final bool moving;
  final String moveLabel;
  final int runningCount;
  final VoidCallback onMove;
  final VoidCallback onOpenRunning;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = _chatL10n(context);
    return Padding(
      key: const ValueKey('background-work-bar'),
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 4),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppTheme.hairline(theme)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
          child: Row(
            children: [
              if (canMove)
                Expanded(
                  child: TextButton.icon(
                    key: const ValueKey('move-work-to-background'),
                    onPressed: moving ? null : onMove,
                    icon: moving
                        ? const SizedBox.square(
                            dimension: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.move_down_rounded, size: 19),
                    label: Text(moveLabel, overflow: TextOverflow.ellipsis),
                  ),
                ),
              if (canMove && runningCount > 0) const SizedBox(width: 4),
              if (runningCount > 0)
                TextButton.icon(
                  key: const ValueKey('open-running-work'),
                  onPressed: onOpenRunning,
                  icon: const Icon(Icons.pending_actions_rounded, size: 19),
                  label: Text(l10n.backgroundWorkRunningCount(runningCount)),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RunningWorkSheet extends StatefulWidget {
  const _RunningWorkSheet({
    required this.shellGateway,
    required this.sessionID,
    required this.initialShells,
    required this.agents,
    required this.onOpenAgent,
  });

  final ShellJobGateway? shellGateway;
  final String sessionID;
  final List<ShellJob> initialShells;
  final List<RunningAgentEntry> agents;
  final ValueChanged<Session> onOpenAgent;

  @override
  State<_RunningWorkSheet> createState() => _RunningWorkSheetState();
}

class _RunningWorkSheetState extends State<_RunningWorkSheet> {
  late List<ShellJob> _shells = widget.initialShells;
  Timer? _timer;
  bool _refreshing = false;
  String? _error;

  List<RunningAgentEntry> get _agents =>
      widget.agents.where((entry) => entry.busy && !entry.current).toList();

  @override
  void initState() {
    super.initState();
    unawaited(_refresh());
    if (widget.shellGateway != null) {
      _timer = Timer.periodic(
        const Duration(seconds: 2),
        (_) => unawaited(_refresh()),
      );
    }
  }

  Future<void> _refresh() async {
    final gateway = widget.shellGateway;
    if (gateway == null || _refreshing) return;
    _refreshing = true;
    try {
      final shells = (await gateway.listShellJobs())
          .where(
            (shell) => shell.running && shell.sessionID == widget.sessionID,
          )
          .toList();
      if (!mounted) return;
      setState(() {
        _shells = shells;
        _error = null;
      });
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      _refreshing = false;
    }
  }

  Future<void> _openOutput(ShellJob shell) async {
    final gateway = widget.shellGateway;
    if (gateway == null) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      constraints: const BoxConstraints(maxWidth: 760),
      builder: (_) => _ShellOutputSheet(
        gateway: gateway,
        shell: shell,
        onStopped: _refresh,
      ),
    );
    unawaited(_refresh());
  }

  Future<void> _updateTimeout(ShellJob shell, Duration? timeout) async {
    try {
      await widget.shellGateway?.updateShellTimeout(shell.id, timeout);
      await _refresh();
    } catch (error) {
      if (mounted) showProductError(context, error);
    }
  }

  Future<void> _stop(ShellJob shell) async {
    final l10n = _chatL10n(context);
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.stop_circle_outlined,
      title: l10n.backgroundShellStopTitle,
      message: shell.command,
      confirmLabel: l10n.backgroundShellStop,
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    try {
      await widget.shellGateway?.stopShellJob(shell.id);
      await _refresh();
    } catch (error) {
      if (mounted) showProductError(context, error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = _chatL10n(context);
    final count = _agents.length + _shells.length;
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * .78,
      child: Column(
        children: [
          const SizedBox(height: 8),
          Container(
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: theme.colorScheme.outlineVariant,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          ListTile(
            title: Text(l10n.backgroundWorkTitle),
            subtitle: Text(
              count == 0
                  ? l10n.backgroundWorkEmptyDescription
                  : l10n.backgroundWorkItemCount(count),
            ),
            trailing: IconButton(
              tooltip: l10n.refresh,
              onPressed: _refreshing ? null : _refresh,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                _error!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
          Expanded(
            child: count == 0
                ? Center(child: Text(l10n.backgroundWorkEmpty))
                : ListView(
                    padding: const EdgeInsets.only(bottom: 24),
                    children: [
                      for (final agent in _agents)
                        ListTile(
                          key: ValueKey(
                            'running-work-agent-${agent.session.id}',
                          ),
                          leading: const Icon(Icons.smart_toy_outlined),
                          title: Text(agent.label),
                          subtitle: Text(l10n.backgroundSubagentRunning),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () => widget.onOpenAgent(agent.session),
                        ),
                      for (final shell in _shells)
                        ListTile(
                          key: ValueKey('running-work-shell-${shell.id}'),
                          leading: const Icon(Icons.terminal_rounded),
                          title: Text(
                            shell.command,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontFamily: AppTheme.monoFamily,
                            ),
                          ),
                          subtitle: Text(
                            l10n.backgroundShellRunning(
                              _runningWorkElapsed(
                                shell.startedAt,
                                l10n.backgroundWorkRunning,
                              ),
                            ),
                          ),
                          onTap: () => _openOutput(shell),
                          trailing: PopupMenuButton<String>(
                            tooltip: l10n.backgroundShellActions,
                            onSelected: (value) {
                              switch (value) {
                                case 'minute':
                                  unawaited(
                                    _updateTimeout(
                                      shell,
                                      const Duration(minutes: 1),
                                    ),
                                  );
                                case 'five-minutes':
                                  unawaited(
                                    _updateTimeout(
                                      shell,
                                      const Duration(minutes: 5),
                                    ),
                                  );
                                case 'no-timeout':
                                  unawaited(_updateTimeout(shell, null));
                                case 'stop':
                                  unawaited(_stop(shell));
                              }
                            },
                            itemBuilder: (_) => [
                              PopupMenuItem(
                                value: 'minute',
                                child: Text(l10n.backgroundShellTimeoutMinute),
                              ),
                              PopupMenuItem(
                                value: 'five-minutes',
                                child: Text(
                                  l10n.backgroundShellTimeoutFiveMinutes,
                                ),
                              ),
                              PopupMenuItem(
                                value: 'no-timeout',
                                child: Text(l10n.backgroundShellTimeoutClear),
                              ),
                              const PopupMenuDivider(),
                              PopupMenuItem(
                                value: 'stop',
                                child: Text(l10n.backgroundShellStop),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}

class _ShellOutputSheet extends StatefulWidget {
  const _ShellOutputSheet({
    required this.gateway,
    required this.shell,
    required this.onStopped,
  });

  final ShellJobGateway gateway;
  final ShellJob shell;
  final Future<void> Function() onStopped;

  @override
  State<_ShellOutputSheet> createState() => _ShellOutputSheetState();
}

class _ShellOutputSheetState extends State<_ShellOutputSheet> {
  final _output = StringBuffer();
  Timer? _timer;
  int _cursor = 0;
  bool _loading = false;
  bool _truncated = false;
  bool _stopping = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_poll());
    _timer = Timer.periodic(
      const Duration(seconds: 1),
      (_) => unawaited(_poll()),
    );
  }

  Future<void> _poll() async {
    if (_loading) return;
    _loading = true;
    try {
      for (var pageCount = 0; pageCount < 8; pageCount++) {
        final page = await widget.gateway.readShellOutput(
          widget.shell.id,
          cursor: _cursor,
          limit: 64 * 1024,
        );
        if (page.output.isNotEmpty) {
          _output.write(page.output);
        }
        _cursor = page.cursor;
        _truncated = _truncated || page.truncated;
        if (!page.hasMore) {
          break;
        }
      }
      if (mounted) setState(() => _error = null);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      _loading = false;
    }
  }

  Future<void> _stop() async {
    final l10n = _chatL10n(context);
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.stop_circle_outlined,
      title: l10n.backgroundShellStopTitle,
      message: widget.shell.command,
      confirmLabel: l10n.backgroundShellStop,
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    setState(() => _stopping = true);
    try {
      await widget.gateway.stopShellJob(widget.shell.id);
      await widget.onStopped();
      if (mounted) Navigator.pop(context);
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _stopping = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = _chatL10n(context);
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * .88,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.terminal_rounded),
            title: Text(
              widget.shell.command,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontFamily: AppTheme.monoFamily),
            ),
            subtitle: Text(
              _truncated
                  ? l10n.backgroundShellOutputTruncated
                  : l10n.backgroundShellOutput,
            ),
            trailing: IconButton(
              key: const ValueKey('stop-shell-job'),
              tooltip: l10n.backgroundShellStop,
              onPressed: _stopping ? null : _stop,
              icon: _stopping
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      Icons.stop_circle_rounded,
                      color: theme.colorScheme.error,
                    ),
            ),
          ),
          const Divider(height: 1),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Text(
                _error!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
          Expanded(
            child: _output.isEmpty && _loading
                ? const Center(child: CircularProgressIndicator())
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(14),
                    child: SelectableText(
                      _output.isEmpty
                          ? l10n.backgroundShellOutputWaiting
                          : _output.toString(),
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontFamily: AppTheme.monoFamily,
                        height: 1.4,
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}

String _runningWorkElapsed(int startedAt, String runningLabel) {
  if (startedAt <= 0) return runningLabel;
  final elapsed = DateTime.now().difference(
    DateTime.fromMillisecondsSinceEpoch(startedAt),
  );
  if (elapsed.inHours > 0) {
    return '${elapsed.inHours}h ${elapsed.inMinutes % 60}m';
  }
  if (elapsed.inMinutes > 0) {
    return '${elapsed.inMinutes}m ${elapsed.inSeconds % 60}s';
  }
  return '${elapsed.inSeconds.clamp(0, 59)}s';
}
