// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_deleted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyDeleted _$PtyDeletedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PtyDeleted',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = PtyDeleted(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$PtyDeletedTypeEnumEnumMap,
          v,
          unknownValue: PtyDeletedTypeEnum.unknownDefaultOpenApi,
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
        (v) => PtyDeletedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$PtyDeletedToJson(PtyDeleted instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PtyDeletedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PtyDeletedTypeEnumEnumMap = {
  PtyDeletedTypeEnum.ptyPeriodDeleted: 'pty.deleted',
  PtyDeletedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
