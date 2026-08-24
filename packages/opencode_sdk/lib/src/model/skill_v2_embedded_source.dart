//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/skill_v2_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'skill_v2_embedded_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SkillV2EmbeddedSource {
  /// Returns a new [SkillV2EmbeddedSource] instance.
  SkillV2EmbeddedSource({required this.type, required this.skill});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SkillV2EmbeddedSourceTypeEnum.unknownDefaultOpenApi,
  )
  final SkillV2EmbeddedSourceTypeEnum type;

  @JsonKey(name: r'skill', required: true, includeIfNull: false)
  final SkillV2Info skill;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SkillV2EmbeddedSource &&
            runtimeType == other.runtimeType &&
            equals([type, skill], [other.type, other.skill]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, skill]);

  factory SkillV2EmbeddedSource.fromJson(Map<String, dynamic> json) =>
      _$SkillV2EmbeddedSourceFromJson(json);

  Map<String, dynamic> toJson() => _$SkillV2EmbeddedSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SkillV2EmbeddedSourceTypeEnum {
  @JsonValue(r'embedded')
  embedded(r'embedded'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SkillV2EmbeddedSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
