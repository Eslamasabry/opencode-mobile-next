//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_v2_reference_local.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigV2ReferenceLocal {
  /// Returns a new [ConfigV2ReferenceLocal] instance.
  ConfigV2ReferenceLocal({required this.path, this.description, this.hidden});

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigV2ReferenceLocal &&
            runtimeType == other.runtimeType &&
            equals(
              [path, description, hidden],
              [other.path, other.description, other.hidden],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([path, description, hidden]);

  factory ConfigV2ReferenceLocal.fromJson(Map<String, dynamic> json) =>
      _$ConfigV2ReferenceLocalFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigV2ReferenceLocalToJson(this);

  String toString() {
    return toJson().toString();
  }
}
