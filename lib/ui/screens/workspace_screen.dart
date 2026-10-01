import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../api/sse.dart';
import '../../domain/return_brief.dart';
import '../../domain/workspace_paths.dart';
import '../../state/team_glance.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/interaction_defaults.dart';
import '../../state/nudges.dart';
import '../../state/orchestration.dart';
import '../../state/attention_feed.dart' show AttentionKind;
import '../../state/session_inventory_cache.dart' show SessionInventoryPreview;
import '../../state/work_row_status_controller.dart';
import '../navigation/attention_landing.dart' show chatLandingPage;
import '../navigation/chat_route.dart';
import '../widgets/default_notices.dart';
import '../widgets/grace_timer.dart';
import '../widgets/last_known_sessions.dart';
import '../widgets/safety_confirms.dart';
import '../widgets/other_servers_panel.dart';
import '../widgets/other_projects_panel.dart';
import '../widgets/phone_server_card.dart' show serverDisplayName;
import '../kit/kit.dart';
import '../widgets/phone_team_setup_strip.dart';
import '../widgets/team_project_strip.dart';
import 'team/projects/team_projects_screen.dart';
import '../kit/scenes/states_scenes.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/relative_time.dart';
import '../widgets/session_menu.dart';
import '../widgets/session_title.dart';
import '../widgets/request_routes.dart';
import '../widgets/older_sessions_pager.dart';
import '../widgets/team_task_row.dart';
import '../widgets/team_discover.dart' show teamPossibleOn;
import 'team_conversation/team_conversation.dart';
import 'team/team_page.dart';
import '../widgets/termux_phone_tools.dart';
import '../widgets/work_status_line.dart';
import '../../termux/bridge.dart';
import 'global_sessions_screen.dart';
import 'isolated_task_sheet.dart';
import 'session_context_screen.dart';
import 'termux_processes_screen.dart';
import 'new_conversation_sheet.dart';
import 'project_folder_actions.dart';
import 'projects_screen.dart';
import 'run_result_screen.dart';
import '../app_theme.dart';
import '../../domain/team_directories.dart';

part 'workspace/workspace_load.dart';
part 'workspace/workspace_build.dart';
part 'workspace/workspace_actions.dart';
part 'workspace/workspace_sheets.dart';
part 'workspace/workspace_widgets.dart';

/// The Work tab (docs/ux-system/map/all.json `workspace`, proposal
/// "redesign"): a kit-only rebuild of today's layout with one New
/// conversation whose chooser ([showNewConversationSheet], slice-P4.5)
/// offers Solo · Team · In a separate copy · On a cloud machine where the
/// server supports each. The rest of the new structure (one "N need you"
/// row to Inbox, the AI Team as a notice until first use) waits for its
/// wave-3 slice.
/// From expanded it is a [KitScreen.twoPane]: the list at the start and the
/// selected conversation beside it.
class WorkspaceScreen extends StatefulWidget {
  final ConnectionController controller;

  /// The server is this phone's own (Termux or in the app): the status line
  /// calls it "OpenCode on this phone" and offers [onRestartServer].
  final bool serverOnThisPhone;

  /// Restarts the phone's server and reconnects; null when this server
  /// cannot be restarted from here.
  final Future<void> Function()? onRestartServer;

  const WorkspaceScreen({
    super.key,
    required this.controller,
    this.serverOnThisPhone = false,
    this.onRestartServer,
  });

  /// Test seam: replaces the phone's leftover-process watcher, so the status
  /// line's "OpenCode has been busy" state can be shown without Termux.
  @visibleForTesting
  static Widget Function(
    BuildContext context,
    Widget Function(BuildContext context, WorkRunawayNotice? notice) builder,
  )?
  debugRunawayWatcher;

