// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_shell_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextShellEnded _$EventSessionNextShellEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextShellEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextShellEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextShellEndedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextShellEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextShellEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextShellEndedToJson(
  EventSessionNextShellEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextShellEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextShellEndedTypeEnumEnumMap = {
  EventSessionNextShellEndedTypeEnum.sessionPeriodNextPeriodShellPeriodEnded:
      'session.next.shell.ended',
  EventSessionNextShellEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
