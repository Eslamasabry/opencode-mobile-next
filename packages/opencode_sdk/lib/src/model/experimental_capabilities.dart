//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_capabilities.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalCapabilities {
  /// Returns a new [ExperimentalCapabilities] instance.
  ExperimentalCapabilities({required this.backgroundSubagents});

  @JsonKey(name: r'backgroundSubagents', required: true, includeIfNull: false)
  final bool backgroundSubagents;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalCapabilities &&
            runtimeType == other.runtimeType &&
            equals([backgroundSubagents], [other.backgroundSubagents]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([backgroundSubagents]);

  factory ExperimentalCapabilities.fromJson(Map<String, dynamic> json) =>
      _$ExperimentalCapabilitiesFromJson(json);

  Map<String, dynamic> toJson() => _$ExperimentalCapabilitiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
