//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/config_v2_experimental_policy.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_experimental.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigExperimental {
  /// Returns a new [ConfigExperimental] instance.
  ConfigExperimental({
    this.disablePasteSummary,

    this.batchTool,

    this.openTelemetry,

    this.primaryTools,

    this.continueLoopOnDeny,

    this.mcpTimeout,

    this.policies,
  });

  @JsonKey(
    name: r'disable_paste_summary',
    required: false,
    includeIfNull: false,
  )
  final bool? disablePasteSummary;

  @JsonKey(name: r'batch_tool', required: false, includeIfNull: false)
  final bool? batchTool;

  @JsonKey(name: r'openTelemetry', required: false, includeIfNull: false)
  final bool? openTelemetry;

  @JsonKey(name: r'primary_tools', required: false, includeIfNull: false)
  final List<String>? primaryTools;

  @JsonKey(
    name: r'continue_loop_on_deny',
    required: false,
    includeIfNull: false,
  )
  final bool? continueLoopOnDeny;

  @JsonKey(name: r'mcp_timeout', required: false, includeIfNull: false)
  final int? mcpTimeout;

  @JsonKey(name: r'policies', required: false, includeIfNull: false)
  final List<ConfigV2ExperimentalPolicy>? policies;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigExperimental &&
            runtimeType == other.runtimeType &&
            equals(
              [
                disablePasteSummary,
                batchTool,
                openTelemetry,
                primaryTools,
                continueLoopOnDeny,
                mcpTimeout,
                policies,
              ],
              [
                other.disablePasteSummary,
                other.batchTool,
                other.openTelemetry,
                other.primaryTools,
                other.continueLoopOnDeny,
                other.mcpTimeout,
                other.policies,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        disablePasteSummary,
        batchTool,
        openTelemetry,
        primaryTools,
        continueLoopOnDeny,
        mcpTimeout,
        policies,
      ]);

  factory ConfigExperimental.fromJson(Map<String, dynamic> json) =>
      _$ConfigExperimentalFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigExperimentalToJson(this);

  String toString() {
    return toJson().toString();
  }
}
