part of '../connection.dart';

/// Directory inventory shares the question reader's bounded selection. Schema
/// snapshots are detached from transports and never persisted with credentials.
class _DirectoryForm {
  _DirectoryForm(Api2FormInfo value, this.fence)
    : form = snapshotForm(value),
      revision = formSchemaRevision(value);
  final Api2FormInfo form;
  final String revision;
  final _DirectoryQuestion fence;
}

String _formInventoryKey(Api2FormInfo form) =>
    jsonEncode([form.sessionID, form.id]);

extension _FeedForms on ConnectionController {
  CapturedFormRequest? _formRequestForFeedItem(ChatFeedItem item) {
    final source = item.sourceId ?? _openCodeSourceId;
    final owner = source == _openCodeSourceId ? this : _sideForSource(source);
    if (_disposed || owner == null || owner._disposed) return null;
    bool rowCurrent() =>
        !_disposed &&
        identical(
          source == _openCodeSourceId ? this : _sideForSource(source),
          owner,
        ) &&
        _feedContainsQuestionRow(
          owner._ocChatFeed(const ChatFeedFilter(includeSubagents: true)),
          item,
        );
    if (!rowCurrent()) return null;
    final profileID = (owner._connectedProfile ?? owner.profile)?.id;
    if (profileID == null || !isProfileReadable(profileID)) return null;
    if (owner.directory == item.directory) {
      final form = owner.formForSession(item.sessionID);
      return form == null
          ? null
          : owner._formRequestForForm(form, rowCurrent: rowCurrent);
    }
    final entry = owner._feedDirectoryForms[item.directory]?.values
        .where(
          (e) =>
              e.form.sessionID == item.sessionID &&
              owner._directoryQuestionCurrent(e.fence),
        )
        .firstOrNull;
    if (entry == null) return null;
    return owner._captureForm(
      entry.form,
      entry.fence,
      item.directory,
      null,
      () =>
          rowCurrent() &&
          isProfileReadable(profileID) &&
          identical(
            owner._feedDirectoryForms[item.directory]?[_formInventoryKey(
              entry.form,
            )],
            entry,
          ),
      scoped: true,
    );
  }

  CapturedFormRequest? _formRequestForForm(
    Api2FormInfo form, {
    bool Function()? rowCurrent,
  }) {
    final target = _connectedProfile ?? profile;
    final folder = directory;
    if (!supportsForms || target == null || folder == null || _disposed) {
      return null;
    }
    final revision = formSchemaRevision(form), space = workspace;
    final fence = _DirectoryQuestion(
      const PendingQuestion(id: '', sessionID: '', prompts: []),
      false,
      target,
      _generation,
    );
    return _captureForm(form, fence, folder, space, () {
      final current = forms[form.id];
      return directory == folder &&
          workspace == space &&
          (rowCurrent?.call() ?? true) &&
          current != null &&
          current.sessionID == form.sessionID &&
          formSchemaRevision(current) == revision;
    });
  }

  CapturedFormRequest? _captureForm(
    Api2FormInfo value,
    _DirectoryQuestion fence,
    String folder,
    String? space,
    bool Function() inventoryCurrent, {
    bool scoped = false,
  }) {
    final form = snapshotForm(value);
    final identity = FormRequestIdentity(
      profileID: fence.profile.id,
      directory: folder,
      workspace: space,
      sessionID: form.sessionID,
      formID: form.id,
      revision: formSchemaRevision(form),
    );
    var invalidated = false;
    bool pending() =>
        !invalidated &&
        _directoryQuestionCurrent(fence) &&
        inventoryCurrent() &&
        !_formSettled.contains(identity.key);
    if (!pending()) return null;
    void removeCaptured({required bool settled}) {
      invalidated = true;
      if (settled) _formSettled.add(identity.key);
      // A late completion must not remove a replacement schema or new profile.
      if (!_directoryQuestionCurrent(fence)) return;
      _feedQuestionEpoch++;
      if (scoped) {
        final key = _formInventoryKey(form);
        if (_feedDirectoryForms[folder]?[key]?.revision == identity.revision) {
          _feedDirectoryForms[folder]?.remove(key);
        }
      } else if (directory == folder &&
          workspace == space &&
          forms[form.id]?.sessionID == form.sessionID &&
          formSchemaRevision(forms[form.id]!) == identity.revision) {
        if (settled) {
          _resolveForm(form.id);
        } else {
          forms.remove(form.id);
          _formRevision++;
          _syncInputAlerts();
          unawaited(refreshPendingForms());
        }
      }
      if (!_disposed) _notifyListeners();
      _feedScheduleRefresh();
    }

    Future<void> send(Map<String, dynamic>? answer) {
      final running = _formReplies[identity.key];
      if (running != null) return running;
      // Detach typed values before any await: booleans, numbers, lists and
      // conditional fields remain the renderer's exact JSON answer.
      final captured = answer == null
          ? null
          : jsonDecode(jsonEncode(answer)) as Map<String, dynamic>;
      final result = () async {
        if (!pending()) {
          throw const ProductException(
            'This form changed. Reopen the current request.',
          );
        }
        if (_formAttempts.contains(identity.key)) {
          throw const ProductException(
            'Delivery is unconfirmed. Check the conversation before answering again.',
          );
        }
        final pair = scoped ? _buildTransportPair(fence.profile) : null;
        try {
          final gateway = pair?.gateway ?? await _requireActionTransport();
          if (pair != null) {
            gateway.setLocation(directory: folder, workspace: space);
            pair.operations.setLocation(directory: folder, workspace: space);
          }
          if (!pending()) {
            throw const ProductException(
              'This form changed. Reopen the current request.',
            );
          }
          // Both chat and list routes reconcile before dispatch. Volatile form
          // events may have been missed even while the connection looks live.
          final fresh = await gateway.pendingForms().timeout(
            const Duration(seconds: 3),
          );
          final latest = fresh
              .where((f) => f.id == form.id && f.sessionID == form.sessionID)
              .firstOrNull;
          if (latest == null ||
              formSchemaRevision(latest) != identity.revision) {
            removeCaptured(settled: false);
            throw const ProductException(
              'This form changed. Reopen the current request.',
            );
          }
          if (!pending()) {
            throw const ProductException(
              'This form changed. Reopen the current request.',
            );
          }
          _formAttempts.add(identity.key);
          try {
            if (captured == null) {
              await gateway
                  .cancelForm(form.sessionID, form.id)
                  .timeout(const Duration(seconds: 15));
            } else {
              await gateway
                  .replyForm(form.sessionID, form.id, captured)
                  .timeout(const Duration(seconds: 15));
            }
          } on ApiException catch (error) {
            if (error.errorTag == 'FormAlreadySettledError' ||
                error.errorTag == 'FormNotFoundError') {
              removeCaptured(settled: true);
              if (captured == null) return;
            } else if (error.statusCode == 400) {
              // A definitive validation rejection did not accept the answer.
              _formAttempts.remove(identity.key);
            }
            rethrow;
          }
          removeCaptured(settled: true);
        } finally {
          pair?.gateway.close();
        }
      }();
      _formReplies[identity.key] = result;
      return result.whenComplete(() => _formReplies.remove(identity.key));
    }

    return CapturedFormRequest(
      form: form,
      identity: identity,
      isPending: pending,
      reply: send,
      cancel: () => send(null),
    );
  }
}
