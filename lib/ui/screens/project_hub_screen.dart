import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../domain/chat_feed.dart' show isTemporaryProjectDirectory;
import '../../domain/server_gateway.dart'
    show ProductException, ServerCapabilities, WorkspaceProject;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../navigation/last_project.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_chip.dart';
import '../kit/kit_buttons.dart' show KitAction;
import '../kit/kit_capability_explainer.dart';
import '../kit/kit_menu.dart';
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
          throw ProductException(l10n.e7LibraryNoProjectFolderIsOpenChooseOne);
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

/// Lets the shell offer system Back to the Project tab before leaving it.
class ProjectHubBackController {
  bool Function()? _handler;
  bool handleBack() => _handler?.call() ?? false;
}

/// The Files tab: the title, a chip naming the project it shows (tap to
/// switch), then every tool that acts on that project, one row each, Changes
/// first. The project is the one last used for a chat, else the open one. A
/// row the connected server cannot serve is absent, and the shell drops the
/// whole tab when no row is left (UX plan 5.1, rule 7). With no project the
/// tab says so and offers the chooser (map project-hub, proposal "fix").
///
/// Files opens inside the tab rather than over it, so the file browser keeps
/// its folder, search and scroll position across tab switches the way it did
/// when it was the tab. The other tools are full screens of their own.
class ProjectHub extends StatefulWidget {
  const ProjectHub({
    super.key,
    required this.controller,
    this.focusSearchSignal,
    this.openFilesSignal,
    this.backController,
  });

  final ConnectionController controller;

  /// Bumped by the shell's Ctrl/Cmd+F while this tab is showing.
  final ValueListenable<int>? focusSearchSignal;

  /// Bumped by the shell when a search result means Files.
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
    if (capabilities.projectManagement || capabilities.worktreeCreate)
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

  /// Files is built on first use and then kept, so leaving it for the hub or
  /// another tab does not reload the tree.
  bool _filesBuilt = false;
  bool _filesOpen = false;
  bool _opening = false;

  /// The live lines under Changes and Terminal ("3 files changed", "1
  /// running"): null until read, and left out when a read fails (the tool's
  /// own page says why). No timer polls them: they are read when the tab
  /// opens, when the project changes, when a tool closes and when the last
  /// running conversation finishes, since that is when files change.
  int? _changedFiles;
  int? _runningTerminals;
  int _statusGeneration = 0;
  (String?, int, String?)? _statusScope;
  bool _wasBusy = false;

  /// The hub's order: Changes first; Search is Files' own field.
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
    widget.openFilesSignal?.addListener(_openFiles);
    widget.backController?._handler = _handleBack;
    widget.controller.addListener(_followConnection);
    _wasBusy = widget.controller.busySessions.isNotEmpty;
    _followConnection();
    WidgetsBinding.instance.addPostFrameCallback((_) => _followLastProject());
  }

  /// Files shows the project the person last used for a chat: when that is
  /// not the open location, the location moves to it once, on first show.
  void _followLastProject() {
    if (!mounted) return;
    final controller = widget.controller;
    final last = lastUsedProjectOf(controller);
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
    if (oldWidget.openFilesSignal != widget.openFilesSignal) {
      oldWidget.openFilesSignal?.removeListener(_openFiles);
      widget.openFilesSignal?.addListener(_openFiles);
    }
    if (oldWidget.backController != widget.backController) {
      oldWidget.backController?._handler = null;
      widget.backController?._handler = _handleBack;
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
    widget.openFilesSignal?.removeListener(_openFiles);
    widget.backController?._handler = null;
    widget.controller.removeListener(_followConnection);
    _focusFilesSearch.dispose();
    super.dispose();
  }

  bool get _canBrowse => widget.controller.capabilities.fileBrowsing;

  bool _handleBack() {
    if (!_filesOpen || !_canBrowse) return false;
    if (_filesBack.handleBack()) return true;
    setState(() => _filesOpen = false);
    return true;
  }

  void _openFiles() {
    if (!mounted || !_canBrowse) return;
    setState(() {
      _filesBuilt = true;
      _filesOpen = true;
    });
  }

  void _searchFiles() {
    if (!mounted || !_canBrowse) return;
    _openFiles();
    // Files may only be mounting in this frame; its listener exists after it.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focusFilesSearch.value++;
    });
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
  Widget build(BuildContext context) {
    final showFiles = _filesOpen && _canBrowse;
    // Visibility keeps the hidden side's state and stops its tickers (and
    // with them its status-line contribution) while it is hidden.
    return Stack(
      children: [
        Visibility(
          visible: !showFiles,
          maintainState: true,
          child: _hub(context),
        ),
        if (_filesBuilt && _canBrowse)
          Visibility(
            visible: showFiles,
            maintainState: true,
            child: _files(context),
          ),
      ],
    );
  }

  Widget _files(BuildContext context) {
    final l10n = _l10n(context);
    return FilesScreen(
      controller: widget.controller,
      focusSearchSignal: _focusFilesSearch,
      backController: _filesBack,
      // One top bar: the folder is the title and Back returns to the hub.
      // System Back does the same, but nothing here is reachable by a
      // gesture alone.
      topBar: (folder) => KitTopBar(
        key: const ValueKey('project-hub-files-header'),
        title: folder ?? l10n.readerUiFiles,
        exit: KitTopBarExit.back,
        exitKey: const ValueKey('project-hub-files-back'),
        onExit: () => setState(() => _filesOpen = false),
      ),
    );
  }

  Widget _hub(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) => _hubBody(context),
  );

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
      body: KitScrollArea(
        builder: (scrollController) => ListView(
          controller: scrollController,
          key: const ValueKey('project-hub'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space3,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            _filesTitle(context),
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
        end: tokens.space1,
        bottom: tokens.sectionGap,
      ),
      child: Row(
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
          const Spacer(),
          KitRowMenu(
            key: const ValueKey('project-hub-menu'),
            items: [
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
    ProjectTool.files => _row(
      tool,
      icon: AppIconography.files,
      title: l10n.readerUiFiles,
      onTap: _openFiles,
    ),
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
    ProjectTool.search => _row(
      tool,
      icon: AppIconography.search,
      title: l10n.readerUiSearchFiles,
      onTap: _searchFiles,
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
