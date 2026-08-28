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
    return DefaultTabController(
      length: 4,
      initialIndex: initialTab.clamp(0, 3),
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Commands & tools'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Commands'),
              Tab(text: 'Tools'),
              Tab(text: 'Skills'),
              Tab(text: 'References'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            CommandsScreen(controller: controller, embedded: true),
            ToolsScreen(controller: controller, embedded: true),
            SkillsScreen(controller: controller, embedded: true),
            ReferencesScreen(controller: controller, embedded: true),
          ],
        ),
      ),
    );
  }
}
