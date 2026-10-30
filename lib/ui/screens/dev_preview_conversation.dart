import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/server_gateway.dart';
import '../../state/connection.dart';
import 'chat_screen.dart' show ChatScreen;

/// What a request sent from the preview does to the conversation's agent,
/// the same rule the chat's composer follows: where steering works it joins
/// the running turn; an agent that restarts from a new message says so; an
/// OpenCode 1 server runs it after the reply; an idle agent starts at once.
enum DevPreviewDelivery { now, joinsTurn, afterReply, restartsTurn }

/// The conversation an in-app preview was opened from: where "Ask for a
/// change…" sends. A preview opened anywhere else (Development services,
/// the phone's ports) has none, and so no field.
class DevPreviewConversation {
  const DevPreviewConversation({required this.delivery, required this.send});

  /// What sending now would do; read just before each send.
  final DevPreviewDelivery Function() delivery;

  /// Sends the words into the conversation; throws when they did not go.
  final Future<void> Function(String text) send;

  /// The conversation behind [context] when it is a conversation screen's
  /// own context (the chat opens the preview with it), and that
  /// conversation can take a message from here: not watched read-only, not
  /// an isolated copy.
  static DevPreviewConversation? of(BuildContext context) {
    final screen = context.widget;
    if (screen is! ChatScreen || screen.watch != null) return null;
    final ConnectionController conn;
    try {
      conn = ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider);
    } catch (_) {
      return null;
    }
    if (conn.isIsolated) return null;
    return through(conn, screen.sessionID);
  }

  /// Sends through [conn]'s gateway with the conversation's own model,
  /// agent and variant, as its composer does.
  static DevPreviewConversation through(
    ConnectionController conn,
    String sessionID,
  ) {
    DevPreviewDelivery delivery() {
      if (!conn.busySessions.contains(sessionID)) return DevPreviewDelivery.now;
      final api = conn.api;
      final midTurn = api is MidTurnPromptGateway
          ? (api as MidTurnPromptGateway).midTurnPrompt(sessionID)
          : null;
      return switch (midTurn) {
        MidTurnPrompt.joinsTurn => DevPreviewDelivery.joinsTurn,
        MidTurnPrompt.restartsTurn => DevPreviewDelivery.restartsTurn,
        null =>
          conn.capabilities.promptSteering
              ? DevPreviewDelivery.joinsTurn
              : DevPreviewDelivery.afterReply,
      };
    }

    Future<void> send(String text) async {
      final api = await conn.prepareActionTransport();
      if (api == null) throw StateError('not connected');
      await conn.waitForSessionSelection(sessionID, expectedApi: api);
      await conn.ensureInteractiveSendAllowed(expectedApi: api);
      final agent = conn.agentForSession(sessionID);
      final variant = conn.variantForSession(sessionID);
      await api.promptAsync(
        sessionID,
        text: text,
        model: conn.modelForSession(sessionID),
        agent: agent.isEmpty ? null : agent,
        variant: variant.isEmpty ? null : variant,
        delivery:
            conn.capabilities.promptSteering &&
                conn.busySessions.contains(sessionID)
            ? PromptDelivery.steer
            : null,
      );
    }

    return DevPreviewConversation(delivery: delivery, send: send);
  }
}
