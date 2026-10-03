import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../domain/team_directories.dart';
import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/phone_project_scan.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit_bidi.dart';
import '../kit/kit_chip.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_search_field.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../kit/motion/kit_refresh.dart';
import '../widgets/phone_project_kind.dart';
import '../widgets/product_states.dart' show productErrorText;
import 'project_folder_actions.dart';
import 'shared_storage_access_flow.dart';

/// Projects (map pages `projects`, `projects-rename-dialog`): the server's
/// open project folders as one row list. A row opens its project; its menu
/// (long-press, right-click) renames it. The current project carries the
/// current mark and the word "Current", never colour alone. A server that
/// cannot switch projects gets the same title and says it works in one
/// folder.
class ProjectsScreen extends StatefulWidget {
  final ConnectionController controller;
  final String? selectedProjectID;

  const ProjectsScreen({
    super.key,
    required this.controller,
    required this.selectedProjectID,
  });

  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  final _search = TextEditingController();
  List<WorkspaceProject>? _projects;
  String? _error;
  String? _busyProjectID;
  String? _switchError;
  bool _loading = false;
  int _loadGeneration = 0;

  /// The folders in the host's project space (null: it has none to read).
  /// The server lists only projects it has opened, so restored or copied
  /// folders show here until they are opened once.
  List<PhoneProject>? _spaceFolders;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _search.addListener(_searchChanged);
    if (widget.controller.capabilities.projectManagement) {
      unawaited(_load());
    }
  }

  void _searchChanged() => setState(() {});

  Future<void> _load() async {
    if (!widget.controller.capabilities.projectManagement) return;
    final generation = ++_loadGeneration;
    setState(() => _loading = true);
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() {
        _loading = false;
        _error = _l10n.e7ProjectProjectsReconnect;
      });
      return;
    }
    try {
      final projects = await repository.listProjects();
      projects.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _projects = projects;
        _error = null;
      });
      unawaited(_loadSpace(generation));
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _error = productErrorText(error);
      });
    }
  }

  Future<void> _loadSpace(int generation) async {
    final folders = await ProjectFolderActions.projectSpaceFolders(
      widget.controller,
    );
    if (!mounted || generation != _loadGeneration) return;
    setState(() => _spaceFolders = folders);
  }

  /// The project space's folders the server has not opened: not a project
  /// of its (nor a worktree of one), matching the search when there is one.
  List<PhoneProject> get _unopened {
    final folders = _spaceFolders;
    if (folders == null) return const [];
    final known = {
      for (final project in _projects ?? const <WorkspaceProject>[])
        for (final directory in [project.directory, ...project.worktrees])
          ConnectionController.normalizeDirectoryPath(directory),
    };
    final query = _query.toLowerCase();
    return [
      for (final folder in folders)
        if (!known.contains(
              ConnectionController.normalizeDirectoryPath(folder.path),
            ) &&
            (query.isEmpty ||
                folder.name.toLowerCase().contains(query) ||
                folder.path.toLowerCase().contains(query)))
          folder,
    ];
  }

  Future<void> _openUnopened(PhoneProject folder) async {
    if (_busyProjectID != null) return;
    final path = await ProjectFolderActions.openKnownFolder(
      context,
      widget.controller,
      folder.path,
    );
    if (path != null && mounted) Navigator.of(context).pop(true);
  }

  /// Real project folders only. The server's catch-all root and any home
  /// folder are never offered: they are not workspaces. Nor are the AI
  /// Team's own folders: its work is on the AI Team screen.
  List<WorkspaceProject> get _usableProjects => (_projects ?? const [])
      .where(
        (project) =>
            !isProtectedWorkspaceDirectory(project.directory) &&
            !isAiTeamDirectory(project.directory),
      )
      .toList(growable: false);

  String get _query => _search.text.trim();

  List<WorkspaceProject> get _visibleProjects {
    final projects = _usableProjects;
    final query = _query.toLowerCase();
    if (query.isEmpty) return projects;
    return projects
        .where(
          (project) =>
              project.name.toLowerCase().contains(query) ||
              project.directory.toLowerCase().contains(query),
        )
        .toList(growable: false);
  }

  Future<void> _select(WorkspaceProject project) async {
    if (!widget.controller.capabilities.projectManagement) return;
    if (_busyProjectID != null) return;
    if (project.id == widget.selectedProjectID &&
        widget.controller.directory == project.directory &&
        widget.controller.workspace == null) {
      Navigator.of(context).pop(false);
      return;
    }
    // A project in shared storage opens only once its files can be seen.
    final access = await SharedStorageAccessFlow.ensure(
      context,
      widget.controller.profile,
      project.directory,
      workRunning: widget.controller.busySessions.isNotEmpty,
    );
    if (!mounted || access != SharedStorageOutcome.proceed) return;
    setState(() {
      _busyProjectID = project.id;
      _switchError = null;
    });
    await widget.controller.selectLocation(directory: project.directory);
    if (!mounted) return;
    final error = widget.controller.locationError;
    setState(() {
      _busyProjectID = null;
      _switchError = error;
    });
    if (error != null) return;
    Navigator.of(context).pop(true);
  }

  Future<void> _createFolder() async {
    if (_busyProjectID != null) return;
    final path = await ProjectFolderActions.createFolder(
      context,
      widget.controller,
      suggestedName: ProjectFolderActions.suggestedName(_projects),
    );
    if (path != null && mounted) Navigator.of(context).pop(true);
  }

  Future<void> _openFolder() async {
    if (_busyProjectID != null) return;
    final path = await ProjectFolderActions.openFolder(
      context,
      widget.controller,
    );
    if (path != null && mounted) Navigator.of(context).pop(true);
  }

  /// Rename runs inside the dialog: while it works the dialog says so, and
  /// a failure stays in the dialog under the field with the typed name kept
  /// (map `projects-rename-dialog`: "rename fails").
  Future<void> _rename(WorkspaceProject project) async {
    if (!widget.controller.capabilities.projectManagement) return;
    if (_busyProjectID != null) return;
    final l10n = _l10n;
    await showKitInputDialog(
      context,
      title: l10n.e7ProjectProjectRenameTitle,
      label: l10n.e7ProjectProjectNameLabel,
      confirmLabel: l10n.e7ProjectProjectSave,
      initial: project.name,
      helper: l10n.e7ProjectProjectNameHint,
      fieldKey: const ValueKey('project-name-input'),
      confirmKey: const ValueKey('confirm-rename-project'),
      onSubmit: (next) => _applyRename(project, next),
    );
  }

  /// Returns null when the name is saved (or nothing changed), the reason
  /// otherwise.
  Future<String?> _applyRename(WorkspaceProject project, String next) async {
    final l10n = _l10n;
    final folderName = _basename(project.directory);
    final normalized = next.trim();
    final serverName = normalized.isEmpty || normalized == folderName
        ? ''
        : normalized;
    if (normalized == project.name && serverName.isNotEmpty) return null;
    setState(() => _busyProjectID = project.id);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        return l10n.e7ProjectProjectRenameFailed(
          l10n.e7ProjectProjectsReconnect,
        );
      }
      final updated = await repository.renameProject(
        projectID: project.id,
        projectDirectory: project.directory,
        name: serverName,
      );
      if (mounted) {
        setState(() {
          _projects = [
            for (final item in _projects ?? const <WorkspaceProject>[])
              if (item.id == updated.id) updated else item,
          ]..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
        });
      }
      return null;
    } catch (error) {
      return l10n.e7ProjectProjectRenameFailed(productErrorText(error));
    } finally {
      if (mounted) setState(() => _busyProjectID = null);
    }
  }

  static String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    final parts = normalized
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? path : parts.last;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    if (!widget.controller.capabilities.projectManagement) {
      return _oneFolder(context, l10n);
    }
    final projects = _projects;
    final usable = _usableProjects;
    final visible = _visibleProjects;
    final query = _query;
    final switchError = _switchError;
    final busy = _loading || _busyProjectID != null;
    return KitScreen(
      width: KitScreenWidth.list,
      // Pull to refresh reloads the list; Try again lives with a failed
      // read, so the top bar carries no refresh of its own.
      topBar: KitTopBar(title: l10n.e7ProjectProjectsTitle),
      search: KitSearchField(
        label: l10n.e7ProjectProjectsSearch,
        controller: _search,
        onChanged: (_) {},
        resultCount: query.isEmpty || projects == null ? null : visible.length,
        fieldKey: const ValueKey('project-search'),
      ),
      loading: busy,
      loadingLabel: l10n.e7ProjectProjectsRefresh,
      body: KitRefresh(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('projects-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          // One rail (R5): the list's gutter; the panels below add none of
          // their own, so notices, states and panels line up.
          padding: KitScreen.padding(context),
          children: [
            if (switchError != null)
              KitNotice(
                key: const ValueKey('projects-switch-error'),
                message: switchError,
                tone: AppStatusTone.failure,
                icon: AppIconography.error,
                onDismiss: () => setState(() => _switchError = null),
              ),
            KitRowGroup(
              margin: EdgeInsets.zero,
              children: [
                if (ProjectFolderActions.canCreate(widget.controller))
                  KitRow(
                    key: const ValueKey('projects-create-folder'),
                    leading: const KitRowIcon(AppIconography.folderAdd),
                    title: l10n.projectFolderCreate,
                    supporting: TextSpan(
                      text: l10n.projectFolderCreateSubtitle(
                        KitBidi.ltr(managedProjectsDirectory),
                      ),
                    ),
                    trailing: const KitChevron(),
                    onTap: _createFolder,
                  ),
                KitRow(
                  key: const ValueKey('projects-open-folder'),
                  leading: const KitRowIcon(AppIconography.folderOpen),
                  title: l10n.projectFolderOpen,
                  supporting: TextSpan(text: l10n.projectFolderOpenSubtitle),
                  supportingMaxLines: 2,
                  trailing: const KitChevron(),
                  onTap: _openFolder,
                ),
              ],
            ),
            if (_loading && projects == null)
              const KitSkeletonRows()
            else if (_error != null && projects == null)
              KitStateView.error(
                title: l10n.e7ProjectProjectsRefreshFailed,
                body: _error,
                size: KitStateSize.inline,
                retry: KitAction(
                  label: l10n.workspaceRetryProjects,
                  onPressed: _load,
                ),
              )
            else if (projects != null && usable.isEmpty)
              // Coherent with the Workspace chooser: the server's home folder
              // is never a project, so a fresh server starts with a new or
              // typed folder.
              KitStateView(
                icon: AppIconography.folders,
                title: l10n.e7ProjectProjectsEmpty,
                body: l10n.e7ProjectProjectsEmptyDetail,
                size: KitStateSize.inline,
              )
            else if (visible.isEmpty && query.isNotEmpty)
              KitSearchNoMatch(query: query, onClear: _search.clear)
            else if (visible.isNotEmpty)
              // The label keeps the section gap from the folder actions.
              KitRowGroup(
                key: const ValueKey('projects-open-group'),
                margin: EdgeInsets.zero,
                label: l10n.e7ProjectProjectsOpened,
                children: [
                  for (final project in visible)
                    _projectRow(context, l10n, project),
                ],
              ),
            if (_unopened.isNotEmpty)
              KitRowGroup(
                key: const ValueKey('projects-space-group'),
                margin: EdgeInsets.zero,
                label: l10n.openProjectIn(
                  KitBidi.ltr(managedProjectsDirectory),
                ),
                children: [
                  for (final folder in _unopened)
                    _spaceRow(context, l10n, folder),
                ],
              ),
            if (_error != null && projects != null)
              KitNotice.error(
                key: const ValueKey('project-refresh-error'),
                title: l10n.e7ProjectProjectsRefreshFailed,
                message: _error!,
                retry: KitAction(
                  label: l10n.workspaceRetryProjects,
                  onPressed: _load,
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// A folder of the project space the server has not opened: its kind, a
  /// Git badge when it is a repository, and one tap opens it.
  Widget _spaceRow(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProject folder,
  ) => KitRow(
    key: ValueKey('project-space-${folder.name}'),
    leading: KitRow.icon(context, phoneProjectKindIcon(folder.kind)),
    title: folder.name,
    supporting: TextSpan(text: phoneProjectKindLabel(l10n, folder.kind)),
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (folder.hasGit) ...[
          KitChip(label: l10n.phoneScanGit, icon: AppIconography.branch),
          SizedBox(width: KitTokens.of(context).space2),
        ],
        const KitChevron(),
      ],
    ),
    enabled: _busyProjectID == null,
    onTap: () => _openUnopened(folder),
  );

  Widget _projectRow(
    BuildContext context,
    AppLocalizations l10n,
    WorkspaceProject project,
  ) {
    final active = project.id == widget.selectedProjectID;
    final busy = _busyProjectID == project.id;
    final worktrees = project.worktrees.length;
    return KitRow(
      key: ValueKey('project-${project.id}'),
      leading: KitRowIcon(AppIconography.files, current: active),
      title: project.name,
      supporting: TextSpan(
        children: [
          if (active) kitCurrentSpan(context, l10n.e7SharedCurrent),
          TextSpan(text: KitBidi.ltr(project.directory)),
          if (worktrees > 0)
            TextSpan(text: ' · ${l10n.e7ProjectProjectWorktrees(worktrees)}'),
        ],
      ),
      trailing: active ? null : const KitChevron(),
      enabled: !busy,
      selected: active,
      onTap: () => _select(project),
      menuLabel: project.name,
      menu: [
        KitMenuItem(
          key: ValueKey('rename-project-${project.id}'),
          label: l10n.e7ProjectProjectRenameAction(project.name),
          icon: AppIconography.edit,
          enabled: _busyProjectID == null,
          onSelected: () => unawaited(_rename(project)),
        ),
      ],
    );
  }

  /// A server that works in one folder (Codex, Paseo): the same title, the
  /// folder it uses, and what to do instead of switching.
  Widget _oneFolder(BuildContext context, AppLocalizations l10n) {
    final directory = widget.controller.directory;
    final hasDirectory = directory != null && directory.isNotEmpty;
    return KitScreen(
      width: KitScreenWidth.reading,
      topBar: KitTopBar(title: l10n.e7ProjectProjectsTitle),
      body: ListView(
        key: const ValueKey('projects-context-list'),
        padding: KitScreen.padding(context),
        children: [
          KitStateView(
            icon: AppIconography.folderOpen,
            title: l10n.projectsOneFolderTitle,
            body: l10n.e7ProjectProjectSwitchUnavailableDetail,
            size: KitStateSize.inline,
          ),
          KitRowGroup(
            margin: EdgeInsets.zero,
            children: [
              KitRow(
                key: const ValueKey('projects-configured-folder'),
                leading: const KitRowIcon(AppIconography.files),
                title: l10n.projectConfiguredFolder,
                supporting: hasDirectory
                    ? null
                    : TextSpan(text: l10n.e7ProjectProjectDefaultDirectory),
                below: hasDirectory
                    ? KitText.mono(directory, selectable: true)
                    : null,
                supportingMaxLines: 2,
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _search
      ..removeListener(_searchChanged)
      ..dispose();
    super.dispose();
  }
}
