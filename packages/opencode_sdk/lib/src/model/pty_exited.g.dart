// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_exited.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyExited _$PtyExitedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PtyExited',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = PtyExited(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$PtyExitedTypeEnumEnumMap,
          v,
          unknownValue: PtyExitedTypeEnum.unknownDefaultOpenApi,
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
        (v) => PtyExitedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$PtyExitedToJson(PtyExited instance) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$PtyExitedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$PtyExitedTypeEnumEnumMap = {
  PtyExitedTypeEnum.ptyPeriodExited: 'pty.exited',
  PtyExitedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
