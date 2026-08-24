//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_next_text_delta_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionNextTextDeltaData {
  /// Returns a new [SessionNextTextDeltaData] instance.
  SessionNextTextDeltaData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.textID,

    required this.delta,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'textID', required: true, includeIfNull: false)
  final String textID;

  @JsonKey(name: r'delta', required: true, includeIfNull: false)
  final String delta;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionNextTextDeltaData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, textID, delta],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.textID,
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
        textID,
        delta,
      ]);

  factory SessionNextTextDeltaData.fromJson(Map<String, dynamic> json) =>
      _$SessionNextTextDeltaDataFromJson(json);

  Map<String, dynamic> toJson() => _$SessionNextTextDeltaDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
