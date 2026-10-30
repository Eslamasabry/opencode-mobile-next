part of '../chat_screen.dart';

// "Continue with…" links: the quiet line that says where this work went or
// came from, and the way out of a provider failure
// (docs/design/agent-handoff-contract.md, "Links and deletion").

mixin _ChatHandoffLinkFields {
  /// The links of this conversation, read once on open and again after the
  /// "Continue with…" sheet closes. Navigation metadata only.
  List<ContinueWithLinkLine> _handoffLinkLines = const [];

  /// Links whose other conversation turned out to be gone this visit.
  final Set<String> _goneHandoffLinks = {};
}

extension _ChatHandoffLinks on _ChatScreenState {
  /// The durable identity the links are stored under: the saved parent
  /// profile, the runtime and the runtime's own session id (not a draft
  /// alias the live connection may use).
  ({String profileId, HandoffOrigin origin, String sessionId})?
  get _handoffIdentity {
    if (_conn.isIsolated || _watching) return null;
    final profileId = _conn.agentHandoffHub.profile?.id;
    if (profileId == null) return null;
    final api = _conn.api;
    return (
      profileId: profileId,
      origin: _conn.isAgentBackend
          ? HandoffOrigin.phoneAgent
          : HandoffOrigin.server,
      sessionId: api is AgentHandoffTargetGateway
          ? (api as AgentHandoffTargetGateway).handoffSessionId(
              widget.sessionID,
            )
          : widget.sessionID,
    );
  }

  void _loadHandoffLinks({bool notify = false}) {
    final identity = _handoffIdentity;
    final lines = identity == null
        ? const <ContinueWithLinkLine>[]
        : continueWithLinkLines(
            AgentHandoffLinks.forPreferences(_conn.store.prefs),
            profileId: identity.profileId,
            sessionId: identity.sessionId,
            origin: identity.origin,
          );
    if (notify) {
      if (mounted) _setChatState(() => _handoffLinkLines = lines);
    } else {
      _handoffLinkLines = lines;
    }
  }

  /// The first link, folded into the bar's line under the title ("IPTV_King
  /// · Claude Code · from Design review"): it opens the other conversation
  /// and takes no row of its own (owner, 2026-10-27). Null when there is
  /// none.
  KitTopBarOrigin? _handoffOrigin() {
    if (_handoffLinkLines.isEmpty) return null;
    final line = _handoffLinkLines.first;
    final strings = _chatL10n(context);
    final gone = _goneHandoffLinks.contains(line.link.id);
    return KitTopBarOrigin(
      originKey: const ValueKey('continued-line'),
      label: gone
          ? strings.continuedGoneShort
          : line.outgoing
          ? strings.continuedInShort(KitBidi.auto(line.agent))
          : strings.continuedFromShort(KitBidi.auto(line.title)),
      semanticsLabel: gone
          ? strings.continuedGone
          : line.outgoing
          ? strings.continuedIn(KitBidi.auto(line.agent))
          : strings.continuedFrom(KitBidi.auto(line.title)),
      gone: gone,
      onTap: () => unawaited(_openHandoffLink(line)),
    );
  }

  /// Opens the other conversation of a link, on the saved server and runtime
  /// the link names. One on another saved server is opened from that
  /// server's own chats. A conversation that no longer opens says so.
  Future<void> _openHandoffLink(ContinueWithLinkLine line) async {
    final link = line.link;
    final profileId = line.outgoing
        ? link.targetProfileId
        : link.sourceProfileId;
    final origin = line.outgoing ? link.targetOrigin : link.sourceOrigin;
    final durable = line.outgoing ? link.targetSessionId : link.sourceSessionId;
    if (profileId != _conn.agentHandoffHub.profile?.id) {
      _showComposerNote(_chatL10n(context).continuedOpenFromList);
      return;
    }
    try {
      var id = durable;
      if (origin == HandoffOrigin.phoneAgent) {
        final gateway = await _conn.agentHandoffTargetGateway(
          link.projectDirectory,
        );
        if (gateway is AgentHandoffTargetGateway) {
          id = (gateway as AgentHandoffTargetGateway)
              .handoffNavigationSessionId(durable);
        }
        final agentId = line.outgoing
            ? AgentCatalog.builtIn.agents
                  .where((agent) => agent.name == link.targetAgent)
                  .firstOrNull
                  ?.id
            : null;
        // The way the Conversations list opens it (its row), so the line
        // and the row land on the same chat.
        id = await _conn.agentHandoffAdopt(
          directory: link.projectDirectory,
          agentId: agentId,
          sessionId: id,
        );
      }
      if (!mounted) return;
      await Navigator.of(context).pushNamed('/chat/$id');
    } catch (_) {
      if (mounted) _setChatState(() => _goneHandoffLinks.add(link.id));
    }
  }

  /// "Continue with…" from the menu, and the quiet "Continue with another
  /// agent" after a provider failure: the same sheet, opened only by the
  /// person.
  Future<void> _continueWithAnotherAgent() async {
    if (!mounted || _conn.isIsolated) return;
    await showContinueWith(
      context,
      connection: _conn,
      sessionId: widget.sessionID,
    );
    if (mounted) _loadHandoffLinks(notify: true);
  }
}
