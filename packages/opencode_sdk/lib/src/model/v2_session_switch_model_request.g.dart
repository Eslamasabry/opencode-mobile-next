// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_switch_model_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionSwitchModelRequest _$V2SessionSwitchModelRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionSwitchModelRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['model']);
  final val = V2SessionSwitchModelRequest(
    model: $checkedConvert(
      'model',
      (v) => ModelRef.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionSwitchModelRequestToJson(
  V2SessionSwitchModelRequest instance,
) => <String, dynamic>{'model': instance.model.toJson()};
