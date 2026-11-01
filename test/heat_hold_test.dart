import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/process_budget.dart';
import 'package:opencode_mobile/platform/thermal.dart';

void main() {
  BudgetWait? decide(ThermalReading thermal, {bool fullSpeed = false}) =>
      ProcessBudget.decide(
        job: HeavyJob.agentSession,
        running: 4,
        limitLifted: false,
        thermal: thermal,
        fullSpeed: fullSpeed,
      );

  test('a forecast near severe holds new heavy work before the pause', () {
    expect(
      decide(const ThermalReading(status: ThermalStatus.none, headroom: 0.92)),
      BudgetWait.warm,
    );
    expect(
      decide(const ThermalReading(status: ThermalStatus.moderate)),
      BudgetWait.warm,
    );
  });

  test('a warm but not hot phone still starts work (only slowed)', () {
    expect(
      decide(const ThermalReading(status: ThermalStatus.none, headroom: 0.75)),
      isNull,
    );
  });

  test('Always full speed lifts the hold; the pause still holds', () {
    expect(
      decide(
        const ThermalReading(status: ThermalStatus.none, headroom: 0.92),
        fullSpeed: true,
      ),
      isNull,
    );
    expect(
      decide(
        const ThermalReading(status: ThermalStatus.severe),
        fullSpeed: true,
      ),
      BudgetWait.warm,
    );
  });
}
