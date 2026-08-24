// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_tool_provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantToolProvider
_$SessionMessageAssistantToolProviderFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantToolProvider', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['executed']);
  final val = SessionMessageAssistantToolProvider(
    executed: $checkedConvert('executed', (v) => v as bool),
    metadata: $checkedConvert(
      'metadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
    resultMetadata: $checkedConvert(
      'resultMetadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantToolProviderToJson(
  SessionMessageAssistantToolProvider instance,
) => <String, dynamic>{
  'executed': instance.executed,
  'metadata': ?instance.metadata,
  'resultMetadata': ?instance.resultMetadata,
};
