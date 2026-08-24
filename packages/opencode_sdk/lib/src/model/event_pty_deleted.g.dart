// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_pty_deleted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPtyDeleted _$EventPtyDeletedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventPtyDeleted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventPtyDeleted(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventPtyDeletedTypeEnumEnumMap,
            v,
            unknownValue: EventPtyDeletedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => PtyDeletedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventPtyDeletedToJson(EventPtyDeleted instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventPtyDeletedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventPtyDeletedTypeEnumEnumMap = {
  EventPtyDeletedTypeEnum.ptyPeriodDeleted: 'pty.deleted',
  EventPtyDeletedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
