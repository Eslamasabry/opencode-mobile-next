//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent_part_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AgentPartSource {
  /// Returns a new [AgentPartSource] instance.
  AgentPartSource({
    required this.value,

    required this.start,

    required this.end,
  });

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  // minimum: 0
  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final int end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AgentPartSource &&
            runtimeType == other.runtimeType &&
            equals([value, start, end], [other.value, other.start, other.end]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([value, start, end]);

  factory AgentPartSource.fromJson(Map<String, dynamic> json) =>
      _$AgentPartSourceFromJson(json);

  Map<String, dynamic> toJson() => _$AgentPartSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}
