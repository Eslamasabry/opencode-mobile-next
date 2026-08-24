// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_revert_stage200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionRevertStage200Response _$V2SessionRevertStage200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionRevertStage200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionRevertStage200Response(
    data: $checkedConvert(
      'data',
      (v) => RevertState.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionRevertStage200ResponseToJson(
  V2SessionRevertStage200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
