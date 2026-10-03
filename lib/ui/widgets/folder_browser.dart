import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../builtin/builtin_folders.dart';
import '../../platform/phone_project_scan.dart';
import '../../platform/phone_storage_folders.dart';
import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_chip.dart';
import '../kit/kit_field.dart';
import '../kit/kit_icon.dart';
import '../kit/kit_icon_button.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_motion.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_section_label.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_since.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_tappable.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/scenes/folders_open_scene.dart';
import 'product_states.dart' show productErrorDetails;

/// Lists the folders directly inside an absolute path.
typedef FolderLister = Future<List<FolderEntry>> Function(String path);

/// What the folder browser was closed with.
sealed class FolderBrowserChoice {
  const FolderBrowserChoice();
}

/// Open [path], which is there, as the project.
final class FolderBrowserOpen extends FolderBrowserChoice {
  const FolderBrowserOpen(this.path);
  final String path;
}

/// Make [path] a new project (a folder that is not there yet), then open it.
final class FolderBrowserCreate extends FolderBrowserChoice {
  const FolderBrowserCreate(this.path);
  final String path;
}

/// Type a path instead, starting from [startPath], the folder being shown.
final class FolderBrowserEnterPath extends FolderBrowserChoice {
  const FolderBrowserEnterPath(this.startPath);
  final String startPath;
}

/// The phone's own storage as a second place to look, next to the project
/// space (shown only for a server on this phone).
class PhoneStoragePlace {
  const PhoneStoragePlace({
    required this.list,
    required this.ensureAccess,
    this.openedBefore,
    this.scan,
  });

  /// Lists the folders of the phone's internal storage.
  final FolderLister list;

  /// Runs the All files access consent when it is missing; true when the
  /// listing can be read.
  final Future<bool> Function(BuildContext context) ensureAccess;

  /// Shared-storage folders opened as projects before (absolute paths).
  final Future<List<String>> Function()? openedBefore;

  /// Starts "Find projects" over the phone's storage with a time cap; null
  /// leaves the menu item out.
  final PhoneProjectScan Function(Duration timeLimit)? scan;
}

/// "Open a project" for a server on this phone (OpenCode inside the app, or
/// the one this app runs in Termux): the folders of its Ubuntu, browsed from
/// the projects folder; [list] says how they are read.
///
/// Built from kit parts only: the one sheet frame ([KitSheet]) after the
/// Canva "Move to a folder" and iOS Files pickers. One header row: a back
/// chevron that goes up one folder (Close at the place's first folder), the
/// folder's name, and a more menu ("Show hidden folders", "Enter a path");
/// under the title the place menu ("This phone", "Project space"), shown
/// only when both exist. The body is one panel of folder rows. A project
/// (a repository, a project OpenCode knows, or any folder straight in the
/// projects folder) opens with a tap and its chevron shows what is in it;
/// any other folder is gone into with a tap. The pinned bar holds the text
/// button "New project" and "Open `<folder>`" for the folder being shown.
/// "New project" swaps the sheet's content in place (never a second sheet or
/// dialog) for the name step; back returns to the same folder.
///
/// States (project-folder-browser map record):
/// - loading: skeleton rows once a listing is slower than a touch answer;
///   after [KitMotion.escalateAfter] a word says the phone is still reading
///   (a Termux read may take up to 15 s), through [KitSince];
/// - empty: an empty folder says so; an empty projects folder (first run)
///   offers its first step, which opens the name dialog;
/// - error: what went wrong, "Try again", and "Up one folder" when there is
///   a folder above (a permission error is left by going up);
/// - disabled: the rows do nothing while a listing runs.
///
/// The home folder and `/` are never offered as a project
/// (`workspace_paths.dart`); the projects folder itself is where projects
/// live, so it is not offered either.
///
/// Not here yet: cloning a repository needs a gateway call (wave 3), and
/// recent projects first needs their order from the server.
class FolderBrowserSheet extends StatefulWidget {
  const FolderBrowserSheet({
    super.key,
    required this.list,
    this.knownProjects,
    this.start = managedProjectsDirectory,
    this.phone,
  });

  /// Null: the project space is the only place (a test, or no storage).
  final PhoneStoragePlace? phone;

  final FolderLister list;

