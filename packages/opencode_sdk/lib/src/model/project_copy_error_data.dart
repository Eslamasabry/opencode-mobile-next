//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_copy_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectCopyErrorData {
  /// Returns a new [ProjectCopyErrorData] instance.
  ProjectCopyErrorData({required this.message, this.forceRequired});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'forceRequired', required: false, includeIfNull: false)
  final bool? forceRequired;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectCopyErrorData &&
            runtimeType == other.runtimeType &&
            equals(
              [message, forceRequired],
              [other.message, other.forceRequired],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([message, forceRequired]);

  factory ProjectCopyErrorData.fromJson(Map<String, dynamic> json) =>
      _$ProjectCopyErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectCopyErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
