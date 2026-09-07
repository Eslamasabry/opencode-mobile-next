import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/isolated_task_launch.dart';
import '../app_theme.dart';
import '../widgets/product_states.dart';

/// Explicit "new task in a fresh worktree" flow. Resolves with the blank
/// session once it exists in the worktree's scope, or null when the user
/// closed the sheet first. Nothing is ever sent to the session from here.
Future<Session?> showIsolatedTaskSheet(
  BuildContext context, {
  required ConnectionController controller,
  required WorkspaceProject project,
  Duration readinessTimeout = const Duration(seconds: 45),
}) => showModalBottomSheet<Session>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: false,
  isDismissible: false,
  enableDrag: false,
  constraints: const BoxConstraints(maxWidth: 720),
  builder: (_) => IsolatedTaskSheet(
    controller: controller,
    project: project,
    readinessTimeout: readinessTimeout,
  ),
);

class IsolatedTaskSheet extends StatefulWidget {
  const IsolatedTaskSheet({
    super.key,
    required this.controller,
    required this.project,
    this.readinessTimeout = const Duration(seconds: 45),
  });

  final ConnectionController controller;
  final WorkspaceProject project;
  final Duration readinessTimeout;

  @override
  State<IsolatedTaskSheet> createState() => _IsolatedTaskSheetState();
}

class _IsolatedTaskSheetState extends State<IsolatedTaskSheet> {
  final _name = TextEditingController();
  IsolatedTaskLaunch? _launch;
  bool _autoOpened = false;
  bool _popped = false;

  @override
  void dispose() {
    _launch?.removeListener(_onLaunchChanged);
    _launch?.dispose();
    _name.dispose();
    super.dispose();
  }

  void _start() {
    if (_launch != null) return;
    final IsolatedTaskLaunch launch;
    try {
      launch = widget.controller.startIsolatedTask(
        project: widget.project,
        name: _name.text,
        readinessTimeout: widget.readinessTimeout,
      );
    } catch (error) {
      showProductError(context, error);
      return;
    }
    launch.addListener(_onLaunchChanged);
    setState(() => _launch = launch);
  }

  void _onLaunchChanged() {
    if (!mounted) return;
    final launch = _launch!;
    setState(() {});
    if (launch.phase == IsolatedTaskPhase.ready && !_autoOpened) {
      // Ready is the one state that opens without another tap: the user
      // already asked for the session when they pressed Start.
      _autoOpened = true;
      unawaited(launch.open());
    } else if (launch.phase == IsolatedTaskPhase.opened && !_popped) {
      _popped = true;
      Navigator.of(context).pop(launch.session);
    }
  }

  void _close() {
    if (_popped) return;
    final launch = _launch;
    if (launch != null && launch.canCancel) launch.cancel();
    _popped = true;
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final launch = _launch;
    // Only the open step is uninterruptible: leaving mid-open would drop a
    // session the server may still create. Every other state closes as a
    // plain stop-waiting, which never deletes anything.
    final busy = launch != null && launch.phase == IsolatedTaskPhase.opening;
    return PopScope(
      canPop: !busy,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) return;
        final launch = _launch;
        if (launch != null && launch.canCancel) launch.cancel();
        _popped = true;
      },
      child: Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          key: const Key('isolated-task-sheet'),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      l10n.isolatedTaskTitle,
                      style: theme.textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    key: const Key('isolated-task-close'),
                    tooltip: l10n.isolatedTaskClose,
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    onPressed: busy ? null : _close,
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(widget.project.name, style: theme.textTheme.titleSmall),
              Text(
                widget.project.directory,
                style: theme.textTheme.bodySmall?.copyWith(
                  fontFamily: AppTheme.monoFamily,
                  color: AppTheme.mutedOf(theme),
                ),
              ),
              const SizedBox(height: 12),
              if (launch == null)
                ..._form(l10n, theme)
              else
                _status(l10n, theme),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _form(AppLocalizations l10n, ThemeData theme) => [
    Text(
      l10n.isolatedTaskIntro(widget.project.name),
      style: theme.textTheme.bodyMedium,
    ),
    const SizedBox(height: 16),
    TextField(
      key: const Key('isolated-task-name'),
      controller: _name,
      autofocus: false,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _start(),
      decoration: InputDecoration(
        labelText: l10n.isolatedTaskNameLabel,
        helperText: l10n.isolatedTaskNameHelper,
      ),
    ),
    const SizedBox(height: 16),
    FilledButton.icon(
      key: const Key('isolated-task-start'),
      onPressed: _start,
      icon: const Icon(Icons.account_tree_outlined),
      label: Text(l10n.isolatedTaskStart),
    ),
  ];

