import 'dart:async';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../domain/delimited_text.dart';
import '../../domain/static_svg.dart';
import '../../feedback/bug_report.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/local_pdf.dart';
import '../../state/connection.dart';
import '../../state/review_handoff.dart';
import '../../state/shared_storage_gate.dart';
import '../app_theme.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_breadcrumb.dart';
import '../kit/kit_buttons.dart' show KitAction;
import '../kit/kit_copy.dart';
import '../kit/kit_divider.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart' show KitChevron;
import '../kit/kit_screen.dart';
import '../kit/kit_scrollbar.dart' show KitScrollArea;
import '../kit/kit_search_field.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_status_line.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../kit/kit_undo.dart';
import '../kit/kit_viewer.dart';
import '../kit/motion/kit_refresh.dart';
import '../kit/scenes/states_scenes.dart';
import '../widgets/file_preview.dart' show FilePreviewData;
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/reader_preferences.dart';
import 'review_workspace.dart';
import 'shared_storage_access_flow.dart';

/// Project file browser backed by `/file`, with name search (`/find/file`)
/// and the kit's one viewer (map pages files, files-row-actions-sheet,
/// files-file-viewer-sheet, files-changes-sheet).
enum _FileSurface { files, symbols }

typedef ProjectFileAttachment =
    Future<void> Function(String path, FilePreviewData data);
typedef ProjectReviewPrompt = void Function(String prompt);

/// Opens the review of the project's uncommitted changes. Shared by Files and
/// the Project tab's "Changes" row so both doors show the same review.
Future<String?> pushWorkingTreeReview(
  BuildContext context,
  ConnectionController controller, {
  ReviewHandoffSession? handoff,
  String? initialFile,
}) {
  final copy = readerL10n(context);
  return pushKitPage<String>(
    context,
    (_) => ReviewWorkspace(
      initialScope: ReviewDiffScope.workingTree,
      initialFile: initialFile,
      handoff: handoff,
      loadWorkingTreeDiffs: () async {
        final repository = await controller.prepareActionRepository();
        if (repository == null) {
          throw ProductException(copy.readerUiReconnecting);
        }
        return repository.listVcsDiffs(VcsDiffMode.workingTree);
      },
    ),
  );
}

/// Legacy return path, used only when there is no handoff session: the
/// review workspace pops with formatted text that goes to the host chat if
/// one supplied a callback, and to the clipboard otherwise.
///
/// The clipboard path confirms itself through [KitCopy] (announced once, no
/// snackbar). The chat path returns the confirmation for the caller to show
/// in place (a [KitNotice]); null when there is nothing to say.
Future<String?> deliverReviewPrompt(
  BuildContext context,
  String? prompt, [
  ProjectReviewPrompt? callback,
]) async {
  if (prompt == null || prompt.trim().isEmpty) return null;
  final reviewPrompt = prompt.trim();
  final copy = readerL10n(context);
  if (callback != null) {
    callback(reviewPrompt);
    return copy.readerUiCommentAdded;
  }
  await KitCopy.copy(
    context,
    reviewPrompt,
    announcement: copy.readerUiCommentCopied,
    redact: false,
  );
  return null;
}

/// Lets a containing navigation shell offer Back to its active Files tab.
class FilesBackController {
  bool Function()? _handler;
  bool handleBack() => _handler?.call() ?? false;
}

class FilesScreen extends StatefulWidget {
  final ConnectionController controller;
  final ProjectFileAttachment? onAttachFile;
  final ProjectReviewPrompt? onReviewPrompt;

  /// UX-103: when Files is opened from a chat, add-to-prompt affordances
  /// stage structured references on that session's composer. Without it —
  /// Files opened from the workspace home — those affordances are hidden and
  /// review comments fall back to the clipboard.
  final ReviewHandoffSession? handoff;

  /// Bumped by the shell's Ctrl+F while this destination is showing. Desktop
  /// only in practice: nothing dispatches app shortcuts off desktop.
  final ValueListenable<int>? focusSearchSignal;
  final FilesBackController? backController;

  /// The host's top bar, given the open folder's name (null at the project
  /// root), so the folder is the title (map files: "current folder as the
  /// title"). Null: the host draws its own bar, or none.
  final KitTopBar Function(String? folder)? topBar;

  const FilesScreen({
    super.key,
    required this.controller,
    this.onAttachFile,
    this.onReviewPrompt,
    this.handoff,
    this.focusSearchSignal,
    this.backController,
    this.topBar,
  });

  @override
  State<FilesScreen> createState() => _FilesScreenState();
}

/// A message about something the person just did here, shown in place
/// above the list (the kit shell has no Scaffold for a snackbar).
class _FilesNotice {
  const _FilesNotice(this.message, {this.error = false});

  final String message;
  final bool error;
}

/// One opened file: its path, the line to show, the source the viewer
/// reads (made once, so a rebuild does not reload it) and the content once
/// it arrived (Save and Attach reuse it).
class _ViewerSession {
  _ViewerSession(this.path, this.initialLine);

  final String path;
  final int? initialLine;
  FileContent? content;
  late final KitViewerSource source;

  String get name => path.split('/').last;
}

class _FilesScreenState extends State<FilesScreen> {
  List<FileNode>? _entries;
  List<WorkspaceSymbol>? _symbols;
  Map<String, VersionControlFile> _fileStatuses = const {};
  _FileSurface _surface = _FileSurface.files;
  String _path = '';
  String? _error;
  bool _loading = false;
  DateTime? _loadStartedAt;
  String? _fileStatusesError;
  String? _searchOriginPath;
  bool _showHidden = false;
  _FilesNotice? _notice;

  /// Set when this shared-storage folder lists only hidden entries because
  /// the host that reads it lacks storage access: the list says so instead
  /// of looking like an empty project.
  SharedStorageBlock _storageBlock = SharedStorageBlock.none;

  /// The file open in the detail pane (expanded windows only).
  _ViewerSession? _viewer;
  bool _viewerOpen = false;
  bool _attaching = false;
  bool _downloading = false;

  /// Set while the screen clears the field itself, so the field's own
  /// "cleared" callback does not start a second load.
  bool _suppressSearch = false;

  final _search = TextEditingController();
  final _searchFocus = FocusNode(debugLabel: 'files-search');
  ServerOperationsGateway? _repository;
  int _locationRevision = -1;
  int _controllerLocationRevision = -1;
  int _dataRefreshRevision = -1;
  int _requestGeneration = 0;
  int _fileStatusesGeneration = 0;

  static const _maxViewerChars = 200000;

