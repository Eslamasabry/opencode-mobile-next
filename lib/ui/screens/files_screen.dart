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

part 'files/files_loading.dart';
part 'files/files_viewer.dart';
part 'files/files_actions.dart';
part 'files/files_body.dart';
part 'files/files_entries.dart';

/// Project file browser backed by `/file`, with name search (`/find/file`)
/// and the kit's one viewer (map pages files, files-row-actions-sheet,
/// files-file-viewer-sheet, files-changes-sheet).
enum _FileSurface { files, symbols, text }

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

  /// Lines found by the text search (null before one has run).
  List<FindMatch>? _textMatches;
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

  /// Extensions in the part files cannot call [setState] themselves.
  void _set(VoidCallback fn) => setState(fn);

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
        switch (_surface) {
          case _FileSurface.symbols:
            _searchSymbols(_search.text);
          case _FileSurface.text:
            _searchText(_search.text);
          case _FileSurface.files:
            _searchFiles(_search.text);
        }
      } else if (_surface == _FileSurface.files) {
        _load(_path);
      } else {
        setState(() {
          _symbols = null;
          _textMatches = null;
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
      _textMatches = null;
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
      if (surface == _FileSurface.text) _textMatches = null;
    });
    if (surface == _FileSurface.files) unawaited(_load(origin));
  }

  /// The field's settled query (KitSearchField waits for typing to settle,
  /// and reports a clear at once).
  void _onSearchChanged(String query) {
    if (_suppressSearch) return;
    switch (_surface) {
      case _FileSurface.symbols:
        unawaited(_searchSymbols(query));
      case _FileSurface.text:
        unawaited(_searchText(query));
      case _FileSurface.files:
        unawaited(_searchFiles(query));
    }
  }

  void _clearSearch() {
    _clearSearchText();
    if (_surface != _FileSurface.files) {
      _requestGeneration++;
      setState(() {
        _symbols = null;
        _textMatches = null;
        _loading = false;
        _error = null;
      });
      return;
    }
    final origin = _searchOriginPath ?? _path;
    _searchOriginPath = null;
    _load(origin);
  }

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
