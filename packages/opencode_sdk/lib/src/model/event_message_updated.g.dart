// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_message_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMessageUpdated _$EventMessageUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventMessageUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventMessageUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventMessageUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventMessageUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncEventMessageUpdatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventMessageUpdatedToJson(
  EventMessageUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMessageUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMessageUpdatedTypeEnumEnumMap = {
  EventMessageUpdatedTypeEnum.messagePeriodUpdated: 'message.updated',
  EventMessageUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
