// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_reference_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2ReferenceList200Response _$V2ReferenceList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2ReferenceList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2ReferenceList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => ReferenceInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2ReferenceList200ResponseToJson(
  V2ReferenceList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
