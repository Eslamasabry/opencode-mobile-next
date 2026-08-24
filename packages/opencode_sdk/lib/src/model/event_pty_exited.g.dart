// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_pty_exited.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPtyExited _$EventPtyExitedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventPtyExited', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventPtyExited(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventPtyExitedTypeEnumEnumMap,
            v,
            unknownValue: EventPtyExitedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => PtyExitedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventPtyExitedToJson(EventPtyExited instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventPtyExitedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventPtyExitedTypeEnumEnumMap = {
  EventPtyExitedTypeEnum.ptyPeriodExited: 'pty.exited',
  EventPtyExitedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
