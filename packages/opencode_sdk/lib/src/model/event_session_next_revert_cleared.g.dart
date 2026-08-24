// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_revert_cleared.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextRevertCleared _$EventSessionNextRevertClearedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextRevertCleared', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextRevertCleared(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextRevertClearedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextRevertClearedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextRevertClearedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextRevertClearedToJson(
  EventSessionNextRevertCleared instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextRevertClearedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextRevertClearedTypeEnumEnumMap = {
  EventSessionNextRevertClearedTypeEnum
          .sessionPeriodNextPeriodRevertPeriodCleared:
      'session.next.revert.cleared',
  EventSessionNextRevertClearedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
