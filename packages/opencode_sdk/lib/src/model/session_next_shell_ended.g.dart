// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_shell_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextShellEnded _$SessionNextShellEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextShellEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextShellEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextShellEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextShellEndedTypeEnum.unknownDefaultOpenApi,
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
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextShellEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextShellEndedToJson(
  SessionNextShellEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextShellEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextShellEndedTypeEnumEnumMap = {
  SessionNextShellEndedTypeEnum.sessionPeriodNextPeriodShellPeriodEnded:
      'session.next.shell.ended',
  SessionNextShellEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
