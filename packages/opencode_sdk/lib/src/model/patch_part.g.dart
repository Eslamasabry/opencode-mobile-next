// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'patch_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PatchPart _$PatchPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PatchPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'hash',
          'files',
        ],
      );
      final val = PatchPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PatchPartTypeEnumEnumMap,
            v,
            unknownValue: PatchPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        hash: $checkedConvert('hash', (v) => v as String),
        files: $checkedConvert(
          'files',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PatchPartToJson(PatchPart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$PatchPartTypeEnumEnumMap[instance.type]!,
  'hash': instance.hash,
  'files': instance.files,
};

const _$PatchPartTypeEnumEnumMap = {
  PatchPartTypeEnum.patch_: 'patch',
  PatchPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
