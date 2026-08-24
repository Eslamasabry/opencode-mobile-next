// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_providers200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigProviders200Response _$ConfigProviders200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ConfigProviders200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['providers', 'default']);
  final val = ConfigProviders200Response(
    providers: $checkedConvert(
      'providers',
      (v) => (v as List<dynamic>)
          .map((e) => Provider.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    default_: $checkedConvert(
      'default',
      (v) => Map<String, String>.from(v as Map),
    ),
  );
  return val;
}, fieldKeyMap: const {'default_': 'default'});

Map<String, dynamic> _$ConfigProviders200ResponseToJson(
  ConfigProviders200Response instance,
) => <String, dynamic>{
  'providers': instance.providers.map((e) => e.toJson()).toList(),
  'default': instance.default_,
};
