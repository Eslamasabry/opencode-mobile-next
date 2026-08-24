// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_success_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolSuccessSyncEventData
_$SyncEventSessionNextToolSuccessSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolSuccessSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'structured',
      'content',
      'provider',
    ],
  );
  final val = SyncEventSessionNextToolSuccessSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    structured: $checkedConvert('structured', (v) => v as Object),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(LLMToolContent.fromJson).toList(),
    ),
    outputPaths: $checkedConvert(
      'outputPaths',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
    result: $checkedConvert('result', (v) => v),
    provider: $checkedConvert(
      'provider',
      (v) => SyncEventSessionNextToolCalledSyncEventDataProvider.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolSuccessSyncEventDataToJson(
  SyncEventSessionNextToolSuccessSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'structured': instance.structured,
  'content': instance.content.map((e) => e.toJson()).toList(),
  'outputPaths': ?instance.outputPaths,
  'result': ?instance.result,
  'provider': instance.provider.toJson(),
};
