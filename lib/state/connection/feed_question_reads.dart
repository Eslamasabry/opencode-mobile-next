part of '../connection.dart';

/// Runtime-only inventory; no question text or credentials are persisted.
class _DirectoryQuestion {
  _DirectoryQuestion(
    this.question,
    this.modern,
    ServerProfile source,
    this.generation,
  ) : profile = ServerProfile(
        id: source.id,
        name: source.name,
        baseUrl: source.baseUrl,
        username: source.username,
        password: source.password,
        backend: source.backend,
        flavor: source.flavor,
        requiresPasswordReentry: source.requiresPasswordReentry,
      )..cleartextConfirmedOrigin = source.cleartextConfirmedOrigin;
  final PendingQuestion question;
  final bool modern;
  final ServerProfile profile;
  final int generation;
  bool attempted = false;
}

extension _FeedQuestionReads on ConnectionController {
  bool _directoryQuestionCurrent(_DirectoryQuestion entry) =>
      !_disposed &&
      entry.generation == _generation &&
      isProfileReadable(entry.profile.id) &&
      (_connectedProfile ?? profile)?.id == entry.profile.id &&
      store.profiles.any(
        (p) =>
            p.id == entry.profile.id &&
            p.baseUrl == entry.profile.baseUrl &&
            p.username == entry.profile.username &&
            p.password == entry.profile.password &&
            p.flavor == entry.profile.flavor &&
            p.backend == entry.profile.backend &&
            !p.requiresPasswordReentry,
      );

  void _invalidateFeedDirectoryQuestions(EventEnvelope event) {
    if (!event.type.startsWith('question.') &&
        !event.type.startsWith('permission.') &&
        !event.type.startsWith('form.v2.') &&
        event.type != 'session.deleted') {
      return;
    }
    _feedQuestionEpoch++;
    final folder = event.directory;
    if (folder == null) {
      _feedDirectoryQuestions.clear();
      _feedDirectoryForms.clear();
    } else {
      _feedDirectoryForms.remove(
        ConnectionController.normalizeDirectoryPath(folder),
      );
      _feedDirectoryQuestions.remove(
        ConnectionController.normalizeDirectoryPath(folder),
      );
    }
    _feedScheduleRefresh();
    if (!_disposed) _notifyListeners();
  }

  Future<void> _refreshFeedDirectoryQuestions() async {
    final target = _connectedProfile ?? profile;
    if (target == null ||
        target.backend != ServerBackend.openCode ||
        _disposed) {
      return;
    }
    final generation = _generation, epoch = _feedQuestionEpoch;
    final fence = _DirectoryQuestion(
      const PendingQuestion(id: '', sessionID: '', prompts: []),
      false,
      target,
      generation,
    );
    bool current() =>
        _directoryQuestionCurrent(fence) && epoch == _feedQuestionEpoch;
    final rows = _ocChatFeed(
      const ChatFeedFilter(includeSubagents: true),
    ).items;
    final folders =
        rows
            .where(
              (r) =>
                  r.status == ChatStatus.needsYou && r.directory != directory,
            )
            .map((r) => r.directory)
            .toSet()
            .toList()
          ..sort();
    // Four directories per pass: one form read or two question reads each.
    // Each read times out after 3 seconds; at most 24 seconds in total.
    // Rotate to give later waiting directories a turn; excluded data is unknown.
    final selected = <String>[];
    for (var i = 0; i < 4 && i < folders.length; i++) {
      selected.add(folders[(_feedQuestionCursor + i) % folders.length]);
    }
    if (folders.isNotEmpty) {
      _feedQuestionCursor =
          (_feedQuestionCursor + selected.length) % folders.length;
    }
    _feedDirectoryQuestions.removeWhere((key, _) => !selected.contains(key));
    _feedDirectoryForms.removeWhere((key, _) => !selected.contains(key));
    for (final folder in selected) {
      if (!current()) return;
      final pair = _buildTransportPair(fence.profile);
      try {
        pair.gateway.setLocation(directory: folder);
        pair.operations.setLocation(directory: folder);
        if (pair.gateway.capabilities.forms) {
          List<Api2FormInfo> pending = const [];
          try {
            pending = await pair.gateway.pendingForms().timeout(
              const Duration(seconds: 3),
            );
          } catch (_) {}
          if (!current()) return;
          final previous = _feedDirectoryForms[folder];
          final next = <String, _DirectoryForm>{};
          for (final form in pending.take(128)) {
            if (form.id.isEmpty ||
                !rows.any(
                  (r) => r.directory == folder && r.sessionID == form.sessionID,
                )) {
              continue;
            }
            final key = _formInventoryKey(form),
                old = previous?[_formInventoryKey(form)];
            next[key] =
                old != null &&
                    _directoryQuestionCurrent(old.fence) &&
                    old.revision == formSchemaRevision(form)
                ? old
                : _DirectoryForm(form, fence);
          }
          _feedDirectoryForms[folder] = next;
          _feedDirectoryQuestions.remove(folder);
          continue;
        }
        List<PendingQuestion>? legacy, modern;
        try {
          legacy = await pair.operations.listQuestions().timeout(
            const Duration(seconds: 3),
          );
        } catch (_) {}
        if (!current()) return;
        try {
          modern = (await pair.gateway.pendingQuestionsV2().timeout(
            const Duration(seconds: 3),
          )).take(128).map(PendingQuestion.fromJson).toList();
        } catch (_) {}
        if (!current()) return;
        final next = <String, _DirectoryQuestion>{};
        final previous = _feedDirectoryQuestions[folder];
        void add(PendingQuestion question, bool isModern) {
          if (question.id.isEmpty ||
              !rows.any(
                (r) =>
                    r.directory == folder && r.sessionID == question.sessionID,
              )) {
            return;
          }
          final old = previous?[question.id];
          next[question.id] =
              old != null &&
                  _directoryQuestionCurrent(old) &&
                  old.modern == isModern &&
                  _questionContents(old.question) == _questionContents(question)
              ? old
              : _DirectoryQuestion(
                  question,
                  isModern,
                  fence.profile,
                  generation,
                );
        }

        for (final q in (legacy ?? const <PendingQuestion>[]).take(128)) {
          add(q, false);
        }
        for (final q in modern ?? const <PendingQuestion>[]) {
          add(q, true);
        }
        // Failed reads never keep old actionable content.
        _feedDirectoryQuestions[folder] = next;
      } finally {
        pair.gateway.close();
      }
    }
  }
}
