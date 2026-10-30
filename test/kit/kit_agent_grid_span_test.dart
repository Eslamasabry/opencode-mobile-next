// KitAgentGridSpan: tells a card it spans the row (covered with KitAgentGrid
// in kit_agent_grid_test.dart; this checks the lookup itself).
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

void main() {
  testWidgets('a card inside a span is wide; outside it is not', (
    tester,
  ) async {
    late bool inside;
    late bool outside;
    await tester.pumpWidget(
      Column(
        textDirection: TextDirection.ltr,
        children: [
          KitAgentGridSpan(
            child: Builder(
              builder: (context) {
                inside = KitAgentGridSpan.wideOf(context);
                return const SizedBox();
              },
            ),
          ),
          Builder(
            builder: (context) {
              outside = KitAgentGridSpan.wideOf(context);
              return const SizedBox();
            },
          ),
        ],
      ),
    );
    expect(inside, isTrue);
    expect(outside, isFalse);
  });
}
