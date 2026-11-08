/// The Node.js release (and the Paseo daemon version) the built-in Ubuntu
/// installs for the phone's agents and tools. Pinned with checksums: raising
/// a version is a code change with a test run behind it.
const builtinNodePins = <String, String>{
  'node_version': 'v24.22.2',
  'node_base_url': 'https://nodejs.org/dist',
  'node_sha256_arm64':
      '95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2',
  'node_sha256_x64':
      '361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9',
  'paseo_version': '0.11.2',
};
