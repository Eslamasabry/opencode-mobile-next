import 'scheduling/schedule_routes.dart';
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../domain/chat_feed.dart' show isTemporaryProjectDirectory;
import '../../domain/server_gateway.dart'
    show ProductException, ServerCapabilities, WorkspaceProject;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/review_handoff.dart' show ReviewHandoffSession;
import '../app_iconography.dart';
import '../navigation/last_project.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_chip.dart';
import '../kit/kit_buttons.dart' show KitAction;
import '../kit/kit_capability_explainer.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_page_heading.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart' show KitChevron, KitRowMenu;
import '../kit/kit_screen.dart';
import '../kit/kit_scrollbar.dart' show KitScrollArea;
import '../kit/kit_state_view.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../widgets/product_states.dart';
import 'development_services_screen.dart';
import 'files_screen.dart';
import 'managed_workspaces_screen.dart';
import 'project_health_screen.dart';
import 'project_folder_actions.dart';
import 'terminal_screen.dart';
import 'worktrees_screen.dart';

/// A tool scoped to the current project, in the order the Project tab lists
/// them. Development services and cloud environments came over from the
/// retired Manage project page (slice-P3.11a), so the project's tools have
/// one landing.
enum ProjectTool {
  files,
  changes,
  terminal,
  health,
  worktrees,
  services,
  workspaces,
  search,
}

/// Asks the shell for a tool that lives inside the Project tab (Files and its
/// search), from a search result on any route.
class OpenProjectToolIntent extends Intent {
  const OpenProjectToolIntent(this.tool);
  final ProjectTool tool;
}

/// Opens a Project tool that is a screen of its own. Shared by the tab's rows
/// and by search, so both doors do exactly the same thing. Files and Search
/// are not here: they live inside the tab ([OpenProjectToolIntent]).
Future<void> openProjectTool(
  BuildContext context,
  ConnectionController controller,
  ProjectTool tool,
) async {
  final l10n = _l10n(context);
  try {
    switch (tool) {
      case ProjectTool.files || ProjectTool.search:
        return;
      case ProjectTool.changes:
        final prompt = await pushWorkingTreeReview(context, controller);
        if (context.mounted) await deliverReviewPrompt(context, prompt);
      case ProjectTool.terminal:
        await pushKitPage<void>(
          context,
          (_) => TerminalPage(controller: controller),
        );
      case ProjectTool.health:
        final repository = await controller.prepareActionRepository();
        if (!context.mounted) return;
        if (repository == null) {
          throw ProductException(l10n.e7WorkspaceDisconnected);
        }
        await pushKitPage<void>(
          context,
          (_) => ProjectHealthScreen(
            repository: repository,
            repositoryResolver: controller.prepareActionRepository,
            capabilities: controller.capabilities,
          ),
        );
      case ProjectTool.services:
        if (controller.directory?.isNotEmpty != true) {
          throw ProductException(l10n.e7LibraryNoProjectFolderIsOpenChooseOne);
        }
        await pushKitPage<void>(
          context,
          (_) => DevelopmentServicesScreen(controller: controller),
        );
      case ProjectTool.worktrees || ProjectTool.workspaces:
        final repository = await controller.prepareActionRepository();
        if (!context.mounted) return;
        if (repository == null) {
          throw ProductException(l10n.e7WorkspaceDisconnected);
        }
        final directory = controller.directory;
        final project = directory == null
            ? null
            : projectForDirectory(await repository.listProjects(), directory);
        if (!context.mounted) return;
        if (project == null) {
          // A folder is open, but OpenCode does not track it as a git
          // project with a commit (a new folder, or no git): say that.
          throw ProductException(
            directory?.isNotEmpty == true
                ? l10n.projectToolsNeedGitCommit
                : l10n.e7LibraryNoProjectFolderIsOpenChooseOne,
          );
        }
        await pushKitPage<void>(
          context,
          (_) => tool == ProjectTool.worktrees
              ? WorktreesScreen(controller: controller, project: project)
              : ManagedWorkspacesScreen(
                  controller: controller,
                  project: project,
                ),
        );
    }
  } catch (error) {
    if (context.mounted) showProductError(context, error);
  }
}

