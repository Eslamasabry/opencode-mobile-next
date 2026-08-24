// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_vcs_branch_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventVcsBranchUpdated _$EventVcsBranchUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventVcsBranchUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventVcsBranchUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventVcsBranchUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventVcsBranchUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => VcsBranchUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventVcsBranchUpdatedToJson(
  EventVcsBranchUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventVcsBranchUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventVcsBranchUpdatedTypeEnumEnumMap = {
  EventVcsBranchUpdatedTypeEnum.vcsPeriodBranchPeriodUpdated:
      'vcs.branch.updated',
  EventVcsBranchUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
