// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_created.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionCreated _$EventSessionCreatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionCreated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionCreated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionCreatedTypeEnumEnumMap,
            v,
            unknownValue: EventSessionCreatedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$EventSessionCreatedToJson(
  EventSessionCreated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionCreatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionCreatedTypeEnumEnumMap = {
  EventSessionCreatedTypeEnum.sessionPeriodCreated: 'session.created',
  EventSessionCreatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
