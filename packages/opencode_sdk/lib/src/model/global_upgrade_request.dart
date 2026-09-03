//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'global_upgrade_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class GlobalUpgradeRequest {
  /// Returns a new [GlobalUpgradeRequest] instance.
  GlobalUpgradeRequest({required this.target});

  @JsonKey(name: r'target', required: true, includeIfNull: false)
  final String target;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is GlobalUpgradeRequest &&
            runtimeType == other.runtimeType &&
            equals([target], [other.target]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([target]);

  factory GlobalUpgradeRequest.fromJson(Map<String, dynamic> json) =>
      _$GlobalUpgradeRequestFromJson(json);

  Map<String, dynamic> toJson() => _$GlobalUpgradeRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