  /// The catalog project that owns [directory]: its root or a listed
  /// worktree first, then any project containing it. Shared with the Project
  /// tab so both name the same project for the same folder.
  static WorkspaceProject? projectForDirectory(
    List<WorkspaceProject> projects,
    String directory,
  ) {
    for (final project in projects) {
      if (project.directory == directory ||
          project.worktrees.contains(directory)) {
        return project;
      }
    }
    for (final project in projects) {
      if (ConnectionController.projectContainsDirectory(project, directory)) {
        return project;
      }
    }
    return null;
  }

  @override
  State<WorkspaceScreen> createState() => _WorkspaceScreenState();
}

class _WorkspaceScreenState extends State<WorkspaceScreen> {
  List<WorkspaceProject>? _projects;
  List<WorkspaceInfo> _workspaces = const [];
  String? _projectError;
  String? _workspaceError;
  String? _selectedProjectID;
  String? _selectedWorkspaceID;
  String? _selectedDirectory;
  bool _creating = false;
  int _loadGeneration = 0;
  int _dataRefreshRevision = 0;

  /// Conversations archived in this window whose Undo is still open: hidden
  /// from every list until the archive is committed or undone.
  final Set<String> _pendingArchive = {};

  /// The conversation open in the detail pane (expanded and wider).
  String? _selectedSessionID;

  /// The wide detail pane's landing: the waiting request its card opens on,
  /// or a conversation-menu pick the chat runs once open. [_detailOpen]
  /// counts opens, so a second pick on the same row runs too.
  String? _detailRequestID;
  SessionMenuAction? _detailAction;
  int _detailOpen = 0;

  /// A failed act, said once above the list until dismissed (§4.8: where the
  /// thing is, never a snackbar).
  String? _notice;

  /// A project picked on a fresh connection is being opened.
  bool _selectingInitial = false;

  /// Said once per server after this tab opened a project by itself (the
  /// only one, or the one worked on most recently) instead of asking.
  String? _defaultNotice;

  /// Whether another project could have been opened instead: only then does
  /// the notice offer the way to one.
  bool _defaultCanChange = false;

  /// Whether this server can run an AI Team (a Termux phone asks its
  /// runtime once), for New conversation's Team choice.
  String? _teamAskedFor;
  bool _teamPossible = false;

  /// Opening the saved project was tried and left no folder open: the
  /// folder chooser may show.
  bool _restoreGaveUp = false;

  /// The project folder that is about to open while none is open yet: the
  /// saved one being restored, or the one picked for a fresh connection.
  /// While it is set the header names it and the folder chooser stays away
  /// (work-tab cleanup item 1: the chooser flashed during a restore).
  String? get _pendingDirectory {
    final controller = widget.controller;
    if (controller.directory != null) return null;
    if (_selectingInitial && _selectedDirectory != null) {
      return _selectedDirectory;
    }
    if (_restoreGaveUp) return null;
    return controller.savedProjectDirectory;
  }

  /// The folder the header names: the open one, else the one opening.
  String? get _headerDirectory =>
      widget.controller.directory ?? _pendingDirectory;

  /// What each conversation row is doing (slice-P5.5), for this server and
  /// project only. Rows are observed only while the connection is live; a
  /// dropped connection freezes them as last seen (no live mark), and a new
  /// server or project starts empty.
  final WorkRowStatusController _rowStatus = WorkRowStatusController();
  Object? _rowScope;

  /// Extensions in the part files cannot call [setState] themselves.
  void _set(VoidCallback fn) => setState(fn);

