import '../domain/command_receipts.dart';
import '../domain/server_gateway.dart';
import '../state/offline_queue.dart';
import '../api2/gateway.dart';
import '../api2/models.dart';
import '../api2/transport.dart';
import 'opencode_api.dart';

/// Keeps protocol branching out of controller/UI. No new service or transport.
class CommandReceiptTransport {
  const CommandReceiptTransport(this.gateway);
  final ServerGateway gateway;

  bool matchesEndpoint(String endpoint) {
    final current = gateway;
    if (current is Api2Gateway) {
      return Uri.tryParse(Api2Transport.normalizeServerRoot(endpoint)) ==
          Uri.tryParse(current.transport.serverRoot);
    }
    if (current is OpenCodeApi) {
      return Uri.tryParse(endpoint.replaceFirst(RegExp(r'/+$'), '')) ==
          Uri.tryParse(current.baseUrl.replaceFirst(RegExp(r'/+$'), ''));
    }
    return false;
  }

  bool get supportsSessionCreation =>
      gateway.capabilities.commandReceipts && gateway is Api2Gateway;

  Future<void> createSession(String sessionID) async {
    if (!supportsSessionCreation || gateway.isClosed) {
      throw const CommandReceiptException();
    }
    final session = await (gateway as Api2Gateway).client.createSession(
      id: sessionID,
    );
    if (session.id != sessionID) throw const CommandReceiptException();
  }

  Future<bool> lookupSession(String sessionID) async {
    if (!supportsSessionCreation || gateway.isClosed) return false;
    final session = await (gateway as Api2Gateway).client.session(sessionID);
    return session.id == sessionID;
  }

  bool get supported =>
      gateway.capabilities.commandReceipts &&
      (gateway is CorrelatedPromptGateway || gateway is Api2Gateway);

  Future<void> dispatch(
    QueuedPrompt prompt,
    String receiptID, {
    void Function()? beforeSend,
  }) async {
    final current = gateway;
    final location = (current.directory, current.workspace);
    void checkAdmission() {
      if (current.isClosed ||
          location != (current.directory, current.workspace)) {
        throw const CommandReceiptException();
      }
      beforeSend?.call();
    }

    if (!supported) throw const CommandReceiptException();
    if (current is Api2Gateway) {
      // Same preflight as the normal v2 gateway; never implicitly commit revert.
      if ((await current.client.session(prompt.sessionID)).reverted) {
        throw const CommandReceiptException();
      }
      checkAdmission();
      final receipt = await current.client.prompt(
        prompt.sessionID,
        id: receiptID,
        text: prompt.text,
        files: [
          for (final attachment in prompt.attachments)
            Api2PromptFile(uri: attachment.url, name: attachment.filename),
        ],
        agents: [
          for (final mention in prompt.mentions)
            Api2PromptAgentMention(
              name: mention.name,
              mention: Api2Mention(
                start: mention.start,
                end: mention.end,
                text: mention.value,
              ),
            ),
        ],
      );
      if (receipt.id != receiptID ||
          receipt.sessionID != prompt.sessionID ||
          receipt.type != 'prompt') {
        throw const CommandReceiptException();
      }
      return;
    }
    await (current as CorrelatedPromptGateway).promptWithMessageID(
      prompt.sessionID,
      messageID: receiptID,
      text: prompt.text,
      model: prompt.model,
      agent: prompt.agent?.isNotEmpty == true ? prompt.agent : null,
      variant: prompt.variant?.isNotEmpty == true ? prompt.variant : null,
      attachments: prompt.attachments,
      agentMentions: prompt.mentions,
      beforeSend: checkAdmission,
    );
  }

  Future<bool> lookup(CommandReceipt receipt) async {
    if (!supported || gateway.isClosed) return false;
    final current = gateway;
    if (current is Api2Gateway) {
      // Pending inbox work and promoted user messages are disjoint views.
      final inbox = await current.client.inbox(receipt.sessionID);
      if (inbox.any(
        (item) =>
            item.id == receipt.receiptID &&
            item.sessionID == receipt.sessionID &&
            item.type == 'prompt',
      )) {
        return true;
      }
      final message = await current.client.message(
        receipt.sessionID,
        receipt.receiptID,
      );
      return message.id == receipt.receiptID && message is Api2UserMessage;
    }
    // V1 domain's complete paged read also works for injected gateways. Never
    // infer acceptance from equal text, assistant progress, or an empty page.
    String? cursor;
    final seen = <String>{};
    for (var pageNumber = 0; pageNumber < 100; pageNumber++) {
      final page = await current.messagePage(receipt.sessionID, cursor: cursor);
      if (page.items.any(
        (message) =>
            message.info.id == receipt.receiptID &&
            message.info.sessionID == receipt.sessionID &&
            message.info.role == 'user',
      )) {
        return true;
      }
      cursor = page.nextCursor;
      if (cursor == null || !seen.add(cursor)) return false;
    }
    return false;
  }
}
