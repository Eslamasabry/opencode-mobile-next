// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_pty_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPtyUpdated _$EventPtyUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventPtyUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventPtyUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventPtyUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventPtyUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => PtyCreatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventPtyUpdatedToJson(EventPtyUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventPtyUpdatedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventPtyUpdatedTypeEnumEnumMap = {
  EventPtyUpdatedTypeEnum.ptyPeriodUpdated: 'pty.updated',
  EventPtyUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
