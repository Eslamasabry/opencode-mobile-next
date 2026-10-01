part of '../chat_screen.dart';

// Attachments: files, photos, web sources and keyboard-inserted content
// added to the prompt, within the size and count limits.

const _maxAttachmentCount = 5;

const _maxAttachmentBytes = 10 * 1024 * 1024;

const _maxAggregateAttachmentBytes = 20 * 1024 * 1024;

@visibleForTesting
Future<Uint8List?> readAttachmentBytesWithinLimit(
  PlatformFile file, {
  required int maxBytes,
}) async {
  if (maxBytes < 0) {
    throw ArgumentError.value(maxBytes, 'maxBytes', 'must not be negative');
  }

  final stream = file.readAsByteStream();

  // Retain at most the allowed payload plus one byte. The extra byte detects a
  // file that grew after the picker reported its metadata without allowing an
  // unbounded read or allocation.
  final bytes = BytesBuilder();
  var byteCount = 0;
  final iterator = StreamIterator<List<int>>(stream);
  try {
    while (byteCount <= maxBytes && await iterator.moveNext()) {
      final chunk = iterator.current;
      if (chunk.isEmpty) continue;
      final remaining = maxBytes + 1 - byteCount;
      final acceptedLength = chunk.length < remaining
          ? chunk.length
          : remaining;
      if (acceptedLength == chunk.length) {
        bytes.add(chunk);
      } else {
        final acceptedBytes = Uint8List(acceptedLength)
          ..setRange(0, acceptedLength, chunk);
        bytes.add(acceptedBytes);
      }
      byteCount += acceptedLength;
      if (byteCount > maxBytes) return null;
    }
    return bytes.takeBytes();
  } finally {
    await iterator.cancel();
  }
}

mixin _ChatAttachmentFields {
  bool _photoBusy = false;

  /// The local attachment recovery note shows once per session, not on
  /// every attachment. [_attachmentNoteActive] keeps it up while the first
  /// batch is staged; the static set remembers sessions that have seen it.
  bool _attachmentNoteActive = false;
}

extension _ChatAttachments on _ChatScreenState {
  static final Set<String> _attachmentNoteShownSessions = {};

  bool get _supportsPromptAttachments => _conn.capabilities.promptAttachments;

  Future<void> _addWebSources() async {
    if (_conn.isIsolated || !_conn.capabilities.webSearch) return;
    if (_sending || _promptShelfBusy || _voiceConversation) return;
    final source = _speechScopeNow;
    final snapshot = _snapshotPrompt();
    final location = _conn.locationRevision;
    final revision = _promptContentRevision;
    final route = ModalRoute.of(context);
    final selections = await Navigator.of(context)
        .push<List<WebSourceSelection>>(
          KitPageRoute(builder: (_) => WebSourcesScreen(controller: _conn)),
        );
    if (!mounted || selections == null || selections.isEmpty) return;
    if (source != _speechScopeNow ||
        revision != _promptContentRevision ||
        !_promptUnchanged(snapshot, location) ||
        !(route?.isCurrent ?? true)) {
      _showComposerNote(_chatL10n(context).webSourcesDraftChanged);
      return;
    }
    final appendix = jsonEncode([
      for (final selection in selections)
        {
          'title': selection.title,
          'url': selection.url,
          'excerpt': selection.excerpt,
        },
    ]);
    final addition = '${_chatL10n(context).webSourcesDraftLabel}\n$appendix';
    final text = snapshot.text.isEmpty
        ? addition
        : '${snapshot.text}\n\n$addition';
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    await _persistDraft();
    if (mounted && source == _speechScopeNow) _focus.requestFocus();
  }

