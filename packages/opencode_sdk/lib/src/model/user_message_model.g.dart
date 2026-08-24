// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_message_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserMessageModel _$UserMessageModelFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UserMessageModel', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['providerID', 'modelID']);
      final val = UserMessageModel(
        providerID: $checkedConvert('providerID', (v) => v as String),
        modelID: $checkedConvert('modelID', (v) => v as String),
        variant: $checkedConvert('variant', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$UserMessageModelToJson(UserMessageModel instance) =>
    <String, dynamic>{
      'providerID': instance.providerID,
      'modelID': instance.modelID,
      'variant': ?instance.variant,
    };
