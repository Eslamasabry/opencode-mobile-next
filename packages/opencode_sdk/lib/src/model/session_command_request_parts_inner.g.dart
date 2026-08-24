// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_command_request_parts_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCommandRequestPartsInner _$SessionCommandRequestPartsInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionCommandRequestPartsInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'mime', 'url']);
  final val = SessionCommandRequestPartsInner(
    id: $checkedConvert('id', (v) => v as String?),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionCommandRequestPartsInnerTypeEnumEnumMap,
        v,
        unknownValue:
            SessionCommandRequestPartsInnerTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SessionCommandRequestPartsInnerToJson(
  SessionCommandRequestPartsInner instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'type': _$SessionCommandRequestPartsInnerTypeEnumEnumMap[instance.type]!,
  'mime': instance.mime,
  'filename': ?instance.filename,
  'url': instance.url,
  'source': ?instance.source_?.toJson(),
};

const _$SessionCommandRequestPartsInnerTypeEnumEnumMap = {
  SessionCommandRequestPartsInnerTypeEnum.file: 'file',
  SessionCommandRequestPartsInnerTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
