// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_input_delta_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolInputDeltaData _$SessionNextToolInputDeltaDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolInputDeltaData', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'delta',
    ],
  );
  final val = SessionNextToolInputDeltaData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    delta: $checkedConvert('delta', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolInputDeltaDataToJson(
  SessionNextToolInputDeltaData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'delta': instance.delta,
};
