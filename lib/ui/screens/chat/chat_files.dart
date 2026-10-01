part of '../chat_screen.dart';

// Files, links and references: path links in replies, tool output files,
// dropped files, and review references staged for the next prompt.

mixin _ChatFileFields {
  final Map<String, Future<List<FileNode>>> _pathLinkDirs = {};
  final Map<String, DateTime> _pathLinkDirsAt = {};
  final Map<String, Future<bool>> _pathLinkChecks = {};
  final Map<String, DateTime> _pathLinkMissAt = {};
}

extension _ChatFiles on _ChatScreenState {
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
