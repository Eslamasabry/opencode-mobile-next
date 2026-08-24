// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_edited.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileEdited _$FileEditedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'FileEdited',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = FileEdited(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$FileEditedTypeEnumEnumMap,
          v,
          unknownValue: FileEditedTypeEnum.unknownDefaultOpenApi,
        ),
      ),
      durable: $checkedConvert(
        'durable',
        (v) => v == null
            ? null
            : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
      ),
      location: $checkedConvert(
        'location',
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert(
        'data',
        (v) => FileEditedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$FileEditedToJson(FileEdited instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$FileEditedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$FileEditedTypeEnumEnumMap = {
  FileEditedTypeEnum.filePeriodEdited: 'file.edited',
  FileEditedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