/// The catalog project that owns [directory]: its root or a listed
/// worktree first, then any project containing it.
WorkspaceProject? projectForDirectory(
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

/// Opens a project's Files as a page of its own: the phone's door to the
/// project tools now that there is no navigation bar (owner, 2026-10-20).
/// The conversation's project chip, Search ("Files", a project's name) and
/// Ctrl/Cmd+2 on a phone land here; Back returns to where it was opened.
///
/// [directory] is the project to show (the conversation's); null keeps the
/// one Files would show anyway (the last used for a chat, else the open
/// one). [openFiles] opens the browser at once, [focusSearch] its search.
/// The file extras come from a conversation: attach and review prompts go
/// to its composer.
Future<void> openProjectFiles(
  BuildContext context,
  ConnectionController controller, {
  String? directory,
  bool openFiles = false,
  bool focusSearch = false,
  ProjectFileAttachment? onAttachFile,
  ProjectReviewPrompt? onReviewPrompt,
  ReviewHandoffSession? handoff,
}) => pushKitPage<void>(
  context,
  (_) => ProjectHub(
    key: const ValueKey('project-files-page'),
    controller: controller,
    page: true,
    directory: directory,
    openFilesOnStart: openFiles || focusSearch,
    focusSearchOnStart: focusSearch,
    onAttachFile: onAttachFile,
    onReviewPrompt: onReviewPrompt,
    handoff: handoff,
  ),
);

/// Lets the shell offer system Back to the Project tab before leaving it.
class ProjectHubBackController {
  bool Function()? _handler;
  bool handleBack() => _handler?.call() ?? false;

  VoidCallback? _hub;

  /// Back to the hub's rows from Files (the Project tab tapped again). The
  /// browser keeps its folder and scroll for the next visit.
  void showHub() => _hub?.call();
}

/// The project's Files page (vision 2026-10-20, "just the files"): the
/// large title, the project in quiet text under it (tap to switch), then
/// the project's files and folders, one plain row each, an uncommitted
/// change marked on its name. Search and Terminal are quiet icons in the
/// bar; Changes and every other tool that acts on the project are in its ⋯,
/// each gated as before. The project is the one last used for a chat, else
/// the open one. With no project the page says so and offers the chooser
/// (map project-hub, proposal "fix"). A server that cannot browse files
/// keeps the tools as rows, and the shell drops the whole destination when
/// no tool is left (UX plan 5.1, rule 7).
class ProjectHub extends StatefulWidget {
  const ProjectHub({
    super.key,
    required this.controller,
    this.focusSearchSignal,
    this.openFilesSignal,
    this.backController,
    this.page = false,
    this.directory,
    this.openFilesOnStart = false,
    this.focusSearchOnStart = false,
    this.onAttachFile,
    this.onReviewPrompt,
    this.handoff,
  });

  final ConnectionController controller;

  /// A page of its own ([openProjectFiles]) rather than the rail's Files
  /// destination: a top bar with Back, and Browse files opens the browser as
  /// its own page, so system Back (and predictive back) walks out of it.
  final bool page;

  /// The project to show on open; null follows the last used one.
  final String? directory;

  /// Kept for the doors that asked for the browser: the page is the files.
  final bool openFilesOnStart;

  /// Opens the page with its search field open and focused.
  final bool focusSearchOnStart;

  /// From a conversation: what the browser's Attach and review prompts do.
  final ProjectFileAttachment? onAttachFile;
  final ProjectReviewPrompt? onReviewPrompt;
  final ReviewHandoffSession? handoff;

  /// Bumped by the shell's Ctrl/Cmd+F while this tab is showing.
  final ValueListenable<int>? focusSearchSignal;

  /// Bumped by the shell when a search result means Files. The page is the
  /// files, so there is nothing more to open.
  final ValueListenable<int>? openFilesSignal;
  final ProjectHubBackController? backController;

  /// Changes and Search ride on Files' gate: the working-tree review and the
  /// file finder are served by the same file API, and Files was their only
  /// door before this tab existed. Search stays a tool (the app's search
  /// opens it) although the hub lists it inside Files' own field.
  static List<ProjectTool> toolsFor(ServerCapabilities capabilities) => [
    if (capabilities.fileBrowsing) ProjectTool.files,
    if (capabilities.fileBrowsing) ProjectTool.changes,
    if (capabilities.terminal) ProjectTool.terminal,
    if (capabilities.projectManagement) ProjectTool.health,
    if (capabilities.projectManagement || capabilities.worktreeBrowsing)
      ProjectTool.worktrees,
    if (capabilities.developmentServices) ProjectTool.services,
    if (capabilities.projectManagement && capabilities.managedWorkspaces)
      ProjectTool.workspaces,
    if (capabilities.fileBrowsing) ProjectTool.search,
  ];

  static bool isAvailable(ServerCapabilities capabilities) =>
      toolsFor(capabilities).isNotEmpty;

  @override
  State<ProjectHub> createState() => _ProjectHubState();
}

class _ProjectHubState extends State<ProjectHub> {
  final _filesBack = FilesBackController();
  final _focusFilesSearch = ValueNotifier<int>(0);

  /// Find (Ctrl/Cmd+F) was asked for with no project open: the page shows
  /// the server's folder with its search field instead of the chooser.
  bool _findAsked = false;
  bool _opening = false;

  /// The live counts for the ⋯ menu ("Changes · 3 files") and the Terminal
  /// row of a server without file browsing ("1 running"): null until read,
  /// and left out when a read fails (the tool's own page says why). No
  /// timer polls them: they are read when the page opens, when the project
  /// changes, when a tool closes and when the last running conversation
  /// finishes, since that is when files change.
  int? _changedFiles;
  int? _runningTerminals;
  int _statusGeneration = 0;
  (String?, int, String?)? _statusScope;
  bool _wasBusy = false;

  /// The order of the tools rows on a server without file browsing.
  static const _hubOrder = [
    ProjectTool.changes,
    ProjectTool.files,
    ProjectTool.terminal,
    ProjectTool.health,
    ProjectTool.worktrees,
    ProjectTool.services,
    ProjectTool.workspaces,
  ];

  @override
  void initState() {
    super.initState();
    widget.focusSearchSignal?.addListener(_searchFiles);
    widget.backController
      ?.._handler = _handleBack
      .._hub = _showHub;
    widget.controller.addListener(_followConnection);
    _wasBusy = widget.controller.busySessions.isNotEmpty;
    _followConnection();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _followLastProject();
      if (!mounted) return;
      if (widget.focusSearchOnStart) _searchFiles();
    });
  }

  /// Files shows the project it was opened for, else the one the person last
  /// used for a chat: when that is not the open location, the location moves
  /// to it once, on first show.
  void _followLastProject() {
    if (!mounted) return;
    final controller = widget.controller;
    final last = widget.directory ?? lastUsedProjectOf(controller);
    if (last == null || last == controller.directory) return;
    if (!controller.isConnected || !controller.capabilities.projectManagement) {
      return;
    }
    unawaited(controller.selectLocation(directory: last));
  }

  /// Reads the live lines again when the server, location or project
  /// changed, or when the last busy conversation went idle.
  void _followConnection() {
    if (!mounted) return;
    final controller = widget.controller;
    final scope = (
      controller.profile?.id,
      controller.locationRevision,
      controller.directory,
    );
    final busy = controller.busySessions.isNotEmpty;
    final finished = _wasBusy && !busy;
    _wasBusy = busy;
    if (scope != _statusScope) {
      _statusScope = scope;
      _changedFiles = null;
      _runningTerminals = null;
      unawaited(_readStatus());
    } else if (finished) {
      unawaited(_readStatus());
    }
  }

  Future<void> _readStatus() async {
    final controller = widget.controller;
    final generation = ++_statusGeneration;
    final repository = controller.repository;
    final directory = controller.directory;
    if (!controller.isConnected ||
        repository == null ||
        directory == null ||
        directory.isEmpty) {
      return;
    }
    final capabilities = controller.capabilities;
    bool current() =>
        mounted &&
        generation == _statusGeneration &&
        identical(controller.repository, repository);
    Future<int?> count(Future<int> Function() read) async {
      try {
        return await read();
      } catch (_) {
        // The row keeps its title only; the tool itself says what failed.
        return null;
      }
    }

    final results = await Future.wait([
      capabilities.fileBrowsing
          ? count(() async => (await repository.listFileStatuses()).length)
          : Future<int?>.value(),
      capabilities.terminal
          ? count(
              () async => (await repository.listTerminals())
                  .where((terminal) => terminal.running)
                  .length,
            )
          : Future<int?>.value(),
    ]);
    if (!current()) return;
    setState(() {
      _changedFiles = results[0];
      _runningTerminals = results[1];
    });
  }

  @override
  void didUpdateWidget(ProjectHub oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusSearchSignal != widget.focusSearchSignal) {
      oldWidget.focusSearchSignal?.removeListener(_searchFiles);
      widget.focusSearchSignal?.addListener(_searchFiles);
    }
    if (oldWidget.backController != widget.backController) {
      oldWidget.backController
        ?.._handler = null
        .._hub = null;
      widget.backController
        ?.._handler = _handleBack
        .._hub = _showHub;
    }
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_followConnection);
      widget.controller.addListener(_followConnection);
      _statusScope = null;
      _followConnection();
    }
  }

  @override
  void dispose() {
    widget.focusSearchSignal?.removeListener(_searchFiles);
    widget.backController
      ?.._handler = null
      .._hub = null;
    widget.controller.removeListener(_followConnection);
    _focusFilesSearch.dispose();
    super.dispose();
  }

  bool get _canBrowse => widget.controller.capabilities.fileBrowsing;

  /// The shell's Back while Files shows: up a folder, out of the search.
  bool _handleBack() => _canBrowse && _filesBack.handleBack();

  /// The rail's Files tapped again: nothing to go back to, the files are
  /// the page.
  void _showHub() {}

  void _searchFiles() {
    if (!mounted || !_canBrowse) return;
    final open = widget.controller.directory;
    final none =
        open == null || open.isEmpty || isTemporaryProjectDirectory(open);
    if (none && !widget.openFilesOnStart && !_findAsked) {
      // Find asks for the files themselves: with no project chosen they are
      // the server's own folder, as from /files; the field exists from the
      // next frame.
      setState(() => _findAsked = true);
      WidgetsBinding.instance.addPostFrameCallback((_) => _searchFiles());
      return;
    }
    _focusFilesSearch.value++;
  }

  /// Back to the conversation the page was opened from, after "Ask about
  /// this file" put the file in its composer: everything above that
  /// conversation's route (the reader, this page) closes.
  void _backToConversation() {
    final page = ModalRoute.of(context);
    if (page == null) return;
    final navigator = Navigator.of(context);
    navigator.popUntil((route) => route == page);
    if (page.isCurrent && navigator.canPop()) navigator.pop();
  }

  /// One tool at a time: the rows that first ask the server something would
  /// otherwise stack two screens on a double tap.
  Future<void> _guard(Future<void> Function() open) async {
    if (_opening) return;
    _opening = true;
    try {
      await open();
    } finally {
      _opening = false;
    }
  }

  /// Opens [tool]; coming back reads the live lines again, since Changes
  /// and Terminal are where they change.
  Future<void> _openTool(ProjectTool tool) => _guard(() async {
    await openProjectTool(context, widget.controller, tool);
    if (mounted) unawaited(_readStatus());
  });

  /// Chooses a project in the same "Open a project" sheet the New
  /// conversation chip uses (New project, Search this phone, Opened before).
  /// Opening a folder moves the location to it; it also becomes the last
  /// used project, so Files and the next chat start there.
  Future<void> _chooseProject() => _guard(() async {
    final directory = await ProjectFolderActions.openFolder(
      context,
      widget.controller,
    );
    if (directory != null) {
      rememberLastUsedProject(widget.controller, directory);
    }
  });

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final open = widget.controller.directory;
      // A temporary folder is never a project: the page reads as having none.
      final directory = isTemporaryProjectDirectory(open) ? null : open;
      final none = directory == null || directory.isEmpty;
      // Asked for the files themselves (/files, All files, Search's file
      // browser): with no project chosen they are the server's own folder,
      // as the browser always showed them; otherwise the chooser.
      if (none && !(_canBrowse && (widget.openFilesOnStart || _findAsked))) {
        return _noProject(context);
      }
      if (!_canBrowse) return _hubBody(context);
      return _files(context, directory ?? '');
    },
  );

  /// The page is the project's files (vision 2026-10-20, "just the
  /// files"): the large title and the project under it, then one row per
  /// entry. Search and Terminal are quiet icons in the bar; every other
  /// tool is in its ⋯.
  Widget _files(BuildContext context, String directory) {
    final l10n = _l10n(context);
    final capabilities = widget.controller.capabilities;
    final files = FilesScreen(
      key: const ValueKey('project-files-browser'),
      controller: widget.controller,
      onAttachFile: widget.onAttachFile,
      onReviewPrompt: widget.onReviewPrompt,
      handoff: widget.handoff,
      focusSearchSignal: _focusFilesSearch,
      // As the rail's destination the shell asks for Back; as a page the
      // browser's own PopScope walks up the folders first.
      backController: widget.page ? null : _filesBack,
      onAsked: widget.page && widget.handoff != null
          ? _backToConversation
          : null,
      topBar: (path, search, view) => KitTopBar(
        key: const ValueKey('project-files-page-bar'),
        // The large title under the bar names the page.
        title: '',
        exit: widget.page ? KitTopBarExit.auto : KitTopBarExit.none,
        actions: [
          search,
          if (capabilities.terminal)
            KitAction(
              key: const ValueKey('project-files-terminal'),
              label: l10n.libraryTerminalTitle,
              icon: AppIconography.terminal,
              onPressed: () => unawaited(_openTool(ProjectTool.terminal)),
            ),
        ],
        menuKey: const ValueKey('project-hub-menu'),
        menu: [..._toolsMenu(l10n, directory), ...view],
      ),
      header: (path) => _heading(context, directory, path),
    );
    // As a page it is its own screen (ground, live background); as the
    // rail's destination the shell's screen holds it.
    return widget.page ? KitScreen(page: true, body: files) : files;
  }

  /// The page's ⋯: Changes first, named with its count, then the project's
  /// other tools, then the project itself. Each is gated as its row was.
  List<KitMenuItem> _toolsMenu(AppLocalizations l10n, String directory) {
    final capabilities = widget.controller.capabilities;
    final available = ProjectHub.toolsFor(capabilities);
    final canSwitch = capabilities.projectManagement;
    KitMenuItem tool(ProjectTool tool, String label, IconData icon) =>
        KitMenuItem(
          key: ValueKey('project-hub-${tool.name}'),
          label: label,
          icon: icon,
          group: 'tools',
          onSelected: () => unawaited(_openTool(tool)),
        );
    return [
      if (available.contains(ProjectTool.changes))
        KitMenuItem(
          key: const ValueKey('project-hub-changes'),
          label: l10n.filesMenuChanges(_changedFiles ?? 0),
          icon: AppIconography.review,
          group: 'tools',
          onSelected: () => unawaited(_reviewChanges()),
        ),
      if (available.contains(ProjectTool.health))
        tool(
          ProjectTool.health,
          l10n.e7LibraryProjectHealth,
          AppIconography.diagnostics,
        ),
      if (available.contains(ProjectTool.worktrees))
        tool(
          ProjectTool.worktrees,
          l10n.e7LibraryWorktrees,
          AppIconography.branch,
        ),
      if (available.contains(ProjectTool.services))
        tool(
          ProjectTool.services,
          l10n.servicesTitle,
          AppIconography.processor,
        ),
      if (available.contains(ProjectTool.workspaces))
        tool(
          ProjectTool.workspaces,
          l10n.e7LibraryManagedWorkspaces,
          AppIconography.cloud,
        ),
      if (canSchedulePrompts(widget.controller))
        KitMenuItem(
          label: l10n.scheduleTitle,
          icon: Icons.schedule,
          group: 'project',
          onSelected: () =>
              unawaited(openScheduledPrompts(context, widget.controller)),
        ),
      if (canSwitch)
        KitMenuItem(
          key: const ValueKey('project-hub-switch'),
          label: l10n.e7LibrarySwitchProject,
          icon: AppIconography.swap,
          group: 'project',
          onSelected: () => unawaited(_chooseProject()),
        ),
      if (directory.isNotEmpty)
        KitMenuItem.copy(
          key: const ValueKey('project-hub-copy-path'),
          label: l10n.projectHubCopyFolderPath,
          text: () => directory,
        ),
    ];
  }

  /// Changes from the page: the working tree's review, and what the
  /// person writes there goes to the conversation the page came from.
  Future<void> _reviewChanges() => _guard(() async {
    final prompt = await pushWorkingTreeReview(
      context,
      widget.controller,
      handoff: widget.handoff,
    );
    if (!mounted) return;
    await deliverReviewPrompt(context, prompt, widget.onReviewPrompt);
    if (mounted) unawaited(_readStatus());
  });

  /// The large title (the page at the project's root, else the open
  /// folder's name) and one quiet line under it: the project, which opens
  /// the project chooser where the server can switch; inside a folder,
  /// where that folder is.
  Widget _heading(BuildContext context, String directory, String path) {
    final l10n = _l10n(context);
    final project = _basename(directory);
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    final title = parts.isEmpty ? l10n.shellTabFiles : parts.last;
    final where = parts.length < 2
        ? project
        : [project, ...parts.take(parts.length - 1)].join(' / ');
    final canSwitch =
        parts.isEmpty && widget.controller.capabilities.projectManagement;
    return KitPageHeading(
      title: title,
      titleKey: const ValueKey('project-hub-title'),
      namesRoute: widget.page,
      // No project chosen (the server's own folder): no project line.
      line: directory.isEmpty
          ? null
          : parts.isEmpty
          ? KitBidi.auto(project)
          : KitBidi.ltr(where),
      lineKey: const ValueKey('project-files-project'),
      lineTapKey: const ValueKey('project-hub-context'),
      onLineTap: canSwitch ? () => unawaited(_chooseProject()) : null,
      lineTapLabel: canSwitch ? l10n.filesProjectSwitchLabel(project) : null,
    );
  }

  /// No project open: the page's title, then one calm state that says so
  /// and offers the chooser as the one main action.
  Widget _noProject(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    return KitScreen(
      width: KitScreenWidth.list,
      topBar: widget.page
          ? const KitTopBar(key: ValueKey('project-files-page-bar'), title: '')
          : null,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // As the rail's destination the title keeps the gap a bar
          // would give it.
          if (!widget.page) SizedBox(height: tokens.space3),
          KitPageHeading(
            title: l10n.shellTabFiles,
            titleKey: const ValueKey('project-hub-title'),
            namesRoute: widget.page,
          ),
          Expanded(
            child: KitStateView.calm(
              key: const ValueKey('project-hub-context'),
              icon: AppIconography.folderOpen,
              title: l10n.e7LibraryNoProjectSelected,
              body: l10n.kitCapProjectOpenWhy,
              primary: KitAction(
                key: const ValueKey('project-hub-choose-project'),
                label: l10n.kitCapProjectOpenEnable,
                onPressed: () => unawaited(_chooseProject()),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _hubBody(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final open = widget.controller.directory;
    // A temporary folder is never a project: the tab reads as having none.
    final directory = isTemporaryProjectDirectory(open) ? null : open;
    final available = ProjectHub.toolsFor(widget.controller.capabilities);
    final tools = [
      for (final tool in _hubOrder)
        // Terminal stays listed on a server without one, dimmed with the
        // reason, while the tab has other tools to show (slice-P3.11a).
        if (available.contains(tool) ||
            (tool == ProjectTool.terminal && available.isNotEmpty))
          tool,
    ];
    return KitScreen(
      width: KitScreenWidth.list,
      // As a page the bar names it and has Back; as the rail's destination
      // the large title below does.
      topBar: widget.page
          ? KitTopBar(
              key: const ValueKey('project-files-page-bar'),
              title: l10n.shellTabFiles,
            )
          : null,
      body: KitScrollArea(
        builder: (scrollController) => ListView(
          controller: scrollController,
          key: const ValueKey('project-hub'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space3,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            if (!widget.page) _filesTitle(context),
            if (directory != null && directory.isNotEmpty)
              _projectHeader(context, directory)
            else
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.gutter,
                  end: tokens.gutter,
                  bottom: tokens.sectionGap,
                ),
                child: KitStateView.missing(
                  key: const ValueKey('project-hub-context'),
                  capability: 'project.open',
                  icon: AppIconography.folderOpen,
                  title: l10n.e7LibraryNoProjectSelected,
                  why: l10n.kitCapProjectOpenWhy,
                  enableKey: const ValueKey('project-hub-choose-project'),
                  enable: KitAction(
                    label: l10n.kitCapProjectOpenEnable,
                    onPressed: () => unawaited(_chooseProject()),
                  ),
                ),
              ),
            // The tools act on a project: with none open the chooser above
            // is the only thing on the tab.
            if (tools.isNotEmpty && directory != null && directory.isNotEmpty)
              KitRowGroup(
                children: [
                  for (final tool in tools) _toolRow(context, l10n, tool),
                ],
              ),
          ],
        ),
      ),
    );
  }

  /// The tab's large title.
  Widget _filesTitle(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.space1,
      ),
      child: Semantics(
        header: true,
        child: KitText(
          _l10n(context).shellTabFiles,
          key: const ValueKey('project-hub-title'),
          role: KitTextRole.largeTitle,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
    );
  }

  /// The project chip under the title: its name, and a tap that opens the
  /// project chooser. The folder path is one Copy away in the menu beside
  /// the chip.
  Widget _projectHeader(BuildContext context, String directory) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final canSwitch = widget.controller.capabilities.projectManagement;
    final name = KitBidi.auto(_basename(directory));
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.sectionGap,
      ),
      // spaceBetween, not a Spacer: a Spacer beside the loose Flexible only
      // takes half the free room, which left the menu stranded mid-row.
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Flexible(
            child: Semantics(
              container: true,
              hint: canSwitch ? l10n.e7LibrarySwitchProject : null,
              child: canSwitch
                  ? KitChip.action(
                      key: const ValueKey('project-hub-context'),
                      icon: AppIconography.folderOpen,
                      label: name,
                      onPressed: () => unawaited(_chooseProject()),
                    )
                  : KitChip(
                      key: const ValueKey('project-hub-context'),
                      icon: AppIconography.folderOpen,
                      label: name,
                    ),
            ),
          ),
          KitRowMenu(
            key: const ValueKey('project-hub-menu'),
            items: [
              if (canSchedulePrompts(widget.controller))
                KitMenuItem(
                  label: l10n.scheduleTitle,
                  icon: Icons.schedule,
                  onSelected: () => unawaited(
                    openScheduledPrompts(context, widget.controller),
                  ),
                ),
              if (canSwitch)
                KitMenuItem(
                  key: const ValueKey('project-hub-switch'),
                  label: l10n.e7LibrarySwitchProject,
                  icon: AppIconography.swap,
                  onSelected: () => unawaited(_chooseProject()),
                ),
              KitMenuItem.copy(
                key: const ValueKey('project-hub-copy-path'),
                label: l10n.projectHubCopyFolderPath,
                text: () => directory,
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// Changes and Terminal carry a live line once read ("3 files changed",
  /// "1 running"); the rest are titles, and Project health keeps one line
  /// saying what it checks, since its name does not.
  Widget _toolRow(
    BuildContext context,
    AppLocalizations l10n,
    ProjectTool tool,
  ) => switch (tool) {
    // Only listed where the server cannot browse files (the page is the
    // files otherwise), so these two never show; kept for the switch.
    ProjectTool.files || ProjectTool.search => const SizedBox.shrink(),
    ProjectTool.changes => _row(
      tool,
      icon: AppIconography.review,
      title: l10n.readerUiChanges,
      subtitle: switch (_changedFiles) {
        final count? => l10n.projectHubChangedFiles(count),
        null => null,
      },
      onTap: () => _openTool(tool),
    ),
    ProjectTool.terminal when !widget.controller.capabilities.terminal =>
      KitRow.unavailable(
        key: ValueKey('project-hub-${tool.name}'),
        leading: KitRow.icon(context, AppIconography.terminal),
        title: l10n.libraryTerminalTitle,
        reason: KitCapabilityExplainer.whyOf(context, 'flag:terminal'),
        capability: 'flag:terminal',
      ),
    ProjectTool.terminal => _row(
      tool,
      icon: AppIconography.terminal,
      title: l10n.libraryTerminalTitle,
      subtitle: switch (_runningTerminals) {
        final count? when count > 0 => l10n.projectHubTerminalsRunning(count),
        _ => null,
      },
      onTap: () => _openTool(tool),
    ),
    ProjectTool.health => _row(
      tool,
      icon: AppIconography.diagnostics,
      title: l10n.e7LibraryProjectHealth,
      subtitle: l10n.projectHubHealthSubtitle,
      onTap: () => _openTool(tool),
    ),
    ProjectTool.worktrees => _row(
      tool,
      icon: AppIconography.branch,
      title: l10n.e7LibraryWorktrees,
      onTap: () => _openTool(tool),
    ),
    ProjectTool.services => _row(
      tool,
      icon: AppIconography.processor,
      title: l10n.servicesTitle,
      subtitle: l10n.servicesSubtitle,
      onTap: () => _openTool(tool),
    ),
    ProjectTool.workspaces => _row(
      tool,
      icon: AppIconography.cloud,
      title: l10n.e7LibraryManagedWorkspaces,
      onTap: () => _openTool(tool),
    ),
  };

  Widget _row(
    ProjectTool tool, {
    required IconData icon,
    required String title,
    required VoidCallback onTap,
    String? subtitle,
  }) => KitRow(
    key: ValueKey('project-hub-${tool.name}'),
    leading: KitRow.icon(context, icon),
    title: title,
    supporting: subtitle == null ? null : TextSpan(text: subtitle),
    supportingMaxLines: 2,
    trailing: const KitChevron(),
    onTap: onTap,
  );

  static String _basename(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }
}

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));
