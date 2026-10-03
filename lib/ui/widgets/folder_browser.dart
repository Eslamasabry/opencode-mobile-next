import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../builtin/builtin_folders.dart';
import '../../platform/phone_storage_folders.dart';
import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_breadcrumb.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_field.dart';
import '../kit/kit_icon_button.dart';
import '../kit/kit_motion.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_section_label.dart';
import '../kit/kit_segmented.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_since.dart';
import '../kit/kit_state_view.dart';
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
  });

  /// Lists the folders of the phone's internal storage.
  final FolderLister list;

  /// Runs the All files access consent when it is missing; true when the
  /// listing can be read.
  final Future<bool> Function(BuildContext context) ensureAccess;

  /// Shared-storage folders opened as projects before (absolute paths).
  final Future<List<String>> Function()? openedBefore;
}

/// "Open a project" for a server on this phone (OpenCode inside the app, or
/// the one this app runs in Termux): the folders of its Ubuntu, browsed from
/// the projects folder; [list] says how they are read.
///
/// Built from kit parts only (revamp unit shared-work-1): the one sheet
/// frame ([KitSheet]) with its title, a scrolling body and pinned actions.
/// The body is the folder being shown (left to right, mono), then the
/// folders on one panel of [KitRow]s, then "New project" ([KitField] and
/// Create). A project (a git repository, a project OpenCode knows, or any
/// folder straight in the projects folder) opens with a tap, and its
/// chevron shows what is in it; any other folder is gone into with a tap.
/// "Up one folder" goes back up to `/`. Pinned below: "Open `<folder>`" for
/// the folder being shown, and "Enter a path".
///
/// States (project-folder-browser map record):
/// - loading: skeleton rows once a listing is slower than a touch answer;
///   after [KitMotion.escalateAfter] a word says the phone is still reading
///   (a Termux read may take up to 15 s), through [KitSince];
/// - empty: an empty folder says so; an empty projects folder (first run)
///   offers its first step, "New project", which puts the cursor in the
///   name field;
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
  final _name = TextEditingController();
  final _nameFocus = FocusNode(debugLabel: 'folder-browser-name');
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
  String? _problem;

  /// Looking at the phone's storage rather than the project space.
  bool _phone = false;
  bool _showHidden = false;
  bool _accessRefused = false;
  List<String> _opened = const [];

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
    _name.dispose();
    _nameFocus.dispose();
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
      _problem = null;
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

  Future<void> _newFolder(AppLocalizations l10n) async {
    final name = await showKitInputDialog(
      context,
      title: l10n.folderBrowserNewFolderTitle,
      label: l10n.projectFolderNameLabel,
      confirmLabel: l10n.projectFolderCreateAction,
      hint: l10n.projectFolderNameHint,
      helper: l10n.projectFolderNewProjectHelp(KitBidi.ltr(_path)),
      cancelLabel: l10n.projectFolderCancel,
      validate: (value) => projectFolderNameProblem(value.trim()),
      fieldKey: const ValueKey('phone-new-folder-name'),
      confirmKey: const ValueKey('phone-new-folder-create'),
    );
    if (name == null || !mounted) return;
    final clean = name.trim();
    final path = _join(_path, clean);
    final exists = _entries?.any((entry) => entry.name == clean) ?? false;
    KitSheet.close<FolderBrowserChoice>(
      context,
      exists ? FolderBrowserOpen(path) : FolderBrowserCreate(path),
    );
  }

  bool get _settled => !_loading && _error == null && _entries != null;

  void _create() {
    final name = _name.text.trim();
    final path = _join(_path, name);
    final problem =
        projectFolderNameProblem(name) ?? workspaceDirectoryProblem(path);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    // A name that is already a folder here simply opens it.
    final exists = _entries?.any((entry) => entry.name == name) ?? false;
    KitSheet.close<FolderBrowserChoice>(
      context,
      exists ? FolderBrowserOpen(path) : FolderBrowserCreate(path),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final media = MediaQuery.of(context);
    final maxHeight =
        (media.size.height - media.viewInsets.bottom - media.padding.top) * .9;
    // While the name is typed the keyboard leaves little room: the folders
    // step aside for the field, which keeps its place in the tree (and its
    // focus).
    final typing = media.viewInsets.bottom > 0;
    final canOpen = _settled && _canOpenHere;
    return SafeArea(
      child: Padding(
        padding: EdgeInsetsDirectional.only(bottom: media.viewInsets.bottom),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight),
          child: KitSheet(
            key: const ValueKey('in-app-projects'),
            title: l10n.projectFolderInAppTitle,
            // The route this sheet opens in draws the drag handle.
            handle: false,
            onClose: () => KitSheet.close<FolderBrowserChoice>(context),
            loading: _loading && !_showSkeleton,
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
            secondary: _phone && _settled
                ? KitAction(
                    key: const ValueKey('phone-new-folder'),
                    label: l10n.folderBrowserNewFolderHere,
                    icon: AppIconography.folderAdd,
                    onPressed: () => _newFolder(l10n),
                  )
                : null,
            tertiary: [
              KitAction(
                key: const ValueKey('in-app-enter-path'),
                label: l10n.projectFolderEnterPath,
                onPressed: () => KitSheet.close<FolderBrowserChoice>(
                  context,
                  FolderBrowserEnterPath(_path),
                ),
              ),
            ],
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (widget.phone != null) _places(context, l10n),
                _where(context, l10n, canOpen: canOpen),
                if (!typing) _folders(context, l10n),
                if (!_phone) _newProject(context, l10n),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Where to look: the project space or this phone's storage, and the
  /// plain note when the phone's storage was not allowed.
  Widget _places(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: tokens.space3),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitSectionLabel(
            l10n.folderBrowserPlaceLabel,
            margin: EdgeInsets.zero,
            gapBefore: 0,
          ),
          KitSegmented<bool>(
            key: const ValueKey('folder-browser-places'),
            semanticsLabel: l10n.folderBrowserPlaceLabel,
            selected: _phone,
            onChanged: _choosePlace,
            segments: [
              KitSegment(
                key: const ValueKey('place-projects'),
                value: false,
                label: l10n.folderBrowserPlaceProjects,
                icon: AppIconography.folders,
              ),
              KitSegment(
                key: const ValueKey('place-phone'),
                value: true,
                label: l10n.folderBrowserPlacePhone,
                icon: AppIconography.phone,
              ),
            ],
          ),
          if (_accessRefused && !_phone) ...[
            SizedBox(height: tokens.space3),
            KitNotice(
              key: const ValueKey('folder-browser-phone-refused'),
              icon: AppIconography.folderOpen,
              title: l10n.folderBrowserPhoneRefusedTitle,
              message: l10n.folderBrowserPhoneRefusedBody,
            ),
          ],
        ],
      ),
    );
  }

  /// The phone's storage: the trail from "Internal storage", each folder
  /// above the current one a tap back.
  Widget _trail(BuildContext context, AppLocalizations l10n) {
    final below = _path == PhoneStorageFolders.root
        ? const <String>[]
        : _path.substring(PhoneStorageFolders.root.length + 1).split('/');
    return KitBreadcrumb(
      breadcrumbKey: const ValueKey('folder-browser-path'),
      rootLabel: l10n.folderBrowserInternalStorage,
      segments: below,
      onSelected: (index) => _load(
        index < 0
            ? PhoneStorageFolders.root
            : '${PhoneStorageFolders.root}/${below.take(index + 1).join('/')}',
      ),
    );
  }

  /// The folder being shown, and why it cannot be opened when it cannot.
  Widget _where(
    BuildContext context,
    AppLocalizations l10n, {
    required bool canOpen,
  }) {
    final tokens = KitTokens.of(context);
    if (_phone) {
      return Padding(
        padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
        child: _trail(context, l10n),
      );
    }
    final entries = _entries;
    final String? note = canOpen || !_settled || entries!.isEmpty
        ? null
        : isProtectedWorkspaceDirectory(_path)
        ? l10n.folderBrowserHomeHere
        : _path == managedProjectsDirectory
        ? l10n.folderBrowserProjectsHere
        : null;
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: tokens.space3),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText.mono(
            _path,
            key: const ValueKey('folder-browser-path'),
            // One line that keeps the root and the folder's own name.
            cut: KitMonoCut.middle,
            tone: KitTextTone.secondary,
            semanticsLabel: l10n.folderBrowserCurrent(_path),
          ),
          if (note != null) ...[
            SizedBox(height: tokens.space2),
            KitText(
              note,
              key: const ValueKey('folder-browser-note'),
              role: KitTextRole.secondary,
            ),
          ],
        ],
      ),
    );
  }

  /// "Up one folder", the folders on one panel, and the state of the
  /// listing (loading, slow, empty, error).
  Widget _folders(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final parent = _parent;
    final entries = _visible;
    final error = _error;
    final Widget? state;
    if (_loading && _showSkeleton) {
      state = _loadingState(l10n);
    } else if (error != null) {
      state = _errorState(l10n, error, parent);
    } else if (_entries != null && entries.isEmpty) {
      state = _emptyState(l10n);
    } else {
      state = null;
    }
    final rows = state != null ? const <FolderEntry>[] : entries;
    // After an error the state itself offers the way up, next to its
    // words; the row would say it twice.
    final up = error == null ? parent : null;
    return KeyedSubtree(
      key: const ValueKey('folder-browser-list'),
      child: Padding(
        padding: EdgeInsetsDirectional.only(bottom: tokens.space4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_phone &&
                _path == PhoneStorageFolders.root &&
                _opened.isNotEmpty)
              ..._openedBefore(context, l10n),
            if (up != null || rows.isNotEmpty)
              KitRowGroup(
                margin: EdgeInsetsDirectional.zero,
                children: [
                  if (up != null) _upRow(context, l10n, up),
                  for (final entry in rows) _folderRow(context, l10n, entry),
                ],
              ),
            if (state != null) ...[
              if (up != null) SizedBox(height: tokens.space3),
              state,
            ],
            if (_phone) _hiddenSwitch(l10n),
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

  Widget _hiddenSwitch(AppLocalizations l10n) => Padding(
    padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space3),
    child: KitSwitchRow(
      switchKey: const ValueKey('folder-browser-hidden'),
      title: l10n.folderBrowserShowHidden,
      value: _showHidden,
      onChanged: (value) => setState(() => _showHidden = value),
    ),
  );

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

  Widget _upRow(BuildContext context, AppLocalizations l10n, String parent) =>
      KitRow(
        key: const ValueKey('folder-browser-up'),
        leading: KitRow.icon(context, AppIconography.chevronUp),
        title: l10n.folderBrowserUp,
        // Isolated left to right, so `/root` never reads `root/` in a
        // right-to-left sentence.
        supporting: TextSpan(
          text: _phone && parent == PhoneStorageFolders.root
              ? l10n.folderBrowserInternalStorage
              : KitBidi.ltr(parent),
          style: _phone && parent == PhoneStorageFolders.root
              ? null
              : KitText.styleFor(KitTextRole.mono),
        ),
        enabled: !_loading,
        onTap: () => _load(parent),
      );

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
              onPressed: _nameFocus.requestFocus,
            )
          : null,
    );
  }

  Widget _errorState(AppLocalizations l10n, Object error, String? parent) {
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
      // A folder the app may not read is left by going up.
      tertiary: [
        if (parent != null)
          KitAction(
            key: const ValueKey('folder-browser-error-up'),
            label: l10n.folderBrowserUp,
            icon: AppIconography.chevronUp,
            onPressed: () => _load(parent),
          ),
      ],
      details: productErrorDetails(error),
    );
  }

  /// "New project": a name, made inside the folder being shown.
  Widget _newProject(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSectionLabel(
          l10n.projectFolderNewProject,
          margin: EdgeInsets.zero,
          gapBefore: 0,
        ),
        KitField(
          key: const ValueKey('in-app-new-project-name'),
          controller: _name,
          focusNode: _nameFocus,
          label: l10n.projectFolderProjectNameLabel,
          hint: l10n.projectFolderNameHint,
          helper: l10n.projectFolderNewProjectHelp(KitBidi.ltr(_path)),
          error: _problem,
          textInputAction: TextInputAction.done,
          onChanged: (_) {
            if (_problem != null) setState(() => _problem = null);
          },
          onSubmitted: (_) => _create(),
        ),
        SizedBox(height: tokens.space2),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitButton.secondary(
            key: const ValueKey('in-app-new-project-create'),
            label: l10n.projectFolderCreateAction,
            icon: AppIconography.folderAdd,
            expand: false,
            onPressed: _create,
          ),
        ),
      ],
    );
  }
}