  Widget _status(AppLocalizations l10n, ThemeData theme) {
    final launch = _launch!;
    final name = launch.worktree?.name ?? launch.requestedName ?? '';
    final branch = launch.worktree?.branch;
    final (
      String headline,
      String? detail,
      bool progress,
    ) = switch (launch.phase) {
      IsolatedTaskPhase.idle || IsolatedTaskPhase.creating => (
        l10n.isolatedTaskCreating,
        l10n.isolatedTaskCreatingHint,
        true,
      ),
      IsolatedTaskPhase.preparing => (
        l10n.isolatedTaskPreparing(name),
        null,
        true,
      ),
      // After a failed open the worktree is still ready but nothing is in
      // flight: no spinner, and the headline stops promising an open.
      IsolatedTaskPhase.ready when launch.openError != null => (
        l10n.isolatedTaskReadyIdle(name),
        null,
        false,
      ),
      IsolatedTaskPhase.ready => (l10n.isolatedTaskReady(name), null, true),
      IsolatedTaskPhase.unconfirmed => (
        l10n.isolatedTaskUnconfirmed(name),
        l10n.isolatedTaskUnconfirmedHint,
        false,
      ),
      IsolatedTaskPhase.failed => (
        launch.worktree == null
            ? l10n.isolatedTaskCreateFailed
            : l10n.isolatedTaskFailed,
        launch.message ??
            (launch.failure == null ? null : productErrorText(launch.failure!)),
        false,
      ),
      IsolatedTaskPhase.opening => (l10n.isolatedTaskOpening(name), null, true),
      IsolatedTaskPhase.opened => (l10n.isolatedTaskOpened(name), null, false),
      IsolatedTaskPhase.cancelled => (
        l10n.isolatedTaskCancelled,
        launch.worktree == null
            ? l10n.isolatedTaskCancelledUnknown
            : l10n.isolatedTaskCancelledKept(name),
        false,
      ),
    };
    final kept =
        launch.phase == IsolatedTaskPhase.failed && launch.worktree != null;
    final openError = launch.openError;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          liveRegion: true,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 2, right: 12),
                child: progress
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        switch (launch.phase) {
                          IsolatedTaskPhase.failed => Icons.error_outline,
                          IsolatedTaskPhase.unconfirmed => Icons.help_outline,
                          IsolatedTaskPhase.opened =>
                            Icons.check_circle_outline,
                          _ => Icons.info_outline,
                        },
                        size: 20,
                        color: launch.phase == IsolatedTaskPhase.failed
                            ? theme.colorScheme.error
                            : AppTheme.mutedOf(theme),
                      ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      headline,
                      key: const Key('isolated-task-status'),
                      style: theme.textTheme.bodyLarge,
                    ),
                    if (detail != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        detail,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: launch.phase == IsolatedTaskPhase.failed
                              ? theme.colorScheme.error
                              : AppTheme.mutedOf(theme),
                        ),
                      ),
                    ],
                    if (kept) ...[
                      const SizedBox(height: 4),
                      Text(
                        l10n.isolatedTaskFailedKept(name),
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                    ],
                    if (branch != null && branch.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        l10n.isolatedTaskBranch(branch),
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontFamily: AppTheme.monoFamily,
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                    ],
                    if (openError != null) ...[
                      const SizedBox(height: 8),
                      Text(
                        productErrorText(openError),
                        key: const Key('isolated-task-open-error'),
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.error,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Wrap(
          alignment: WrapAlignment.end,
          spacing: 8,
          runSpacing: 8,
          children: _actions(l10n, launch),
        ),
      ],
    );
  }

  List<Widget> _actions(AppLocalizations l10n, IsolatedTaskLaunch launch) {
    switch (launch.phase) {
      case IsolatedTaskPhase.idle:
      case IsolatedTaskPhase.creating:
      case IsolatedTaskPhase.preparing:
        return [
          OutlinedButton(
            key: const Key('isolated-task-stop'),
            onPressed: _close,
            child: Text(l10n.isolatedTaskStopWaiting),
          ),
        ];
      case IsolatedTaskPhase.unconfirmed:
        return [
          TextButton(
            key: const Key('isolated-task-stop'),
            onPressed: _close,
            child: Text(l10n.isolatedTaskStopWaiting),
          ),
          OutlinedButton(
            key: const Key('isolated-task-keep-waiting'),
            onPressed: launch.keepWaiting,
            child: Text(l10n.isolatedTaskKeepWaiting),
          ),
          FilledButton(
            key: const Key('isolated-task-open-anyway'),
            onPressed: () => unawaited(launch.open(acceptUnconfirmed: true)),
            child: Text(l10n.isolatedTaskOpenAnyway),
          ),
        ];
      case IsolatedTaskPhase.ready:
        return [
          if (launch.openError != null)
            FilledButton(
              key: const Key('isolated-task-retry-open'),
              onPressed: () => unawaited(launch.open()),
              child: Text(l10n.isolatedTaskRetryOpen),
            ),
          if (launch.openError != null)
            TextButton(
              key: const Key('isolated-task-dismiss'),
              onPressed: _close,
              child: Text(l10n.isolatedTaskClose),
            ),
        ];
      case IsolatedTaskPhase.opening:
      case IsolatedTaskPhase.opened:
        return const [];
      case IsolatedTaskPhase.failed:
      case IsolatedTaskPhase.cancelled:
        return [
          FilledButton(
            key: const Key('isolated-task-dismiss'),
            onPressed: _close,
            child: Text(l10n.isolatedTaskClose),
          ),
        ];
    }
  }
}
