//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_project_copy_remove_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2ProjectCopyRemoveRequest {
  /// Returns a new [V2ProjectCopyRemoveRequest] instance.
  V2ProjectCopyRemoveRequest({required this.directory, required this.force});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'force', required: true, includeIfNull: false)
  final bool force;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2ProjectCopyRemoveRequest &&
            runtimeType == other.runtimeType &&
            equals([directory, force], [other.directory, other.force]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([directory, force]);

  factory V2ProjectCopyRemoveRequest.fromJson(Map<String, dynamic> json) =>
      _$V2ProjectCopyRemoveRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2ProjectCopyRemoveRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
