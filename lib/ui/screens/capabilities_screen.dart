import 'package:flutter/material.dart';

import '../../state/connection.dart';
import 'library_screen.dart';
import 'tools_screen.dart';

/// One tabbed home for the four server-capability catalogs, replacing four
/// separate More destinations with adjacent tabs.
class CapabilitiesScreen extends StatelessWidget {
  final ConnectionController controller;
  final int initialTab;

  const CapabilitiesScreen({
    super.key,
    required this.controller,
    this.initialTab = 0,
  });

  @override
  Widget build(BuildContext context) {
    // §7 rule 4: a tab whose screen has no backend is dropped and the screen
    // it sits in survives. The tool inventory is the only gated catalog here.
    final tools = controller.capabilities.toolInventory;
    final tabs = <(String, Widget)>[
      ('Commands', CommandsScreen(controller: controller, embedded: true)),
      if (tools) ('Tools', ToolsScreen(controller: controller, embedded: true)),
      ('Skills', SkillsScreen(controller: controller, embedded: true)),
      ('References', ReferencesScreen(controller: controller, embedded: true)),
    ];
    return DefaultTabController(
      length: tabs.length,
      initialIndex: initialTab.clamp(0, tabs.length - 1),
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Commands & tools'),
          bottom: TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            tabs: [
              for (final tab in tabs)
                Tab(key: ValueKey('capabilities-tab-${tab.$1}'), text: tab.$1),
            ],
          ),
        ),
        body: TabBarView(children: [for (final tab in tabs) tab.$2]),
      ),
    );
  }
}
