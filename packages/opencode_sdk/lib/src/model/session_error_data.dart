//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union033.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionErrorData {
  /// Returns a new [SessionErrorData] instance.
  SessionErrorData({this.sessionID, this.error});

  @JsonKey(name: r'sessionID', required: false, includeIfNull: false)
  final String? sessionID;

  @JsonKey(name: r'error', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion033? error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionErrorData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, error], [other.sessionID, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, error]);

  factory SessionErrorData.fromJson(Map<String, dynamic> json) =>
      _$SessionErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$SessionErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
