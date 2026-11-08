/// The Node.js release (and the Paseo daemon version) the built-in Ubuntu
/// installs for the phone's agents and tools. Pinned with checksums: raising
/// a version is a code change with a test run behind it.
const builtinNodePins = <String, String>{
  'node_version': 'v24.21.0',
  'node_base_url': 'https://nodejs.org/dist',
  'node_sha256_arm64':
      '724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5',
  'node_sha256_x64':
      '6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff',
  'paseo_version': '0.11.2',
};
