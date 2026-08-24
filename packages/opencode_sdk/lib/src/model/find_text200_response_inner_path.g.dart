// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'find_text200_response_inner_path.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FindText200ResponseInnerPath _$FindText200ResponseInnerPathFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('FindText200ResponseInnerPath', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['text']);
  final val = FindText200ResponseInnerPath(
    text: $checkedConvert('text', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$FindText200ResponseInnerPathToJson(
  FindText200ResponseInnerPath instance,
) => <String, dynamic>{'text': instance.text};
