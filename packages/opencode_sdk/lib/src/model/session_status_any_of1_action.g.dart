// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_any_of1_action.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusAnyOf1Action _$SessionStatusAnyOf1ActionFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionStatusAnyOf1Action', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['reason', 'provider', 'title', 'message', 'label'],
  );
  final val = SessionStatusAnyOf1Action(
    reason: $checkedConvert('reason', (v) => v as String),
    provider: $checkedConvert('provider', (v) => v as String),
    title: $checkedConvert('title', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    label: $checkedConvert('label', (v) => v as String),
    link: $checkedConvert('link', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SessionStatusAnyOf1ActionToJson(
  SessionStatusAnyOf1Action instance,
) => <String, dynamic>{
  'reason': instance.reason,
  'provider': instance.provider,
  'title': instance.title,
  'message': instance.message,
  'label': instance.label,
  'link': ?instance.link,
};
