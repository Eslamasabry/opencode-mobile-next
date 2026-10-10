part of '../chat_screen.dart';

// Agent cards in the conversation (docs/design/genui-plan-2026-10-07.md): a
// completed `oc-ui` tool call is drawn as a native card in place of its tool
// row. The domain decides what is a card, its state and its answer; this file
// only hands the transcript's parts to it and puts the card's kit view in the
// reply. Nothing here builds a prompt.

extension _ChatAgentCards on _ChatScreenState {
  /// The connection's card surface.
  GenUiController? get _genUi => _conn;

  /// Who the conversation is with, as the composer and the cards say it.
  String? get _agentName {
    // The conversation's own agent (Claude Code or Pi) where the server
    // says which it is; a server's name for itself is not the agent's.
    final api = _conn.api;
    if (api is AgentFeatureGateway &&
        (api as AgentFeatureGateway).agentFeaturesSupported) {
      final owner = (api as AgentFeatureGateway).agentFeaturesOwner(
        widget.sessionID,
      );
      if (owner != null) return owner;
    }
    return _conn.isAgentBackend
        ? _conn.profile?.name
        : switch (_conn.profile?.backend) {
            ServerBackend.paseo => 'Claude Code',
            ServerBackend.codex => 'Codex',
            _ => null,
          };
  }

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
        connectors: ConnectionConnectorCardHost(_conn),
        onOpenConnectors: () => unawaited(
          pushKitPage<void>(
            context,
            (_) => McpCatalogScreen(controller: _conn),
          ),
        ),
      ),
    );
  }
}

extension _MessageViewAgentCards on _MessageView {
  /// Takes every tool call the domain reads as an agent card out of its run
  /// and makes it a run of its own, so the card stands in the reply (never
  /// inside a folded work line) and the calls around it keep their grouping.
  List<_AssistantPartRun> _withCards(
    List<_AssistantPartRun> runs,
    _ChatScreenState? chat,
  ) {
    if (chat == null || chat._genUi == null) return runs;
    final result = <_AssistantPartRun>[];
    for (final run in runs) {
      if (run.parts.first.type != 'tool') {
        result.add(run);
        continue;
      }
      var plain = <Part>[];
      var first = true;
      void flush() {
        if (plain.isEmpty) return;
        result.add(
          _AssistantPartRun(
            plain,
            grouped: plain.length > 1,
            heading: first ? run.heading : null,
            note: first ? run.note : null,
          ),
        );
        first = false;
        plain = <Part>[];
      }

      for (final part in run.parts) {
        final card = chat._cardForPart(part, part.messageID ?? m.info.id);
        if (card == null) {
          plain.add(part);
          continue;
        }
        flush();
        result.add(_AssistantPartRun([part], card: card));
        first = false;
      }
      flush();
    }
    return result;
  }
}