  /// The folders OpenCode already has as projects (normalised paths). A
  /// failure only leaves the marks out.
  final Future<Set<String>> Function()? knownProjects;

  final String start;

  @override
  State<FolderBrowserSheet> createState() => _FolderBrowserSheetState();
}

class _FolderBrowserSheetState extends State<FolderBrowserSheet> {
  late String _path = BuiltinRootfsFolders.normalize(widget.start) ?? '/';
  List<FolderEntry>? _entries;
  Object? _error;
  bool _loading = true;

  /// When the listing now running began; null when none runs.
  DateTime? _loadingSince;

  /// Skeleton rows only once a listing takes longer than a touch answer,
  /// so going into a folder does not flash.
  bool _showSkeleton = true;
  Timer? _skeletonTimer;
  int _generation = 0;
  Set<String> _known = const {};

  /// Looking at the phone's storage rather than the project space.
  bool _phone = false;
  bool _showHidden = false;
  bool _accessRefused = false;

  /// The inline name field of "New project here".
  bool _naming = false;
  final TextEditingController _name = TextEditingController();
  String? _nameError;
  List<String> _opened = const [];

  /// "Find projects": the step swapped in place, and its results, kept for
  /// the life of this sheet only.
  bool _finding = false;
  PhoneProjectScanState? _scanState;

  FolderLister get _lister => _phone ? widget.phone!.list : widget.list;

  @override
  void initState() {
    super.initState();
    _load(_path);
    _loadKnown();
  }

  @override
  void dispose() {
    _skeletonTimer?.cancel();
    _scanState?.dispose();
    _name.dispose();
    super.dispose();
  }

  Future<void> _loadKnown() async {
    final load = widget.knownProjects;
    if (load == null) return;
    try {
      final known = await load();
      if (mounted) setState(() => _known = known);
    } catch (_) {
      // Only the "OpenCode project" marks depend on it.
    }
  }

  Future<void> _load(String path) async {
    final generation = ++_generation;
    _skeletonTimer?.cancel();
    setState(() {
      _loading = true;
      _loadingSince = clock.now();
      if (_entries == null && _error == null) _showSkeleton = true;
    });
    _skeletonTimer = Timer(KitMotion.quick, () {
      if (mounted && generation == _generation) {
        setState(() => _showSkeleton = true);
      }
    });
    List<FolderEntry>? entries;
    Object? error;
    try {
      entries = await _lister(path);
    } catch (caught) {
      error = caught;
    }
    if (!mounted || generation != _generation) return;
    _skeletonTimer?.cancel();
    setState(() {
      _path = path;
      _entries = entries;
      _error = error;
      _loading = false;
      _loadingSince = null;
      _showSkeleton = false;
    });
  }

  static String _join(String folder, String name) =>
      folder == '/' ? '/$name' : '$folder/$name';

  static String _nameOf(String path) =>
      path == '/' ? '/' : path.substring(path.lastIndexOf('/') + 1);

  bool _isProject(FolderEntry entry) =>
      !_phone &&
      !isProtectedWorkspaceDirectory(entry.path) &&
      (entry.isGit ||
          _known.contains(entry.path) ||
          BuiltinRootfsFolders.parentOf(entry.path) ==
              managedProjectsDirectory);

  /// The folder shown can be opened as it is: not home or `/`, and not the
  /// projects folder that holds the projects.
  bool get _canOpenHere => _phone
      ? _path != PhoneStorageFolders.root
      : !isProtectedWorkspaceDirectory(_path) &&
            _path != managedProjectsDirectory;

  String? get _parent => _phone
      ? PhoneStorageFolders.parentOf(_path)
      : BuiltinRootfsFolders.parentOf(_path);

  List<FolderEntry> get _visible {
    final entries = _entries ?? const <FolderEntry>[];
    return _phone && !_showHidden
        ? [
            for (final entry in entries)
              if (!entry.name.startsWith('.')) entry,
          ]
        : entries;
  }

