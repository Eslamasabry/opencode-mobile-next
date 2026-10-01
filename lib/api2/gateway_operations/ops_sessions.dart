part of '../gateway_operations.dart';

mixin _Api2SessionOps on _Api2Core {
  // ---------------- Session operations ----------------

  @override
  Future<Session> getSessionDetails(String id) => _guard(
    'Could not load the session',
    () async => mapApi2Session(await client.session(id)),
  );

  @override
  Future<List<Session>> listSessionChildren(String id) =>
      _guard('Could not load subagent sessions', () async {
        final page = await client.sessions(parentID: id, limit: 200);
        return page.data.map(mapApi2Session).toList();
      });

  @override
  Future<String?> shareSession(String id) => Future.error(
    const ProductException('Session sharing is unavailable on this server'),
  );

  @override
  Future<void> unshareSession(String id) => Future.error(
    const ProductException('Session sharing is unavailable on this server'),
  );

  @override
  Future<void> archiveSession(String id) => Future.error(
    const ProductException('Session archiving is unavailable on this server'),
  );

  @override
  Future<String> forkSession(String id, {String? messageID}) =>
      _guard('Could not fork the session', () async {
        // Lossy: v1 forked "at" a message. The v2 boundary union offers
        // {type: "before", messageID} (exclusive) or {type: "through"}
        // (everything); an anchored fork therefore excludes the anchor
        // message itself.
        final json = await _transport.postJson(
          '/session/$id/fork',
          body: {
            'boundary': messageID != null && messageID.isNotEmpty
                ? {'type': 'before', 'messageID': messageID}
                : {'type': 'through'},
          },
        );
        final forked = Api2Session.fromJson(_dataMap(json));
        if (forked == null) {
          throw const ProductException('OpenCode returned no forked session');
        }
        // Give the copy a plain name (best effort; see the v1 client).
        try {
          final original = await client.session(id);
          final title = forkedSessionTitle(original.title);
          if (title != null) await client.renameSession(forked.id, title);
        } on Object {
          // Leave the server's title; the display layer hides its stamp.
        }
        return forked.id;
      });

  @override
  Future<void> revertSession(String id, String messageID) async {
    await stageSessionRevert(id, messageID, applyFiles: true);
  }

  @override
  Future<void> restoreSession(String id) => clearSessionRevert(id);

  @override
  Future<void> viewSession(String sessionID, int idle) =>
      _guard('Could not synchronize read state', () async {
        await _transport.postJson(
          '/session/${Uri.encodeComponent(sessionID)}/view',
          query: _loc(),
          body: {'idle': idle},
        );
      });

  @override
  Future<String?> sessionRevertPrompt(String sessionID, String messageID) =>
      _guard('Could not load the prompt', () async {
        final message = await client.message(sessionID, messageID);
        if (message.id != messageID || message is! Api2UserMessage) return null;
        return message.text.isNotEmpty
            ? message.text
            : message.files
                  .map((file) => file.name ?? '')
                  .where((name) => name.isNotEmpty)
                  .join(', ');
      });

  @override
  Future<SessionRevert> stageSessionRevert(
    String sessionID,
    String messageID, {
    required bool applyFiles,
  }) => _guard('Could not stage the revert', () async {
    final json = await _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/stage',
      body: {'messageID': messageID, 'files': applyFiles},
    );
    final revert = Api2SessionRevert.fromJson(_dataMap(json));
    if (revert == null) {
      throw const ProductException('OpenCode returned no staged boundary.');
    }
    return mapApi2Revert(revert);
  });

  @override
  Future<void> clearSessionRevert(String sessionID) => _guard(
    'Could not clear the staged revert',
    () => _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/clear',
    ),
  );

  @override
  Future<void> commitSessionRevert(String sessionID) => _guard(
    'Could not commit the staged revert',
    () => _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/commit',
    ),
  );

  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) => _guard(
    'Could not compact the session',
    // Lossy: v2 compaction has no provider/model pair; the session's own
    // compaction agent handles it.
    () => _transport.postJson('/session/$id/compact', body: const {}),
  );

  @override
  Future<void> addSessionLocationReminder(String sessionID, String directory) =>
      _guard('Could not update the session location context', () async {
        await _transport.postJson(
          '/session/$sessionID/synthetic',
          body: {
            'text':
                '<system-reminder>The user has changed the current working '
                'directory to "$directory". This is still the same project '
                'but at a possibly new location; take this into account when '
                'working with any files from now on.</system-reminder>',
            'resume': false,
          },
        );
      });

  // ---------------- Requests (questions & saved permissions) ----------------

  @override
  Future<List<PendingQuestion>> listQuestions() async =>
      // Questions were replaced by forms (capability legacyQuestionRequests:
      // false); the forms UI wires in a later slice.
      const [];

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) =>
      Future.error(
        const ProductException(
          'Question dialogs are unavailable on this server',
        ),
      );

  @override
  Future<void> rejectQuestion(String id) => Future.error(
    const ProductException('Question dialogs are unavailable on this server'),
  );

  @override
  Future<List<SavedPermission>> listSavedPermissions() =>
      _guard('Could not load always allowed actions', () async {
        final project = await loadCurrentProject();
        final projectID = project?.id ?? '';
        final json = await _transport.getJson(
          '/permission/saved',
          query: {'projectID': ?(projectID.isEmpty ? null : projectID)},
        );
        return [
          for (final item in _dataMaps(json))
            if ((item['id'] ?? '').toString().isNotEmpty)
              SavedPermission(
                id: item['id'].toString(),
                projectID: (item['projectID'] ?? '').toString(),
                action: (item['action'] ?? '').toString(),
                resource: (item['resource'] ?? '').toString(),
              ),
        ];
      });

  @override
  Future<void> removeSavedPermission(String id) =>
      _guard('Could not revoke the always allowed action', () async {
        if (id.trim().isEmpty) {
          throw const ProductException('Saved permission ID is missing');
        }
        await _transport.deleteJson(
          '/permission/saved/${Uri.encodeComponent(id)}',
        );
      });
}
