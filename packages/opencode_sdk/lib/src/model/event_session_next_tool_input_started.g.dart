// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_input_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolInputStarted _$EventSessionNextToolInputStartedFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('EventSessionNextToolInputStarted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionNextToolInputStarted(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionNextToolInputStartedTypeEnumEnumMap,
            v,
            unknownValue:
                EventSessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncEventSessionNextToolInputStartedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionNextToolInputStartedToJson(
  EventSessionNextToolInputStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolInputStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolInputStartedTypeEnumEnumMap = {
  EventSessionNextToolInputStartedTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodStarted:
      'session.next.tool.input.started',
  EventSessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
