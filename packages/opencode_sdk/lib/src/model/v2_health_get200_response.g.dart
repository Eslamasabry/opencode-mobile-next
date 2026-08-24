// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_health_get200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2HealthGet200Response _$V2HealthGet200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2HealthGet200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['healthy']);
  final val = V2HealthGet200Response(
    healthy: $checkedConvert(
      'healthy',
      (v) => $enumDecode(
        _$V2HealthGet200ResponseHealthyEnumEnumMap,
        v,
        unknownValue: V2HealthGet200ResponseHealthyEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2HealthGet200ResponseToJson(
  V2HealthGet200Response instance,
) => <String, dynamic>{
  'healthy': _$V2HealthGet200ResponseHealthyEnumEnumMap[instance.healthy]!,
};

const _$V2HealthGet200ResponseHealthyEnumEnumMap = {
  V2HealthGet200ResponseHealthyEnum.true_: 'true',
  V2HealthGet200ResponseHealthyEnum.unknownDefaultOpenApi: '11184809',
};