  bool _matchesFileScope({
    required String? profileID,
    required int locationRevision,
    ServerGateway? api,
    ServerOperationsGateway? repository,
  }) =>
      mounted &&
      widget.controller.profile?.id == profileID &&
      widget.controller.locationRevision == locationRevision &&
      (api == null || identical(widget.controller.api, api)) &&
      (repository == null ||
          identical(widget.controller.repository, repository));

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_controllerChanged);
    _search.addListener(_searchChanged);
    widget.focusSearchSignal?.addListener(_focusSearch);
    widget.backController?._handler = _handleBack;
    _captureLocation();
    _load('');
  }

  @override
  void didUpdateWidget(FilesScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.backController != widget.backController) {
      oldWidget.backController?._handler = null;
      widget.backController?._handler = _handleBack;
    }
    if (oldWidget.focusSearchSignal != widget.focusSearchSignal) {
      oldWidget.focusSearchSignal?.removeListener(_focusSearch);
      widget.focusSearchSignal?.addListener(_focusSearch);
    }
  }

  /// Ctrl+F: put the caret in the find field and select what is already there
  /// so a second search replaces the first, the way every desktop find does.
  void _focusSearch() {
    if (!mounted) return;
    _searchFocus.requestFocus();
    _search.selection = TextSelection(
      baseOffset: 0,
      extentOffset: _search.text.length,
    );
  }

  void _searchChanged() {
    if (mounted) setState(() {});
  }

  void _clearSearchText() {
    _suppressSearch = true;
    try {
      _search.clear();
    } finally {
      _suppressSearch = false;
    }
  }

  void _captureLocation() {
    _repository = widget.controller.repository;
    _locationRevision = _revisionOf(_repository);
    _controllerLocationRevision = widget.controller.locationRevision;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
  }

  void _controllerChanged() {
    final repository = widget.controller.repository;
    final revision = _revisionOf(repository);
    final controllerLocationChanged =
        _controllerLocationRevision != widget.controller.locationRevision;
    final dataRefreshChanged =
        _dataRefreshRevision != widget.controller.dataRefreshRevision;
    if (identical(repository, _repository) &&
        revision == _locationRevision &&
        !controllerLocationChanged &&
        !dataRefreshChanged) {
      return;
    }
    _repository = repository;
    _locationRevision = revision;
    _controllerLocationRevision = widget.controller.locationRevision;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _requestGeneration++;
    _fileStatusesGeneration++;
    if (widget.controller.lifecycleSuspended) {
      setState(() {
        _loading = false;
        _entries ??= const [];
      });
      return;
    }
    if (widget.controller.connectionLoading &&
        !dataRefreshChanged &&
        !controllerLocationChanged) {
      return;
    }
    if (dataRefreshChanged && !controllerLocationChanged) {
      if (_search.text.trim().isNotEmpty) {
        if (_surface == _FileSurface.symbols) {
          _searchSymbols(_search.text);
        } else {
          _searchFiles(_search.text);
        }
      } else if (_surface == _FileSurface.files) {
        _load(_path);
      } else {
        setState(() {
          _symbols = null;
          _error = null;
        });
      }
      return;
    }
    _clearSearchText();
    _searchOriginPath = null;
    setState(() {
      _entries = null;
      _path = '';
      _viewer = null;
      _notice = null;
      _symbols = null;
      _error = null;
      _fileStatuses = const {};
      _fileStatusesError = null;
    });
    if (_surface == _FileSurface.files) {
      _load('');
    } else {
      setState(() => _loading = false);
    }
  }

  int _revisionOf(ServerOperationsGateway? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  String _relativePath(String path) =>
      path.split('/').where((component) => component.isNotEmpty).join('/');

  void _navigateTo(String path) {
    _searchOriginPath = null;
    _clearSearchText();
    _load(path);
  }

  bool get _canNavigateBack =>
      _search.text.isNotEmpty ||
      (_surface == _FileSurface.files && _path.isNotEmpty);

  bool _handleBack() {
    // The shell Scaffold can consume MediaQuery's keyboard inset for its body.
    // Read the view inset as well so the embedded Files tab still handles IME Back.
    if (_keyboardOpen) {
      FocusManager.instance.primaryFocus?.unfocus();
      return true;
    }
    if (!_canNavigateBack) return false;
    if (_search.text.isNotEmpty) {
      _clearSearch();
    } else {
      final parts = _path.split('/');
      _navigateTo(parts.take(parts.length - 1).join('/'));
    }
    return true;
  }

  bool get _keyboardOpen =>
      MediaQuery.viewInsetsOf(context).bottom > 0 ||
      View.of(context).viewInsets.bottom > 0;

  void _selectSurface(_FileSurface surface) {
    if (_surface == surface) return;
    _requestGeneration++;
    final origin = _searchOriginPath ?? _path;
    _clearSearchText();
    _searchOriginPath = null;
    setState(() {
      _surface = surface;
      _loading = false;
      _error = null;
      if (surface == _FileSurface.symbols) _symbols = null;
    });
    if (surface == _FileSurface.files) unawaited(_load(origin));
  }

  /// The field's settled query (KitSearchField waits for typing to settle,
  /// and reports a clear at once).
  void _onSearchChanged(String query) {
    if (_suppressSearch) return;
    if (_surface == _FileSurface.symbols) {
      unawaited(_searchSymbols(query));
    } else {
      unawaited(_searchFiles(query));
    }
  }

  void _clearSearch() {
    _clearSearchText();
    if (_surface == _FileSurface.symbols) {
      _requestGeneration++;
      setState(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final origin = _searchOriginPath ?? _path;
    _searchOriginPath = null;
    _load(origin);
  }

  void _startLoading() {
    _loading = true;
    _loadStartedAt = DateTime.now();
    _error = null;
  }

  /// A listing of only hidden entries (or none) in a shared-storage folder
  /// may be Android hiding the rest: ask which host lacks access.
  Future<void> _checkStorageAccess(List<FileNode> nodes, int generation) async {
    if (!nodes.every((node) => node.name.startsWith('.'))) return;
    final block = await SharedStorageGate.blockFor(
      widget.controller.profile,
      widget.controller.directory,
    );
    if (!mounted ||
        generation != _requestGeneration ||
        block == _storageBlock) {
      return;
    }
    setState(() => _storageBlock = block);
  }

  Future<void> _allowStorageAccess() async {
    final outcome = await SharedStorageAccessFlow.resolve(
      context,
      _storageBlock,
    );
    if (outcome == SharedStorageOutcome.proceed && mounted) {
      await _refreshFiles();
    }
  }

  Widget _storageAccessNotice(AppLocalizations l10n) {
    final termux = _storageBlock == SharedStorageBlock.termuxAccess;
    return KitRefresh(
      onRefresh: _refreshFiles,
      child: KitStateView(
        key: const ValueKey('files-storage-access'),
        icon: AppIconography.folderOpen,
        title: l10n.filesAccessNeededTitle,
        body: termux
            ? l10n.filesAccessNeededTermuxBody
            : l10n.filesAccessNeededBody,
        primary: KitAction(
          key: const ValueKey('files-storage-access-action'),
          label: termux ? l10n.storageTermuxAllow : l10n.storageAccessAllow,
          onPressed: () => unawaited(_allowStorageAccess()),
        ),
      ),
    );
  }

  Future<void> _load(String path) async {
    path = _relativePath(path);
    final generation = ++_requestGeneration;
    setState(() {
      _startLoading();
      _notice = null;
      _storageBlock = SharedStorageBlock.none;
      // Keep the destination through errors so Retry opens the same folder.
      if (_path != path) _entries = null;
      _path = path;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final nodes = await api.listFiles(path);
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
        _entries = nodes
            .map(
              (node) => FileNode(
                name: node.name,
                path: _relativePath(node.path),
                isDir: node.isDir,
              ),
            )
            .toList();
        _path = path;
      });
      unawaited(_checkStorageAccess(nodes, generation));
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _searchFiles(String q) async {
    if (q.trim().isEmpty) {
      final origin = _searchOriginPath ?? _path;
      _searchOriginPath = null;
      await _load(origin);
      return;
    }
    _searchOriginPath ??= _path;
    final generation = ++_requestGeneration;
    setState(_startLoading);
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      if (api == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final repository = widget.controller.repository;
      if (repository != null) {
        unawaited(_loadFileStatuses(repository));
      }
      final results = await api.findFile(q.trim());
      if (!mounted || generation != _requestGeneration) return;
      setState(() {
        _entries = results.map((r) {
          final path = _relativePath(r);
          final parts = path.split('/');
          return FileNode(name: parts.last, path: path, isDir: false);
        }).toList();
      });
    } catch (e) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(e));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _refreshFiles() => _searchFiles(_search.text);

  /// Change marks load with every listing, so a failure is a condition on
  /// the status line, not a second Try again beside the list's own (map
  /// files: "two retries on failure").
  Future<void> _loadFileStatuses(ServerOperationsGateway repository) async {
    final generation = ++_fileStatusesGeneration;
    final profileID = widget.controller.profile?.id;
    final locationRevision = widget.controller.locationRevision;
    if (!_matchesFileScope(
      profileID: profileID,
      locationRevision: locationRevision,
      repository: repository,
    )) {
      return;
    }
    try {
      final statuses = await repository.listFileStatuses();
      if (generation != _fileStatusesGeneration ||
          !_matchesFileScope(
            profileID: profileID,
            locationRevision: locationRevision,
            repository: repository,
          )) {
        return;
      }
      setState(() {
        _fileStatusesError = null;
        _fileStatuses = {
          for (final status in statuses)
            _relativePath(status.path): VersionControlFile(
              path: _relativePath(status.path),
              status: status.status,
              additions: status.additions,
              deletions: status.deletions,
            ),
        };
      });
    } catch (error) {
      if (generation != _fileStatusesGeneration ||
          !_matchesFileScope(
            profileID: profileID,
            locationRevision: locationRevision,
            repository: repository,
          )) {
        return;
      }
      setState(() {
        _fileStatusesError = _fileStatuses.isEmpty
            ? readerL10n(context).readerUiIndicatorsUnavailable
            : readerL10n(context).readerUiIndicatorsFailed;
      });
    }
  }

  Future<void> _searchSymbols(String query) async {
    final value = query.trim();
    if (value.isEmpty) {
      _requestGeneration++;
      setState(() {
        _symbols = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final generation = ++_requestGeneration;
    setState(_startLoading);
    try {
      await widget.controller.prepareActionTransport();
      if (!mounted || generation != _requestGeneration) return;
      final repository = widget.controller.repository;
      if (repository == null) {
        throw ProductException(readerL10n(context).readerUiDisconnected);
      }
      final results = await repository.findWorkspaceSymbols(value);
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _symbols = results);
    } catch (error) {
      if (!mounted || generation != _requestGeneration) return;
      setState(() => _error = productErrorText(error));
    } finally {
      if (mounted && generation == _requestGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  // --- The viewer -------------------------------------------------------------

  /// Opens a file in the kit's one viewer: beside the list where the window
  /// shows two panes, otherwise as the viewer's own sheet or page.
  void _openFile(FileNode node, {int? initialLine}) {
    final session = _newSession(_relativePath(node.path), initialLine);
    if (KitScreen.showsDetail(context)) {
      setState(() => _viewer = session);
      return;
    }
    unawaited(_showViewer(session));
  }

  _ViewerSession _newSession(String path, int? initialLine) {
    final session = _ViewerSession(path, initialLine);
    session.source = KitViewerSource.load(() => _loadViewerContent(session));
    return session;
  }

  Future<void> _showViewer(_ViewerSession session) async {
    final preferences = ReaderPreferencesScope.maybeOf(context);
    _viewerOpen = true;
    try {
      await showKitViewer(
        context,
        name: session.name,
        path: _viewerPath(session),
        source: session.source,
        primary: _viewerPrimary(session),
        more: _viewerMore(session, embedded: false),
        wrap: preferences?.value.wrapCode,
        onWrapChanged: preferences == null
            ? null
            : (wrap) =>
                  unawaited(saveReaderPreferences(context, wrapCode: wrap)),
        viewerKey: const ValueKey('files-viewer'),
      );
    } finally {
      _viewerOpen = false;
    }
  }

  String _viewerPath(_ViewerSession session) => session.initialLine == null
      ? session.path
      : readerL10n(
          context,
        ).readerUiPathLine(session.path, session.initialLine!);

  /// The one labelled action: Add to prompt when Files came from a
  /// conversation, otherwise Attach when the host can take a file.
  KitAction? _viewerPrimary(_ViewerSession session) {
    final l10n = readerL10n(context);
    if (widget.handoff != null) {
      return KitAction(
        key: const Key('project-file-add-reference'),
        label: l10n.readerUiAddReference,
        icon: AppIconography.link,
        onPressed: () => _stageProjectFile(session.path, session.initialLine),
      );
    }
    if (widget.onAttachFile != null) {
      return KitAction(
        key: const Key('project-file-attach'),
        label: l10n.readerUiAttachPrompt,
        icon: AppIconography.attach,
        working: _attaching,
        onPressed: () => unawaited(_attachFromViewer(session)),
      );
    }
    return null;
  }

  List<KitMenuItem> _viewerMore(
    _ViewerSession session, {
    required bool embedded,
  }) {
    final l10n = readerL10n(context);
    final change = _fileStatuses[session.path];
    return [
      if (widget.handoff != null && widget.onAttachFile != null)
        KitMenuItem(
          key: const Key('project-file-attach'),
          // Named apart from "Add to prompt": this one uploads the file's
          // contents with the message.
          label: l10n.readerUiAttachPrompt,
          icon: AppIconography.attach,
          enabled: !_attaching,
          onSelected: () => unawaited(_attachFromViewer(session)),
        ),
      KitMenuItem(
        key: const Key('project-file-download'),
        label: l10n.fileSave,
        icon: AppIconography.download,
        enabled: !_downloading,
        onSelected: () => unawaited(_download(session)),
      ),
      if (embedded)
        KitMenuItem(
          key: const Key('project-file-reload'),
          label: l10n.fileReload,
          icon: AppIconography.retry,
          onSelected: () => setState(
            () => _viewer = _newSession(session.path, session.initialLine),
          ),
        ),
      if (change != null)
        KitMenuItem(
          key: const Key('project-file-review'),
          label: l10n.readerUiOpenReview,
          icon: AppIconography.review,
          onSelected: () => unawaited(
            _reviewFileChange(
              FileNode(name: session.name, path: session.path, isDir: false),
            ),
          ),
        ),
      KitMenuItem.copy(
        key: const Key('project-file-copy-path'),
        label: l10n.readerUiCopyPath,
        text: () => session.path,
      ),
    ];
  }

  /// Reads [path] from the server for the scope Files is in now. Null when
  /// the server or project changed while it was read: nothing from the old
  /// scope may land in the new one.
  Future<FileContent?> _fetchScoped(String path) async {
    final copy = readerL10n(context);
    final profileID = widget.controller.profile?.id;
    final locationRevision = widget.controller.locationRevision;
    final initialApi = widget.controller.api;
    final api = await widget.controller.prepareActionTransport();
    if (api == null) throw ProductException(copy.readerUiDisconnected);
    if ((initialApi != null && !identical(api, initialApi)) ||
        !_matchesFileScope(
          profileID: profileID,
          locationRevision: locationRevision,
          api: api,
        )) {
      return null;
    }
    final content = await api.fileContent(path);
    if (!_matchesFileScope(
      profileID: profileID,
      locationRevision: locationRevision,
      api: api,
    )) {
      return null;
    }
    return content;
  }

  Future<KitViewerContent> _loadViewerContent(_ViewerSession session) async {
    final copy = readerL10n(context);
    final content = await _fetchScoped(session.path);
    if (content == null) throw ProductException(copy.filesViewerScopeChanged);
    session.content = content;
    return _viewerContent(session.name, content, session.initialLine);
  }

  Future<void> _attachFromViewer(_ViewerSession session) async {
    final action = widget.onAttachFile;
    if (action == null || _attaching) return;
    final content = session.content;
    if (content == null) return _attachFile(session.path);
    setState(() => _attaching = true);
    try {
      await action(session.path, _exportData(session.path, content));
      if (!mounted) return;
      // Attached: back to the tree, which says where the file went.
      if (_viewerOpen) await Navigator.of(context).maybePop();
      if (!mounted) return;
      setState(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiAttachedReturn(session.name),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) setState(() => _attaching = false);
    }
  }

  Future<void> _download(_ViewerSession session) async {
    if (_downloading) return;
    setState(() => _downloading = true);
    try {
      final content = session.content ?? await _fetchScoped(session.path);
      if (content == null || !mounted) return;
      final data = _exportData(session.path, content);
      final bytes = data.exportBytes;
      if (bytes == null) return;
      final savedPath = await FilePicker.saveFile(
        dialogTitle: readerL10n(context).readerUiSaveNamed(data.name),
        fileName: data.name,
        bytes: bytes,
      );
      if (!mounted || savedPath == null) return;
      setState(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiSavedDevice(data.name),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  void _fail(Object error) => setState(
    () => _notice = _FilesNotice(productErrorText(error), error: true),
  );

  static FilePreviewData _exportData(String path, FileContent content) =>
      FilePreviewData(
        name: path.split('/').last,
        mimeType: content.mimeType,
        bytes: content.isBinary ? content.bytes() : null,
        text: content.isBinary ? null : content.content,
      );

  /// What the viewer shows for one file: the data only; drawing is the
  /// kit's (KitViewer). Parsing stays in lib/domain and lib/platform.
  static Future<KitViewerContent> _viewerContent(
    String name,
    FileContent content,
    int? initialLine,
  ) async {
    final data = _exportData(name, content);
    final bytes = data.bytes;
    if (data.isRasterImage && bytes != null && bytes.isNotEmpty) {
      return KitViewerContent.image(bytes, semanticsLabel: name);
    }
    final text = data.text;
    if (text != null) {
      var shown = text;
      var truncated = false;
      if (shown.length > _maxViewerChars) {
        var end = _maxViewerChars;
        final unit = shown.codeUnitAt(end - 1);
        if (unit >= 0xD800 && unit <= 0xDBFF) end--;
        shown = shown.substring(0, end);
        truncated = true;
      }
      final ext = _extension(name);
      final separator = data.separator;
      if (separator != null && initialLine == null) {
        final table = DelimitedText.parse(text, separator);
        if (table.failure == null) {
          return KitViewerContent.delimited(
            table.rows,
            original: text,
            truncated: table.hasMore,
          );
        }
      }
      if (data.mimeType == 'image/svg+xml' && initialLine == null) {
        final svg = StaticSvg.parse(text);
        if (svg != null) {
          return KitViewerContent.svg(svg.source, original: text);
        }
      }
      if ((ext == 'md' || ext == 'markdown') && initialLine == null) {
        return KitViewerContent.markdown(shown, truncated: truncated);
      }
      if (initialLine != null || _isCode(ext)) {
        return KitViewerContent.code(
          shown,
          language: ext.isEmpty ? null : ext,
          truncated: truncated,
          initialLine: initialLine,
        );
      }
      return KitViewerContent.text(shown, truncated: truncated);
    }
    if (data.mimeType == 'application/pdf' &&
        bytes != null &&
        bytes.isNotEmpty) {
      try {
        return await _pdfContent(bytes);
      } catch (_) {
        // No renderer here (or the file is refused): say what it is.
      }
    }
    return KitViewerContent.binary(
      mimeType: data.mimeType,
      byteLength: data.byteLength,
    );
  }

  /// Renders the first page to learn the page count, then each page only
  /// while the viewer shows it (LocalPdf, one bounded request per page).
  static Future<KitViewerContent> _pdfContent(Uint8List bytes) async {
    final first = await LocalPdf.render(bytes, 0, LocalPdf.requestID());
    final requests = <int, String>{};
    KitPdfPage page(Uint8List png) {
      final header = ByteData.sublistView(png);
      return KitPdfPage(
        bytes: png,
        width: header.getUint32(16),
        height: header.getUint32(20),
      );
    }

    return KitViewerContent.pdf(
      pageCount: first.pageCount.clamp(1, LocalPdf.maxPages),
      renderPage: (index, _) async {
        if (index == 0) return page(first.png);
        final id = LocalPdf.requestID();
        requests[index] = id;
        try {
          return page((await LocalPdf.render(bytes, index, id)).png);
        } finally {
          requests.remove(index);
        }
      },
      cancelPage: (index) {
        final id = requests.remove(index);
        if (id != null) unawaited(LocalPdf.cancel(id));
      },
    );
  }

  static String _extension(String name) {
    final dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
  }

  static bool _isCode(String ext) =>
      _fileTypeIcon('x.$ext') == AppIconography.code ||
      _fileTypeIcon('x.$ext') == AppIconography.dataObject;

  /// A plain project file staged as a reference: the agent is pointed at the
  /// path (and the line the user was reading), nothing is uploaded.
  void _stageProjectFile(String path, int? line) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final change = _fileStatuses[path];
    _stageReference(
      ReviewReference(
        id: handoff.nextID('project-file'),
        kind: change == null
            ? ReviewReferenceKind.file
            : ReviewReferenceKind.changedFile,
        path: path,
        lineLabel: line == null ? null : readerL10n(context).readerUiLine(line),
        added: change?.additions,
        removed: change?.deletions,
        status: change?.status,
      ),
    );
  }

  void _openSymbol(WorkspaceSymbol symbol) {
    _openFile(
      FileNode(
        name: symbol.path.split('/').last,
        path: symbol.path,
        isDir: false,
      ),
      initialLine: symbol.line,
    );
  }

  /// Shared by every Files add-to-prompt affordance: a staged reference is
  /// done with Undo; a duplicate or a full tray is said in place.
  void _stageReference(ReviewReference reference) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final outcome = handoff.stage(reference);
    if (!mounted) return;
    final l10n = readerL10n(context);
    switch (outcome) {
      case ReviewStageOutcome.staged:
        showKitUndo(
          context,
          key: const Key('files-staged-notice'),
          message: l10n.readerUiReferenceAdded(reference.label),
          onUndo: () => handoff.store.remove(handoff.sessionID, reference.id),
        );
      case ReviewStageOutcome.duplicate:
        setState(
          () => _notice = _FilesNotice(
            l10n.readerUiReferenceDuplicate(reference.label),
          ),
        );
      case ReviewStageOutcome.full:
        setState(
          () => _notice = _FilesNotice(
            l10n.readerUiReferenceFull(ReviewHandoffStore.maxPerSession),
          ),
        );
    }
  }

  Future<void> _reviewChanges() async {
    final prompt = await pushWorkingTreeReview(
      context,
      widget.controller,
      handoff: widget.handoff,
    );
    if (mounted) await _deliver(prompt);
  }

  Future<void> _reviewFileChange(FileNode node) async {
    final prompt = await pushWorkingTreeReview(
      context,
      widget.controller,
      handoff: widget.handoff,
      initialFile: node.path,
    );
    if (mounted) await _deliver(prompt);
  }

  Future<void> _deliver(String? prompt) async {
    final said = await deliverReviewPrompt(
      context,
      prompt,
      widget.onReviewPrompt,
    );
    if (said != null && mounted) {
      setState(() => _notice = _FilesNotice(said));
    }
  }

  /// Attaches a file straight from the tree. The viewer's Attach does the
  /// same thing once it has the content; this fetches the content first so
  /// the menu does not need the viewer open.
  Future<void> _attachFile(String path) async {
    final action = widget.onAttachFile;
    if (action == null || _attaching) return;
    setState(() => _attaching = true);
    try {
      final content = await _fetchScoped(path);
      if (content == null) return;
      await action(path, _exportData(path, content));
      if (!mounted) return;
      setState(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiAttached(path.split('/').last),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) setState(() => _attaching = false);
    }
  }

  // --- Layout -----------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final content = _body(context);
    if (widget.backController != null) return content;
    return PopScope(
      canPop: !_canNavigateBack && !_keyboardOpen,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBack();
      },
      child: content,
    );
  }

  List<String> get _crumbs =>
      _path.split('/').where((c) => c.isNotEmpty).toList();

  Widget _body(BuildContext context) {
    final l10n = readerL10n(context);
    final crumbs = _crumbs;
    final viewer = _viewer;
    final screen = KitScreen.twoPane(
      listPaneKey: const ValueKey('files-list-pane'),
      detailPaneKey: const ValueKey('files-detail-pane'),
      search: _searchField(l10n),
      status: _fileStatusesError == null
          ? null
          : KitStatus(
              kind: KitStatusKind.info,
              id: 'files-change-marks',
              icon: AppIconography.info,
              message: _fileStatusesError!,
            ),
      loading: _loading,
      loadingLabel: _surface == _FileSurface.symbols
          ? l10n.filesSearching
          : l10n.filesLoadingFolder,
      list: _listPane(context, l10n, crumbs),
      detail: viewer == null ? null : _embeddedViewer(viewer),
      emptyDetail: KitStateView(
        key: const ValueKey('files-detail-empty'),
        icon: AppIconography.fileText,
        title: l10n.readerUiSelectFile,
      ),
    );
    final topBar = widget.topBar?.call(crumbs.isEmpty ? null : crumbs.last);
    if (topBar == null) return screen;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        topBar,
        Expanded(child: screen),
      ],
    );
  }

  Widget _embeddedViewer(_ViewerSession session) {
    final preferences = ReaderPreferencesScope.maybeOf(context);
    return KitViewer(
      key: ValueKey('files-viewer-${session.path}:${session.initialLine}'),
      name: session.name,
      path: _viewerPath(session),
      source: session.source,
      primary: _viewerPrimary(session),
      more: _viewerMore(session, embedded: true),
      wrap: preferences?.value.wrapCode,
      onWrapChanged: preferences == null
          ? null
          : (wrap) => unawaited(saveReaderPreferences(context, wrapCode: wrap)),
      viewerKey: const ValueKey('files-viewer'),
    );
  }

  KitSearchField _searchField(AppLocalizations l10n) {
    final preferences = ReaderPreferencesScope.maybeOf(context);
    final sourceFirst = preferences?.value.sourceFirst ?? false;
    final symbols = _surface == _FileSurface.symbols;
    final String? activeFilter;
    final VoidCallback? clearFilter;
    if (symbols) {
      activeFilter = l10n.readerUiSymbols;
      clearFilter = () => _selectSurface(_FileSurface.files);
    } else if (sourceFirst) {
      activeFilter = l10n.readerUiSourceFirst;
      clearFilter = () =>
          unawaited(saveReaderPreferences(context, sourceFirst: false));
    } else if (_showHidden) {
      activeFilter = l10n.filesShowHidden;
      clearFilter = () => setState(() => _showHidden = false);
    } else {
      activeFilter = null;
      clearFilter = null;
    }
    return KitSearchField(
      fieldKey: const ValueKey('files-search-field'),
      filterKey: const ValueKey('file-surface-selector'),
      controller: _search,
      focusNode: _searchFocus,
      label: symbols ? l10n.readerUiSearchSymbols : l10n.readerUiSearchFiles,
      // Enter searches at once through onChanged (the field settles the
      // query on submit); a second handler would query twice.
      onChanged: _onSearchChanged,
      activeFilter: activeFilter,
      onClearFilter: clearFilter,
      filters: [
        if (widget.controller.capabilities.workspaceSymbols) ...[
          KitMenuItem(
            key: const ValueKey('file-surface-files'),
            label: l10n.readerUiFiles,
            group: 'surface',
            checked: !symbols,
            onSelected: () => _selectSurface(_FileSurface.files),
          ),
          KitMenuItem(
            key: const ValueKey('file-surface-symbols'),
            label: l10n.readerUiSymbols,
            group: 'surface',
            checked: symbols,
            onSelected: () => _selectSurface(_FileSurface.symbols),
          ),
        ],
        if (!symbols && preferences != null) ...[
          KitMenuItem(
            key: const ValueKey('files-order-server'),
            label: l10n.readerUiServerOrder,
            group: 'order',
            checked: !sourceFirst,
            onSelected: () =>
                unawaited(saveReaderPreferences(context, sourceFirst: false)),
          ),
          KitMenuItem(
            key: const ValueKey('files-order-source-first'),
            label: l10n.readerUiSourceFirst,
            group: 'order',
            checked: sourceFirst,
            onSelected: () =>
                unawaited(saveReaderPreferences(context, sourceFirst: true)),
          ),
        ],
        if (!symbols)
          KitMenuItem(
            key: const ValueKey('files-show-hidden'),
            label: l10n.filesShowHidden,
            icon: AppIconography.hidden,
            checked: _showHidden,
            onSelected: () => setState(() => _showHidden = !_showHidden),
          ),
      ],
    );
  }

  Widget _listPane(
    BuildContext context,
    AppLocalizations l10n,
    List<String> crumbs,
  ) {
    final tokens = KitTokens.of(context);
    final files = _surface == _FileSurface.files;
    final notice = _notice;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (files && crumbs.isNotEmpty && _search.text.isEmpty)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: KitBreadcrumb(
              breadcrumbKey: const ValueKey('files-breadcrumb'),
              rootLabel: l10n.filesProjectRoot,
              segments: crumbs,
              onSelected: (index) => _navigateTo(
                index < 0 ? '' : crumbs.take(index + 1).join('/'),
              ),
            ),
          ),
        if (notice != null)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: notice.error
                ? KitNotice.error(
                    key: const ValueKey('files-notice'),
                    message: notice.message,
                  )
                : KitNotice(
                    key: const ValueKey('files-notice'),
                    message: notice.message,
                    tone: AppStatusTone.ok,
                    onDismiss: () => setState(() => _notice = null),
                  ),
          ),
        Expanded(child: files ? _fileList(l10n) : _symbolList(l10n)),
      ],
    );
  }

  /// A first load with nothing to show yet; after 8 s it says it is still
  /// waiting and offers Try again (STATE-5).
  Widget _waiting(String title, Future<void> Function() retry) => KitStateView(
    key: const ValueKey('files-loading'),
    icon: AppIconography.waiting,
    title: title,
    since: _loadStartedAt,
    onSlow: [
      KitAction(
        key: const ValueKey('files-slow-retry'),
        label: readerL10n(context).commonRetry,
        onPressed: () => unawaited(retry()),
      ),
    ],
  );

  /// UX-102: after a run the question is "what changed?", so the changed
  /// set is one tap away as the list's first row: the same inset and
  /// hairline as the file rows under it, not a card of its own. It opens
  /// the diff itself (Review, KitDiffView with its one "Change 1 of N"
  /// navigator and file list); no list of the same files in between.
  Widget? _changesRow(AppLocalizations l10n) {
    if (_fileStatuses.isEmpty) return null;
    var added = 0, removed = 0;
    for (final change in _fileStatuses.values) {
      added += change.additions;
      removed += change.deletions;
    }
    return KitRow(
      key: const ValueKey('files-changes-card'),
      leading: KitRow.icon(context, AppIconography.review),
      title: l10n.readerUiChangedCount(_fileStatuses.length),
      supporting: TextSpan(text: KitBidi.ltr('+$added −$removed')),
      trailing: const KitChevron(),
      onTap: () => unawaited(_reviewChanges()),
    );
  }

  Widget _fileList(AppLocalizations l10n) {
    if (_loading && _entries == null) {
      return _waiting(l10n.filesLoadingFolder, _refreshFiles);
    }
    if (_error != null) {
      return KitRefresh(
        onRefresh: _refreshFiles,
        child: _loadFailed(l10n.filesLoadFailedTitle, _error!, _refreshFiles),
      );
    }
    if (_entries != null &&
        _storageBlock != SharedStorageBlock.none &&
        _search.text.isEmpty) {
      return _storageAccessNotice(l10n);
    }
    final hidden = <FileNode>[];
    final entries = _displayEntries(hidden);
    final changes = _changesRow(l10n);
    if (_entries != null && entries.isEmpty) {
      final searching = _search.text.isNotEmpty;
      final onlyHidden = !searching && hidden.isNotEmpty;
      final state = KitRefresh(
        onRefresh: _refreshFiles,
        // An empty folder is the open folder; a filter that matched nothing
        // is the magnifier (one drawing per kind of state).
        child: KitStateView(
          key: ValueKey(searching ? 'files-no-match' : 'files-empty-folder'),
          icon: AppIconography.folderOpen,
          illustration: searching
              ? const StatesSearchScene()
              : const StatesFolderScene(),
          title: searching ? l10n.readerUiNoFiles : l10n.readerUiEmptyFolder,
          body: searching
              ? l10n.readerUiTryFileName
              : onlyHidden
              ? l10n.filesOnlyHidden
              : l10n.readerUiPullRefresh,
          primary: onlyHidden
              ? KitAction(
                  key: const ValueKey('files-show-hidden-action'),
                  label: l10n.filesShowHidden,
                  onPressed: () => setState(() => _showHidden = true),
                )
              : searching
              ? null
              : KitAction(
                  key: const ValueKey('files-refresh'),
                  label: l10n.fileReload,
                  onPressed: () => unawaited(_refreshFiles()),
                ),
        ),
      );
      if (changes == null) return state;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: EdgeInsetsDirectional.only(
              top: KitTokens.of(context).space2,
            ),
            child: changes,
          ),
          const KitDivider(inset: KitDividerInset.text),
          Expanded(child: state),
        ],
      );
    }
    final lead = changes == null ? 0 : 1;
    return KitRefresh(
      onRefresh: _refreshFiles,
      child: KitScrollArea(
        builder: (scrollController) => ListView.separated(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          itemCount: lead + entries.length,
          separatorBuilder: (_, _) =>
              const KitDivider(inset: KitDividerInset.text),
          itemBuilder: (context, i) =>
              i < lead ? changes! : _fileRow(context, l10n, entries[i - lead]),
        ),
      ),
    );
  }

  Widget _fileRow(BuildContext context, AppLocalizations l10n, FileNode node) {
    final change = node.isDir ? null : _fileStatuses[node.path];
    final deleted = change?.status == 'deleted';
    final descendantChanges = node.isDir
        ? _fileStatuses.keys
              .where((path) => path.startsWith('${node.path}/'))
              .length
        : 0;
    final detail = _fileDetail(node, change, descendantChanges);
    return KitRow(
      key: ValueKey('project-file-${node.path}'),
      leading: KitRow.icon(
        context,
        node.isDir
            ? AppIconography.files
            : deleted
            ? AppIconography.removeCircle
            : _fileTypeIcon(node.name),
      ),
      title: node.name,
      titleMaxLines: 2,
      supporting: detail == null ? null : TextSpan(text: detail),
      supportingMaxLines: 2,
      selected: node.path == _viewer?.path,
      trailing: node.isDir ? const KitChevron() : null,
      // Long-press, right-click and the keyboard open the row's menu; its
      // items are also the row's semantic actions (KIT-28).
      menu: _fileRowMenu(l10n, node, change),
      menuLabel: l10n.gestureEquivFileRowActions(node.name),
      onTap: deleted
          ? () => unawaited(_reviewFileChange(node))
          : () {
              if (node.isDir) {
                _navigateTo(node.path);
              } else {
                _openFile(node);
              }
            },
    );
  }

  /// The row's rarer actions: the same list on a long press, a right click
  /// and the context-menu key. Opening is the row's tap, so it is not here.
  List<KitMenuItem> _fileRowMenu(
    AppLocalizations l10n,
    FileNode node,
    VersionControlFile? change,
  ) {
    final deleted = change?.status == 'deleted';
    final path = _relativePath(node.path);
    return [
      if (!node.isDir && !deleted && widget.onAttachFile != null)
        KitMenuItem(
          key: const ValueKey('file-menu-attach'),
          label: l10n.readerUiAttachPrompt,
          icon: AppIconography.attach,
          onSelected: () => unawaited(_attachFile(path)),
        ),
      if (!node.isDir && !deleted && widget.handoff != null)
        KitMenuItem(
          key: const ValueKey('file-menu-reference'),
          label: l10n.readerUiAddReference,
          icon: AppIconography.link,
          onSelected: () => _stageProjectFile(path, null),
        ),
      if (change != null)
        KitMenuItem(
          key: const ValueKey('file-menu-review'),
          label: l10n.readerUiOpenReview,
          icon: AppIconography.review,
          onSelected: () => unawaited(_reviewFileChange(node)),
        ),
      KitMenuItem.copy(
        key: const ValueKey('file-menu-copy-path'),
        label: l10n.readerUiCopyPath,
        text: () => path,
      ),
      KitMenuItem.copy(
        key: const ValueKey('file-menu-copy-name'),
        label: l10n.filesCopyName,
        icon: AppIconography.textShort,
        text: () => node.name,
      ),
    ];
  }

  /// Type-aware glyphs so a directory scans by kind, matching the developer
  /// file browsers cited in docs/design-inspiration.md.
  static IconData _fileTypeIcon(String name) {
    return switch (_extension(name)) {
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' ||
      'kt' ||
      'java' ||
      'swift' ||
      'c' ||
      'cc' ||
      'cpp' ||
      'h' ||
      'cs' ||
      'rb' ||
      'php' ||
      'sh' => AppIconography.code,
      'png' ||
      'jpg' ||
      'jpeg' ||
      'gif' ||
      'webp' ||
      'svg' ||
      'ico' => AppIconography.image,
      'md' || 'txt' || 'rst' || 'pdf' => AppIconography.article,
      'json' ||
      'yaml' ||
      'yml' ||
      'toml' ||
      'xml' ||
      'ini' ||
      'lock' ||
      'gradle' ||
      'properties' => AppIconography.dataObject,
      'zip' || 'tar' || 'gz' || 'jar' || 'apk' => AppIconography.zip,
      _ => AppIconography.fileText,
    };
  }

  /// The listing as shown: deleted files added back where they were, dot
  /// entries left out unless Show hidden files is on (collected into
  /// [hidden] so an all-hidden folder can say so), and the reader's order.
  List<FileNode> _displayEntries(List<FileNode> hidden) {
    final entries = [...?_entries];
    final paths = entries.map((node) => node.path).toSet();
    final searchMode = _searchOriginPath != null;
    final query = _search.text.trim().toLowerCase();
    final prefix = _path.isEmpty ? '' : '$_path/';
    for (final change in _fileStatuses.values) {
      if (change.status != 'deleted') continue;
      if (searchMode) {
        if (query.isNotEmpty && !change.path.toLowerCase().contains(query)) {
          continue;
        }
        if (paths.add(change.path)) {
          entries.add(
            FileNode(
              name: change.path.split('/').last,
              path: change.path,
              isDir: false,
            ),
          );
        }
        continue;
      }
      if (!change.path.startsWith(prefix)) continue;
      final remainder = change.path.substring(prefix.length);
      if (remainder.isEmpty) continue;
      final parts = remainder.split('/');
      final childPath = '$prefix${parts.first}';
      if (paths.add(childPath)) {
        entries.add(
          FileNode(name: parts.first, path: childPath, isDir: parts.length > 1),
        );
      }
    }
    if (!_showHidden && !searchMode) {
      hidden.addAll(entries.where((entry) => entry.name.startsWith('.')));
      entries.removeWhere((entry) => entry.name.startsWith('.'));
    }
    if (ReaderPreferencesScope.maybeOf(context)?.value.sourceFirst == true) {
      // Stable partition: the server's order remains intact within each group.
      // Generated entries remain visible, including deleted files.
      return [
        ...entries.where((entry) => !_isGeneratedEntry(entry)),
        ...entries.where(_isGeneratedEntry),
      ];
    }
    return entries;
  }

  bool _isGeneratedEntry(FileNode node) {
    const generatedDirectories = {
      'node_modules',
      '.git',
      '.dart_tool',
      'build',
      'dist',
      'coverage',
      '__pycache__',
      '.gradle',
    };
    final name = node.name.toLowerCase();
    final directories = node.path.split('/');
    if (!node.isDir && directories.isNotEmpty) directories.removeLast();
    return directories.any(
          (part) => generatedDirectories.contains(part.toLowerCase()),
        ) ||
        name.endsWith('.lock') ||
        name.endsWith('.g.dart') ||
        name.endsWith('.freezed.dart') ||
        name.endsWith('.min.js') ||
        name.endsWith('.map');
  }

  /// The row's second line: where a search result lives, and its change
  /// mark in words (or how many files changed inside a folder).
  String? _fileDetail(
    FileNode node,
    VersionControlFile? change,
    int descendantChanges,
  ) {
    final details = <String>[];
    if (_search.text.isNotEmpty && node.path != node.name) {
      details.add(KitBidi.ltr(node.path));
    }
    if (change != null) {
      details.add(_fileStatusLabel(context, change.status));
    } else if (descendantChanges > 0) {
      details.add(readerL10n(context).readerUiChangedCount(descendantChanges));
    }
    return details.isEmpty ? null : details.join(' · ');
  }

  /// A listing that could not load: the unplugged drawing every load
  /// failure uses, the reason, Try again, and Report a bug.
  Widget _loadFailed(
    String title,
    String message,
    Future<void> Function() onRetry,
  ) {
    final l10n = readerL10n(context);
    return KitStateView(
      key: const ValueKey('files-load-failed'),
      icon: AppIconography.error,
      tone: AppStatusTone.failure,
      illustration: const StatesUnpluggedScene(),
      title: title,
      body: message,
      primary: KitAction(
        label: l10n.commonRetry,
        onPressed: () => unawaited(onRetry()),
      ),
      tertiary: [
        KitAction(
          key: const ValueKey('product-error-report-bug'),
          label: l10n.e7LibraryReportABug,
          onPressed: () => unawaited(openBugReport(context)),
        ),
      ],
    );
  }

  Widget _symbolList(AppLocalizations l10n) {
    Future<void> retry() => _searchSymbols(_search.text);
    if (_loading && _symbols == null) {
      return _waiting(l10n.filesSearching, retry);
    }
    if (_error != null) {
      return KitRefresh(
        onRefresh: retry,
        child: _loadFailed(l10n.filesSymbolsFailedTitle, _error!, retry),
      );
    }
    if (_search.text.trim().isEmpty) {
      return KitStateView(
        key: const ValueKey('files-symbols-hint'),
        icon: AppIconography.dataObject,
        title: l10n.readerUiWorkspaceSymbols,
        body: l10n.readerUiSymbolsHint,
      );
    }
    if (_symbols?.isEmpty == true) {
      return KitRefresh(
        onRefresh: retry,
        child: KitStateView(
          key: const ValueKey('files-no-symbols'),
          icon: AppIconography.search,
          illustration: const StatesSearchScene(),
          title: l10n.readerUiNoSymbols,
          body: l10n.readerUiSymbolsUnavailable,
        ),
      );
    }
    final symbols = _symbols ?? const <WorkspaceSymbol>[];
    return KitRefresh(
      onRefresh: retry,
      child: KitScrollArea(
        builder: (scrollController) => ListView.separated(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          itemCount: symbols.length,
          separatorBuilder: (_, _) =>
              const KitDivider(inset: KitDividerInset.text),
          itemBuilder: (context, index) {
            final symbol = symbols[index];
            return KitRow(
              key: ValueKey('workspace-symbol-${symbol.path}-${symbol.line}'),
              leading: KitRow.icon(context, _symbolIcon(symbol.kind)),
              title: symbol.name,
              supporting: TextSpan(
                text:
                    '${_symbolKind(symbol.kind)} · '
                    '${KitBidi.ltr('${symbol.path}:${symbol.line}:${symbol.column}')}',
              ),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: () => _openSymbol(symbol),
            );
          },
        ),
      ),
    );
  }

  String _symbolKind(int kind) => switch (kind) {
    1 => readerL10n(context).readerUiSymbolFile,
    2 => readerL10n(context).readerUiSymbolModule,
    3 => readerL10n(context).readerUiSymbolNamespace,
    4 => readerL10n(context).readerUiSymbolPackage,
    5 => readerL10n(context).readerUiSymbolClass,
    6 => readerL10n(context).readerUiSymbolMethod,
    7 => readerL10n(context).readerUiSymbolProperty,
    8 => readerL10n(context).readerUiSymbolField,
    9 => readerL10n(context).readerUiSymbolConstructor,
    10 => readerL10n(context).readerUiSymbolEnum,
    11 => readerL10n(context).readerUiSymbolInterface,
    12 => readerL10n(context).readerUiSymbolFunction,
    13 => readerL10n(context).readerUiSymbolVariable,
    14 => readerL10n(context).readerUiSymbolConstant,
    22 => readerL10n(context).readerUiSymbolEnummember,
    23 => readerL10n(context).readerUiSymbolStruct,
    24 => readerL10n(context).readerUiSymbolEvent,
    25 => readerL10n(context).readerUiSymbolOperator,
    26 => readerL10n(context).readerUiSymbolTypeparameter,
    _ => readerL10n(context).readerUiSymbolSymbol,
  };

  static IconData _symbolIcon(int kind) => switch (kind) {
    5 || 10 || 11 || 23 => AppIconography.category,
    6 || 9 || 12 => AppIconography.function,
    7 || 8 || 13 || 14 => AppIconography.dataObject,
    1 || 2 || 3 || 4 => AppIconography.folders,
    _ => AppIconography.code,
  };

  @override
  void dispose() {
    widget.backController?._handler = null;
    widget.controller.removeListener(_controllerChanged);
    widget.focusSearchSignal?.removeListener(_focusSearch);
    _search.removeListener(_searchChanged);
    _search.dispose();
    _searchFocus.dispose();
    super.dispose();
  }
}

String _fileStatusLabel(BuildContext context, String status) =>
    switch (status) {
      'added' => readerL10n(context).readerUiAdded,
      'deleted' => readerL10n(context).readerUiDeleted,
      'modified' => readerL10n(context).readerUiModified,
      _ => readerL10n(context).readerUiChanged,
    };

AppLocalizations readerL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
