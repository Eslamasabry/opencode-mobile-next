// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_skills200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppSkills200ResponseInner _$AppSkills200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('AppSkills200ResponseInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name', 'location', 'content']);
  final val = AppSkills200ResponseInner(
    name: $checkedConvert('name', (v) => v as String),
    description: $checkedConvert('description', (v) => v as String?),
    location: $checkedConvert('location', (v) => v as String),
    content: $checkedConvert('content', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$AppSkills200ResponseInnerToJson(
  AppSkills200ResponseInner instance,
) => <String, dynamic>{
  'name': instance.name,
  'description': ?instance.description,
  'location': instance.location,
  'content': instance.content,
};
