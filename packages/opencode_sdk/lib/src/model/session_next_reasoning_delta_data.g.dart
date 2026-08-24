// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_reasoning_delta_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextReasoningDeltaData _$SessionNextReasoningDeltaDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextReasoningDeltaData', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'reasoningID',
      'delta',
    ],
  );
  final val = SessionNextReasoningDeltaData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    reasoningID: $checkedConvert('reasoningID', (v) => v as String),
    delta: $checkedConvert('delta', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionNextReasoningDeltaDataToJson(
  SessionNextReasoningDeltaData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'reasoningID': instance.reasoningID,
  'delta': instance.delta,
};