  Future<void> _choosePlace(bool phone) async {
    final place = widget.phone;
    if (place == null || phone == _phone) return;
    if (phone) {
      final granted = await place.ensureAccess(context);
      if (!mounted) return;
      if (!granted) {
        setState(() => _accessRefused = true);
        return;
      }
      setState(() {
        _phone = true;
        _accessRefused = false;
        _entries = null;
        _error = null;
      });
      unawaited(_loadOpened());
      await _load(PhoneStorageFolders.root);
    } else {
      setState(() {
        _phone = false;
        _entries = null;
        _error = null;
      });
      await _load(BuiltinRootfsFolders.normalize(widget.start) ?? '/');
    }
  }

  Future<void> _loadOpened() async {
    final load = widget.phone?.openedBefore;
    if (load == null) return;
    try {
      final opened = await load();
      if (mounted) setState(() => _opened = opened);
    } catch (_) {
      // Only the "Opened before" rows depend on it.
    }
  }

  /// "New project here": the sheet's actions turn in place into a name
  /// field (no second sheet or dialog); the project is made inside the
  /// folder being shown (project space or phone storage), then opened.
  void _newProject(AppLocalizations l10n) {
    _name.clear();
    setState(() {
      _naming = true;
      _nameError = null;
    });
  }

  void _cancelNaming() => setState(() {
    _naming = false;
    _nameError = null;
  });

  String? _nameProblem(String value) {
    final clean = value.trim();
    return projectFolderNameProblem(clean) ??
        (_phone ? null : workspaceDirectoryProblem(_join(_path, clean)));
  }

  void _submitName(AppLocalizations l10n) {
    final clean = _name.text.trim();
    final problem = _nameProblem(clean);
    if (problem != null) {
      setState(() => _nameError = problem);
      return;
    }
    final path = _join(_path, clean);
    // A name that is already a folder here simply opens it.
    final exists = _entries?.any((entry) => entry.name == clean) ?? false;
    KitSheet.close<FolderBrowserChoice>(
      context,
      exists ? FolderBrowserOpen(path) : FolderBrowserCreate(path),
    );
  }

  /// "Find projects": swaps the sheet in place for the results. What an
  /// earlier look found is shown as it was; a look that back cut short
  /// starts again.
  void _findProjects() {
    final start = widget.phone?.scan;
    if (start == null) return;
    final state = _scanState ??= PhoneProjectScanState(start);
    setState(() => _finding = true);
    if (!state.hasResult && !state.scanning) state.run();
  }

  void _stopFinding() {
    _scanState?.cancel();
    setState(() => _finding = false);
  }

  bool get _settled => !_loading && _error == null && _entries != null;

  /// The first folder of the place being shown: its header offers Close
  /// there and a back chevron everywhere below it.
  String get _placeRoot => _phone
      ? PhoneStorageFolders.root
      : (BuiltinRootfsFolders.normalize(widget.start) ?? '/');

  bool get _atRoot => _path == _placeRoot || _parent == null;

