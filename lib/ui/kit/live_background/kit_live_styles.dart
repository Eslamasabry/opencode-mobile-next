import 'kit_live_style.dart';
import 'styles/kit_live_aurora.dart';
import 'styles/kit_live_blueprint.dart';
import 'styles/kit_live_bokeh.dart';
import 'styles/kit_live_brushed_metal.dart';
import 'styles/kit_live_caustics.dart';
import 'styles/kit_live_circuit.dart';
import 'styles/kit_live_constellation.dart';
import 'styles/kit_live_dot_ripple.dart';
import 'styles/kit_live_flow_threads.dart';
import 'styles/kit_live_glow_spill.dart';
import 'styles/kit_live_halftone.dart';
import 'styles/kit_live_honeycomb.dart';
import 'styles/kit_live_light_rays.dart';
import 'styles/kit_live_liquid_blobs.dart';
import 'styles/kit_live_matrix_rain.dart';
import 'styles/kit_live_mesh.dart';
import 'styles/kit_live_oscilloscope.dart';
import 'styles/kit_live_radar.dart';
import 'styles/kit_live_silk.dart';
import 'styles/kit_live_topo.dart';

export 'kit_live_style.dart';

/// Every look, in the order the picker shows them: the ambient looks, then
/// the industrial ones, then the premium ones. Add a style by writing
/// one [KitLiveStyle] and listing it here (its label goes in the ARB files).
const List<KitLiveStyle> kitLiveStyles = <KitLiveStyle>[
  KitLiveAurora(),
  KitLiveMesh(),
  KitLiveDotRipple(),
  KitLiveTopo(),
  KitLiveFlowThreads(),
  KitLiveGlowSpill(),
  KitLiveLightRays(),
  KitLiveConstellation(),
  KitLiveHalftone(),
  KitLiveLiquidBlobs(),
  KitLiveMatrixRain(),
  KitLiveBlueprint(),
  KitLiveCircuit(),
  KitLiveOscilloscope(),
  KitLiveRadar(),
  KitLiveHoneycomb(),
  KitLiveBrushedMetal(),
  KitLiveSilk(),
  KitLiveCaustics(),
  KitLiveBokeh(),
];

/// The look shipped as the default.
const String kitLiveDefaultStyleId = 'aurora';

/// The style stored under [id]; the default when [id] is unknown or null.
KitLiveStyle kitLiveStyleById(String? id) => kitLiveStyles.firstWhere(
  (s) => s.id == id,
  orElse: () => kitLiveStyles.firstWhere((s) => s.id == kitLiveDefaultStyleId),
);
