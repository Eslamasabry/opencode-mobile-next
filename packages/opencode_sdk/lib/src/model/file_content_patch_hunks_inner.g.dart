// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_content_patch_hunks_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileContentPatchHunksInner _$FileContentPatchHunksInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('FileContentPatchHunksInner', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'oldStart',
      'oldLines',
      'newStart',
      'newLines',
      'lines',
    ],
  );
  final val = FileContentPatchHunksInner(
    oldStart: $checkedConvert('oldStart', (v) => (v as num).toInt()),
    oldLines: $checkedConvert('oldLines', (v) => (v as num).toInt()),
    newStart: $checkedConvert('newStart', (v) => (v as num).toInt()),
    newLines: $checkedConvert('newLines', (v) => (v as num).toInt()),
    lines: $checkedConvert(
      'lines',
      (v) => (v as List<dynamic>).map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$FileContentPatchHunksInnerToJson(
  FileContentPatchHunksInner instance,
) => <String, dynamic>{
  'oldStart': instance.oldStart,
  'oldLines': instance.oldLines,
  'newStart': instance.newStart,
  'newLines': instance.newLines,
  'lines': instance.lines,
};
