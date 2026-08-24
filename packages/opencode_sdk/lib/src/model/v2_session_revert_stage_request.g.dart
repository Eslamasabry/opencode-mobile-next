// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_revert_stage_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionRevertStageRequest _$V2SessionRevertStageRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionRevertStageRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['messageID']);
  final val = V2SessionRevertStageRequest(
    messageID: $checkedConvert('messageID', (v) => v as String),
    files: $checkedConvert('files', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$V2SessionRevertStageRequestToJson(
  V2SessionRevertStageRequest instance,
) => <String, dynamic>{
  'messageID': instance.messageID,
  'files': ?instance.files,
};
