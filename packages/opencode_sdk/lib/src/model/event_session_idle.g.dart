// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_idle.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionIdle _$EventSessionIdleFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionIdle', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionIdle(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionIdleTypeEnumEnumMap,
            v,
            unknownValue: EventSessionIdleTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncStealRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionIdleToJson(EventSessionIdle instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventSessionIdleTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventSessionIdleTypeEnumEnumMap = {
  EventSessionIdleTypeEnum.sessionPeriodIdle: 'session.idle',
  EventSessionIdleTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
