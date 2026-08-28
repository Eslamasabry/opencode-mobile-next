// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2Info _$ModelV2InfoFromJson(Map<String, dynamic> json) => $checkedCreate(
  'ModelV2Info',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const [
        'id',
        'providerID',
        'name',
        'api',
        'capabilities',
        'request',
        'variants',
        'time',
        'cost',
        'status',
        'enabled',
        'limit',
      ],
    );
    final val = ModelV2Info(
      id: $checkedConvert('id', (v) => v as String),
      providerID: $checkedConvert('providerID', (v) => v as String),
      family: $checkedConvert('family', (v) => v as String?),
      name: $checkedConvert('name', (v) => v as String),
      api: $checkedConvert('api', (v) => ModelApi.fromJson(v)),
      capabilities: $checkedConvert(
        'capabilities',
        (v) => ModelV2Capabilities.fromJson(v as Map<String, dynamic>),
      ),
      request: $checkedConvert(
        'request',
        (v) => ModelV2InfoRequest.fromJson(v as Map<String, dynamic>),
      ),
      variants: $checkedConvert(
        'variants',
        (v) => (v as List<dynamic>)
            .map(
              (e) =>
                  ModelV2InfoVariantsInner.fromJson(e as Map<String, dynamic>),
            )
            .toList(),
      ),
      time: $checkedConvert(
        'time',
        (v) => ModelV2InfoTime.fromJson(v as Map<String, dynamic>),
      ),
      cost: $checkedConvert(
        'cost',
        (v) => (v as List<dynamic>)
            .map((e) => ModelCost.fromJson(e as Map<String, dynamic>))
            .toList(),
      ),
      status: $checkedConvert(
        'status',
        (v) => $enumDecode(
          _$ModelV2InfoStatusEnumEnumMap,
          v,
          unknownValue: ModelV2InfoStatusEnum.unknownDefaultOpenApi,
        ),
      ),
      enabled: $checkedConvert('enabled', (v) => v as bool),
      limit: $checkedConvert(
        'limit',
        (v) => ModelV2InfoLimit.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$ModelV2InfoToJson(ModelV2Info instance) =>
    <String, dynamic>{
      'id': instance.id,
      'providerID': instance.providerID,
      'family': ?instance.family,
      'name': instance.name,
      'api': instance.api.toJson(),
      'capabilities': instance.capabilities.toJson(),
      'request': instance.request.toJson(),
      'variants': instance.variants.map((e) => e.toJson()).toList(),
      'time': instance.time.toJson(),
      'cost': instance.cost.map((e) => e.toJson()).toList(),
      'status': _$ModelV2InfoStatusEnumEnumMap[instance.status]!,
      'enabled': instance.enabled,
      'limit': instance.limit.toJson(),
    };

const _$ModelV2InfoStatusEnumEnumMap = {
  ModelV2InfoStatusEnum.alpha: 'alpha',
  ModelV2InfoStatusEnum.beta: 'beta',
  ModelV2InfoStatusEnum.deprecated: 'deprecated',
  ModelV2InfoStatusEnum.active: 'active',
  ModelV2InfoStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
