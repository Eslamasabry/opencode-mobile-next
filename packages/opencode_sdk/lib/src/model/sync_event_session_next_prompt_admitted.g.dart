// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_prompt_admitted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextPromptAdmitted _$SyncEventSessionNextPromptAdmittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextPromptAdmitted', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextPromptAdmitted(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextPromptAdmittedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextPromptAdmittedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextPromptAdmittedToJson(
  SyncEventSessionNextPromptAdmitted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextPromptAdmittedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextPromptAdmittedTypeEnumEnumMap = {
  SyncEventSessionNextPromptAdmittedTypeEnum.sync_: 'sync',
  SyncEventSessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
