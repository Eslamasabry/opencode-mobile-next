// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_update_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionUpdateRequest _$SessionUpdateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionUpdateRequest', json, ($checkedConvert) {
  final val = SessionUpdateRequest(
    title: $checkedConvert('title', (v) => v as String?),
    metadata: $checkedConvert('metadata', (v) => v),
    permission: $checkedConvert(
      'permission',
      (v) => (v as List<dynamic>?)
          ?.map((e) => PermissionRule.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    time: $checkedConvert(
      'time',
      (v) => v == null
          ? null
          : SessionUpdateRequestTime.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionUpdateRequestToJson(
  SessionUpdateRequest instance,
) => <String, dynamic>{
  'title': ?instance.title,
  'metadata': ?instance.metadata,
  'permission': ?instance.permission?.map((e) => e.toJson()).toList(),
  'time': ?instance.time?.toJson(),
};
