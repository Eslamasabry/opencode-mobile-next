// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_text_delta_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextTextDeltaData _$SessionNextTextDeltaDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextTextDeltaData', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'textID',
      'delta',
    ],
  );
  final val = SessionNextTextDeltaData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    textID: $checkedConvert('textID', (v) => v as String),
    delta: $checkedConvert('delta', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionNextTextDeltaDataToJson(
  SessionNextTextDeltaData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'textID': instance.textID,
  'delta': instance.delta,
};
