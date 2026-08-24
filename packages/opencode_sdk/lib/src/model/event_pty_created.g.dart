// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_pty_created.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPtyCreated _$EventPtyCreatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventPtyCreated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventPtyCreated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventPtyCreatedTypeEnumEnumMap,
            v,
            unknownValue: EventPtyCreatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => PtyCreatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventPtyCreatedToJson(EventPtyCreated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventPtyCreatedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventPtyCreatedTypeEnumEnumMap = {
  EventPtyCreatedTypeEnum.ptyPeriodCreated: 'pty.created',
  EventPtyCreatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
