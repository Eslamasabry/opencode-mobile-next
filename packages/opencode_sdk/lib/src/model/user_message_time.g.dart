// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_message_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserMessageTime _$UserMessageTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UserMessageTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['created']);
      final val = UserMessageTime(
        created: $checkedConvert('created', (v) => v as num),
      );
      return val;
    });

Map<String, dynamic> _$UserMessageTimeToJson(UserMessageTime instance) =>
    <String, dynamic>{'created': instance.created};
