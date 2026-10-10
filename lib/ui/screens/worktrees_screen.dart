import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../navigation/chat_route.dart';
import '../widgets/product_states.dart' show productErrorText;

/// A message about the last act, said above the list until dismissed: a
/// failure, or a worktree that finished (with its next step).
class _WorktreesNotice {
  const _WorktreesNotice(this.message, {required this.tone, this.action});

  final String message;
  final AppStatusTone tone;
  final KitAction? action;
}

/// Worktrees (docs/ux-system/map/all.json `worktrees`, proposal "fix"):
/// the project's main copy and its worktrees, one pinned New worktree
/// primary, a state view when there are none, and on each row its state
/// word ("Current · …", "Preparing…", "Setup failed · …") with the rarer
/// acts on long-press: open, start a conversation there, reset, delete and
/// copy the folder path. Reset and delete confirm and run inside the
/// question, so a failure keeps it open.
class WorktreesScreen extends StatefulWidget {
  final ConnectionController controller;
  final WorkspaceProject project;

  const WorktreesScreen({
    super.key,
    required this.controller,
    required this.project,
  });

  @override
  State<WorktreesScreen> createState() => _WorktreesScreenState();
}

class _WorktreesScreenState extends State<WorktreesScreen> {
  List<WorktreeInfo>? _worktrees;
  final Map<String, WorktreeInfo> _knownWorktrees = {};

