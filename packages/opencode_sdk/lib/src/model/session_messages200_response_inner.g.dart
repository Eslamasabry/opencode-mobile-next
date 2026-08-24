// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_messages200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessages200ResponseInner _$SessionMessages200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessages200ResponseInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['info', 'parts']);
  final val = SessionMessages200ResponseInner(
    info: $checkedConvert('info', (v) => Message.fromJson(v)),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>).map(ModelPart.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessages200ResponseInnerToJson(
  SessionMessages200ResponseInner instance,
) => <String, dynamic>{
  'info': instance.info.toJson(),
  'parts': instance.parts.map((e) => e.toJson()).toList(),
};
