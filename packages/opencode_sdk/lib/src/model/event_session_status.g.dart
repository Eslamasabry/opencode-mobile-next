// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionStatus _$EventSessionStatusFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionStatus', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionStatus(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionStatusTypeEnumEnumMap,
            v,
            unknownValue: EventSessionStatusTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SessionStatusSchema2Data.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionStatusToJson(EventSessionStatus instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventSessionStatusTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventSessionStatusTypeEnumEnumMap = {
  EventSessionStatusTypeEnum.sessionPeriodStatus: 'session.status',
  EventSessionStatusTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
