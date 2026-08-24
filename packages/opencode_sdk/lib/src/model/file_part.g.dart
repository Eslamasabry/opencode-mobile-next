// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FilePart _$FilePartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FilePart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'mime',
          'url',
        ],
      );
      final val = FilePart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FilePartTypeEnumEnumMap,
            v,
            unknownValue: FilePartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        mime: $checkedConvert('mime', (v) => v as String),
        filename: $checkedConvert('filename', (v) => v as String?),
        url: $checkedConvert('url', (v) => v as String),
        source_: $checkedConvert(
          'source',
          (v) => v == null ? null : FilePartSource.fromJson(v),
        ),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$FilePartToJson(FilePart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$FilePartTypeEnumEnumMap[instance.type]!,
  'mime': instance.mime,
  'filename': ?instance.filename,
  'url': instance.url,
  'source': ?instance.source_?.toJson(),
};

const _$FilePartTypeEnumEnumMap = {
  FilePartTypeEnum.file: 'file',
  FilePartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
