// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_create_request_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCreateRequestModel _$SessionCreateRequestModelFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionCreateRequestModel', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'providerID']);
  final val = SessionCreateRequestModel(
    id: $checkedConvert('id', (v) => v as String),
    providerID: $checkedConvert('providerID', (v) => v as String),
    variant: $checkedConvert('variant', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SessionCreateRequestModelToJson(
  SessionCreateRequestModel instance,
) => <String, dynamic>{
  'id': instance.id,
  'providerID': instance.providerID,
  'variant': ?instance.variant,
};
