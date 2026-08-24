//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'console_state.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConsoleState {
  /// Returns a new [ConsoleState] instance.
  ConsoleState({
    required this.consoleManagedProviders,

    this.activeOrgName,

    required this.switchableOrgCount,
  });

  @JsonKey(
    name: r'consoleManagedProviders',
    required: true,
    includeIfNull: false,
  )
  final List<String> consoleManagedProviders;

  @JsonKey(name: r'activeOrgName', required: false, includeIfNull: false)
  final String? activeOrgName;

  // minimum: 0
  @JsonKey(name: r'switchableOrgCount', required: true, includeIfNull: false)
  final int switchableOrgCount;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConsoleState &&
            runtimeType == other.runtimeType &&
            equals(
              [consoleManagedProviders, activeOrgName, switchableOrgCount],
              [
                other.consoleManagedProviders,
                other.activeOrgName,
                other.switchableOrgCount,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        consoleManagedProviders,
        activeOrgName,
        switchableOrgCount,
      ]);

  factory ConsoleState.fromJson(Map<String, dynamic> json) =>
      _$ConsoleStateFromJson(json);

  Map<String, dynamic> toJson() => _$ConsoleStateToJson(this);

  String toString() {
    return toJson().toString();
  }
}
