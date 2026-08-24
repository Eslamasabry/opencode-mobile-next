//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_next_tool_input_delta_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionNextToolInputDeltaData {
  /// Returns a new [SessionNextToolInputDeltaData] instance.
  SessionNextToolInputDeltaData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.delta,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'delta', required: true, includeIfNull: false)
  final String delta;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionNextToolInputDeltaData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, callID, delta],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.delta,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        callID,
        delta,
      ]);

  factory SessionNextToolInputDeltaData.fromJson(Map<String, dynamic> json) =>
      _$SessionNextToolInputDeltaDataFromJson(json);

  Map<String, dynamic> toJson() => _$SessionNextToolInputDeltaDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
