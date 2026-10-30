part of '../chat_screen.dart';

// `@file` mentions: typing `@ser` lists matching project files beside the
// subagents, and the "+" tool "File from the project" picks one from a
// sheet. A pick writes `@lib/server.dart ` into the prompt and stages the
// file as an attachment that names that text, so the agent reads the file.

mixin _ChatMentionFields {
  Timer? _fileSearchTimer;
  int _fileSearchGeneration = 0;
  String _fileSearchQuery = '';
  List<String> _fileHits = const [];
}

/// Searches the project's file names while the person types, waiting for the
/// typing to settle.
const _fileSearchSettle = Duration(milliseconds: 150);

/// The most files one search keeps, and the most the strip shows.
const _fileSearchLimit = 20;

extension _ChatMentions on _ChatScreenState {
  void _insertAgentMention(CatalogAgent agent) {
    if (!_supportsPromptAgentMentions) return;
    _insertMentionText(agent.id);
  }

  /// Writes `@name ` over the `@word` being typed, or at the caret.
  void _insertMentionText(String name) {
    final current = _composer.value;
    final query = _activeAgentQuery(current);
    final selection = current.selection;
    final fallback = selection.isValid
        ? selection.start.clamp(0, current.text.length)
        : current.text.length;
    final start = query?.start ?? fallback;
    final end =
        query?.end ??
        (selection.isValid
            ? selection.end.clamp(start, current.text.length)
            : start);
    final needsLeadingSpace =
        query == null &&
        start > 0 &&
        !RegExp(r'\s').hasMatch(current.text.substring(start - 1, start));
    final needsTrailingSpace =
        end == current.text.length ||
        !RegExp(r'\s').hasMatch(current.text.substring(end, end + 1));
    final replacement =
        '${needsLeadingSpace ? ' ' : ''}@$name${needsTrailingSpace ? ' ' : ''}';
    final nextText = current.text.replaceRange(start, end, replacement);
    _composer.value = TextEditingValue(
      text: nextText,
      selection: TextSelection.collapsed(offset: start + replacement.length),
    );
    _focus.requestFocus();
  }

  /// Project files can be searched and pointed at from this conversation.
  bool get _canMentionFiles =>
      !_conn.isIsolated && !_watching && _conn.capabilities.fileBrowsing;

  /// The server takes a file part for the pick; Paseo's agents take pictures
  /// only, so there the `@path` text alone reaches the agent.
  bool get _stagesMentionedFiles =>
      _supportsPromptAttachments && !_conn.capabilities.promptImagesOnly;

  /// A path the server returned, relative to the project.
  String _mentionPath(String raw) {
    var path = raw;
    final directory = (_conn.directory ?? '').replaceFirst(RegExp(r'/+$'), '');
    if (directory.isNotEmpty && path.startsWith('$directory/')) {
      path = path.substring(directory.length + 1);
    }
    while (path.startsWith('./')) {
      path = path.substring(2);
    }
    return path;
  }

  /// Files whose names can be typed after an `@`: no spaces.
  List<String> _mentionableFiles(Iterable<String> raw, String query) {
    final needle = query.toLowerCase();
    int rank(String path) {
      final name = path.split('/').last.toLowerCase();
      return name.startsWith(needle)
          ? 0
          : name.contains(needle)
          ? 1
          : 2;
    }

    final seen = <String>{};
    // A folder (a result ending in `/`) has no file name to show and is not
    // a file to read, so the suggestions leave it out.
    final paths = [
      for (final value in raw.where((r) => !r.endsWith('/')).map(_mentionPath))
        if (value.isNotEmpty &&
            !value.contains(RegExp(r'\s')) &&
            seen.add(value))
          value,
    ];
    final order = {for (var i = 0; i < paths.length; i++) paths[i]: i};
    paths.sort((a, b) {
      final byRank = rank(a).compareTo(rank(b));
      return byRank != 0 ? byRank : order[a]!.compareTo(order[b]!);
    });
    return paths.take(_fileSearchLimit).toList();
  }

  Future<List<String>> _findMentionFiles(String query) async {
    final api = await _conn.prepareActionTransport();
    if (api == null) throw StateError('offline');
    return _mentionableFiles(await api.findFile(query), query);
  }

  /// One folder of the project for the file sheet: its folders (paths with
  /// a trailing `/`) first, then its files.
  Future<List<String>> _browseMentionFolder(String folder) async {
    final api = await _conn.prepareActionTransport();
    if (api == null) throw StateError('offline');
    final nodes = await api.listFiles(folder);
    final folders = [
      for (final node in nodes)
        if (node.isDir && !_mentionPath(node.path).contains(RegExp(r'\s')))
          '${_mentionPath(node.path).replaceFirst(RegExp(r'/+$'), '')}/',
    ]..sort();
    return [
      ...folders,
      ..._mentionableFiles([
        for (final node in nodes)
          if (!node.isDir) node.path,
      ], ''),
    ];
  }

  /// Runs on every composer change: keeps the file rows in step with the
  /// `@word` at the caret, and drops a staged file whose mention text the
  /// person deleted.
  void _onMentionText() {
    if (_attachments.any((attachment) => attachment.isFileMention)) {
      final kept = resolveFileMentions(_composer.text, _attachments);
      if (kept.length != _attachments.length) {
        _setChatState(() {
          _attachments
            ..clear()
            ..addAll(kept);
        });
      }
    }
    final query = _canMentionFiles ? _activeAgentQuery(_composer.value) : null;
    final word = query?.query ?? '';
    if (word.isEmpty) {
      _fileSearchTimer?.cancel();
      _fileSearchQuery = '';
      _fileSearchGeneration++;
      if (_fileHits.isNotEmpty) _setChatState(() => _fileHits = const []);
      return;
    }
    if (word == _fileSearchQuery) return;
    _fileSearchQuery = word;
    _fileSearchTimer?.cancel();
    _fileSearchTimer = Timer(
      _fileSearchSettle,
      () => unawaited(_searchMentionFiles(word)),
    );
  }

  Future<void> _searchMentionFiles(String word) async {
    final generation = ++_fileSearchGeneration;
    try {
      final found = await _findMentionFiles(word);
      if (!mounted || generation != _fileSearchGeneration) return;
      _setChatState(() => _fileHits = found);
    } catch (_) {
      // No spinner and no error row in a suggestion strip: a search that
      // failed shows no files, and the agents still show.
      if (!mounted || generation != _fileSearchGeneration) return;
      if (_fileHits.isNotEmpty) _setChatState(() => _fileHits = const []);
    }
  }

  void _stopMentionSearch() {
    _fileSearchTimer?.cancel();
    _fileSearchGeneration++;
  }

  Future<void> _openProjectFiles() async {
    if (!_canMentionFiles) return;
    final path = await showKitProjectFileSheet(
      context,
      search: _findMentionFiles,
      initial: _browseMentionFolder,
    );
    if (path == null || !mounted) return;
    _insertFileMention(path);
  }

  /// Writes `@path ` over the `@word` being typed (or at the caret) and
  /// stages the file as the attachment that mention names.
  void _insertFileMention(String path) {
    if (!_canMentionFiles) return;
    _insertMentionText(path);
    final directory = _conn.directory ?? '';
    if (!_stagesMentionedFiles ||
        directory.isEmpty ||
        _attachments.length >= _maxAttachmentCount ||
        _attachments.any((a) => a.mentionPath == path)) {
      return;
    }
    _setChatState(
      () => _attachments.add(
        PromptAttachment.projectFile(directory: directory, relativePath: path),
      ),
    );
  }
}
