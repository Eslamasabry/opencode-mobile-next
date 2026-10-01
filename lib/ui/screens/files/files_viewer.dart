part of '../files_screen.dart';

/// The file viewer: opening, fetching and rendering file content.
extension _FilesViewer on _FilesScreenState {
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
          onSelected: () => _set(
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
    return _FilesViewer._viewerContent(
      session.name,
      content,
      session.initialLine,
    );
  }

  Future<void> _attachFromViewer(_ViewerSession session) async {
    final action = widget.onAttachFile;
    if (action == null || _attaching) return;
    final content = session.content;
    if (content == null) return _attachFile(session.path);
    _set(() => _attaching = true);
    try {
      await action(
        session.path,
        _FilesViewer._exportData(session.path, content),
      );
      if (!mounted) return;
      // Attached: back to the tree, which says where the file went.
      if (_viewerOpen) await Navigator.of(context).maybePop();
      if (!mounted) return;
      _set(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiAttachedReturn(session.name),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) _set(() => _attaching = false);
    }
  }

  Future<void> _download(_ViewerSession session) async {
    if (_downloading) return;
    _set(() => _downloading = true);
    try {
      final content = session.content ?? await _fetchScoped(session.path);
      if (content == null || !mounted) return;
      final data = _FilesViewer._exportData(session.path, content);
      final bytes = data.exportBytes;
      if (bytes == null) return;
      final savedPath = await FilePicker.saveFile(
        dialogTitle: readerL10n(context).readerUiSaveNamed(data.name),
        fileName: data.name,
        bytes: bytes,
      );
      if (!mounted || savedPath == null) return;
      _set(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiSavedDevice(data.name),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) _set(() => _downloading = false);
    }
  }

  void _fail(Object error) =>
      _set(() => _notice = _FilesNotice(productErrorText(error), error: true));

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
    final data = _FilesViewer._exportData(name, content);
    final bytes = data.bytes;
    if (data.isRasterImage && bytes != null && bytes.isNotEmpty) {
      return KitViewerContent.image(bytes, semanticsLabel: name);
    }
    final text = data.text;
    if (text != null) {
      var shown = text;
      var truncated = false;
      if (shown.length > _FilesScreenState._maxViewerChars) {
        var end = _FilesScreenState._maxViewerChars;
        final unit = shown.codeUnitAt(end - 1);
        if (unit >= 0xD800 && unit <= 0xDBFF) end--;
        shown = shown.substring(0, end);
        truncated = true;
      }
      final ext = _FilesViewer._extension(name);
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
      if (initialLine != null || _FilesViewer._isCode(ext)) {
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
        return await _FilesViewer._pdfContent(bytes);
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
      _FilesBody._fileTypeIcon('x.$ext') == AppIconography.code ||
      _FilesBody._fileTypeIcon('x.$ext') == AppIconography.dataObject;
}
