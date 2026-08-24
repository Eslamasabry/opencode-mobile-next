// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionUpdated _$EventSessionUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventSessionUpdatedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$EventSessionUpdatedToJson(
  EventSessionUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionUpdatedTypeEnumEnumMap = {
  EventSessionUpdatedTypeEnum.sessionPeriodUpdated: 'session.updated',
  EventSessionUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
