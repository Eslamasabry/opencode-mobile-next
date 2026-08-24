// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'text_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TextPart _$TextPartFromJson(Map<String, dynamic> json) => $checkedCreate(
  'TextPart',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const ['id', 'sessionID', 'messageID', 'type', 'text'],
    );
    final val = TextPart(
      id: $checkedConvert('id', (v) => v as String),
      sessionID: $checkedConvert('sessionID', (v) => v as String),
      messageID: $checkedConvert('messageID', (v) => v as String),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$TextPartTypeEnumEnumMap,
          v,
          unknownValue: TextPartTypeEnum.unknownDefaultOpenApi,
        ),
      ),
      text: $checkedConvert('text', (v) => v as String),
      synthetic: $checkedConvert('synthetic', (v) => v as bool?),
      ignored: $checkedConvert('ignored', (v) => v as bool?),
      time: $checkedConvert(
        'time',
        (v) =>
            v == null ? null : TextPartTime.fromJson(v as Map<String, dynamic>),
      ),
      metadata: $checkedConvert('metadata', (v) => v),
    );
    return val;
  },
);

Map<String, dynamic> _$TextPartToJson(TextPart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$TextPartTypeEnumEnumMap[instance.type]!,
  'text': instance.text,
  'synthetic': ?instance.synthetic,
  'ignored': ?instance.ignored,
  'time': ?instance.time?.toJson(),
  'metadata': ?instance.metadata,
};

const _$TextPartTypeEnumEnumMap = {
  TextPartTypeEnum.text: 'text',
  TextPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
