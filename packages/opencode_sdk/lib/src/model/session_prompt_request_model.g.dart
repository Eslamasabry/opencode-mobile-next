// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_prompt_request_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionPromptRequestModel _$SessionPromptRequestModelFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionPromptRequestModel', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['modelID', 'providerID']);
  final val = SessionPromptRequestModel(
    modelID: $checkedConvert('modelID', (v) => v as String),
    providerID: $checkedConvert('providerID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionPromptRequestModelToJson(
  SessionPromptRequestModel instance,
) => <String, dynamic>{
  'modelID': instance.modelID,
  'providerID': instance.providerID,
};
