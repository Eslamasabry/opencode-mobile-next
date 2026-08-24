// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_select_session_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiSelectSessionRequest _$TuiSelectSessionRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('TuiSelectSessionRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID']);
  final val = TuiSelectSessionRequest(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$TuiSelectSessionRequestToJson(
  TuiSelectSessionRequest instance,
) => <String, dynamic>{'sessionID': instance.sessionID};
