//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'installation_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class InstallationUpdatedData {
  /// Returns a new [InstallationUpdatedData] instance.
  InstallationUpdatedData({required this.version});

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final String version;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is InstallationUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([version], [other.version]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([version]);

  factory InstallationUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$InstallationUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$InstallationUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
