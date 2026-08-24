// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderList200Response _$ProviderList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['all', 'default', 'connected']);
  final val = ProviderList200Response(
    all: $checkedConvert(
      'all',
      (v) => (v as List<dynamic>)
          .map((e) => Provider.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    default_: $checkedConvert(
      'default',
      (v) => Map<String, String>.from(v as Map),
    ),
    connected: $checkedConvert(
      'connected',
      (v) => (v as List<dynamic>).map((e) => e as String).toList(),
    ),
  );
  return val;
}, fieldKeyMap: const {'default_': 'default'});

Map<String, dynamic> _$ProviderList200ResponseToJson(
  ProviderList200Response instance,
) => <String, dynamic>{
  'all': instance.all.map((e) => e.toJson()).toList(),
  'default': instance.default_,
  'connected': instance.connected,
};
