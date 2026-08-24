// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_shell_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextShellStarted _$EventSessionNextShellStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextShellStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextShellStarted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextShellStartedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextShellStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextShellStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextShellStartedToJson(
  EventSessionNextShellStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextShellStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextShellStartedTypeEnumEnumMap = {
  EventSessionNextShellStartedTypeEnum
          .sessionPeriodNextPeriodShellPeriodStarted:
      'session.next.shell.started',
  EventSessionNextShellStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
