// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_content_patch.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileContentPatch _$FileContentPatchFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('FileContentPatch', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['oldFileName', 'newFileName', 'hunks']);
  final val = FileContentPatch(
    oldFileName: $checkedConvert('oldFileName', (v) => v as String),
    newFileName: $checkedConvert('newFileName', (v) => v as String),
    oldHeader: $checkedConvert('oldHeader', (v) => v as String?),
    newHeader: $checkedConvert('newHeader', (v) => v as String?),
    hunks: $checkedConvert(
      'hunks',
      (v) => (v as List<dynamic>)
          .map(
            (e) =>
                FileContentPatchHunksInner.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
    ),
    index: $checkedConvert('index', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$FileContentPatchToJson(FileContentPatch instance) =>
    <String, dynamic>{
      'oldFileName': instance.oldFileName,
      'newFileName': instance.newFileName,
      'oldHeader': ?instance.oldHeader,
      'newHeader': ?instance.newHeader,
      'hunks': instance.hunks.map((e) => e.toJson()).toList(),
      'index': ?instance.index,
    };
