// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_tokens_cache.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionTokensCache _$SessionTokensCacheFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionTokensCache', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['read', 'write']);
      final val = SessionTokensCache(
        read: $checkedConvert('read', (v) => v as num),
        write: $checkedConvert('write', (v) => v as num),
      );
      return val;
    });

Map<String, dynamic> _$SessionTokensCacheToJson(SessionTokensCache instance) =>
    <String, dynamic>{'read': instance.read, 'write': instance.write};
