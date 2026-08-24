// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_edited_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileEditedData _$FileEditedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileEditedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['file']);
      final val = FileEditedData(
        file: $checkedConvert('file', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$FileEditedDataToJson(FileEditedData instance) =>
    <String, dynamic>{'file': instance.file};
