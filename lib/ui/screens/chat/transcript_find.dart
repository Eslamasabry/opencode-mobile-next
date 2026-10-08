part of '../chat_screen.dart';

/// Find in this conversation, docked under the top bar: the field and its
/// close, the count once ("3 of 12 matches") with previous and next, and,
/// only while part of the history is not loaded, what is missing and the
/// way to search it all. The hits themselves are marked in the transcript.
class _TranscriptFindBar extends StatefulWidget {
  const _TranscriptFindBar({
    required this.controller,
    required this.focusNode,
    required this.count,
    required this.current,
    required this.hasOlder,
    required this.loading,
    required this.onChanged,
    required this.onNext,
    required this.onPrevious,
    required this.onClose,
    required this.onLoadOlder,
    required this.needsReload,
    required this.searchingAll,
    required this.onCancelLoading,
    this.error,
  });
  final TextEditingController controller;
  final FocusNode focusNode;
  final int count;
  final int current;
  final bool hasOlder;
  final bool loading;
  final bool needsReload;
  final bool searchingAll;
  final VoidCallback onCancelLoading;
  final Object? error;
  final ValueChanged<String> onChanged;
  final VoidCallback onNext;
  final VoidCallback onPrevious;
  final VoidCallback onClose;
  final VoidCallback onLoadOlder;

  @override
  State<_TranscriptFindBar> createState() => _TranscriptFindBarState();
}

class _TranscriptFindBarState extends State<_TranscriptFindBar> {
  late String _text = widget.controller.text;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onText);
  }

  @override
  void didUpdateWidget(_TranscriptFindBar old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller.removeListener(_onText);
      widget.controller.addListener(_onText);
      _text = widget.controller.text;
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onText);
    super.dispose();
  }

  /// Every edit the person makes goes to the screen at once (the screen
  /// settles the search itself). A selection change is not an edit, and a
  /// query the screen set itself (from the timeline) is already searched.
  void _onText() {
    final text = widget.controller.text;
    if (text == _text) return;
    setState(() => _text = text);
    if (widget.focusNode.hasFocus) widget.onChanged(text);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final count = widget.count;
    final error = widget.error;
    final searched = _text.trim().isNotEmpty;
    final older = KitAction(
      key: const ValueKey('transcript-find-older'),
      label: widget.needsReload ? l10n.historyReload : l10n.transcriptFindAll,
      working: widget.loading,
      onPressed: widget.loading ? () {} : widget.onLoadOlder,
    );
    return CallbackShortcuts(
      key: const ValueKey('transcript-find-bar'),
      // Esc clears a query first (the field's own); with none it closes.
      bindings: {
        const SingleActivator(LogicalKeyboardKey.escape): widget.onClose,
      },
      child: ListView(
        shrinkWrap: true,
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space2,
          tokens.space1,
          tokens.space2,
        ),
        children: [
          Row(
            children: [
              Expanded(
                child: KitSearchField(
                  label: l10n.transcriptFindHint,
                  controller: widget.controller,
                  focusNode: widget.focusNode,
                  // Clear (its button) may run before the field has focus.
                  onChanged: (query) {
                    if (query.isEmpty) widget.onChanged(query);
                  },
                  onSubmitted: (_) => widget.onNext(),
                  fieldKey: const ValueKey('transcript-find-input'),
                ),
              ),
              KitIconButton(
                icon: AppIconography.close,
                tooltip: l10n.transcriptFindClose,
                onPressed: widget.onClose,
              ),
            ],
          ),
          if (searched)
            Padding(
              padding: EdgeInsetsDirectional.only(end: tokens.space1),
              child: Row(
                children: [
                  Expanded(
                    child: Semantics(
                      liveRegion: true,
                      child: KitText(
                        count == 0
                            ? l10n.transcriptFindNone
                            : l10n.transcriptFindCount(
                                widget.current + 1,
                                count,
                              ),
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
                        tabular: true,
                      ),
                    ),
                  ),
                  KitIconButton(
                    key: const ValueKey('transcript-find-previous'),
                    icon: AppIconography.chevronUp,
                    tooltip: l10n.transcriptFindPrevious,
                    onPressed: count == 0 ? null : widget.onPrevious,
                  ),
                  KitIconButton(
                    key: const ValueKey('transcript-find-next'),
                    icon: AppIconography.chevronDown,
                    tooltip: l10n.transcriptFindNext,
                    onPressed: count == 0 ? null : widget.onNext,
                  ),
                ],
              ),
            ),
          if (error != null)
            KitNotice.error(
              key: const ValueKey('transcript-find-older-error'),
              message: productErrorText(error, l10n: l10n),
              error: error,
              retry: older,
            )
          else if (widget.searchingAll)
            KitNotice(
              key: const ValueKey('transcript-find-searching-all'),
              icon: AppIconography.history,
              tone: AppStatusTone.progress,
              message: l10n.transcriptFindPartial,
              actions: [
                KitAction(
                  key: const ValueKey('transcript-find-cancel-all'),
                  label: l10n.transcriptFindStopSearchingAll,
                  onPressed: widget.onCancelLoading,
                ),
              ],
            )
          else if (widget.hasOlder)
            KitNotice(
              key: const ValueKey('transcript-find-partial'),
              icon: AppIconography.history,
              message: l10n.transcriptFindPartial,
              actions: [older],
            ),
        ],
      ),
    );
  }
}

