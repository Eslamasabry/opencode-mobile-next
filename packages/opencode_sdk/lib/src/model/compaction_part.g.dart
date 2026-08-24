// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'compaction_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CompactionPart _$CompactionPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CompactionPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'sessionID', 'messageID', 'type', 'auto'],
      );
      final val = CompactionPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$CompactionPartTypeEnumEnumMap,
            v,
            unknownValue: CompactionPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        auto: $checkedConvert('auto', (v) => v as bool),
        overflow: $checkedConvert('overflow', (v) => v as bool?),
        tailStartId: $checkedConvert('tail_start_id', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'tailStartId': 'tail_start_id'});

Map<String, dynamic> _$CompactionPartToJson(CompactionPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$CompactionPartTypeEnumEnumMap[instance.type]!,
      'auto': instance.auto,
      'overflow': ?instance.overflow,
      'tail_start_id': ?instance.tailStartId,
    };

const _$CompactionPartTypeEnumEnumMap = {
  CompactionPartTypeEnum.compaction: 'compaction',
  CompactionPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
