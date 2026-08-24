// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'reference_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReferenceInfo _$ReferenceInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ReferenceInfo', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'path', 'source']);
      final val = ReferenceInfo(
        name: $checkedConvert('name', (v) => v as String),
        path: $checkedConvert('path', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        hidden: $checkedConvert('hidden', (v) => v as bool?),
        source_: $checkedConvert('source', (v) => ReferenceSource.fromJson(v)),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$ReferenceInfoToJson(ReferenceInfo instance) =>
    <String, dynamic>{
      'name': instance.name,
      'path': instance.path,
      'description': ?instance.description,
      'hidden': ?instance.hidden,
      'source': instance.source_.toJson(),
    };
