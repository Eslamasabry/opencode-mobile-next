// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_shell_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextShellStarted _$SessionNextShellStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextShellStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextShellStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextShellStartedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextShellStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextShellStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextShellStartedToJson(
  SessionNextShellStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextShellStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextShellStartedTypeEnumEnumMap = {
  SessionNextShellStartedTypeEnum.sessionPeriodNextPeriodShellPeriodStarted:
      'session.next.shell.started',
  SessionNextShellStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
