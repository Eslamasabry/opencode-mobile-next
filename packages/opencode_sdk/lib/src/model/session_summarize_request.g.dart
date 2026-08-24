// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_summarize_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionSummarizeRequest _$SessionSummarizeRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionSummarizeRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['providerID', 'modelID']);
  final val = SessionSummarizeRequest(
    providerID: $checkedConvert('providerID', (v) => v as String),
    modelID: $checkedConvert('modelID', (v) => v as String),
    auto: $checkedConvert('auto', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$SessionSummarizeRequestToJson(
  SessionSummarizeRequest instance,
) => <String, dynamic>{
  'providerID': instance.providerID,
  'modelID': instance.modelID,
  'auto': ?instance.auto,
};
