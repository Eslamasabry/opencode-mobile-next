// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_skill_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SkillList200Response _$V2SkillList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SkillList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2SkillList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => SkillV2Info.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SkillList200ResponseToJson(
  V2SkillList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
