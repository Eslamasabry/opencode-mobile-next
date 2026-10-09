import 'dart:convert';

import 'gen_ui_asks.dart';
import 'gen_ui_nodes.dart';

final class GenUiScope {
  const GenUiScope({
    required this.profileID,
    required this.sourceId,
    required this.directory,
    this.workspace,
  });
  final String profileID;
  final String sourceId;
  final String directory;
  final String? workspace;

  @override
  bool operator ==(Object other) =>
      other is GenUiScope &&
      profileID == other.profileID &&
      sourceId == other.sourceId &&
      directory == other.directory &&
      workspace == other.workspace;
  @override
  int get hashCode => Object.hash(profileID, sourceId, directory, workspace);
}

/// A display-only catalog suggestion, never a connection or command request.
final class GenUiConnectorSuggestion {
  const GenUiConnectorSuggestion({
    required this.catalogId,
    required this.reason,
  });

  final String catalogId;
  final String reason;
}

final class GenUiCard {
  GenUiCard({
    required this.scope,
    required this.id,
    required this.title,
    required this.sessionID,
    required this.callID,
    required this.messageID,
    required this.revision,
    required List<GenUiNode> body,
    this.ask,
    this.connector,
  }) : body = List.unmodifiable(body);
  final GenUiScope scope;
  final String id;
  final String title;
  final String sessionID;
  final String callID;
  final String messageID;
  final String revision;
  final List<GenUiNode> body;
  final GenUiAsk? ask;
  final GenUiConnectorSuggestion? connector;

  /// Transport identity; revisions are compared separately before dispatch.
  String get identity => jsonEncode([
    scope.profileID,
    scope.sourceId,
    scope.directory,
    scope.workspace,
    sessionID,
    messageID,
    callID,
  ]);
}

sealed class GenUiParse {
  const GenUiParse();
}

final class GenUiParsed extends GenUiParse {
  const GenUiParsed(this.card);
  final GenUiCard card;
}

final class GenUiUnreadable extends GenUiParse {
  const GenUiUnreadable({required this.reason});
  final GenUiProblem reason;
}

enum GenUiProblem {
  notACard,
  version,
  tooLarge,
  unknownKey,
  badValue,
  secretField,
  unsupportedAsk,
}

enum GenUiCardState { waiting, answered, passedOver, report, unknown }

enum GenUiDeliveryState { idle, held, sending, deliveryUnknown, failed }
