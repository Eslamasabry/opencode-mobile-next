//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_icon.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectIcon {
  /// Returns a new [ProjectIcon] instance.
  ProjectIcon({this.url, this.override, this.color});

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  @JsonKey(name: r'override', required: false, includeIfNull: false)
  final String? override;

  @JsonKey(name: r'color', required: false, includeIfNull: false)
  final String? color;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectIcon &&
            runtimeType == other.runtimeType &&
            equals(
              [url, override, color],
              [other.url, other.override, other.color],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([url, override, color]);

  factory ProjectIcon.fromJson(Map<String, dynamic> json) =>
      _$ProjectIconFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectIconToJson(this);

  String toString() {
    return toJson().toString();
  }
}
