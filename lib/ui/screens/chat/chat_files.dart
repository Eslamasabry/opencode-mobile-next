part of '../chat_screen.dart';

// Files, links and references: path links in replies, tool output files,
// dropped files, and review references staged for the next prompt.

/// The largest picture a reply's thumbnail reads (8 MiB).
const _maxPathImageBytes = 8 * 1024 * 1024;

mixin _ChatFileFields {
  final Map<String, Future<List<FileNode>>> _pathLinkDirs = {};
  final Map<String, DateTime> _pathLinkDirsAt = {};
  final Map<String, Future<bool>> _pathLinkChecks = {};
  final Map<String, DateTime> _pathLinkMissAt = {};
}

extension _ChatFiles on _ChatScreenState {
  Future<FilePreviewData> _loadToolOutputFile(ToolOutputFile file) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: strings.chatUiFilesAreUnavailableInThisPreview,
      );
    }
    final path = file.path;
    final api = await _conn.prepareActionTransport();
    if (path == null || path.isEmpty || api == null) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: strings.chatUiTheGeneratedFileIsNotAvailableFrom,
      );
    }
    final content = await api.fileContent(path);
    final binary = content.isBinary || content.encoding == 'base64';
    final bytes = binary ? content.bytes() : null;
    return FilePreviewData(
      name: file.displayName,
      mimeType: file.mimeType ?? content.mimeType,
      bytes: bytes,
      text: binary ? null : content.content,
      error: binary && bytes!.isEmpty
          ? strings.chatUiTheServerReturnedEmptyImageData
          : null,
    );
  }

  List<ReviewReference> get _stagedReferences => _handoff.references;

  void _attachReference(ReferenceInfo reference) {
    if (!_conn.capabilities.fileBrowsing) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final attachment = PromptAttachment.reference(
      name: reference.name,
      path: reference.path,
    );
    if (_attachments.any(
      (candidate) =>
          candidate.isDirectoryReference && candidate.url == attachment.url,
    )) {
      _showComposerNote(
        _chatL10n(context).chatUiReferenceAlreadyAdded(reference.name),
      );
      _focus.requestFocus();
      return;
    }
    final current = _composer.text.trimRight();
    final mention = '@${reference.name}';
    final text = current.isEmpty ? mention : '$current $mention';
    _setChatState(() {
      _attachments.add(attachment);
      _composer.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    });
    _focus.requestFocus();
  }

  /// Folds every staged reference into the prompt text just before it is
  /// sent. References are pointers, not attachments: they leave the composer
  /// as structured markdown the agent can read, and the chips clear with
  /// them.
  void _applyStagedReferences() {
    final references = _handoff.references;
    if (references.isEmpty) return;
    final block = ReviewReference.format(references);
    if (block.isEmpty) return;
    final current = _composer.text.trim();
    final text = current.isEmpty ? block : '$current\n\n$block';
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _handoff.store.clear(widget.sessionID);
  }

  /// Says why the chips are still there after a slash command ran, so the
  /// user does not read a surviving reference as a send that failed.
  void _noteReferencesKeptForNextPrompt() {
    if (!mounted) return;
    final count = _handoff.references.length;
    _showComposerNote(
      count == 1
          ? _chatL10n(context).chatUiReferenceKeptForYourNextPromptCommands
          : _chatL10n(context).chatUiReferencesKeptForYourNextPromptCommands,
      key: const Key('references-kept-notice'),
    );
  }

  void _removeStagedReference(ReviewReference reference) =>
      _handoff.store.remove(widget.sessionID, reference.id);
  // UX-103 review handoff (end).

  void _addReviewPrompt(String prompt) {
    if (!mounted || prompt.trim().isEmpty) return;
    final current = _composer.text.trimRight();
    final value = prompt.trim();
    final text = current.isEmpty ? value : '$current\n\n$value';
    _setChatState(() {
      _composer.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    });
    _focus.requestFocus();
    _showComposerNote(_chatL10n(context).chatUiReviewCommentAddedToThePrompt);
  }

  Future<bool> _checkPathLink(String path) async {
    final slash = path.lastIndexOf('/');
    final name = slash >= 0 ? path.substring(slash + 1) : '';
    if (name.isEmpty) return false;
    final dir = slash == 0 ? '/' : path.substring(0, slash);
    try {
      final listedAt = _pathLinkDirsAt[dir];
      if (listedAt != null &&
          DateTime.now().difference(listedAt) >
              _ChatScreenState._pathLinkNegativeTtl) {
        _pathLinkDirs.remove(dir);
      }
      final nodes = await _pathLinkDirs.putIfAbsent(dir, () {
        _pathLinkDirsAt[dir] = DateTime.now();
        return () async {
          final api = await _conn.prepareActionTransport();
          if (api == null) throw StateError('offline');
          return api.listFiles(dir);
        }();
      });
      final found = nodes.any((node) => !node.isDir && node.name == name);
      if (!found) _pathLinkMissAt[path] = DateTime.now();
      return found;
    } catch (_) {
      // A transient failure must not brand the path dead for the whole
      // session; forget both futures so a later rebuild can retry.
      _pathLinkDirs.remove(dir);
      _pathLinkChecks.remove(path);
      return false;
    }
  }

  /// The bytes of a picture file a reply names (`![shot](/tmp/shot.png)`),
  /// for its thumbnail in the transcript. Null when the file cannot be read
  /// or is too large to keep as a thumbnail's source; the reply then shows
  /// the picture as a named chip that still opens the viewer.
  Future<Uint8List?> _loadPathImage(String path) async {
    if (_conn.isIsolated || !_conn.capabilities.fileBrowsing) return null;
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) return null;
      final content = await api.fileContent(path);
      if (!content.isBinary && content.encoding != 'base64') return null;
      final bytes = content.bytes();
      return bytes.isEmpty || bytes.length > _maxPathImageBytes ? null : bytes;
    } catch (_) {
      return null;
    }
  }

  Future<void> _attachToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    await _addPreviewAttachment(
      filename: file.displayName,
      mimeType: data.mimeType ?? file.mimeType,
      data: data,
    );
    if (!mounted) return;
    _focus.requestFocus();
    _showComposerNote(_chatL10n(context).chatUiFileAttached(file.displayName));
  }

  Future<void> _attachProjectFile(String path, FilePreviewData data) =>
      !_supportsPromptAttachments
      ? Future<void>.sync(
          () => _showComposerNote(_chatL10n(context).codexTextOnlyPrompt),
        )
      : _addPreviewAttachment(
          filename: path.split('/').last,
          mimeType: data.mimeType,
          data: data,
        );

  /// Files dropped onto the composer from the desktop file manager.
  ///
  /// Goes through the same `_addPreviewAttachment` pipeline the picker and
  /// the file viewer use, so the count, per-file and aggregate caps apply
  /// identically. The size is checked from the drop's own metadata first, so
  /// an oversized file is refused without ever being read into memory.
  Future<void> _handleDroppedFiles(List<DroppedFile> files) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    for (final file in files) {
      try {
        if (await file.length() > _maxAttachmentBytes) {
          throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
        }
        final bytes = await file.readBytes();
        if (!mounted) return;
        await _addPreviewAttachment(
          filename: file.name,
          mimeType: file.mimeType,
          data: FilePreviewData(
            name: file.name,
            mimeType: file.mimeType,
            bytes: bytes,
          ),
        );
      } catch (error) {
        if (!mounted) return;
        showProductError(context, error);
        return;
      }
    }
    if (!mounted) return;
    _focus.requestFocus();
  }

  Future<void> _addPreviewAttachment({
    required String filename,
    required String? mimeType,
    required FilePreviewData data,
  }) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(
        data.error ?? _chatL10n(context).chatUiTheFileHasNoContentToAttach,
      );
    }
    if (_attachments.length >= _maxAttachmentCount) {
      throw ProductException(
        _chatL10n(context).chatUiAttachmentCountLimit(_maxAttachmentCount),
      );
    }
    if (bytes.length > _maxAttachmentBytes) {
      throw ProductException(
        _chatL10n(context).chatUiEachAttachmentMustBe10MBOr,
      );
    }
    final currentBytes = _attachments.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes + bytes.length > _maxAggregateAttachmentBytes) {
      throw ProductException(
        _chatL10n(context).chatUiAttachmentsMustTotalNoMoreThan20,
      );
    }
    final mime = promptAttachmentMime(
      filename: filename,
      bytes: bytes,
      declaredMime: mimeType,
    );
    if (mime == null) {
      throw ProductException(_chatL10n(context).chatAttachmentUnsupported);
    }
    final attachment = PromptAttachment(
      mime: mime,
      filename: filename,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    if (!mounted) return;
    _setChatState(() => _attachments.add(attachment));
  }

  Future<void> _downloadToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    if (_conn.isIsolated) return;
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(
        data.error ?? _chatL10n(context).chatUiTheFileHasNoContentToSave,
      );
    }
    final savedPath = await FilePicker.saveFile(
      dialogTitle: _chatL10n(context).chatUiSaveFile(file.displayName),
      fileName: file.displayName,
      bytes: bytes,
    );
    if (!mounted || savedPath == null) return;
    _showComposerNote(_chatL10n(context).chatUiFileSaved(file.displayName));
  }
}

