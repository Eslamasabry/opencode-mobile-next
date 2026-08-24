// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_init_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionInitRequest _$SessionInitRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionInitRequest', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['modelID', 'providerID', 'messageID'],
      );
      final val = SessionInitRequest(
        modelID: $checkedConvert('modelID', (v) => v as String),
        providerID: $checkedConvert('providerID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SessionInitRequestToJson(SessionInitRequest instance) =>
    <String, dynamic>{
      'modelID': instance.modelID,
      'providerID': instance.providerID,
      'messageID': instance.messageID,
    };