  /// Worktrees OpenCode is preparing, with when the wait began.
  final Map<String, DateTime> _preparing = {};
  final Map<String, String> _failures = {};
  final Map<String, Timer> _preparationTimers = {};
  StreamSubscription<EventEnvelope>? _events;
  String? _loadError;
  String? _busyDirectory;
  bool _creating = false;
  int _loadGeneration = 0;
  _WorktreesNotice? _notice;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _events = widget.controller.events.listen(_handleEvent);
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() {
        _loadError = _l10n.e7LibraryOpenCodeIsReconnectingTryAgainShortly;
      });
      return;
    }
    try {
      final worktrees = await repository.listWorktrees(
        projectDirectory: widget.project.directory,
        projectID: widget.project.id,
      );
      if (!mounted || generation != _loadGeneration) return;
      final merged = worktrees.map((worktree) {
        final known = _knownWorktrees[worktree.directory];
        return known == null
            ? worktree
            : WorktreeInfo(
                name: known.name,
                directory: worktree.directory,
                branch: known.branch,
              );
      }).toList()..sort((a, b) => a.name.compareTo(b.name));
      setState(() {
        _worktrees = _dedupeWorktrees(merged);
        _loadError = null;
      });
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _loadError = productErrorText(error));
    }
  }

  void _handleEvent(EventEnvelope event) {
    if (!mounted ||
        (event.type != 'worktree.ready' && event.type != 'worktree.failed')) {
      return;
    }
    final directory = event.directory?.trim() ?? '';
    if (directory.isEmpty || !_knownWorktrees.containsKey(directory)) return;
    _preparationTimers.remove(directory)?.cancel();
    if (event.type == 'worktree.ready') {
      final current = _knownWorktrees[directory]!;
      final name = event.properties['name']?.toString().trim();
      final branch = event.properties['branch']?.toString().trim();
      setState(() {
        _knownWorktrees[directory] = WorktreeInfo(
          name: name?.isNotEmpty == true ? name! : current.name,
          directory: directory,
          branch: branch?.isNotEmpty == true ? branch : current.branch,
        );
        _preparing.remove(directory);
        _failures.remove(directory);
        _replaceKnownWorktree(directory);
        // Ready: the next step is a conversation in it.
        _notice = _WorktreesNotice(
          _l10n.e7LibraryIsReady(_basename(directory)),
          tone: AppStatusTone.ok,
          action: KitAction(
            key: const ValueKey('worktrees-ready-start'),
            label: _l10n.worktreesStartConversation,
            onPressed: () => unawaited(_startConversation(directory)),
          ),
        );
      });
      return;
    }
    final message = event.properties['message']?.toString().trim();
    setState(() {
      _preparing.remove(directory);
      _failures[directory] = message?.isNotEmpty == true
          ? message!
          : _l10n.e7LibraryOpenCodeCouldNotPrepareThisWorktree;
    });
  }

  void _replaceKnownWorktree(String directory) {
    final current = _worktrees;
    final known = _knownWorktrees[directory];
    if (current == null || known == null) return;
    final index = current.indexWhere((item) => item.directory == directory);
    if (index >= 0) current[index] = known;
  }

  List<WorktreeInfo> _dedupeWorktrees(List<WorktreeInfo> worktrees) {
    final byName = <String, WorktreeInfo>{};
    for (final worktree in worktrees) {
      final key = worktree.name.toLowerCase();
      final existing = byName[key];
      if (existing == null ||
          _isExactCurrent(worktree.directory) ||
          (!_isExactCurrent(existing.directory) &&
              _knownWorktrees.containsKey(worktree.directory))) {
        byName[key] = worktree;
      }
    }
    final result = byName.values.toList();
    result.sort((a, b) => a.name.compareTo(b.name));
    return result;
  }

  bool _isExactCurrent(String directory) =>
      widget.controller.directory == directory;

  bool _isCurrentWorktree(WorktreeInfo worktree) {
    final selected = widget.controller.directory;
    if (selected == worktree.directory) return true;
    if (selected == null || selected == widget.project.directory) return false;
    return widget.project.worktrees.contains(selected) &&
        _basename(selected) == worktree.name;
  }

  void _markPreparationUnconfirmed(String directory) {
    if (!mounted || !_preparing.containsKey(directory)) return;
    setState(() {
      _preparing.remove(directory);
      _notice = _WorktreesNotice(
        _l10n.e7LibraryWasCreatedItsSetupStatusIsNot(_basename(directory)),
        tone: AppStatusTone.neutral,
      );
    });
  }

  void _say(Object error) {
    if (!mounted) return;
    setState(
      () => _notice = _WorktreesNotice(
        productErrorText(error),
        tone: AppStatusTone.failure,
      ),
    );
  }

  Future<ServerOperationsGateway> _repository() async {
    final message = _l10n.e7LibraryOpenCodeIsReconnectingTryAgain;
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(message);
  }

  /// `worktrees-create-dialog`: one short entry. The create call runs
  /// inside the dialog, so a failure stays under the field with the name
  /// kept; once created, the row says it is being prepared.
  Future<void> _create() async {
    if (_creating || !widget.controller.capabilities.worktreeCreate) return;
    final l10n = _l10n;
    // The dialog shows its own working state while the create call runs.
    _creating = true;
    WorktreeInfo? created;
    try {
      await showKitInputDialog(
        context,
        title: l10n.e7LibraryNewWorktree,
        label: l10n.e7LibraryNameOptional,
        hint: 'mobile-review',
        helper: l10n.worktreesCreateHelper,
        confirmLabel: l10n.projectFolderCreateAction,
        fieldKey: const ValueKey('worktree-name-field'),
        confirmKey: const ValueKey('confirm-create-worktree'),
        onSubmit: (value) async {
          try {
            created = await (await _repository()).createWorktree(
              projectDirectory: widget.project.directory,
              name: value.trim(),
            );
            return null;
          } catch (error) {
            return productErrorText(error);
          }
        },
      );
      final worktree = created;
      if (worktree == null || !mounted) return;
      setState(() {
        _knownWorktrees[worktree.directory] = worktree;
        _preparing[worktree.directory] = DateTime.now();
        _failures.remove(worktree.directory);
        _notice = null;
        final current = _worktrees ?? <WorktreeInfo>[];
        if (!current.any((item) => item.directory == worktree.directory)) {
          current.add(worktree);
          current.sort((a, b) => a.name.compareTo(b.name));
        }
        _worktrees = current;
      });
      if (worktree.ready) {
        _handleEvent(
          EventEnvelope(
            type: 'worktree.ready',
            properties: {'name': worktree.name, 'branch': worktree.branch},
            directory: worktree.directory,
          ),
        );
        return;
      }
      _preparationTimers[worktree.directory]?.cancel();
      _preparationTimers[worktree.directory] = Timer(
        const Duration(seconds: 45),
        () => _markPreparationUnconfirmed(worktree.directory),
      );
    } finally {
      _creating = false;
    }
  }

  Future<void> _open(String directory) async {
    if (_busyDirectory != null) return;
    if (_preparing.containsKey(directory)) {
      _say(_l10n.e7LibraryWaitForOpenCodeToFinishPreparingThis);
      return;
    }
    setState(() {
      _busyDirectory = directory;
      _notice = null;
    });
    try {
      await widget.controller.selectLocation(directory: directory);
      if (!mounted) return;
      if (widget.controller.directory != directory) {
        throw ProductException(_l10n.e7LibraryOpenCodeDidNotSwitchLocations);
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      _say(error);
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  /// Switches to [directory] and starts a new conversation there.
  Future<void> _startConversation(String directory) async {
    if (_busyDirectory != null) return;
    if (_preparing.containsKey(directory)) {
      _say(_l10n.e7LibraryWaitForOpenCodeToFinishPreparingThis);
      return;
    }
    setState(() {
      _busyDirectory = directory;
      _notice = null;
    });
    try {
      await widget.controller.selectLocation(directory: directory);
      if (!mounted) return;
      if (widget.controller.directory != directory) {
        throw ProductException(_l10n.e7LibraryOpenCodeDidNotSwitchLocations);
      }
      final session = await widget.controller.createSession();
      if (!mounted) return;
      await Navigator.of(context).pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
    } catch (error) {
      _say(error);
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  Future<List<VersionControlFile>?> _inspect(WorktreeInfo worktree) async {
    setState(() {
      _busyDirectory = worktree.directory;
      _notice = null;
    });
    try {
      return await (await _repository()).listWorktreeFileStatuses(
        worktree.directory,
      );
    } catch (error) {
      if (mounted) {
        _say(
          _l10n.e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
            worktree.name,
            productErrorText(error),
          ),
        );
      }
      return null;
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  /// `worktrees-reset-dialog`: the same friction as delete (the typed name
  /// when there are changes); the reset runs inside the question.
  Future<void> _reset(WorktreeInfo worktree) async {
    if (_busyDirectory != null) return;
    final changes = await _inspect(worktree);
    if (!mounted || changes == null) return;
    final l10n = _l10n;
    final done = await showKitConfirm(
      context,
      title: l10n.e7LibraryReset(worktree.name),
      body: l10n.e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes,
      confirmLabel: l10n.e7LibraryResetWorktree,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.restart,
      consequences: _changeConsequences(changes),
      typedName: changes.isEmpty ? null : worktree.name,
      details: [KitTechnicalValue(l10n.worktreesFolder, worktree.directory)],
      confirmKey: const ValueKey('confirm-reset-worktree'),
      action: () async {
        await (await _repository()).resetWorktree(
          projectDirectory: widget.project.directory,
          directory: worktree.directory,
        );
        if (_isCurrentWorktree(worktree)) {
          await widget.controller.selectLocation(
            directory: widget.project.directory,
          );
          await widget.controller.selectLocation(directory: worktree.directory);
        }
      },
    );
    if (!mounted || !done) return;
    setState(
      () => _notice = _WorktreesNotice(
        l10n.e7LibraryResetToTheDefaultBranch(worktree.name),
        tone: AppStatusTone.ok,
      ),
    );
    await _load();
  }

  /// `worktrees-remove-dialog`: the typed name, then the removal inside the
  /// question (switching to the main copy first when it is in use).
  Future<void> _remove(WorktreeInfo worktree) async {
    if (_busyDirectory != null) return;
    final changes = await _inspect(worktree);
    if (!mounted || changes == null) return;
    final l10n = _l10n;
    final done = await showKitConfirm(
      context,
      title: l10n.e7LibraryRemove(worktree.name),
      body: l10n.e7LibraryTheWorktreeDirectoryAndItsGitBranch,
      confirmLabel: l10n.e7LibraryRemovePermanently,
      kind: KitConfirmKind.destructive,
      consequences: _changeConsequences(changes),
      typedName: worktree.name,
      details: [KitTechnicalValue(l10n.worktreesFolder, worktree.directory)],
      confirmKey: const ValueKey('confirm-remove-worktree'),
      action: () async {
        if (_isCurrentWorktree(worktree)) {
          await widget.controller.selectLocation(
            directory: widget.project.directory,
          );
        }
        await (await _repository()).removeWorktree(
          projectDirectory: widget.project.directory,
          directory: worktree.directory,
        );
      },
    );
    if (!mounted || !done) return;
    _preparationTimers.remove(worktree.directory)?.cancel();
    setState(() {
      _knownWorktrees.remove(worktree.directory);
      _preparing.remove(worktree.directory);
      _failures.remove(worktree.directory);
      _notice = _WorktreesNotice(
        l10n.e7LibraryAndItsBranchWereRemoved(worktree.name),
        tone: AppStatusTone.ok,
      );
    });
    await _load();
  }

  /// The changed files a reset or removal loses, as the confirmation's
  /// counted fact; nothing when the worktree is clean.
  List<String> _changeConsequences(List<VersionControlFile> changes) => [
    if (changes.isNotEmpty) _l10n.e7LibraryChangedFilesDetected(changes.length),
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final worktrees = _worktrees;
    final canCreate = widget.controller.capabilities.worktreeCreate;
    final rails = EdgeInsets.symmetric(horizontal: tokens.gutter);
    final notice = _notice;
    final primaryCurrent =
        widget.controller.directory == widget.project.directory;
    return KitScreen(
      width: KitScreenWidth.list,
      // Pull to refresh reloads the list; Try again lives in the error
      // state, so the top bar carries no refresh of its own.
      topBar: KitTopBar(title: l10n.e7LibraryWorktrees),
      loading: worktrees == null && _loadError == null,
      loadingLabel: l10n.e7LibraryRefreshWorktrees,
      // Create is offered only where the create call is contract-proven
      // (`worktreeCreate`); listing, opening and inspection stay available.
      // While the list failed, Try again is the one primary (LAY-12).
      bottom: canCreate && _loadError == null
          ? KitActionBlock(
              primary: KitAction(
                key: const ValueKey('create-worktree'),
                label: l10n.e7LibraryNewWorktree,
                icon: AppIconography.add,
                onPressed: _create,
              ),
            )
          : null,
      body: KitRefresh(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('worktrees-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            if (notice != null)
              Padding(
                padding: rails.add(
                  EdgeInsetsDirectional.only(bottom: tokens.space3),
                ),
                child: KitNotice(
                  key: const ValueKey('worktrees-notice'),
                  message: notice.message,
                  tone: notice.tone,
                  icon: notice.tone == AppStatusTone.failure
                      ? AppIconography.warning
                      : AppIconography.checkCircle,
                  actions: [?notice.action],
                  onDismiss: () => setState(() => _notice = null),
                  dismissLabel: l10n.workspaceDismissNotice,
                ),
              ),
            // One list: the main copy (the project's own folder) first,
            // then its worktrees. The page title names them, so the list
            // has no label or count of its own.
            KitRowGroup(
              key: const ValueKey('worktrees-group'),
              children: [
                _mainCopyRow(context, primaryCurrent),
                if (worktrees != null && _loadError == null)
                  for (final worktree in worktrees) _row(context, worktree),
              ],
            ),
            if (worktrees == null && _loadError == null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: const KitSkeletonRows(count: 3),
              )
            else if (_loadError != null)
              Padding(
                padding: rails.add(
                  EdgeInsetsDirectional.only(top: tokens.sectionGap),
                ),
                child: KitStateView.error(
                  key: const ValueKey('worktrees-load-failed'),
                  title: l10n.worktreesLoadFailedTitle,
                  body: _loadError,
                  size: KitStateSize.inline,
                  retry: KitAction(label: l10n.commonRetry, onPressed: _load),
                ),
              )
            else if (worktrees!.isEmpty)
              Padding(
                padding: rails.add(
                  EdgeInsetsDirectional.only(top: tokens.sectionGap),
                ),
                child: KitStateView(
                  key: const ValueKey('no-worktrees'),
                  size: KitStateSize.inline,
                  icon: AppIconography.branch,
                  title: l10n.e7LibraryNoIsolatedWorktreesYet,
                  // The pinned New worktree is the first step; without the
                  // create call the list says only what will be here.
                  body: l10n.emptyTeachWorktreesMessage,
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// The main copy: the project's own folder, opened by a tap unless it is
  /// the one in use.
  Widget _mainCopyRow(BuildContext context, bool primaryCurrent) {
    final l10n = _l10n;
    return KitRow(
      key: const ValueKey('primary-worktree'),
      leading: KitRowIcon(AppIconography.projects, current: primaryCurrent),
      title: _basename(widget.project.directory),
      supporting: TextSpan(
        children: [
          if (primaryCurrent) _stateSpan(context, l10n.e7SharedCurrent),
          TextSpan(text: l10n.worktreesMainCopy),
        ],
      ),
      trailing: _busyDirectory == widget.project.directory
          ? const KitTaskMark(state: KitTaskState.working)
          : primaryCurrent
          ? null
          : const KitChevron(),
      onTap: primaryCurrent || _busyDirectory != null
          ? null
          : () => _open(widget.project.directory),
      menuLabel: l10n.e7LibraryWorktreeActions,
      menu: [
        if (!primaryCurrent)
          KitMenuItem(
            label: l10n.globalSessionsOpen,
            icon: AppIconography.externalLink,
            onSelected: () => _open(widget.project.directory),
          ),
        KitMenuItem.copy(
          label: l10n.worktreesCopyFolder,
          text: () => widget.project.directory,
        ),
      ],
    );
  }

  static TextSpan _stateSpan(BuildContext context, String word) => TextSpan(
    text: '$word · ',
    style: KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    ),
  );

  Widget _row(BuildContext context, WorktreeInfo worktree) {
    final l10n = _l10n;
    final current = _isCurrentWorktree(worktree);
    final since = _preparing[worktree.directory];
    final preparing = since != null;
    final failure = _failures[worktree.directory];
    final busy = _busyDirectory == worktree.directory;
    final resetAvailable = widget.controller.capabilities.worktreeReset;
    final where = worktree.branch?.isNotEmpty == true
        ? worktree.branch!
        : worktree.directory;
    return KitSince(
      key: ValueKey('worktree-${worktree.directory}'),
      since: since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
        // The state word leads (STATE-9); a long setup says how long it
        // has been going.
        final supporting = TextSpan(
          children: [
            if (failure != null) ...[
              _stateSpan(context, l10n.worktreesSetupFailedWord),
              TextSpan(text: failure),
            ] else if (preparing) ...[
              TextSpan(
                text: l10n.e7LibraryPreparingFilesAndProjectTasks,
                style: KitText.styleOf(
                  context,
                  KitTextRole.label,
                  tone: KitTextTone.primary,
                ),
              ),
              if (status.phase == KitSincePhase.slow)
                TextSpan(
                  text: ' · ${KitSince.waitingLabel(context, status.elapsed)}',
                ),
            ] else ...[
              if (current) _stateSpan(context, l10n.e7SharedCurrent),
              TextSpan(text: where),
            ],
          ],
        );
        return KitRow(
          leading: preparing || busy
              ? SizedBox.square(
                  dimension: KitTokens.of(context).iconTileSize,
                  child: const Center(
                    child: KitTaskMark(state: KitTaskState.working),
                  ),
                )
              : failure != null
              ? KitRow.icon(context, AppIconography.error)
              : KitRowIcon(AppIconography.branch, current: current),
          title: worktree.name,
          supporting: supporting,
          supportingMaxLines: 2,
          trailing: busy || preparing || current ? null : const KitChevron(),
          onTap: busy || preparing || current
              ? null
              : () => _open(worktree.directory),
          menuLabel: l10n.e7LibraryWorktreeActions,
          menu: busy
              ? const []
              : [
                  if (!current && !preparing && failure == null)
                    KitMenuItem(
                      key: const ValueKey('worktree-menu-open'),
                      label: l10n.globalSessionsOpen,
                      icon: AppIconography.externalLink,
                      onSelected: () => _open(worktree.directory),
                    ),
                  if (!preparing && failure == null)
                    KitMenuItem(
                      key: const ValueKey('worktree-menu-start'),
                      label: l10n.worktreesStartConversation,
                      icon: AppIconography.chat,
                      onSelected: () =>
                          unawaited(_startConversation(worktree.directory)),
                    ),
                  KitMenuItem.copy(
                    key: const ValueKey('worktree-menu-copy'),
                    label: l10n.worktreesCopyFolder,
                    text: () => worktree.directory,
                  ),
                  if (resetAvailable && !preparing && failure == null)
                    KitMenuItem(
                      key: const ValueKey('worktree-menu-reset'),
                      label: l10n.e7LibraryReset2,
                      icon: AppIconography.restart,
                      destructive: true,
                      onSelected: () => unawaited(_reset(worktree)),
                    ),
                  KitMenuItem(
                    key: const ValueKey('worktree-menu-remove'),
                    label: l10n.promptStashDelete,
                    icon: AppIconography.delete,
                    destructive: true,
                    onSelected: () => unawaited(_remove(worktree)),
                  ),
                ],
        );
      },
    );
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  @override
  void dispose() {
    _loadGeneration++;
    _events?.cancel();
    for (final timer in _preparationTimers.values) {
      timer.cancel();
    }
    super.dispose();
  }
}
