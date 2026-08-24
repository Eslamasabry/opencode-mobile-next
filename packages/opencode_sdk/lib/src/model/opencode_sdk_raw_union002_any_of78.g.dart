// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of78.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf78 _$OpencodeSdkRawUnion002AnyOf78FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf78', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf78(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf78TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf78TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionRejectedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf78ToJson(
  OpencodeSdkRawUnion002AnyOf78 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf78TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf78TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf78TypeEnum.questionPeriodRejected:
      'question.rejected',
  OpencodeSdkRawUnion002AnyOf78TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
