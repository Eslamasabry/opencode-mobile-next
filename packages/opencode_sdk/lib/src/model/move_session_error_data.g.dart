// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'move_session_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MoveSessionErrorData _$MoveSessionErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MoveSessionErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message']);
  final val = MoveSessionErrorData(
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$MoveSessionErrorDataToJson(
  MoveSessionErrorData instance,
) => <String, dynamic>{'message': instance.message};
