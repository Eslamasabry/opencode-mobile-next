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
class V2Event implements OpenCodeRawJsonValue {
  V2Event(Object? value) : value = _copyJsonValue(value);

  factory V2Event.fromJson(Object? json) => V2Event(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"\$ref\":\"#/components/schemas/Models-devRefreshed\"},{\"\$ref\":\"#/components/schemas/IntegrationUpdated\"},{\"\$ref\":\"#/components/schemas/IntegrationConnectionUpdated\"},{\"\$ref\":\"#/components/schemas/CatalogUpdated\"},{\"\$ref\":\"#/components/schemas/SessionCreated\"},{\"\$ref\":\"#/components/schemas/SessionUpdated\"},{\"\$ref\":\"#/components/schemas/SessionDeleted\"},{\"\$ref\":\"#/components/schemas/MessageUpdated\"},{\"\$ref\":\"#/components/schemas/MessageRemoved\"},{\"\$ref\":\"#/components/schemas/MessagePartUpdated\"},{\"\$ref\":\"#/components/schemas/MessagePartRemoved\"},{\"\$ref\":\"#/components/schemas/SessionNextAgentSwitched\"},{\"\$ref\":\"#/components/schemas/SessionNextModelSwitched\"},{\"\$ref\":\"#/components/schemas/SessionNextMoved\"},{\"\$ref\":\"#/components/schemas/SessionNextPrompted\"},{\"\$ref\":\"#/components/schemas/SessionNextPromptAdmitted\"},{\"\$ref\":\"#/components/schemas/SessionNextContextUpdated\"},{\"\$ref\":\"#/components/schemas/SessionNextSynthetic\"},{\"\$ref\":\"#/components/schemas/SessionNextShellStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextShellEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextStepStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextStepEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextStepFailed\"},{\"\$ref\":\"#/components/schemas/SessionNextTextStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextTextDelta\"},{\"\$ref\":\"#/components/schemas/SessionNextTextEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextReasoningStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextReasoningDelta\"},{\"\$ref\":\"#/components/schemas/SessionNextReasoningEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextToolInputStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextToolInputDelta\"},{\"\$ref\":\"#/components/schemas/SessionNextToolInputEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextToolCalled\"},{\"\$ref\":\"#/components/schemas/SessionNextToolProgress\"},{\"\$ref\":\"#/components/schemas/SessionNextToolSuccess\"},{\"\$ref\":\"#/components/schemas/SessionNextToolFailed\"},{\"\$ref\":\"#/components/schemas/SessionNextRetried\"},{\"\$ref\":\"#/components/schemas/SessionNextCompactionStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextCompactionDelta\"},{\"\$ref\":\"#/components/schemas/SessionNextCompactionEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertStaged\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertCleared\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertCommitted\"},{\"\$ref\":\"#/components/schemas/MessagePartDelta\"},{\"\$ref\":\"#/components/schemas/SessionDiff\"},{\"\$ref\":\"#/components/schemas/SessionError\"},{\"\$ref\":\"#/components/schemas/InstallationUpdated\"},{\"\$ref\":\"#/components/schemas/InstallationUpdate-available\"},{\"\$ref\":\"#/components/schemas/FileEdited\"},{\"\$ref\":\"#/components/schemas/ReferenceUpdated\"},{\"\$ref\":\"#/components/schemas/PermissionV2Asked\"},{\"\$ref\":\"#/components/schemas/PermissionV2Replied\"},{\"\$ref\":\"#/components/schemas/PluginAdded\"},{\"\$ref\":\"#/components/schemas/ProjectDirectoriesUpdated\"},{\"\$ref\":\"#/components/schemas/FileWatcherUpdated\"},{\"\$ref\":\"#/components/schemas/PtyCreated\"},{\"\$ref\":\"#/components/schemas/PtyUpdated\"},{\"\$ref\":\"#/components/schemas/PtyExited\"},{\"\$ref\":\"#/components/schemas/PtyDeleted\"},{\"\$ref\":\"#/components/schemas/QuestionV2Asked\"},{\"\$ref\":\"#/components/schemas/QuestionV2Replied\"},{\"\$ref\":\"#/components/schemas/QuestionV2Rejected\"},{\"\$ref\":\"#/components/schemas/TodoUpdated\"},{\"\$ref\":\"#/components/schemas/LspUpdated\"},{\"\$ref\":\"#/components/schemas/PermissionAsked\"},{\"\$ref\":\"#/components/schemas/PermissionReplied\"},{\"\$ref\":\"#/components/schemas/TuiPromptAppend\"},{\"\$ref\":\"#/components/schemas/TuiCommandExecute\"},{\"\$ref\":\"#/components/schemas/TuiToastShow\"},{\"\$ref\":\"#/components/schemas/TuiSessionSelect\"},{\"\$ref\":\"#/components/schemas/McpToolsChanged\"},{\"\$ref\":\"#/components/schemas/McpBrowserOpenFailed\"},{\"\$ref\":\"#/components/schemas/CommandExecuted\"},{\"\$ref\":\"#/components/schemas/ProjectUpdated\"},{\"\$ref\":\"#/components/schemas/SessionStatusSchema2\"},{\"\$ref\":\"#/components/schemas/SessionIdle\"},{\"\$ref\":\"#/components/schemas/QuestionAsked\"},{\"\$ref\":\"#/components/schemas/QuestionRepliedSchema2\"},{\"\$ref\":\"#/components/schemas/QuestionRejectedSchema2\"},{\"\$ref\":\"#/components/schemas/SessionCompacted\"},{\"\$ref\":\"#/components/schemas/VcsBranchUpdated\"},{\"\$ref\":\"#/components/schemas/WorkspaceReady\"},{\"\$ref\":\"#/components/schemas/WorkspaceFailed\"},{\"\$ref\":\"#/components/schemas/WorkspaceStatus\"},{\"\$ref\":\"#/components/schemas/WorktreeReady\"},{\"\$ref\":\"#/components/schemas/WorktreeFailed\"},{\"\$ref\":\"#/components/schemas/ServerConnected\"},{\"\$ref\":\"#/components/schemas/GlobalDisposed\"}]}";

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