mixin _ChatFindFields {
  final _findController = TextEditingController();
  final _findFocus = FocusNode();
  final _findNavigationFocus = FocusNode(skipTraversal: true);
  BuildContext? _findExcerptContext;

  /// Searches tool calls by their words, as the transcript shows them; the
  /// host sets [_findToolL10n] each build.
  late final _findIndex = TranscriptSearchIndex(
    toolLabel: (part) => toolLabel(
      part.toolName ?? '',
      state: part.toolState,
      l10n: _findToolL10n,
    ),
  );
  AppLocalizations? _findToolL10n;
  Timer? _findDebounce;
  bool _findOpen = false;
  String _findQuery = '';
  List<TranscriptMatch> _findHits = [];
  int _findCursor = 0;
  String? _findKey;
  int? _findLocation;
  bool _findAllLoading = false;
}

extension _ChatFind on _ChatScreenState {
  void _syncFind() {
    _findToolL10n = _chatL10n(context);
    _findHits = _findOpen ? _findIndex.search(_visibleHistory, _findQuery) : [];
    final retained = _findHits.indexWhere((match) => match.key == _findKey);
    _findCursor = retained >= 0
        ? retained
        : _findCursor.clamp(0, math.max(0, _findHits.length - 1));
    _findKey = _findHits.isEmpty ? null : _findHits[_findCursor].key;
  }

  void _openFind({String? query, String? messageID}) {
    _setChatState(() {
      _findOpen = true;
      _findLocation = _conn.locationRevision;
      if (query != null) {
        _findController.text = query;
        _findQuery = query.trim();
        _findKey = null;
        _findCursor = 0;
      }
      _syncFind();
      if (messageID != null) {
        final index = _findHits.indexWhere(
          (match) => match.messageID == messageID,
        );
        if (index >= 0) {
          _findCursor = index;
          _findKey = _findHits[index].key;
        }
      }
    });
    if (messageID != null) {
      _jumpToMessage(messageID, alignment: .85);
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_findOpen) return;
        _findFocus.requestFocus();
        _findController.selection = TextSelection(
          baseOffset: 0,
          extentOffset: _findController.text.length,
        );
      });
    }
  }

  void _changeFind(String query) {
    _findDebounce?.cancel();
    _findDebounce = Timer(const Duration(milliseconds: 220), () {
      if (!mounted || !_findOpen) return;
      _setChatState(() {
        _findQuery = query.trim();
        _findKey = null;
        _findCursor = 0;
        _syncFind();
      });
      if (_findHits.isNotEmpty) {
        _jumpToMessage(_findHits.first.messageID, alignment: .85);
      }
    });
  }

  void _navigateFind(int step) {
    _findDebounce?.cancel();
    if (_findQuery != _findController.text.trim()) {
      _setChatState(() {
        _findQuery = _findController.text.trim();
        _findCursor = 0;
        _findKey = null;
        _syncFind();
      });
      step = 0;
    }
    if (_findHits.isEmpty) return;
    _findFocus.unfocus();
    _findNavigationFocus.requestFocus();
    _findExcerptContext = null;
    _setChatState(() {
      _findCursor = (_findCursor + step) % _findHits.length;
      _findKey = _findHits[_findCursor].key;
    });
    _jumpToMessage(_findHits[_findCursor].messageID, alignment: .85);
  }

  void _closeFind() {
    _findAllLoading = false;
    _findDebounce?.cancel();
    _findFocus.unfocus();
    _findNavigationFocus.unfocus();
    _findExcerptContext = null;
    _setChatState(() {
      _findOpen = false;
      _findQuery = '';
      _findController.clear();
      _findHits = [];
      _findKey = null;
    });
  }

  Future<void> _searchAllHistory() async {
    if (_findAllLoading || _loading || _loadingOlder) return;
    _findNavigationFocus.requestFocus();
    final location = _conn.locationRevision;
    _setChatState(() => _findAllLoading = true);
    try {
      while (mounted &&
          _findOpen &&
          _findAllLoading &&
          location == _conn.locationRevision &&
          _olderCursor != null) {
        final before = _olderCursor;
        await _loadOlder();
        if (_olderError != null || _olderCursor == before) break;
      }
    } finally {
      if (mounted) _setChatState(() => _findAllLoading = false);
    }
  }
}
