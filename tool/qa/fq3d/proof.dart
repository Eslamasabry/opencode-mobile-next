import 'dart:convert';

import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/models.dart';

/// Accept only the complete password line emitted by pinned OC2 2.0.10.
/// The caller owns stdout chunk framing and keeps this value in memory only.
String? generatedServerPassword(String line) {
  final match = RegExp(
    r'^server password ([A-Za-z0-9_-]{43})$',
  ).firstMatch(line);
  return match != null && match.group(0) == line ? match.group(1) : null;
}

/// A catalog mode's own id can differ from its underlying provider modelID.
Api2ModelRef catalogRef(Api2ModelInfo row) =>
    Api2ModelRef(id: api2CatalogModelID(row), providerID: row.providerID ?? '');

String _variant(String? value) => switch (value) {
  null || '' || 'default' => '',
  final value => value,
};

bool sameModel(Api2ModelRef? actual, Api2ModelRef wanted) =>
    actual != null &&
    wanted.id.isNotEmpty &&
    wanted.providerID.isNotEmpty &&
    actual.id == wanted.id &&
    actual.providerID == wanted.providerID &&
    _variant(actual.variant) == _variant(wanted.variant);

/// Pinned schema/llm.ts normalizes successful final answers to `stop`.
/// `end_turn` is a provider rawFinish, not a normalized finish value.
/// Execution success by itself cannot substitute for retained fresh output.
bool successfulAssistant(
  Api2AssistantMessage message,
  Api2ModelRef wanted, {
  required Set<String> previousIDs,
  required bool executionSucceeded,
}) =>
    executionSucceeded &&
    message.id.isNotEmpty &&
    !previousIDs.contains(message.id) &&
    message.completed &&
    message.error == null &&
    message.finish == 'stop' &&
    sameModel(message.model, wanted);

/// Check the exact retained inline attachment, without trusting a URI/name.
bool retainedPng(Api2UserMessage message, List<int> bytes) {
  if (message.files.length != 1 || bytes.isEmpty) {
    return false;
  }
  final file = message.files.single;
  final data = file.data;
  if (file.mime != 'image/png' || data == null) {
    return false;
  }
  try {
    final retained = base64Decode(data);
    if (retained.length != bytes.length) {
      return false;
    }
    for (var index = 0; index < bytes.length; index++) {
      if (retained[index] != bytes[index]) {
        return false;
      }
    }
    return true;
  } on FormatException {
    return false;
  }
}

/// Accept a JSON answer or one complete JSON code fence, never loose prose.
bool imageAnswerMatches(String text) {
  var answer = text.trim();
  final fence = RegExp(
    r'^```(?:json)?[ \t]*\r?\n([\s\S]*?)\r?\n```$',
    caseSensitive: false,
  ).firstMatch(answer);
  if (fence != null && fence.group(0) == answer) {
    answer = fence.group(1)!.trim();
  }
  try {
    final decoded = jsonDecode(answer);
    return decoded is Map &&
        decoded.length == 2 &&
        decoded['left'] == 'red' &&
        decoded['right'] == 'blue';
  } on FormatException {
    return false;
  }
}

/// The pinned provider auth discriminator, never status/message heuristics.
bool providerAuthFailure(Api2StructuredError? error) =>
    error?.type == 'provider.auth';

/// Location is optional on global envelopes. The exact owned session ID is
/// bound to its directory by create/readback; reject an explicit foreign
/// directory but do not discard a terminal solely for omitted location.
bool ownedEventScope(
  Api2EventEnvelope envelope, {
  required String sessionID,
  required String directory,
}) {
  final event = envelope.event;
  final actualID = switch (event) {
    Api2SessionExecutionEvent() => event.sessionID,
    Api2SessionRetryScheduledEvent() => event.sessionID,
    _ => null,
  };
  return sessionID.isNotEmpty &&
      directory.startsWith('/') &&
      actualID == sessionID &&
      (envelope.location?.directory == null ||
          envelope.location!.directory == directory);
}
