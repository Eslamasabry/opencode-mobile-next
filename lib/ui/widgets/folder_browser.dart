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
import 'phone_project_kind.dart';
import 'product_states.dart' show productErrorDetails;

/// The pages of the sheet: it opens on [start]; the others swap in place.
enum _Step { start, name, browse, find }

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
    this.scan,
  });

  /// Lists the folders of the phone's internal storage.
  final FolderLister list;

  /// Runs the All files access consent when it is missing; true when the
  /// listing can be read.
  final Future<bool> Function(BuildContext context) ensureAccess;

  /// Starts "Find projects" over the phone's storage with a time cap; null
  /// leaves the menu item out.
  final PhoneProjectScan Function(Duration timeLimit)? scan;
}

/// "Open a project" for a server on this phone (OpenCode inside the app, or
/// the one this app runs in Termux): the folders of its Ubuntu, browsed from
/// the projects folder; [list] says how they are read.
///
/// Built from kit parts only: the one sheet frame ([KitSheet]). It opens on a
/// start page of three plain options and swaps its content in place (never a
/// second sheet or dialog):
///
/// 1. "New project", the prominent top row: the name step ("Creates
///    `<folder>/<name>`", "Create and open", a quiet "Change folder" that
///    picks another parent in the browser and comes back). The default folder
///    is the host's project space, or `Projects` on the phone's storage when
///    the host has none ([projectSpace]);
/// 2. "Search this phone", only where [phone] can scan (consent first): a
///    calm search step with a timer, a bar against the time cap, folders
///    checked and projects found, results as they come, "Stop", then
///    "`<m>` found in 0:09", "Look deeper" after the cap, and a way forward
///    when nothing is found; back cancels it;
/// 3. "Opened before": [recent] folders with a Git badge where there is a
///    `.git` (checked after, kept for the sheet's life); a tap opens one.
///
/// A quiet "Choose a folder" opens the browser: a back chevron that goes up
/// one folder (Back to the start page at the place's first folder), the
/// folder's name, a more menu ("Show hidden folders", "Enter a path"), the
/// place menu ("This phone", "Project space") when both exist, one panel of
/// folder rows, and the pinned "Open `<folder>`". A project (a repository, a
/// project OpenCode knows, or any folder straight in the projects folder)
/// opens with a tap and its chevron shows what is in it; any other folder is
/// gone into with a tap. A server that cannot list folders has no search row.
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
    this.recent,
    this.projectSpace = true,
  });

  /// The folders opened before (absolute paths, most recent first): the
  /// start page's "Opened before" rows. Null or empty hides the section.
  final Future<List<String>> Function()? recent;

  /// The host has a project space to put new projects in; without one they
  /// go in `Projects` on the phone's internal storage.
  final bool projectSpace;

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

  /// Which page of the sheet shows: it opens on [_Step.start], and every
  /// other page swaps in place (never a second sheet or dialog).
  _Step _step = _Step.start;

  /// The browser is picking the parent folder of a new project.
  bool _picking = false;

  /// Where "New project" makes the project: the host's project space, or
  /// `Projects` on the phone's storage when the host has none.
  late String _createIn = widget.projectSpace
      ? managedProjectsDirectory
      : '${PhoneStorageFolders.root}/Projects';

  /// The name field of the New project step.
  final TextEditingController _name = TextEditingController();
  String? _nameError;

  /// "Opened before" and which of them hold a repository (checked after,
  /// kept for the life of this sheet).
  List<String> _recent = const [];
  final Map<String, ({PhoneProjectKind? kind, bool git})> _info = {};

  /// "Search this phone": its results, kept for the life of this sheet only.
  PhoneProjectScanState? _scanState;

  FolderLister get _lister => _phone ? widget.phone!.list : widget.list;

  @override
  void initState() {
    super.initState();
    _load(_path);
    _loadKnown();
    _loadRecent();
  }

  @override
  void dispose() {
    _skeletonTimer?.cancel();
    _scanState?.dispose();
    _name.dispose();
    super.dispose();
  }

  Future<void> _loadRecent() async {
    final load = widget.recent;
    if (load == null) return;
    try {
      final paths = await load();
      if (!mounted) return;
      setState(() => _recent = paths);
      for (final path in paths) {
        unawaited(_probe(path));
      }
    } catch (_) {
      // Only the "Opened before" rows depend on it.
    }
  }

  /// What [path] is (its kind from the scanner's markers) and whether it
  /// holds a repository: the phone's folders are read here, the project
  /// space's from the listing of the folder above it. A failure leaves the
  /// folder plain, with no badge. Cached for the life of this sheet.
  Future<void> _probe(String path) async {
    ({PhoneProjectKind? kind, bool git}) info = (kind: null, git: false);
    try {
      if (PhoneStorageFolders.normalize(path) != null) {
        info = await PhoneProjectScanner.inspect(path);
      } else {
        final parent = BuiltinRootfsFolders.parentOf(path);
        if (parent != null) {
          final entries = await widget.list(parent);
          for (final entry in entries) {
            if (entry.path != path) continue;
            final found = PhoneProjectScanner.classify([
              ...?entry.inside,
              if (entry.isGit) '.git',
            ]);
            info = (kind: found?.kind, git: entry.isGit);
          }
        }
      }
    } catch (_) {
      info = (kind: null, git: false);
    }
    if (mounted) setState(() => _info[path] = info);
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
      !_picking &&
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

  /// "New project": the name step, in place.
  void _newProject() {
    _name.clear();
    setState(() {
      _step = _Step.name;
      _nameError = null;
    });
  }

  void _cancelNaming() => setState(() {
    _step = _Step.start;
    _nameError = null;
  });

  static bool _onPhone(String path) =>
      PhoneStorageFolders.normalize(path) != null;

  /// "Change folder": the browser, to pick the parent folder; "Use (folder)"
  /// comes back to the name step.
  Future<void> _changeFolder() async {
    setState(() {
      _picking = true;
      _step = _Step.browse;
    });
    await _browseAt(_createIn);
  }

  Future<void> _browseAt(String path) async {
    if (_onPhone(path) && !_phone) {
      await _choosePlace(true);
      if (_phone && path != PhoneStorageFolders.root) await _load(path);
    } else if (!_onPhone(path) && _phone) {
      await _choosePlace(false);
      if (path != _path) await _load(path);
    } else if (path != _path) {
      await _load(path);
    }
  }

  void _useFolder() => setState(() {
    _createIn = _path;
    _picking = false;
    _step = _Step.name;
  });

  /// Back from the browser: to the name step when it was picking a folder,
  /// else to the start page.
  void _leaveBrowse() => setState(() {
    _step = _picking ? _Step.name : _Step.start;
    _picking = false;
  });

  String? _nameProblem(String value) {
    final clean = value.trim();
    return projectFolderNameProblem(clean) ??
        (_onPhone(_createIn)
            ? null
            : workspaceDirectoryProblem(_join(_createIn, clean)));
  }

  void _submitName(AppLocalizations l10n) {
    final clean = _name.text.trim();
    final problem = _nameProblem(clean);
    if (problem != null) {
      setState(() => _nameError = problem);
      return;
    }
    final path = _join(_createIn, clean);
    // A name that is already a folder there simply opens it.
    final exists =
        _path == _createIn &&
        (_entries?.any((entry) => entry.name == clean) ?? false);
    KitSheet.close<FolderBrowserChoice>(
      context,
      exists ? FolderBrowserOpen(path) : FolderBrowserCreate(path),
    );
  }

  /// "Search this phone": the consent first when access is missing, then
  /// the search step. What an earlier search found is shown as it was; one
  /// that Stop or back cut short starts again.
  Future<void> _startSearch() async {
    final place = widget.phone;
    final start = place?.scan;
    if (place == null || start == null) return;
    final granted = await place.ensureAccess(context);
    if (!mounted) return;
    if (!granted) {
      setState(() => _accessRefused = true);
      return;
    }
    final state = _scanState ??= PhoneProjectScanState(start);
    setState(() {
      _accessRefused = false;
      _step = _Step.find;
    });
    if (!state.hasResult && !state.scanning) state.run();
  }

  void _stopFinding() {
    _scanState?.cancel();
    setState(() => _step = _Step.start);
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
          child: switch (_step) {
            _Step.start => _startStep(l10n),
            _Step.name => _nameStep(l10n),
            _Step.browse => _listStep(l10n),
            _Step.find => _scanStep(l10n),
          },
        ),
      ),
    );
  }

  /// The name of the new project, made inside [_createIn]; "Change folder"
  /// picks another parent in the browser.
  Widget _nameStep(AppLocalizations l10n) => KitSheet(
    key: const ValueKey('in-app-projects'),
    step: 'name',
    title: l10n.projectFolderNewProject,
    handle: false,
    leading: KitAction(
      key: const ValueKey('phone-new-folder-cancel'),
      label: l10n.kitTopBarBack,
      onPressed: _cancelNaming,
    ),
    onClose: () => KitSheet.close<FolderBrowserChoice>(context),
    primary: KitAction(
      key: const ValueKey('phone-new-folder-create'),
      calm: true,
      label: l10n.folderBrowserCreateAndOpen,
      icon: AppIconography.folderAdd,
      onPressed: () => _submitName(l10n),
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        KitField(
          fieldKey: const ValueKey('phone-new-folder-name'),
          label: l10n.projectFolderProjectNameLabel,
          controller: _name,
          hint: l10n.projectFolderNameHint,
          error: _nameError,
          autofocus: true,
          textInputAction: TextInputAction.done,
          onChanged: (value) => setState(() {
            if (_nameError != null) _nameError = _nameProblem(value);
          }),
          onSubmitted: (_) => _submitName(l10n),
        ),
        // One line: "Creates …/projects/<name> · Change", or "In /root/projects
        // · Change" while the name is empty. Cut at its start so the name
        // stays visible.
        Row(
          children: [
            Flexible(
              child: KitText(
                _name.text.trim().isEmpty
                    ? l10n.openProjectIn(KitBidi.ltr(_cutStart(_createIn, 30)))
                    : l10n.folderBrowserNewProjectCreates(
                        KitBidi.ltr(
                          _cutStart(_join(_createIn, _name.text.trim()), 30),
                        ),
                      ),
                role: KitTextRole.secondary,
                maxLines: 1,
              ),
            ),
            KitText(' · ', role: KitTextRole.secondary),
            KitTappable(
              key: const ValueKey('phone-new-folder-change'),
              label: l10n.openProjectChangeFolder,
              onTap: _changeFolder,
              child: KitText.link(l10n.openProjectChange),
            ),
          ],
        ),
      ],
    ),
  );

  /// What the sheet opens on: three plain options and nothing else. New
  /// project first, then the search (only where the host can be searched),
  /// then the folders opened before, and a quiet link to browse any folder.
  Widget _startStep(AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final canSearch = widget.phone?.scan != null;
    return KitSheet(
      key: const ValueKey('in-app-projects'),
      step: 'start',
      title: l10n.openProjectTitle,
      handle: false,
      onClose: () => KitSheet.close<FolderBrowserChoice>(context),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // New project and Search are siblings: two rows of one block.
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            children: [
              KitRow(
                key: const ValueKey('open-project-new'),
                leading: KitRow.badge(
                  context,
                  AppIconography.add,
                  accent: true,
                ),
                title: l10n.projectFolderNewProject,
                titleAccent: true,
                trailing: const KitChevron(),
                onTap: _newProject,
              ),
              if (canSearch)
                KitRow(
                  key: const ValueKey('open-project-search'),
                  leading: KitRow.badge(context, AppIconography.search),
                  title: l10n.openProjectSearchPhone,
                  trailing: const KitChevron(),
                  onTap: _startSearch,
                ),
            ],
          ),
          if (_accessRefused)
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space2),
              child: KitText(
                l10n.folderBrowserPhoneRefused,
                key: const ValueKey('folder-browser-phone-refused'),
                role: KitTextRole.secondary,
              ),
            ),
          if (_recent.isNotEmpty) ...[
            KitSectionLabel(l10n.folderBrowserOpenedBefore),
            KitRowGroup(
              margin: EdgeInsetsDirectional.zero,
              children: [
                for (final (index, path) in _recent.indexed)
                  _recentRow(l10n, index, path),
              ],
            ),
          ],
          SizedBox(height: tokens.space4),
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            children: [
              KitRow(
                key: const ValueKey('open-project-browse'),
                leading: KitRow.icon(context, AppIconography.folders),
                title: l10n.openProjectChooseFolder,
                trailing: const KitChevron(),
                onTap: () => setState(() {
                  _picking = false;
                  _step = _Step.browse;
                }),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _recentRow(AppLocalizations l10n, int index, String path) {
    final info = _info[path];
    return KitRow(
      key: ValueKey('open-project-recent-$index'),
      leading: KitRow.icon(context, _kindIcon(info?.kind)),
      title: _nameOf(path),
      supporting: TextSpan(
        text:
            '${_kindLabel(l10n, info?.kind)} · ${KitBidi.ltr(_cutStart(path))}',
      ),
      trailing: _badgeAndChevron(l10n, info?.git ?? false),
      onTap: () =>
          KitSheet.close<FolderBrowserChoice>(context, FolderBrowserOpen(path)),
    );
  }

  /// "Git" with a branch glyph, on every git folder, in the neutral chip.
  Widget _badgeAndChevron(AppLocalizations l10n, bool git) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      if (git) ...[
        KitChip(label: l10n.phoneScanGit, icon: AppIconography.branch),
        SizedBox(width: KitTokens.of(context).space2),
      ],
      const KitChevron(),
    ],
  );

  static IconData _kindIcon(PhoneProjectKind? kind) =>
      phoneProjectKindIcon(kind);

  /// A path cut at its start when long, so the folders nearest the project
  /// stay readable.
  static String _cutStart(String path, [int max = 36]) {
    if (path.length <= max) return path;
    final tail = path.substring(path.length - (max - 1));
    final slash = tail.indexOf('/');
    return '…${slash > 0 ? tail.substring(slash) : tail}';
  }

  /// m:ss, as the search's timer reads.
  static String _clock(Duration time) {
    final seconds = time.inSeconds;
    return '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';
  }

  /// The search, in place: a calm page with the time, a bar against the time
  /// cap, what has been checked and found so far, and the projects as they
  /// come. One tap on a project opens it, as "Open" does for a folder.
  Widget _scanStep(AppLocalizations l10n) {
    final state = _scanState!;
    return ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final found = state.projects;
        final end = state.end;
        final seconds = state.limit.inSeconds;
        final running = state.scanning;
        final time = _clock(state.elapsed);
        final String? subtitle = running
            ? time
            : end == PhoneScanEnd.completed && found.isEmpty
            ? null
            : end == PhoneScanEnd.timedOut
            ? l10n.phoneScanStopped(seconds, found.length)
            : end == PhoneScanEnd.limited
            ? l10n.phoneScanFirstShown(found.length)
            : l10n.phoneScanDoneIn(found.length, time);
        final deeper = end == PhoneScanEnd.timedOut && seconds < 30;
        final tokens = KitTokens.of(context);
        return KitSheet(
          key: const ValueKey('in-app-projects'),
          step: 'find',
          title: running ? l10n.phoneScanSearching : l10n.phoneScanTitle,
          subtitle: subtitle,
          handle: false,
          onClose: () => KitSheet.close<FolderBrowserChoice>(context),
          leading: KitAction(
            key: const ValueKey('phone-scan-back'),
            label: l10n.kitTopBarBack,
            onPressed: _stopFinding,
          ),
          bar: running || deeper,
          primary: running
              ? KitAction(
                  key: const ValueKey('phone-scan-stop'),
                  label: l10n.phoneScanStop,
                  neutral: true,
                  onPressed: state.cancel,
                )
              : deeper
              ? KitAction(
                  key: const ValueKey('phone-scan-deeper'),
                  label: l10n.phoneScanLookDeeper,
                  onPressed: () =>
                      state.run(timeLimit: const Duration(seconds: 30)),
                )
              : null,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (running) ...[
                // Moving, not filling: the search has no known end. Still under
                // reduced motion.
                KitProgressView(
                  progress: KitProgress.waiting(
                    key: const ValueKey('phone-scan-progress'),
                    caption: l10n.phoneScanChecked(state.checked, found.length),
                    semanticsLabel: l10n.phoneScanSearching,
                  ),
                ),
                SizedBox(height: tokens.space3),
              ],
              if (found.isNotEmpty)
                KitRowGroup(
                  margin: EdgeInsetsDirectional.zero,
                  children: [
                    for (final project in found)
                      _projectRow(context, l10n, project),
                  ],
                )
              else if (!running)
                KitStateView(
                  key: const ValueKey('phone-scan-empty'),
                  size: KitStateSize.inline,
                  icon: AppIconography.folderOpen,
                  illustration: const KitFoldersOpenScene(),
                  title: l10n.phoneScanEmptyTitle,
                  body: l10n.phoneScanEmptyBody,
                ),
            ],
          ),
        );
      },
    );
  }

  static String _kindLabel(AppLocalizations l10n, PhoneProjectKind? kind) =>
      phoneProjectKindLabel(l10n, kind);

  /// The folder the project is in, from the storage's top, cut at its start
  /// when long so the end (the nearest folders) stays readable.
  static String _where(String path) {
    // The phone's storage reads from its top; the project space and other
    // Ubuntu folders read as the browser shows them (`/root/projects`).
    if (!path.startsWith('${PhoneStorageFolders.root}/')) {
      return _cutStart(BuiltinRootfsFolders.parentOf(path) ?? path, 34);
    }
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
    leading: KitRow.icon(context, _kindIcon(project.kind)),
    title: project.name,
    supporting: TextSpan(
      text:
          '${_kindLabel(l10n, project.kind)} · ${KitBidi.ltr(_where(project.path))}',
    ),
    trailing: _badgeAndChevron(l10n, project.hasGit),
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
              key: const ValueKey('folder-browser-start'),
              label: l10n.kitTopBarBack,
              onPressed: _leaveBrowse,
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
      primary: _picking
          ? (_settled &&
                    (_phone ||
                        _canOpenHere ||
                        _path == managedProjectsDirectory)
                ? KitAction(
                    key: const ValueKey('folder-browser-use'),
                    label: l10n.openProjectUseFolder(_nameOf(_path)),
                    icon: AppIconography.folderOpen,
                    onPressed: _useFolder,
                  )
                : null)
          : canOpen
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
              onPressed: _newProject,
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
