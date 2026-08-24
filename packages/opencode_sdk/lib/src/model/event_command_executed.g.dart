// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_command_executed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventCommandExecuted _$EventCommandExecutedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventCommandExecuted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventCommandExecuted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventCommandExecutedTypeEnumEnumMap,
        v,
        unknownValue: EventCommandExecutedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => CommandExecutedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventCommandExecutedToJson(
  EventCommandExecuted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventCommandExecutedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventCommandExecutedTypeEnumEnumMap = {
  EventCommandExecutedTypeEnum.commandPeriodExecuted: 'command.executed',
  EventCommandExecutedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
