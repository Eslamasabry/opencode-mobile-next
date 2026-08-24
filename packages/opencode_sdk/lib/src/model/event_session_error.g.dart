// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionError _$EventSessionErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionError(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionErrorTypeEnumEnumMap,
            v,
            unknownValue: EventSessionErrorTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) =>
              EventSessionErrorProperties.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionErrorToJson(EventSessionError instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventSessionErrorTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventSessionErrorTypeEnumEnumMap = {
  EventSessionErrorTypeEnum.sessionPeriodError: 'session.error',
  EventSessionErrorTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
