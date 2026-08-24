//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_project_copy_generate_name_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalProjectCopyGenerateNameRequest {
  /// Returns a new [ExperimentalProjectCopyGenerateNameRequest] instance.
  ExperimentalProjectCopyGenerateNameRequest({this.context});

  @JsonKey(name: r'context', required: false, includeIfNull: false)
  final String? context;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalProjectCopyGenerateNameRequest &&
            runtimeType == other.runtimeType &&
            equals([context], [other.context]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([context]);

  factory ExperimentalProjectCopyGenerateNameRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalProjectCopyGenerateNameRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalProjectCopyGenerateNameRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
