// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_message_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMessageRemoved _$EventMessageRemovedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventMessageRemoved', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventMessageRemoved(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventMessageRemovedTypeEnumEnumMap,
            v,
            unknownValue: EventMessageRemovedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncEventMessageRemovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventMessageRemovedToJson(
  EventMessageRemoved instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMessageRemovedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMessageRemovedTypeEnumEnumMap = {
  EventMessageRemovedTypeEnum.messagePeriodRemoved: 'message.removed',
  EventMessageRemovedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
