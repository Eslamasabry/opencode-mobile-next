//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_watcher.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigWatcher {
  /// Returns a new [ConfigWatcher] instance.
  ConfigWatcher({this.ignore});

  @JsonKey(name: r'ignore', required: false, includeIfNull: false)
  final List<String>? ignore;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigWatcher &&
            runtimeType == other.runtimeType &&
            equals([ignore], [other.ignore]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([ignore]);

  factory ConfigWatcher.fromJson(Map<String, dynamic> json) =>
      _$ConfigWatcherFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigWatcherToJson(this);

  String toString() {
    return toJson().toString();
  }
}
