// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_deleted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionDeleted _$EventSessionDeletedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionDeleted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionDeleted(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionDeletedTypeEnumEnumMap,
            v,
            unknownValue: EventSessionDeletedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncEventSessionCreatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionDeletedToJson(
  EventSessionDeleted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionDeletedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionDeletedTypeEnumEnumMap = {
  EventSessionDeletedTypeEnum.sessionPeriodDeleted: 'session.deleted',
  EventSessionDeletedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
