//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_revert_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionRevertRequest {
  /// Returns a new [SessionRevertRequest] instance.
  SessionRevertRequest({required this.messageID, this.partID});

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'partID', required: false, includeIfNull: false)
  final String? partID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionRevertRequest &&
            runtimeType == other.runtimeType &&
            equals([messageID, partID], [other.messageID, other.partID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([messageID, partID]);

  factory SessionRevertRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionRevertRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionRevertRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
