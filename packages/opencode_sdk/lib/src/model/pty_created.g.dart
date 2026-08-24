// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_created.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyCreated _$PtyCreatedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PtyCreated',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = PtyCreated(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$PtyCreatedTypeEnumEnumMap,
          v,
          unknownValue: PtyCreatedTypeEnum.unknownDefaultOpenApi,
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
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert(
        'data',
        (v) => PtyCreatedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$PtyCreatedToJson(PtyCreated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PtyCreatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PtyCreatedTypeEnumEnumMap = {
  PtyCreatedTypeEnum.ptyPeriodCreated: 'pty.created',
  PtyCreatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
