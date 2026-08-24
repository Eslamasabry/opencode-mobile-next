// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyUpdated _$PtyUpdatedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PtyUpdated',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = PtyUpdated(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$PtyUpdatedTypeEnumEnumMap,
          v,
          unknownValue: PtyUpdatedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$PtyUpdatedToJson(PtyUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PtyUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PtyUpdatedTypeEnumEnumMap = {
  PtyUpdatedTypeEnum.ptyPeriodUpdated: 'pty.updated',
  PtyUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
