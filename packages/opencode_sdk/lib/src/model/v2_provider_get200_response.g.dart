// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_provider_get200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2ProviderGet200Response _$V2ProviderGet200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2ProviderGet200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2ProviderGet200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => ProviderV2Info.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2ProviderGet200ResponseToJson(
  V2ProviderGet200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
