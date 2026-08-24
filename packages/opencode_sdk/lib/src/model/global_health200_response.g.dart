// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'global_health200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GlobalHealth200Response _$GlobalHealth200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('GlobalHealth200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['healthy', 'version']);
  final val = GlobalHealth200Response(
    healthy: $checkedConvert(
      'healthy',
      (v) => $enumDecode(
        _$GlobalHealth200ResponseHealthyEnumEnumMap,
        v,
        unknownValue: GlobalHealth200ResponseHealthyEnum.unknownDefaultOpenApi,
      ),
    ),
    version: $checkedConvert('version', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$GlobalHealth200ResponseToJson(
  GlobalHealth200Response instance,
) => <String, dynamic>{
  'healthy': _$GlobalHealth200ResponseHealthyEnumEnumMap[instance.healthy]!,
  'version': instance.version,
};

const _$GlobalHealth200ResponseHealthyEnumEnumMap = {
  GlobalHealth200ResponseHealthyEnum.true_: 'true',
  GlobalHealth200ResponseHealthyEnum.unknownDefaultOpenApi: '11184809',
};