  /// The folder's own name, or the place's name at its root.
  String _title(AppLocalizations l10n) => _phone && _path == _placeRoot
      ? l10n.folderBrowserInternalStorage
      : _nameOf(_path);

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final media = MediaQuery.of(context);
    final maxHeight =
        (media.size.height - media.viewInsets.bottom - media.padding.top) * .9;
    return SafeArea(
      child: Padding(
        padding: EdgeInsetsDirectional.only(bottom: media.viewInsets.bottom),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight),
          child: _finding
              ? _scanStep(l10n)
              : _naming
              ? _nameStep(l10n)
              : _listStep(l10n),
        ),
      ),
    );
  }

  /// Step two, in place of the list (no second sheet or dialog): the name
  /// of the new project, made inside the folder that was being shown.
  Widget _nameStep(AppLocalizations l10n) => KitSheet(
    key: const ValueKey('in-app-projects'),
    step: 'name',
    title: l10n.projectFolderNewProject,
    subtitle: l10n.folderBrowserNewProjectIn(_title(l10n)),
    handle: false,
    leading: KitAction(
      key: const ValueKey('phone-new-folder-cancel'),
      label: l10n.kitTopBarBack,
      onPressed: _cancelNaming,
    ),
    onClose: () => KitSheet.close<FolderBrowserChoice>(context),
    primary: KitAction(
      key: const ValueKey('phone-new-folder-create'),
      label: l10n.folderBrowserCreateAndOpen,
      icon: AppIconography.folderAdd,
      onPressed: () => _submitName(l10n),
    ),
    child: KitField(
      fieldKey: const ValueKey('phone-new-folder-name'),
      label: l10n.projectFolderProjectNameLabel,
      controller: _name,
      hint: l10n.projectFolderNameHint,
      helper: l10n.folderBrowserNewProjectCreates(
        KitBidi.ltr(
          _join(_path, _name.text.trim().isEmpty ? '…' : _name.text.trim()),
        ),
      ),
      error: _nameError,
      autofocus: true,
      textInputAction: TextInputAction.done,
      onChanged: (value) => setState(() {
        if (_nameError != null) _nameError = _nameProblem(value);
      }),
      onSubmitted: (_) => _submitName(l10n),
    ),
  );

  /// Step two, in place of the list: the projects found on the phone, as
  /// they are found. One tap opens one, as "Open" does for a folder.
  Widget _scanStep(AppLocalizations l10n) {
    final state = _scanState!;
    return ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final found = state.projects;
        final end = state.end;
        final seconds = state.limit.inSeconds;
        final String? subtitle = state.scanning
            ? l10n.phoneScanLooking
            : end == PhoneScanEnd.completed && found.isEmpty
            ? null
            : end == PhoneScanEnd.timedOut
            ? l10n.phoneScanStopped(seconds, found.length)
            : end == PhoneScanEnd.limited
            ? l10n.phoneScanFirstShown(found.length)
            : l10n.phoneScanFound(found.length);
        final deeper = end == PhoneScanEnd.timedOut && seconds < 30;
        return KitSheet(
          key: const ValueKey('in-app-projects'),
          step: 'find',
          title: l10n.phoneScanTitle,
          subtitle: subtitle,
          handle: false,
          loading: state.scanning,
          onClose: () => KitSheet.close<FolderBrowserChoice>(context),
          leading: KitAction(
            key: const ValueKey('phone-scan-back'),
            label: l10n.kitTopBarBack,
            onPressed: _stopFinding,
          ),
          bar: deeper,
          primary: deeper
              ? KitAction(
                  key: const ValueKey('phone-scan-deeper'),
                  label: l10n.phoneScanLookDeeper,
                  onPressed: () =>
                      state.run(timeLimit: const Duration(seconds: 30)),
                )
              : null,
          child: found.isEmpty
              ? state.scanning
                    ? const KitSkeletonRows(count: 4)
                    : KitStateView(
                        key: const ValueKey('phone-scan-empty'),
                        size: KitStateSize.inline,
                        icon: AppIconography.folderOpen,
                        illustration: const KitFoldersOpenScene(),
                        title: l10n.phoneScanEmptyTitle,
                        body: l10n.phoneScanEmptyBody,
                      )
              : KitRowGroup(
                  margin: EdgeInsetsDirectional.zero,
                  children: [
                    for (final project in found)
                      _projectRow(context, l10n, project),
                  ],
                ),
        );
      },
    );
  }

  static String _kindLabel(AppLocalizations l10n, PhoneProjectKind kind) =>
      switch (kind) {
        PhoneProjectKind.dart => l10n.phoneScanKindDart,
        PhoneProjectKind.node => l10n.phoneScanKindNode,
        PhoneProjectKind.python => l10n.phoneScanKindPython,
        PhoneProjectKind.rust => l10n.phoneScanKindRust,
        PhoneProjectKind.go => l10n.phoneScanKindGo,
        PhoneProjectKind.java => l10n.phoneScanKindJava,
        PhoneProjectKind.ruby => l10n.phoneScanKindRuby,
        PhoneProjectKind.php => l10n.phoneScanKindPhp,
        PhoneProjectKind.dotnet => l10n.phoneScanKindDotnet,
        PhoneProjectKind.cpp => l10n.phoneScanKindCpp,
        PhoneProjectKind.git => l10n.phoneScanKindGit,
      };

  /// The folder the project is in, from the storage's top, cut at its start
  /// when long so the end (the nearest folders) stays readable.
  static String _where(String path) {
    final parent = PhoneStorageFolders.parentOf(path) ?? path;
    final short = parent == PhoneStorageFolders.root
        ? '/'
        : parent.substring(PhoneStorageFolders.root.length);
    return short.length <= 34
        ? short
        : '…${short.substring(short.length - 33)}';
  }

  Widget _projectRow(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProject project,
  ) => KitRow(
    key: ValueKey('phone-scan-${project.path}'),
    leading: KitRow.icon(
      context,
      project.kind == PhoneProjectKind.git
          ? AppIconography.branch
          : AppIconography.projects,
    ),
    title: project.name,
    supporting: TextSpan(
      text:
          '${_kindLabel(l10n, project.kind)} · ${KitBidi.ltr(_where(project.path))}',
    ),
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (project.hasGit && project.kind != PhoneProjectKind.git) ...[
          KitChip(label: l10n.phoneScanGit),
          SizedBox(width: KitTokens.of(context).space2),
        ],
        const KitChevron(),
      ],
    ),
    onTap: () => KitSheet.close<FolderBrowserChoice>(
      context,
      FolderBrowserOpen(project.path),
    ),
  );

  /// Step one: the folder being shown, one row per folder inside it, and
  /// the bar with "New project" and "Open" plus the folder name.
  Widget _listStep(AppLocalizations l10n) {
    final canOpen = _settled && _canOpenHere;
    final parent = _parent;
    return KitSheet(
      key: const ValueKey('in-app-projects'),
      step: 'list',
      title: _title(l10n),
      // The route this sheet opens in draws the drag handle.
      handle: false,
      onClose: () => KitSheet.close<FolderBrowserChoice>(context),
      leading: _atRoot || parent == null
          ? KitAction(
              key: const ValueKey('folder-browser-close'),
              label: l10n.kitSheetClose,
              icon: AppIconography.close,
              onPressed: () => KitSheet.close<FolderBrowserChoice>(context),
            )
          : KitAction(
              key: const ValueKey('folder-browser-up'),
              label: l10n.folderBrowserUp,
              onPressed: _loading ? null : () => _load(parent),
            ),
      menu: [
        if (_phone)
          KitMenuItem(
            key: const ValueKey('folder-browser-hidden'),
            label: l10n.folderBrowserShowHidden,
            checked: _showHidden,
            onSelected: () => setState(() => _showHidden = !_showHidden),
          ),
        if (_phone && widget.phone?.scan != null)
          KitMenuItem(
            key: const ValueKey('folder-browser-find'),
            label: l10n.folderBrowserFindProjects,
            onSelected: _findProjects,
          ),
        KitMenuItem(
          key: const ValueKey('in-app-enter-path'),
          label: l10n.projectFolderEnterPath,
          onSelected: () => KitSheet.close<FolderBrowserChoice>(
            context,
            FolderBrowserEnterPath(_path),
          ),
        ),
      ],
      headerLine: widget.phone == null ? null : _placeLine(l10n),
      loading: _loading && !_showSkeleton,
      bar: true,
      secondary: _settled
          ? KitAction(
              key: const ValueKey('phone-new-folder'),
              label: l10n.projectFolderNewProject,
              onPressed: () => _newProject(l10n),
            )
          : null,
      primary: canOpen
          ? KitAction(
              key: const ValueKey('folder-browser-open'),
              label: l10n.folderBrowserOpen(_nameOf(_path)),
              icon: AppIconography.folderOpen,
              onPressed: () => KitSheet.close<FolderBrowserChoice>(
                context,
                FolderBrowserOpen(_path),
              ),
            )
          : null,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _note(l10n, canOpen: canOpen),
          _folders(context, l10n),
        ],
      ),
    );
  }

  /// Under the title: where the folders are ("This phone", "Project
  /// space"), a menu that switches the place. Its tooltip is the full path.
  Widget _placeLine(AppLocalizations l10n) {
    final label = _phone
        ? l10n.folderBrowserPlacePhone
        : l10n.folderBrowserPlaceProjects;
    return Builder(
      builder: (anchor) => KitTappable(
        key: const ValueKey('folder-browser-places'),
        label: l10n.folderBrowserPlaceLabel,
        tooltip: l10n.folderBrowserCurrent(_path),
        onTap: () => showKitMenu(
          anchor,
          semanticsLabel: l10n.folderBrowserPlaceLabel,
          items: [
            KitMenuItem(
              key: const ValueKey('place-projects'),
              label: l10n.folderBrowserPlaceProjects,
              icon: AppIconography.folders,
              checked: !_phone,
              onSelected: () => _choosePlace(false),
            ),
            KitMenuItem(
              key: const ValueKey('place-phone'),
              label: l10n.folderBrowserPlacePhone,
              icon: AppIconography.phone,
              checked: _phone,
              onSelected: () => _choosePlace(true),
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: KitText(
                label,
                key: const ValueKey('folder-browser-place'),
                role: KitTextRole.secondary,
              ),
            ),
            const KitIcon(
              AppIconography.chevronDown,
              size: KitIconSize.small,
              tone: KitTextTone.secondary,
            ),
          ],
        ),
      ),
    );
  }

  /// Why the folder shown cannot be opened, when it cannot.
  Widget _note(AppLocalizations l10n, {required bool canOpen}) {
    final entries = _entries;
    final String? note = _phone || canOpen || !_settled || entries!.isEmpty
        ? null
        : isProtectedWorkspaceDirectory(_path)
        ? l10n.folderBrowserHomeHere
        : _path == managedProjectsDirectory
        ? l10n.folderBrowserProjectsHere
        : null;
    if (note == null) return const SizedBox.shrink();
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: KitTokens.of(context).space3),
      child: KitText(
        note,
        key: const ValueKey('folder-browser-note'),
        role: KitTextRole.secondary,
      ),
    );
  }

  /// The folders on one panel, and the state of the listing (loading,
  /// slow, empty, error).
  Widget _folders(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final entries = _visible;
    final error = _error;
    final Widget? state;
    if (_loading && _showSkeleton) {
      state = _loadingState(l10n);
    } else if (error != null) {
      state = _errorState(l10n, error);
    } else if (_entries != null && entries.isEmpty) {
      state = _emptyState(l10n);
    } else {
      state = null;
    }
    final rows = state != null ? const <FolderEntry>[] : entries;
    return KeyedSubtree(
      key: const ValueKey('folder-browser-list'),
      child: Padding(
        padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_accessRefused && !_phone)
              Padding(
                padding: EdgeInsetsDirectional.only(bottom: tokens.space3),
                child: KitText(
                  l10n.folderBrowserPhoneRefused,
                  key: const ValueKey('folder-browser-phone-refused'),
                  role: KitTextRole.secondary,
                ),
              ),
            if (_phone &&
                _path == PhoneStorageFolders.root &&
                _opened.isNotEmpty)
              ..._openedBefore(context, l10n),
            if (rows.isNotEmpty)
              KitRowGroup(
                margin: EdgeInsetsDirectional.zero,
                children: [
                  for (final entry in rows) _folderRow(context, l10n, entry),
                ],
              ),
            ?state,
          ],
        ),
      ),
    );
  }

  /// Shared-storage folders opened as projects before: one tap opens.
  List<Widget> _openedBefore(BuildContext context, AppLocalizations l10n) => [
    KitSectionLabel(
      l10n.folderBrowserOpenedBefore,
      margin: EdgeInsets.zero,
      gapBefore: 0,
    ),
    KitRowGroup(
      margin: EdgeInsetsDirectional.only(bottom: KitTokens.of(context).space3),
      children: [
        for (final (index, path) in _opened.indexed)
          KitRow(
            key: ValueKey('phone-opened-$index'),
            leading: KitRow.icon(context, AppIconography.history),
            title: _nameOf(path),
            supporting: TextSpan(
              text: KitBidi.ltr(
                path.startsWith('${PhoneStorageFolders.root}/')
                    ? path.substring(PhoneStorageFolders.root.length)
                    : path,
              ),
              style: KitText.styleFor(KitTextRole.mono),
            ),
            trailing: const KitChevron(),
            onTap: () => KitSheet.close<FolderBrowserChoice>(
              context,
              FolderBrowserOpen(path),
            ),
          ),
      ],
    ),
  ];

  /// "package.json, src, 12 more": what is inside, in one line.
  String? _hint(AppLocalizations l10n, FolderEntry entry) {
    final inside = entry.inside;
    if (!_phone || inside == null) return null;
    if (inside.isEmpty) return l10n.folderBrowserHintEmpty;
    final names = inside.join(', ');
    return entry.more > 0
        ? l10n.folderBrowserHintMore(names, entry.more)
        : names;
  }

  Widget _folderRow(
    BuildContext context,
    AppLocalizations l10n,
    FolderEntry entry,
  ) {
    final project = _isProject(entry);
    final known =
        _known.contains(entry.path) &&
        !isProtectedWorkspaceDirectory(entry.path);
    final hint = _hint(l10n, entry);
    final String? mark = known
        ? l10n.folderBrowserProject
        : entry.isGit
        ? l10n.folderBrowserGit
        : null;
    final String? supporting = mark != null && hint != null
        ? '$mark · $hint'
        : mark ?? hint;
    return KitRow(
      key: ValueKey('in-app-project-${entry.name}'),
      leading: KitRow.icon(
        context,
        known
            ? AppIconography.projects
            : entry.isGit
            ? AppIconography.branch
            : AppIconography.folderOpen,
      ),
      title: entry.name,
      supporting: supporting == null ? null : TextSpan(text: supporting),
      trailing: project
          ? KitIconButton(
              key: ValueKey('folder-browse-${entry.name}'),
              icon: AppIconography.chevronRight,
              size: 20,
              tooltip: l10n.folderBrowserShowInside(entry.name),
              onPressed: _loading ? null : () => _load(entry.path),
            )
          : const KitChevron(),
      enabled: !_loading,
      onTap: project
          ? () => KitSheet.close<FolderBrowserChoice>(
              context,
              FolderBrowserOpen(entry.path),
            )
          : () => _load(entry.path),
    );
  }

  /// Skeleton rows; once the read has taken [KitMotion.escalateAfter], one
  /// word that it is still going (a Termux read may take up to 15 s).
  Widget _loadingState(AppLocalizations l10n) => KitSince(
    since: _loadingSince,
    builder: (context, status) => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (status.isSlow) ...[
          KitNotice(
            key: const ValueKey('folder-browser-slow'),
            icon: AppIconography.clock,
            title: l10n.folderBrowserSlowTitle,
            message: l10n.folderBrowserSlowBody,
          ),
          SizedBox(height: KitTokens.of(context).space3),
        ],
        const KitSkeletonRows(count: 4),
      ],
    ),
  );

  Widget _emptyState(AppLocalizations l10n) {
    final projects = _path == managedProjectsDirectory;
    return KitStateView(
      key: const ValueKey('folder-browser-empty'),
      size: KitStateSize.inline,
      icon: AppIconography.folderOpen,
      illustration: const KitFoldersOpenScene(),
      title: projects
          ? l10n.folderBrowserNoProjectsTitle
          : l10n.folderBrowserEmptyTitle,
      body: projects
          ? l10n.folderBrowserNoProjectsBody
          : _phone
          ? l10n.folderBrowserEmptyPhoneBody
          : l10n.folderBrowserEmptyBody,
      // The first run's first step: the cursor goes to the name.
      primary: projects
          ? KitAction(
              key: const ValueKey('folder-browser-first-project'),
              label: l10n.folderBrowserFirstProject,
              icon: AppIconography.folderAdd,
              onPressed: () => _newProject(l10n),
            )
          : null,
    );
  }

  Widget _errorState(AppLocalizations l10n, Object error) {
    final problem = error is FolderListException ? error.problem : null;
    return KitStateView(
      key: const ValueKey('folder-browser-error'),
      size: KitStateSize.inline,
      icon: AppIconography.warning,
      // A neutral glyph with the words, never the danger colour (LOOK-5).
      tone: AppStatusTone.neutral,
      title: l10n.folderBrowserErrorTitle,
      body: switch (problem) {
        FolderListProblem.notInstalled => l10n.folderBrowserErrorNotInstalled,
        FolderListProblem.missing => l10n.folderBrowserErrorMissing,
        FolderListProblem.denied => l10n.folderBrowserErrorDenied,
        FolderListProblem.linked => l10n.folderBrowserErrorLinked,
        FolderListProblem.timedOut => l10n.folderBrowserErrorTimedOut,
        _ => l10n.folderBrowserErrorFailed,
      },
      secondary: KitAction(
        key: const ValueKey('folder-browser-retry'),
        label: l10n.folderBrowserRetry,
        icon: AppIconography.retry,
        onPressed: () => _load(_path),
      ),
      details: productErrorDetails(error),
    );
  }
}
