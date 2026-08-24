// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_part_delta_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessagePartDeltaData _$MessagePartDeltaDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MessagePartDeltaData', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['sessionID', 'messageID', 'partID', 'field', 'delta'],
  );
  final val = MessagePartDeltaData(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    partID: $checkedConvert('partID', (v) => v as String),
    field: $checkedConvert('field', (v) => v as String),
    delta: $checkedConvert('delta', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$MessagePartDeltaDataToJson(
  MessagePartDeltaData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'partID': instance.partID,
  'field': instance.field,
  'delta': instance.delta,
};
