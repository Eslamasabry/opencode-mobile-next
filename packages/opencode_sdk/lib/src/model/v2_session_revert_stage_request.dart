//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_revert_stage_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionRevertStageRequest {
  /// Returns a new [V2SessionRevertStageRequest] instance.
  V2SessionRevertStageRequest({required this.messageID, this.files});

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final bool? files;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionRevertStageRequest &&
            runtimeType == other.runtimeType &&
            equals([messageID, files], [other.messageID, other.files]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([messageID, files]);

  factory V2SessionRevertStageRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionRevertStageRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionRevertStageRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
