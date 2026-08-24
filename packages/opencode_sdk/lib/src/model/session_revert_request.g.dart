// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_revert_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionRevertRequest _$SessionRevertRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionRevertRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['messageID']);
  final val = SessionRevertRequest(
    messageID: $checkedConvert('messageID', (v) => v as String),
    partID: $checkedConvert('partID', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SessionRevertRequestToJson(
  SessionRevertRequest instance,
) => <String, dynamic>{
  'messageID': instance.messageID,
  'partID': ?instance.partID,
};
