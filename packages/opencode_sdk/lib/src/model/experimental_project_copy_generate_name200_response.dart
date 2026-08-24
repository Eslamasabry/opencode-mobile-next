//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_project_copy_generate_name200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalProjectCopyGenerateName200Response {
  /// Returns a new [ExperimentalProjectCopyGenerateName200Response] instance.
  ExperimentalProjectCopyGenerateName200Response({required this.name});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalProjectCopyGenerateName200Response &&
            runtimeType == other.runtimeType &&
            equals([name], [other.name]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name]);

  factory ExperimentalProjectCopyGenerateName200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalProjectCopyGenerateName200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalProjectCopyGenerateName200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
