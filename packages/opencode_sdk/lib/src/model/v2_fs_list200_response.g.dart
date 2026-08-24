// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_fs_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2FsList200Response _$V2FsList200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2FsList200Response', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['location', 'data']);
      final val = V2FsList200Response(
        location: $checkedConvert(
          'location',
          (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => (v as List<dynamic>)
              .map((e) => FileSystemEntry.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2FsList200ResponseToJson(
  V2FsList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
