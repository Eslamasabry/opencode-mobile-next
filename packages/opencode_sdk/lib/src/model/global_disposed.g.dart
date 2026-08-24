// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'global_disposed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GlobalDisposed _$GlobalDisposedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('GlobalDisposed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = GlobalDisposed(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$GlobalDisposedTypeEnumEnumMap,
            v,
            unknownValue: GlobalDisposedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$GlobalDisposedToJson(GlobalDisposed instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$GlobalDisposedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$GlobalDisposedTypeEnumEnumMap = {
  GlobalDisposedTypeEnum.globalPeriodDisposed: 'global.disposed',
  GlobalDisposedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
