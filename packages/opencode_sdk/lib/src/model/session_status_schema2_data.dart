//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_status.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_schema2_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusSchema2Data {
  /// Returns a new [SessionStatusSchema2Data] instance.
  SessionStatusSchema2Data({required this.sessionID, required this.status});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'status', required: true, includeIfNull: false)
  final SessionStatus status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusSchema2Data &&
            runtimeType == other.runtimeType &&
            equals([sessionID, status], [other.sessionID, other.status]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, status]);

  factory SessionStatusSchema2Data.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusSchema2DataFromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusSchema2DataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
