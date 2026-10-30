import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/isolated_task_launch.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/product_states.dart'
    show productErrorDetails, productErrorText;

/// Start in a separate copy (isolated-task-sheet; closure gap 15): asks what
/// the new conversation should work on, makes a separate copy of the
/// project (a git worktree on its own branch), waits for the project's
/// setup, opens a conversation in the copy and sends the task there.
/// Resolves with the session once it exists in the copy's scope, or null
/// when the person closed the sheet first.
///
/// The frame is not dismissible by a swipe: leaving while the conversation
/// is being opened or the task sent would drop work the server may still
/// be doing. Back, Esc and a tap outside still close it as "stop waiting"
/// in every other state (the body handles them), and each state shows its
/// one way out.
Future<Session?> showIsolatedTaskSheet(
  BuildContext context, {
  required ConnectionController controller,
  required WorkspaceProject project,
  Duration readinessTimeout = const Duration(seconds: 45),
  String initialTask = '',
}) {
  final openingScope = controller.isolatedTaskScope;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return showKitSheet<Session>(
    context,
    title: l10n.isolatedTaskTitle,
    subtitle: project.name,
    icon: AppIconography.branch,
    dismissible: false,
    body: (_) => IsolatedTaskSheet(
      controller: controller,
      project: project,
      openingScope: openingScope,
      readinessTimeout: readinessTimeout,
      initialTask: initialTask,
    ),
  );
}

/// The draft target of the task field ([KitDraft.keyFor]): one per server
/// profile, kept until the task reaches its conversation.
const isolatedTaskDraftTarget = 'isolated.task';

class IsolatedTaskSheet extends StatefulWidget {
  const IsolatedTaskSheet({
    super.key,
    required this.controller,
    required this.project,
    required this.openingScope,
    this.readinessTimeout = const Duration(seconds: 45),
    this.initialTask = '',
  });

  /// Words already typed (on the start screen): the task field begins with
  /// them, unless a saved draft says otherwise.
  final String initialTask;
  final ConnectionController controller;
  final WorkspaceProject project;
  final Object openingScope;
  final Duration readinessTimeout;

  @override
  State<IsolatedTaskSheet> createState() => _IsolatedTaskSheetState();
}

class _IsolatedTaskSheetState extends State<IsolatedTaskSheet> {
  final _task = TextEditingController();
  late final KitDraft? _taskDraft = switch (widget.controller.profile?.id) {
    final profileId? when profileId.isNotEmpty => KitDraft(
      target: isolatedTaskDraftTarget,
      profileId: profileId,
      controller: _task,
    ),
    _ => null,
  };
  final _name = TextEditingController();
  IsolatedTaskLaunch? _launch;
  bool _autoOpened = false;
  bool _popped = false;
  late final ConnectionController _controller;
  late final Object _openingScope;
  bool _invalid = false;
  Object? _startError;

  /// When Start was pressed: the elapsed time of the whole wait.
  DateTime? _startedAt;

  /// The conversation exists; the typed task is on its way to it.
  bool _sending = false;

  /// The conversation opened but its task was not sent: the session to
  /// open, why, and whether the task was kept as the session's draft.
  Session? _unsent;
  Object? _sendError;
  bool _draftKept = false;

  /// The copy whose setup failed was removed: its name, for the form's
  /// notice.
  String? _removed;

  @override
  void initState() {
    super.initState();
    _controller = widget.controller;
    _task.text = widget.initialTask;
    _openingScope = widget.openingScope;
    _invalid = _openingScope != _controller.isolatedTaskScope;
    _controller.addListener(_scopeChanged);
  }

  void _scopeChanged() {
    // Once launched, controller guards own the transition into the new tree.
    // Before Start, any observed mismatch permanently retires this sheet.
    if (_launch == null && _openingScope != _controller.isolatedTaskScope) {
      setState(() => _invalid = true);
    }
  }

