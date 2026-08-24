// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'snapshot_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SnapshotPart _$SnapshotPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SnapshotPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'snapshot',
        ],
      );
      final val = SnapshotPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SnapshotPartTypeEnumEnumMap,
            v,
            unknownValue: SnapshotPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        snapshot: $checkedConvert('snapshot', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SnapshotPartToJson(SnapshotPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$SnapshotPartTypeEnumEnumMap[instance.type]!,
      'snapshot': instance.snapshot,
    };

const _$SnapshotPartTypeEnumEnumMap = {
  SnapshotPartTypeEnum.snapshot: 'snapshot',
  SnapshotPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
