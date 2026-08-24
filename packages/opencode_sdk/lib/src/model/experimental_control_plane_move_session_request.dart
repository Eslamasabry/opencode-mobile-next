//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_destination.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_control_plane_move_session_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalControlPlaneMoveSessionRequest {
  /// Returns a new [ExperimentalControlPlaneMoveSessionRequest] instance.
  ExperimentalControlPlaneMoveSessionRequest({
    required this.sessionID,

    required this.destination,

    this.moveChanges,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'destination', required: true, includeIfNull: false)
  final MoveSessionDestination destination;

  @JsonKey(name: r'moveChanges', required: false, includeIfNull: false)
  final bool? moveChanges;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalControlPlaneMoveSessionRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, destination, moveChanges],
              [other.sessionID, other.destination, other.moveChanges],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([sessionID, destination, moveChanges]);

  factory ExperimentalControlPlaneMoveSessionRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalControlPlaneMoveSessionRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalControlPlaneMoveSessionRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
