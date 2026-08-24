//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_select_session_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiSelectSessionRequest {
  /// Returns a new [TuiSelectSessionRequest] instance.
  TuiSelectSessionRequest({required this.sessionID});

  /// Session ID to navigate to
  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiSelectSessionRequest &&
            runtimeType == other.runtimeType &&
            equals([sessionID], [other.sessionID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([sessionID]);

  factory TuiSelectSessionRequest.fromJson(Map<String, dynamic> json) =>
      _$TuiSelectSessionRequestFromJson(json);

  Map<String, dynamic> toJson() => _$TuiSelectSessionRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
