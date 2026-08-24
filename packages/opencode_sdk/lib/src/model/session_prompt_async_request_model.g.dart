// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_prompt_async_request_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionPromptAsyncRequestModel _$SessionPromptAsyncRequestModelFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionPromptAsyncRequestModel', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['providerID', 'modelID']);
  final val = SessionPromptAsyncRequestModel(
    providerID: $checkedConvert('providerID', (v) => v as String),
    modelID: $checkedConvert('modelID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionPromptAsyncRequestModelToJson(
  SessionPromptAsyncRequestModel instance,
) => <String, dynamic>{
  'providerID': instance.providerID,
  'modelID': instance.modelID,
};
