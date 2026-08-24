// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Model _$ModelFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('Model', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'id',
      'providerID',
      'api',
      'name',
      'capabilities',
      'cost',
      'limit',
      'status',
      'options',
      'headers',
      'release_date',
    ],
  );
  final val = Model(
    id: $checkedConvert('id', (v) => v as String),
    providerID: $checkedConvert('providerID', (v) => v as String),
    api: $checkedConvert('api', (v) => ModelApi.fromJson(v)),
    name: $checkedConvert('name', (v) => v as String),
    family: $checkedConvert('family', (v) => v as String?),
    capabilities: $checkedConvert(
      'capabilities',
      (v) => ModelCapabilities.fromJson(v as Map<String, dynamic>),
    ),
    cost: $checkedConvert(
      'cost',
      (v) => ModelCost.fromJson(v as Map<String, dynamic>),
    ),
    limit: $checkedConvert(
      'limit',
      (v) => ProviderConfigModelsValueLimit.fromJson(v as Map<String, dynamic>),
    ),
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$ModelStatusEnumEnumMap,
        v,
        unknownValue: ModelStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    options: $checkedConvert('options', (v) => v as Object),
    headers: $checkedConvert(
      'headers',
      (v) => Map<String, String>.from(v as Map),
    ),
    releaseDate: $checkedConvert('release_date', (v) => v as String),
    variants: $checkedConvert(
      'variants',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
  );
  return val;
}, fieldKeyMap: const {'releaseDate': 'release_date'});

Map<String, dynamic> _$ModelToJson(Model instance) => <String, dynamic>{
  'id': instance.id,
  'providerID': instance.providerID,
  'api': instance.api.toJson(),
  'name': instance.name,
  'family': ?instance.family,
  'capabilities': instance.capabilities.toJson(),
  'cost': instance.cost.toJson(),
  'limit': instance.limit.toJson(),
  'status': _$ModelStatusEnumEnumMap[instance.status]!,
  'options': instance.options,
  'headers': instance.headers,
  'release_date': instance.releaseDate,
  'variants': ?instance.variants,
};

const _$ModelStatusEnumEnumMap = {
  ModelStatusEnum.alpha: 'alpha',
  ModelStatusEnum.beta: 'beta',
  ModelStatusEnum.deprecated: 'deprecated',
  ModelStatusEnum.active: 'active',
  ModelStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
