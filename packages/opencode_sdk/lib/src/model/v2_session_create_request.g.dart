// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionCreateRequest _$V2SessionCreateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionCreateRequest', json, ($checkedConvert) {
  final val = V2SessionCreateRequest(
    id: $checkedConvert('id', (v) => v as String?),
    agent: $checkedConvert('agent', (v) => v as String?),
    model: $checkedConvert(
      'model',
      (v) => v == null ? null : ModelRef.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionCreateRequestToJson(
  V2SessionCreateRequest instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'agent': ?instance.agent,
  'model': ?instance.model?.toJson(),
  'location': ?instance.location?.toJson(),
};
