// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'reference_local_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReferenceLocalSource _$ReferenceLocalSourceFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ReferenceLocalSource', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'path']);
  final val = ReferenceLocalSource(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$ReferenceLocalSourceTypeEnumEnumMap,
        v,
        unknownValue: ReferenceLocalSourceTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    path: $checkedConvert('path', (v) => v as String),
    description: $checkedConvert('description', (v) => v as String?),
    hidden: $checkedConvert('hidden', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$ReferenceLocalSourceToJson(
  ReferenceLocalSource instance,
) => <String, dynamic>{
  'type': _$ReferenceLocalSourceTypeEnumEnumMap[instance.type]!,
  'path': instance.path,
  'description': ?instance.description,
  'hidden': ?instance.hidden,
};

const _$ReferenceLocalSourceTypeEnumEnumMap = {
  ReferenceLocalSourceTypeEnum.local: 'local',
  ReferenceLocalSourceTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