  void _observeRows() {
    final controller = widget.controller;
    final scope = (
      controller,
      controller.profile?.id,
      controller.directory,
      controller.workspace,
    );
    if (scope != _rowScope) {
      _rowScope = scope;
      _rowStatus.clear();
    }
    // Only a live, current transport is evidence; a reconnect starts a new
    // generation and the next live notification re-observes every row.
    _rowStatus.setConnected(controller.isConnected);
    if (!controller.isConnected) return;
    final generation = _rowStatus.generation;
    final now = DateTime.now();
    final active = controller.profile?.id;
    // A failure the connected transport confirmed (a session error), from
    // the same feed the Inbox lists; idleness alone is never Done or Failed.
    final failed = <String>{
      for (final item in controller.attentionFeed.items)
        if (item.profileID == active &&
            item.kind == AttentionKind.failedRun &&
            item.target.taskID == null &&
            item.target.runID == null &&
            item.target.hasConversation)
          item.target.sessionID!,
    };
    for (final session in controller.sessionsById.values) {
      final id = session.id;
      final needsYou =
          controller.permissionsForSession(id).isNotEmpty ||
          controller.questionForSession(id) != null ||
          controller.formForSession(id) != null;
      final busy = controller.busySessions.contains(id);
      _rowStatus.observe(
        id,
        !needsYou && !busy && failed.contains(id)
            ? const WorkRowFacts(phase: WorkRowPhase.failed)
            : WorkRowFacts.chat(busy: busy, needsYou: needsYou),
        observedAt: now,
        generation: generation,
      );
    }
  }

  @override
  void initState() {
    super.initState();
    _observeRows();
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_changed);
    widget.controller.nudges.addListener(_nudgesChanged);
    _load();
  }

  @override
  void didUpdateWidget(WorkspaceScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.controller, widget.controller)) return;
    // Another connection behind the same tab: listen to it and start over.
    oldWidget.controller.removeListener(_changed);
    oldWidget.controller.nudges.removeListener(_nudgesChanged);
    widget.controller.addListener(_changed);
    widget.controller.nudges.addListener(_nudgesChanged);
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _restoreGaveUp = false;
    _selectingInitial = false;
    _selectedSessionID = null;
    _observeRows();
    unawaited(_load());
  }

  void _nudgesChanged() {
    if (mounted) setState(() {});
  }

  bool _nudgeCheckQueued = false;

  /// The "pin" tip (UX plan 5.8): once a second project has been used, Work
  /// is no longer one short list and pinning starts to pay. Runs after the
  /// frame because an offer notifies listeners. [canOffer] is false while
  /// Work is behind another tab or route, where a tip would be spent unseen.
  void _queuePinNudge({required bool visible, required bool canOffer}) {
    if (_nudgeCheckQueued) return;
    _nudgeCheckQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _nudgeCheckQueued = false;
      if (!mounted) return;
      final controller = widget.controller;
      final nudges = controller.nudges;
      if (!visible) {
        // One nudge app-wide: a tip left behind must not block the rest.
        nudges.releaseScope(NudgeRegistry.workScope);
        return;
      }
      final directory = controller.directory;
      final profileID = controller.profile?.id ?? '';
      if (directory != null && controller.isConnected) {
        unawaited(
          nudges.noteProjectUsed(profileID: profileID, directory: directory),
        );
      }
      if (canOffer && nudges.secondProjectUsed) {
        nudges.offer(NudgeId.pinConversations, scope: NudgeRegistry.workScope);
      }
    });
  }

  void _changed() {
    if (!mounted) return;
    final shouldReload =
        _dataRefreshRevision != widget.controller.dataRefreshRevision &&
        widget.controller.repository != null;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _observeRows();
    setState(() {});
    if (shouldReload) unawaited(_load());
  }

  static String _workspaceName(WorkspaceInfo workspace) =>
      workspace.branch?.isNotEmpty == true ? workspace.branch! : workspace.name;

  static bool _isPhoneProjectsRoot(String path) =>
      path.replaceAll(RegExp(r'/+$'), '') == '/root/projects';

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  @override
  Widget build(BuildContext context) => _buildScreen(context);

  @override
  void dispose() {
    _loadGeneration++;
    widget.controller.removeListener(_changed);
    widget.controller.nudges.removeListener(_nudgesChanged);
    final nudges = widget.controller.nudges;
    // Deferred: listeners must not rebuild while the tree is being torn down.
    scheduleMicrotask(() => nudges.releaseScope(NudgeRegistry.workScope));
    _rowStatus.dispose();
    super.dispose();
  }
}
