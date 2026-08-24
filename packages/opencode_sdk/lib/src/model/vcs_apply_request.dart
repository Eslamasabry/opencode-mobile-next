//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_apply_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsApplyRequest {
  /// Returns a new [VcsApplyRequest] instance.
  VcsApplyRequest({required this.patch_});

  @JsonKey(name: r'patch', required: true, includeIfNull: false)
  final String patch_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsApplyRequest &&
            runtimeType == other.runtimeType &&
            equals([patch_], [other.patch_]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([patch_]);

  factory VcsApplyRequest.fromJson(Map<String, dynamic> json) =>
      _$VcsApplyRequestFromJson(json);

  Map<String, dynamic> toJson() => _$VcsApplyRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
