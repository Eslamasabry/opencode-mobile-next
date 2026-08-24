//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_fork_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionForkRequest {
  /// Returns a new [SessionForkRequest] instance.
  SessionForkRequest({this.messageID});

  @JsonKey(name: r'messageID', required: false, includeIfNull: false)
  final String? messageID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionForkRequest &&
            runtimeType == other.runtimeType &&
            equals([messageID], [other.messageID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([messageID]);

  factory SessionForkRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionForkRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionForkRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
