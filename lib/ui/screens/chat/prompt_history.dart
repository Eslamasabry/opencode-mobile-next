part of '../chat_screen.dart';

/// The body of "Reuse a prompt": text from this conversation and recent
/// sends, newest first. Tapping a row adds its text to the draft; nothing is
/// resent and no attachment is copied. The host opens it with
/// [showKitSheet] (which draws the title and the close button) and appends
/// the chosen text. Not its own scroll view: the sheet frame scrolls it.
class _PromptHistorySheet extends StatefulWidget {
  const _PromptHistorySheet({required this.prompts});
  final List<String> prompts;

  @override
  State<_PromptHistorySheet> createState() => _PromptHistorySheetState();
}

class _PromptHistorySheetState extends State<_PromptHistorySheet> {
  final _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _clear() {
    _search.clear();
    setState(() => _query = '');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final query = _query.trim().toLowerCase();
    final matches = [
      for (final text in widget.prompts)
        if (text.toLowerCase().contains(query)) text,
    ];
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSearchField(
          controller: _search,
          label: l10n.composerReuseSearch,
          fieldKey: const Key('prompt-history-search'),
          resultCount: query.isEmpty ? null : matches.length,
          onChanged: (value) => setState(() => _query = value),
        ),
        if (matches.isEmpty && query.isNotEmpty)
          KitSearchNoMatch(query: _query.trim(), onClear: _clear)
        else if (matches.isEmpty)
          KitStateView(
            size: KitStateSize.inline,
            icon: AppIconography.history,
            title: l10n.composerReuseEmpty,
          )
        else
          KitRowGroup(
            leadingIcons: false,
            children: [
              for (var index = 0; index < matches.length; index++)
                KitRow(
                  key: ValueKey('reuse-prompt-$index'),
                  title: matches[index],
                  titleMaxLines: 3,
                  trailing: const KitRowIcon(AppIconography.add),
                  onTap: () => Navigator.pop(context, matches[index]),
                ),
            ],
          ),
      ],
    );
  }
}

mixin _ChatPromptHistoryFields {
  final _promptHistory = PromptHistoryNavigation();
}

extension _ChatPromptHistory on _ChatScreenState {
  List<String> get _recentPrompts => {
    for (final message in _visibleHistory.toList().reversed)
      if (message.info.role == 'user' && !message.info.id.startsWith('local-'))
        if (_ChatScreenState._messageText(message).trim() case final text
            when text.isNotEmpty)
          text,
    ..._savedPromptHistory,
  }.take(50).toList();

  List<String> get _savedPromptHistory {
    try {
      return _conn.sentPromptHistory;
    } catch (_) {
      return const [];
    }
  }

  Future<void> _reusePrompt() async {
    final l10n = _chatL10n(context);
    final text = await showKitSheet<String>(
      context,
      sheetKey: const Key('prompt-history-sheet'),
      title: l10n.composerReuseTitle,
      subtitle: l10n.promptHistoryIntro,
      icon: AppIconography.history,
      body: (_) => _PromptHistorySheet(prompts: _recentPrompts),
    );
    if (!mounted || text == null) return;
    final draft = _composer.text.trimRight();
    final next = draft.isEmpty ? text : '$draft\n\n$text';
    _composer.value = TextEditingValue(
      text: next,
      selection: TextSelection.collapsed(offset: next.length),
    );
    _focus.requestFocus();
  }

  KeyEventResult _navigatePromptHistory(KeyEvent event) {
    if (_conn.isIsolated) return KeyEventResult.ignored;
    final value = _composer.value;
    if (_sending ||
        _promptShelfBusy ||
        !isPromptHistoryKey(
          event,
          value,
          suggestionsOpen:
              value.text.trimLeft().startsWith('/') ||
              _activeAgentQuery(value) != null,
        )) {
      return KeyEventResult.ignored;
    }
    final next = _promptHistory.move(
      event.logicalKey == LogicalKeyboardKey.arrowUp,
      value,
      _recentPrompts,
    );
    if (next == null) return KeyEventResult.ignored;
    _setChatState(() => _composer.value = next);
    return KeyEventResult.handled;
  }

  void _restoreHistoryDraft() {
    final original = _promptHistory.restore();
    if (original == null || !mounted) return;
    _setChatState(() => _composer.value = original);
    _persistDraft();
    _focus.requestFocus();
  }

  Future<void> _rememberSentPrompt(String profile, String text) async {
    if (_conn.isIsolated) return;
    try {
      await _conn.rememberSentPrompt(profile, text);
    } catch (_) {
      if (mounted && _conn.promptShelfProfileID == profile) {
        _showComposerNote(_chatL10n(context).promptHistorySaveFailed);
      }
    }
  }
}
