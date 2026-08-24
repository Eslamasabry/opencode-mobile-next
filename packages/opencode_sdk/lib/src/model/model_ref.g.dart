// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_ref.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelRef _$ModelRefFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelRef', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'providerID']);
      final val = ModelRef(
        id: $checkedConvert('id', (v) => v as String),
        providerID: $checkedConvert('providerID', (v) => v as String),
        variant: $checkedConvert('variant', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ModelRefToJson(ModelRef instance) => <String, dynamic>{
  'id': instance.id,
  'providerID': instance.providerID,
  'variant': ?instance.variant,
};
