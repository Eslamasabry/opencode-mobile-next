// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_control_plane_move_session_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalControlPlaneMoveSessionRequest
_$ExperimentalControlPlaneMoveSessionRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalControlPlaneMoveSessionRequest', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'destination']);
  final val = ExperimentalControlPlaneMoveSessionRequest(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    destination: $checkedConvert(
      'destination',
      (v) => MoveSessionDestination.fromJson(v as Map<String, dynamic>),
    ),
    moveChanges: $checkedConvert('moveChanges', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalControlPlaneMoveSessionRequestToJson(
  ExperimentalControlPlaneMoveSessionRequest instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'destination': instance.destination.toJson(),
  'moveChanges': ?instance.moveChanges,
};