  Future<void> _pickAttachment() async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_promptShelfBusy) return;
    final location = _conn.locationRevision;
    _setChatState(() => _photoBusy = true);
    try {
      final attachment = await _chooseAttachment(_attachments);
      if (attachment != null && mounted && location == _conn.locationRevision) {
        _setChatState(() => _attachments.add(attachment));
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _photoBusy = false);
    }
  }

  bool _photoMatches(PendingPromptPhoto photo) =>
      photo.profileID == _draftProfileID &&
      photo.sessionID == widget.sessionID &&
      photo.directory == _conn.directory &&
      photo.workspace == _conn.workspace &&
      _draftLocation == _conn.locationRevision;

  String _photoError(Object error) {
    final l10n = _chatL10n(context);
    if (error is PromptPhotoException) {
      return switch (error.failure) {
        PromptPhotoFailure.tooLarge => l10n.photoTooLarge,
        PromptPhotoFailure.unsupported => l10n.chatAttachmentUnsupported,
        PromptPhotoFailure.storage => l10n.photoStorageFailed,
        PromptPhotoFailure.pending => l10n.photoPendingOther,
        PromptPhotoFailure.unavailable => l10n.photoUnavailable,
      };
    }
    if (error is PlatformException &&
        error.code.toLowerCase().contains('denied')) {
      return l10n.photoPermissionDenied;
    }
    return l10n.photoUnavailable;
  }

  Future<void> _pickPhoto(ImageSource source) async {
    if (_conn.isIsolated || !_supportsPromptAttachments) {
      if (!_conn.isIsolated && !_supportsPromptAttachments) {
        _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      }
      return;
    }
    if (_promptShelfBusy || !platformCapabilities.supportsPromptPhotos) return;
    if (_attachments.length >= _maxAttachmentCount ||
        _attachments.fold<int>(
              0,
              (total, a) => total + _attachmentByteLength(a),
            ) >=
            _maxAggregateAttachmentBytes) {
      _showActionError(_chatL10n(context).photoDraftFull);
      return;
    }
    // A photo still waiting from an earlier pick joins its own
    // conversation's draft first (P3.2): no question about it. This
    // conversation's photo goes into this composer; another one's into that
    // conversation's saved draft. One that cannot move yet keeps waiting.
    if (_conn.promptPhotos.pending case final pending?) {
      if (pending.profileID == _draftProfileID &&
          pending.sessionID == widget.sessionID) {
        await _applyPendingPhoto(pending);
      } else {
        await _conn.recoverPendingPhoto();
      }
      if (!mounted) return;
      if (_conn.promptPhotos.pending != null) {
        _showActionError(_chatL10n(context).photoPendingOther);
        return;
      }
    }
    if (!mounted || !await _persistDraft() || !mounted) return;
    if (_draftLocation != _conn.locationRevision ||
        _conn.profile?.id != _draftProfileID) {
      return;
    }
    _setChatState(() => _photoBusy = true);
    try {
      if (source == ImageSource.gallery) {
        await _pickGalleryPhotos();
        return;
      }
      final photo = await _conn.promptPhotos.pick(
        profileID: _draftProfileID,
        sessionID: widget.sessionID,
        directory: _draftDirectory,
        workspace: _draftWorkspace,
        source: source,
      );
      if (photo != null && mounted && _photoMatches(photo)) {
        await _applyPendingPhoto(photo, fromPicker: true);
      }
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) _setChatState(() => _photoBusy = false);
    }
  }

  /// Gallery: as many photos as the draft still has room for, in one visit.
  Future<void> _pickGalleryPhotos() async {
    final picked = await _conn.promptPhotos.pickMany(
      profileID: _draftProfileID,
      sessionID: widget.sessionID,
      directory: _draftDirectory,
      workspace: _draftWorkspace,
      limit: _maxAttachmentCount - _attachments.length,
    );
    if (!mounted || picked.isEmpty) return;
    if (_draftLocation != _conn.locationRevision ||
        _conn.profile?.id != _draftProfileID) {
      _showActionError(_chatL10n(context).photoOtherLocation);
      return;
    }
    var bytes = _attachments.fold<int>(
      0,
      (total, a) => total + _attachmentByteLength(a),
    );
    final added = <PromptAttachment>[];
    var full = false;
    for (final attachment in picked) {
      if (_attachments.any((a) => a.url == attachment.url) ||
          added.any((a) => a.url == attachment.url)) {
        continue;
      }
      final size = _attachmentByteLength(attachment);
      if (_attachments.length + added.length >= _maxAttachmentCount ||
          bytes + size > _maxAggregateAttachmentBytes) {
        full = true;
        break;
      }
      bytes += size;
      added.add(attachment);
    }
    if (added.isNotEmpty) {
      _setChatState(() => _attachments.addAll(added));
      await _persistDraft();
    }
    if (full && mounted) _showActionError(_chatL10n(context).photoDraftFull);
  }

  Future<void> _applyPendingPhoto(
    PendingPromptPhoto photo, {
    bool fromPicker = false,
  }) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_promptShelfBusy && !fromPicker) return;
    if (!_photoMatches(photo)) {
      _showActionError(_chatL10n(context).photoOtherLocation);
      return;
    }
    _setChatState(() => _photoBusy = true);
    try {
      final attachment = await _conn.promptPhotos.readPending(photo.id);
      if (!mounted || !_photoMatches(photo)) return;
      if (!_attachments.any((a) => a.url == attachment.url)) {
        if (_attachments.length >= _maxAttachmentCount ||
            _attachments.fold<int>(
                      0,
                      (total, a) => total + _attachmentByteLength(a),
                    ) +
                    _attachmentByteLength(attachment) >
                _maxAggregateAttachmentBytes) {
          _showActionError(_chatL10n(context).photoDraftFull);
          return;
        }
        _setChatState(() => _attachments.add(attachment));
      }
      if (await _persistDraft()) await _conn.promptPhotos.discard(photo.id);
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) _setChatState(() => _photoBusy = false);
    }
  }

  Future<void> _discardPendingPhoto(PendingPromptPhoto photo) async {
    try {
      await _conn.promptPhotos.discard(photo.id);
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    }
  }

  Future<void> _reviewPendingPhoto(PendingPromptPhoto photo) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_photoBusy) return;
    _setChatState(() => _photoBusy = true);
    try {
      final attachment = await _conn.promptPhotos.readPending(photo.id);
      if (!mounted) return;
      _setChatState(() => _photoBusy = false);
      await showFilePreviewSheet(
        context,
        FilePreviewData.fromDataUrl(
          name: attachment.filename,
          mimeType: attachment.mime,
          url: attachment.url,
        ),
      );
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) _setChatState(() => _photoBusy = false);
    }
  }

  /// Attaches an image committed into the composer by the IME — keyboard
  /// GIF/sticker insertions and Android's clipboard-image paste chip both
  /// arrive here via InputConnection.commitContent.
  ///
  /// This is the only zero-dependency image-paste path on Android: the
  /// framework's [Clipboard] service API reads `text/plain` exclusively, so
  /// a manual "Paste image" menu action cannot read image bytes without a
  /// platform plugin. Content without inline bytes (a URI-only commit) is
  /// ignored rather than half-attached.
  Future<void> _handleInsertedContent(KeyboardInsertedContent content) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final bytes = content.data;
    if (bytes == null || bytes.isEmpty) return;
    final mime = content.mimeType.isEmpty ? 'image/png' : content.mimeType;
    final extension = switch (mime.toLowerCase()) {
      'image/jpeg' || 'image/jpg' => 'jpg',
      'image/gif' => 'gif',
      'image/webp' => 'webp',
      'image/bmp' => 'bmp',
      _ => 'png',
    };
    final name =
        'pasted-image-${DateTime.now().millisecondsSinceEpoch}.$extension';
    try {
      await _addPreviewAttachment(
        filename: name,
        mimeType: mime,
        data: FilePreviewData(name: name, mimeType: mime, bytes: bytes),
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<PromptAttachment?> _chooseAttachment(
    List<PromptAttachment> current,
  ) async {
    final strings = _chatL10n(context);
    if (!_supportsPromptAttachments) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return null;
    }
    final unsupportedAttachment = strings.chatAttachmentUnsupported;
    if (current.length >= _maxAttachmentCount) {
      throw ProductException(
        strings.chatUiAttachmentCountLimit(_maxAttachmentCount),
      );
    }
    final currentBytes = current.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes >= _maxAggregateAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    final file = await FilePicker.pickFile(
      dialogTitle: strings.chatUiAttachToPrompt,
    );
    if (file == null) return null;
    final size = await file.length();
    if (size > _maxAttachmentBytes) {
      throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
    }
    if (size > 0 && currentBytes + size > _maxAggregateAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    final remainingAggregateBytes = _maxAggregateAttachmentBytes - currentBytes;
    final readLimit = remainingAggregateBytes < _maxAttachmentBytes
        ? remainingAggregateBytes
        : _maxAttachmentBytes;
    final bytes = await readAttachmentBytesWithinLimit(
      file,
      maxBytes: readLimit,
    );
    if (bytes == null && readLimit < _maxAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    if (bytes == null) {
      throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
    }
    if (isOfficeDocument(file.name)) {
      return _officeAttachment(file.name, bytes);
    }
    final mime = promptAttachmentMime(filename: file.name, bytes: bytes);
    if (mime == null) {
      throw ProductException(unsupportedAttachment);
    }
    final attachment = PromptAttachment(
      mime: mime,
      filename: file.name,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    return attachment;
  }

  /// A workbook or Word document, attached as the text it contains. No model
  /// takes these files as they are; read on the phone, a sheet is CSV and a
  /// document is its paragraphs, which an agent can work with.
  Future<PromptAttachment> _officeAttachment(
    String filename,
    Uint8List bytes,
  ) async {
    final strings = _chatL10n(context);
    final OfficeText converted;
    try {
      // Off the UI thread: a large sheet is a lot of XML.
      converted = await compute(
        (({String name, Uint8List bytes}) input) =>
            officeDocumentAsText(input.name, input.bytes),
        (name: filename, bytes: bytes),
      );
    } on FormatException {
      throw ProductException(strings.chatAttachmentOfficeUnreadable(filename));
    }
    if (converted.text.isEmpty) {
      throw ProductException(strings.chatAttachmentOfficeEmpty(filename));
    }
    final spreadsheet = !filename.toLowerCase().endsWith('.docx');
    final text = [
      strings.chatAttachmentOfficeHeader(filename),
      if (converted.truncated) strings.chatAttachmentOfficeTruncated,
      '',
      converted.text,
    ].join('\n');
    if (mounted) {
      _showComposerNote(
        spreadsheet
            ? strings.chatAttachmentSheetAttached(
                filename,
                converted.sections,
                converted.lines,
              )
            : strings.chatAttachmentDocumentAttached(filename),
      );
    }
    return PromptAttachment(
      mime: 'text/plain',
      filename: '$filename.${spreadsheet ? 'csv' : 'txt'}',
      url: 'data:text/plain;base64,${base64Encode(utf8.encode(text))}',
    );
  }

  Future<void> _openPromptEditor() async {
    if (_conn.isIsolated) return;
    if (_promptShelfBusy) return;
    final result = await Navigator.of(context).push<_PromptEditorResult>(
      KitPageRoute<_PromptEditorResult>(
        fullscreenDialog: true,
        builder: (_) => _PromptEditorScreen(
          initialValue: _composer.value,
          initialAttachments: _attachments,
          chooseAttachment: _supportsPromptAttachments
              ? _chooseAttachment
              : null,
        ),
      ),
    );
    if (!mounted || result == null) return;
    _composer.value = result.value;
    _setChatState(() {
      _attachments
        ..clear()
        ..addAll(result.attachments);
    });
    _focus.requestFocus();
  }

  int _attachmentByteLength(PromptAttachment attachment) {
    final comma = attachment.url.indexOf(',');
    if (comma < 0) return 0;
    final header = attachment.url.substring(0, comma);
    final payload = attachment.url.substring(comma + 1);
    if (!header.endsWith(';base64')) return utf8.encode(payload).length;
    final padding = payload.endsWith('==')
        ? 2
        : payload.endsWith('=')
        ? 1
        : 0;
    return (payload.length * 3 ~/ 4) - padding;
  }

  String _mimeForFilename(String filename) {
    final extension = filename.contains('.')
        ? filename.split('.').last.toLowerCase()
        : '';
    return switch (extension) {
      'png' => 'image/png',
      'jpg' || 'jpeg' => 'image/jpeg',
      'gif' => 'image/gif',
      'webp' => 'image/webp',
      'svg' => 'image/svg+xml',
      'pdf' => 'application/pdf',
      'json' => 'application/json',
      'md' || 'txt' || 'log' => 'text/plain',
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' => 'text/plain',
      _ => 'application/octet-stream',
    };
  }

  /// Once per session: true while the first staged attachments of this
  /// session are showing, false afterwards.
  bool _attachmentNoteVisible() {
    if (_attachments.isEmpty) {
      if (_attachmentNoteActive) {
        _attachmentNoteActive = false;
        _attachmentNoteShownSessions.add(widget.sessionID);
      }
      return false;
    }
    if (!_supportsPromptAttachments) return true;
    if (_attachmentNoteActive) return true;
    if (_attachmentNoteShownSessions.contains(widget.sessionID)) return false;
    _attachmentNoteActive = true;
    return true;
  }
}
