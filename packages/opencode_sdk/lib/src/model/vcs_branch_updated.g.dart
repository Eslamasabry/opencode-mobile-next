// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_branch_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsBranchUpdated _$VcsBranchUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsBranchUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = VcsBranchUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$VcsBranchUpdatedTypeEnumEnumMap,
            v,
            unknownValue: VcsBranchUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => VcsBranchUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$VcsBranchUpdatedToJson(VcsBranchUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$VcsBranchUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$VcsBranchUpdatedTypeEnumEnumMap = {
  VcsBranchUpdatedTypeEnum.vcsPeriodBranchPeriodUpdated: 'vcs.branch.updated',
  VcsBranchUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
