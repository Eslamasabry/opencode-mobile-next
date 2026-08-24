//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_apply200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsApply200Response {
  /// Returns a new [VcsApply200Response] instance.
  VcsApply200Response({required this.applied});

  @JsonKey(name: r'applied', required: true, includeIfNull: false)
  final bool applied;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsApply200Response &&
            runtimeType == other.runtimeType &&
            equals([applied], [other.applied]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([applied]);

  factory VcsApply200Response.fromJson(Map<String, dynamic> json) =>
      _$VcsApply200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$VcsApply200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
