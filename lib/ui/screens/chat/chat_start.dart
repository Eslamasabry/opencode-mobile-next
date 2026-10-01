part of '../chat_screen.dart';

// An empty conversation's start: the facts above the composer and the
// starter prompts.

mixin _ChatStartFields {
  /// Facts about the folder a new conversation runs in; null while none
  /// have been asked for.
  final _startFacts = ValueNotifier<ChatStartFacts?>(null);
  String? _startFactsDirectory;
  bool _startFactsRequested = false;
  int _startFactsGeneration = 0;
  Timer? _startFactsFallback;
}

extension _ChatStart on _ChatScreenState {
  /// A starter fills the composer and never sends: the person may want to
  /// finish the sentence or change a word first.
  void _insertSuggestion(String text) {
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    if (_focus.hasFocus) {
      // Focus alone does not bring back a keyboard the person dismissed
      // while the field kept focus.
      unawaited(SystemChannels.textInput.invokeMethod<void>('TextInput.show'));
    } else {
      _focus.requestFocus();
    }
  }

  /// The ways to start for the folder as known now; nothing once the
  /// person has typed, so the row never competes with their own words.
  Widget _startersRow() => ListenableBuilder(
    listenable: Listenable.merge([_startFacts, _composer]),
    builder: (context, _) {
      final starters = _composer.text.trim().isNotEmpty
          ? const <ChatStarter>[]
          : chatStartSuggestions(
              _currentStartFacts(),
              l10n: _chatL10n(context),
            );
      if (starters.isEmpty) return const SizedBox.shrink();
      return Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 860),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: _ChatStarters(
              starters: starters,
              onPick: (starter) => _insertSuggestion(starter.text),
            ),
          ),
        ),
      );
    },
  );

  /// The facts the empty state shows now: pending until the folder the
  /// conversation runs in has been looked at.
  ChatStartFacts _currentStartFacts() {
    final facts = _startFacts.value;
    final directory = _conn.directory;
    if (facts != null && facts.directory == directory) return facts;
    return ChatStartFacts.pending(directory);
  }

  /// Asks once per folder, and only when an empty conversation is on screen,
  /// so opening an existing conversation costs nothing.
  void _requestStartFacts() {
    final directory = _conn.directory;
    if (_startFactsRequested && _startFactsDirectory == directory) return;
    _startFactsRequested = true;
    _startFactsDirectory = directory;
    // Built during build; the load starts after the frame so its first
    // answer never marks widgets dirty mid-build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_loadStartFacts(directory));
    });
  }

  Future<void> _loadStartFacts(String? directory) async {
    final generation = ++_startFactsGeneration;
    final bare = ChatStartFacts(directory: directory);
    void publish(ChatStartFacts facts) {
      if (!mounted || generation != _startFactsGeneration) return;
      _startFactsFallback?.cancel();
      _startFacts.value = facts;
    }

    if (_conn.isIsolated || !bare.hasProject) {
      publish(bare);
      return;
    }
    // A slow server must not hold the starters back: after a moment they
    // appear from what is known, and a late answer still sharpens them.
    _startFactsFallback?.cancel();
    _startFactsFallback = Timer(const Duration(seconds: 3), () {
      if (mounted &&
          generation == _startFactsGeneration &&
          _startFacts.value?.directory != directory) {
        _startFacts.value = bare;
      }
    });
    final filesFuture = _conn.capabilities.fileBrowsing
        ? () async {
            final api = await _conn.prepareActionTransport();
            return api?.listFiles('');
          }().then<List<FileNode>?>((files) => files, onError: (_) => null)
        : Future<List<FileNode>?>.value(null);
    final vcsFuture = () async {
      final repository = await _conn.prepareActionRepository();
      return repository?.loadVersionControlHealth();
    }().then<VersionControlHealth?>((health) => health, onError: (_) => null);
    final (files, vcs) = await (filesFuture, vcsFuture).wait;
    final git = switch (vcs?.setupState) {
      VersionControlSetupState.git => true,
      VersionControlSetupState.absent => false,
      _ => (vcs?.branch?.isNotEmpty ?? false) ? true : null,
    };
    final branch = vcs?.branch?.trim() ?? '';
    publish(
      ChatStartFacts(
        directory: directory,
        // The server may list Git's own folder; it is not the project's.
        entries: files?.where((node) => node.name != '.git').length,
        git: git,
        // A branch name needs a commit to point at; an unborn branch reads
        // back as HEAD, or not at all.
        hasHistory: git != false && branch.isNotEmpty && branch != 'HEAD',
        changes: vcs?.changes.length,
      ),
    );
  }
}
