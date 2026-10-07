part of '../chat_screen.dart';

// Agent cards in the conversation (docs/design/genui-plan-2026-10-07.md): a
// completed `oc-ui` tool call is drawn as a native card in place of its tool
// row. The domain decides what is a card, its state and its answer; this file
// only hands the transcript's parts to it and puts the card's kit view in the
// reply. Nothing here builds a prompt.

extension _ChatAgentCards on _ChatScreenState {
  /// The connection's card surface, or null until it implements one.
  GenUiController? get _genUi {
    final conn = _conn;
    return conn is GenUiController ? conn as GenUiController : null;
  }

  /// Who the conversation is with, as the composer and the cards say it.
  String? get _agentName => _conn.isAgentBackend
      ? _conn.profile?.name
      : switch (_conn.profile?.backend) {
          ServerBackend.paseo => 'Claude Code',
          ServerBackend.codex => 'Codex',
          _ => null,
        };

  /// A card of this conversation waits for the person: the composer says the
  /// answer can also be typed.
  bool get _cardWaits =>
      _genUi?.waitingCardsForSession(widget.sessionID).isNotEmpty ?? false;

  AgentCardPhotos? get _agentCardPhotos =>
      !_conn.isIsolated &&
          _supportsPromptAttachments &&
          platformCapabilities.supportsPromptPhotos
      ? StoreAgentCardPhotos(
          store: _conn.promptPhotos,
          profileID: _draftProfileID,
          sessionID: widget.sessionID,
          directory: _draftDirectory,
          workspace: _draftWorkspace,
        )
      : null;

  /// The card for a tool part, or null when the part is an ordinary tool
  /// call (or the connection draws no cards).
  GenUiParse? _cardForPart(Part part, String messageID) {
    final gen = _genUi;
    if (gen == null || part.type != 'tool') return null;
    return gen.genUiCardForPart(widget.sessionID, messageID, part);
  }

  Widget _agentCardRow(GenUiParse parse) {
    final gen = _genUi!;
    return ListenableBuilder(
      listenable: _conn,
      builder: (context, _) => AgentCardView(
        key: ValueKey(
          parse is GenUiParsed
              ? 'agent-card-view-${parse.card.callID}'
              : 'agent-card-view-unreadable',
        ),
        controller: gen,
        parse: parse,
        agentLabel: _agentName ?? 'OpenCode',
        busy:
            _conn.busySessions.contains(widget.sessionID) ||
            _localTurnSince != null,
        photos: _agentCardPhotos,
      ),
    );
  }
}
