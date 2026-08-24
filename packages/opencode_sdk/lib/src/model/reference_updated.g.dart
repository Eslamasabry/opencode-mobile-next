// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'reference_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReferenceUpdated _$ReferenceUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ReferenceUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = ReferenceUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ReferenceUpdatedTypeEnumEnumMap,
            v,
            unknownValue: ReferenceUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert('data', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ReferenceUpdatedToJson(ReferenceUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$ReferenceUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$ReferenceUpdatedTypeEnumEnumMap = {
  ReferenceUpdatedTypeEnum.referencePeriodUpdated: 'reference.updated',
  ReferenceUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
