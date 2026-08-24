// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_revert_staged.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextRevertStaged _$EventSessionNextRevertStagedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextRevertStaged', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextRevertStaged(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextRevertStagedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextRevertStagedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextRevertStagedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextRevertStagedToJson(
  EventSessionNextRevertStaged instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextRevertStagedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextRevertStagedTypeEnumEnumMap = {
  EventSessionNextRevertStagedTypeEnum
          .sessionPeriodNextPeriodRevertPeriodStaged:
      'session.next.revert.staged',
  EventSessionNextRevertStagedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
