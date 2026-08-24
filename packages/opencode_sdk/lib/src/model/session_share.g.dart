// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_share.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionShare _$SessionShareFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionShare', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['url']);
      final val = SessionShare(url: $checkedConvert('url', (v) => v as String));
      return val;
    });

Map<String, dynamic> _$SessionShareToJson(SessionShare instance) =>
    <String, dynamic>{'url': instance.url};