  @override
  void didUpdateWidget(covariant IsolatedTaskSheet oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.controller, _controller) ||
        widget.project.id != oldWidget.project.id ||
        widget.project.directory != oldWidget.project.directory) {
      _invalid = true;
      if (_launch?.canCancel == true) _launch!.cancel();
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_scopeChanged);
    _launch?.removeListener(_onLaunchChanged);
    _launch?.dispose();
    _task.dispose();
    _name.dispose();
    super.dispose();
  }

  void _start() {
    if (_launch != null) return;
    if (_invalid || _openingScope != _controller.isolatedTaskScope) {
      setState(() => _invalid = true);
      return;
    }
    final IsolatedTaskLaunch launch;
    try {
      launch = _controller.startIsolatedTask(
        project: widget.project,
        expectedScope: _openingScope,
        name: _name.text,
        readinessTimeout: widget.readinessTimeout,
      );
    } catch (error) {
      setState(() => _startError = error);
      return;
    }
    launch.addListener(_onLaunchChanged);
    final now = DateTime.now();
    setState(() {
      _startError = null;
      _removed = null;
      _launch = launch;
      _startedAt = now;
    });
  }

  /// Drops the finished launch and returns to the form, keeping what was
  /// typed ([removed] names a copy that was just removed).
  void _backToForm({String? removed}) {
    final launch = _launch;
    launch?.removeListener(_onLaunchChanged);
    launch?.dispose();
    setState(() {
      _launch = null;
      _autoOpened = false;
      _removed = removed;
    });
  }

  /// A failed create: a new launch with the same words.
  void _tryAgain() {
    _backToForm();
    _start();
  }

  void _onLaunchChanged() {
    if (!mounted || _invalid) return;
    final launch = _launch!;
    setState(() {});
    if (launch.phase == IsolatedTaskPhase.ready && !_autoOpened) {
      // Ready is the one state that opens without another tap: the person
      // already asked for the conversation when they pressed Start.
      _autoOpened = true;
      unawaited(launch.open());
    } else if (launch.phase == IsolatedTaskPhase.opened &&
        !_popped &&
        !_sending &&
        _unsent == null) {
      unawaited(_deliver(launch.session!));
    }
  }

  /// The conversation exists: sends the typed task to it (when any), then
  /// closes with the session. A task that did not go out is kept as the
  /// conversation's draft and the sheet says so before it opens.
  Future<void> _deliver(Session session) async {
    final task = _task.text.trim();
    if (task.isEmpty) {
      _finish(session);
      return;
    }
    setState(() => _sending = true);
    try {
      await _send(session, task);
    } catch (error) {
      var kept = false;
      try {
        await _controller.saveSessionDraft(session.id, task);
        kept = true;
        unawaited(_taskDraft?.clear());
      } catch (_) {
        // The words are shown under Details to copy instead.
      }
      if (!mounted) return;
      setState(() {
        _sending = false;
        _unsent = session;
        _sendError = error;
        _draftKept = kept;
      });
      return;
    }
    unawaited(_taskDraft?.clear());
    if (!mounted) return;
    setState(() => _sending = false);
    _finish(session);
  }

  /// One prompt to the new conversation, with the model, agent and variant
  /// it would send with from its composer.
  Future<void> _send(Session session, String task) async {
    final api = await _controller.prepareActionTransport();
    if (api == null) {
      throw const ProductException('OpenCode is reconnecting.');
    }
    await _controller.waitForSessionSelection(session.id, expectedApi: api);
    final agent = _controller.agentForSession(session.id);
    final variant = _controller.variantForSession(session.id);
    await api.promptAsync(
      session.id,
      text: task,
      model: _controller.modelForSession(session.id),
      agent: agent.isEmpty ? null : agent,
      variant: variant.isEmpty ? null : variant,
    );
  }

  void _finish(Session session) {
    if (_popped || !mounted) return;
    _popped = true;
    Navigator.of(context).pop(session);
  }

  void _close() {
    if (_popped) return;
    // The conversation exists even though its task did not go out: leaving
    // opens it, where the task waits as a draft.
    if (_unsent case final session?) {
      _finish(session);
      return;
    }
    final launch = _launch;
    if (launch != null && launch.canCancel) launch.cancel();
    _popped = true;
    Navigator.of(context).pop();
  }

  bool get _busy {
    final launch = _launch;
    return _sending ||
        (launch != null && launch.phase == IsolatedTaskPhase.opening);
  }

  /// Remove the copy whose setup failed, after one destructive question.
  Future<void> _remove(IsolatedTaskLaunch launch) async {
    final worktree = launch.worktree;
    if (worktree == null) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final removed = await showKitConfirm(
      context,
      title: l10n.isolatedTaskRemoveTitle(worktree.name),
      body: l10n.isolatedTaskRemoveBody,
      confirmLabel: l10n.isolatedTaskRemove,
      kind: KitConfirmKind.destructive,
      details: [
        KitTechnicalValue(l10n.isolatedTaskCopyFolder, worktree.directory),
      ],
      confirmKey: const Key('isolated-task-remove-confirm'),
      action: () async {
        final repository = await _controller.prepareActionRepository();
        if (repository == null) {
          throw const ProductException('OpenCode is reconnecting.');
        }
        await repository.removeWorktree(
          projectDirectory: widget.project.directory,
          directory: worktree.directory,
        );
      },
    );
    if (!mounted || !removed) return;
    _backToForm(removed: worktree.name);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final launch = _launch;
    // Only opening and sending are uninterruptible: leaving then would drop
    // a conversation the server may still create. Every other state closes
    // as a plain stop-waiting, which never deletes anything. The frame
    // blocks every pop; this scope turns back, Esc and a tap outside into
    // Close.
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) {
          final launch = _launch;
          if (launch != null && launch.canCancel) launch.cancel();
          _popped = true;
          return;
        }
        if (!_busy) _close();
      },
      child: Column(
        key: const Key('isolated-task-sheet'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_invalid)
            KitStateView(
              icon: AppIconography.info,
              title: l10n.isolatedTaskScopeChanged,
              size: KitStateSize.inline,
              secondary: KitAction(
                key: const Key('isolated-task-dismiss'),
                label: l10n.isolatedTaskClose,
                onPressed: _close,
              ),
            )
          else if (_unsent case final session?)
            _unsentState(l10n, session)
          else if (launch == null)
            ..._form(context, l10n)
          else
            _status(context, l10n, launch),
        ],
      ),
    );
  }

  List<Widget> _form(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final startError = _startError;
    final removed = _removed;
    return [
      KitText(
        l10n.isolatedTaskIntro,
        key: const Key('isolated-task-intro'),
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
      if (removed != null) ...[
        SizedBox(height: tokens.space3),
        KitNotice(
          key: const Key('isolated-task-removed'),
          tone: AppStatusTone.ok,
          icon: AppIconography.checkCircle,
          message: l10n.isolatedTaskRemoved(removed),
        ),
      ],
      SizedBox(height: tokens.space4),
      KitField(
        label: l10n.isolatedTaskPromptLabel,
        helper: l10n.isolatedTaskPromptHelper,
        kind: KitFieldKind.multiline,
        controller: _taskDraft == null ? _task : null,
        draft: _taskDraft,
        autofocus: true,
        fieldKey: const Key('isolated-task-prompt'),
      ),
      SizedBox(height: tokens.space3),
      // What most people never change, folded away (§6).
      KitRowGroup(
        margin: EdgeInsets.zero,
        children: [
          KitExpandRow(
            headerKey: const Key('isolated-task-options'),
            title: l10n.isolatedTaskOptions,
            maintainState: true,
            children: [
              Padding(
                padding: EdgeInsetsDirectional.fromSTEB(
                  tokens.gutter,
                  tokens.space1,
                  tokens.gutter,
                  tokens.space3,
                ),
                child: KitField(
                  label: l10n.isolatedTaskNameLabel,
                  helper: l10n.isolatedTaskNameHelper,
                  controller: _name,
                  textInputAction: TextInputAction.done,
                  onSubmitted: (_) => _start(),
                  fieldKey: const Key('isolated-task-name'),
                ),
              ),
            ],
          ),
        ],
      ),
      if (startError != null) ...[
        SizedBox(height: tokens.space3),
        KitNotice.error(
          key: const Key('isolated-task-start-error'),
          message: productErrorText(startError),
          error: startError,
        ),
      ],
      SizedBox(height: tokens.space4),
      KitActionBlock(
        primary: KitAction(
          key: const Key('isolated-task-start'),
          label: l10n.isolatedTaskStart,
          icon: AppIconography.branch,
          onPressed: _start,
        ),
        tertiary: [
          KitAction(
            key: const Key('isolated-task-close'),
            label: l10n.isolatedTaskClose,
            onPressed: _close,
          ),
        ],
      ),
      SizedBox(height: tokens.space4),
      KitDetailsFold(
        values: [
          KitTechnicalValue(
            l10n.isolatedTaskProjectFolder,
            widget.project.directory,
          ),
        ],
      ),
    ];
  }

  /// The staged wait: make the copy (1 of 3), set it up (2 of 3), open the
  /// conversation and send the task (3 of 3). The caption says how long it
  /// usually takes and, once the wait is slow, how long it has taken.
  KitProgress? _progress(
    AppLocalizations l10n,
    IsolatedTaskLaunch launch,
    String? waited,
  ) {
    final usually = [l10n.isolatedTaskUsually, ?waited].join(' · ');
    final stageOpen = _task.text.trim().isEmpty
        ? l10n.isolatedTaskStageOpen
        : l10n.isolatedTaskStageSend;
    return switch (launch.phase) {
      IsolatedTaskPhase.idle ||
      IsolatedTaskPhase.creating => KitProgress.staged(
        step: 1,
        of: 3,
        label: l10n.isolatedTaskStageCreate,
        caption: usually,
      ),
      IsolatedTaskPhase.preparing => KitProgress.staged(
        step: 2,
        of: 3,
        label: l10n.isolatedTaskStagePrepare,
        caption: usually,
      ),
      IsolatedTaskPhase.ready when launch.openError == null =>
        KitProgress.staged(step: 3, of: 3, label: stageOpen),
      IsolatedTaskPhase.opening || IsolatedTaskPhase.opened =>
        KitProgress.staged(step: 3, of: 3, label: stageOpen),
      _ => null,
    };
  }

  Widget _status(
    BuildContext context,
    AppLocalizations l10n,
    IsolatedTaskLaunch launch,
  ) => KitSince(
    since: _startedAt,
    ticks: KitSinceTicks.minutes,
    builder: (context, wait) => _statusView(
      context,
      l10n,
      launch,
      waited: wait.isSlow ? KitSince.waitingLabel(context, wait.elapsed) : null,
    ),
  );

  Widget _statusView(
    BuildContext context,
    AppLocalizations l10n,
    IsolatedTaskLaunch launch, {
    required String? waited,
  }) {
    final worktree = launch.worktree;
    final name = worktree?.name ?? launch.requestedName ?? '';
    final branch = worktree?.branch;
    final failed = launch.phase == IsolatedTaskPhase.failed;
    final createFailed = failed && worktree == null;
    final (String headline, String? body) = switch (launch.phase) {
      IsolatedTaskPhase.idle || IsolatedTaskPhase.creating => (
        l10n.isolatedTaskCreating,
        l10n.isolatedTaskCreatingHint,
      ),
      IsolatedTaskPhase.preparing => (
        l10n.isolatedTaskPreparing(name),
        l10n.isolatedTaskPreparingHint,
      ),
      // After a failed open the copy is still ready but nothing is in
      // flight: no progress, and the headline stops promising an open.
      IsolatedTaskPhase.ready when launch.openError != null => (
        l10n.isolatedTaskReadyIdle(name),
        null,
      ),
      IsolatedTaskPhase.ready => (l10n.isolatedTaskReady(name), null),
      IsolatedTaskPhase.unconfirmed => (
        l10n.isolatedTaskUnconfirmed(name),
        l10n.isolatedTaskUnconfirmedHint,
      ),
      IsolatedTaskPhase.failed when createFailed => (
        l10n.isolatedTaskCreateFailed,
        launch.failure == null ? null : productErrorText(launch.failure!),
      ),
      IsolatedTaskPhase.failed => (
        l10n.isolatedTaskFailed(name),
        l10n.isolatedTaskFailedBody,
      ),
      IsolatedTaskPhase.opening => (l10n.isolatedTaskOpening(name), null),
      IsolatedTaskPhase.opened => (
        _sending
            ? l10n.isolatedTaskSending(name)
            : l10n.isolatedTaskOpened(name),
        null,
      ),
      IsolatedTaskPhase.cancelled => (l10n.isolatedTaskCancelled, null),
    };
    final icon = switch (launch.phase) {
      IsolatedTaskPhase.failed => AppIconography.error,
      IsolatedTaskPhase.unconfirmed => AppIconography.question,
      IsolatedTaskPhase.opened when !_sending => AppIconography.checkCircle,
      IsolatedTaskPhase.cancelled => AppIconography.info,
      _ => AppIconography.branch,
    };
    final progress = _progress(l10n, launch, waited);
    final (primary, secondary, tertiary) = _actions(l10n, launch);
    final openError = launch.openError;
    // The server's own words (a setup script's output, a refused create)
    // are technical: under Details only, redacted by the fold.
    final String? technical = createFailed
        ? productErrorDetails(launch.failure)
        : failed
        ? launch.message
        : null;
    return KitStateView(
      icon: icon,
      tone: failed
          ? AppStatusTone.failure
          : progress != null
          ? AppStatusTone.progress
          : AppStatusTone.neutral,
      title: headline,
      titleKey: const Key('isolated-task-status'),
      body: body == null || body.isEmpty ? null : body,
      bodyKey: const Key('isolated-task-body'),
      progress: progress,
      size: KitStateSize.inline,
      primary: primary,
      secondary: secondary,
      tertiary: tertiary,
      content: openError == null
          ? null
          : KitNotice.error(
              key: const Key('isolated-task-open-error'),
              message: productErrorText(openError),
              error: openError,
            ),
      detailNotes: [
        if (technical != null && !createFailed) l10n.isolatedTaskSetupOutput,
      ],
      details: technical,
      detailValues: [
        if (branch != null && branch.isNotEmpty)
          KitTechnicalValue(l10n.isolatedTaskBranchLabel, branch),
        if (worktree != null)
          KitTechnicalValue(l10n.isolatedTaskCopyFolder, worktree.directory),
      ],
      detailsKey: const Key('isolated-task-details'),
    );
  }

  /// The conversation opened but its task did not go out: say so, say
  /// where the task is, and open the conversation.
  Widget _unsentState(AppLocalizations l10n, Session session) => KitStateView(
    icon: AppIconography.warning,
    tone: AppStatusTone.failure,
    title: l10n.isolatedTaskSendFailed,
    titleKey: const Key('isolated-task-status'),
    body: _draftKept
        ? l10n.isolatedTaskSendFailedBody
        : l10n.isolatedTaskSendFailedLost,
    bodyKey: const Key('isolated-task-body'),
    size: KitStateSize.inline,
    primary: KitAction(
      key: const Key('isolated-task-open-conversation'),
      label: l10n.isolatedTaskOpenConversation,
      onPressed: () => _finish(session),
    ),
    // Lost as a draft too: the words themselves, to copy.
    details: _draftKept ? productErrorDetails(_sendError) : _task.text.trim(),
    detailsKey: const Key('isolated-task-details'),
  );

  (KitAction?, KitAction?, List<KitAction>) _actions(
    AppLocalizations l10n,
    IsolatedTaskLaunch launch,
  ) {
    final stop = KitAction(
      key: const Key('isolated-task-stop'),
      label: l10n.isolatedTaskStopWaiting,
      onPressed: _close,
    );
    final close = KitAction(
      key: const Key('isolated-task-dismiss'),
      label: l10n.isolatedTaskClose,
      onPressed: _close,
    );
    switch (launch.phase) {
      case IsolatedTaskPhase.idle:
      case IsolatedTaskPhase.creating:
      case IsolatedTaskPhase.preparing:
        return (null, stop, const []);
      case IsolatedTaskPhase.unconfirmed:
        return (
          KitAction(
            key: const Key('isolated-task-open-anyway'),
            label: l10n.isolatedTaskStartAnyway,
            onPressed: () => unawaited(launch.open(acceptUnconfirmed: true)),
          ),
          KitAction(
            key: const Key('isolated-task-keep-waiting'),
            label: l10n.isolatedTaskKeepWaiting,
            onPressed: launch.keepWaiting,
          ),
          [stop],
        );
      case IsolatedTaskPhase.ready:
        if (launch.openError == null) return (null, null, const []);
        return (
          KitAction(
            key: const Key('isolated-task-retry-open'),
            label: l10n.isolatedTaskRetryOpen,
            onPressed: () => unawaited(launch.open()),
          ),
          null,
          [close],
        );
      case IsolatedTaskPhase.opening:
      case IsolatedTaskPhase.opened:
        return (null, null, const []);
      case IsolatedTaskPhase.failed when launch.canStartAfterFailedSetup:
        // The copy exists: start in it anyway, or remove it. Running the
        // setup again has no server contract, so it is not offered.
        return (
          KitAction(
            key: const Key('isolated-task-start-anyway'),
            label: l10n.isolatedTaskStartAnyway,
            onPressed: () => unawaited(launch.open(acceptFailedSetup: true)),
          ),
          null,
          [
            KitAction(
              key: const Key('isolated-task-remove'),
              label: l10n.isolatedTaskRemove,
              icon: AppIconography.delete,
              onPressed: () => unawaited(_remove(launch)),
            ),
            close,
          ],
        );
      case IsolatedTaskPhase.failed:
        // Nothing was made: the same request again, with the same words.
        return (
          KitAction(
            key: const Key('isolated-task-try-again'),
            label: l10n.isolatedTaskRetryOpen,
            icon: AppIcons.retry,
            onPressed: _tryAgain,
          ),
          null,
          [close],
        );
      case IsolatedTaskPhase.cancelled:
        return (null, close, const []);
    }
  }
}