/// The files a finished turn changed, as rows under the reply (concept 9,
/// owner 2026-10-20), and the project's Files under them.
extension _ChatTurnFiles on _ChatScreenState {
  /// Null when the turn changed nothing, or the conversation cannot open
  /// files (the demo, a watched team worker).
  Widget? _turnChangedFiles(
    BuildContext context,
    List<MessageWithParts> messages,
    int index,
  ) {
    if (_conn.isIsolated || _watching) return null;
    final files = RunResult.editedFileStats(
      _turnSteps(messages, index),
      max: _maxTurnFileRows + 1,
    );
    if (files.isEmpty) return null;
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final shown = files.take(_maxTurnFileRows).toList();
    final directory =
        _conn.sessionsById[widget.sessionID]?.directory ?? _conn.directory;
    final project = isTemporaryProjectDirectory(directory) ? null : directory;
    final canBrowse =
        project != null && ProjectHub.isAvailable(_conn.capabilities);
    final allFiles = files.length > shown.length
        ? l10n.chatTurnAllFilesMore(files.length - shown.length)
        : l10n.chatTurnAllFiles;
    return Column(
      key: const ValueKey('chat-turn-files'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitRowGroup(
          margin: EdgeInsetsDirectional.only(top: tokens.space2),
          children: [
            for (final file in shown)
              KitRow(
                key: ValueKey('chat-turn-file-${file.path}'),
                leading: KitRow.icon(context, AppIconography.fileText),
                title: file.name,
                titleIsFileName: true,
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (file.added case final added? when added > 0)
                      Semantics(
                        label: l10n.chatTurnFileLinesAdded(added),
                        child: ExcludeSemantics(
                          child: KitText(
                            '+$added',
                            role: KitTextRole.body,
                            tone: KitTextTone.success,
                          ),
                        ),
                      ),
                    if (file.removed case final removed? when removed > 0)
                      Padding(
                        padding: EdgeInsetsDirectional.only(
                          start: tokens.space2,
                        ),
                        child: Semantics(
                          label: l10n.chatTurnFileLinesRemoved(removed),
                          child: ExcludeSemantics(
                            child: KitText(
                              '−$removed',
                              role: KitTextRole.body,
                              tone: KitTextTone.secondary,
                            ),
                          ),
                        ),
                      ),
                    const KitChevron(),
                  ],
                ),
                onTap: () => unawaited(
                  showFilePreviewSheetLoading(
                    context,
                    name: file.name,
                    path: file.path,
                    load: () => _loadToolOutputFile(
                      ToolOutputFile(path: file.path, filename: file.name),
                    ),
                  ),
                ),
              ),
          ],
        ),
        if (canBrowse)
          // A quiet link under the rows (concept 9): the folder glyph and
          // the words in the secondary tone, one 48 dp target.
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitTappable(
              tappableKey: const ValueKey('chat-turn-all-files'),
              label: allFiles,
              shape: KitShape.button,
              surface: KitSurfaceLevel.ground,
              onTap: () => unawaited(
                openProjectFiles(
                  context,
                  _conn,
                  directory: project,
                  openFiles: true,
                  onAttachFile: _attachProjectFile,
                  onReviewPrompt: _addReviewPrompt,
                  handoff: _handoff,
                ),
              ),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: tokens.minTarget),
                child: Padding(
                  // The glyph starts on the rows' own edge.
                  padding: EdgeInsetsDirectional.only(end: tokens.space2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      KitIcon(
                        AppIconography.folderOpen,
                        size: KitIconSize.small,
                        tone: KitTextTone.secondary,
                      ),
                      SizedBox(width: tokens.space2),
                      Flexible(
                        child: KitText(
                          allFiles,
                          role: KitTextRole.secondary,
                          tone: KitTextTone.secondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// A turn lists at most this many changed files; "All files" says the rest.
const _maxTurnFileRows = 5;
