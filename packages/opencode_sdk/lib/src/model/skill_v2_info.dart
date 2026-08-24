//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'skill_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SkillV2Info {
  /// Returns a new [SkillV2Info] instance.
  SkillV2Info({
    required this.name,

    this.description,

    this.slash,

    required this.location,

    required this.content,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'slash', required: false, includeIfNull: false)
  final bool? slash;

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final String location;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final String content;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SkillV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [name, description, slash, location, content],
              [
                other.name,
                other.description,
                other.slash,
                other.location,
                other.content,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, description, slash, location, content]);

  factory SkillV2Info.fromJson(Map<String, dynamic> json) =>
      _$SkillV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$SkillV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
