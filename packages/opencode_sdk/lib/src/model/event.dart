//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

import 'dart:convert';

import 'package:opencode_sdk/src/http/wire.dart';

/// Lossless JSON representation of an OpenAPI union.
///
/// dart-dio flattens anyOf/oneOf branches into an aggregate model, which can
/// reject valid branches or discard branch-specific fields. This wrapper keeps
/// the wire value intact while exposing the exact normalized schema descriptor.
class Event implements OpenCodeRawJsonValue {
  Event(Object? value) : value = _copyJsonValue(value);

  factory Event.fromJson(Object? json) => Event(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/EventModels-devRefreshed\"},{\"\$ref\":\"#/components/schemas/EventIntegrationUpdated\"},{\"\$ref\":\"#/components/schemas/EventIntegrationConnectionUpdated\"},{\"\$ref\":\"#/components/schemas/EventCatalogUpdated\"},{\"\$ref\":\"#/components/schemas/EventSessionCreated\"},{\"\$ref\":\"#/components/schemas/EventSessionUpdated\"},{\"\$ref\":\"#/components/schemas/EventSessionDeleted\"},{\"\$ref\":\"#/components/schemas/EventMessageUpdated\"},{\"\$ref\":\"#/components/schemas/EventMessageRemoved\"},{\"\$ref\":\"#/components/schemas/EventMessagePartUpdated\"},{\"\$ref\":\"#/components/schemas/EventMessagePartRemoved\"},{\"\$ref\":\"#/components/schemas/EventSessionNextAgentSwitched\"},{\"\$ref\":\"#/components/schemas/EventSessionNextModelSwitched\"},{\"\$ref\":\"#/components/schemas/EventSessionNextMoved\"},{\"\$ref\":\"#/components/schemas/EventSessionNextPrompted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextPromptAdmitted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextContextUpdated\"},{\"\$ref\":\"#/components/schemas/EventSessionNextSynthetic\"},{\"\$ref\":\"#/components/schemas/EventSessionNextShellStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextShellEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextStepStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextStepEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextStepFailed\"},{\"\$ref\":\"#/components/schemas/EventSessionNextTextStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextTextDelta\"},{\"\$ref\":\"#/components/schemas/EventSessionNextTextEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextReasoningStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextReasoningDelta\"},{\"\$ref\":\"#/components/schemas/EventSessionNextReasoningEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolInputStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolInputDelta\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolInputEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolCalled\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolProgress\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolSuccess\"},{\"\$ref\":\"#/components/schemas/EventSessionNextToolFailed\"},{\"\$ref\":\"#/components/schemas/EventSessionNextRetried\"},{\"\$ref\":\"#/components/schemas/EventSessionNextCompactionStarted\"},{\"\$ref\":\"#/components/schemas/EventSessionNextCompactionDelta\"},{\"\$ref\":\"#/components/schemas/EventSessionNextCompactionEnded\"},{\"\$ref\":\"#/components/schemas/EventSessionNextRevertStaged\"},{\"\$ref\":\"#/components/schemas/EventSessionNextRevertCleared\"},{\"\$ref\":\"#/components/schemas/EventSessionNextRevertCommitted\"},{\"\$ref\":\"#/components/schemas/EventMessagePartDelta\"},{\"\$ref\":\"#/components/schemas/EventSessionDiff\"},{\"\$ref\":\"#/components/schemas/EventSessionError\"},{\"\$ref\":\"#/components/schemas/EventInstallationUpdated\"},{\"\$ref\":\"#/components/schemas/EventInstallationUpdate-available\"},{\"\$ref\":\"#/components/schemas/EventFileEdited\"},{\"\$ref\":\"#/components/schemas/EventReferenceUpdated\"},{\"\$ref\":\"#/components/schemas/EventPermissionV2Asked\"},{\"\$ref\":\"#/components/schemas/EventPermissionV2Replied\"},{\"\$ref\":\"#/components/schemas/EventPluginAdded\"},{\"\$ref\":\"#/components/schemas/EventProjectDirectoriesUpdated\"},{\"\$ref\":\"#/components/schemas/EventFileWatcherUpdated\"},{\"\$ref\":\"#/components/schemas/EventPtyCreated\"},{\"\$ref\":\"#/components/schemas/EventPtyUpdated\"},{\"\$ref\":\"#/components/schemas/EventPtyExited\"},{\"\$ref\":\"#/components/schemas/EventPtyDeleted\"},{\"\$ref\":\"#/components/schemas/EventQuestionV2Asked\"},{\"\$ref\":\"#/components/schemas/EventQuestionV2Replied\"},{\"\$ref\":\"#/components/schemas/EventQuestionV2Rejected\"},{\"\$ref\":\"#/components/schemas/EventTodoUpdated\"},{\"\$ref\":\"#/components/schemas/EventLspUpdated\"},{\"\$ref\":\"#/components/schemas/EventPermissionAsked\"},{\"\$ref\":\"#/components/schemas/EventPermissionReplied\"},{\"\$ref\":\"#/components/schemas/EventTuiPromptAppendSchema2\"},{\"\$ref\":\"#/components/schemas/EventTuiCommandExecuteSchema2\"},{\"\$ref\":\"#/components/schemas/EventTuiToastShowSchema2\"},{\"\$ref\":\"#/components/schemas/EventTuiSessionSelectSchema2\"},{\"\$ref\":\"#/components/schemas/EventMcpToolsChanged\"},{\"\$ref\":\"#/components/schemas/EventMcpBrowserOpenFailed\"},{\"\$ref\":\"#/components/schemas/EventCommandExecuted\"},{\"\$ref\":\"#/components/schemas/EventProjectUpdated\"},{\"\$ref\":\"#/components/schemas/EventSessionStatus\"},{\"\$ref\":\"#/components/schemas/EventSessionIdle\"},{\"\$ref\":\"#/components/schemas/EventQuestionAsked\"},{\"\$ref\":\"#/components/schemas/EventQuestionReplied\"},{\"\$ref\":\"#/components/schemas/EventQuestionRejected\"},{\"\$ref\":\"#/components/schemas/EventSessionCompacted\"},{\"\$ref\":\"#/components/schemas/EventVcsBranchUpdated\"},{\"\$ref\":\"#/components/schemas/EventWorkspaceReady\"},{\"\$ref\":\"#/components/schemas/EventWorkspaceFailed\"},{\"\$ref\":\"#/components/schemas/EventWorkspaceStatus\"},{\"\$ref\":\"#/components/schemas/EventWorktreeReady\"},{\"\$ref\":\"#/components/schemas/EventWorktreeFailed\"},{\"\$ref\":\"#/components/schemas/EventServerConnected\"},{\"\$ref\":\"#/components/schemas/EventGlobalDisposed\"},{\"\$ref\":\"#/components/schemas/EventServerInstanceDisposed\"}]}";

  @override
  final Object? value;

  Object? toJson() => _copyJsonValue(value);

  Map<String, dynamic>? get objectValue =>
      value is Map<String, dynamic> ? value as Map<String, dynamic> : null;

  @override
  String toString() => jsonEncode(value);
}

Object? _copyJsonValue(Object? value) {
  if (value == null || value is String || value is num || value is bool) {
    return value;
  }
  if (value is List) {
    return value.map(_copyJsonValue).toList(growable: false);
  }
  if (value is Map) {
    return value.map<String, dynamic>((key, item) {
      if (key is! String) {
        throw ArgumentError.value(
          value,
          'value',
          'JSON object keys must be strings',
        );
      }
      return MapEntry(key, _copyJsonValue(item));
    });
  }
  throw ArgumentError.value(value, 'value', 'Not a JSON value');
}
